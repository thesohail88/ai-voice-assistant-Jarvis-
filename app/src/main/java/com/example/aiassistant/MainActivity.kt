package com.example.aiassistant

import android.Manifest
import android.app.Activity
import android.content.Intent
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
import android.widget.*
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
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#050811"), Color.parseColor("#0A0F1D"), Color.parseColor("#04060A"))
            )
            isFillViewport = true
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(32), dp(20), dp(32))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        // HUD Header
        val hudHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(20))
        }
        val arcLogo = View(this).apply { background = createShape(GradientDrawable.OVAL, "#082F49", "#00F0FF", 2, 0) }
        hudHeader.addView(arcLogo, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) })

        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleCol.addView(TextView(this).apply {
            text = "JARVIS // PROTOCOL"
            textSize = 20f
            setTextColor(Color.parseColor("#00F0FF"))
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            letterSpacing = 0.08f
        })
        titleCol.addView(TextView(this).apply {
            text = "AUTONOMOUS MULTIMODAL CORE"
            textSize = 10f
            setTextColor(Color.parseColor("#64748B"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
        })
        hudHeader.addView(titleCol)
        mainLayout.addView(hudHeader)

        // Telemetry Card
        val telemetryCard = createGlassCard("#00F0FF", "#0A192F")
        val telemetryLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statusRow.addView(View(this).apply { background = createShape(GradientDrawable.OVAL, "#10B981", "#10B981", 0, 0) }, LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(8) })
        statusRow.addView(TextView(this).apply {
            text = "SYSTEM STATUS"
            textSize = 11f
            setTextColor(Color.parseColor("#94A3B8"))
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        statusRow.addView(TextView(this).apply {
            text = "LIVE // STANDBY"
            textSize = 10f
            setTextColor(Color.parseColor("#10B981"))
            typeface = Typeface.DEFAULT_BOLD
            background = createShape(GradientDrawable.RECTANGLE, "#064E3B", "#10B981", 1, 6)
            setPadding(dp(8), dp(3), dp(8), dp(3))
        })
        telemetryLayout.addView(statusRow)
        telemetryLayout.addView(createSpacer(14))

        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(createNeonBtn("⚡ INITIALIZE ENGINE", "#0284C7", "#00F0FF") { startAssistantService() }, LinearLayout.LayoutParams(0, dp(46), 1.3f).apply { marginEnd = dp(8) })
        btnRow.addView(createNeonBtn("🛡️ PERMISSIONS", "#1E293B", "#64748B") { requestAppPermissions() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        telemetryLayout.addView(btnRow)
        telemetryCard.addView(telemetryLayout)
        mainLayout.addView(telemetryCard)
        mainLayout.addView(createSpacer(16))

        // Voicemail & SMS Switch Card
        val switchCard = createGlassCard("#38BDF8", "#0B1528")
        val switchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val switchCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        switchCol.addView(TextView(this).apply {
            text = "AI VOICEMAIL & SMS ENGINE"
            textSize = 13f
            setTextColor(Color.parseColor("#F8FAFC"))
            typeface = Typeface.DEFAULT_BOLD
        })
        toggleStatusLabel = TextView(this).apply {
            val enabled = languageManager.isVoicemailEnabled()
            text = if (enabled) "Dispatches SMS on missed/busy calls" else "Voicemail engine disabled"
            textSize = 11f
            setTextColor(if (enabled) Color.parseColor("#00F0FF") else Color.parseColor("#64748B"))
        }
        switchCol.addView(toggleStatusLabel)
        switchLayout.addView(switchCol)

        val vSwitch = Switch(this).apply {
            isChecked = languageManager.isVoicemailEnabled()
            thumbTintList = ColorStateList.valueOf(Color.parseColor("#00F0FF"))
            trackTintList = ColorStateList.valueOf(Color.parseColor("#0284C7"))
            setOnCheckedChangeListener { _, isChecked ->
                languageManager.setVoicemailEnabled(isChecked)
                toggleStatusLabel.text = if (isChecked) "Dispatches SMS on missed/busy calls" else "Voicemail engine disabled"
                toggleStatusLabel.setTextColor(if (isChecked) Color.parseColor("#00F0FF") else Color.parseColor("#64748B"))
                Toast.makeText(this@MainActivity, if (isChecked) "Voicemail Active" else "Voicemail Disabled", Toast.LENGTH_SHORT).show()
            }
        }
        switchLayout.addView(vSwitch)
        switchCard.addView(switchLayout)
        mainLayout.addView(switchCard)
        mainLayout.addView(createSpacer(20))

        // Contact Router
        mainLayout.addView(TextView(this).apply {
            text = "TARGET ROUTING RULES"
            textSize = 12f
            setTextColor(Color.parseColor("#00F0FF"))
            typeface = Typeface.DEFAULT_BOLD
        })
        mainLayout.addView(createSpacer(8))

        val configCard = createGlassCard("#7C3AED", "#120D26")
        val configLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        configLayout.addView(createNeonBtn("👤 SELECT DIRECTORY CONTACT", "#181433", "#7C3AED") {
            startActivityForResult(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), PICK_CONTACT_REQUEST)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))

        contactDisplayTv = TextView(this).apply {
            text = "No contact target locked."
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(dp(4), dp(8), dp(4), dp(10))
        }
        configLayout.addView(contactDisplayTv)

        languageSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, languages.map { it.first })
            background = createShape(GradientDrawable.RECTANGLE, "#1E1B4B", "#4338CA", 1, 8)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        configLayout.addView(languageSpinner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)))
        configLayout.addView(createSpacer(12))

        configLayout.addView(createNeonBtn("DEPLOY CONTACT PROTOCOL", "#7C3AED", "#A78BFA") {
            if (selectedContactNumber.isNotBlank()) {
                val sel = languages[languageSpinner.selectedItemPosition]
                val cleanLang = sel.first.replace("[^a-zA-Z ()]".toRegex(), "").trim()
                languageManager.saveContactRule(selectedContactNumber, if (selectedContactName.isBlank()) selectedContactNumber else selectedContactName, cleanLang, sel.second)
                Toast.makeText(this, "Protocol configured for $selectedContactName", Toast.LENGTH_SHORT).show()
                selectedContactName = ""
                selectedContactNumber = ""
                contactDisplayTv.text = "No contact target locked."
                contactDisplayTv.setTextColor(Color.parseColor("#64748B"))
                refreshRulesList()
            } else {
                Toast.makeText(this, "Select a contact first", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))

        configCard.addView(configLayout)
        mainLayout.addView(configCard)
        mainLayout.addView(createSpacer(20))

        // Rules List
        val rulesHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        rulesHeaderRow.addView(TextView(this).apply {
            text = "ACTIVE PROTOCOLS"
            textSize = 12f
            setTextColor(Color.parseColor("#F8FAFC"))
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        activeRulesBadge = TextView(this).apply {
            text = "0 RULES"
            textSize = 10f
            setTextColor(Color.parseColor("#00F0FF"))
            typeface = Typeface.DEFAULT_BOLD
            background = createShape(GradientDrawable.RECTANGLE, "#082F49", "#00F0FF", 1, 4)
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }
        rulesHeaderRow.addView(activeRulesBadge)
        mainLayout.addView(rulesHeaderRow)
        mainLayout.addView(createSpacer(8))

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
            val emptyCard = createGlassCard("#1E293B", "#0A0E1A")
            val emptyTv = TextView(this).apply {
                text = "No custom target rules configured.\nUnanswered calls automatically respond in English."
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#475569"))
                setPadding(dp(16), dp(20), dp(16), dp(20))
            }
            emptyCard.addView(emptyTv)
            rulesContainer.addView(emptyCard)
            return
        }

        for (rule in rules) {
            val ruleCard = createGlassCard("#0284C7", "#0D1829")
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                gravity = Gravity.CENTER_VERTICAL
            }
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(this).apply { text = rule.name; textSize = 14f; setTextColor(Color.parseColor("#F8FAFC")); typeface = Typeface.DEFAULT_BOLD })
            textCol.addView(TextView(this).apply { text = "${rule.number} • ${rule.languageName}"; textSize = 11f; setTextColor(Color.parseColor("#00F0FF")) })
            row.addView(textCol)

            val delBtn = Button(this).apply {
                text = "✕"
                textSize = 13f
                setTextColor(Color.parseColor("#EF4444"))
                typeface = Typeface.DEFAULT_BOLD
                background = createShape(GradientDrawable.RECTANGLE, "#3A0B0B", "#DC2626", 1, 8)
                setOnClickListener { languageManager.removeContactRule(rule.number); refreshRulesList() }
            }
            row.addView(delBtn, LinearLayout.LayoutParams(dp(38), dp(36)))
            ruleCard.addView(row)
            rulesContainer.addView(ruleCard)
            rulesContainer.addView(createSpacer(8))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_CONTACT_REQUEST && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use {
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
        val intent = Intent(this, AssistantForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
    }

    private fun createGlassCard(glowColor: String, bgTint: String): FrameLayout {
        return FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            background = createShape(GradientDrawable.RECTANGLE, bgTint, glowColor, 1, 14)
            elevation = dp(4).toFloat()
        }
    }

    private fun createNeonBtn(label: String, bg: String, glow: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = RippleDrawable(ColorStateList.valueOf(Color.parseColor(glow)), createShape(GradientDrawable.RECTANGLE, bg, glow, 1, 10), null)
            setOnClickListener { onClick() }
        }
    }

    private fun createShape(shapeType: Int, fillColor: String, strokeColor: String, strokeWidthDp: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = shapeType
            if (radiusDp > 0) cornerRadius = dp(radiusDp).toFloat()
            setColor(Color.parseColor(fillColor))
            if (strokeWidthDp > 0) setStroke(dp(strokeWidthDp), Color.parseColor(strokeColor))
        }
    }

    private fun createSpacer(dpVal: Int): View {
        return View(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(dpVal)) }
    }

    private fun dp(dpVal: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpVal.toFloat(), resources.displayMetrics).toInt()
    }
}
