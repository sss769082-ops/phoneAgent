package com.example.agentapp

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class AgentService : Service() {

    private lateinit var wsServer: AgentWebSocketServer
    private lateinit var collector: DataCollector
    private lateinit var ngrokManager: NgrokManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        var isRunning = false
        var publicUrl = ""
        const val CHANNEL_ID = "agent_channel"
        const val NOTIF_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Starting agent..."))

        collector = DataCollector(this)

        // Start WebSocket server on port 8080
        wsServer = AgentWebSocketServer(8080, collector, this)
        wsServer.start()

        // Start ngrok tunnel
        ngrokManager = NgrokManager(this)
        scope.launch {
            try {
                val url = ngrokManager.startTunnel(8080)
                publicUrl = url
                updateNotification(url)
                // Notify MainActivity
                sendBroadcast(Intent("NGROK_URL_READY").putExtra("url", url))
            } catch (e: Exception) {
                updateNotification("Tunnel error: ${e.message}")
                sendBroadcast(Intent("NGROK_URL_READY").putExtra("url", "ERROR: ${e.message}"))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY  // Restart if killed
    }

    override fun onDestroy() {
        isRunning = false
        publicUrl = ""
        scope.cancel()
        try { wsServer.stop() } catch (e: Exception) {}
        try { ngrokManager.stop() } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Phone Agent",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Phone Agent background service"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Phone Agent Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification(text))
    }
}
