package com.example.aiassistant

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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

    /**
     * Pauses third-party music playback (Spotify, YT Music, etc.)
     */
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

    /**
     * Releases audio focus so background music continues playing.
     */
    private fun releaseAudioFocusAndResumeMusic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    fun speak(text: String, persona: AssistantPersona) {
        if (!isReady) return

        requestAudioFocusAndPauseMusic()

        when (persona) {
            AssistantPersona.JARVIS -> {
                tts.setPitch(0.70f)
                tts.setSpeechRate(1.0f)
            }
            AssistantPersona.FRIDAY -> {
                tts.setPitch(1.30f)
                tts.setSpeechRate(1.05f)
            }
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "AI_RESPONSE_UTTERANCE")
    }

    fun shutdown() {
        releaseAudioFocusAndResumeMusic()
        tts.stop()
        tts.shutdown()
    }
}
