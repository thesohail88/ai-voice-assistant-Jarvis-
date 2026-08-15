package com.example.aiassistant

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

class VoiceManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isInitialized = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.language = Locale.US
        } else {
            Log.e("VoiceManager", "TTS initialization failed.")
        }
    }

    fun speak(text: String, persona: AssistantPersona, languageCode: String = "en") {
        if (!isInitialized || tts == null) return

        try {
            val locale = when (languageCode.lowercase()) {
                "hi" -> Locale("hi", "IN")
                "es" -> Locale("es", "ES")
                "fr" -> Locale.FRANCE
                "de" -> Locale.GERMANY
                "ar" -> Locale("ar")
                "zh" -> Locale.CHINESE
                else -> Locale.US
            }
            tts?.language = locale

            if (persona == AssistantPersona.JARVIS) {
                tts?.setPitch(0.92f)
                tts?.setSpeechRate(0.98f)
                selectVoice(genderFemale = false, locale = locale)
            } else {
                tts?.setPitch(1.08f)
                tts?.setSpeechRate(1.02f)
                selectVoice(genderFemale = true, locale = locale)
            }

            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "UTTERANCE_ID")
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error speaking", e)
        }
    }

    private fun selectVoice(genderFemale: Boolean, locale: Locale) {
        val availableVoices = tts?.voices ?: return
        for (voice in availableVoices) {
            if (voice.locale.language == locale.language) {
                val voiceName = voice.name.lowercase()
                if (genderFemale && (voiceName.contains("female") || voiceName.contains("sfg") || voiceName.contains("woman"))) {
                    tts?.voice = voice
                    return
                } else if (!genderFemale && (voiceName.contains("male") || voiceName.contains("smb") || voiceName.contains("man"))) {
                    tts?.voice = voice
                    return
                }
            }
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
