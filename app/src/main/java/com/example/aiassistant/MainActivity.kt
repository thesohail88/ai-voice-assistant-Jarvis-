package com.example.aiassistant

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var tvChatFeed: TextView
    private lateinit var scrollChat: ScrollView
    private lateinit var etChatInput: EditText
    private lateinit var btnSendChat: Button
    private lateinit var btnTogglePersona: Button
    private lateinit var btnDefaultLangSwitch: Button
    private lateinit var btnManageContactLangs: Button
    private lateinit var btnTestVoiceSample: Button
    private lateinit var btnStartService: Button
    private lateinit var btnPermissions: Button
    private lateinit var arcReactorView: ArcReactorHudView
    private lateinit var contactManager: ContactManager
    private lateinit var aiRouter: UnifiedAiRouter
    private lateinit var voiceManager: VoiceManager

    private var activePersona = AssistantPersona.JARVIS
    private val activityScope = CoroutineScope(Dispatchers.Main)

    private val supportedLanguages = arrayOf(
        "English", "Hindi", "Spanish", "French", "German", "Japanese", "Russian", "Arabic"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val keyConfig = ApiKeyConfig(
            groqKey = BuildConfig.GROQ_KEY,
            geminiKey = BuildConfig.GEMINI_KEY,
            openRouterKey = BuildConfig.OPENROUTER_KEY
        )
        aiRouter = UnifiedAiRouter(keyConfig, this)
        voiceManager = VoiceManager(this)
        contactManager = ContactManager(this)

        tvChatFeed = findViewById(R.id.tvChatFeed)
        scrollChat = findViewById(R.id.scrollChat)
        etChatInput = findViewById(R.id.etChatInput)
        btnSendChat = findViewById(R.id.btnSendChat)
        btnTogglePersona = findViewById(R.id.btnTogglePersona)
        btnDefaultLangSwitch = findViewById(R.id.btnDefaultLangSwitch)
        btnManageContactLangs = findViewById(R.id.btnManageContactLangs)
        btnTestVoiceSample = findViewById(R.id.btnTestVoiceSample)
        btnStartService = findViewById(R.id.btnStartService)
        btnPermissions = findViewById(R.id.btnPermissions)
        arcReactorView = findViewById(R.id.arcReactorView)

        val prefs = getSharedPreferences("AssistantPrefs", Context.MODE_PRIVATE)
        val defaultLang = prefs.getString("MESSAGE_LANGUAGE", "English") ?: "English"

        btnDefaultLangSwitch.text = "DEF: ${defaultLang.substring(0, 3).uppercase()}"
        updateContactRulesButtonLabel()
        checkAndRequestSystemPermissions()
        requestBatteryExemption()

        btnSendChat.setOnClickListener {
            val userText = etChatInput.text.toString().trim()
            if (userText.isNotBlank()) {
                etChatInput.text.clear()
                appendChatMessage("YOU: $userText", "#ECEFF1")
                activityScope.launch {
                    arcReactorView.setCoreState(true)
                    val response = aiRouter.processDirectTextPrompt(userText, activePersona)
                    arcReactorView.setCoreState(false)
                    appendChatMessage("[${activePersona.name}]: $response", if (activePersona == AssistantPersona.JARVIS) "#00E5FF" else "#FF5722")
                    voiceManager.speak(response, activePersona)
                }
            }
        }

        btnTogglePersona.setOnClickListener {
            activePersona = if (activePersona == AssistantPersona.JARVIS) {
                btnTogglePersona.text = "FRIDAY"
                btnTogglePersona.setTextColor(Color.parseColor("#FF5722"))
                btnTogglePersona.setBackgroundColor(Color.parseColor("#381308"))
                btnTestVoiceSample.setTextColor(Color.parseColor("#FF5722"))
                arcReactorView.setPersona(AssistantPersona.FRIDAY)
                appendChatMessage("[SYS]: Active persona changed to F.R.I.D.A.Y.", "#FF5722")
                AssistantPersona.FRIDAY
            } else {
                btnTogglePersona.text = "JARVIS"
                btnTogglePersona.setTextColor(Color.parseColor("#00E5FF"))
                btnTogglePersona.setBackgroundColor(Color.parseColor("#122C4A"))
                btnTestVoiceSample.setTextColor(Color.parseColor("#00E5FF"))
                arcReactorView.setPersona(AssistantPersona.JARVIS)
                appendChatMessage("[SYS]: Active persona changed to J.A.R.V.I.S.", "#00E5FF")
                AssistantPersona.JARVIS
            }
        }

        btnTestVoiceSample.setOnClickListener {
            appendChatMessage("[SYS]: Playing acoustic voice sample for ${activePersona.name}...", if (activePersona == AssistantPersona.JARVIS) "#00E5FF" else "#FF5722")
            voiceManager.playVoiceSample(activePersona)
        }

        btnDefaultLangSwitch.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Select Global Voicemail/SMS Translation Language")
                .setItems(supportedLanguages) { _, which ->
                    val chosen = supportedLanguages[which]
                    prefs.edit().putString("MESSAGE_LANGUAGE", chosen).apply()
                    btnDefaultLangSwitch.text = "DEF: ${chosen.substring(0, 3).uppercase()}"
                    appendChatMessage("[SYS]: Global voicemail language updated to $chosen", "#00E5FF")
                }
                .show()
        }

        btnManageContactLangs.setOnClickListener {
            showContactRuleManagerDialog()
        }

        btnStartService.setOnClickListener {
            startCoreService()
        }

        btnPermissions.setOnClickListener {
            validateAndRequestPermissions()
        }

        arcReactorView.setOnClickListener {
            appendChatMessage("[${activePersona.name}]: Systems operational, awaiting orders.", if (activePersona == AssistantPersona.JARVIS) "#00E5FF" else "#FF5722")
            voiceManager.speak("Systems operational, sir. Standing by.", activePersona)
        }
    }

    private fun appendChatMessage(message: String, hexColor: String) {
        val currentText = tvChatFeed.text.toString()
        val newText = "$currentText\n\n$message"
        tvChatFeed.text = newText
        scrollChat.post { scrollChat.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun startCoreService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            val serviceIntent = Intent(this, AssistantForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            appendChatMessage("[SYS]: 24/7 background listener activated.", "#00E676")
        } else {
            Toast.makeText(this, "Microphone permission required.", Toast.LENGTH_SHORT).show()
            checkAndRequestSystemPermissions()
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
            Toast.makeText(this, "No contacts found.", Toast.LENGTH_SHORT).show()
            return
        }

        val contactNames = contacts.map { "${it.name} (${contactManager.getLanguageForContact(it.name, "Default")})" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select Contact to Assign Translation Language")
            .setItems(contactNames) { _, which ->
                val selectedContact = contacts[which]
                showLanguageSelectorForContact(selectedContact.name)
            }
            .setNeutralButton("View Rules") { _, _ ->
                val rules = contactManager.getAllCustomContactRules()
                val text = if (rules.isEmpty()) "No custom contact rules assigned." else rules.entries.joinToString("\n") { "• ${it.key}: ${it.value}" }
                AlertDialog.Builder(this).setTitle("Active Contact Rules").setMessage(text).setPositiveButton("OK", null).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLanguageSelectorForContact(contactName: String) {
        val options = arrayOf("Default", *supportedLanguages, "Remove Rule")
        AlertDialog.Builder(this)
            .setTitle("Assign Language for $contactName")
            .setItems(options) { _, which ->
                val selection = options[which]
                if (selection == "Remove Rule" || selection == "Default") {
                    contactManager.removeContactRule(contactName)
                    appendChatMessage("[SYS]: Reset rule for $contactName to default", "#ECEFF1")
                } else {
                    contactManager.setLanguageForContact(contactName, selection)
                    appendChatMessage("[SYS]: Assigned $selection translation for $contactName", "#00E676")
                }
                updateContactRulesButtonLabel()
            }
            .show()
    }

    private fun updateContactRulesButtonLabel() {
        val count = contactManager.getAllCustomContactRules().size
        btnManageContactLangs.text = "RULES ($count)"
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryExemption() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            try {
                startActivity(intent)
            } catch (_: Exception) {}
        }
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
            appendChatMessage("[SYS]: Overlay permission requested.", "#FFD54F")
        } else {
            appendChatMessage("[SYS]: All permissions validated.", "#00E676")
        }
    }
}
