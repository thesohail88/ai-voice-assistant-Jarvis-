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
    private lateinit var tvFollowUpStat: TextView
    private lateinit var btnTogglePersona: Button
    private lateinit var btnDefaultLangSwitch: Button
    private lateinit var btnManageContactLangs: Button
    private lateinit var btnStartService: Button
    private lateinit var btnPermissions: Button
    private lateinit var arcReactorView: ArcReactorHudView
    private lateinit var contactManager: ContactManager

    private var activePersona = AssistantPersona.JARVIS
    private var isFollowUpEnabled = true

    private val supportedLanguages = arrayOf(
        "English", "Hindi", "Spanish", "French", "German", "Japanese", "Russian", "Arabic"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        contactManager = ContactManager(this)

        tvTerminalLog = findViewById(R.id.tvTerminalLog)
        scrollTerminal = findViewById(R.id.scrollTerminal)
        tvBatteryStat = findViewById(R.id.tvBatteryStat)
        tvFollowUpStat = findViewById(R.id.tvFollowUpStat)
        btnTogglePersona = findViewById(R.id.btnTogglePersona)
        btnDefaultLangSwitch = findViewById(R.id.btnDefaultLangSwitch)
        btnManageContactLangs = findViewById(R.id.btnManageContactLangs)
        btnStartService = findViewById(R.id.btnStartService)
        btnPermissions = findViewById(R.id.btnPermissions)
        arcReactorView = findViewById(R.id.arcReactorView)

        val prefs = getSharedPreferences("AssistantPrefs", Context.MODE_PRIVATE)
        val defaultLang = prefs.getString("MESSAGE_LANGUAGE", "English") ?: "English"
        isFollowUpEnabled = prefs.getBoolean("FOLLOW_UP_CONVERSATION", true)

        btnDefaultLangSwitch.text = "DEF: ${defaultLang.substring(0, 3).uppercase()}"
        updateContactRulesButtonLabel()
        updateTelemetryReadout()
        checkAndRequestSystemPermissions()

        // 1. Switch Persona (JARVIS <-> FRIDAY)
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

        // 2. Global Default Voicemail / SMS Translation Selector
        btnDefaultLangSwitch.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Select Default Voicemail/SMS Translation Language")
                .setItems(supportedLanguages) { _, which ->
                    val chosen = supportedLanguages[which]
                    prefs.edit().putString("MESSAGE_LANGUAGE", chosen).apply()
                    btnDefaultLangSwitch.text = "DEF: ${chosen.substring(0, 3).uppercase()}"
                    logToTerminal("Default global message language set to: $chosen")
                }
                .show()
        }

        // 3. Per-Contact Custom Language Rule Manager
        btnManageContactLangs.setOnClickListener {
            showContactRuleManagerDialog()
        }

        // 4. Toggle Follow-Up Conversation Mode
        tvFollowUpStat.setOnClickListener {
            isFollowUpEnabled = !isFollowUpEnabled
            prefs.edit().putBoolean("FOLLOW_UP_CONVERSATION", isFollowUpEnabled).apply()
            tvFollowUpStat.text = "FOLLOW-UP: ${if (isFollowUpEnabled) "ON" else "OFF"}"
            tvFollowUpStat.setTextColor(if (isFollowUpEnabled) Color.parseColor("#00E676") else Color.parseColor("#EF5350"))
            logToTerminal("Follow-up dialogue state: ${if (isFollowUpEnabled) "ENABLED" else "DISABLED"}")
        }

        // 5. Start / Restart Core Standby Foreground Service
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

        // 6. Permissions Verification
        btnPermissions.setOnClickListener {
            validateAndRequestPermissions()
        }

        // 7. Interactive Arc Core Tap
        arcReactorView.setOnClickListener {
            logToTerminal("Manual HUD core trigger engaged.")
            val voiceManager = VoiceManager(this)
            voiceManager.speak("Systems operational, sir. Awaiting orders.", activePersona)
        }
    }

    private fun showContactRuleManagerDialog() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), 102)
            Toast.makeText(this, "Phonebook permission requested.", Toast.LENGTH_SHORT).show()
            return
        }

        val contacts = contactManager.getAllDeviceContacts()
        if (contacts.isEmpty()) {
            Toast.makeText(this, "No contacts found or phonebook is empty.", Toast.LENGTH_SHORT).show()
            return
        }

        val contactNames = contacts.map { "${it.name} (${contactManager.getLanguageForContact(it.name, "Default")})" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select Contact to Assign Voicemail Language")
            .setItems(contactNames) { _, which ->
                val selectedContact = contacts[which]
                showLanguageSelectorForContact(selectedContact.name)
            }
            .setNeutralButton("View Active Rules") { _, _ ->
                showActiveRulesSummary()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLanguageSelectorForContact(contactName: String) {
        val options = arrayOf("Default", *supportedLanguages, "Remove Rule")
        AlertDialog.Builder(this)
            .setTitle("Assign Voicemail Language for $contactName")
            .setItems(options) { _, which ->
                val selection = options[which]
                if (selection == "Remove Rule" || selection == "Default") {
                    contactManager.removeContactRule(contactName)
                    logToTerminal("Reset translation for $contactName to default")
                } else {
                    contactManager.setLanguageForContact(contactName, selection)
                    logToTerminal("Assigned $selection translation for $contactName")
                }
                updateContactRulesButtonLabel()
            }
            .show()
    }

    private fun showActiveRulesSummary() {
        val rules = contactManager.getAllCustomContactRules()
        val text = if (rules.isEmpty()) "No custom contact rules assigned." else rules.entries.joinToString("\n") { "• ${it.key}: ${it.value}" }
        AlertDialog.Builder(this)
            .setTitle("Active Contact Voicemail Rules")
            .setMessage(text)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun updateContactRulesButtonLabel() {
        val count = contactManager.getAllCustomContactRules().size
        btnManageContactLangs.text = "RULES ($count)"
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
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE
        )
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
