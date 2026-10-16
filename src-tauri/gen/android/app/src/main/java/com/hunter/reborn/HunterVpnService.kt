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
import javax.net.ssl.SSLSocketFactory

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
            val `in` = client.getInputStream()
            val out = client.getOutputStream()

            // 1. SOCKS5 Handshake
            val ver = `in`.read()
            if (ver != 5) {
                client.close()
                return
            }
            val nmethods = `in`.read()
            if (nmethods <= 0) {
                client.close()
                return
            }
            val methods = ByteArray(nmethods)
            `in`.read(methods)
            out.write(byteArrayOf(5, 0)) // No Authentication Required
            out.flush()

            // 2. Request
            val rVer = `in`.read()
            val cmd = `in`.read() // 1 = CONNECT
            `in`.read() // RSV
            val atyp = `in`.read()

            val targetHost = when (atyp) {
                1 -> { // IPv4
                    val ip = ByteArray(4)
                    `in`.read(ip)
                    InetAddress.getByAddress(ip).hostAddress ?: ""
                }
                3 -> { // Domain name
                    val len = `in`.read()
                    val domain = ByteArray(len)
                    `in`.read(domain)
                    String(domain, Charsets.UTF_8)
                }
                else -> {
                    client.close()
                    return
                }
            }

            val p1 = `in`.read()
            val p2 = `in`.read()
            val targetPort = ((p1 and 0xFF) shl 8) or (p2 and 0xFF)

            // 3. Conecta via túnel SSH através do canal direct-tcpip
            val channel = session.openChannel("direct-tcpip") as ChannelDirectTCPIP
            channel.setHost(targetHost)
            channel.setPort(targetPort)
            channel.setInputStream(`in`)
            channel.setOutputStream(out)
            channel.connect(5000)

            // 4. Responde sucesso para o cliente SOCKS5 (tun2proxy)
            out.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
            out.flush()

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

    override fun createSocket(host: String, port: Int): Socket {
        val s = if (port == 443 && !isWebSocket) {
            val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val sslSocket = sslFactory.createSocket()
            vpnService.protect(sslSocket)
            sslSocket.connect(InetSocketAddress(host, port), 10000)
            sslSocket
        } else {
            val rawSocket = Socket()
            vpnService.protect(rawSocket)
            rawSocket.connect(InetSocketAddress(host, port), 10000)
            rawSocket
        }

        if (isWebSocket || port == 80) {
            val out = s.getOutputStream()
            val req = "GET / HTTP/1.1\r\nHost: $host\r\nConnection: Upgrade\r\nUpgrade: Websocket\r\n\r\n"
            out.write(req.toByteArray(Charsets.UTF_8))
            out.flush()

            val `in` = s.getInputStream()
            val sb = StringBuilder()
            var bytesRead = 0
            while (bytesRead < 2048) {
                val b = `in`.read()
                if (b == -1) break
                sb.append(b.toChar())
                bytesRead++
                if (sb.endsWith("\r\n\r\n")) {
                    break
                }
            }
            Log.i("HunterSocketFactory", "WebSocket handshake recebido: ${sb.take(50)}")
        } else if (port == 22) {
            val `in` = s.getInputStream()
            val sb = StringBuilder()
            var bytesRead = 0
            while (bytesRead < 2048) {
                val b = `in`.read()
                if (b == -1) break
                sb.append(b.toChar())
                bytesRead++
                if (sb.endsWith("\r\n\r\n")) {
                    break
                }
            }
            Log.i("HunterSocketFactory", "Banner HTTP 22 consumido: ${sb.take(50)}")
        }

        return s
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
                
                // Tenta carregar chave RSA embarcada
                try {
                    val rsaKeyBase64 = "LS0tLS1CRUdJTiBSU0EgUFJJVkFURSBLRVktLS0tLQpQcm9jLVR5cGU6IDQsRU5DUllQVEVECkRFSy1JbmZvOiBBRVMtMTI4LUNCQyw1REY3M0FDNzJEQjBFMEJCNDJDNkU1QTU4NkVEM0Q2MwoKZ09HZHg4L2cyOXluQmw3UjdVTWthWnJkaEJtZE5wVCtWaWplbE1PeUxrOXdsZ29Ddm5XWFV5blV2cWt4NHRJRApQcFVvcUZlUjNGM3czeVlpVloySW5PR3dQUGZoK0dZMGJFY0pleGNLQmFwaHcvSmN1dkNkMGJncHBMTGxxRjBqCmNQV2ZvNFVQVmdtdUVaSjZuMVFza2NkNzBqaDkrMWR1TDVtNXFsTVVwdjIzQXU0SHlDSmE5ODNzbHNaRzlYZWEKRmpUNjB3YVlnT3JwYjVNYWlIZFlGYWxhUCtkU2g2ZGdCZjMxT0xwZjAyanIrNG1PRnh1OG5sWDBlK0puTTRmegpoNWNwdDhoVlpYL2F5M24rYmZOaFBjSzJ4TkZVTnRMS2pGL2lzZGJWOWJzaVFoR0lOQmFSeWdySExzZ0puTVFuCkhjYjlFUVdyUHV0N2hxNVByU0sxY3FLVFRSbjd2VXVsakt2alg5T01OMUozRUpWMTRsWjRPV3IrQlNXYnVYalIKbzJDU0dBQmk2S3htRGpFd0hDeGVBazVUTEwvTSt5RlNBamNyVE56aUFVTVg2QXQ3UnJYQm54a2hGRnVLazhjRwpaZmNRMFNIUWZudE5pdnJqWlBzWFJVMEc3ZTVPVnprMyt4MWc0QlVnN3U4eTgwcXpzckpUeGR4aWM4M2pKS2wvCkNxRDlIelM5TGxzVjM3dFBkTldkWUdoTGU4MW5WejJZZVhDbjFPUy9xZ2RORHBnR3A2ZzVHNTVBNHp1REVaYUoKUTM0RzZhK1FPUnlHbTRvZGxzRDlUdEtMdjM4ZXZzQUJrOTVJWG9HMmJZQmVsVEhuZndDOHlndm1rREM1SERDQgorcWlwc2lpcDYvK2NLRTZ1dGxjN0VJbGF0c1BrOWZ6cXNVOUM3M1pzRDZtbVdBalFlOFpINFR5MkVycVg5cDkvCm1hOWtsWXZyQ1RoVkFGL3hTaXFRdmI1aXhNNGJsUktCRjh3ejNLeUpPN1BZYUFkcWVYa1BnTEdzUEJGR3RzQ2QKNUFQQ1ltaEkzcEpLV0ZQbXRJNXAwR1FVUlBtM2wxRm1GQ1hMdGJ3YkJ4ZVdKWVdEdHVzaGRrTHhvdHBQa0xiTQpjR1dPVlhkekFBNUdzaGp3dUI3d2hiZit6TVZYZ0NqY2JRTXNlSWRTRkFsRDZVeXF5ZTF2TVV2K3JRN05TRFJECjNqZ3BycXAzSGpDbVhwc0o4L1BjVEsxVXRNSHlCZWUvM2k3c2kzbVVzSDJXU2ZUTW96R1ArakNpMi9QSHZGSXoKNjk4R2NFaWI5S2FBZGdKRWowSVBBQUVldmpxZm9uSTZDYXBTeFVaQUFWTmZRVVVFVFFHQ200WGU2cDZhVVBkTApJeHUrQ29qdkZUc28xSUVObm1mekY2WmVmSFU5ci9aTFdvYnpXYmN1VDJPOEtrcm9aVlhFNGhFK2poTWRIcWZOCkJOZWFZWHlJdjRPZURTS0p5NEJYQmNadzVOSXozYVdjbVZ6S2d4dVl5MHFPRU5HR0ovTFBZK2xVdzgwRTFLOHcKYlNDSVRXK3lvV2JZamFZenVDc0VtNy9jQkJyWWRXbVd6bGNaUEltVVpuVXJZaStDbllxOVlpeXpqSkEzVkhYdwpWUzl2ZG5WK2VNbXBwU08yYU1ldjZtUnJOTStzeWRpTEhZeGdNb1hpNFZ6MlJkSFZ5VFRUS3c0bmFzMTJnajBxCkZicXpvcVo2MGp0anBDaDB3Q3Y4ZXZHNVd3S1NnSy9OOHpwam5CVFpNZGRtSFZEV3N2Sk9hNUZvblVCd0ZBanoKWmNrTDhTVmRVelJHT1VNbVZLdUU3aHJRb3g2aHNzZW1FMmFPYUtERkw3YUJ1czYxeUQxc3l3My9FRHBldWxYSQpNdkdtR1NTV2c2cVFsZ0RyOUJ6TUt6aHRPckpJbzI3V3pkZVFBVlV6TWdyTDRYRU9OMENyeGZsTzJINThhaW9xCjhiQ2VXazZ0L3E4OW1Wd1FKYnZIandKbnIrNGR5SGlZamc1SDNxV3l1dVFtRnlIbkZjanlZV0tPTGVxdDRLWmcKVVRQYncxSlN6cmQxR0hqSlFzQ1dBMiszOFdkaGJkUi9OQStkZTNiL2dBNTVJeHY1VnpQT1hJakdsZlhaL2I3TQpMdUxiaXlLZnVYblJ3YlRUMnpzcEtIMTVCREVOaXdrRkkwb21YRkRTTkkwaDRMUXBYT0hURDU5OE12c1pEeWJICjZDeE1EWndVYmdZd3dTcEZGdDlCalhGNWZDT1Jlc242WmtwQUthUUM1WkF0ZGtQR25zQy80Tk5KeEk4QmtzazcKV291OEFOOG5aM3ZBc0F2R2VyQ3c4U0Jyd0NlVENLMVB2OTZQNldiVkNYRVN2VXhKUWpabmpRQ3VhcjMwMjJ5SwozV0xsRmk2aXl6Q0REc3Z2T2NpODgvNkFJQ3JjYlJRbzFac215aVFJR3hkRW90eHJKd1RBWVU0UXA5U3Y5TjNCCnU5MmEyaG94ZktFNUNwbmxXOVpUa0JmZG04ZHphdHJrUUFWNG9IMzZiRnlodFZXT09SNlF3TDVWTXNkRjF2ZlIKZzgzQmZsMlZNZ3JUUkxMenY1dmF6b09TbmFmR0lHWXJvZXBPWXByQnIwaGdrdXJXU3dGVXdWWGpsTEtucnFrRApFdkJPckV5TjBUdWMycS9DWUJZdHZLYzhiUGE0N3V1YTdOYTVrekc5Y0VkNjBla2VqSnJ0eU4xTnJwenNJVi83CnZCK3BlVHhWdTUrRWU0ODVuRUR0RDlrcXQvV0x4dG05M2RGRjVZLzJHYVF1N2dzblgzcGpNNUthT1NFUXFHSHgKTVcxNytQRG10VkMraktxbkY1N3lWZXlQbFFPT3I0Z2lkVlVjWlRRRkFiSC9NdlZPdTZzTmVNN2YyZXJTdytqdQpkM0NCQ1ZaMloxNXFJV1FXb2pudy9NWWRZRGdETFYwT2RWRU5TaWg0TE45bUNTR0ZVWTlaTExQa0NuT2NKc3F0CnR1Z0g5d3M5VkM1bHpsRjVqdGN3OTFxbXduc0FJOGtQTWl6RHNqZ3ZjS2N2czlJREU0ZFlaMktkb2crOFZaQzQKVGFDczgzeEp3Z24rc0dUZExHRlRZbVNWaW9RQ0g0bjRuNkxQUGFXR2s0MVBlQzFvUWhlZ3pQVGdGZ2RHVHZzZwo1NlhHZSt5MUROR3Q1VVZ2T1ZDbXplRzhNR3FTMHNEbUJaa0ZKTm1OQXRDa2Q5c05FNCt4VCtBZ2JwclFDNkpsCmtEbll5aUZNUnVxZUNJVEM1Tm13dHBFZnlFYUVlUTJqRFdrUGxsaWtoL2NiOFJud1BxRkhqbEJMaEVCTXBSL28KTGthVllxcTVtTk81bHZ3MDYxeThFNWVwdmFJVCtRTnAzWDlFRDA5bmYzbGJzOUxhR1NGZE5NVVhiNkxKaDdydAoxZCtlRlZmMzV6REtBZWMzd1AwZ0kyMHNxdHlsMURydkRjQlQyR2hPVUhPUFFIQTdPSERqR0RCYS9JSHhKQnFEClNySUhzdUZEY1Y5aE5zU2pzbXVOS1BTSGZoczdiaDFsUW9ucFFBMDZaeUFIT0Fxb1R5V25vQ283cHo2akVwNmsKZjNJTGw0Vkc1dTFub3Q0QzVYSjVnSm1HYTF1REUzT1hEL1NyV1UyeEs2c0JQWUpmbitPV2QvZDRrS2xxYk02NwplNVZkRTZWR3FEMENRdUxoanN1TUdLanpaV2N6RWRlZm5Sc240dnA1Vmt4TDFjam1SZUZnRzZXVENjVFo1KzErCkJwTXppRGZhc0dCak5CbitGSUdVZHZXd3owVkpqTVpuL1BZNVN5Wnd6dzZwaG5FK21mN0k0ZUdqKzE4TjQ2elMKd3Q5ZHlRNWZGUzJuVi9ZZGFmcUszZEQ0MWpJV0hVdFBGU0YwbG9mSDZ3bS90TXJNRzFNYzJqdlNSUW5TYnoxdQp1SWZkcjVoV2N6SS9jcGJIcFp6K2d2SVJEekt0RTdkTjl5OHluUmQvZXlPQ3RzTnljTVVoVVBBWWZhVnpFcERoCnpzTFgyUUlNUGJUOTNwWlV5cVlIMm82N0Rram8wR0xVanR3M2pGaGl1ekF1cmVsbkcvVENQdFQrb1Y5ZzkvT1IKb3pKcEJqbWk3NklQK21vNE5pbXRJbXhhck0xUFdtc1RBKzA1SWlBR0pka04xNkdTN3cyUTJJbmJaSGt4N21UOAotLS0tLUVORCBSU0EgUFJJVkFURSBLRVktLS0tLQ=="
                    val rsaKeyBytes = android.util.Base64.decode(rsaKeyBase64, android.util.Base64.DEFAULT)
                    val rsaPass = " ".repeat(64).toByteArray(Charsets.UTF_8)
                    jsch.addIdentity("revhunter", rsaKeyBytes, null, rsaPass)
                } catch (e: Exception) {
                    Log.w(TAG, "Aviso ao carregar chave RSA no JSch: ${e.message}")
                }

                val session = jsch.getSession("gp45ndonr", host, port)
                session.setPassword("ueuf49gpv")
                session.setConfig("StrictHostKeyChecking", "no")
                session.setConfig("PreferredAuthentications", "publickey,password,keyboard-interactive")
                session.timeout = 10000

                val isWs = (port == 80)
                session.setSocketFactory(HunterSocketFactory(this, isWs))

                try {
                    session.connect(10000)
                    sshSession = session
                    val forwarder = LocalSocks5Forwarder(session, 1080)
                    forwarder.start()
                    socksForwarder = forwarder
                    Log.i(TAG, "Sessão SSH conectada! SOCKS5 dinâmico ativo em 127.0.0.1:1080")
                } catch (e: Exception) {
                    Log.w(TAG, "Aviso de conexão inicial SSH: ${e.message}. Prosseguindo com criação de interface TUN...")
                }

                // 2. Estabelece a interface TUN do Android
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
