package com.example.aiassistant

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class CustomSkill(
    val name: String,
    val description: String,
    val jsCode: String
)

class SkillRegistry(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("jarvis_learned_skills", Context.MODE_PRIVATE)

    fun registerSkill(skill: CustomSkill) {
        val json = JSONObject().apply {
            put("name", skill.name)
            put("description", skill.description)
            put("jsCode", skill.jsCode)
        }
        prefs.edit().putString(skill.name.lowercase(), json.toString()).apply()
    }

    fun getSkill(name: String): CustomSkill? {
        val raw = prefs.getString(name.lowercase(), null) ?: return null
        return try {
            val json = JSONObject(raw)
            CustomSkill(
                name = json.getString("name"),
                description = json.getString("description"),
                jsCode = json.getString("jsCode")
            )
        } catch (e: Exception) {
            null
        }
    }

    fun getAllSkillsSummary(): String {
        val skills = mutableListOf<String>()
        prefs.all.forEach { (_, value) ->
            if (value is String) {
                try {
                    val json = JSONObject(value)
                    skills.add("${json.getString("name")}: ${json.getString("description")}")
                } catch (e: Exception) {
                    // Ignore malformed entries
                }
            }
        }
        return if (skills.isEmpty()) "None" else skills.joinToString("; ")
    }
}
