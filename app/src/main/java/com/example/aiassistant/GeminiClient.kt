package com.example.aiassistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiClient(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun queryAssistant(userPrompt: String, persona: AssistantPersona): String = withContext(Dispatchers.IO) {
        val systemPrompt = if (persona == AssistantPersona.JARVIS) {
            "You are Jarvis, Tony Stark's sophisticated British AI assistant. Speak concisely, intelligently, and respectfully."
        } else {
            "You are Friday, an efficient, direct, and witty female AI assistant. Respond sharply and concisely."
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

        val jsonPayload = JSONObject().apply {
            put("system_instruction", JSONObject().put("parts", JSONObject().put("text", systemPrompt)))
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", userPrompt)
                ))
            ))
        }

        val body = jsonPayload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        try {
            val response = client.newCall(request).execute()
            val rawJson = response.body?.string() ?: ""
            val json = JSONObject(rawJson)
            return@withContext json
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        } catch (e: Exception) {
            return@withContext "Apologies, I encountered an error connecting to the neural network."
        }
    }
}
