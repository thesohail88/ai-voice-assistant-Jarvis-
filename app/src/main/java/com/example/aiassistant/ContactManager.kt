package com.example.aiassistant

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.provider.ContactsContract
import org.json.JSONObject

data class ContactItem(val name: String, val phoneNumber: String)

class ContactManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("ContactLangRules", Context.MODE_PRIVATE)

    @SuppressLint("Range")
    fun getAllDeviceContacts(): List<ContactItem> {
        val contactList = mutableListOf<ContactItem>()
        val contentResolver = context.contentResolver
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)) ?: ""
                val number = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: ""
                val cleanNumber = number.replace(" ", "").replace("-", "")
                if (name.isNotBlank()) {
                    contactList.add(ContactItem(name, cleanNumber))
                }
            }
        }
        return contactList.distinctBy { it.name }
    }

    fun setLanguageForContact(contactIdentifier: String, language: String) {
        val cleanKey = contactIdentifier.lowercase().trim().replace(" ", "_")
        prefs.edit().putString(cleanKey, language).apply()
    }

    fun getLanguageForContact(contactIdentifier: String, defaultLang: String = "English"): String {
        val cleanKey = contactIdentifier.lowercase().trim().replace(" ", "_")
        val saved = prefs.getString(cleanKey, null)
        if (saved != null) return saved

        // Fuzzy match against phonebook names
        val allRules = prefs.all
        for ((key, value) in allRules) {
            if (cleanKey.contains(key) || key.contains(cleanKey)) {
                return value.toString()
            }
        }
        return defaultLang
    }

    fun getAllCustomContactRules(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for ((key, value) in prefs.all) {
            map[key.replace("_", " ").uppercase()] = value.toString()
        }
        return map
    }

    fun removeContactRule(contactIdentifier: String) {
        val cleanKey = contactIdentifier.lowercase().trim().replace(" ", "_")
        prefs.edit().remove(cleanKey).apply()
    }
}
