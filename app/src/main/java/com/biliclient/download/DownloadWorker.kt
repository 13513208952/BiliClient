package com.biliclient.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.biliclient.data.db.DownloadDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 续传兜底（doc 08 §3.2）：进程被杀后，重新把未完成任务投入下载（调用前台 Service）。
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: DownloadDao
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val pending = dao.unfinished()
        for (task in pending) {
            DownloadService.enqueue(applicationContext, task.id)
        }
        return Result.success()
    }

    companion object {
        fun scheduleResume(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<DownloadWorker>().build()
            WorkManager.getInstance(ctx)
                .enqueueUniqueWork("download_resume", ExistingWorkPolicy.REPLACE, req)
        }
    }
}
