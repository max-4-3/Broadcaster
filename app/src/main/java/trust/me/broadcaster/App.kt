package trust.me.broadcaster

import android.app.Application

class App: Application() {
    override fun onCreate() {
        super.onCreate()

        AppSettings.init(this)
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
    }
}