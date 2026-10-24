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
    private val gson = GsonBuilder().setLenient().create()
    private val gsonPretty = GsonBuilder().setPrettyPrinting().setLenient().create()

    private object LuaUtils {
        fun fromJson(src: LuaValue): LuaValue? {
            return try {
                if (src.isstring() && !src.toString().isEmpty())
                    toLuaValue(gson.fromJson(src.toString(), Any::class.java))
                else
                    null
            } catch (_: Exception) {
                null
            }
        }

        fun toJson(obj: LuaValue, pretty: LuaValue = LuaValue.valueOf(false)): String {
            return (if (pretty.optboolean(false)) gsonPretty else gson).toJson(fromLuaValue(obj))
        }

        fun fromTimestamp(value: LuaValue): String {
            return if (value.isnumber())
                SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(value.tolong()))
            else
                "invalid timestamp: $value"
        }

        private fun toLuaValue(obj: Any?): LuaValue {
            return when (obj) {
                null -> LuaValue.NIL

                is String -> LuaValue.valueOf(obj)
                is Boolean -> LuaValue.valueOf(obj)
                is Int -> LuaValue.valueOf(obj)
                is Number -> LuaValue.valueOf(obj.toDouble())

                is Map<*, *> -> LuaTable().apply {
                    obj.forEach { (key, value) ->
                        if (key != null) {
                            set(key.toString(), toLuaValue(value))
                        }
                    }
                }

                is List<*> -> LuaTable().apply {
                    obj.forEachIndexed { index, value ->
                        set(index + 1, toLuaValue(value))
                    }
                }

                is Array<*> -> LuaTable().apply {
                    obj.forEachIndexed { index, value ->
                        set(index + 1, toLuaValue(value))
                    }
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
                        keys.mapNotNull { "$it" }.associateWith { fromLuaValue(table.get(it)) }
                    }
                }
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
            if (outputFn.isnil() || !outputFn.isfunction())
                throw IllegalStateException("'getOutput' fn mission or invalid")

            val outputValue = outputFn.call()
            if (!outputValue.isstring() && !outputValue.istable())
                throw IllegalArgumentException("return value of 'getOutput' is not string or table")

            return Result.success(
                if (outputValue.istable())
                    LuaUtils.toJson(outputValue)
                else
                    outputValue.toString()
            )
        } catch (e: LuaError) {
            throw Exception("Lua Script Error: ${e.message}")
        } catch (e: Exception) {
            return Result.failure(e)
        }
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
        return gsonPretty.toJson(fromBundle(bundle))
    }

    private fun fromBundle(obj: Any?): Any? {
        return when (obj) {
            null -> null
            is Number -> obj
            is Boolean -> obj

            is List<*> -> obj.map { value -> fromBundle(value) }
            is Array<*> -> obj.map { value -> fromBundle(value) }
            is CharArray -> obj.map { value -> value.toString() }

            is Bundle -> obj.keySet().associateWith { value -> fromBundle(obj.get(value)) }

            else -> obj.toString()
        }
    }
}
