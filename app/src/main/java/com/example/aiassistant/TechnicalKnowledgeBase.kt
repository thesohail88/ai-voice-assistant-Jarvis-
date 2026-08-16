package com.example.aiassistant

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class TechnicalSkill(
    val title: String,
    val domain: String, // e.g. "Python", "Algorithms", "Network Protocols", "DevOps"
    val principles: String,
    val executableLogic: String? = null
)

class TechnicalKnowledgeBase(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("jarvis_tech_knowledge", Context.MODE_PRIVATE)

    fun learnTechnicalSkill(skill: TechnicalSkill) {
        val json = JSONObject().apply {
            put("title", skill.title)
            put("domain", skill.domain)
            put("principles", skill.principles)
            put("executableLogic", skill.executableLogic ?: "")
        }
        prefs.edit().putString(skill.title.lowercase(), json.toString()).apply()
    }

    fun queryKnowledge(query: String): List<TechnicalSkill> {
        val results = mutableListOf<TechnicalSkill>()
        val clean = query.lowercase()

        prefs.all.forEach { (_, value) ->
            if (value is String) {
                try {
                    val json = JSONObject(value)
                    val title = json.getString("title")
                    val domain = json.getString("domain")
                    val principles = json.getString("principles")
                    val logic = json.optString("executableLogic", "")

                    if (title.lowercase().contains(clean) || domain.lowercase().contains(clean) || clean.contains(domain.lowercase())) {
                        results.add(TechnicalSkill(title, domain, principles, logic))
                    }
                } catch (e: Exception) {
                    // Skip malformed entries
                }
            }
        }
        return results
    }

    fun getKnowledgeSummary(): String {
        val summaryList = mutableListOf<String>()
        prefs.all.forEach { (_, value) ->
            if (value is String) {
                try {
                    val json = JSONObject(value)
                    summaryList.add("${json.getString("domain")}: ${json.getString("title")}")
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
        return if (summaryList.isEmpty()) "Standard Base Knowledge" else summaryList.take(20).joinToString("; ")
    }
}
