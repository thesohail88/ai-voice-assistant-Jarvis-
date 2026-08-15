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
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    // Updated to the current production Gemini 2.5 Flash endpoint
    private val endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

    suspend fun queryAssistant(userPrompt: String, persona: AssistantPersona): String = withContext(Dispatchers.IO) {
        val systemPrompt = if (persona == AssistantPersona.JARVIS) {
            "You are Jarvis. Be direct, intelligent, and concise (1-2 sentences max)."
        } else {
            "You are Friday. Respond sharply and concisely (1-2 sentences max)."
        }

        val jsonPayload = JSONObject().apply {
            put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", userPrompt)
                ))
            ))
        }

        val body = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(endpointUrl)
            .addHeader("x-goog-api-key", apiKey.trim())
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        try {
            val response = client.newCall(request).execute()
            val rawJson = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e("GeminiClient", "API Error (${response.code}): $rawJson")
                return@withContext "Error ${response.code}: " + extractErrorMessage(rawJson)
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
            Log.e("GeminiClient", "Query Connection Error", e)
            return@withContext "Connection failed: ${e.localizedMessage}"
        }
    }

    suspend fun processVoiceAudio(wavAudioBytes: ByteArray): Pair<AssistantPersona?, String?> = withContext(Dispatchers.IO) {
        val base64Audio = Base64.encodeToString(wavAudioBytes, Base64.NO_WRAP)

        val systemPrompt = """
            Listen carefully to this audio recording:
            1. If the user mentions 'Jarvis', respond starting with 'PERSONA:JARVIS|' followed by your short answer.
            2. If the user mentions 'Friday', respond starting with 'PERSONA:FRIDAY|' followed by your short answer.
            3. If neither was spoken, reply ONLY with 'NO_WAKE_WORD'.
            Keep all answers under 2 sentences.
        """.trimIndent()

        val jsonPayload = JSONObject().apply {
            put("system_instruction", JSONObject().put("parts", JSONObject().put("text", systemPrompt))))
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().apply {
                    put(JSONObject().put("inline_data", JSONObject().apply {
                        put("mime_type", "audio/wav")
                        put("data", base64Audio)
                    }))
                    put(JSONObject().put("text", "Respond to this audio command."))
                })
            ))
        }

        val body = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(endpointUrl)
            .addHeader("x-goog-api-key", apiKey.trim())
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        try {
            val response = client.newCall(request).execute()
            val rawJson = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e("GeminiClient", "Audio API Error (${response.code}): $rawJson")
                val errMsg = extractErrorMessage(rawJson)
                return@withContext Pair(AssistantPersona.JARVIS, "Server error: $errMsg")
            }

            val json = JSONObject(rawJson)
            val text = json
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
            Log.e("GeminiClient", "Audio Connection Error", e)
            return@withContext Pair(null, null)
        }
    }

    private fun extractErrorMessage(rawJson: String): String {
        return try {
            JSONObject(rawJson).getJSONObject("error").getString("message")
        } catch (e: Exception) {
            rawJson.take(120)
        }
    }
}
