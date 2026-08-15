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
    private lateinit var audioManager: AudioManager
    private var wakeLock: PowerManager.WakeLock? = null

    private val geminiClient = GeminiClient("AQ.Ab8RN6L9MCXJyQQ1f7B5chjOG7tWZ-nX2Omvu4bpeyq5BQY_rw")
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        voiceManager = VoiceManager(this)
        deviceController = DeviceController(this)
        speakerVerifier = SpeakerVerifier(this)
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
            // Enable in-call speakerphone so caller and AI can hear each other
            delay(1000)
            audioManager.mode = AudioManager.MODE_IN_CALL
            audioManager.isSpeakerphoneOn = true

            // Default greeting persona for screening
            val greeting = "Hello. You have reached the personal AI assistant for this line. My owner is currently unavailable. Please state your name and the purpose of your call."
            voiceManager.speak(greeting, AssistantPersona.JARVIS)
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
            .setContentText("Call screening & background monitoring enabled...")
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
