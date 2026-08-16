package com.example.aiassistant

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationInterceptorService : NotificationListenerService() {

    companion object {
        var onNotificationReceived: ((sender: String, message: String, app: String) -> Unit)? = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val packageName = sbn.packageName ?: return
        val extras = sbn.notification.extras ?: return

        // Filter system noise, ongoing downloads, or media playback notifications
        if ((sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim() ?: ""

        val effectiveMessage = if (bigText.isNotBlank()) bigText else text
        if (effectiveMessage.isBlank()) return

        val appLabel = when {
            packageName.contains("dialer") || packageName.contains("telecom") -> "Phone Voicemail"
            packageName.contains("messaging") || packageName.contains("mms") -> "SMS"
            packageName.contains("whatsapp") -> "WhatsApp"
            packageName.contains("telegram") -> "Telegram"
            else -> "App"
        }

        // Only intercept SMS, Voicemails, and Direct Communication Apps
        if (appLabel == "Phone Voicemail" || appLabel == "SMS" || appLabel == "WhatsApp" || appLabel == "Telegram") {
            Log.d("NotificationInterceptor", "Intercepted message from $title ($appLabel): $effectiveMessage")
            onNotificationReceived?.invoke(title, effectiveMessage, appLabel)
        }
    }
}
