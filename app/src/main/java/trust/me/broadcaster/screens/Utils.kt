package trust.me.broadcaster.screens

import android.content.pm.PackageManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import trust.me.broadcaster.services.NotificationService


fun isNotificationServiceEnabled(context: Context): Boolean {
    return when (context.packageManager.getComponentEnabledSetting(ComponentName(context, NotificationService::class.java))) {
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
        else -> true // DEFAULT (manifest value, enabled) or ENABLED
    }
}

fun hasNotificationAccess(context: Context): Boolean {
    val enabledListeners = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false

    val component = ComponentName(
        context,
        NotificationService::class.java
    )

    return enabledListeners.split(':').any {
        it.equals(component.flattenToString(), ignoreCase = true)
    }
}

fun highlightLua(code: String): AnnotatedString {
    val keywords = setOf(
        "and", "break", "do", "else", "elseif", "end",
        "false", "for", "function", "goto", "if", "in",
        "local", "nil", "not", "or", "repeat", "return",
        "then", "true", "until", "while"
    )

    val pattern = Regex(
        """--\[\[[\s\S]*?]]|--[^\n]*|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\b\d+(?:\.\d+)?\b|\b[A-Za-z_]\w*\b"""
    )

    return buildAnnotatedString {
        append(code)

        pattern.findAll(code).forEach { match ->
            val token = match.value

            val color = when {
                token in keywords -> Color(0xFFC586C0)
                token.startsWith("--") -> Color(0xFF6A9955)
                token.first().isDigit() -> Color(0xFFB5CEA8)
                token.startsWith("\"") || token.startsWith("'") -> Color(0xFFCE9178)
                else -> null
            }

            if (color != null) {
                addStyle(
                    SpanStyle(color = color),
                    match.range.first,
                    match.range.last + 1
                )
            }
        }
    }
}

