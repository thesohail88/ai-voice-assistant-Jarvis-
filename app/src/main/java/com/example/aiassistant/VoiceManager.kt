package com.example.aiassistant

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

class VoiceManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    @Volatile private var isTtsReady = false

    init {
        // Initialize Google TTS engine directly for natural neural voices
        tts = TextToSpeech(context, { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                configureNaturalAudioAttributes()
            }
        }, "com.google.android.tts")
    }

    private fun configureNaturalAudioAttributes() {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(audioAttributes)
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error setting audio attributes", e)
        }
    }

    fun speak(text: String, persona: AssistantPersona) {
        if (!isTtsReady || text.isBlank()) return

        // Strip action macros and normalize text for realistic human prosody
        var cleanSpeech = text.replace(Regex("ACTION_[A-Z_:]+.*"), "").trim()
        cleanSpeech = humanizeProsody(cleanSpeech)
        if (cleanSpeech.isBlank()) return

        try {
            if (persona == AssistantPersona.JARVIS) {
                applyJarvisHumanProfile()
            } else {
                applyFridayHumanProfile()
            }

            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, 0.0f)
            }

            tts?.speak(cleanSpeech, TextToSpeech.QUEUE_FLUSH, params, "JARVIS_PROSODY_UTTERANCE")
        } catch (e: Exception) {
            Log.e("VoiceManager", "TTS playback error", e)
        }
    }

    private fun applyJarvisHumanProfile() {
        val ukLocale = Locale.UK
        tts?.language = ukLocale

        // Select the highest-quality natural/neural British male voice available
        val availableVoices = tts?.voices ?: emptySet()
        val jarvisVoice = availableVoices.firstOrNull { voice ->
            voice.locale == Locale.UK &&
                    !voice.name.lowercase().contains("female") &&
                    (voice.name.lowercase().contains("en-gb-x-gbd") ||
                     voice.name.lowercase().contains("en-gb-x-rjs") ||
                     voice.name.lowercase().contains("male") ||
                     voice.quality >= Voice.QUALITY_HIGH)
        } ?: availableVoices.firstOrNull { it.locale == Locale.UK && !it.isNetworkConnectionRequired }

        if (jarvisVoice != null) {
            tts?.voice = jarvisVoice
        }

        // Acoustic profile matching Paul Bettany (resonant, steady British delivery)
        tts?.setPitch(0.86f)
        tts?.setSpeechRate(0.94f)
    }

    private fun applyFridayHumanProfile() {
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

        // Tactical, clear cadence matching Kerry Condon
        tts?.setPitch(1.12f)
        tts?.setSpeechRate(1.04f)
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
