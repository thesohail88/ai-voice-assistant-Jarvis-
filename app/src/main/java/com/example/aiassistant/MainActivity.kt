package com.example.aiassistant

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var speakerVerifier: SpeakerVerifier
    private lateinit var languageManager: ContactLanguageManager

    // Supported languages: Display Name -> BCP-47 Tag
    private val languages = listOf(
        "English (US)" to "en-US",
        "English (UK)" to "en-GB",
        "English (India)" to "en-IN",
        "Hindi (India)" to "hi-IN",
        "Spanish (Spain)" to "es-ES",
        "French (France)" to "fr-FR",
        "German (Germany)" to "de-DE",
        "Japanese (Japan)" to "ja-JP",
        "Arabic (Saudi Arabia)" to "ar-SA"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speakerVerifier = SpeakerVerifier(this)
        languageManager = ContactLanguageManager(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 50, 40, 40)
        }

        // Section 1: Contact Language Configuration
        val titleText = TextView(this).apply {
            text = "Contact Language Settings"
            textSize = 18f
            setPadding(0, 0, 0, 20)
        }

        val inputContact = EditText(this).apply {
            hint = "Contact Phone Number or Name"
        }

        val spinnerLanguage = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                languages.map { it.first }
            )
        }

        val savedListText = TextView(this).apply {
            textSize = 14f
            setPadding(0, 20, 0, 20)
            text = formatSavedContacts()
        }

        val btnSaveContact = Button(this).apply {
            text = "Save Language for Contact"
            setOnClickListener {
                val contact = inputContact.text.toString().trim()
                if (contact.isNotEmpty()) {
                    val selectedCode = languages[spinnerLanguage.selectedItemPosition].second
                    languageManager.setLanguageForContact(contact, selectedCode)
                    Toast.makeText(context, "Saved $contact -> ${languages[spinnerLanguage.selectedItemPosition].first}", Toast.LENGTH_SHORT).show()
                    inputContact.text.clear()
                    savedListText.text = formatSavedContacts()
                } else {
                    Toast.makeText(context, "Please enter a contact number", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Section 2: Service Controls
        val btnStart = Button(this).apply {
            text = "Start Background Assistant"
            setOnClickListener {
                val serviceIntent = Intent(this@MainActivity, AssistantForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            }
        }

        layout.addView(titleText)
        layout.addView(inputContact)
        layout.addView(spinnerLanguage)
        layout.addView(btnSaveContact)
        layout.addView(savedListText)
        layout.addView(btnStart)

        val scrollView = ScrollView(this)
        scrollView.addView(layout)
        setContentView(scrollView)

        requestRequiredPermissions()
    }

    private fun formatSavedContacts(): String {
        val all = languageManager.getAllCustomContacts()
        if (all.isEmpty()) return "No custom contact rules saved yet (Default: English)."
        val builder = StringBuilder("Configured Contact Rules:\n")
        all.forEach { (contact, lang) ->
            builder.append("• $contact: $lang\n")
        }
        return builder.toString()
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissions.add(Manifest.permission.ANSWER_PHONE_CALLS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
    }
}
