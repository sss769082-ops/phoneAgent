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

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvUrl: TextView
    private lateinit var etNgrokToken: EditText
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private val urlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val display = intent.getStringExtra("display") ?: intent.getStringExtra("url") ?: return
            runOnUiThread {
                tvUrl.text = display
                if (!display.startsWith("Error")) {
                    tvStatus.text = "Agent running!"
                } else {
                    tvStatus.text = display
                }
            }
        }
    }

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
    ) { _ -> doStartService() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus     = findViewById(R.id.tvStatus)
        tvUrl        = findViewById(R.id.tvUrl)
        etNgrokToken = findViewById(R.id.etNgrokToken)
        btnStart     = findViewById(R.id.btnStart)
        btnStop      = findViewById(R.id.btnStop)

        val prefs = getSharedPreferences("agent_prefs", MODE_PRIVATE)
        etNgrokToken.setText(prefs.getString("ngrok_token", ""))

        if (AgentService.isRunning) {
            tvStatus.text = "Agent running!"
            tvUrl.text = if (AgentService.publicUrl.isNotEmpty())
                AgentService.publicUrl else "Getting URL..."
        }

        btnStart.setOnClickListener {
            val token = etNgrokToken.text.toString().trim()
            prefs.edit().putString("ngrok_token", token).apply()
            tvStatus.text = "Starting agent..."
            tvUrl.text = "Please wait 10-15 seconds..."
            permLauncher.launch(requiredPermissions)
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, AgentService::class.java))
            tvStatus.text = "Agent stopped"
            tvUrl.text = ""
        }

        findViewById<Button>(R.id.btnPermissions).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (AgentService.isRunning && AgentService.publicUrl.isNotEmpty()) {
            tvStatus.text = "Agent running!"
            tvUrl.text = AgentService.publicUrl
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
        ContextCompat.startForegroundService(this, Intent(this, AgentService::class.java))
    }
}
