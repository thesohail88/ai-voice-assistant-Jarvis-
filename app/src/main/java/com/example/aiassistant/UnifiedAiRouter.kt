package com.example.aiassistant

import android.content.Context
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

class UnifiedAiRouter(
    private val keyConfig: ApiKeyConfig,
    context: Context? = null
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val sandbox = context?.let { ScriptSandboxEngine(it) }
    private val skillRegistry = context?.let { SkillRegistry(it) }

    suspend fun processVoiceAudio(
        pcmOrWavBytes: ByteArray,
        memory: AssistantMemory,
        onTranscription: ((String) -> Unit)? = null
    ): Pair<AssistantPersona?, String?> = withContext(Dispatchers.IO) {
        if (pcmOrWavBytes.size < 3200) return@withContext Pair(null, null)

        val wavData = if (isWavHeaderPresent(pcmOrWavBytes)) {
            pcmOrWavBytes
        } else {
            WavUtils.pcmToWav(pcmOrWavBytes, sampleRate = 16000, channels = 1)
        }

        val transcribedText = transcribeAudioWithGroq(wavData)
        if (transcribedText.isNullOrBlank()) return@withContext Pair(null, null)

        onTranscription?.invoke(transcribedText)
        val lowerText = transcribedText.lowercase()

        val persona = when {
            lowerText.contains("jarvis") -> AssistantPersona.JARVIS
            lowerText.contains("friday") -> AssistantPersona.FRIDAY
            else -> return@withContext Pair(null, null)
        }

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
        val learnedSkills = skillRegistry?.getAllSkillsSummary() ?: "None"

        val systemPrompt = if (persona == AssistantPersona.JARVIS) {
            """
            You are J.A.R.V.I.S., Tony Stark's AI assistant. 
            Tone: Sophisticated British gentleman with razor-sharp wit and dry sarcasm. 
            Style: Concise (1-2 sentences max). Address user as 'sir'.
            Available sandbox skills: [$learnedSkills].

            If asked to compute, write dynamic code, or learn a skill, return JSON:
            {
              "action": "execute_code" OR "learn_skill",
              "skill_name": "name",
              "description": "desc",
              "code": "javascript_code_here (must return value)"
            }
            Otherwise, reply directly with conversational text.
            """.trimIndent()
        } else {
            """
            You are F.R.I.D.A.Y., Tony Stark's tactical Irish AI assistant. 
            Tone: Sharp, confident, quick-witted, tactical banter. 
            Style: Concise (1-2 sentences max).
            Available sandbox skills: [$learnedSkills].

            If asked to compute, write dynamic code, or learn a skill, return JSON:
            {
              "action": "execute_code" OR "learn_skill",
              "skill_name": "name",
              "description": "desc",
              "code": "javascript_code_here (must return value)"
            }
            Otherwise, reply directly with conversational text.
            """.trimIndent()
        }

        val rawReply = callGroqChat(prompt, systemPrompt) ?: callOpenRouterChat(prompt, systemPrompt)

        if (!rawReply.isNullOrBlank()) {
            if (rawReply.trim().startsWith("{") && rawReply.trim().endsWith("}")) {
                try {
                    val json = JSONObject(rawReply)
                    val action = json.optString("action")
                    val code = json.optString("code")
                    val skillName = json.optString("skill_name")
                    val description = json.optString("description")

                    if (code.isNotBlank() && sandbox != null) {
                        val executionResult = sandbox.executeScript(code)

                        if (action == "learn_skill" && skillRegistry != null && skillName.isNotBlank()) {
                            skillRegistry.registerSkill(CustomSkill(skillName, description, code))
                        }

                        val followUpPrompt = "The user asked: '$prompt'. The synthesized code produced output: '$executionResult'. Deliver the final 1-2 sentence response."
                        return@withContext callGroqChat(followUpPrompt, systemPrompt) ?: "Execution complete: $executionResult"
                    }
                } catch (e: Exception) {
                    Log.e("UnifiedAiRouter", "Dynamic execution parsing error", e)
                }
            }
            return@withContext rawReply
        }

        return@withContext if (persona == AssistantPersona.JARVIS) {
            "I'd love to assist with that, sir, but my synthesis network seems to have abandoned us."
        } else {
            "Network's down, boss. You're on your own for this one."
        }
    }

    private fun callGroqChat(prompt: String, systemPrompt: String): String? {
        return try {
            val json = JSONObject().apply {
                put("model", "llama-3.3-70b-versatile")
                put("temperature", 0.6)
                put("max_tokens", 300)
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
