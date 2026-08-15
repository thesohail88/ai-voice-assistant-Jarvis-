package com.example.aiassistant

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GroqClient(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val transcribeUrl = "https://api.groq.com/openai/v1/audio/transcriptions"
    private val chatUrl = "https://api.groq.com/openai/v1/chat/completions"

    suspend fun transcribeAudio(wavAudioBytes: ByteArray): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", "recording.wav",
                wavAudioBytes.toRequestBody("audio/wav".toMediaType())
            )
            .addFormDataPart("model", "whisper-large-v3")
            .addFormDataPart("response_format", "json")
            .build()

        val request = Request.Builder()
            .url(transcribeUrl)
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            val rawJson = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e("GroqClient", "Whisper HTTP ${response.code}: $rawJson")
                return@withContext Pair(null, "Whisper Error ${response.code}: $rawJson")
            }

            val transcript = JSONObject(rawJson).optString("text", "").trim()
            return@withContext Pair(transcript, null)
        } catch (e: Exception) {
            Log.e("GroqClient", "Transcription connection failed", e)
            return@withContext Pair(null, "Transcription network error: ${e.localizedMessage}")
        }
    }

    suspend fun queryAssistant(userPrompt: String, persona: AssistantPersona): String = withContext(Dispatchers.IO) {
        val systemPrompt = if (persona == AssistantPersona.JARVIS) {
            "You are Jarvis, Sohail Shaikh's AI assistant. Keep all responses direct, intelligent, and concise in a natural and human voice (1-2 sentences max)."
        } else {
            "You are Friday, an efficient, direct female AI assistant. Respond sharply and concisely in a natural and human voice (1-2 sentences max)."
        }

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            put(JSONObject().put("role", "user").put("content", userPrompt))
        }

        val jsonPayload = JSONObject().apply {
            put("model", "llama-3.3-70b-versatile")
            put("messages", messages)
            put("max_tokens", 100)
            put("temperature", 0.6)
        }

        val body = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(chatUrl)
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        try {
            val response = client.newCall(request).execute()
            val rawJson = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e("GroqClient", "Llama HTTP ${response.code}: $rawJson")
                return@withContext "Error ${response.code}: Server issue."
            }

            val json = JSONObject(rawJson)
            return@withContext json
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        } catch (e: Exception) {
            Log.e("GroqClient", "Chat completion failed", e)
            return@withContext "Connection interrupted."
        }
    }

    suspend fun processVoiceAudio(
        wavAudioBytes: ByteArray,
        onTranscriptLogged: (String) -> Unit
    ): Pair<AssistantPersona?, String?> = withContext(Dispatchers.IO) {
        val (transcript, error) = transcribeAudio(wavAudioBytes)

        if (error != null) {
            return@withContext Pair(AssistantPersona.JARVIS, error)
        }

        if (transcript.isNullOrBlank()) {
            return@withContext Pair(null, null)
        }

        onTranscriptLogged(transcript)
        val lowerText = transcript.lowercase()

        // Flexible wake word matching (handles punctuation and common homophones)
        val persona = when {
            lowerText.contains("jarvis") || lowerText.contains("jarvis.") || lowerText.contains("jarvis,") -> AssistantPersona.JARVIS
            lowerText.contains("friday") || lowerText.contains("friday.") || lowerText.contains("friday,") -> AssistantPersona.FRIDAY
            else -> return@withContext Pair(null, null) // Ignore background chatter
        }

        val cleanedPrompt = transcript
            .replace("(?i)jarvis".toRegex(), "")
            .replace("(?i)friday".toRegex(), "")
            .trim()

        val promptToSend = if (cleanedPrompt.isBlank()) "Acknowledge that you are online and ready." else cleanedPrompt
        val reply = queryAssistant(promptToSend, persona)

        return@withContext Pair(persona, reply)
    }
}
