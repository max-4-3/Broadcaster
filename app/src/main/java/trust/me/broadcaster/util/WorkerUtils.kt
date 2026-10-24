package trust.me.broadcaster.util

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.gson.Gson
import trust.me.broadcaster.dataclasses.SenderData
import trust.me.broadcaster.workers.ReactorWorker


object WorkerUtils {
    private val gson = Gson()

    @JvmStatic
    fun enqueueWork(senderData: SenderData, context: Context): Operation {
        val inputData = workDataOf(
            ReactorWorker.WorkerKeys.SenderData.value to gson.toJson(senderData)
        )

        val workRequest = OneTimeWorkRequestBuilder<ReactorWorker>()
            .setInputData(inputData)
            .build()

        return WorkManager.getInstance(context).enqueue(workRequest)
    }
}
