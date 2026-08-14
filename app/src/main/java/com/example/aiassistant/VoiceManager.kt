package com.example.aiassistant

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

enum class AssistantPersona { JARVIS, FRIDAY }

class VoiceManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech = TextToSpeech(context, this)
    private var isReady = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            isReady = true
        }
    }

    fun speak(text: String, persona: AssistantPersona) {
        if (!isReady) return

        when (persona) {
            AssistantPersona.JARVIS -> {
                // Pitch down for deep male voice persona
                tts.setPitch(0.70f)
                tts.setSpeechRate(1.0f)
            }
            AssistantPersona.FRIDAY -> {
                // Pitch up for female voice persona
                tts.setPitch(1.30f)
                tts.setSpeechRate(1.05f)
            }
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "VOICE_ID")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
