package com.example.aiassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AssistantForegroundService : Service() {

    private lateinit var voiceManager: VoiceManager
    private lateinit var deviceController: DeviceController
    private lateinit var assistantMemory: AssistantMemory
    private lateinit var aiRouter: UnifiedAiRouter
    private var audioRecorder: ContinuousAudioRecorder? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    private val keyConfig = ApiKeyConfig(
        groqKey = "", // Injected at build time or via secrets
        geminiKey = "",
        openRouterKey = ""
    )

    override fun onCreate() {
        super.onCreate()
        voiceManager = VoiceManager(this)
        deviceController = DeviceController(this)
        assistantMemory = AssistantMemory(this)
        aiRouter = UnifiedAiRouter(keyConfig, this)

        // Acquire persistent CPU WakeLock so OS does not sleep when locked
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AIAssistant::LockScreenListeningLock").apply {
            setReferenceCounted(false)
            acquire(24 * 60 * 60 * 1000L) // 24 hours
        }

        // Start Foreground Service immediately
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1001,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(1001, createNotification())
        }

        serviceScope.launch {
            delay(1000)
            voiceManager.speak("I have indeed been uploaded, sir. We're online and ready.", AssistantPersona.JARVIS)
            startContinuousListening()
        }
    }

    private fun startContinuousListening() {
        audioRecorder = ContinuousAudioRecorder { audioBytes ->
            serviceScope.launch {
                val (persona, response) = aiRouter.processVoiceAudio(audioBytes, assistantMemory)
                if (persona != null && !response.isNullOrBlank()) {
                    deviceController.handleActionCommand(response)
                    voiceManager.speak(response, persona)
                }
            }
        }
        audioRecorder?.startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // Automatically restarts if OS attempts to kill
    }

    private fun createNotification(): Notification {
        val channelId = "jarvis_active_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "JARVIS Live Core",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous background voice listening active"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("JARVIS // Live Protocol")
            .setContentText("Microphone active • Lock-screen standby")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        audioRecorder?.stopListening()
        wakeLock?.let { if (it.isHeld) it.release() }
        voiceManager.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
