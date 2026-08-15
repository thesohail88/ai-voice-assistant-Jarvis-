package com.example.aiassistant

import android.util.Base64
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
            "You are Jarvis, Tony Stark's AI. Be concise, intelligent, and helpful."
        } else {
            "You are Friday, an efficient, direct female AI. Respond sharply and concisely."
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
            return@withContext "Apologies, I could not complete the request."
        }
    }

    suspend fun processVoiceAudio(pcmAudioBytes: ByteArray): Pair<AssistantPersona?, String?> = withContext(Dispatchers.IO) {
        val base64Audio = Base64.encodeToString(pcmAudioBytes, Base64.NO_WRAP)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

        val systemPrompt = """
            You are a voice classifier and assistant. 
            Analyze the attached audio:
            1. If the user addressed 'Jarvis', start response with 'PERSONA:JARVIS|' followed by your reply.
            2. If the user addressed 'Friday', start response with 'PERSONA:FRIDAY|' followed by your reply.
            3. If neither wake word was spoken, respond ONLY with 'NO_WAKE_WORD'.
            Keep responses concise and direct.
        """.trimIndent()

        val payload = JSONObject().apply {
            put("system_instruction", JSONObject().put("parts", JSONObject().put("text", systemPrompt)))
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().apply {
                    put(JSONObject().put("inline_data", JSONObject().apply {
                        put("mime_type", "audio/pcm;rate=16000")
                        put("data", base64Audio)
                    }))
                    put(JSONObject().put("text", "Process this voice command"))
                })
            ))
        }

        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        try {
            val response = client.newCall(request).execute()
            val rawJson = response.body?.string() ?: ""
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
            e.printStackTrace()
            return@withContext Pair(null, null)
        }
    }
}
