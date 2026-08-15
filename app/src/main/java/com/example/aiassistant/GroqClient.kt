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
import kotlin.random.Random

class GroqClient(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val transcribeUrl = "https://api.groq.com/openai/v1/audio/transcriptions"
    private val chatUrl = "https://api.groq.com/openai/v1/chat/completions"

    private val jarvisGreetings = listOf("At your service, sir.", "Yes, sir?", "Online and listening.", "How can I help, sir?")
    private val fridayGreetings = listOf("Yes?", "Online and ready.", "Listening.", "Go ahead.")

    suspend fun transcribeAudio(wavAudioBytes: ByteArray): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", "recording.wav",
                wavAudioBytes.toRequestBody("audio/wav".toMediaType())
            )
            .addFormDataPart("model", "whisper-large-v3-turbo") // Ultra-fast Turbo STT
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
                return@withContext Pair<String?, String?>(null, "Whisper Error ${response.code}: $rawJson")
            }

            val transcript = JSONObject(rawJson).optString("text", "").trim()
            return@withContext Pair<String?, String?>(transcript, null)
        } catch (e: Exception) {
            Log.e("GroqClient", "Transcription connection failed", e)
            return@withContext Pair<String?, String?>(null, "Transcription network error: ${e.localizedMessage}")
        }
    }

    suspend fun queryAssistant(userPrompt: String, persona: AssistantPersona): String = withContext(Dispatchers.IO) {
        val systemPrompt = if (persona == AssistantPersona.JARVIS) {
            "You are Jarvis, Sohail's AI assistant. Keep all responses direct, intelligent, and concise (1-2 sentences max)."
        } else {
            "You are Friday, an efficient, direct female AI assistant. Respond sharply and concisely (1-2 sentences max)."
        }

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            put(JSONObject().put("role", "user").put("content", userPrompt))
        }

        val jsonPayload = JSONObject().apply {
            put("model", "llama-3.3-70b-versatile")
            put("messages", messages)
            put("max_tokens", 60) // Capped tokens for instant response time
            put("temperature", 0.5)
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
        onTranscriptLogged: ((String) -> Unit)? = null
    ): Pair<AssistantPersona?, String?> = withContext(Dispatchers.IO) {
        val (transcript, error) = transcribeAudio(wavAudioBytes)

        if (error != null) {
            return@withContext Pair<AssistantPersona?, String?>(AssistantPersona.JARVIS, error)
        }

        if (transcript.isNullOrBlank()) {
            return@withContext Pair<AssistantPersona?, String?>(null, null)
        }

        onTranscriptLogged?.invoke(transcript)
        val lowerText = transcript.lowercase()

        val persona = when {
            lowerText.contains("jarvis") -> AssistantPersona.JARVIS
            lowerText.contains("friday") -> AssistantPersona.FRIDAY
            else -> return@withContext Pair<AssistantPersona?, String?>(null, null)
        }

        val cleanedPrompt = transcript
            .replace("(?i)jarvis".toRegex(), "")
            .replace("(?i)friday".toRegex(), "")
            .replace("[.,?!]".toRegex(), "")
            .trim()

        // INSTANT ACKNOWLEDGMENT: Skip LLM network hop if the user only called the name
        if (cleanedPrompt.isBlank()) {
            val instantReply = if (persona == AssistantPersona.JARVIS) {
                jarvisGreetings[Random.nextInt(jarvisGreetings.size)]
            } else {
                fridayGreetings[Random.nextInt(fridayGreetings.size)]
            }
            return@withContext Pair<AssistantPersona?, String?>(persona, instantReply)
        }

        val reply = queryAssistant(cleanedPrompt, persona)
        return@withContext Pair<AssistantPersona?, String?>(persona, reply)
    }
}
