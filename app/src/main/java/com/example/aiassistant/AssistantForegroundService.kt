package com.example.aiassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AssistantForegroundService : Service() {

    private lateinit var voiceManager: VoiceManager
    private lateinit var deviceController: DeviceController
    private lateinit var speakerVerifier: SpeakerVerifier
    private val geminiClient = GeminiClient("AQ.Ab8RN6J1tryTLk-GI0XGqR7P_cSUfZTkoPE_iPBKXn71_PYvVw")
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        voiceManager = VoiceManager(this)
        deviceController = DeviceController(this)
        speakerVerifier = SpeakerVerifier(this)
        startForeground(1001, createNotification())
    }

    /**
     * Entry point called when voice input is received.
     */
    fun handleIncomingVoice(
        persona: AssistantPersona,
        spokenText: String,
        voiceEmbedding: FloatArray?
    ) {
        // Step 1: Speaker Verification
        if (voiceEmbedding != null && !speakerVerifier.isAuthorizedUser(voiceEmbedding)) {
            // Voice does not match enrolled profile -> ignore completely
            return
        }

        // Step 2: Check for system commands (e.g. YouTube, Timer)
        val wasDeviceAction = deviceController.handleActionCommand(spokenText)
        if (wasDeviceAction) {
            val confirm = if (persona == AssistantPersona.JARVIS) "Right away, sir." else "Done."
            voiceManager.speak(confirm, persona)
            return
        }

        // Step 3: Route prompt to Gemini AI
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
            .setContentTitle("Biometric AI Assistant Active")
            .setContentText("Listening only for your voice...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }

    override fun onDestroy() {
        voiceManager.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
