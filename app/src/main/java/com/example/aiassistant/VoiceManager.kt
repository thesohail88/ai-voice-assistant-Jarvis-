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
            Log.e("VoiceManager", "Error setting audio attributes", e)
        }
    }

    fun speak(text: String, persona: AssistantPersona) {
        if (!isTtsReady || text.isBlank()) return

        var cleanSpeech = text.replace(Regex("ACTION_[A-Z_:]+.*"), "").trim()
        cleanSpeech = sanitizeForSsml(cleanSpeech)
        if (cleanSpeech.isBlank()) return

        try {
            val ssmlPayload = if (persona == AssistantPersona.JARVIS) {
                applyJarvisProfile()
                // Deep British male: 118 Hz baseline pitch with steady pacing
                """<speak><prosody pitch="-4st" rate="92%"><emphasis level="moderate">$cleanSpeech</emphasis></prosody></speak>"""
            } else {
                applyFridayProfile()
                // Kerry Condon: 210 Hz mezzo-soprano with authentic Irish cadence
                """<speak><prosody pitch="+3st" rate="104%"><prosody contour="(0%,+0st) (75%,+1st) (100%,+3st)">$cleanSpeech</prosody></prosody></speak>"""
            }

            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, 0.0f)
            }

            tts?.speak(ssmlPayload, TextToSpeech.QUEUE_FLUSH, params, "ASSISTANT_SSML_ID")
        } catch (e: Exception) {
            Log.e("VoiceManager", "TTS SSML error, falling back to raw playback", e)
            tts?.speak(cleanSpeech, TextToSpeech.QUEUE_FLUSH, null, "FALLBACK_ID")
        }
    }

    fun playVoiceSample(persona: AssistantPersona) {
        if (persona == AssistantPersona.JARVIS) {
            speak("Systems operational, sir.", AssistantPersona.JARVIS)
        } else {
            speak("All systems tactical and ready, boss.", AssistantPersona.FRIDAY)
        }
    }

    private fun applyJarvisProfile() {
        tts?.language = Locale.UK
        val availableVoices = tts?.voices ?: emptySet()
        val jarvisVoice = availableVoices.firstOrNull { voice ->
            voice.locale == Locale.UK &&
                    !voice.name.lowercase().contains("female") &&
                    !voice.name.lowercase().contains("woman") &&
                    (voice.name.lowercase().contains("male") ||
                     voice.name.lowercase().contains("en-gb-x-rjs") ||
                     voice.name.lowercase().contains("en-gb-x-gbd"))
        } ?: availableVoices.firstOrNull { it.locale == Locale.UK && !it.isNetworkConnectionRequired }

        if (jarvisVoice != null) {
            tts?.voice = jarvisVoice
        }
        tts?.setPitch(0.80f)
        tts?.setSpeechRate(0.92f)
    }

    private fun applyFridayProfile() {
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
        tts?.setPitch(1.10f)
        tts?.setSpeechRate(1.04f)
    }

    private fun sanitizeForSsml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
            .trim()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        isTtsReady = false
    }
}
