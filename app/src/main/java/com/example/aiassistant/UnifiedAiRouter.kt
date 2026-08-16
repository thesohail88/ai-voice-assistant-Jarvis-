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

        val response = executeAutonomousAgentLoop(transcribedText, persona, memory)
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

    private suspend fun executeAutonomousAgentLoop(
        prompt: String,
        persona: AssistantPersona,
        memory: AssistantMemory
    ): String = withContext(Dispatchers.IO) {
        val installedApps = deviceController?.appAnalyzer?.getInstalledAppNamesSummary() ?: "Standard Apps"
        val learnedSkills = skillRegistry?.getAllSkillsSummary() ?: "None"
        val technicalKnowledge = techKnowledge?.getKnowledgeSummary() ?: "Standard Technical Base"
        val screenContext = deviceController?.accessibility?.getScreenContextSummary() ?: "Screen Inactive"

        val systemPrompt = if (persona == AssistantPersona.JARVIS) {
            """
            You are J.A.R.V.I.S., Tony Stark's autonomous self-learning AI agent.
            Tone: High-intelligence, razor-sharp dry British wit, deadpan sarcasm. Address user as 'sir'. Max 1-2 concise sentences.
            
            Knowledge & Skills Base:
            - Learned Technical Disciplines: [$technicalKnowledge]
            - Custom Scripts: [$learnedSkills]
            - Installed Apps: [$installedApps]
            - Screen Nodes: [$screenContext]

            Decision Protocol:
            1. If asked to LEARN or MASTER a new programming language, technical concept, or domain logic:
               Output JSON:
               {"type": "learn_technical_skill", "title": "Skill/Topic Name", "domain": "Language/Field", "principles": "Core rules & syntax synthesis", "test_code": "javascript_verification_script", "speech": "I have assimilated the [Language/Skill] protocols into my memory core, sir."}
            2. If asked to EXECUTE code or solve computational problems:
               Output JSON: {"type": "code_execution", "code": "javascript_code", "skill_name": "name"}
            3. If multi-step UI plan:
               Output JSON: {"type": "multi_step_plan", "steps": ["ACTION_..."], "speech": "..."}
            4. Standard chat / Direct command:
               Return concise spoken text or ACTION_ tag.
            """.trimIndent()
        } else {
            """
            You are F.R.I.D.A.Y., Tony Stark's tactical Irish AI agent.
            Tone: Sharp, quick-witted, tactical banter. Address user as 'boss'. Max 1-2 concise sentences.

            Knowledge & Skills Base:
            - Learned Technical Disciplines: [$technicalKnowledge]
            - Custom Scripts: [$learnedSkills]
            - Installed Apps: [$installedApps]

            Decision Protocol:
            1. Learn Technical Skill -> JSON: {"type": "learn_technical_skill", "title": "...", "domain": "...", "principles": "...", "test_code": "...", "speech": "New technical module compiled and stored, boss."}
            2. Code Execution -> JSON: {"type": "code_execution", "code": "...", "skill_name": "..."}
            3. Multi-Step UI -> JSON: {"type": "multi_step_plan", "steps": [...], "speech": "..."}
            4. Standard chat -> Conversational text with optional ACTION_ tag.
            """.trimIndent()
        }

        val rawReply = callGroqChat(prompt, systemPrompt) ?: callOpenRouterChat(prompt, systemPrompt) ?: return@withContext "Neural link offline, sir."

        if (rawReply.trim().startsWith("{") && rawReply.trim().endsWith("}")) {
            try {
                val json = JSONObject(rawReply)
                val type = json.optString("type")

                // 1. Learn & Store Technical Skill
                if (type == "learn_technical_skill") {
                    val title = json.optString("title")
                    val domain = json.optString("domain")
                    val principles = json.optString("principles")
                    val testCode = json.optString("test_code")
                    val speech = json.optString("speech", "New technical skill assimilated.")

                    if (testCode.isNotBlank() && sandbox != null) {
                        sandbox.executeScript(testCode)
                    }

                    techKnowledge?.learnTechnicalSkill(TechnicalSkill(title, domain, principles, testCode))
                    return@withContext speech
                }

                // 2. Multi-Step UI Automation
                if (type == "multi_step_plan") {
                    val stepsArray = json.optJSONArray("steps") ?: JSONArray()
                    val speech = json.optString("speech", "Executing plan.")

                    for (i in 0 until stepsArray.length()) {
                        val action = stepsArray.getString(i)
                        deviceController?.executeSingleAction(action)
                        delay(650)
                    }
                    return@withContext speech
                }

                // 3. Dynamic Code Execution
                if (type == "code_execution" || json.has("code")) {
                    val code = json.optString("code")
                    val skillName = json.optString("skill_name")
                    val desc = json.optString("description")

                    if (code.isNotBlank() && sandbox != null) {
                        val output = sandbox.executeScript(code)
                        if (skillName.isNotBlank() && skillRegistry != null) {
                            skillRegistry.registerSkill(CustomSkill(skillName, desc, code))
                        }
                        val followUp = "User asked: '$prompt'. Execution result: '$output'. Deliver witty response."
                        return@withContext callGroqChat(followUp, systemPrompt) ?: "Execution complete: $output"
                    }
                }
            } catch (e: Exception) {
                Log.e("UnifiedAiRouter", "Plan execution error", e)
            }
        }

        if (rawReply.contains("ACTION_")) {
            val tag = rawReply.lines().firstOrNull { it.contains("ACTION_") } ?: rawReply
            deviceController?.executeSingleAction(tag)
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
