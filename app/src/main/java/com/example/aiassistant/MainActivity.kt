package com.example.aiassistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvTerminalLog: TextView
    private lateinit var scrollTerminal: ScrollView
    private lateinit var tvBatteryStat: TextView
    private lateinit var btnTogglePersona: Button
    private lateinit var btnStartService: Button
    private lateinit var btnPermissions: Button
    private lateinit var arcReactorContainer: FrameLayout

    private var activePersona = AssistantPersona.JARVIS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvTerminalLog = findViewById(R.id.tvTerminalLog)
        scrollTerminal = findViewById(R.id.scrollTerminal)
        tvBatteryStat = findViewById(R.id.tvBatteryStat)
        btnTogglePersona = findViewById(R.id.btnTogglePersona)
        btnStartService = findViewById(R.id.btnStartService)
        btnPermissions = findViewById(R.id.btnPermissions)
        arcReactorContainer = findViewById(R.id.arcReactorContainer)

        updateTelemetryReadout()
        checkAndRequestSystemPermissions()

        btnTogglePersona.setOnClickListener {
            activePersona = if (activePersona == AssistantPersona.JARVIS) {
                btnTogglePersona.text = "MODE: FRIDAY"
                btnTogglePersona.setTextColor(Color.parseColor("#FF5722"))
                btnTogglePersona.setBackgroundColor(Color.parseColor("#3E1C12"))
                logToTerminal("Switched primary persona to F.R.I.D.A.Y.")
                AssistantPersona.FRIDAY
            } else {
                btnTogglePersona.text = "MODE: JARVIS"
                btnTogglePersona.setTextColor(Color.parseColor("#00E5FF"))
                btnTogglePersona.setBackgroundColor(Color.parseColor("#10253F"))
                logToTerminal("Switched primary persona to J.A.R.V.I.S.")
                AssistantPersona.JARVIS
            }
        }

        btnStartService.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                val serviceIntent = Intent(this, AssistantForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                logToTerminal("Foreground continuous core activated.")
            } else {
                Toast.makeText(this, "Microphone permission required first.", Toast.LENGTH_SHORT).show()
                checkAndRequestSystemPermissions()
            }
        }

        btnPermissions.setOnClickListener {
            validateAndRequestPermissions()
        }

        arcReactorContainer.setOnClickListener {
            logToTerminal("Manual voice trigger engaged.")
            val voiceManager = VoiceManager(this)
            voiceManager.speak("Systems operational, sir. Awaiting orders.", activePersona)
        }
    }

    private fun updateTelemetryReadout() {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = (level * 100 / scale.toFloat()).toInt()
        tvBatteryStat.text = "PWR: $batteryPct%"
    }

    private fun logToTerminal(message: String) {
        val currentText = tvTerminalLog.text.toString()
        val newText = "$currentText\n[${System.currentTimeMillis() % 100000}] $message"
        tvTerminalLog.text = newText
        scrollTerminal.post { scrollTerminal.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun checkAndRequestSystemPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        }
    }

    private fun validateAndRequestPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            logToTerminal("Overlay permission requested.")
        } else {
            logToTerminal("All system permissions validated.")
        }
    }
}
