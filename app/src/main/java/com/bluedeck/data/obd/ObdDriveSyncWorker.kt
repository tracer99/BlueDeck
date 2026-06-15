package com.bluedeck.data.obd

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ObdDriveSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val driveSyncManager: ObdDriveSyncManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getLong(KEY_SESSION_ID, -1L)
        if (sessionId < 0) return Result.failure()
        return driveSyncManager.syncSession(sessionId)
            .fold(
                onSuccess = { Result.success() },
                onFailure = { Result.retry() }
            )
    }

    companion object {
        private const val KEY_SESSION_ID = "session_id"

        fun enqueue(context: Context, sessionId: Long) {
            val request = OneTimeWorkRequestBuilder<ObdDriveSyncWorker>()
                .setInputData(workDataOf(KEY_SESSION_ID to sessionId))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "obd_drive_sync_$sessionId",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
