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
                Log.d("VoicemailReceiver", "Call ringing from: $savedCallerNumber. Waiting for user response...")
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // User answered the call manually -> DO NOT trigger voicemail
                if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                    isIncomingRinging = false
                    Log.d("VoicemailReceiver", "Call answered by user. Voicemail cancelled.")
                }
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                // Ringing finished without OFFHOOK -> Call was missed, timed out, or user was busy/declined
                if (isIncomingRinging && (lastState == TelephonyManager.CALL_STATE_RINGING)) {
                    isIncomingRinging = false

                    // If number was masked by OS broadcast, fetch latest missed call from CallLog
                    val finalCallerNumber = if (savedCallerNumber.isNotBlank()) {
                        savedCallerNumber
                    } else {
                        getLatestMissedCallNumber(context) ?: "Unknown Caller"
                    }

                    Log.d("VoicemailReceiver", "Call missed/unanswered from: $finalCallerNumber. Triggering AI Voicemail.")

                    val languageManager = ContactLanguageManager(context)
                    val preferredLanguage = languageManager.getLanguageForContact(finalCallerNumber)

                    // Dispatch to Assistant Foreground Service
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
