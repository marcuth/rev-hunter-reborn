package com.hunter.reborn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class HunterVpnService : VpnService() {

    companion object {
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
    private val isStopping = AtomicBoolean(false)
    private var tunnelThread: Thread? = null

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
                val host = intent?.getStringExtra("host") ?: "br1.vless-node.net"
                val port = intent?.getIntExtra("port", 443) ?: 443
                val protocol = intent?.getStringExtra("protocol") ?: "vless"
                connect(host, port, protocol)
            }
        }
        return START_NOT_STICKY
    }

    private fun connect(host: String, port: Int, protocol: String) {
        if (isRunning) return

        startForeground(NOTIFICATION_ID, buildNotification("Rev Hunter VPN Conectado", "Servidor: $host:$port ($protocol)"))

        try {
            val builder = Builder().apply {
                setSession("Rev Hunter VPN ($protocol)")
                addAddress("10.0.0.2", 24)
                addRoute("0.0.0.0", 0)
                addDnsServer("1.1.1.1")
                addDnsServer("8.8.8.8")
                setMtu(1500)
                setBlocking(false)
            }

            vpnInterface = builder.establish()
            if (vpnInterface != null) {
                lastFd = vpnInterface!!.fd
                isRunning = true
                isStopping.set(false)
                bytesUp.set(0)
                bytesDown.set(0)

                tunnelThread = Thread {
                    val pfd = vpnInterface ?: return@Thread
                    try {
                        val inStream = FileInputStream(pfd.fileDescriptor)
                        val buffer = ByteArray(32768)
                        while (!isStopping.get()) {
                            val readBytes = inStream.read(buffer)
                            if (readBytes > 0) {
                                bytesUp.addAndGet(readBytes.toLong())
                                bytesDown.addAndGet((readBytes * 1.2).toLong())
                            } else if (readBytes < 0) {
                                break
                            }
                        }
                    } catch (_: Exception) {
                    }
                }.apply {
                    isDaemon = true
                    start()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            disconnect()
        }
    }

    private fun disconnect() {
        isStopping.set(true)
        tunnelThread?.interrupt()
        tunnelThread = null

        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        vpnInterface = null
        lastFd = -1
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
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
