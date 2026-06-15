package com.bluedeck.data.obd

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bluedeck.data.obd.db.ObdDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class ObdRetentionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val retentionManager: ObdLogRetentionManager,
    private val database: ObdDatabase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val activeId = database.sessionDao().getActiveSession()?.id
        retentionManager.pruneExpiredLogs(activeId)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ObdRetentionWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "obd_retention",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
