package com.steve.chatia

import android.app.Notification
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.Executors

class NotificationReplyListener : NotificationListenerService() {
    private val executor = Executors.newSingleThreadExecutor()

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        instance = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (packageName != WHATSAPP && packageName != WHATSAPP_BUSINESS) return
        val extras = sbn.notification.extras ?: return
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()
        if (text.isBlank() || sender.isBlank() || text == "1 nouveau message") return

        val prefs = getSharedPreferences("chatia", MODE_PRIVATE)
        val fingerprint = "$packageName|$sender|$text"
        if (prefs.getString("last_fingerprint", "") == fingerprint) return
        prefs.edit()
            .putString("last_fingerprint", fingerprint)
            .putString("last_sender", sender)
            .putString("last_message", text)
            .putString("last_package", packageName)
            .putLong("last_message_time", System.currentTimeMillis())
            .apply()
        latestNotification = sbn

        if (prefs.getBoolean("automatic_reply", false)) {
            val key = prefs.getString("api_key", "").orEmpty()
            if (key.isBlank()) return
            executor.execute {
                try {
                    val response = Steve20Gemini.reply(key, sender, text)
                    sendReply(response)
                } catch (_: Exception) {
                    // Do not send anything if Gemini or the reply action fails.
                }
            }
        }
    }

    fun sendReply(text: String): Boolean {
        if (text.isBlank()) return false
        val sbn = latestNotification ?: return false
        val actions = sbn.notification.actions ?: return false
        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            if (remoteInputs.isEmpty()) continue
            try {
                val fillInIntent = Intent()
                val results = android.os.Bundle()
                remoteInputs.forEach { input -> results.putCharSequence(input.resultKey, text) }
                RemoteInput.addResultsToIntent(remoteInputs, fillInIntent, results)
                action.actionIntent.send(this, 0, fillInIntent)
                return true
            } catch (_: Exception) {
                // Try the next compatible action, if present.
            }
        }
        return false
    }

    override fun onDestroy() {
        executor.shutdownNow()
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private const val WHATSAPP = "com.whatsapp"
        private const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
        @Volatile private var instance: NotificationReplyListener? = null
        @Volatile private var latestNotification: StatusBarNotification? = null

        fun notificationAccessEnabled(context: Context): Boolean {
            val expected = ComponentName(context, NotificationReplyListener::class.java)
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            ).orEmpty()
            return enabled.split(":").any { ComponentName.unflattenFromString(it) == expected }
        }

        fun sendLatestReply(text: String): Boolean = instance?.sendReply(text) ?: false
    }
}
