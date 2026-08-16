package com.example.aiassistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DeviceController(private val context: Context) {

    val accessibility: AgentAccessibilityService?
        get() = AgentAccessibilityService.instance

    val appAnalyzer = AppAnalyzer(context)
    private val scope = CoroutineScope(Dispatchers.Main)

    fun handleActionCommand(command: String) {
        val lines = command.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("ACTION_") || trimmed.contains("ACTION_")) {
                val actionPart = trimmed.substring(trimmed.indexOf("ACTION_"))
                executeSingleAction(actionPart)
            }
        }
    }

    fun executeSingleAction(actionString: String) {
        val parts = actionString.split(":", limit = 2)
        val actionType = parts[0].trim()
        val param = if (parts.size > 1) parts[1].trim() else ""

        try {
            when (actionType) {
                "ACTION_OPEN_APP" -> openApp(param)
                "ACTION_CALL" -> initiatePhoneCall(param)
                "ACTION_UI_CLICK" -> clickUiElement(param)
                "ACTION_UI_TYPE" -> typeUiText(param)
                "ACTION_SCROLL_DOWN", "ACTION_SCROLL" -> performScrollDown()
                "ACTION_SCROLL_UP" -> performScrollUp()
                "ACTION_SWIPE_LEFT" -> performSwipeLeft()
                "ACTION_SWIPE_RIGHT" -> performSwipeRight()
                "ACTION_PRESS_BACK", "ACTION_BACK" -> accessibility?.pressBack()
                "ACTION_PRESS_HOME", "ACTION_HOME" -> accessibility?.pressHome()
                "ACTION_PRESS_RECENTS", "ACTION_RECENTS" -> accessibility?.pressRecents()
                "ACTION_NOTIFICATIONS" -> accessibility?.openNotifications()
                "ACTION_QUICK_SETTINGS" -> accessibility?.openQuickSettings()
                "ACTION_LOCK_SCREEN" -> accessibility?.lockDeviceScreen()
                "ACTION_TAKE_SCREENSHOT" -> accessibility?.takeScreenSnapshot()
                else -> Log.w("DeviceController", "Unknown action command: $actionType")
            }
        } catch (e: Exception) {
            Log.e("DeviceController", "Error executing action: $actionString", e)
        }
    }

    private fun openApp(appName: String) {
        val launchIntent = appAnalyzer.getLaunchIntentForAppName(appName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            Log.w("DeviceController", "App not found: $appName")
        }
    }

    private fun initiatePhoneCall(phoneNumberOrName: String) {
        val cleanNumber = phoneNumberOrName.replace("[^0-9+]".toRegex(), "")
        val intent = if (cleanNumber.isNotBlank()) {
            Intent(Intent.ACTION_CALL, Uri.parse("tel:$cleanNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse("tel:$phoneNumberOrName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("DeviceController", "Failed to initiate call", e)
        }
    }

    private fun clickUiElement(target: String) {
        if (target.contains(",")) {
            val coords = target.split(",")
            val x = coords[0].trim().toFloatOrNull()
            val y = coords[1].trim().toFloatOrNull()
            if (x != null && y != null) {
                scope.launch {
                    accessibility?.clickAtCoordinates(x, y)
                }
                return
            }
        }

        val clicked = accessibility?.clickElementByText(target) ?: false
        if (!clicked) {
            accessibility?.clickElementById(target)
        }
    }

    private fun typeUiText(text: String) {
        accessibility?.typeTextIntoFocusedField(text)
    }

    private fun performScrollDown() {
        scope.launch {
            accessibility?.scrollDown()
        }
    }

    private fun performScrollUp() {
        scope.launch {
            accessibility?.scrollUp()
        }
    }

    private fun performSwipeLeft() {
        scope.launch {
            accessibility?.swipeLeft()
        }
    }

    private fun performSwipeRight() {
        scope.launch {
            accessibility?.swipeRight()
        }
    }
}
