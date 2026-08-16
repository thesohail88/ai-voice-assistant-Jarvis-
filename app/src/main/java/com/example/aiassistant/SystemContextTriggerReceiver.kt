package com.example.aiassistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class SystemContextTriggerReceiver : BroadcastReceiver() {

    companion object {
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var isIncoming = false
        private var savedNumber: String? = null

        fun getSystemContextSnapshot(context: Context): String {
            val prefs = context.getSharedPreferences("AssistantPrefs", Context.MODE_PRIVATE)
            val lang = prefs.getString("MESSAGE_LANGUAGE", "English") ?: "English"
            return "Battery: Nominal | Status: Armed | Translation: $lang"
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            val state = when (stateStr) {
                TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
                TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
                else -> TelephonyManager.CALL_STATE_IDLE
            }

            onCallStateChanged(context, state, number)
        }
    }

    private fun onCallStateChanged(context: Context, state: Int, number: String?) {
        if (lastState == state) return

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                isIncoming = true
                savedNumber = number
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // Call answered
                if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                    isIncoming = false
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                // Call ended or missed
                if (lastState == TelephonyManager.CALL_STATE_RINGING && isIncoming) {
                    // Missed Call Detected
                    val missedNumber = savedNumber ?: number
                    if (!missedNumber.isNullOrBlank()) {
                        val keyConfig = ApiKeyConfig(
                            groqKey = BuildConfig.GROQ_KEY,
                            geminiKey = BuildConfig.GEMINI_KEY,
                            openRouterKey = BuildConfig.OPENROUTER_KEY
                        )
                        val aiRouter = UnifiedAiRouter(keyConfig, context)
                        MissedCallSmsResponder(context).handleMissedCall(missedNumber, null, aiRouter)
                    }
                }
                isIncoming = false
            }
        }
        lastState = state
    }
}
