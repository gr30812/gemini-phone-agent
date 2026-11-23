package com.example.geminiagent.os

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

data class CapturedNotification(
    val id: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val timeFormatted: String,
    @Transient val sbn: StatusBarNotification? = null
)

class AgentNotificationListenerService : NotificationListenerService() {

    companion object {
        var instance: AgentNotificationListenerService? = null
        private const val MAX_HISTORY = 100
        val notificationHistory = ConcurrentLinkedDeque<CapturedNotification>()

        // Privacy blocklist: Exclude banking, UPI, and password manager apps from being recorded or shared with cloud AI
        private val SENSITIVE_PACKAGES = setOf(
            "com.google.android.apps.nbu.paisa.user", // Google Pay
            "com.phonepe.app",                       // PhonePe
            "net.one97.paytm",                       // Paytm
            "in.org.npci.upiapp",                    // BHIM
            "com.sbi.lotusintouch",                  // SBI Yono
            "com.axis.mobile",                       // Axis Bank
            "com.msf.kbank.mobile",                  // Kotak Bank
            "com.csam.icici.bank.imobile",           // ICICI Bank
            "com.hdfcbank.android",                  // HDFC Bank
            "com.bankofbaroda.mconnect",             // BoB World
            "com.canarabank.mobility",               // Canara ai1
            "com.onepassword.android",               // 1Password
            "com.lastpass.lpandroid",                // LastPass
            "com.bitwarden",                         // Bitwarden
            "com.dashlane",                          // Dashlane
            "com.nordpass.android",                  // NordPass
            "org.keepassdroid"                       // KeePass
        )

        fun isPermissionGranted(context: Context): Boolean {
            val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
            return enabledListeners.contains(context.packageName)
        }

        fun openSettingsIntent(): Intent {
            return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        fun getRecentNotifications(appFilter: String? = null, limit: Int = 10): List<CapturedNotification> {
            val list = notificationHistory.toList()
            val filtered = if (!appFilter.isNullOrBlank()) {
                val query = appFilter.trim().lowercase(Locale.ROOT)
                list.filter {
                    it.appName.lowercase(Locale.ROOT).contains(query) ||
                    it.packageName.lowercase(Locale.ROOT).contains(query) ||
                    it.title.lowercase(Locale.ROOT).contains(query)
                }
            } else {
                list
            }
            return filtered.take(limit)
        }

        fun replyToNotification(notificationId: String, replyMessage: String, context: Context): Boolean {
            val notif = notificationHistory.find { it.id == notificationId } ?: return false
            val sbn = notif.sbn ?: return false
            val notification = sbn.notification ?: return false

            val actions = notification.actions ?: return false
            for (action in actions) {
                val remoteInputs = action.remoteInputs ?: continue
                for (remoteInput in remoteInputs) {
                    try {
                        val intent = Intent()
                        val bundle = Bundle()
                        bundle.putCharSequence(remoteInput.resultKey, replyMessage)
                        RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
                        action.actionIntent.send(context, 0, intent)
                        return true
                    } catch (e: Exception) {
                        return false
                    }
                }
            }
            return false
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: return
        // Ignore our own package
        if (pkg == packageName) return

        // Privacy blocklist: Ignore banking, UPI, and password manager apps
        if (SENSITIVE_PACKAGES.contains(pkg)) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val messageContent = if (bigText.isNotEmpty()) bigText else text

        if (title.isBlank() && messageContent.isBlank()) return

        val appName = try {
            val pm = applicationContext.packageManager
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (e: Exception) {
            pkg
        }

        val timeFormatted = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(sbn.postTime))
        val captured = CapturedNotification(
            id = "${pkg}_${sbn.id}_${sbn.postTime}",
            packageName = pkg,
            appName = appName,
            title = title,
            text = messageContent,
            timestamp = sbn.postTime,
            timeFormatted = timeFormatted,
            sbn = sbn
        )

        // Prepend to deque and maintain max limit
        notificationHistory.addFirst(captured)
        while (notificationHistory.size > MAX_HISTORY) {
            notificationHistory.removeLast()
        }
    }
}
