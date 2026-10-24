package trust.me.broadcaster.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import trust.me.broadcaster.AppSettings
import trust.me.broadcaster.util.DataConversion
import trust.me.broadcaster.util.WorkerUtils
import java.util.regex.Pattern


class NotificationService : NotificationListenerService() {
    companion object {
        private const val TAG = "TrustMeBroadcaster"
        var alive = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SERVICE CREATED")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "LISTENER CONNECTED")
        alive = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "LISTENER DISCONNECTED")
        alive = false
    }

    override fun onListenerHintsChanged(hints: Int) {
        Log.d(TAG, "Listener hints: $hints")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        Log.d(TAG, "onNotificationPosted(sbn, rankingMap)")
        onNotificationPosted(sbn)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        Log.d(TAG, "onNotificationPosted(${sbn})")
        if (sbn == null || sbn.isOngoing) {
            Log.e(TAG, "sbn is null or notification is onGoing")
            return
        }

        val appSettings = AppSettings(this)
        val packageName = sbn.packageName

        val pattern = appSettings.string(AppSettings.Keys.Regex).takeIf { it.isNotEmpty() } ?: ".*"
        val regex = patternOrNull(pattern)
        if (regex == null) {
            Log.e(TAG, "invalid pattern: $pattern")
            return
        } else {
            Log.d(TAG, "pattern(${regex.pattern()})")
        }

        if (!regex.matcher(packageName).matches()) {
            Log.e(TAG, "pattern(${regex.pattern()}) != input(${packageName})")
            return
        }

        if (this.packageName == packageName) {
            Log.e(TAG, "Skipping self-notification from our own package.")
            return
        }

        val data = DataConversion.fromNotification(sbn)
        Log.i(TAG, "data(${data})")

        val result = WorkerUtils.enqueueWork(data, this)
        Log.i(TAG, "result(${result.state})")
    }

    private fun patternOrNull(pattern: String): Pattern? {
        return try {
            Pattern.compile(pattern)
        } catch (_: Exception) {
            null
        }
    }
}
