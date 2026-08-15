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

    /**
     * 1. Transcribes incoming WAV audio to text in ~100ms using Whisper Large v3
     */
    private suspend fun transcribeAudio(wavAudioBytes: ByteArray): String? = withContext(Dispatchers.IO) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", "speech.wav",
                wavAudioBytes.toRequestBody("audio/wav".toMediaType())
            )
            .addFormDataPart("model", "whisper-large-v3")
            .addFormDataPart("temperature", "0.0")
            .build()

        val request = Request.Builder()
            .url(transcribeUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            val rawJson = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.e("GroqClient", "Transcription error ${response.code}: $rawJson")
                return@withContext null
            }
            return@withContext JSONObject(rawJson).optString("text", "").trim()
        } catch (e: Exception) {
            Log.e("GroqClient", "Transcription failed", e)
            return@withContext null
        }
    }

    /**
     * 2. Runs the transcribed text through Llama 3.3 70B for instant reasoning
     */
    suspend fun queryAssistant(userPrompt: String, persona: AssistantPersona): String = withContext(Dispatchers.IO) {
        val systemPrompt = if (persona == AssistantPersona.JARVIS) {
            "You are Jarvis, Tony Stark's AI. Respond intelligently, politely, and very concisely (1-2 sentences max)."
        } else {
            "You are Friday, an efficient, direct female AI. Respond sharply, crisply, and concisely (1-2 sentences max)."
        }

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            put(JSONObject().put("role", "user").put("content", userPrompt))
        }

        val jsonPayload = JSONObject().apply {
            put("model", "llama-3.3-70b-versatile")
            put("messages", messages)
            put("max_tokens", 100)
            put("temperature", 0.5)
        }

        val body = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(chatUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        try {
            val response = client.newCall(request).execute()
            val rawJson = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.e("GroqClient", "Chat error ${response.code}: $rawJson")
                return@withContext "Apologies, I encountered an issue."
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

    /**
     * Complete pipeline: Whisper STT -> Local Wake Word Check -> Llama 3.3 LLM
     */
    suspend fun processVoiceAudio(wavAudioBytes: ByteArray): Pair<AssistantPersona?, String?> = withContext(Dispatchers.IO) {
        val transcript = transcribeAudio(wavAudioBytes) ?: return@withContext Pair(null, null)
        val lowerText = transcript.lowercase()

        // 100% deterministic local wake word check
        val persona = when {
            lowerText.contains("jarvis") -> AssistantPersona.JARVIS
            lowerText.contains("friday") -> AssistantPersona.FRIDAY
            else -> return@withContext Pair(null, null) // Ignore ambient noise without calling the LLM
        }

        // Clean user command
        val cleanedPrompt = transcript
            .replace("(?i)jarvis".toRegex(), "")
            .replace("(?i)friday".toRegex(), "")
            .trim()

        val promptToSend = if (cleanedPrompt.isBlank()) "Acknowledge that you are listening and ready." else cleanedPrompt
        val reply = queryAssistant(promptToSend, persona)

        return@withContext Pair(persona, reply)
    }
}
