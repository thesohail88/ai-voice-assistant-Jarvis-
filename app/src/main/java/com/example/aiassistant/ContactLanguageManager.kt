package com.example.aiassistant

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

data class ContactRule(
    val name: String,
    val number: String,
    val languageName: String,
    val languageCode: String
)

class ContactLanguageManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("voicemail_contact_rules", Context.MODE_PRIVATE)

    // Voicemail master toggle state
    fun isVoicemailEnabled(): Boolean {
        return prefs.getBoolean("key_voicemail_enabled", true)
    }

    fun setVoicemailEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("key_voicemail_enabled", enabled).apply()
    }

    fun saveContactRule(number: String, name: String, languageName: String, languageCode: String) {
        val cleanNumber = normalizeNumber(number)
        val json = JSONObject().apply {
            put("name", name)
            put("number", cleanNumber)
            put("languageName", languageName)
            put("languageCode", languageCode)
        }
        prefs.edit().putString(cleanNumber, json.toString()).apply()
    }

    fun removeContactRule(number: String) {
        val cleanNumber = normalizeNumber(number)
        prefs.edit().remove(cleanNumber).apply()
    }

    fun getAllRules(): List<ContactRule> {
        val list = mutableListOf<ContactRule>()
        prefs.all.forEach { (key, value) ->
            if (key != "key_voicemail_enabled" && value is String) {
                try {
                    val json = JSONObject(value)
                    list.add(
                        ContactRule(
                            name = json.getString("name"),
                            number = json.getString("number"),
                            languageName = json.getString("languageName"),
                            languageCode = json.getString("languageCode")
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return list
    }

    fun getLanguageForContact(number: String): String {
        val cleanNumber = normalizeNumber(number)
        val raw = prefs.getString(cleanNumber, null) ?: return "en"
        return try {
            JSONObject(raw).getString("languageCode")
        } catch (e: Exception) {
            "en"
        }
    }

    private fun normalizeNumber(raw: String): String {
        return raw.replace("[^0-9+]".toRegex(), "").trim()
    }
}
