package trust.me.broadcaster.util

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkRequest
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import androidx.work.BackoffPolicy
import com.google.gson.Gson
import trust.me.broadcaster.dataclasses.SenderData
import trust.me.broadcaster.workers.ReactorWorker
import trust.me.broadcaster.AppSettings


object WorkerUtils {
    private val gson = Gson()
    private var prev = AtomicLong(System.currentTimeMillis())

    private fun workDelaySec(): Long {
        val now = System.currentTimeMillis()
        val result = ((now - prev.getAndSet(now)) % (AppSettings.int(AppSettings.Keys.MaxDelaySeconds).takeIf { it > 0 } ?: 10)) + 1

        return result
    }

    @JvmStatic
    fun enqueueWork(senderData: SenderData, context: Context): Operation? {
        if (AppSettings.boolean(AppSettings.Keys.PauseTemporarily)) return null

        val inputData = workDataOf(
            ReactorWorker.WorkerKeys.SenderData.value to gson.toJson(senderData)
        )

        val workRequest = OneTimeWorkRequestBuilder<ReactorWorker>()
            .setInputData(inputData)
            .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .setInitialDelay(workDelaySec(), TimeUnit.SECONDS)
            .build()

        return WorkManager.getInstance(context).enqueue(workRequest)
    }
}
