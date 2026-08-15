package com.example.aiassistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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

    private lateinit var contactDisplayTv: TextView
    private lateinit var rulesContainer: LinearLayout
    private lateinit var languageSpinner: Spinner

    private val languages = listOf(
        Pair("English", "en"),
        Pair("Hindi", "hi"),
        Pair("Spanish", "es"),
        Pair("French", "fr"),
        Pair("German", "de"),
        Pair("Arabic", "ar"),
        Pair("Mandarin", "zh")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        languageManager = ContactLanguageManager(this)

        // Root layout
        val rootScrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#0F172A")) // Dark theme slate
            isFillViewport = true
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(24), dpToPx(16), dpToPx(24))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Header Title
        val titleTv = TextView(this).apply {
            text = "AI Voice Assistant & Voicemail"
            textSize = 22f
            setTextColor(Color.parseColor("#F8FAFC"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        mainLayout.addView(titleTv)

        val subTitleTv = TextView(this).apply {
            text = "Autonomous voicemail agent & 24/7 smart assistant"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dpToPx(4), 0, dpToPx(16))
        }
        mainLayout.addView(subTitleTv)

        // Top Action Card
        val actionCard = createCardLayout()
        val actionCardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        }

        val cardTitle = TextView(this).apply {
            text = "Service Controls"
            textSize = 16f
            setTextColor(Color.parseColor("#38BDF8"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        actionCardLayout.addView(cardTitle)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dpToPx(12), 0, 0)
        }

        val startBtn = createStyledButton("Start Service", "#0284C7") {
            startAssistantService()
        }
        val permBtn = createStyledButton("Permissions", "#334155") {
            requestAppPermissions()
        }

        btnRow.addView(startBtn, LinearLayout.LayoutParams(0, dpToPx(46), 1f).apply { marginEnd = dpToPx(8) })
        btnRow.addView(permBtn, LinearLayout.LayoutParams(0, dpToPx(46), 1f))
        actionCardLayout.addView(btnRow)
        actionCard.addView(actionCardLayout)
        mainLayout.addView(actionCard)

        addSpacer(mainLayout, 16)

        // Contact Voicemail Configuration Card
        val configCard = createCardLayout()
        val configLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        }

        val configHeader = TextView(this).apply {
            text = "Configure Contact Voicemail"
            textSize = 16f
            setTextColor(Color.parseColor("#F8FAFC"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        configLayout.addView(configHeader)

        addSpacer(configLayout, 12)

        val pickContactBtn = createStyledButton("👤 Choose Contact from Phone", "#1E293B") {
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            startActivityForResult(intent, PICK_CONTACT_REQUEST)
        }
        configLayout.addView(pickContactBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(46)))

        contactDisplayTv = TextView(this).apply {
            text = "No contact selected"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dpToPx(8), 0, dpToPx(12))
        }
        configLayout.addView(contactDisplayTv)

        val langLabel = TextView(this).apply {
            text = "Select Voicemail Language:"
            textSize = 13f
            setTextColor(Color.parseColor("#E2E8F0"))
        }
        configLayout.addView(langLabel)

        languageSpinner = Spinner(this).apply {
            val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, languages.map { it.first })
            this.adapter = adapter
            background = createRoundedDrawable("#1E293B", "#475569")
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
        }
        configLayout.addView(languageSpinner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48)).apply { topMargin = dpToPx(6) })

        addSpacer(configLayout, 14)

        val saveRuleBtn = createStyledButton("Save Voicemail Rule", "#0284C7") {
            if (selectedContactNumber.isNotBlank()) {
                val selectedLang = languages[languageSpinner.selectedItemPosition]
                languageManager.saveContactRule(
                    selectedContactNumber,
                    if (selectedContactName.isBlank()) selectedContactNumber else selectedContactName,
                    selectedLang.first,
                    selectedLang.second
                )
                Toast.makeText(this, "Rule saved for $selectedContactName", Toast.LENGTH_SHORT).show()
                selectedContactName = ""
                selectedContactNumber = ""
                contactDisplayTv.text = "No contact selected"
                refreshRulesList()
            } else {
                Toast.makeText(this, "Please choose a contact first", Toast.LENGTH_SHORT).show()
            }
        }
        configLayout.addView(saveRuleBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(46)))

        configCard.addView(configLayout)
        mainLayout.addView(configCard)

        addSpacer(mainLayout, 20)

        // Saved Rules Header
        val rulesHeader = TextView(this).apply {
            text = "Configured Contacts"
            textSize = 16f
            setTextColor(Color.parseColor("#F8FAFC"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        mainLayout.addView(rulesHeader)

        addSpacer(mainLayout, 8)

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
            val emptyTv = TextView(this).apply {
                text = "No custom rules set. All calls default to English voicemail."
                textSize = 13f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(0, dpToPx(8), 0, 0)
            }
            rulesContainer.addView(emptyTv)
            return
        }

        for (rule in rules) {
            val ruleCard = createCardLayout()
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))
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
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val detailsTv = TextView(this).apply {
                text = "${rule.number} • ${rule.languageName}"
                textSize = 12f
                setTextColor(Color.parseColor("#38BDF8"))
            }

            textInfo.addView(nameTv)
            textInfo.addView(detailsTv)
            row.addView(textInfo)

            val deleteBtn = Button(this).apply {
                text = "Delete"
                textSize = 12f
                setTextColor(Color.parseColor("#EF4444"))
                background = createRoundedDrawable("#334155", "#EF4444")
                setOnClickListener {
                    languageManager.removeContactRule(rule.number)
                    refreshRulesList()
                }
            }
            row.addView(deleteBtn, LinearLayout.LayoutParams(dpToPx(80), dpToPx(38)))
            ruleCard.addView(row)
            rulesContainer.addView(ruleCard)
            addSpacer(rulesContainer, 8)
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
                        contactDisplayTv.text = "Selected: $selectedContactName ($selectedContactNumber)"
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
        Toast.makeText(this, "Assistant & Voicemail Active", Toast.LENGTH_SHORT).show()
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

    private fun createCardLayout(): FrameLayout {
        return FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            background = createRoundedDrawable("#1E293B", "#334155")
        }
    }

    private fun createStyledButton(label: String, bgColor: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = createRoundedDrawable(bgColor, bgColor)
            setOnClickListener { onClick() }
        }
    }

    private fun createRoundedDrawable(fillColor: String, strokeColor: String): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(10).toFloat()
            setColor(Color.parseColor(fillColor))
            setStroke(dpToPx(1), Color.parseColor(strokeColor))
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
