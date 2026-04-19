package com.example.agentapp

import android.Manifest
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

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
                tvUrl.text = "Dashboard URL:\n$url"
                tvStatus.text = "Agent running! Connect from anywhere."
            }
        }
    }

    // Only request permissions that are not SMS (SMS handled separately)
    private val requiredPermissions = buildList {
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Start service regardless — SMS is optional
        doStartService()
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

        // Show current status if already running
        if (AgentService.isRunning) {
            tvStatus.text = "Agent already running"
            tvUrl.text = if (AgentService.publicUrl.isNotEmpty())
                "Dashboard URL:\n${AgentService.publicUrl}"
            else
                "Getting tunnel URL..."
        }

        btnStart.setOnClickListener {
            val token = etNgrokToken.text.toString().trim()
            if (token.isEmpty()) {
                tvStatus.text = "Please enter your ngrok authtoken first!\nGet it free at ngrok.com/signup"
                return@setOnClickListener
            }
            // Save token
            prefs.edit().putString("ngrok_token", token).apply()
            tvStatus.text = "Starting..."
            permLauncher.launch(requiredPermissions)
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, AgentService::class.java))
            tvStatus.text = "Agent stopped"
            tvUrl.text = ""
        }

        // Button to manually open app permissions settings
        findViewById<Button>(R.id.btnPermissions).setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check if service is running and update URL
        if (AgentService.isRunning && AgentService.publicUrl.isNotEmpty()) {
            tvStatus.text = "Agent running!"
            tvUrl.text = "Dashboard URL:\n${AgentService.publicUrl}"
        }

        val filter = IntentFilter("NGROK_URL_READY")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(urlReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(urlReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(urlReceiver) } catch (e: Exception) {}
    }

    private fun doStartService() {
        tvStatus.text = "Agent starting... getting ngrok URL (10-20 sec)"
        tvUrl.text = "Please wait..."
        ContextCompat.startForegroundService(this, Intent(this, AgentService::class.java))
    }
}
