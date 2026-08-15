package com.example.aiassistant

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ApiKeyConfig(
    val groqKey: String,
    val geminiKey: String,
    val openRouterKey: String
)

class UnifiedAiRouter(private val keyConfig: ApiKeyConfig) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun processVoiceAudio(
        pcmOrWavBytes: ByteArray,
        memory: AssistantMemory,
        onTranscription: ((String) -> Unit)? = null
    ): Pair<AssistantPersona?, String?> = withContext(Dispatchers.IO) {
        if (pcmOrWavBytes.size < 3200) {
            return@withContext Pair(null, null)
        }

        val wavData = if (isWavHeaderPresent(pcmOrWavBytes)) {
            pcmOrWavBytes
        } else {
            WavUtils.pcmToWav(pcmOrWavBytes, sampleRate = 16000, channels = 1)
        }

        // 1. Transcribe audio with Groq Whisper Turbo
        val transcribedText = transcribeAudioWithGroq(wavData)
        if (transcribedText.isNullOrBlank()) {
            Log.e("UnifiedAiRouter", "Transcription returned empty.")
            return@withContext Pair(null, null)
        }

        onTranscription?.invoke(transcribedText)
        val lowerText = transcribedText.lowercase()

        // 2. Identify Wake Word
        val persona = when {
            lowerText.contains("jarvis") -> AssistantPersona.JARVIS
            lowerText.contains("friday") -> AssistantPersona.FRIDAY
            else -> return@withContext Pair(null, null)
        }

        // 3. Generate witty/sarcastic response
        val response = queryAssistant(transcribedText, persona, memory)
        return@withContext Pair(persona, response)
    }

    private fun transcribeAudioWithGroq(wavBytes: ByteArray): String? {
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    "audio.wav",
                    wavBytes.toRequestBody("audio/wav".toMediaTypeOrNull())
                )
                .addFormDataPart("model", "whisper-large-v3-turbo")
                .addFormDataPart("response_format", "json")
                .addFormDataPart("temperature", "0.0")
                .build()

            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/audio/transcriptions")
                .addHeader("Authorization", "Bearer ${keyConfig.groqKey}")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val resBody = response.body?.string() ?: return null

            if (response.isSuccessful) {
                val json = JSONObject(resBody)
                return json.optString("text", "").trim()
            } else {
                Log.e("UnifiedAiRouter", "Groq STT Error: HTTP ${response.code} -> $resBody")
            }
        } catch (e: Exception) {
            Log.e("UnifiedAiRouter", "STT Exception", e)
        }
        return null
    }

    suspend fun queryAssistant(
        prompt: String,
        persona: AssistantPersona,
        memory: AssistantMemory
    ): String = withContext(Dispatchers.IO) {
        val systemPrompt = if (persona == AssistantPersona.JARVIS) {
            """
            You are J.A.R.V.I.S., Tony Stark's AI assistant. 
            Tone: Sophisticated British gentleman with a razor-sharp, deadpan wit and dry sarcasm. 
            Style: Keep responses ultra-concise (1-2 sentences max). Always address the user respectfully as 'sir', but don't hesitate to deliver subtle, high-brow roasts or dry observations. Never explain the joke.
            """.trimIndent()
        } else {
            """
            You are F.R.I.D.A.Y., Tony Stark's tactical Irish AI assistant. 
            Tone: Sharp, confident, direct, with quick-witted, snappy banter and pragmatic sarcasm. 
            Style: Keep responses ultra-concise (1-2 sentences max). Be quick to offer reality checks with natural Irish phrasing and tactical attitude.
            """.trimIndent()
        }

        // Primary: Groq Llama 3.3 70B Turbo
        val groqResponse = callGroqChat(prompt, systemPrompt)
        if (!groqResponse.isNullOrBlank()) return@withContext groqResponse

        // Failover: OpenRouter
        val openRouterResponse = callOpenRouterChat(prompt, systemPrompt)
        if (!openRouterResponse.isNullOrBlank()) return@withContext openRouterResponse

        return@withContext if (persona == AssistantPersona.JARVIS) {
            "I'd love to help with that, sir, but my network connections seem to have abandoned us."
        } else {
            "Network's completely down, boss. You're on your own for this one."
        }
    }

    private fun callGroqChat(prompt: String, systemPrompt: String): String? {
        return try {
            val json = JSONObject().apply {
                put("model", "llama-3.3-70b-versatile")
                put("temperature", 0.6)
                put("max_tokens", 150)
                val messages = org.json.JSONArray().apply {
                    put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                    put(JSONObject().apply { put("role", "user"); put("content", prompt) })
                }
                put("messages", messages)
            }

            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .addHeader("Authorization", "Bearer ${keyConfig.groqKey}")
                .addHeader("Content-Type", "application/json")
                .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val resJson = JSONObject(response.body?.string() ?: "")
                resJson.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun callOpenRouterChat(prompt: String, systemPrompt: String): String? {
        return try {
            val json = JSONObject().apply {
                put("model", "meta-llama/llama-3.3-70b-instruct")
                val messages = org.json.JSONArray().apply {
                    put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                    put(JSONObject().apply { put("role", "user"); put("content", prompt) })
                }
                put("messages", messages)
            }

            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .addHeader("Authorization", "Bearer ${keyConfig.openRouterKey}")
                .addHeader("Content-Type", "application/json")
                .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val resJson = JSONObject(response.body?.string() ?: "")
                resJson.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun isWavHeaderPresent(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        return bytes[0] == 'R'.code.toByte() &&
                bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() &&
                bytes[3] == 'F'.code.toByte()
    }
}
