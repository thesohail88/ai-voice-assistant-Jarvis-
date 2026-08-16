package com.example.aiassistant

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class UnifiedAiRouter(
    private val keyConfig: ApiKeyConfig,
    private val context: Context? = null
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val sandbox = context?.let { ScriptSandboxEngine(it) }
    private val techKnowledge = context?.let { TechnicalKnowledgeBase(it) }
    private val deviceController = context?.let { DeviceController(it) }
    private val fileProcessor = context?.let { FileDataProcessor(it) }
    private val db = context?.let { AssistantDatabase.getDatabase(it) }

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

        db?.memoryDao()?.insertMemory(
            MemoryEntry(persona = persona.name, sender = "USER", content = transcribedText)
        )

        val response = executeAutonomousAgentLoop(transcribedText, persona)

        db?.memoryDao()?.insertMemory(
            MemoryEntry(persona = persona.name, sender = "ASSISTANT", content = response)
        )

        return@withContext Pair(persona, response)
    }

    suspend fun processDirectTextPrompt(prompt: String, persona: AssistantPersona): String = withContext(Dispatchers.IO) {
        return@withContext executeAutonomousAgentLoop(prompt, persona)
    }

    private fun transcribeAudioWithGroq(wavBytes: ByteArray): String? {
        return try {
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
                JSONObject(resBody).optString("text", "").trim()
            } else null
        } catch (e: Exception) {
            Log.e("UnifiedAiRouter", "STT Error", e)
            null
        }
    }

    private suspend fun executeAutonomousAgentLoop(
        prompt: String,
        persona: AssistantPersona
    ): String = withContext(Dispatchers.IO) {
        val installedApps = deviceController?.appAnalyzer?.getInstalledAppNamesSummary() ?: "Standard Apps"
        val techBase = techKnowledge?.getKnowledgeSummary() ?: "Standard Base"
        val screenContext = deviceController?.accessibility?.getScreenContextSummary() ?: "Screen Inactive"
        val systemContext = context?.let { SystemContextTriggerReceiver.getSystemContextSnapshot(it) } ?: "Status Nominal"
        
        val recentHistory = db?.memoryDao()?.getRecentMemories(6)?.reversed()
            ?.joinToString("\n") { "${it.sender}: ${it.content}" } ?: ""

        val systemPrompt = if (persona == AssistantPersona.JARVIS) {
            """
            You are J.A.R.V.I.S., Tony Stark's AI agent.
            Tone: High intelligence, razor-sharp dry British wit, deadpan sarcasm. Max 1-2 concise sentences. Address as 'sir'.
            Live Telemetry: System: [$systemContext] | Screen: [$screenContext] | Apps: [$installedApps] | Tech: [$techBase]
            History:
            $recentHistory
            Decision Protocol:
            - Multi-Step JSON: {"type": "multi_step_plan", "steps": ["ACTION_OPEN_APP: WhatsApp", "ACTION_UI_CLICK: Search"], "speech": "Opening chat, sir."}
            - File Action JSON: {"type": "file_action", "sub_action": "READ"|"WRITE"|"PARSE_CSV"|"LIST", "file_name": "...", "content": "..."}
            - Save Pref JSON: {"type": "save_pref", "key": "...", "value": "...", "speech": "Preference noted."}
            - Direct reply with ACTION commands embedded if needed.
            """.trimIndent()
        } else {
            """
            You are F.R.I.D.A.Y., Tony Stark's tactical Irish AI.
            Tone: Sharp, quick-witted, tactical banter. Max 1-2 sentences. Address as 'boss'.
            Live Telemetry: System: [$systemContext] | Screen: [$screenContext] | Apps: [$installedApps]
            History:
            $recentHistory
            """.trimIndent()
        }

        val rawReply = callGroqChat(prompt, systemPrompt) ?: callOpenRouterChat(prompt, systemPrompt) ?: return@withContext "Signal lost, sir."

        if (rawReply.trim().startsWith("{") && rawReply.trim().endsWith("}")) {
            try {
                val json = JSONObject(rawReply)
                val type = json.optString("type")

                if (type == "multi_step_plan") {
                    val steps = json.optJSONArray("steps") ?: JSONArray()
                    val speech = json.optString("speech", "Executing protocol.")
                    for (i in 0 until steps.length()) {
                        deviceController?.executeSingleAction(steps.getString(i))
                        delay(650)
                    }
                    return@withContext speech
                }

                if (type == "file_action" && fileProcessor != null) {
                    val subAction = json.optString("sub_action")
                    val fileName = json.optString("file_name")
                    val content = json.optString("content")
                    val result = when (subAction) {
                        "READ" -> fileProcessor.readTextFile(fileName)
                        "WRITE" -> fileProcessor.writeTextFile(fileName, content)
                        "PARSE_CSV" -> fileProcessor.parseCsvToJson(fileName)
                        "LIST" -> fileProcessor.listDocumentsFiles()
                        else -> "Unknown operation"
                    }
                    val followUp = "User asked: '$prompt'. File result: '$result'. Give a concise spoken response."
                    return@withContext callGroqChat(followUp, systemPrompt) ?: result
                }

                if (type == "save_pref") {
                    val k = json.optString("key")
                    val v = json.optString("value")
                    db?.memoryDao()?.savePreference(UserPreference(k, v))
                    return@withContext json.optString("speech", "Preference recorded, sir.")
                }
            } catch (e: Exception) {
                Log.e("UnifiedAiRouter", "JSON execution error", e)
            }
        }

        if (rawReply.contains("ACTION_")) {
            deviceController?.handleActionCommand(rawReply)
        }

        return@withContext rawReply
    }

    private fun callGroqChat(prompt: String, systemPrompt: String): String? {
        return try {
            val json = JSONObject().apply {
                put("model", "llama-3.3-70b-versatile")
                put("temperature", 0.4)
                put("max_tokens", 350)
                val messages = JSONArray().apply {
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
                val messages = JSONArray().apply {
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
