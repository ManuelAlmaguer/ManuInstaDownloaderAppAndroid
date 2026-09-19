package com.manu.reeldrop.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.manu.reeldrop.core.ServiceLocator

/**
 * Safety net that resumes queued downloads when the network comes back, when the app is
 * restarted, or when a foreground service start was refused by the OS.
 */
class ResumeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        ServiceLocator.init(applicationContext)
        return runCatching {
            ServiceLocator.engine.resumePending()
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        private const val WORK_NAME = "reeldrop_resume_downloads"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<ResumeWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            runCatching {
                WorkManager.getInstance(context)
                    .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
            }
        }
    }
}
