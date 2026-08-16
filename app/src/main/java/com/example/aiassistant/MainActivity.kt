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
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvTerminalLog: TextView
    private lateinit var scrollTerminal: ScrollView
    private lateinit var tvBatteryStat: TextView
    private lateinit var btnTogglePersona: Button
    private lateinit var btnLangSwitch: Button
    private lateinit var btnFollowUpToggle: Button
    private lateinit var btnStartService: Button
    private lateinit var btnPermissions: Button
    private lateinit var arcReactorView: ArcReactorHudView

    private var activePersona = AssistantPersona.JARVIS
    private var isFollowUpEnabled = true

    private val supportedLanguages = arrayOf(
        "English", "Hindi", "Spanish", "French", "German", "Japanese"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvTerminalLog = findViewById(R.id.tvTerminalLog)
        scrollTerminal = findViewById(R.id.scrollTerminal)
        tvBatteryStat = findViewById(R.id.tvBatteryStat)
        btnTogglePersona = findViewById(R.id.btnTogglePersona)
        btnLangSwitch = findViewById(R.id.btnLangSwitch)
        btnFollowUpToggle = findViewById(R.id.btnFollowUpToggle)
        btnStartService = findViewById(R.id.btnStartService)
        btnPermissions = findViewById(R.id.btnPermissions)
        arcReactorView = findViewById(R.id.arcReactorView)

        val prefs = getSharedPreferences("AssistantPrefs", Context.MODE_PRIVATE)
        val currentLang = prefs.getString("MESSAGE_LANGUAGE", "English") ?: "English"
        isFollowUpEnabled = prefs.getBoolean("FOLLOW_UP_CONVERSATION", true)

        btnLangSwitch.text = "LANG: ${currentLang.uppercase()}"
        updateFollowUpButtonUI()
        updateTelemetryReadout()
        checkAndRequestSystemPermissions()

        // Switch Persona (JARVIS <-> FRIDAY)
        btnTogglePersona.setOnClickListener {
            activePersona = if (activePersona == AssistantPersona.JARVIS) {
                btnTogglePersona.text = "FRIDAY"
                btnTogglePersona.setTextColor(Color.parseColor("#FF5722"))
                btnTogglePersona.setBackgroundColor(Color.parseColor("#381308"))
                arcReactorView.setPersona(AssistantPersona.FRIDAY)
                logToTerminal("Active persona switched to F.R.I.D.A.Y.")
                AssistantPersona.FRIDAY
            } else {
                btnTogglePersona.text = "JARVIS"
                btnTogglePersona.setTextColor(Color.parseColor("#00E5FF"))
                btnTogglePersona.setBackgroundColor(Color.parseColor("#132B45"))
                arcReactorView.setPersona(AssistantPersona.JARVIS)
                logToTerminal("Active persona switched to J.A.R.V.I.S.")
                AssistantPersona.JARVIS
            }
        }

        // Multilingual Voicemail/SMS Translation Selector
        btnLangSwitch.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Select Translation Language")
                .setItems(supportedLanguages) { _, which ->
                    val chosen = supportedLanguages[which]
                    prefs.edit().putString("MESSAGE_LANGUAGE", chosen).apply()
                    btnLangSwitch.text = "LANG: ${chosen.uppercase()}"
                    logToTerminal("Voicemail/SMS output language updated to: $chosen")
                }
                .show()
        }

        // Follow-Up Conversation Mode Toggle
        btnFollowUpToggle.setOnClickListener {
            isFollowUpEnabled = !isFollowUpEnabled
            prefs.edit().putBoolean("FOLLOW_UP_CONVERSATION", isFollowUpEnabled).apply()
            updateFollowUpButtonUI()
            logToTerminal("Follow-up dialogue mode: ${if (isFollowUpEnabled) "ACTIVE" else "MUTED"}")
        }

        // Start Core Service
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

        // Permissions Verification
        btnPermissions.setOnClickListener {
            validateAndRequestPermissions()
        }

        // Tap Arc Reactor Core
        arcReactorView.setOnClickListener {
            logToTerminal("Manual HUD core trigger engaged.")
            val voiceManager = VoiceManager(this)
            voiceManager.speak("Systems operational, sir. Awaiting orders.", activePersona)
        }
    }

    private fun updateFollowUpButtonUI() {
        if (isFollowUpEnabled) {
            btnFollowUpToggle.text = "FOLLOW-UP: ON"
            btnFollowUpToggle.setTextColor(Color.parseColor("#00E676"))
        } else {
            btnFollowUpToggle.text = "FOLLOW-UP: OFF"
            btnFollowUpToggle.setTextColor(Color.parseColor("#EF5350"))
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
