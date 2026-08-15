package com.example.aiassistant

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AssistantForegroundService : Service() {

    private lateinit var voiceManager: VoiceManager
    private lateinit var deviceController: DeviceController
    private lateinit var languageManager: ContactLanguageManager
    private lateinit var audioManager: AudioManager
    private var continuousRecorder: ContinuousAudioRecorder? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // ⚠️ Paste your actual Groq key starting with gsk_
    private val apiKey = "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"
    private lateinit var groqClient: GroqClient
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        voiceManager = VoiceManager(this)
        deviceController = DeviceController(this)
        languageManager = ContactLanguageManager(this)
        groqClient = GroqClient(apiKey)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AIAssistant::HardwareMicWakeLock").apply {
            acquire(24 * 60 * 60 * 1000L)
        }

        startForeground(1001, createNotification())

        serviceScope.launch {
            delay(1500)
            voiceManager.speak("Systems operational. Ready on Groq engine.", AssistantPersona.JARVIS)
            startHardwareListening()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("ACTION_CALL_ANSWERED", false) == true) {
            val callerNumber = intent.getStringExtra("CALLER_NUMBER") ?: "Caller"
            handleAutoAnsweredCall(callerNumber)
        }
        return START_STICKY
    }

    private fun startHardwareListening() {
        continuousRecorder?.stopListening()
        continuousRecorder = ContinuousAudioRecorder { wavAudioChunk ->
            serviceScope.launch {
                val (persona, reply) = groqClient.processVoiceAudio(wavAudioChunk) { heardText ->
                    serviceScope.launch(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "Heard: \"$heardText\"", Toast.LENGTH_SHORT).show()
                    }
                }

                if (persona != null && !reply.isNullOrBlank()) {
                    Toast.makeText(applicationContext, "${persona.name}: $reply", Toast.LENGTH_LONG).show()
                    voiceManager.speak(reply, persona)
                    deviceController.handleActionCommand(reply)
                }
            }
        }
        continuousRecorder?.startListening()
    }

    private fun handleAutoAnsweredCall(callerNumber: String) {
        serviceScope.launch {
            delay(1000)
            audioManager.mode = AudioManager.MODE_IN_CALL
            audioManager.isSpeakerphoneOn = true

            val targetLanguage = languageManager.getLanguageForContact(callerNumber)
            val prompt = "Generate a short polite call screening greeting stating the owner is away. Language code: $targetLanguage."
            val greeting = groqClient.queryAssistant(prompt, AssistantPersona.JARVIS)

            voiceManager.speak(greeting, AssistantPersona.JARVIS, targetLanguage)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartServiceIntent = Intent(applicationContext, AssistantForegroundService::class.java).also {
            it.setPackage(packageName)
        }
        val restartServicePendingIntent = PendingIntent.getService(
            this, 1, restartServiceIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmService = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmService.set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + 1000,
            restartServicePendingIntent
        )
        super.onTaskRemoved(rootIntent)
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
            .setContentText("Listening 24/7 in background...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
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
