package com.example.aiassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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
    private lateinit var screenWakeHelper: ScreenWakeHelper
    private lateinit var hudOverlayManager: HudOverlayManager
    private lateinit var proactiveTelemetryMonitor: ProactiveTelemetryMonitor
    private lateinit var audioManager: AudioManager
    
    private var audioRecorder: ContinuousAudioRecorder? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    private val keyConfig = ApiKeyConfig(
        groqKey = BuildConfig.GROQ_KEY,
        geminiKey = BuildConfig.GEMINI_KEY,
        openRouterKey = BuildConfig.OPENROUTER_KEY
    )

    override fun onCreate() {
        super.onCreate()
        voiceManager = VoiceManager(this)
        deviceController = DeviceController(this)
        assistantMemory = AssistantMemory(this)
        aiRouter = UnifiedAiRouter(keyConfig, this)
        screenWakeHelper = ScreenWakeHelper(this)
        hudOverlayManager = HudOverlayManager(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AIAssistant::LockScreenListeningLock"
        ).apply {
            setReferenceCounted(false)
            acquire(24 * 60 * 60 * 1000L)
        }

        requestContinuousAudioFocus()
        startServiceInForeground()

        // Proactive hardware & battery alerts
        proactiveTelemetryMonitor = ProactiveTelemetryMonitor(this) { alertText, persona ->
            serviceScope.launch {
                screenWakeHelper.wakeScreen(5000L)
                hudOverlayManager.showListeningHud(persona)
                voiceManager.speak(alertText, persona)
                delay(4000)
                hudOverlayManager.hideHud()
            }
        }
        proactiveTelemetryMonitor.startMonitoring()

        // Incoming message intelligence listener
        NotificationInterceptorService.onNotificationReceived = { sender, message, app ->
            serviceScope.launch {
                val prompt = "Incoming $app message from $sender: '$message'. Give a 1-sentence tactical briefing."
                val response = aiRouter.processDirectTextPrompt(prompt, AssistantPersona.JARVIS)
                
                screenWakeHelper.wakeScreen(6000L)
                hudOverlayManager.showListeningHud(AssistantPersona.JARVIS)
                voiceManager.speak(response, AssistantPersona.JARVIS)
                delay(5000)
                hudOverlayManager.hideHud()
            }
        }

        // Silent Bluetooth earbud click trigger
        MediaButtonTriggerReceiver.onMediaButtonClicked = {
            serviceScope.launch {
                screenWakeHelper.wakeScreen(5000L)
                hudOverlayManager.showListeningHud(AssistantPersona.JARVIS)
                voiceManager.speak("At your service, sir.", AssistantPersona.JARVIS)
                delay(3000)
                hudOverlayManager.hideHud()
            }
        }

        serviceScope.launch {
            delay(1200)
            voiceManager.speak("Systems operational, sir. Standing by on lock screen.", AssistantPersona.JARVIS)
            startContinuousListening()
        }
    }

    private fun startServiceInForeground() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1001,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(1001, notification)
        }
    }

    private fun requestContinuousAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .build()
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private fun startContinuousListening() {
        audioRecorder = ContinuousAudioRecorder { audioBytes ->
            serviceScope.launch {
                hudOverlayManager.showListeningHud(AssistantPersona.JARVIS)

                val (persona, response) = aiRouter.processVoiceAudio(audioBytes, assistantMemory)
                if (persona != null && !response.isNullOrBlank()) {
                    screenWakeHelper.wakeScreen(8000L)
                    deviceController.handleActionCommand(response)
                    voiceManager.speak(response, persona)
                }

                delay(4000)
                hudOverlayManager.hideHud()
            }
        }
        audioRecorder?.startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val channelId = "jarvis_active_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "JARVIS Core Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous voice recognition and lock-screen standby"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("JARVIS // Active Protocol")
            .setContentText("Neural Core Standby • Lock-screen enabled")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        audioRecorder?.stopListening()
        proactiveTelemetryMonitor.stopMonitoring()
        hudOverlayManager.hideHud()
        wakeLock?.let { if (it.isHeld) it.release() }
        voiceManager.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
