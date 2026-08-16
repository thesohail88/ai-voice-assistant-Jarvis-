package com.example.aiassistant

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationInterceptorService : NotificationListenerService() {

    companion object {
        var onNotificationReceived: ((sender: String, message: String, appName: String) -> Unit)? = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val packageName = sbn.packageName
        val isTargetApp = packageName.contains("whatsapp") ||
                packageName.contains("telegram") ||
                packageName.contains("messaging") ||
                packageName.contains("com.google.android.apps.messaging")

        if (!isTargetApp) return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (title.isNotBlank() && text.isNotBlank() && !text.contains("messages")) {
            val appLabel = when {
                packageName.contains("whatsapp") -> "WhatsApp"
                packageName.contains("telegram") -> "Telegram"
                else -> "SMS"
            }
            Log.d("NotificationInterceptor", "Intercepted message from $title on $appLabel: $text")
            onNotificationReceived?.invoke(title, text, appLabel)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}
