package trust.me.broadcaster

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit


class AppSettings {
    companion object {
        private const val PREFS_NAME = "app_prefs"
    }

    enum class Keys {
        Regex,
        ReactorConfig,
    }

    private val prefs: SharedPreferences

    fun string(key: Keys): String = prefs.getString("$key", "") ?: ""
    fun string(key: Keys, value: String): Unit = prefs.edit { putString("$key", value) }

    constructor(context: Context) {
        prefs = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
    }
}