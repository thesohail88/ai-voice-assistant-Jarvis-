package com.example.aiassistant

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

class VoiceManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            }
        }
    }

    fun speak(text: String, persona: AssistantPersona) {
        if (!isTtsReady || text.isBlank()) return

        val cleanSpeech = text.replace(Regex("ACTION_[A-Z_:]+.*"), "").trim()
        if (cleanSpeech.isBlank()) return

        try {
            if (persona == AssistantPersona.JARVIS) {
                applyJarvisAcoustics()
            } else {
                applyFridayAcoustics()
            }

            tts?.speak(cleanSpeech, TextToSpeech.QUEUE_FLUSH, null, "VOICE_PLAYBACK_ID")
        } catch (e: Exception) {
            Log.e("VoiceManager", "TTS error", e)
        }
    }

    private fun applyJarvisAcoustics() {
        tts?.language = Locale.UK
        // Find deep British male voice available in Google TTS engine
        val bestVoice = tts?.voices?.firstOrNull { voice ->
            voice.locale == Locale.UK &&
                    !voice.isNetworkConnectionRequired &&
                    (voice.name.lowercase().contains("en-gb-x-rjs") || 
                     voice.name.lowercase().contains("male") || 
                     voice.features.contains("male"))
        }

        if (bestVoice != null) {
            tts?.voice = bestVoice
        }

        tts?.setPitch(0.82f)       // Lower pitch for resonant British tone
        tts?.setSpeechRate(0.92f)   // Calm, composed delivery
    }

    private fun applyFridayAcoustics() {
        val irishLocale = Locale("en", "IE")
        val result = tts?.setLanguage(irishLocale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.language = Locale.UK
        }

        val bestVoice = tts?.voices?.firstOrNull { voice ->
            (voice.locale.language == "en" && voice.locale.country == "IE") ||
            (voice.locale == Locale.UK && voice.name.lowercase().contains("female"))
        }

        if (bestVoice != null) {
            tts?.voice = bestVoice
        }

        tts?.setPitch(1.18f)       // Tactical female frequency
        tts?.setSpeechRate(1.08f)   // Crisp, fast-paced tempo
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
