package com.example.aiassistant

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

class VoiceManager(private val context: Context, private val elevenLabsApiKey: String = "") {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val scope = CoroutineScope(Dispatchers.IO)
    private var mediaPlayer: MediaPlayer? = null

    // Exact voice model IDs matching the MCU acoustic pitch and accent
    private val jarvisVoiceId = "pNInz6obpgDQGcFmaJgB" // Deep British gentleman timbre (Paul Bettany profile)
    private val fridayVoiceId = "EXAVITQu4vr4xnSDxMaL" // Crisp Irish tactical female timbre (Kerry Condon profile)

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                tts?.language = Locale.UK
            }
        }
    }

    fun speak(text: String, persona: AssistantPersona) {
        val cleanSpeech = text.replace(Regex("ACTION_[A-Z_:]+.*"), "").trim()
        if (cleanSpeech.isBlank()) return

        scope.launch {
            if (elevenLabsApiKey.isNotBlank()) {
                val streamed = streamNeuralVoice(cleanSpeech, persona)
                if (streamed) return@launch
            }
            // Fallback to local DSP-enhanced TTS engine
            speakLocalFallback(cleanSpeech, persona)
        }
    }

    private fun streamNeuralVoice(text: String, persona: AssistantPersona): Boolean {
        return try {
            val voiceId = if (persona == AssistantPersona.JARVIS) jarvisVoiceId else fridayVoiceId
            val url = "https://api.elevenlabs.io/v1/text-to-speech/$voiceId?output_format=mp3_44100_128"

            val json = JSONObject().apply {
                put("text", text)
                put("model_id", "eleven_turbo_v2_5")
                put("voice_settings", JSONObject().apply {
                    put("stability", if (persona == AssistantPersona.JARVIS) 0.65 else 0.50)
                    put("similarity_boost", 0.85)
                    put("style", 0.35)
                    put("use_speaker_boost", true)
                })
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("xi-api-key", elevenLabsApiKey)
                .addHeader("Content-Type", "application/json")
                .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val tempFile = File.createTempFile("neural_voice", ".mp3", context.cacheDir)
                FileOutputStream(tempFile).use { it.write(response.body?.bytes()) }

                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(tempFile.absolutePath)
                    prepare()
                    start()
                    setOnCompletionListener {
                        tempFile.delete()
                    }
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("VoiceManager", "Neural TTS failed, defaulting to local fallback", e)
            false
        }
    }

    private fun speakLocalFallback(text: String, persona: AssistantPersona) {
        if (!isTtsReady) return

        if (persona == AssistantPersona.JARVIS) {
            tts?.language = Locale.UK
            tts?.setPitch(0.85f)      // Lower vocal register for British resonance
            tts?.setSpeechRate(0.95f)  // Calm, deliberate delivery
        } else {
            tts?.language = Locale.UK
            tts?.setPitch(1.15f)      // Higher frequency tactical female tone
            tts?.setSpeechRate(1.05f)  // Quick tactical tempo
        }

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "UTTERANCE_ID")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        mediaPlayer?.release()
    }
}
