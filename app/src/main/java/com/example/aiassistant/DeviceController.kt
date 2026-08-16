package com.example.aiassistant

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent

class DeviceController(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val appAnalyzer = AppAnalyzer(context)
    private val accessibility: AgentAccessibilityService?
        get() = AgentAccessibilityService.instance

    fun handleActionCommand(response: String) {
        try {
            when {
                // 1. Phone Call
                response.contains("ACTION_CALL:") -> {
                    val target = response.substringAfter("ACTION_CALL:").trim()
                    makePhoneCall(target)
                }

                // 2. Instant App Launch
                response.contains("ACTION_OPEN_APP:") -> {
                    val appName = response.substringAfter("ACTION_OPEN_APP:").trim()
                    openAppByName(appName)
                }

                // 3. Navigation & System Controls
                response.contains("ACTION_NAV_HOME") -> accessibility?.triggerGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                response.contains("ACTION_NAV_BACK") -> accessibility?.triggerGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                response.contains("ACTION_NAV_RECENTS") -> accessibility?.triggerGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                response.contains("ACTION_NOTIFICATIONS") -> accessibility?.triggerGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                response.contains("ACTION_QUICK_SETTINGS") -> accessibility?.triggerGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                response.contains("ACTION_SCREENSHOT") -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        accessibility?.triggerGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                    }
                }
                response.contains("ACTION_LOCK_SCREEN") -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        accessibility?.triggerGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                    }
                }

                // 4. Media Controls
                response.contains("ACTION_MEDIA_PLAY") || response.contains("ACTION_MEDIA_PAUSE") -> sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                response.contains("ACTION_MEDIA_NEXT") -> sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT)
                response.contains("ACTION_MEDIA_PREV") -> sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

                // 5. Volume Adjustments
                response.contains("ACTION_VOLUME_UP") -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                response.contains("ACTION_VOLUME_DOWN") -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                response.contains("ACTION_VOLUME_MAX") -> {
                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max, AudioManager.FLAG_SHOW_UI)
                }
                response.contains("ACTION_VOLUME_MUTE") -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)

                // 6. Flashlight
                response.contains("ACTION_FLASHLIGHT_ON") -> toggleFlashlight(true)
                response.contains("ACTION_FLASHLIGHT_OFF") -> toggleFlashlight(false)

                // 7. System Settings Pages
                response.contains("ACTION_SETTINGS_WIFI") -> openSettingsPage(Settings.ACTION_WIFI_SETTINGS)
                response.contains("ACTION_SETTINGS_BLUETOOTH") -> openSettingsPage(Settings.ACTION_BLUETOOTH_SETTINGS)
                response.contains("ACTION_SETTINGS_DISPLAY") -> openSettingsPage(Settings.ACTION_DISPLAY_SETTINGS)
                response.contains("ACTION_SETTINGS_MAIN") -> openSettingsPage(Settings.ACTION_SETTINGS)

                // 8. Alarms & Timers
                response.contains("ACTION_SET_ALARM:") -> {
                    val parts = response.substringAfter("ACTION_SET_ALARM:").trim().split(":")
                    if (parts.size >= 2) {
                        setAlarm(parts[0].toIntOrNull() ?: 7, parts[1].toIntOrNull() ?: 0)
                    }
                }
                response.contains("ACTION_SET_TIMER:") -> {
                    val seconds = response.substringAfter("ACTION_SET_TIMER:").trim().toIntOrNull() ?: 60
                    setTimer(seconds)
                }

                // 9. Web & YouTube Search
                response.contains("ACTION_SEARCH_YOUTUBE:") -> {
                    val query = response.substringAfter("ACTION_SEARCH_YOUTUBE:").trim()
                    val intent = Intent(Intent.ACTION_SEARCH).apply {
                        setPackage("com.google.android.youtube")
                        putExtra("query", query)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
                response.contains("ACTION_SEARCH_WEB:") -> {
                    val query = response.substringAfter("ACTION_SEARCH_WEB:").trim()
                    val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                        putExtra(SearchManager.QUERY, query)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }

                // 10. UI Auto-Click & Typing via Accessibility
                response.contains("ACTION_UI_CLICK:") -> {
                    val elementText = response.substringAfter("ACTION_UI_CLICK:").trim()
                    accessibility?.clickElementByText(elementText)
                }
                response.contains("ACTION_UI_TYPE:") -> {
                    val textToType = response.substringAfter("ACTION_UI_TYPE:").trim()
                    accessibility?.typeTextIntoFocusedNode(textToType)
                }
                response.contains("ACTION_SCROLL_DOWN") -> accessibility?.performScroll(true)
                response.contains("ACTION_SCROLL_UP") -> accessibility?.performScroll(false)
            }
        } catch (e: Exception) {
            Log.e("DeviceController", "Action execution failure", e)
        }
    }

    fun openAppByName(appName: String) {
        val packageName = appAnalyzer.findPackageForQuery(appName)
        if (packageName != null) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                context.startActivity(launchIntent)
                return
            }
        }
        Log.w("DeviceController", "Could not find launchable package for: $appName")
    }

    @SuppressLint("MissingPermission")
    fun makePhoneCall(target: String) {
        val phoneNumber = if (target.matches("^[0-9+ ]+$".toRegex())) {
            target.replace(" ", "")
        } else {
            getContactNumberByName(target) ?: target
        }

        if (phoneNumber.isNotBlank()) {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(dialIntent)
            }
        }
    }

    private fun getContactNumberByName(name: String): String? {
        return try {
            val cursor: Cursor? = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                null
            )
            cursor?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun sendMediaKeyEvent(keyCode: Int) {
        val down = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        }
        val up = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
        context.sendOrderedBroadcast(down, null)
        context.sendOrderedBroadcast(up, null)
    }

    private fun toggleFlashlight(status: Boolean) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, status)
        } catch (e: Exception) {
            Log.e("DeviceController", "Flashlight error", e)
        }
    }

    private fun openSettingsPage(action: String) {
        val intent = Intent(action).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    private fun setAlarm(hour: Int, minute: Int) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    private fun setTimer(seconds: Int) {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
