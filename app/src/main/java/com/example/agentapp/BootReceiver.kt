package com.example.agentapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Auto-start the agent service when phone boots
            val prefs = context.getSharedPreferences("agent_prefs", Context.MODE_PRIVATE)
            val token = prefs.getString("ngrok_token", "")
            if (!token.isNullOrEmpty()) {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AgentService::class.java)
                )
            }
        }
    }
}
