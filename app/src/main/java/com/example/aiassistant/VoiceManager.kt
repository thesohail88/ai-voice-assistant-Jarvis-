package com.example.aiassistant

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

class VoiceManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    @Volatile private var isTtsReady = false

    init {
        tts = TextToSpeech(context, { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                configureAudioAttributes()
            }
        }, "com.google.android.tts")
    }

    private fun configureAudioAttributes() {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(audioAttributes)
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error configuring audio attributes", e)
        }
    }

    fun speak(text: String, persona: AssistantPersona) {
        if (!isTtsReady || text.isBlank()) return

        var cleanSpeech = text.replace(Regex("ACTION_[A-Z_:]+.*"), "").trim()
        cleanSpeech = humanizeProsody(cleanSpeech)
        if (cleanSpeech.isBlank()) return

        try {
            if (persona == AssistantPersona.JARVIS) {
                applyJarvisMaleProfile()
            } else {
                applyFridayFemaleProfile()
            }

            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, 0.0f)
            }

            tts?.speak(cleanSpeech, TextToSpeech.QUEUE_FLUSH, params, "JARVIS_SYNTHESIS_ID")
        } catch (e: Exception) {
            Log.e("VoiceManager", "TTS playback error", e)
        }
    }

    private fun applyJarvisMaleProfile() {
        val ukLocale = Locale.UK
        tts?.language = ukLocale

        val availableVoices = tts?.voices ?: emptySet()
        val jarvisMaleVoice = availableVoices.firstOrNull { voice ->
            voice.locale == Locale.UK &&
                    !voice.name.lowercase().contains("female") &&
                    !voice.name.lowercase().contains("woman") &&
                    (voice.name.lowercase().contains("male") ||
                     voice.name.lowercase().contains("en-gb-x-rjs") ||
                     voice.name.lowercase().contains("en-gb-x-gbd"))
        } ?: availableVoices.firstOrNull { it.locale == Locale.UK && !it.isNetworkConnectionRequired }

        if (jarvisMaleVoice != null) {
            tts?.voice = jarvisMaleVoice
        }

        // Fixed Pitch & Rate: Deep Resonant British Tone
        tts?.setPitch(0.82f)
        tts?.setSpeechRate(0.92f)
    }

    private fun applyFridayFemaleProfile() {
        val irishLocale = Locale("en", "IE")
        val langResult = tts?.setLanguage(irishLocale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.language = Locale.UK
        }

        val availableVoices = tts?.voices ?: emptySet()
        val fridayVoice = availableVoices.firstOrNull { voice ->
            (voice.locale.language == "en" && voice.locale.country == "IE") ||
            (voice.locale == Locale.UK && (voice.name.lowercase().contains("female") || voice.name.lowercase().contains("en-gb-x-gbf")))
        }

        if (fridayVoice != null) {
            tts?.voice = fridayVoice
        }

        // Fixed Pitch & Rate: Tactical Crisp Delivery
        tts?.setPitch(1.18f)
        tts?.setSpeechRate(1.08f)
    }

    private fun humanizeProsody(input: String): String {
        return input
            .replace("...", ", ")
            .replace(" - ", ", ")
            .replace(";", ",")
            .replace(Regex("(?<=[a-zA-Z]),(?=[a-zA-Z])"), ", ")
            .trim()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        isTtsReady = false
    }
}
