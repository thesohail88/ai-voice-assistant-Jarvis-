package com.example.aiassistant

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MissedCallSmsResponder(private val context: Context) {

    private val contactManager = ContactManager(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    fun handleMissedCall(callerNumber: String, callerName: String?, aiRouter: UnifiedAiRouter) {
        if (callerNumber.isBlank()) return

        scope.launch {
            try {
                val prefs = context.getSharedPreferences("AssistantPrefs", Context.MODE_PRIVATE)
                val isAutoSmsEnabled = prefs.getBoolean("AUTO_MISSED_CALL_SMS", true)
                if (!isAutoSmsEnabled) return@launch

                val defaultLang = prefs.getString("MESSAGE_LANGUAGE", "English") ?: "English"
                val targetLang = contactManager.getLanguageForContact(callerName ?: callerNumber, defaultLang)

                val prompt = "Generate a concise 1-sentence auto-reply SMS stating that the owner is currently unavailable and will call back shortly. Respond strictly in $targetLang without quotes."
                val smsBody = aiRouter.processDirectTextPrompt(prompt, AssistantPersona.JARVIS)

                val cleanSmsBody = smsBody.replace("\"", "").trim()
                sendSmsDirect(callerNumber, cleanSmsBody)
                Log.d("MissedCallSms", "Dispatched auto-SMS to $callerNumber in $targetLang: $cleanSmsBody")
            } catch (e: Exception) {
                Log.e("MissedCallSms", "Failed to dispatch missed call SMS", e)
            }
        }
    }

    private fun sendSmsDirect(phoneNumber: String, message: String) {
        try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
        } catch (e: Exception) {
            Log.e("MissedCallSms", "SMS Manager failure", e)
        }
    }
}
