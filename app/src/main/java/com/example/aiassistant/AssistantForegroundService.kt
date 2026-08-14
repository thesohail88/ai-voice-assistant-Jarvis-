package com.example.aiassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AssistantForegroundService : Service() {

    private lateinit var voiceManager: VoiceManager

    override fun onCreate() {
        super.onCreate()
        voiceManager = VoiceManager(this)
        startForeground(1001, createNotification())
    }

    fun triggerPersona(persona: AssistantPersona, query: String) {
        val greeting = if (persona == AssistantPersona.JARVIS) {
            "Jarvis online. At your service."
        } else {
            "Friday here. How can I help you today?"
        }
        voiceManager.speak(greeting, persona)
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
            .setContentText("Listening for 'Jarvis' and 'Friday'...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }

    override fun onDestroy() {
        voiceManager.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
