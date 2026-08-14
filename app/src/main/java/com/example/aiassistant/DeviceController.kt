package com.example.aiassistant

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock

class DeviceController(private val context: Context) {

    fun handleActionCommand(text: String): Boolean {
        val lower = text.lowercase()

        return when {
            // Open YouTube
            lower.contains("open youtube") -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:"))
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                true
            }

            // Web Search
            lower.startsWith("search for ") || lower.startsWith("google ") -> {
                val query = lower.replace("search for ", "").replace("google ", "")
                val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra(SearchManager.QUERY, query)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                true
            }

            // Set Timer / Alarm
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

            else -> false // Not a device command; route to Gemini LLM
        }
    }
}
