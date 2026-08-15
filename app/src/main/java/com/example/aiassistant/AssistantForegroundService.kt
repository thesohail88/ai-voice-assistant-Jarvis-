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
import android.os.IBinder
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class AssistantForegroundService : Service() {

    private lateinit var voiceManager: VoiceManager
    private lateinit var deviceController: DeviceController
    private lateinit var speakerVerifier: SpeakerVerifier
    private lateinit var languageManager: ContactLanguageManager
    private lateinit var audioManager: AudioManager
    private var wakeLock: PowerManager.WakeLock? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var recognizerIntent: Intent

    // ⚠️ Make sure to paste your actual Gemini API key here
    private val geminiClient = GeminiClient("AQ.Ab8RN6LGTH90nHMefG8sOBoVQi3FEo70G9rIYIgGni-lXwSbBg")
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        voiceManager = VoiceManager(this)
        deviceController = DeviceController(this)
        speakerVerifier = SpeakerVerifier(this)
        languageManager = ContactLanguageManager(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AIAssistant::LockScreenWakeLock").apply {
            acquire(24 * 60 * 60 * 1000L)
        }

        startForeground(1001, createNotification())
        initSpeechRecognizer()
    }

    private fun initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                // Restart listening automatically if it times out or errors
                restartListening()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.firstOrNull() ?: ""
                processSpokenText(spokenText)
                restartListening()
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        startListening()
    }

    private fun startListening() {
        try {
            speechRecognizer?.startListening(recognizerIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun restartListening() {
        serviceScope.launch {
            delay(500)
            startListening()
        }
    }

    private fun processSpokenText(text: String) {
        val lower = text.lowercase().trim()

        when {
            lower.contains("jarvis") -> {
                val command = lower.substringAfter("jarvis").trim()
                handleIncomingVoice(AssistantPersona.JARVIS, command)
            }
            lower.contains("friday") -> {
                val command = lower.substringAfter("friday").trim()
                handleIncomingVoice(AssistantPersona.FRIDAY, command)
            }
        }
    }

    private fun handleIncomingVoice(persona: AssistantPersona, spokenText: String) {
        if (spokenText.isEmpty()) {
            val prompt = if (persona == AssistantPersona.JARVIS) "At your service, sir." else "Online. How can I help?"
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("ACTION_CALL_ANSWERED", false) == true) {
            val callerNumber = intent.getStringExtra("CALLER_NUMBER") ?: "Caller"
            handleAutoAnsweredCall(callerNumber)
        }
        return START_STICKY
    }

    private fun handleAutoAnsweredCall(callerNumber: String) {
        serviceScope.launch {
            delay(1000)
            audioManager.mode = AudioManager.MODE_IN_CALL
            audioManager.isSpeakerphoneOn = true

            val targetLanguage = languageManager.getLanguageForContact(callerNumber)
            val prompt = "Generate a short greeting answering the phone for an unavailable owner in language code $targetLanguage. State you are taking a message."
            val greeting = geminiClient.queryAssistant(prompt, AssistantPersona.JARVIS)

            voiceManager.speak(greeting, AssistantPersona.JARVIS, targetLanguage)
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
            .setContentTitle("Jarvis & Friday Active")
            .setContentText("Listening continuously for wake words...")
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
