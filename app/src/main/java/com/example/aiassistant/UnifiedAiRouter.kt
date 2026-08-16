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
    private val appAnalyzer = context?.let { AppAnalyzer(it) }

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
            lowerText.contains("jarvis") || lowerText.contains("travis") -> AssistantPersona.JARVIS
            lowerText.contains("friday") || lowerText.contains("frieda") -> AssistantPersona.FRIDAY
            else -> return@withContext Pair(null, null)
        }

        val response = queryAssistant(transcribedText, persona, memory)
        return@withContext Pair(persona, response)
    }

    private fun transcribeAudioWithGroq(wavBytes: ByteArray): String? {
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "audio.wav", wavBytes.toRequestBody("audio/wav".toMediaTypeOrNull()))
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
        val installedApps = appAnalyzer?.getInstalledAppNamesSummary() ?: "Standard Applications"

        val actionGrammar = """
            Available Device Action Tags (append the exact tag to execute):
            - Open any App: ACTION_OPEN_APP: [Exact or closest app name]
            - Call: ACTION_CALL: [Name/Number]
            - Home / Back / Recents: ACTION_NAV_HOME / ACTION_NAV_BACK / ACTION_NAV_RECENTS
            - Notifications: ACTION_NOTIFICATIONS
            - Screenshot: ACTION_SCREENSHOT
            - Lock Phone: ACTION_LOCK_SCREEN
            - Music: ACTION_MEDIA_PLAY / ACTION_MEDIA_PAUSE / ACTION_MEDIA_NEXT / ACTION_MEDIA_PREV
            - Volume: ACTION_VOLUME_UP / ACTION_VOLUME_DOWN / ACTION_VOLUME_MAX / ACTION_VOLUME_MUTE
            - Flashlight: ACTION_FLASHLIGHT_ON / ACTION_FLASHLIGHT_OFF
            - Settings: ACTION_SETTINGS_WIFI / ACTION_SETTINGS_BLUETOOTH / ACTION_SETTINGS_MAIN
            - Alarm: ACTION_SET_ALARM: [Hour24]:[Minute]
            - Timer: ACTION_SET_TIMER: [Seconds]
            - YouTube Search: ACTION_SEARCH_YOUTUBE: [Query]
            - Web Search: ACTION_SEARCH_WEB: [Query]
            - UI Click / Type: ACTION_UI_CLICK: [Text] / ACTION_UI_TYPE: [Text]
            - Scroll: ACTION_SCROLL_DOWN / ACTION_SCROLL_UP
        """.trimIndent()

        val systemPrompt = if (persona == AssistantPersona.JARVIS) {
            """
            You are J.A.R.V.I.S., Tony Stark's personal British AI assistant.
            Tone: High-intelligence, razor-sharp dry wit, deadpan sarcasm. Address user as 'sir'. Max 1-2 concise sentences.
            
            Installed Apps on User's Phone:
            [$installedApps]

            $actionGrammar

            If asked to open/launch any app or game, confirm with witty delivery and append ACTION_OPEN_APP: [App Name].
            If asked for custom math/programming, output JSON: {"action":"execute_code","code":"..."}
            """.trimIndent()
        } else {
            """
            You are F.R.I.D.A.Y., Tony Stark's tactical Irish AI assistant.
            Tone: Sharp, quick-witted, tactical banter. Address user as 'boss'. Max 1-2 concise sentences.

            Installed Apps on User's Phone:
            [$installedApps]

            $actionGrammar

            If asked to open/launch any app or game, confirm with snappy delivery and append ACTION_OPEN_APP: [App Name].
            If asked for custom math/programming, output JSON: {"action":"execute_code","code":"..."}
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

                        val followUpPrompt = "User asked: '$prompt'. Dynamic code output: '$executionResult'. Deliver final response."
                        return@withContext callGroqChat(followUpPrompt, systemPrompt) ?: "Execution complete: $executionResult"
                    }
                } catch (e: Exception) {
                    Log.e("UnifiedAiRouter", "Execution error", e)
                }
            }
            return@withContext rawReply
        }

        return@withContext if (persona == AssistantPersona.JARVIS) "All systems are currently unresponsive, sir." else "Network's completely down, boss."
    }

    private fun callGroqChat(prompt: String, systemPrompt: String): String? {
        return try {
            val json = JSONObject().apply {
                put("model", "llama-3.3-70b-versatile")
                put("temperature", 0.5)
                put("max_tokens", 250)
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
