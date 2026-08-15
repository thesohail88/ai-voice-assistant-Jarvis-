package com.example.aiassistant

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log

class CallInterceptorReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: return

        if (state == TelephonyManager.EXTRA_STATE_RINGING) {
            val languageManager = ContactLanguageManager(context)
            val preferredLanguage = languageManager.getLanguageForContact(incomingNumber)

            Log.d("CallInterceptor", "Incoming call from $incomingNumber. Disconnecting & routing to voicemail.")

            disconnectCall(context)

            val serviceIntent = Intent(context, AssistantForegroundService::class.java).apply {
                putExtra("ACTION_VOICEMAIL_LOGGED", true)
                putExtra("CALLER_NUMBER", incomingNumber)
                putExtra("LANGUAGE_CODE", preferredLanguage)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectCall(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                telecomManager?.endCall()
            }
        } catch (e: Exception) {
            Log.e("CallInterceptor", "Unable to end call: ${e.message}")
        }
    }
}
