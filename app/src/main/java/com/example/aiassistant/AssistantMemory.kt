package com.example.aiassistant

import android.content.Context
import android.content.SharedPreferences

class AssistantMemory(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("assistant_memory_prefs", Context.MODE_PRIVATE)

    fun saveFact(key: String, value: String) {
        prefs.edit().putString(key.lowercase().trim(), value.trim()).apply()
    }

    fun getAllMemoriesFormatted(): String {
        val allEntries = prefs.all
        if (allEntries.isEmpty()) return "None"
        val sb = StringBuilder()
        for ((key, value) in allEntries) {
            sb.append("- ").append(key).append(": ").append(value).append("\n")
        }
        return sb.toString().trim()
    }

    fun extractAndLearn(userPrompt: String, llmReply: String) {
        val lower = userPrompt.lowercase()
        if (lower.contains("remember that") || lower.contains("my name is") || lower.contains("i like") || lower.contains("my favorite")) {
            val fact = userPrompt
                .replace("(?i)remember that".toRegex(), "")
                .replace("(?i)please remember".toRegex(), "")
                .trim()
            saveFact("fact_${System.currentTimeMillis()}", fact)
        }
    }
}
