package trust.me.broadcaster.util

import android.app.Notification
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.service.notification.StatusBarNotification
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import org.luaj.vm2.lib.jse.JsePlatform
import trust.me.broadcaster.dataclasses.SenderData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

object DataConversion {
    private object LuaUtils {
        private val gson = GsonBuilder().setLenient().create()
        private val gsonPretty = GsonBuilder().setPrettyPrinting().setLenient().create()

        fun fromJson(src: LuaValue): LuaValue? {
            return try {
                if (src.isstring() && !src.toString().isEmpty()) toLuaValue(gson.fromJson(src.toString(), Any::class.java))
                else null
            } catch (_: Exception) {
                null
            }
        }

        fun toJson(obj: LuaValue, pretty: LuaValue = LuaValue.valueOf(false)): String {
            return (if (pretty.optboolean(false)) gsonPretty else gson).toJson(fromLuaValue(obj))
        }

        fun fromTimestamp(value: LuaValue): String =
            if (value.isnumber()) SimpleDateFormat(
                "dd MMM yyyy, hh:mm a",
                Locale.getDefault()
            ).format(Date(value.tolong()))
            else "Invalid timestamp '$value'"

        private fun toLuaValue(obj: Any?): LuaValue {
            return when (obj) {
                null -> LuaValue.NIL
                is String -> LuaValue.valueOf(obj)
                is Number -> LuaValue.valueOf(obj.toDouble())
                is Boolean -> LuaValue.valueOf(obj)
                is Map<*, *> -> {
                    val table = LuaTable()
                    for ((key, value) in obj.filter { it.key != null }.mapNotNull { "${it.key}" to it.value }) {
                        table.set(key, toLuaValue(value))
                    }

                    table
                }
                is List<*> -> {
                    val table = LuaTable()
                    for (i in obj.indices) {
                        table.set(i + 1, toLuaValue(obj[i]))
                    }

                    table
                }
                else -> LuaValue.NIL
            }
        }

        private fun fromLuaValue(value: LuaValue): Any? {
            return when {
                value.isnil() -> null
                value.isstring() -> value.toString()
                value.isboolean() -> value.toboolean()
                value.isint() -> value.toint()
                value.islong() -> value.tolong()
                value.isnumber() -> {
                    val double = value.todouble()
                    if (double % 1.0 == 0.0) double.toLong() else double
                }
                value.istable() -> {
                    val table = value.checktable()
                    val keys = table.keys()
                    val length = table.length()

                    val isArray = keys.size == length && length > 0 && (1..length).all { !table.get(it).isnil() }
                    if (isArray) {
                        ((1..length).map { fromLuaValue(table.get(it)) }.toMutableList())
                    } else {
                        mutableMapOf(*keys.mapNotNull { "$it" to fromLuaValue(table.get(it)) }.toTypedArray())
                    }
                }
                value.isfunction() || value.isclosure() || value.isuserdata() -> null
                else -> null
            }
        }
    }

