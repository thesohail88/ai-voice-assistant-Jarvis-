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

    // Updated with your API Key
    private val apiKey = "AQ.Ab8RN6LNWwcJsFDXsOj9dzu7talXI8TmKFQDtgDXisvtqZoQhA"
    private lateinit var geminiClient: GeminiClient
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        voiceManager = VoiceManager(this)
        deviceController = DeviceController(this)
        languageManager = ContactLanguageManager(this)
        geminiClient = GeminiClient(apiKey)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Keep CPU active when phone screen locks
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AIAssistant::HardwareMicWakeLock").apply {
            acquire(24 * 60 * 60 * 1000L) // 24 hours
        }

        startForeground(1001, createNotification())

        // Initial startup confirmation and microphone launch
        serviceScope.launch {
            delay(1500)
            voiceManager.speak("Systems operational. I am ready.", AssistantPersona.JARVIS)
            startHardwareListening()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle Auto-Answered Phone Call Intent from CallInterceptorReceiver
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
                val (persona, reply) = geminiClient.processVoiceAudio(wavAudioChunk)

                if (persona != null && !reply.isNullOrBlank()) {
                    Toast.makeText(applicationContext, "${persona.name}: $reply", Toast.LENGTH_SHORT).show()

                    // Speak the response back in natural human voice
                    voiceManager.speak(reply, persona)

                    // Execute any matching system intents in parallel
                    deviceController.handleActionCommand(reply)
                }
            }
        }
        continuousRecorder?.startListening()
    }

    private fun handleAutoAnsweredCall(callerNumber: String) {
        serviceScope.launch {
            // Give the in-call audio route 1 second to connect
            delay(1000)
            audioManager.mode = AudioManager.MODE_IN_CALL
            audioManager.isSpeakerphoneOn = true

            // Look up specific language for this contact
            val targetLanguage = languageManager.getLanguageForContact(callerNumber)

            val prompt = "Generate a short greeting answering the phone for an unavailable owner in language code $targetLanguage. State that you are taking a message."
            val greeting = geminiClient.queryAssistant(prompt, AssistantPersona.JARVIS)

            voiceManager.speak(greeting, AssistantPersona.JARVIS, targetLanguage)
        }
    }

    /**
     * Prevents Android from killing the assistant when the app is swiped away from Recents
     */
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
            .setContentText("Listening 24/7 in background & ready for calls...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        continuousRecorder?.stopListening()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        voiceManager.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
