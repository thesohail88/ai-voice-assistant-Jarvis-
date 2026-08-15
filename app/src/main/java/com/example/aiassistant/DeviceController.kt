package com.example.aiassistant

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat

class DeviceController(private val context: Context) {

    fun handleActionCommand(text: String): Boolean {
        val lower = text.lowercase().trim()

        return when {
            lower.contains("flashlight on") || lower.contains("torch on") -> {
                toggleFlashlight(true)
                true
            }
            lower.contains("flashlight off") || lower.contains("torch off") -> {
                toggleFlashlight(false)
                true
            }
            lower.startsWith("call ") || lower.startsWith("dial ") || lower.startsWith("phone ") -> {
                val rawNumber = lower.replace("call", "")
                    .replace("dial", "")
                    .replace("phone", "")
                    .trim()
                makeDirectPhoneCall(rawNumber)
                true
            }
            lower.contains("open youtube") -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    true
                } else {
                    launchAppByPackage("com.google.android.youtube")
                }
            }
            lower.contains("open whatsapp") -> launchAppByPackage("com.whatsapp")
            lower.contains("open instagram") -> launchAppByPackage("com.instagram.android")
            lower.contains("open spotify") -> launchAppByPackage("com.spotify.music")
            lower.contains("open camera") -> {
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                true
            }
            lower.startsWith("open ") || lower.startsWith("launch ") -> {
                val appName = lower.removePrefix("open ").removePrefix("launch ").trim()
                launchAppByName(appName)
            }
            lower.startsWith("search for ") || lower.startsWith("google ") -> {
                val query = lower.replace("search for ", "").replace("google ", "").trim()
                val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra(SearchManager.QUERY, query)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                true
            }
            lower.startsWith("set timer for") -> {
                val seconds = parseTimerDurationInSeconds(lower)
                val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_MESSAGE, "AI Timer")
                    putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                true
            }
            else -> false
        }
    }

    private fun makeDirectPhoneCall(phoneNumber: String) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(callIntent)
        } else {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
        }
    }

    private fun toggleFlashlight(status: Boolean) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, status)
        } catch (e: Exception) {
            Log.e("DeviceController", "Flashlight toggle error", e)
        }
    }

    private fun launchAppByPackage(packageName: String): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            true
        } else {
            false
        }
    }

    private fun launchAppByName(appName: String): Boolean {
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        for (app in installedApps) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label == appName || label.contains(appName)) {
                val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return true
                }
            }
        }
        return false
    }

    private fun parseTimerDurationInSeconds(text: String): Int {
        val numbers = Regex("\\d+").findAll(text).map { it.value.toInt() }.toList()
        val num = numbers.firstOrNull() ?: 60

        return when {
            text.contains("minute") || text.contains("min") -> num * 60
            text.contains("hour") || text.contains("hr") -> num * 3600
            else -> num
        }
    }
}
