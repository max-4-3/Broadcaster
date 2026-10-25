package trust.me.broadcaster

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import java.lang.reflect.Type


object AppSettings {
    private const val PREFS_NAME = "app_prefs"
    private lateinit var prefs: SharedPreferences
    val allowedVersions = setOf("1.0")

    enum class Keys(val type: Type) {
        Regex(String::class.java),
        ReactorConfig(String::class.java),
        ShowNotifications(Boolean::class.java),
        MaxDelaySeconds(Int::class.java),
        PauseTemporarily(Boolean::class.java),
    }

    @JvmStatic
    fun int(key: Keys): Int = prefs.getInt("$key", 1)

    @JvmStatic
    fun int(key: Keys, value: Int): Unit = prefs.edit { putInt("$key", value) }

    @JvmStatic
    fun string(key: Keys): String = prefs.getString("$key", "") ?: ""

    @JvmStatic
    fun string(key: Keys, value: String): Unit = prefs.edit { putString("$key", value) }

    @JvmStatic
    fun stringSet(key: Keys, value: Set<String>): Unit = prefs.edit { putStringSet("$key", value) }

    @JvmStatic
    fun boolean(key: Keys): Boolean = prefs.getBoolean("$key", false)

    @JvmStatic
    fun boolean(key: Keys, value: Boolean): Unit = prefs.edit { putBoolean("$key", value) }

    @JvmStatic
    fun init(context: Context) {
        prefs = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        prefs.edit {
            for (entry in Keys.entries) {
                if (!prefs.contains(entry.name)) {
                    when (entry.type) {
                        String::class.java -> putString(entry.name, "")
                        Boolean::class.java -> putBoolean(entry.name, false)
                        Int::class.java -> putInt(entry.name, 0)
                        Long::class.java -> putLong(entry.name, 0L)
                        Float::class.java -> putFloat(entry.name, 0f)
                        Collection::class.java -> putStringSet(entry.name, emptySet<String>())
                    }
                }
            }
        }
    }

    private fun fooBar(key: Keys, value: Any?) {
        when (value) {
            is Boolean -> boolean(key, value)
            is String -> string(key, value)
            is Int -> int(key, value)
            is Collection<*> -> {
                require(value.all { it is String }) {
                    "Expected a collection of string for $key"
                }

                stringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
    }

    @JvmStatic
    fun load(map: Map<String, Any?>) {
        for ((keyRaw, value) in map) {
            if (value == null) continue

            val key = runCatching { Keys.valueOf(keyRaw) }.getOrNull() ?: continue

            try {
                fooBar(key, value)
            } catch (e: Exception) {
                Log.e("ImportLauncher", "Failed to import $key", e)
            }
        }
    }

    @JvmStatic
    fun dump(): Map<String, Any> =
        prefs.all.mapNotNull { (key, value) ->
            if (value == null) return@mapNotNull null
            val enumKey = runCatching { Keys.valueOf(key) }
                .getOrNull() ?: return@mapNotNull null

            enumKey.name to value
        }.toMap()

    @JvmStatic
    fun forContext(context: Context): SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
