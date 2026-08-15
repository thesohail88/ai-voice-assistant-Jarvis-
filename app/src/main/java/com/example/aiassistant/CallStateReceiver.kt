package com.example.aiassistant

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.telephony.TelephonyManager
import android.util.Log

class CallStateReceiver : BroadcastReceiver() {

    companion object {
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var isIncomingRinging = false
        private var savedCallerNumber: String = ""
        private var callStartTime: Long = 0
    }

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val rawNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        if (!rawNumber.isNullOrBlank()) {
            savedCallerNumber = rawNumber.replace("[^0-9+]".toRegex(), "")
        }

        val currentState = when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
            TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
            TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
            else -> TelephonyManager.CALL_STATE_IDLE
        }

        when (currentState) {
            TelephonyManager.CALL_STATE_RINGING -> {
                isIncomingRinging = true
                callStartTime = System.currentTimeMillis()
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                    isIncomingRinging = false
                }
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                if (isIncomingRinging && (lastState == TelephonyManager.CALL_STATE_RINGING)) {
                    isIncomingRinging = false

                    val languageManager = ContactLanguageManager(context)

                    // Skip execution if user disabled the voicemail toggle
                    if (!languageManager.isVoicemailEnabled()) {
                        Log.d("CallStateReceiver", "Voicemail & SMS auto-responder is disabled. Skipping.")
                        savedCallerNumber = ""
                        lastState = currentState
                        return
                    }

                    val finalCallerNumber = if (savedCallerNumber.isNotBlank()) {
                        savedCallerNumber
                    } else {
                        getLatestMissedCallNumber(context) ?: "Unknown Caller"
                    }

                    val preferredLanguage = languageManager.getLanguageForContact(finalCallerNumber)

                    val serviceIntent = Intent(context, AssistantForegroundService::class.java).apply {
                        putExtra("ACTION_VOICEMAIL_LOGGED", true)
                        putExtra("CALLER_NUMBER", finalCallerNumber)
                        putExtra("LANGUAGE_CODE", preferredLanguage)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }

                    savedCallerNumber = ""
                }
                isIncomingRinging = false
            }
        }

        lastState = currentState
    }

    private fun getLatestMissedCallNumber(context: Context): String? {
        return try {
            val cursor: Cursor? = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    it.getString(0)?.replace("[^0-9+]".toRegex(), "")
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
