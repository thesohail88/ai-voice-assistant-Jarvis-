package com.example.aiassistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private lateinit var languageManager: ContactLanguageManager
    private val PICK_CONTACT_REQUEST = 1001
    private val PERMISSION_REQUEST_CODE = 1002

    private var selectedContactName: String = ""
    private var selectedContactNumber: String = ""

    private lateinit var contactCardContent: LinearLayout
    private lateinit var contactDisplayTv: TextView
    private lateinit var rulesContainer: LinearLayout
    private lateinit var languageSpinner: Spinner
    private lateinit var statusBadge: TextView

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

        // Root Background
        val rootScrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#090D16")) // Ultra-deep Obsidian Navy
            isFillViewport = true
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(32), dpToPx(20), dpToPx(32))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Top Header Section with Glowing Subtitle
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val titleTv = TextView(this).apply {
            text = "JARVIS CORE // AI"
            textSize = 24f
            setTextColor(Color.parseColor("#38BDF8")) // Neon Cyan
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            letterSpacing = 0.08f
        }
        headerLayout.addView(titleTv)

        val subTitleTv = TextView(this).apply {
            text = "Multimodal Autonomous Voicemail & Device Engine"
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, dpToPx(4), 0, dpToPx(20))
        }
        headerLayout.addView(subTitleTv)
        mainLayout.addView(headerLayout)

        // --- 1. Service Status & Quick Control Card ---
        val statusCard = createGlassCard("#00E5FF")
        val statusCardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(18), dpToPx(18), dpToPx(18), dpToPx(18))
        }

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val pulseIcon = View(this).apply {
            background = createCircleDrawable("#00E676") // Neon Green Active Dot
        }
        statusRow.addView(pulseIcon, LinearLayout.LayoutParams(dpToPx(10), dpToPx(10)).apply { marginEnd = dpToPx(10) })

        val statusTitle = TextView(this).apply {
            text = "VOICE ENGINE STATUS"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#94A3B8"))
            letterSpacing = 0.05f
        }
        statusRow.addView(statusTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        statusBadge = TextView(this).apply {
            text = "ONLINE"
            textSize = 11f
            setTextColor(Color.parseColor("#00E676"))
            typeface = Typeface.DEFAULT_BOLD
            background = createRoundedDrawable("#052E16", "#15803D", 6)
            setPadding(dpToPx(8), dpToPx(3), dpToPx(8), dpToPx(3))
        }
        statusRow.addView(statusBadge)
        statusCardLayout.addView(statusRow)

        addSpacer(statusCardLayout, 16)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val startBtn = createNeonButton("▶ START SERVICE", "#0284C7", "#38BDF8") {
            startAssistantService()
        }
        val permBtn = createNeonButton("🔒 PERMISSIONS", "#1E293B", "#475569") {
            requestAppPermissions()
        }

        btnRow.addView(startBtn, LinearLayout.LayoutParams(0, dpToPx(48), 1.2f).apply { marginEnd = dpToPx(10) })
        btnRow.addView(permBtn, LinearLayout.LayoutParams(0, dpToPx(48), 1f))
        statusCardLayout.addView(btnRow)
        statusCard.addView(statusCardLayout)
        mainLayout.addView(statusCard)

        addSpacer(mainLayout, 24)

        // --- 2. Contact Routing Section ---
        val sectionHeader = TextView(this).apply {
            text = "INTELLIGENT VOICEMAIL ROUTING"
            textSize = 13f
            setTextColor(Color.parseColor("#38BDF8"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
        }
        mainLayout.addView(sectionHeader)

        addSpacer(mainLayout, 10)

        val configCard = createGlassCard("#1E293B")
        val configLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(18), dpToPx(18), dpToPx(18), dpToPx(18))
        }

        val pickContactBtn = createNeonButton("👤 SELECT PHONE CONTACT", "#111827", "#38BDF8") {
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            startActivityForResult(intent, PICK_CONTACT_REQUEST)
        }
        configLayout.addView(pickContactBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48)))

        contactDisplayTv = TextView(this).apply {
            text = "No contact chosen. Default language will apply."
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(dpToPx(4), dpToPx(10), dpToPx(4), dpToPx(12))
        }
        configLayout.addView(contactDisplayTv)

        val langLabel = TextView(this).apply {
            text = "Assigned Voicemail Language:"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
        }
        configLayout.addView(langLabel)

        languageSpinner = Spinner(this).apply {
            val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, languages.map { it.first })
            this.adapter = adapter
            background = createRoundedDrawable("#111827", "#334155", 8)
            setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10))
        }
        configLayout.addView(languageSpinner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(50)).apply { topMargin = dpToPx(6) })

        addSpacer(configLayout, 16)

        val saveRuleBtn = createNeonButton("⚡ DEPLOY CONTACT RULE", "#2563EB", "#60A5FA") {
            if (selectedContactNumber.isNotBlank()) {
                val selectedLang = languages[languageSpinner.selectedItemPosition]
                languageManager.saveContactRule(
                    selectedContactNumber,
                    if (selectedContactName.isBlank()) selectedContactNumber else selectedContactName,
                    selectedLang.first.replace("🌐 ", "").replace("🇮🇳 ", "").replace("🇪🇸 ", "").replace("🇫🇷 ", "").replace("🇩🇪 ", "").replace("🇸🇦 ", "").replace("🇨🇳 ", ""),
                    selectedLang.second
                )
                Toast.makeText(this, "Rule deployed for $selectedContactName", Toast.LENGTH_SHORT).show()
                selectedContactName = ""
                selectedContactNumber = ""
                contactDisplayTv.text = "No contact chosen. Default language will apply."
                contactDisplayTv.setTextColor(Color.parseColor("#64748B"))
                refreshRulesList()
            } else {
                Toast.makeText(this, "Select a contact from phone first", Toast.LENGTH_SHORT).show()
            }
        }
        configLayout.addView(saveRuleBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48)))

        configCard.addView(configLayout)
        mainLayout.addView(configCard)

        addSpacer(mainLayout, 28)

        // --- 3. Active Deployments / Configured Contacts List ---
        val rulesHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val rulesTitle = TextView(this).apply {
            text = "ACTIVE PROTOCOLS"
            textSize = 13f
            setTextColor(Color.parseColor("#F8FAFC"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
        }
        rulesHeaderRow.addView(rulesTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        mainLayout.addView(rulesHeaderRow)
        addSpacer(mainLayout, 12)

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

        if (rules.isEmpty()) {
            val emptyCard = createGlassCard("#1E293B")
            val emptyLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dpToPx(16), dpToPx(24), dpToPx(16), dpToPx(24))
            }
            val emptyTv = TextView(this).apply {
                text = "No active contact routing rules configured.\nAll rejected calls automatically respond in English."
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#475569"))
                setLineSpacing(dpToPx(4).toFloat(), 1f)
            }
            emptyLayout.addView(emptyTv)
            emptyCard.addView(emptyLayout)
            rulesContainer.addView(emptyCard)
            return
        }

        for (rule in rules) {
            val ruleCard = createGlassCard("#0284C7")
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
                textSize = 15f
                setTextColor(Color.parseColor("#F8FAFC"))
                typeface = Typeface.DEFAULT_BOLD
            }
            val detailsTv = TextView(this).apply {
                text = "${rule.number}  •  ${rule.languageName}"
                textSize = 12f
                setTextColor(Color.parseColor("#38BDF8"))
                setPadding(0, dpToPx(2), 0, 0)
            }

            textInfo.addView(nameTv)
            textInfo.addView(detailsTv)
            row.addView(textInfo)

            val deleteBtn = Button(this).apply {
                text = "✕"
                textSize = 14f
                setTextColor(Color.parseColor("#EF4444"))
                typeface = Typeface.DEFAULT_BOLD
                background = createRoundedDrawable("#450A0A", "#991B1B", 8)
                setOnClickListener {
                    languageManager.removeContactRule(rule.number)
                    refreshRulesList()
                }
            }
            row.addView(deleteBtn, LinearLayout.LayoutParams(dpToPx(42), dpToPx(40)))
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
                        contactDisplayTv.setTextColor(Color.parseColor("#38BDF8"))
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
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.CALL_PHONE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
    }

    private fun createGlassCard(borderColor: String): FrameLayout {
        return FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            background = createRoundedDrawable("#0F172A", borderColor, 14)
            elevation = dpToPx(4).toFloat()
        }
    }

    private fun createNeonButton(label: String, bgColor: String, strokeColor: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            
            val normalBg = createRoundedDrawable(bgColor, strokeColor, 10)
            val rippleColor = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
            background = RippleDrawable(rippleColor, normalBg, null)
            
            setOnClickListener { onClick() }
        }
    }

    private fun createRoundedDrawable(fillColor: String, strokeColor: String, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(radiusDp).toFloat()
            setColor(Color.parseColor(fillColor))
            setStroke(dpToPx(1), Color.parseColor(strokeColor))
        }
    }

    private fun createCircleDrawable(fillColor: String): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(fillColor))
        }
    }

    private fun addSpacer(parent: LinearLayout, dp: Int) {
        val space = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(dp))
        }
        parent.addView(space)
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
