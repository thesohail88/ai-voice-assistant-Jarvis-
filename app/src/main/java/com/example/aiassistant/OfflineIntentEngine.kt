package com.example.aiassistant

class OfflineIntentEngine {

    fun matchOfflineCommand(spokenText: String): String? {
        val clean = spokenText.lowercase().trim()

        return when {
            clean.contains("flashlight on") || clean.contains("torch on") -> "ACTION_FLASHLIGHT_ON"
            clean.contains("flashlight off") || clean.contains("torch off") -> "ACTION_FLASHLIGHT_OFF"
            clean.contains("go home") || clean.contains("home screen") -> "ACTION_NAV_HOME"
            clean.contains("go back") -> "ACTION_NAV_BACK"
            clean.contains("show recents") || clean.contains("open apps") -> "ACTION_NAV_RECENTS"
            clean.contains("take screenshot") || clean.contains("capture screen") -> "ACTION_SCREENSHOT"
            clean.contains("lock screen") || clean.contains("lock phone") -> "ACTION_LOCK_SCREEN"
            clean.contains("mute volume") || clean.contains("silence") -> "ACTION_VOLUME_MUTE"
            clean.contains("volume up") || clean.contains("louder") -> "ACTION_VOLUME_UP"
            clean.contains("volume down") || clean.contains("quieter") -> "ACTION_VOLUME_DOWN"
            clean.contains("pause music") || clean.contains("stop music") -> "ACTION_MEDIA_PAUSE"
            clean.contains("play music") || clean.contains("resume") -> "ACTION_MEDIA_PLAY"
            clean.contains("next song") || clean.contains("skip") -> "ACTION_MEDIA_NEXT"
            clean.contains("previous song") -> "ACTION_MEDIA_PREV"
            clean.startsWith("open ") -> "ACTION_OPEN_APP: " + spokenText.substringAfter("open ").trim()
            clean.startsWith("call ") -> "ACTION_CALL: " + spokenText.substringAfter("call ").trim()
            else -> null
        }
    }
}
