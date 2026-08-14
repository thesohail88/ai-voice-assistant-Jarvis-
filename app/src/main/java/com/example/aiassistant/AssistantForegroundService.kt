package com.example.aiassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AssistantForegroundService : Service() {

    private lateinit var voiceManager: VoiceManager
    private lateinit var deviceController: DeviceController
    private lateinit var speakerVerifier: SpeakerVerifier
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Replace with your Google AI Studio API key
    private val geminiClient = GeminiClient("AQ.Ab8RN6J1tryTLk-GI0XGqR7P_cSUfZTkoPE_iPBKXn71_PYvVw")
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        voiceManager = VoiceManager(this)
        deviceController = DeviceController(this)
        speakerVerifier = SpeakerVerifier(this)

        // Keep CPU active when phone screen locks
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AIAssistant::LockScreenWakeLock").apply {
            acquire(24 * 60 * 60 * 1000L) // 24 hours
        }

        startForeground(1001, createNotification())
    }

    fun handleIncomingVoice(
        persona: AssistantPersona,
        spokenText: String,
        voiceEmbedding: FloatArray?
    ) {
        // 1. Biometric verification
        if (voiceEmbedding != null && !speakerVerifier.isAuthorizedUser(voiceEmbedding)) {
            return
        }

        // 2. Hardware / Call Intent actions
        val wasDeviceAction = deviceController.handleActionCommand(spokenText)
        if (wasDeviceAction) {
            val confirm = if (persona == AssistantPersona.JARVIS) "Executing now, sir." else "Done."
            voiceManager.speak(confirm, persona)
            return
        }

        // 3. AI Reasoning
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
            .setContentText("Listening continuously & on lock screen...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }

    override fun onDestroy() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        voiceManager.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
