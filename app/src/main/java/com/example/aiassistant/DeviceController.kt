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
    val appAnalyzer = AppAnalyzer(context)
    val accessibility: AgentAccessibilityService?
        get() = AgentAccessibilityService.instance

    fun executeSingleAction(actionTag: String): Boolean {
        val trimmed = actionTag.trim()
        try {
            when {
                trimmed.startsWith("ACTION_OPEN_APP:") -> {
                    openAppByName(trimmed.substringAfter("ACTION_OPEN_APP:").trim())
                    return true
                }
                trimmed.startsWith("ACTION_CALL:") -> {
                    makePhoneCall(trimmed.substringAfter("ACTION_CALL:").trim())
                    return true
                }
                trimmed.startsWith("ACTION_UI_CLICK:") -> {
                    return accessibility?.clickElementByText(trimmed.substringAfter("ACTION_UI_CLICK:").trim()) ?: false
                }
                trimmed.startsWith("ACTION_UI_TYPE:") -> {
                    return accessibility?.typeTextIntoFocusedNode(trimmed.substringAfter("ACTION_UI_TYPE:").trim()) ?: false
                }
                trimmed == "ACTION_NAV_HOME" -> return accessibility?.triggerGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) ?: false
                trimmed == "ACTION_NAV_BACK" -> return accessibility?.triggerGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) ?: false
                trimmed == "ACTION_NAV_RECENTS" -> return accessibility?.triggerGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) ?: false
                trimmed == "ACTION_NOTIFICATIONS" -> return accessibility?.triggerGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) ?: false
                trimmed == "ACTION_QUICK_SETTINGS" -> return accessibility?.triggerGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS) ?: false
                trimmed == "ACTION_SCREENSHOT" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        return accessibility?.triggerGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) ?: false
                    }
                }
                trimmed == "ACTION_LOCK_SCREEN" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        return accessibility?.triggerGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) ?: false
                    }
                }
                trimmed == "ACTION_MEDIA_PLAY" || trimmed == "ACTION_MEDIA_PAUSE" -> sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                trimmed == "ACTION_MEDIA_NEXT" -> sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT)
                trimmed == "ACTION_MEDIA_PREV" -> sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                trimmed == "ACTION_VOLUME_UP" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                trimmed == "ACTION_VOLUME_DOWN" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                trimmed == "ACTION_VOLUME_MAX" -> audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_SHOW_UI)
                trimmed == "ACTION_VOLUME_MUTE" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                trimmed == "ACTION_FLASHLIGHT_ON" -> toggleFlashlight(true)
                trimmed == "ACTION_FLASHLIGHT_OFF" -> toggleFlashlight(false)
                trimmed == "ACTION_SETTINGS_WIFI" -> openSettingsPage(Settings.ACTION_WIFI_SETTINGS)
                trimmed == "ACTION_SETTINGS_BLUETOOTH" -> openSettingsPage(Settings.ACTION_BLUETOOTH_SETTINGS)
                trimmed == "ACTION_SETTINGS_MAIN" -> openSettingsPage(Settings.ACTION_SETTINGS)
                trimmed == "ACTION_SCROLL_DOWN" -> return accessibility?.performScroll(true) ?: false
                trimmed == "ACTION_SCROLL_UP" -> return accessibility?.performScroll(false) ?: false
                trimmed.startsWith("ACTION_SEARCH_YOUTUBE:") -> {
                    val query = trimmed.substringAfter("ACTION_SEARCH_YOUTUBE:").trim()
                    val intent = Intent(Intent.ACTION_SEARCH).apply {
                        setPackage("com.google.android.youtube")
                        putExtra("query", query)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e("DeviceController", "Action execution error on $actionTag", e)
        }
        return false
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
            cursor?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: Exception) {
            null
        }
    }

    private fun sendMediaKeyEvent(keyCode: Int) {
        val down = Intent(Intent.ACTION_MEDIA_BUTTON).apply { putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode)) }
        val up = Intent(Intent.ACTION_MEDIA_BUTTON).apply { putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, keyCode)) }
        context.sendOrderedBroadcast(down, null)
        context.sendOrderedBroadcast(up, null)
    }

    private fun toggleFlashlight(status: Boolean) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cameraManager.setTorchMode(cameraManager.cameraIdList[0], status)
        } catch (e: Exception) {
            Log.e("DeviceController", "Flashlight error", e)
        }
    }

    private fun openSettingsPage(action: String) {
        context.startActivity(Intent(action).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
    }
}
