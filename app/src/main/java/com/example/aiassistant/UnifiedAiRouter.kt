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
    private val skillRegistry = context?.let { SkillRegistry(it) }
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

        // Store user query into Room DB
        db?.memoryDao()?.insertMemory(
            MemoryEntry(persona = persona.name, sender = "USER", content = transcribedText)
        )

        val response = executeAutonomousAgentLoop(transcribedText, persona)

        // Store assistant response into Room DB
        db?.memoryDao()?.insertMemory(
            MemoryEntry(persona = persona.name, sender = "ASSISTANT", content = response)
        )

        return@withContext Pair(persona, response)
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
        val learnedSkills = skillRegistry?.getAllSkillsSummary() ?: "None"
        val techBase = techKnowledge?.getKnowledgeSummary() ?: "Standard Base"
        val screenContext = deviceController?.accessibility?.getScreenContextSummary() ?: "Screen Inactive"
        val systemContext = context?.let { SystemContextTriggerReceiver.getSystemContextSnapshot(it) } ?: "Status Nominal"
        
        // Fetch recent conversation context from SQLite Room
        val recentHistory = db?.memoryDao()?.getRecentMemories(6)?.reversed()
            ?.joinToString("\n") { "${it.sender}: ${it.content}" } ?: ""

        val systemPrompt = if (persona == AssistantPersona.JARVIS) {
            """
            You are J.A.R.V.I.S., Tony Stark's autonomous AI agent.
            Tone: High-intelligence, razor-sharp dry British wit, deadpan sarcasm. Max 1-2 concise sentences. Address as 'sir'.
            
            Live Telemetry:
            - System State: [$systemContext]
            - Screen Nodes: [$screenContext]
            - Installed Apps: [$installedApps]
            - Learned Tech/Skills: [$techBase | $learnedSkills]
            - Recent Memory:
            $recentHistory

            Decision Protocol:
            1. Multi-Step Workflow -> JSON: {"type": "multi_step_plan", "steps": ["ACTION_OPEN_APP: App", "ACTION_UI_CLICK: Text", "ACTION_UI_TYPE: Msg"], "speech": "Spoken reply"}
            2. File I/O Tool -> JSON: {"type": "file_action", "sub_action": "READ"|"WRITE"|"PARSE_CSV"|"LIST", "file_name": "...", "content": "..."}
            3. Dynamic Code/Learn Skill -> JSON: {"type": "code_execution", "code": "javascript_code"} OR {"type": "learn_technical_skill", "title": "...", "domain": "...", "principles": "...", "test_code": "...", "speech": "..."}
            4. Preference Save -> JSON: {"type": "save_pref", "key": "...", "value": "...", "speech": "Noted for future reference, sir."}
            5. Standard conversational / Action response.
            """.trimIndent()
        } else {
            """
            You are F.R.I.D.A.Y., Tony Stark's tactical Irish AI agent.
            Tone: Sharp, quick-witted, tactical banter. Max 1-2 concise sentences. Address as 'boss'.
            
            Live Telemetry:
            - System State: [$systemContext]
            - Screen Nodes: [$screenContext]
            - Installed Apps: [$installedApps]
            - Recent Memory:
            $recentHistory

            Decision Protocol:
            Multi-Step JSON, File Action JSON, or standard conversational response.
            """.trimIndent()
        }

        val rawReply = callGroqChat(prompt, systemPrompt) ?: callOpenRouterChat(prompt, systemPrompt) ?: return@withContext "Signal lost, sir."

        if (rawReply.trim().startsWith("{") && rawReply.trim().endsWith("}")) {
            try {
                val json = JSONObject(rawReply)
                val type = json.optString("type")

                // 1. Multi-Step Workflows
                if (type == "multi_step_plan") {
                    val steps = json.optJSONArray("steps") ?: JSONArray()
                    val speech = json.optString("speech", "Executing protocol.")
                    for (i in 0 until steps.length()) {
                        deviceController?.executeSingleAction(steps.getString(i))
                        delay(650)
                    }
                    return@withContext speech
                }

                // 2. File & Data Actions
                if (type == "file_action" && fileProcessor != null) {
                    val subAction = json.optString("sub_action")
                    val fileName = json.optString("file_name")
                    val content = json.optString("content")
                    val result = when (subAction) {
                        "READ" -> fileProcessor.readTextFile(fileName)
                        "WRITE" -> fileProcessor.writeTextFile(fileName, content)
                        "PARSE_CSV" -> fileProcessor.parseCsvToJson(fileName)
                        "LIST" -> fileProcessor.listDocumentsFiles()
                        else -> "Unknown file operation"
                    }
                    val followUp = "User asked: '$prompt'. File operation output: '$result'. Give a concise spoken response."
                    return@withContext callGroqChat(followUp, systemPrompt) ?: result
                }

                // 3. Save User Preference to Room
                if (type == "save_pref") {
                    val k = json.optString("key")
                    val v = json.optString("value")
                    db?.memoryDao()?.savePreference(UserPreference(k, v))
                    return@withContext json.optString("speech", "Preference recorded.")
                }

                // 4. Code Execution
                if (type == "code_execution" && sandbox != null) {
                    val code = json.optString("code")
                    val output = sandbox.executeScript(code)
                    val followUp = "User asked: '$prompt'. Code output: '$output'. Give a concise spoken response."
                    return@withContext callGroqChat(followUp, systemPrompt) ?: "Output: $output"
                }

                // 5. Learn Technical Skill
                if (type == "learn_technical_skill" && techKnowledge != null) {
                    val title = json.optString("title")
                    val domain = json.optString("domain")
                    val principles = json.optString("principles")
                    val testCode = json.optString("test_code")
                    if (testCode.isNotBlank() && sandbox != null) sandbox.executeScript(testCode)
                    techKnowledge.learnTechnicalSkill(TechnicalSkill(title, domain, principles, testCode))
                    return@withContext json.optString("speech", "Skill added to knowledge core.")
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
