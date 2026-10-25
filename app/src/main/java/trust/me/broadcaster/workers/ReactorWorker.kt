package trust.me.broadcaster.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import trust.me.broadcaster.AppSettings
import trust.me.broadcaster.dataclasses.ReactorConfig
import trust.me.broadcaster.dataclasses.SenderData
import trust.me.broadcaster.util.DataConversion.evalBody
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException


class ReactorWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "REACTOR"
        private const val CHANNEL_ID = "reactor_channel"
        private const val GROUP_KEY = "reactor_group"

        private const val MASTER_ID = 1001
        private const val REACTOR_ID_BASE = 2000
    }

    enum class WorkerKeys(val value: String) {
        SenderData("sender_data")
    }

    private lateinit var gson: Gson
    private lateinit var notificationManager: NotificationManagerCompat
    private var showNotifications: Boolean = true

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        init(applicationContext)
        createNotificationChannel()

        val senderJson = inputData.getString(WorkerKeys.SenderData.value).orEmpty()
        if (senderJson.isEmpty()) {
            return@withContext Result.success()
        }

        val configs = AppSettings.string(AppSettings.Keys.ReactorConfig)
            .takeIf { it.isNotEmpty() }
            ?.let {
                gson.fromJson<List<ReactorConfig>>(
                    it,
                    object : TypeToken<List<ReactorConfig>>() {}.type
                )
            }
            .orEmpty()
            .filter { it.endpoint.isNotEmpty() }

        if (configs.isEmpty()) {
            return@withContext Result.success()
        }

        val senderData = gson.fromJson(senderJson, SenderData::class.java)
        val total = configs.size
        var success = 0

        masterNotification(0, total, 0, "Starting reactors")

        // Initially show all reactors as queued.
        configs.forEachIndexed { index, config ->
            reactorNotification(index, config.name, total, Stage.QUEUED)
        }

        try {
            configs.forEachIndexed { index, config ->
                ensureActive()

                val current = index + 1

                masterNotification(
                    index, total, success,
                    "Reactor $current · Starting"
                )

                try {
                    reactorNotification(index, config.name, total, Stage.GENERATING)
                    masterNotification(
                        index, total, success,
                        "Reactor $current · Generating body"
                    )

                    val bodyResult = senderData.evalBody(config.bodyLua)
                    val body = bodyResult.getOrNull()

                    if (bodyResult.isFailure || body.isNullOrEmpty()) {
                        throw IllegalStateException(
                            "Body generation failed",
                            bodyResult.exceptionOrNull()
                        )
                    }

                    ensureActive()

                    reactorNotification(index, config.name, total, Stage.CONNECTING, expandDetail = "Generated body:\n$body")

                    val connection = (URL(config.endpoint)
                        .openConnection() as HttpURLConnection).apply {
                        requestMethod = config.method
                        setRequestProperty("Content-Type", config.ctype)
                        connectTimeout = 10_000
                        readTimeout = 10_000
                    }

                    var code: Int
                    var responseBody: String

                    try {
                        reactorNotification(index, config.name, total, Stage.SENDING, expandDetail = "${config.method} on:\n${config.endpoint}\n${body}\nContent-Type: ${config.ctype}")
                        masterNotification(
                            index, total, success,
                            "Reactor $current · Sending request"
                        )

                        if (config.method.equals("POST", true)) {
                            connection.doOutput = true
                            OutputStreamWriter(connection.outputStream).use {
                                it.write(body)
                                it.flush()
                            }
                        }

                        ensureActive()

                        code = connection.responseCode
                        responseBody = (
                            if (code in 200..299)
                                connection.inputStream
                            else
                                connection.errorStream
                        )?.bufferedReader()?.use { it.readText() }.orEmpty()

                        reactorNotification(
                            index, config.name, total,
                            Stage.VALIDATING,
                            "Checking HTTP $code",
                        )
                    } finally {
                        connection.disconnect()
                    }

                    val ok = code in config.expected
                    if (ok) success++

                    reactorNotification(
                        index, config.name, total, if (ok) Stage.SUCCESS else Stage.FAILED,
                        "${if (!ok) "Unexpected " else ""}HTTP $code · ${config.method}",
                        expandDetail = "[$code]:\n === Response ===\n${responseBody}\n === Request ===\n${body}\n"
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Reactor $current failed", e)

                    reactorNotification(
                        index, config.name,total, Stage.FAILED,
                        e.message ?: "Unknown error"
                    )
                }

                masterNotification(
                    current, total, success,
                    "Reactor $current completed"
                )
            }
        } catch (e: CancellationException) {
            masterNotification(
                total, total, success,
                "Cancelled", finished = true
            )
            throw e
        }

        masterNotification(
            total, total, success,
            "Finished", finished = true
        )

        Log.d(TAG, "Completed: $success/$total")
        Result.success()
    }

    private fun init(context: Context) {
        gson = Gson()
        notificationManager = NotificationManagerCompat.from(context)
        showNotifications = AppSettings.boolean(AppSettings.Keys.ShowNotifications)
    }

    private fun notify(id: Int, builder: NotificationCompat.Builder) {
        try {
            if (!showNotifications) {
                notificationManager.cancel(id)
                return
            }

            notificationManager.notify(id, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission denied", e)
        }
    }

    private fun baseNotification(): NotificationCompat.Builder =
        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setGroup(GROUP_KEY)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

    private fun masterNotification(
        completed: Int,
        total: Int,
        success: Int,
        current: String,
        finished: Boolean = false
    ) {
        if (!showNotifications) return
        val failed = completed - success

        val builder = baseNotification()
            .setContentTitle(
                if (finished) "Broadcaster · Finished"
                else "Broadcaster · Processing"
            )
            .setContentText(
                if (finished) "$success succeeded · $failed failed"
                else "$completed/$total completed · $current"
            )
            .setSubText("$success succeeded · $failed failed")
            .setGroupSummary(true)
            .setOngoing(!finished)
            .setAutoCancel(finished)

        if (!finished) {
            builder.setProgress(total, completed, false)
        }

        notify(MASTER_ID, builder)
    }

    private enum class Stage(val description: String, val progress: Int) {
        QUEUED("Waiting in queue", 0),
        GENERATING("Generating Lua body", 1),
        CONNECTING("Preparing HTTP connection", 2),
        SENDING("Sending HTTP request", 3),
        VALIDATING("Validating HTTP response", 4),
        SUCCESS("Successfully completed", 4),
        FAILED("Execution failed", 4)
    }

    private fun reactorNotification(
        index: Int,
        name: String,
        total: Int,
        stage: Stage,
        detail: String = "",
        expandDetail: String = "",
    ) {
        if (!showNotifications) return
        val finished = stage == Stage.SUCCESS || stage == Stage.FAILED

        val title = when (stage) {
            Stage.SUCCESS -> "Reactor(${name}) ${index + 1} · Success"
            Stage.FAILED -> "Reactor(${name}) ${index + 1} · Failed"
            else -> "Reactor(${name}) ${index + 1}/$total · Running"
        }

        val builder = baseNotification()
            .setContentTitle(title)
            .setContentText(detail.ifEmpty { stage.description })
            .setSubText(
                if (finished) stage.description
                else "Step ${stage.progress}/4"
            )
            .setOngoing(!finished)
            .setAutoCancel(finished)

        when (stage) {
            Stage.QUEUED -> builder.setProgress(0, 0, true)
            Stage.SUCCESS -> builder.setSmallIcon(android.R.drawable.stat_sys_upload_done)
            Stage.FAILED -> builder.setSmallIcon(android.R.drawable.stat_notify_error)
            else -> builder.setProgress(4, stage.progress, false)
        }

        if (expandDetail.isNotEmpty()) builder.setStyle(
            NotificationCompat.BigTextStyle()
                .bigText(expandDetail)
                .setBigContentTitle("More Details...")
                .setSummaryText(detail.ifEmpty { stage.description })
        )

        notify(REACTOR_ID_BASE + index, builder)
    }

    private fun createNotificationChannel() {
        if (!showNotifications) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reactor Notifications",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Reactor execution progress"
            }

            val manager = applicationContext.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

            manager.createNotificationChannel(channel)
        }
    }
}