    @JvmStatic
    fun SenderData.evalBody(script: String): Result<String> {
        try {
            val globals = JsePlatform.standardGlobals()
            globals.set("data", CoerceJavaToLua.coerce(LuaValue.tableOf().apply {
                set("id", LuaValue.valueOf(this@evalBody.id))
                set("name", LuaValue.valueOf(this@evalBody.name))
                set("content", LuaValue.valueOf(this@evalBody.content))
                set("timestamp", LuaValue.valueOf(this@evalBody.timestamp.toDouble()))
                set("extras", LuaValue.valueOf(this@evalBody.extras))
            }))
            globals.set("utils", CoerceJavaToLua.coerce(LuaValue.tableOf().apply {
                set("fromJson", object : OneArgFunction() {
                    override fun call(arg: LuaValue): LuaValue =
                    LuaUtils.fromJson(arg) ?: NIL
                })

                set("toJson", object : TwoArgFunction() {
                    override fun call(obj: LuaValue, pretty: LuaValue): LuaValue =
                        valueOf(LuaUtils.toJson(obj, pretty))
                })

                set("fromTimestamp", object : OneArgFunction() {
                    override fun call(arg: LuaValue): LuaValue =
                        valueOf(LuaUtils.fromTimestamp(arg))
                })
            }))

            globals.load(script).call()
            val outputFn = globals.get("getOutput")
            if (outputFn.isnil() || !outputFn.isfunction()) throw IllegalStateException("'getOutput' fn mission or invalid")

            val outputValue = outputFn.call()
            if (!outputValue.isstring() && !outputValue.istable()) throw IllegalArgumentException("return value of 'getOutput' is not string or table")

            return Result.success(
                if (outputValue.istable()) LuaUtils.toJson(outputValue) else outputValue.toString()
            )
        } catch (e: LuaError) {
            throw Exception("Lua Script Error: ${e.message}")
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    @JvmStatic
    fun SenderData.format(template: String): String {
        // 1. Extract properties dynamically using Java reflection
        val propMap = mutableMapOf<String, Any?>()
        this::class.java.declaredFields.forEach { field ->
            field.isAccessible = true
            if (!field.isSynthetic) { // ignore compiler-generated fields
                propMap[field.name] = field.get(this)
            }
        }

        // 2. Parse extras JSON map once for nested lookups
        val extrasMap: Map<String, Any>? = try {
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            Gson().fromJson(extras, mapType)
        } catch (_: Exception) {
            null
        }

        // 3. Find and replace template placeholders safely using Regex
        val pattern = Pattern.compile("\\{\\{([^}]+)\\}\\}")
        val matcher = pattern.matcher(template)
        val sb = StringBuffer()

        while (matcher.find()) {
            val rawKey = matcher.group(1)?.trim() ?: ""
            val replacement = when {
                // Rule 3: sender_extras -> whole JSON string
                rawKey == "sender_extras" -> extras

                // Rule 4: sender_extras.<key> -> get key from parsed JSON (empty/null if not found)
                rawKey.startsWith("sender_extras.") -> {
                    val subKey = rawKey.removePrefix("sender_extras.")
                    extrasMap?.get(subKey)?.toString() ?: ""
                }

                // Rule 1: sender_<dataclass_prop> -> Valid (prop_value)
                rawKey.startsWith("sender_") -> {
                    val propName = rawKey.removePrefix("sender_")
                    if (propMap.containsKey(propName)) {
                        propMap[propName]?.toString() ?: ""
                    } else {
                        matcher.group(0) // Property doesn't exist, leave as is
                    }
                }

                // Rule 2: <dataclass_prop> without sender_ -> invalid (let it be as it is)
                else -> matcher.group(0)
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    @JvmStatic
    fun fromSMSMessage(intent: Intent): SenderData {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        val content = StringBuilder()
        val gson = Gson()

        for (message in messages) {
            content.append(message.messageBody)
        }

        val message = messages[0]
        return SenderData(
            id = message.originatingAddress ?: "Unknown",
            name = message.displayOriginatingAddress ?: "UnknownAddress",
            content = content.toString(),
            timestamp = message.timestampMillis,
            extras = gson.toJson(mapOf<String, Any>(
                "emailFrom" to message.emailFrom,
                "status" to message.status
            ))
        )
    }

    @JvmStatic
    fun fromNotification(sbn: StatusBarNotification): SenderData {
        val notification = sbn.notification
        val extras: Bundle = notification.extras

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

        val content = buildString {
            if (!text.isNullOrBlank()) appendLine(text)
            if (!bigText.isNullOrBlank()) {
                appendLine(" === More Info ===")
                appendLine(bigText)
            }
            if (!subText.isNullOrBlank()) appendLine(subText)
        }.trim()

        val senderData = SenderData(
            id = sbn.packageName ?: "unknown.package",
            name = title ?: sbn.tag ?: sbn.packageName,
            content = content,
            timestamp = notification.`when`,
            extras = bundleJson(notification.extras)
        )

        return senderData
    }

    private fun bundleJson(bundle: Bundle): String {
        val map = HashMap<String, Any?>()

        for (key in bundle.keySet()) {
            val value = bundle.get(key)

            map[key] = when (value) {
                null -> null
                is Bundle -> Gson().fromJson(
                    bundleJson(value),
                    Map::class.java
                )
                is CharSequence -> value.toString()
                is Number, is Boolean -> value
                is Array<*> -> value.toList()
                else -> value.toString()
            }
        }

        return GsonBuilder()
            .setPrettyPrinting()
            .create()
            .toJson(map)
    }
}
