package com.example.aiassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

class AssistantForegroundService : Service() {

    private lateinit var voiceManager: VoiceManager
    private lateinit var deviceController: DeviceController
    private lateinit var languageManager: ContactLanguageManager
    private lateinit var audioManager: AudioManager
    private var wakeLock: PowerManager.WakeLock? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var recognizerIntent: Intent
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListening = false

    private val geminiClient = GeminiClient("AQ.Ab8RN6LGTH90nHMefG8sOBoVQi3FEo70G9rIYIgGni-lXwSbBg")
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        voiceManager = VoiceManager(this)
        deviceController = DeviceController(this)
        languageManager = ContactLanguageManager(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AIAssistant::LockScreenWakeLock").apply {
            acquire(24 * 60 * 60 * 1000L)
        }

        startForeground(1001, createNotification())
        
        mainHandler.postDelayed({
            setupAndStartRecognizer()
        }, 1000)
    }

    private fun setupAndStartRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showToast("Speech recognition not available on this device")
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString())
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                Log.d("AIAssistant", "Ready and listening...")
            }

            override fun onBeginningOfSpeech() {
                Log.d("AIAssistant", "Speech detected...")
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
            }

            override fun onError(errorCode: Int) {
                isListening = false
                Log.e("AIAssistant", "Recognizer error code: $errorCode")
                // Graceful backoff to avoid recognizer throttle loop
                val retryDelay = if (errorCode == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500L else 600L
                mainHandler.postDelayed({
                    startListeningSafe()
                }, retryDelay)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    Log.d("AIAssistant", "Spoken text: ${matches.joinToString()}")
                    for (match in matches) {
                        if (evaluateWakeWords(match)) break
                    }
                }
                startListeningSafe()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                partials?.firstOrNull()?.let {
                    evaluateWakeWords(it)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        startListeningSafe()
    }

    private fun startListeningSafe() {
        mainHandler.post {
            try {
                if (!isListening) {
                    speechRecognizer?.startListening(recognizerIntent)
                }
            } catch (e: Exception) {
                Log.e("AIAssistant", "Error starting listening", e)
                mainHandler.postDelayed({ setupAndStartRecognizer() }, 2000)
            }
        }
    }

    private fun evaluateWakeWords(text: String): Boolean {
        val lower = text.lowercase().trim()

        if (lower.contains("jarvis")) {
            val command = lower.substringAfter("jarvis").trim()
            handleIncomingVoice(AssistantPersona.JARVIS, command)
            return true
        } else if (lower.contains("friday")) {
            val command = lower.substringAfter("friday").trim()
            handleIncomingVoice(AssistantPersona.FRIDAY, command)
            return true
        }
        return false
    }

    private fun handleIncomingVoice(persona: AssistantPersona, spokenText: String) {
        showToast("${persona.name} triggered: $spokenText")

        if (spokenText.isEmpty()) {
            val prompt = if (persona == AssistantPersona.JARVIS) "Yes, sir?" else "I'm listening."
            voiceManager.speak(prompt, persona)
            return
        }

        val wasDeviceAction = deviceController.handleActionCommand(spokenText)
        if (wasDeviceAction) {
            val confirm = if (persona == AssistantPersona.JARVIS) "Executing now, sir." else "Done."
            voiceManager.speak(confirm, persona)
            return
        }

        serviceScope.launch {
            val reply = geminiClient.queryAssistant(spokenText, persona)
            voiceManager.speak(reply, persona)
        }
    }

    private fun showToast(msg: String) {
        mainHandler.post {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun createNotification(): Notification {
        val channelId = "assistant_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Assistant Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Dual AI Assistant")
            .setContentText("Actively listening for 'Jarvis' and 'Friday'...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        voiceManager.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
