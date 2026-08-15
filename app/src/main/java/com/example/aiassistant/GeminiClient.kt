package com.example.aiassistant

import android.util.Base64
import android.util.Log
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
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

    suspend fun queryAssistant(userPrompt: String, persona: AssistantPersona): String = withContext(Dispatchers.IO) {
        val systemPrompt = if (persona == AssistantPersona.JARVIS) {
            "You are Jarvis, Tony Stark's AI assistant. Keep all responses direct, intelligent, and concise (1-2 sentences max)."
        } else {
            "You are Friday, an efficient, direct female AI assistant. Respond sharply and concisely (1-2 sentences max)."
        }

        val jsonPayload = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            put("contents", JSONArray().put(
                JSONObject().put("role", "user").put("parts", JSONArray().put(
                    JSONObject().put("text", userPrompt)
                ))
            ))
        }

        val body = jsonPayload.toString().toRequestBody("application/json".toMediaType())
        
        // Pass key via both header and query param for maximum compatibility across AI Studio key formats
        val request = Request.Builder()
            .url("$endpointUrl?key=$apiKey")
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        try {
            val response = client.newCall(request).execute()
            val rawJson = response.body?.string() ?: ""
            
            if (!response.isSuccessful) {
                Log.e("GeminiClient", "API Error HTTP ${response.code}: $rawJson")
                return@withContext "Apologies, the server responded with an error."
            }

            val json = JSONObject(rawJson)
            return@withContext json
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
        } catch (e: Exception) {
            Log.e("GeminiClient", "Connection error", e)
            return@withContext "Apologies, I could not process that."
        }
    }

    suspend fun processVoiceAudio(wavAudioBytes: ByteArray): Pair<AssistantPersona?, String?> = withContext(Dispatchers.IO) {
        val base64Audio = Base64.encodeToString(wavAudioBytes, Base64.NO_WRAP)

        val systemPrompt = """
            Listen to this audio recording:
            1. If the user calls or mentions 'Jarvis', respond starting with 'PERSONA:JARVIS|' followed by your answer.
            2. If the user calls or mentions 'Friday', respond starting with 'PERSONA:FRIDAY|' followed by your answer.
            3. If neither 'Jarvis' nor 'Friday' is called, reply ONLY with 'NO_WAKE_WORD'.
            Keep all answers concise (1-2 sentences).
        """.trimIndent()

        val jsonPayload = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            put("contents", JSONArray().put(
                JSONObject().put("role", "user").put("parts", JSONArray().apply {
                    put(JSONObject().put("inlineData", JSONObject().apply {
                        put("mimeType", "audio/wav")
                        put("data", base64Audio)
                    }))
                    put(JSONObject().put("text", "Process this voice command"))
                })
            ))
        }

        val body = jsonPayload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$endpointUrl?key=$apiKey")
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        try {
            val response = client.newCall(request).execute()
            val rawJson = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e("GeminiClient", "API Audio Error HTTP ${response.code}: $rawJson")
                return@withContext Pair(null, null)
            }

            val text = JSONObject(rawJson)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()

            if (text.startsWith("NO_WAKE_WORD")) {
                return@withContext Pair(null, null)
            }

            return@withContext when {
                text.startsWith("PERSONA:JARVIS|") -> {
                    Pair(AssistantPersona.JARVIS, text.removePrefix("PERSONA:JARVIS|").trim())
                }
                text.startsWith("PERSONA:FRIDAY|") -> {
                    Pair(AssistantPersona.FRIDAY, text.removePrefix("PERSONA:FRIDAY|").trim())
                }
                else -> Pair(AssistantPersona.JARVIS, text)
            }
        } catch (e: Exception) {
            Log.e("GeminiClient", "Audio connection error", e)
            return@withContext Pair(null, null)
        }
    }
}
