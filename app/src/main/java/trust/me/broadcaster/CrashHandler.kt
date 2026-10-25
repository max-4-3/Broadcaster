package trust.me.broadcaster

import android.app.Application
import android.content.Intent
import android.util.Log
import kotlin.system.exitProcess

class CrashHandler(private val application: Application) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            // Extract the full stack trace
            val stackTrace = Log.getStackTraceString(throwable)

            // Launch your BugActivity
            val intent = Intent(application, BugActivity::class.java).apply {
                putExtra("EXTRA_CRASH_LOG", stackTrace)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            application.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // Kill the current process so the app doesn't hang in a crashed state
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(1)
        }
    }
}
