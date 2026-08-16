package com.example.aiassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
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

    private val mediaButtonReceiver = object : BroadcastReceiver() {
        private var buttonDownTimestamp = 0L

        override fun onReceive(context: Context, intent: Intent) {
            try {
                if (Intent.ACTION_MEDIA_BUTTON == intent.action || Intent.ACTION_VOICE_COMMAND == intent.action) {
                    val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    }

                    if (event != null) {
                        when (event.action) {
                            KeyEvent.ACTION_DOWN -> {
                                buttonDownTimestamp = System.currentTimeMillis()
                                if (event.keyCode == KeyEvent.KEYCODE_VOICE_ASSIST) {
                                    triggerBluetoothActivation()
                                }
                            }
                            KeyEvent.ACTION_UP -> {
                                val duration = System.currentTimeMillis() - buttonDownTimestamp
                                if (duration >= 550 && (
                                    event.keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                                    event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                                    event.keyCode == KeyEvent.KEYCODE_CALL
                                )) {
                                    triggerBluetoothActivation()
                                }
                            }
                        }
                    } else if (Intent.ACTION_VOICE_COMMAND == intent.action) {
                        triggerBluetoothActivation()
                    }
                }
            } catch (e: Exception) {
                Log.e("ForegroundService", "Media key receiver error", e)
            }
        }
    }

    private fun triggerBluetoothActivation() {
        serviceScope.launch {
            screenWakeHelper.wakeScreen(6000L)
            if (Settings.canDrawOverlays(this@AssistantForegroundService)) {
                hudOverlayManager.showListeningHud(AssistantPersona.JARVIS)
            }
            voiceManager.speak("Yes sir, listening through headset.", AssistantPersona.JARVIS)
            delay(3500)
            hudOverlayManager.hideHud()
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            voiceManager = VoiceManager(this)
            deviceController = DeviceController(this)
            assistantMemory = AssistantMemory(this)
            aiRouter = UnifiedAiRouter(keyConfig, this)
            screenWakeHelper = ScreenWakeHelper(this)
            hudOverlayManager = HudOverlayManager(this)
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            // 1. Acquire Partial WakeLock
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AIAssistant::LockScreenListeningLock"
            ).apply {
                setReferenceCounted(false)
                acquire(24 * 60 * 60 * 1000L)
            }

            // 2. Start Foreground Service Immediately to Prevent ANR/FGS Kill
            startServiceInForeground()
            requestContinuousAudioFocus()

            // 3. Proactive Hardware / Battery Alerts
            proactiveTelemetryMonitor = ProactiveTelemetryMonitor(this) { alertText, persona ->
                serviceScope.launch {
                    screenWakeHelper.wakeScreen(5000L)
                    if (Settings.canDrawOverlays(this@AssistantForegroundService)) {
                        hudOverlayManager.showListeningHud(persona)
                    }
                    voiceManager.speak(alertText, persona)
                    delay(4000)
                    hudOverlayManager.hideHud()
                }
            }
            proactiveTelemetryMonitor.startMonitoring()

            // 4. Voicemail & Notification Interceptor with Multilingual Translation
            NotificationInterceptorService.onNotificationReceived = { sender, message, app ->
                serviceScope.launch {
                    val prefs = getSharedPreferences("AssistantPrefs", Context.MODE_PRIVATE)
                    val targetLang = prefs.getString("MESSAGE_LANGUAGE", "English") ?: "English"

                    val prompt = "Incoming $app message/voicemail from $sender: '$message'. Translate and deliver a 1-sentence tactical briefing in $targetLang."
                    val responseText = aiRouter.processDirectTextPrompt(prompt, AssistantPersona.JARVIS)
                    
                    screenWakeHelper.wakeScreen(6000L)
                    if (Settings.canDrawOverlays(this@AssistantForegroundService)) {
                        hudOverlayManager.showListeningHud(AssistantPersona.JARVIS)
                    }
                    voiceManager.speak(responseText, AssistantPersona.JARVIS)
                    delay(5000)
                    hudOverlayManager.hideHud()
                }
            }

            // 5. Register Media Key Receiver with Android 14 Export Flags
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_MEDIA_BUTTON)
                addAction(Intent.ACTION_VOICE_COMMAND)
                priority = IntentFilter.SYSTEM_HIGH_PRIORITY
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(mediaButtonReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(mediaButtonReceiver, filter)
            }

            serviceScope.launch {
                delay(1000)
                voiceManager.speak("Systems operational, sir. Standing by on lock screen.", AssistantPersona.JARVIS)
                startContinuousListening()
            }
        } catch (e: Exception) {
            Log.e("ForegroundService", "Fatal onCreate error", e)
        }
    }

    private fun startServiceInForeground() {
        val notification = createNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    1001,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(1001, notification)
            }
        } catch (e: Exception) {
            Log.e("ForegroundService", "Failed startForeground", e)
        }
    }

    private fun requestContinuousAudioFocus() {
        try {
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
        } catch (e: Exception) {
            Log.e("ForegroundService", "Audio focus error", e)
        }
    }

    private fun startContinuousListening() {
        try {
            audioRecorder = ContinuousAudioRecorder { audioBytes ->
                serviceScope.launch {
                    if (Settings.canDrawOverlays(this@AssistantForegroundService)) {
                        hudOverlayManager.showListeningHud(AssistantPersona.JARVIS)
                    }

                    val resultPair = aiRouter.processVoiceAudio(audioBytes, assistantMemory)
                    val persona = resultPair.first
                    val response = resultPair.second

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
        } catch (e: Exception) {
            Log.e("ForegroundService", "Continuous listening startup error", e)
        }
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
        try {
            audioRecorder?.stopListening()
            proactiveTelemetryMonitor.stopMonitoring()
            hudOverlayManager.hideHud()
            unregisterReceiver(mediaButtonReceiver)
            val lock = wakeLock
            if (lock != null && lock.isHeld) {
                lock.release()
            }
            voiceManager.shutdown()
        } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
