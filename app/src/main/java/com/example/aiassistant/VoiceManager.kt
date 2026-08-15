package com.example.aiassistant

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

enum class AssistantPersona { JARVIS, FRIDAY }

class VoiceManager(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech = TextToSpeech(context, this)
    private var isReady = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            isReady = true

            // Set audio stream attributes for optimal media clarity
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts.setAudioAttributes(attributes)
            }

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    releaseAudioFocusAndResumeMusic()
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    releaseAudioFocusAndResumeMusic()
                }
            })
        }
    }

    private fun applyHumanVoicePersona(persona: AssistantPersona, languageCode: String) {
        try {
            val targetLocale = Locale.forLanguageTag(languageCode)
            tts.language = targetLocale

            // Search for high-quality, natural/neural voices installed on Android
            val availableVoices = tts.voices
            if (!availableVoices.isNullOrEmpty()) {
                val selectedVoice = when (persona) {
                    AssistantPersona.JARVIS -> {
                        // Look for a British or deeper natural male voice
                        availableVoices.firstOrNull { voice ->
                            val name = voice.name.lowercase()
                            (voice.locale.country.equals("GB", ignoreCase = true) || name.contains("en-gb")) &&
                                    !voice.isNetworkConnectionRequired &&
                                    (name.contains("male") || name.contains("man") || name.contains("#male") || name.contains("en-gb-x-rjs"))
                        } ?: availableVoices.firstOrNull { it.locale.language == targetLocale.language && !it.isNetworkConnectionRequired }
                    }
                    AssistantPersona.FRIDAY -> {
                        // Look for a natural female/crisp voice
                        availableVoices.firstOrNull { voice ->
                            val name = voice.name.lowercase()
                            (name.contains("female") || name.contains("woman") || name.contains("#female") || name.contains("en-us-x-sfg")) &&
                                    !voice.isNetworkConnectionRequired
                        } ?: availableVoices.firstOrNull { it.locale.language == targetLocale.language && !it.isNetworkConnectionRequired }
                    }
                }

                selectedVoice?.let {
                    tts.voice = it
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Natural speech parameters (avoids robotic extreme pitch shifting)
        when (persona) {
            AssistantPersona.JARVIS -> {
                tts.setPitch(0.92f)      // Smooth natural pitch
                tts.setSpeechRate(0.98f)  // Calm, articulated pace
            }
            AssistantPersona.FRIDAY -> {
                tts.setPitch(1.08f)      // Clear, lively pitch
                tts.setSpeechRate(1.02f)  // Prompt, efficient pace
            }
        }
    }

    private fun requestAudioFocusAndPauseMusic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(false)
                .build()

            focusRequest?.let { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    private fun releaseAudioFocusAndResumeMusic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    fun speak(text: String, persona: AssistantPersona, languageCode: String = "en-US") {
        if (!isReady) return

        requestAudioFocusAndPauseMusic()
        applyHumanVoicePersona(persona, languageCode)

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "AI_SPEECH_${System.currentTimeMillis()}")
    }

    fun shutdown() {
        releaseAudioFocusAndResumeMusic()
        tts.stop()
        tts.shutdown()
    }
}
