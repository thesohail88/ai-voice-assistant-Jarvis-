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
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AssistantForegroundService : Service() {

    private lateinit var voiceManager: VoiceManager
    private lateinit var deviceController: DeviceController
    private lateinit var audioManager: AudioManager
    private var continuousRecorder: ContinuousAudioRecorder? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // ⚠️ Replace with your Google AI Studio API key
    private val geminiClient = GeminiClient("AQ.Ab8RN6LGTH90nHMefG8sOBoVQi3FEo70G9rIYIgGni-lXwSbBg")
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        voiceManager = VoiceManager(this)
        deviceController = DeviceController(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AIAssistant::HardwareMicWakeLock").apply {
            acquire(24 * 60 * 60 * 1000L)
        }

        startForeground(1001, createNotification())
        startHardwareListening()
    }

    private fun startHardwareListening() {
        continuousRecorder = ContinuousAudioRecorder { pcmAudioChunk ->
            serviceScope.launch {
                val (persona, reply) = geminiClient.processVoiceAudio(pcmAudioChunk)

                if (persona != null && reply != null) {
                    Toast.makeText(applicationContext, "${persona.name}: $reply", Toast.LENGTH_SHORT).show()

                    val wasHandled = deviceController.handleActionCommand(reply)
                    if (!wasHandled) {
                        voiceManager.speak(reply, persona)
                    }
                }
            }
        }
        continuousRecorder?.startListening()
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
            .setContentText("Hardware microphone stream is live 24/7...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }

    override fun onDestroy() {
        continuousRecorder?.stopListening()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        voiceManager.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
