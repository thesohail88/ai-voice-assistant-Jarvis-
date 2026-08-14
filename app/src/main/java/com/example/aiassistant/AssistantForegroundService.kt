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
    private val geminiClient = GeminiClient("YOUR_GEMINI_API_KEY") // Paste your free key from Google AI Studio
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        voiceManager = VoiceManager(this)
        deviceController = DeviceController(this)
        startForeground(1001, createNotification())
    }

    fun processVoiceCommand(persona: AssistantPersona, spokenText: String) {
        val wasDeviceAction = deviceController.handleActionCommand(spokenText)

        if (wasDeviceAction) {
            val confirm = if (persona == AssistantPersona.JARVIS) "Right away, sir." else "Done."
            voiceManager.speak(confirm, persona)
            return
        }

        // Send to Gemini LLM for reasoning
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
            .setContentTitle("Dual AI Assistant Active")
            .setContentText("Jarvis & Friday ready for commands...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }

    override fun onDestroy() {
        voiceManager.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
