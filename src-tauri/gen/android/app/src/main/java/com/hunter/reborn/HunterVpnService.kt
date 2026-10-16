package com.hunter.reborn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

class LocalSocks5Forwarder(
    private val session: Session,
    private val port: Int = 1080
) {
    private var serverSocket: ServerSocket? = null
    @Volatile private var isRunning = false

    fun start() {
        isRunning = true
        Thread {
            try {
                serverSocket = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
                Log.i("LocalSocks5Forwarder", "SOCKS5 Forwarder escutando em 127.0.0.1:$port")
                while (isRunning) {
                    val client = serverSocket?.accept() ?: break
                    Thread { handleClient(client) }.start()
                }
            } catch (e: Exception) {
                if (isRunning) Log.e("LocalSocks5Forwarder", "Erro no servidor SOCKS5", e)
            }
        }.start()
    }

    private fun handleClient(client: Socket) {
        try {
            val cin = client.getInputStream()
            val cout = client.getOutputStream()

            // 1. SOCKS5 Handshake
            val ver = cin.read()
            if (ver != 5) {
                client.close()
                return
            }
            val nmethods = cin.read()
            if (nmethods <= 0) {
                client.close()
                return
            }
            val methods = ByteArray(nmethods)
            cin.read(methods)
            cout.write(byteArrayOf(5, 0)) // No Authentication Required
            cout.flush()

            // 2. Request
            val rVer = cin.read()
            val cmd = cin.read() // 1 = CONNECT
            cin.read() // RSV
            val atyp = cin.read()

            val targetHost = when (atyp) {
                1 -> { // IPv4
                    val ip = ByteArray(4)
                    cin.read(ip)
                    InetAddress.getByAddress(ip).hostAddress ?: ""
                }
                3 -> { // Domain name
                    val len = cin.read()
                    val domain = ByteArray(len)
                    cin.read(domain)
                    String(domain, Charsets.UTF_8)
                }
                else -> {
                    client.close()
                    return
                }
            }

            val p1 = cin.read()
            val p2 = cin.read()
            val targetPort = ((p1 and 0xFF) shl 8) or (p2 and 0xFF)

            // 3. Conecta via túnel SSH através de canal direct-tcpip
            val channel = session.openChannel("direct-tcpip") as ChannelDirectTCPIP
            channel.setHost(targetHost)
            channel.setPort(targetPort)
            channel.connect(5000)

            // 4. Responde sucesso para o cliente SOCKS5 (tun2proxy)
            cout.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
            cout.flush()

            val chin = channel.inputStream
            val chout = channel.outputStream

            // 5. Bombeamento bidirecional de dados
            val tClientToChannel = Thread {
                try {
                    val buf = ByteArray(16384)
                    var n: Int
                    while (cin.read(buf).also { n = it } != -1) {
                        chout.write(buf, 0, n)
                        chout.flush()
                    }
                } catch (_: Exception) {}
            }

            val tChannelToClient = Thread {
                try {
                    val buf = ByteArray(16384)
                    var n: Int
                    while (chin.read(buf).also { n = it } != -1) {
                        cout.write(buf, 0, n)
                        cout.flush()
                    }
                } catch (_: Exception) {}
            }

            tClientToChannel.start()
            tChannelToClient.start()

            while (channel.isConnected && !client.isClosed) {
                Thread.sleep(500)
            }
        } catch (_: Exception) {
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
    }
}

class HunterSocketFactory(
    private val vpnService: VpnService,
    private val isWebSocket: Boolean
) : SocketFactory {

    private fun getTrustAllSSLSocketFactory(): SSLSocketFactory {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        return sslContext.socketFactory
    }

    override fun createSocket(host: String, port: Int): Socket {
        if (port == 443 && !isWebSocket) {
            // Porta 443: SSH direto sobre TLS (Stunnel) com SNI e certificados aceitos
            val rawSocket = Socket()
            vpnService.protect(rawSocket)
            rawSocket.connect(InetSocketAddress(host, port), 10000)

            val sslFactory = getTrustAllSSLSocketFactory()
            val sslSocket = sslFactory.createSocket(rawSocket, host, port, true) as SSLSocket
            sslSocket.startHandshake()
            Log.i("HunterSocketFactory", "Túnel TLS estabelecido na porta 443 com SNI ($host)")
            return sslSocket
        } else {
            val rawSocket = Socket()
            vpnService.protect(rawSocket)
            rawSocket.connect(InetSocketAddress(host, port), 10000)

            if (isWebSocket || port == 80) {
                val out = rawSocket.getOutputStream()
                val req = "GET / HTTP/1.1\r\nHost: $host\r\nConnection: Upgrade\r\nUpgrade: Websocket\r\n\r\n"
                out.write(req.toByteArray(Charsets.UTF_8))
                out.flush()

                val `in` = rawSocket.getInputStream()
                val sb = StringBuilder()
                var bytesRead = 0
                while (bytesRead < 4096) {
                    val b = `in`.read()
                    if (b == -1) break
                    sb.append(b.toChar())
                    bytesRead++
                    if (sb.contains("200 REVHUNTER\r\n") && sb.endsWith("\r\n\r\n")) {
                        break
                    } else if (sb.contains("101") && sb.endsWith("\r\n\r\n") && !sb.contains("REVHUNTER")) {
                        break
                    }
                }
                Log.i("HunterSocketFactory", "WebSocket handshake concluído: ${sb.take(80)}")
            }

            return rawSocket
        }
    }

    override fun getInputStream(socket: Socket): InputStream {
        return socket.getInputStream()
    }

    override fun getOutputStream(socket: Socket): OutputStream {
        return socket.getOutputStream()
    }
}

class HunterVpnService : VpnService() {

    companion object {
        private const val TAG = "HunterVpnService"
        const val ACTION_CONNECT = "com.hunter.reborn.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.hunter.reborn.ACTION_DISCONNECT"
        private const val CHANNEL_ID = "HunterVpnChannel"
        private const val NOTIFICATION_ID = 2001

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var lastFd: Int = -1
            private set

        val bytesUp = AtomicLong(0)
        val bytesDown = AtomicLong(0)
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var sshSession: Session? = null
    private var socksForwarder: LocalSocks5Forwarder? = null
    private val isStopping = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                disconnect()
                stopSelf()
            }
            ACTION_CONNECT, null -> {
                val host = intent?.getStringExtra("host") ?: "servidor01.rvpronext.online"
                val port = intent?.getIntExtra("port", 80) ?: 80
                val protocol = intent?.getStringExtra("protocol") ?: "ssh"
                connect(host, port, protocol)
            }
        }
        return START_NOT_STICKY
    }

    private fun connect(host: String, port: Int, protocol: String) {
        if (isRunning) return

        try {
            val notification = buildNotification("Rev Hunter VPN Conectando...", "Servidor: $host:$port ($protocol)")
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
        } catch (e: Throwable) {
            Log.e(TAG, "Falha ao iniciar startForeground", e)
        }

        Thread {
            try {
                // 1. Inicializa o cliente SSH e o Dynamic Port Forwarding SOCKS5 na porta 1080
                Log.i(TAG, "Iniciando túnel SSH com JSch para $host:$port...")
                val jsch = JSch()
                
                // Carrega chave RSA desprotegida (sem necessidade de AES-128-CBC)
                try {
                    val rsaKeyBase64 = "LS0tLS1CRUdJTiBSU0EgUFJJVkFURSBLRVktLS0tLQ0KTUlJSktBSUJBQUtDQWdFQTB5UDljWFV2RjhqaHFSWFN4NXhhSFZZdDgzSWZVMWt5d0UzcHBhZUs3TDRVV1M1bA0KVm1TZTdhWTg4dTNldnhhamFxcVdYYmpUYmwrbkREc1hTTC9mVStHR3M2RHplc0hEZVV6TGRGT2huT3U5VC9zMQ0KaktMSnhJWUt1V240dmhoT0owZVBkWDc4SzdwL2x2bjd2WUJFL2ZPSWJsQ2p0eXVpdUdsbzdIdU1LRDI3N3MxWg0KbW1sWGd3VmF2OTAvR05WRnExV3V2SlF3cmdUTklXbU8yZ1lveHZJdjBvbUhvT0FWcWFDZVpKQ1FGcG5MSGs2Yg0KSkw4eFhCZWNBWXpSaURqclE4MDdiUlJSV0ozL0h4c0hIT1ZvQ280ZU5YY0xDdXBDcTF3UVdWa1hFZU9na0lHUQ0KQWM3NkY2SXFwTEFLTC9DVHZtajJnWk9yaDFhSUN3MlFVeTFMVVF0NzZIZ0gzMG1tcHZ6UHJKeVZNTjhvMmdjaw0KWmhnaUpXaEdZclhXbDF1eURlNTdWTkIxY1luOGY2UUpUQ3UyRU8zRnNBbGFFSXg5WWlrZjdPMFEyMWhrRXhYNQ0KM1BVTCs3NXk4eUpwSW85K0R6SnlDSThqblJBMXhpVWZDTTRVWDhJSFRZT0pjb2JkVm41bG8xSWNKSUl0SmdJRA0KQzUzQnhkL01ZRWR3VVFXaEY1dzlhWnVrRGtmeGNHT3NYMnpoYVcwS0RZbCtzU0UzVENaWFM2Uzd1UjZzZmd5eg0KbHcvOWJzN3lEVWpqQjExYVR1cG5odWRQaUJWSzVFOXpzallabC9DMW13a1ZlVmk5UWVpVWlLblgxeEora1JLTg0KQldhWnIyYzVFR1JuMzBOT0kvT09wSXoyeUZGcmNJUW12T1BtSXkyTnIwVEVqWGtRWHB2Rjc0TjZkVmtDQXdFQQ0KQVFLQ0FnQldoYUVPSlY4dTkvckJOMWd3eERtSlhBOW9lNU9xckJySUF5V0Q1S3hoWjJNZlNJdnVZejFkVllvVQ0KWXJXc3lMSWV1OWp5ZnhMbFgvMHNYZXFrdlR2Zk84UGZTRTErT0hqSjlGWkxUUm9sRzlWd09Sa3drR1R1UzUrSg0KYTR1ckducXNUZjdUdnozSERaM0xXSlY0aWwrN1VWalBHK3g2Mno3TEl5Y0VBWHpsNEtacXJXdlNndWlCb0dnWA0KeDZwYm1zbjU5QnF1M0dzMlpIRkxCOTZxS1d2OFdyS09MQjJRSjBPNVIwQ2hKVHhtaG85ZDFBaHZmb25UbXprcQ0KeVhIenlKOWdJWElBdVJxOG5jSTJGUFZOT2hUd2ZseVN2RDJpTTIwdW00TjNtYktDZWkwS0NvM2dyOWg4WnMvaQ0Kdk9EdjFENDNBUHhERFNoMVJWaHFieHg5ZVVvckpXTjYvbXJIUkp3eHZVRjVlR1RHUG9VU2ZxNjhoaWpxOTY3cg0KMGQ5dGVrdTdML2wvL3V5UlNZWjlqWE45VDhVeW5TZDJpeXhYVGxmSWhqV2ViK0pVMGxrSEJQTXRjTG9jR3g0bg0KQnRpRDluUEtYcFgxQmh4MlpsRy8rMVhuSkhhUDQ1R1pYL0RQRFZIQWFyZllMeUhGWk9lYlFRcWVsVURLcnYzOA0KYXFqR2RGaUh1YmQwVDlqd2tOUnUwOXF6MmhUdy9xVnR3VXNzVUdSQVpzWlZnbzBNR3BRR1gvZHR2SlhZcjNVUw0Kc2pIS1VWVWJkT3Q5aGh0a2FkM3BVMWt1dGx0Yml2c0tkRENidzU0OEZHZjJlUEFpeEFRdDZCenYwbUZGMUMzNw0KMXR2ak0xcVE5MDJsNTZLc1UwQXdQUTNieGMvNUNmTDN3QnQ1Q3JsbmZsZWQ4L0NPZ1FLQ0FRRUE2dXR6SHYzcw0KN2R2RXk5SjNyejVkNWM0SnZnbTBEN2dUNUdUMWNGdVJNSVlUc3FhWTRhMDdQMndySWVreUM5b3QwTythK0R3Lw0KSGhqVTNvVHc2c00wVzdiRU9sMUVlc2l3NUE2bGlUSkxUNEpRbnF2QjdseVdlK0hQMXdJU2FPMFg2MElBMlc3bA0KdlBwcnBPZ3lueFFyYlZwdThra1RqSHpWK1o3dTVMa2Y5ZzZsZ3dHS00xRWtUb1ZDZFNqR04xVmwwMkh3RG9FcQ0KRGpjRk5FS2syMXh3SGkyUGRwU2ZRRHljR2xWN2hidjBvc0owcU9pSHpKeCtXeHJjV2IwNlg3aXc2Zkh4SStGRw0KcFBQNXAyQUY3a0c2SGNUb3BYeDNXM21VOGVzeVRwdzU2WkppRkRxNmxKNHBjWkVhL2ptVTRsWkcrR3VrZWFFNw0KSzYrRGZCT2JMaFNBVlFLQ0FRRUE1aFpKM01JMW45dkFuNitXY2FvY2lEdFBKTjZtUGd4cGd3WVM1YjYvZGVURw0KcGNra29iZXBkYnB2QWpyWHJCTVRYS1VXeTdHVlkrQzJMMllYeW5tSVNWWmNPTzVsOVFzZXVjN1EzanVLeGI1Rg0KcjB6OWlKYmRFWGVEeXVSdVowK0E3N0ZzMUxZZ2hQRGs3MStyOWtYR2lOZEtBV2o4YWxoaU5uaXRlS0RwM042cQ0KV3lFTWFCOE40QTZkQ2FYRDNreXdyRWRnbWFNdkhUYmU1bXpYZ2VUQUh4TUVXN3BVSDFVL2pWd2NRVDNVMm5tSQ0KUWpGRUJ0MElUZlJkMEtDUDhJUDJtVmpHQ3NVcWdKa0RjYjVJVDJPRXRFdjYwaFNGVzBOQnJlMk5nb3IxZ1dpLw0KTDMwZXhGVVRNRU03aTVaeFFXNVJQd0ltWHFybDlMNFpXbXlXa1lFVTlRS0NBUUFzTmkrdVQzKzlyRFMzWDE3aQ0KL2t2UnFoc3ZSc2c2aFJ4SlNhN2ZmSHd3czZzVlhrRTdoOHkwdlBqSkl3Z21MdU9jaE51WGlFOGdyTyt6Q1RZRw0KR01MTDEvUEtRQzlaSkZnV002TjMxSjFUMDJTMDJBSmhoODlTZFVjV1llOXhDOTRLaXB0NlVHS3dWRGphQ2xFcw0KbXpFcEhYSHY2MHpnamNYL2VxZmZKL0FaOE51NWNocStXYkVKamc3WjJkaWtKVkJLbmczOXpOVkxZNU41S0dtNg0KVndWS3lLeE5WdDg1eWE1ZlRtTTI0ZzdDYXZKbHI0MlBOb3laUE03bm5MTlZ2SDZmS01lSFJ5aHlkdkF2VGVVUA0KMkJMYzgyZ05iUVZSaytveU5WOThaKzZHUk9Sc2hqa0RLMWtGWkplOTdMeFdkSUphMG5kYjVMb3NHSllZVGVCUQ0KcGM4WkFvSUJBUURRUU9BOTFmS2ZiRkdldDFqZUxIQ0N4ZHZPclUrZTNiRXFqSGJTUjZJWDNTZ0hxbFZqb2s3Wg0KWmhOUDIxNldPdlhOWndlSStvVVN6N05XbDN0MUlpYys2ZEd6SGVSZXROL3NvY3JmT3RsU1BmUG94VEM5VG45dA0KYmRWMThPZ1V6aHNQMkhGKzNoSDFBZXpVMUsxYXpMRHo3cGpuU2RVZGxEM2d1NzJYYjEzOU1mVFRZc2hOL2IyYg0KVE43S2VxVWNNaUcrN1NQOEszMWJYZXpHZDBGZ0ttSFNQbTQxY0NMdjhVTUNlWnFCV3F1cnkxQ1BTUFFmbWpRSQ0KQ3VSWER0ejVmeGZXbUVrNE9sbEw2REw3NHBTa2Rwa0liN1RvTFBhL01kTGkwQjdEUjI4SzRIb2JTNDVyZWUxUw0KUTZoRG9ibUUxWXJvRXRRVDMyOGY1R2dpTmlMYU5CRjVBb0lCQUhCZCtnZEd1aG5DZXJvREErdXZTR0ZLTkZ6Wg0KTnVJd2NTdXdYa2lWMFloZzlKRndGekRhcCtQYW8zczZoN2k1WjcyOVR5d2ltamtROEVON2k5NzdwQlpKZ0VNVg0KN2ZDMFFmM1RhT3NHalFmRHp4d2tObk1QSTJpdE9QQU1xM3cxQkJoT2VhY25vNHFxQkMvU3JsQTFrK2pobEgyVQ0KeTI0bFZCN29MMnZQUmZoNER0V2xyL3BKVG52b3c5c0pSemxpd2w1ZlRCbDBBTFVmaEFOckh0a2Y1TUU4UGdWQg0KVkNsbWJsayszZldCWG5BMFRxSnJqUU03aWJZSjFaWDVrUWh6VUk4VEhqWTQ4cTFvdWNDMTczWTRwL1RBa3U5cA0KQTM3eGpLOGFqcSs5aTdlekI1T0ZzYkF1ekZIYWExa09ZWllEOThyZlBGOXFRU0Y2NElzZkkwMHlYUGM9DQotLS0tLUVORCBSU0EgUFJJVkFURSBLRVktLS0tLQ0K"
                    val rsaKeyBytes = android.util.Base64.decode(rsaKeyBase64, android.util.Base64.DEFAULT)
                    jsch.addIdentity("revhunter", rsaKeyBytes, null, null)
                    Log.i(TAG, "Chave RSA privada carregada com sucesso no JSch!")
                } catch (e: Exception) {
                    Log.w(TAG, "Aviso ao carregar chave RSA no JSch: ${e.message}")
                }

                val session = jsch.getSession("gp45ndonr", host, port)
                session.setPassword("ueuf49gpv")
                session.setConfig("StrictHostKeyChecking", "no")
                session.setConfig("PreferredAuthentications", "publickey,password,keyboard-interactive")
                session.timeout = 15000

                val isWs = (port == 80)
                session.setSocketFactory(HunterSocketFactory(this, isWs))

                try {
                    session.connect(15000)
                    sshSession = session
                    val forwarder = LocalSocks5Forwarder(session, 1080)
                    forwarder.start()
                    socksForwarder = forwarder
                    Log.i(TAG, "Sessão SSH conectada! SOCKS5 dinâmico ativo em 127.0.0.1:1080")
                } catch (e: Exception) {
                    Log.e(TAG, "Falha na conexão inicial SSH: ${e.message}", e)
                    updateNotification("Falha na conexão SSH", e.message ?: "Erro desconhecido")
                    disconnect()
                    return@Thread // Aborta para NÃO prender o tráfego da rede
                }

                // 2. Estabelece a interface TUN do Android apenas após SSH confirmado
                val builder = Builder().apply {
                    setSession("Rev Hunter VPN ($protocol)")
                    addAddress("10.0.0.2", 24)
                    addRoute("0.0.0.0", 0)
                    addDnsServer("1.1.1.1")
                    addDnsServer("8.8.8.8")
                    setMtu(1500)
                    setBlocking(false)
                    try {
                        addDisallowedApplication(packageName)
                    } catch (e: Exception) {
                        Log.w(TAG, "Não foi possível desabilitar o próprio app do túnel VPN", e)
                    }
                }

                vpnInterface = builder.establish()
                if (vpnInterface != null) {
                    lastFd = vpnInterface!!.fd
                    isRunning = true
                    isStopping.set(false)
                    bytesUp.set(0)
                    bytesDown.set(0)
                    Log.i(TAG, "Interface TUN criada com sucesso no FD: $lastFd")
                    updateNotification("Rev Hunter VPN Conectado", "Servidor: $host:$port ($protocol)")
                } else {
                    Log.e(TAG, "builder.establish() retornou null")
                    disconnect()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao estabelecer conexão", e)
                disconnect()
            }
        }.start()
    }

    private fun disconnect() {
        isStopping.set(true)
        try {
            socksForwarder?.stop()
        } catch (_: Exception) {}
        socksForwarder = null

        try {
            sshSession?.disconnect()
        } catch (_: Exception) {}
        sshSession = null

        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null
        lastFd = -1
        isRunning = false
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Throwable) {}
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    override fun onRevoke() {
        disconnect()
        super.onRevoke()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Rev Hunter VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação de túnel VPN ativo"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(title: String, text: String) {
        val notification = buildNotification(title, text)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, text: String): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
