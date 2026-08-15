package com.example.aiassistant

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AssistantForegroundService : Service() {

    private lateinit var voiceManager: VoiceManager
    private lateinit var deviceController: DeviceController
    private lateinit var assistantMemory: AssistantMemory
    private var continuousRecorder: ContinuousAudioRecorder? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val keyConfig = ApiKeyConfig(
        groqKey = "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",
        geminiKey = "AQ.Ab8RN6LNWwcJsFDXsOj9dzu7talXI8TmKFQDtgDXisvtqZoQhA",
        openRouterKey = "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"
    )

    private lateinit var aiRouter: UnifiedAiRouter
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        voiceManager = VoiceManager(this)
        deviceController = DeviceController(this)
        assistantMemory = AssistantMemory(this)
        aiRouter = UnifiedAiRouter(keyConfig)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AIAssistant::HardwareMicWakeLock").apply {
            acquire(24 * 60 * 60 * 1000L)
        }

        startForeground(1001, createNotification())

        serviceScope.launch {
            delay(1500)
            voiceManager.speak("Systems operational. AI Voicemail monitoring active.", AssistantPersona.JARVIS)
            startHardwareListening()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("ACTION_VOICEMAIL_LOGGED", false) == true) {
            val callerNumber = intent.getStringExtra("CALLER_NUMBER") ?: "Unknown Caller"
            val languageCode = intent.getStringExtra("LANGUAGE_CODE") ?: "en"
            handleUnansweredCallVoicemail(callerNumber, languageCode)
        }
        return START_STICKY
    }

    private fun handleUnansweredCallVoicemail(callerNumber: String, languageCode: String) {
        serviceScope.launch {
            // 1. Generate the voicemail message for the caller in their preferred language
            val callerPrompt = "Generate a polite, natural 1-2 sentence automated voicemail message to a caller explaining that I missed their call or my line is busy, and asking them to reply with their message. Respond strictly in language code: $languageCode."
            val voicemailTextForCaller = aiRouter.queryAssistant(callerPrompt, AssistantPersona.JARVIS, assistantMemory)

            // 2. Dispatch the voicemail to the caller via SMS
            val smsSent = sendSmsToCaller(callerNumber, voicemailTextForCaller)

            // 3. Announce status to you through the speaker
            val confirmationMsg = if (smsSent) {
                "Sir, an unanswered call from $callerNumber was registered. I have sent an automated voicemail message in their preferred language."
            } else {
                "Sir, missed call from $callerNumber logged. However, SMS dispatch failed."
            }
            voiceManager.speak(confirmationMsg, AssistantPersona.JARVIS)
        }
    }

    private fun sendSmsToCaller(phoneNumber: String, message: String): Boolean {
        if (phoneNumber.isBlank() || phoneNumber == "Unknown Caller") return false
        return try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                applicationContext.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            Log.d("VoicemailService", "Voicemail SMS sent to $phoneNumber")
            true
        } catch (e: Exception) {
            Log.e("VoicemailService", "Failed to send SMS to $phoneNumber", e)
            false
        }
    }

    private fun startHardwareListening() {
        continuousRecorder?.stopListening()
        continuousRecorder = ContinuousAudioRecorder { wavAudioChunk ->
            serviceScope.launch {
                val (persona, reply) = aiRouter.processVoiceAudio(wavAudioChunk, assistantMemory, null)

                if (persona != null && !reply.isNullOrBlank()) {
                    voiceManager.speak(reply, persona)
                    deviceController.handleActionCommand(reply)
                }
            }
        }
        continuousRecorder?.startListening()
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
            .setContentText("Listening 24/7 & Monitoring Unanswered Calls...")
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
