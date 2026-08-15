package com.example.aiassistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat

class MainActivity : Activity() {

    private lateinit var languageManager: ContactLanguageManager
    private val PICK_CONTACT_REQUEST = 1001
    private val PERMISSION_REQUEST_CODE = 1002

    private var selectedContactName: String = ""
    private var selectedContactNumber: String = ""

    private lateinit var contactDisplayTv: TextView
    private lateinit var rulesContainer: LinearLayout
    private lateinit var languageSpinner: Spinner
    private lateinit var voicemailSwitch: Switch
    private lateinit var toggleStatusLabel: TextView
    private lateinit var activeRulesBadge: TextView

    private val languages = listOf(
        Pair("🌐 English (Default)", "en"),
        Pair("🇮🇳 Hindi", "hi"),
        Pair("🇪🇸 Spanish", "es"),
        Pair("🇫🇷 French", "fr"),
        Pair("🇩🇪 German", "de"),
        Pair("🇸🇦 Arabic", "ar"),
        Pair("🇨🇳 Mandarin", "zh")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        languageManager = ContactLanguageManager(this)

        val rootScrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#050811"),
                    Color.parseColor("#0A0F1D"),
                    Color.parseColor("#04060A")
                )
            )
            isFillViewport = true
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(36), dpToPx(20), dpToPx(36))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // HUD Header
        val hudHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dpToPx(24))
        }

        val arcReactorLogo = View(this).apply {
            background = createArcReactorBadge()
        }
        hudHeader.addView(arcReactorLogo, LinearLayout.LayoutParams(dpToPx(44), dpToPx(44)).apply { marginEnd = dpToPx(14) })

        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleTv = TextView(this).apply {
            text = "JARVIS // PROTOCOL"
            textSize = 22f
            setTextColor(Color.parseColor("#00F0FF"))
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            letterSpacing = 0.08f
        }
        titleColumn.addView(titleTv)

        val subTitleTv = TextView(this).apply {
            text = "AUTONOMOUS MULTIMODAL CORE"
            textSize = 10f
            setTextColor(Color.parseColor("#64748B"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.15f
        }
        titleColumn.addView(subTitleTv)
        hudHeader.addView(titleColumn)
        mainLayout.addView(hudHeader)

        // 1. Live Telemetry Panel
        val telemetryCard = createGlassHUDCard("#00F0FF", "#0A192F")
        val telemetryLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(18), dpToPx(18), dpToPx(18), dpToPx(18))
        }

        val statusHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val pulseBeacon = View(this).apply {
            background = createGlowDot("#10B981")
        }
        statusHeaderRow.addView(pulseBeacon, LinearLayout.LayoutParams(dpToPx(10), dpToPx(10)).apply { marginEnd = dpToPx(10) })

        val statusLabel = TextView(this).apply {
            text = "SYSTEM STATUS"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#94A3B8"))
            letterSpacing = 0.08f
        }
        statusHeaderRow.addView(statusLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val onlineBadge = TextView(this).apply {
            text = "LIVE // STANDBY"
            textSize = 10f
            setTextColor(Color.parseColor("#10B981"))
            typeface = Typeface.DEFAULT_BOLD
            background = createBadgeDrawable("#064E3B", "#10B981", 6)
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            letterSpacing = 0.05f
        }
        statusHeaderRow.addView(onlineBadge)
        telemetryLayout.addView(statusHeaderRow)

        addSpacer(telemetryLayout, 16)

        val metricsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        metricsRow.addView(createMetricChip("WAKE WORD", "JARVIS / FRIDAY", "#00F0FF"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dpToPx(6) })
        metricsRow.addView(createMetricChip("CORE LLM", "GROQ 70B TURBO", "#7C3AED"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dpToPx(6) })
        metricsRow.addView(createMetricChip("FAILOVER", "GEMINI FLASH", "#F59E0B"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        telemetryLayout.addView(metricsRow)

        addSpacer(telemetryLayout, 18)

        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val startBtn = createGlowingButton("⚡ INITIALIZE ENGINE", "#0284C7", "#00F0FF") {
            startAssistantService()
        }
        val permBtn = createGlowingButton("🛡️ SECURITY", "#1E293B", "#64748B") {
            requestAppPermissions()
        }

        btnRow.addView(startBtn, LinearLayout.LayoutParams(0, dpToPx(48), 1.3f).apply { marginEnd = dpToPx(10) })
        btnRow.addView(permBtn, LinearLayout.LayoutParams(0, dpToPx(48), 1f))
        telemetryLayout.addView(btnRow)

        telemetryCard.addView(telemetryLayout)
        mainLayout.addView(telemetryCard)

        addSpacer(mainLayout, 18)

        // 2. Switcher Card
        val switchCard = createGlassHUDCard("#38BDF8", "#0B1528")
        val switchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(18), dpToPx(16), dpToPx(18), dpToPx(16))
        }

        val switchInfoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val switchMainTitle = TextView(this).apply {
            text = "AI VOICEMAIL & SMS ENGINE"
            textSize = 13f
            setTextColor(Color.parseColor("#F8FAFC"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
        }
        switchInfoLayout.addView(switchMainTitle)

        toggleStatusLabel = TextView(this).apply {
            val isEnabled = languageManager.isVoicemailEnabled()
            text = if (isEnabled) "Auto-dispatches multilingual SMS on missed/busy calls" else "Engine disabled (Standard carrier call drop)"
            textSize = 11f
            setTextColor(if (isEnabled) Color.parseColor("#00F0FF") else Color.parseColor("#64748B"))
            setPadding(0, dpToPx(2), 0, 0)
        }
        switchInfoLayout.addView(toggleStatusLabel)
        switchLayout.addView(switchInfoLayout)

        voicemailSwitch = Switch(this).apply {
            isChecked = languageManager.isVoicemailEnabled()
            thumbTintList = ColorStateList.valueOf(Color.parseColor("#00F0FF"))
            trackTintList = ColorStateList.valueOf(Color.parseColor("#0284C7"))
            setOnCheckedChangeListener { _, isChecked ->
                languageManager.setVoicemailEnabled(isChecked)
                toggleStatusLabel.text = if (isChecked) "Auto-dispatches multilingual SMS on missed/busy calls" else "Engine disabled (Standard carrier call drop)"
                toggleStatusLabel.setTextColor(if (isChecked) Color.parseColor("#00F0FF") else Color.parseColor("#64748B"))
                Toast.makeText(
                    this@MainActivity,
                    if (isChecked) "Voicemail Protocol Activated" else "Voicemail Protocol Deactivated",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        switchLayout.addView(voicemailSwitch)
        switchCard.addView(switchLayout)
        mainLayout.addView(switchCard)

        addSpacer(mainLayout, 24)

        // 3. Contact Voicemail Router Card
        val routingHeader = TextView(this).apply {
            text = "TARGET ROUTING RULES"
            textSize = 12f
            setTextColor(Color.parseColor("#00F0FF"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f
        }
        mainLayout.addView(routingHeader)

        addSpacer(mainLayout, 10)

        val configCard = createGlassHUDCard("#7C3AED", "#120D26")
        val configLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(18), dpToPx(18), dpToPx(18), dpToPx(18))
        }

        val pickContactBtn = createGlowingButton("👤 CHOOSE FROM PHONE DIRECTORY", "#181433", "#7C3AED") {
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            startActivityForResult(intent, PICK_CONTACT_REQUEST)
        }
        configLayout.addView(pickContactBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(46)))

        contactDisplayTv = TextView(this).apply {
            text = "No contact target locked."
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(12))
        }
        configLayout.addView(contactDisplayTv)

        val langLabel = TextView(this).apply {
            text = "Assigned Response Dialect:"
            textSize = 11f
            setTextColor(Color.parseColor("#94A3B8"))
            typeface = Typeface.DEFAULT_BOLD
        }
        configLayout.addView(langLabel)

        languageSpinner = Spinner(this).apply {
            val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, languages.map { it.first })
            this.adapter = adapter
            background = createBadgeDrawable("#1E1B4B", "#4338CA", 8)
            setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10))
        }
        configLayout.addView(languageSpinner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48)).apply { topMargin = dpToPx(6) })

        addSpacer(configLayout, 16)

        val saveRuleBtn = createGlowingButton("DEPLOY CONTACT PROTOCOL", "#7C3AED", "#A78BFA") {
            if (selectedContactNumber.isNotBlank()) {
                val selectedLang = languages[languageSpinner.selectedItemPosition]
                languageManager.saveContactRule(
                    selectedContactNumber,
                    if (selectedContactName.isBlank()) selectedContactNumber else selectedContactName,
                    selectedLang.first.replace("🌐 ", "").replace("🇮🇳 ", "").replace("🇪🇸 ", "").replace("🇫🇷 ", "").replace("🇩🇪 ", "").replace("🇸🇦 ", "").replace("🇨🇳 ", ""),
                    selectedLang.second
                )
                Toast.makeText(this, "Protocol configured for $selectedContactName", Toast.LENGTH_SHORT).show()
                selectedContactName = ""
                selectedContactNumber = ""
                contactDisplayTv.text = "No contact target locked."
                contactDisplayTv.setTextColor(Color.parseColor("#64748B"))
                refreshRulesList()
            } else {
                Toast.makeText(this, "Select a contact from phone directory first", Toast.LENGTH_SHORT).show()
            }
        }
        configLayout.addView(saveRuleBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48)))

        configCard.addView(configLayout)
        mainLayout.addView(configCard)

        addSpacer(mainLayout, 26)

        // 4. Active Protocols List
        val rulesHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val rulesTitle = TextView(this).apply {
            text = "ACTIVE PROTOCOLS"
            textSize = 12f
            setTextColor(Color.parseColor("#F8FAFC"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f
        }
        rulesHeaderRow.addView(rulesTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        activeRulesBadge = TextView(this).apply {
            text = "0 RULES"
            textSize = 10f
            setTextColor(Color.parseColor("#00F0FF"))
            typeface = Typeface.DEFAULT_BOLD
            background = createBadgeDrawable("#082F49", "#00F0FF", 4)
            setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2))
        }
        rulesHeaderRow.addView(activeRulesBadge)
        mainLayout.addView(rulesHeaderRow)

        addSpacer(mainLayout, 10)

        rulesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        mainLayout.addView(rulesContainer)

        rootScrollView.addView(mainLayout)
        setContentView(rootScrollView)

        refreshRulesList()
    }

    private fun refreshRulesList() {
        rulesContainer.removeAllViews()
        val rules = languageManager.getAllRules()
        activeRulesBadge.text = "${rules.size} RULES"

        if (rules.isEmpty()) {
            val emptyCard = createGlassHUDCard("#1E293B", "#0A0E1A")
            val emptyLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dpToPx(16), dpToPx(24), dpToPx(16), dpToPx(24))
            }
            val emptyTv = TextView(this).apply {
                text = "No custom target rules configured.\nUnanswered calls automatically respond in English."
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#475569"))
                setLineSpacing(dpToPx(3).toFloat(), 1f)
            }
            emptyLayout.addView(emptyTv)
            emptyCard.addView(emptyLayout)
            rulesContainer.addView(emptyCard)
            return
        }

        for (rule in rules) {
            val ruleCard = createGlassHUDCard("#0284C7", "#0D1829")
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
                gravity = Gravity.CENTER_VERTICAL
            }

            val textInfo = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val nameTv = TextView(this).apply {
                text = rule.name
                textSize = 14f
                setTextColor(Color.parseColor("#F8FAFC"))
                typeface = Typeface.DEFAULT_BOLD
            }
            val detailsTv = TextView(this).apply {
                text = "${rule.number}  •  ${rule.languageName}"
                textSize = 11f
                setTextColor(Color.parseColor("#00F0FF"))
                setPadding(0, dpToPx(2), 0, 0)
            }

            textInfo.addView(nameTv)
            textInfo.addView(detailsTv)
            row.addView(textInfo)

            val deleteBtn = Button(this).apply {
                text = "✕"
                textSize = 13f
                setTextColor(Color.parseColor("#EF4444"))
                typeface = Typeface.DEFAULT_BOLD
                background = createBadgeDrawable("#3A0B0B", "#DC2626", 8)
                setOnClickListener {
                    languageManager.removeContactRule(rule.number)
                    refreshRulesList()
                }
            }
            row.addView(deleteBtn, LinearLayout.LayoutParams(dpToPx(40), dpToPx(38)))
            ruleCard.addView(row)
            rulesContainer.addView(ruleCard)
            addSpacer(rulesContainer, 10)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_CONTACT_REQUEST && resultCode == RESULT_OK) {
            val contactUri: Uri? = data?.data
            if (contactUri != null) {
                val cursor = contentResolver.query(
                    contactUri,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null, null, null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        selectedContactName = it.getString(0) ?: "Contact"
                        selectedContactNumber = it.getString(1) ?: ""
                        contactDisplayTv.text = "Target Locked: $selectedContactName ($selectedContactNumber)"
                        contactDisplayTv.setTextColor(Color.parseColor("#00F0FF"))
                    }
                }
            }
        }
    }

    private fun startAssistantService() {
        val serviceIntent = Intent(this, AssistantForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Toast.makeText(this, "JARVIS Core Initialized", Toast.LENGTH_SHORT).show()
    }

    private fun requestAppPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.CALL_PHONE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
    }

    private fun 
