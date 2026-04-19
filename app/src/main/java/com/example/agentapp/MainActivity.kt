package com.example.agentapp

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvUrl: TextView
    private lateinit var etNgrokToken: EditText
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private val urlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val url = intent.getStringExtra("url") ?: return
            runOnUiThread {
                tvUrl.text = "Dashboard URL:\nws://$url"
                tvStatus.text = "Agent running — connect from anywhere!"
            }
        }
    }

    private val requiredPermissions = buildList {
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filter { !it.value }.keys
        if (denied.isEmpty()) {
            doStartService()
        } else {
            tvStatus.text = "Permissions denied: ${denied.joinToString { it.substringAfterLast('.') }}\nGo to Settings > Apps > Phone Agent > Permissions"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus     = findViewById(R.id.tvStatus)
        tvUrl        = findViewById(R.id.tvUrl)
        etNgrokToken = findViewById(R.id.etNgrokToken)
        btnStart     = findViewById(R.id.btnStart)
        btnStop      = findViewById(R.id.btnStop)

        // Load saved token
        val prefs = getSharedPreferences("agent_prefs", MODE_PRIVATE)
        etNgrokToken.setText(prefs.getString("ngrok_token", ""))

        btnStart.setOnClickListener {
            val token = etNgrokToken.text.toString().trim()
            if (token.isEmpty()) {
                tvStatus.text = "Please enter your ngrok authtoken first!\nGet it free at ngrok.com"
                return@setOnClickListener
            }
            // Save token
            prefs.edit().putString("ngrok_token", token).apply()
            tvStatus.text = "Requesting permissions..."
            permLauncher.launch(requiredPermissions)
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, AgentService::class.java))
            tvStatus.text = "Agent stopped"
            tvUrl.text = ""
        }

        // Show if already running
        if (AgentService.isRunning) {
            tvStatus.text = "Agent already running"
            tvUrl.text = if (AgentService.publicUrl.isNotEmpty())
                "Dashboard URL:\n${AgentService.publicUrl}" else "Getting tunnel URL..."
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("NGROK_URL_READY")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(urlReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(urlReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(urlReceiver) } catch (e: Exception) {}
    }

    private fun doStartService() {
        tvStatus.text = "Starting agent & tunnel..."
        tvUrl.text = "Getting ngrok URL — please wait ~10 seconds..."
        ContextCompat.startForegroundService(this, Intent(this, AgentService::class.java))
    }
}
