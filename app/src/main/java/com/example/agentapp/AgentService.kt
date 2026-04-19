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
        wsServer = AgentWebSocketServer(8080, collector, this)
        wsServer.start()

        ngrokManager = NgrokManager(this)

        scope.launch {
            try {
                val result = ngrokManager.startTunnel(8080)

                // Parse result - format is "PUBLIC:url|LOCAL:ip" or "LOCAL:ip"
                val displayUrl: String
                val broadcastUrl: String

                when {
                    result.startsWith("PUBLIC:") && result.contains("|LOCAL:") -> {
                        val parts = result.split("|")
                        val pub = parts[0].removePrefix("PUBLIC:")
                        val local = parts[1].removePrefix("LOCAL:")
                        displayUrl = "Public: $pub\nLocal: $local"
                        broadcastUrl = pub
                        publicUrl = pub
                    }
                    result.startsWith("LOCAL:") -> {
                        val local = result.removePrefix("LOCAL:")
                        displayUrl = "WiFi only: $local\n(same network as phone)"
                        broadcastUrl = local
                        publicUrl = local
                    }
                    else -> {
                        displayUrl = result
                        broadcastUrl = result
                        publicUrl = result
                    }
                }

                updateNotification(displayUrl)
                sendBroadcast(Intent("NGROK_URL_READY").putExtra("url", broadcastUrl).putExtra("display", displayUrl))

            } catch (e: Exception) {
                val errMsg = e.message ?: "Unknown error"
                publicUrl = "Error: $errMsg"
                updateNotification("Error: $errMsg")
                sendBroadcast(Intent("NGROK_URL_READY").putExtra("url", "ERROR").putExtra("display", "Error: $errMsg"))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
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
        ).apply { description = "Phone Agent background service" }
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
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(text))
    }
}
