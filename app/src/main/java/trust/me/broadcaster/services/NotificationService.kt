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
        Log.e(TAG, "SERVICE CREATED")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.e(TAG, "LISTENER CONNECTED")
        alive = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.e(TAG, "LISTENER DISCONNECTED")
        alive = false
    }

    override fun onListenerHintsChanged(hints: Int) {
        Log.e(TAG, "Listener hints: $hints")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        Log.e(TAG, "onNotificationPosted(sbn, rankingMap)")
        onNotificationPosted(sbn)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        Log.e(TAG, "onNotificationPosted(${sbn})")
        if (sbn == null) {
            Log.e(TAG, "sbn is null")
            return
        }

        val appSettings = AppSettings(this)
        val packageName = sbn.packageName

        val pattern = appSettings.string(AppSettings.Keys.Regex)
            .takeIf { it.isNotEmpty() } ?: ".*"

        val regex = Pattern.compile(pattern)
        Log.e(TAG, "pattern(${regex.pattern()})")

        if (!regex.matcher(packageName).matches()) {
            Log.e(TAG, "pattern(${regex.pattern()}) != input(${packageName})")
            return
        }

        if (this.packageName == packageName) {
            Log.e(TAG, "Skipping self-notification from our own package.")
            return
        }

        val data = DataConversion.fromNotification(sbn)
        Log.e(TAG, "data(${data})")

        val result = WorkerUtils.enqueueWork(data, this)
        Log.e(TAG, "result(${result.state})")
    }
}
