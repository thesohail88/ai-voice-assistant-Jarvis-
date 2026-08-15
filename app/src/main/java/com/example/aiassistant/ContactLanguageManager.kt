package com.example.aiassistant

import android.content.Context
import android.content.SharedPreferences

class ContactLanguageManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ContactLanguagePrefs", Context.MODE_PRIVATE)

    fun setLanguageForContact(phoneNumberOrName: String, languageCode: String) {
        val cleanKey = cleanContactKey(phoneNumberOrName)
        prefs.edit().putString(cleanKey, languageCode).apply()
    }

    fun getLanguageForContact(phoneNumberOrName: String): String {
        val cleanKey = cleanContactKey(phoneNumberOrName)
        return prefs.getString(cleanKey, "en-IN") ?: "en-IN" // Default to English
    }

    fun getAllCustomContacts(): Map<String, *> {
        return prefs.all
    }

    fun removeContact(phoneNumberOrName: String) {
        val cleanKey = cleanContactKey(phoneNumberOrName)
        prefs.edit().remove(cleanKey).apply()
    }

    private fun cleanContactKey(raw: String): String {
        return raw.replace(Regex("[^0-9a-zA-Z]"), "").lowercase()
    }
}
