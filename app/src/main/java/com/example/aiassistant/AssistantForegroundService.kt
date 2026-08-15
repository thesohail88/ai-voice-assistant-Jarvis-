package com.example.aiassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AssistantForegroundService : Service() {

    private lateinit var voiceManager: VoiceManager
    private lateinit var deviceController: DeviceController
    private lateinit var speakerVerifier: SpeakerVerifier
    private lateinit var languageManager: ContactLanguageManager
    private lateinit var audioManager: AudioManager
    private var wakeLock: PowerManager.WakeLock? = null

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

            // Look up specific language for this contact
            val targetLanguage = languageManager.getLanguageForContact(callerNumber)

            val prompt = "Generate a short greeting answering the phone for an unavailable owner in language code $targetLanguage. State you are taking a message."
            val greeting = geminiClient.queryAssistant(prompt, AssistantPersona.JARVIS)

            voiceManager.speak(greeting, AssistantPersona.JARVIS, targetLanguage)
        }
    }

    fun handleIncomingVoice(
        persona: AssistantPersona,
        spokenText: String,
        voiceEmbedding: FloatArray?
    ) {
        if (voiceEmbedding != null && !speakerVerifier.isAuthorizedUser(voiceEmbedding)) {
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
            .setContentText("Multi-language call screener active...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }

    override fun onDestroy() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        voiceManager.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
