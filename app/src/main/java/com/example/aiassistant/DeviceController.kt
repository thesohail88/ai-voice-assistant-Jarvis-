package com.example.aiassistant

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import androidx.core.content.ContextCompat

class DeviceController(private val context: Context) {

    fun handleActionCommand(text: String): Boolean {
        val lower = text.lowercase().trim()

        return when {
            // Make Direct Phone Call (e.g. "call 9876543210" or "phone 12345")
            lower.startsWith("call ") || lower.startsWith("dial ") || lower.startsWith("phone ") -> {
                val rawNumber = lower.replace("call", "")
                    .replace("dial", "")
                    .replace("phone", "")
                    .trim()
                makeDirectPhoneCall(rawNumber)
                true
            }

            // Open YouTube
            lower.contains("open youtube") -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:"))
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                true
            }

            // Web Search
            lower.startsWith("search for ") || lower.startsWith("google ") -> {
                val query = lower.replace("search for ", "").replace("google ", "").trim()
                val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra(SearchManager.QUERY, query)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                true
            }

            // Set Timer
            lower.startsWith("set timer for") -> {
                val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_MESSAGE, "AI Timer")
                    putExtra(AlarmClock.EXTRA_LENGTH, 60)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                true
            }

            else -> false
        }
    }

    private fun makeDirectPhoneCall(phoneNumber: String) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(callIntent)
        } else {
            // Fallback to dialer if CALL_PHONE permission not yet granted
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
        }
    }
}
