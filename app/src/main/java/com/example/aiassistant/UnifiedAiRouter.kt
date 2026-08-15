package com.example.aiassistant

import android.util.Base64
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

data class ApiKeyConfig(
    val groqKey: String = "",
    val geminiKey: String = "",
    val openRouterKey: String = ""
)

class UnifiedAiRouter(private val config: ApiKeyConfig) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jarvisGreetings = listOf("At your service, sir.", "Yes, sir?", "Online and listening.", "How can I help, sir?")
    private val fridayGreetings = listOf("Yes?", "Online and ready.", "Listening.", "Go ahead.")

    suspend fun transcribeAudio(wavAudioBytes: ByteArray): Pair<String?, String?> = withContext(Dispatchers.IO) {
        if (config.groqKey.isNotBlank()) {
            val (text, err) = transcribeViaGroq(wavAudioBytes)
            if (!text.isNullOrBlank()) return@withContext Pair(text, null)
            Log.w("UnifiedAiRouter", "Groq STT failed ($err). Trying Gemini fallback...")
        }

        if (config.geminiKey.isNotBlank()) {
            val (text, err) = transcribeViaGemini(wavAudioBytes)
            if (!text.isNullOrBlank()) return@withContext Pair(text, null)
        }

        Pair(null, "Audio transcription failed across all providers.")
    }

    private fun transcribeViaGroq(wavBytes: ByteArray): Pair<String?, String?> {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "speech.wav", wavBytes.toRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("model", "whisper-large-v3-turbo")
            .addFormDataPart("response_format", "json")
            .build()

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/transcriptions")
            .addHeader("Authorization", "Bearer ${config.groqKey.trim()}")
            .post(requestBody)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val rawJson = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Pair(JSONObject(rawJson).optString("text", "").trim(), null)
            } else {
                Pair(null, "Groq Error ${response.code}: $rawJson")
            }
        } catch (e: Exception) {
            Pair(null, e.localizedMessage)
        }
    }

    private fun transcribeViaGemini(wavBytes: ByteArray): Pair<String?, String?> {
        val base64 = Base64.encodeToString(wavBytes, Base64.NO_WRAP)
        val payload = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().apply {
                    put(JSONObject().put("inline_data", JSONObject().apply {
                        put("mime_type", "audio/wav")
                        put("data", base64)
                    }))
                    put(JSONObject().put("text", "Transcribe this audio strictly verbatim."))
                })
            ))
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent")
            .addHeader("x-goog-api-key", config.geminiKey.trim())
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val response = client.newCall(request).execute()
            val raw = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val text = JSONObject(raw).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                Pair(text.trim(), null)
            } else {
                Pair(null, "Gemini Error ${response.code}: $raw")
            }
        } catch (e: Exception) {
            Pair(null, e.localizedMessage)
        }
    }

    suspend fun queryAssistant(userPrompt: String, persona: AssistantPersona, memory: AssistantMemory? = null): String = withContext(Dispatchers.IO) {
        val basePrompt = if (persona == AssistantPersona.JARVIS) {
            "You are Jarvis, Sohail's AI assistant. Keep responses direct, intelligent, and concise in a natural and human voice (1-2 sentences max)."
        } else {
            "You are Friday, an efficient, direct female AI assistant. Respond sharply and concisely in a natural and human voice (1-2 sentences max)."
        }

        val learnedFacts = memory?.getAllMemoriesFormatted() ?: "None"
        val fullSystemPrompt = """
            $basePrompt
            
            LEARNED USER FACTS:
            $learnedFacts
        """.trimIndent()

        if (config.groqKey.isNotBlank()) {
            val reply = callOpenAiCompatible(
                url = "https://api.groq.com/openai/v1/chat/completions",
                authHeader = "Bearer ${config.groqKey.trim()}",
                model = "llama-3.3-70b-versatile",
                systemPrompt = fullSystemPrompt,
                userPrompt = userPrompt
            )
            if (reply != null) {
                memory?.extractAndLearn(userPrompt, reply)
                return@withContext reply
            }
        }

        if (config.openRouterKey.isNotBlank()) {
            val reply = callOpenAiCompatible(
                url = "https://openrouter.ai/api/v1/chat/completions",
                authHeader = "Bearer ${config.openRouterKey.trim()}",
                model = "openrouter/free",
                systemPrompt = fullSystemPrompt,
                userPrompt = userPrompt
            )
            if (reply != null) {
                memory?.extractAndLearn(userPrompt, reply)
                return@withContext reply
            }
        }

        if (config.geminiKey.isNotBlank()) {
            val reply = callGeminiRest(fullSystemPrompt, userPrompt)
            if (reply != null) {
                memory?.extractAndLearn(userPrompt, reply)
                return@withContext reply
            }
        }

        "Apologies, all AI services are currently unavailable."
    }

    private fun callOpenAiCompatible(url: String, authHeader: String, model: String, systemPrompt: String, userPrompt: String): String? {
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            put(JSONObject().put("role", "user").put("content", userPrompt))
        }
        val json = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("max_tokens", 80)
            put("temperature", 0.5)
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", authHeader)
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val res = client.newCall(request).execute()
            val raw = res.body?.string() ?: ""
            if (res.isSuccessful) {
                JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun callGeminiRest(systemPrompt: String, userPrompt: String): String? {
        val payload = JSONObject().apply {
            put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))))
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent")
            .addHeader("x-goog-api-key", config.geminiKey.trim())
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val res = client.newCall(request).execute()
            val raw = res.body?.string() ?: ""
            if (res.isSuccessful) {
                JSONObject(raw).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun processVoiceAudio(
        wavAudioBytes: ByteArray,
        memory: AssistantMemory? = null,
        onTranscriptLogged: ((String) -> Unit)? = null
    ): Pair<AssistantPersona?, String?> = withContext(Dispatchers.IO) {
        val (transcript, error) = transcribeAudio(wavAudioBytes)

        if (error != null && transcript.isNullOrBlank()) {
            return@withContext Pair(AssistantPersona.JARVIS, error)
        }

        if (transcript.isNullOrBlank()) return@withContext Pair(null, null)

        onTranscriptLogged?.invoke(transcript)
        val lower = transcript.lowercase()

        val persona = when {
            lower.contains("jarvis") -> AssistantPersona.JARVIS
            lower.contains("friday") -> AssistantPersona.FRIDAY
            else -> return@withContext Pair(null, null)
        }

        val cleanedPrompt = transcript
            .replace("(?i)jarvis".toRegex(), "")
            .replace("(?i)friday".toRegex(), "")
            .replace("[.,?!]".toRegex(), "")
            .trim()

        if (cleanedPrompt.isBlank()) {
            val instant = if (persona == AssistantPersona.JARVIS) jarvisGreetings[Random.nextInt(jarvisGreetings.size)] else fridayGreetings[Random.nextInt(fridayGreetings.size)]
            return@withContext Pair(persona, instant)
        }

        val reply = queryAssistant(cleanedPrompt, persona, memory)
        return@withContext Pair(persona, reply)
    }
}
