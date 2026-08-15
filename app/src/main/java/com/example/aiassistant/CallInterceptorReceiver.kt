package com.example.aiassistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

class CallInterceptorReceiver : BroadcastReceiver() {

    companion object {
        var isAutoAnswerEnabled = true
        var autoAnswerDelayMs: Long = 12000L // Set to 12 seconds
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: "Unknown"

        if (state == TelephonyManager.EXTRA_STATE_RINGING && isAutoAnswerEnabled) {
            // Schedule auto-answer if not manually answered within delay
            Handler(Looper.getMainLooper()).postDelayed({
                answerIncomingCall(context, incomingNumber)
            }, autoAnswerDelayMs)
        }
    }

    private fun answerIncomingCall(context: Context, callerNumber: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            try {
                // Programmatically accept call
                telecomManager?.acceptRingingCall()

                // Notify background service to start speaking to caller
                val serviceIntent = Intent(context, AssistantForegroundService::class.java).apply {
                    putExtra("ACTION_CALL_ANSWERED", true)
                    putExtra("CALLER_NUMBER", callerNumber)
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }
}
