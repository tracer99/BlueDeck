package com.bluedeck.data.obd

import com.bluedeck.data.obd.db.ObdDatabase
import com.bluedeck.data.obd.db.ObdSessionEntity
import com.bluedeck.data.repository.PreferencesManager
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObdLogRetentionManager @Inject constructor(
    private val database: ObdDatabase,
    private val preferencesManager: PreferencesManager
) {
    suspend fun getStorageUsage(): ObdStorageUsage {
        val sessionCount = database.sessionDao().count()
        val tracked = database.sessionDao().totalSizeBytes()
        val dbBytes = database.openHelper.readableDatabase.path
            ?.let { File(it).length() }
            ?: tracked
        return ObdStorageUsage(sessionCount, maxOf(tracked, dbBytes))
    }

    suspend fun pruneExpiredLogs(activeSessionId: Long? = null) {
        val retentionDays = preferencesManager.obdLogRetentionDays.first()
        val maxMb = preferencesManager.obdLogMaxStorageMb.first()
        val maxBytes = if (maxMb > 0) maxMb.toLong() * 1024L * 1024L else Long.MAX_VALUE
        val cutoff = if (retentionDays > 0) {
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
        } else {
            0L
        }

        val sessions = database.sessionDao().getAllOldestFirst()
        for (session in sessions) {
            if (session.id == activeSessionId) continue
            val referenceTime = session.endedAt ?: session.startedAt
            val tooOld = retentionDays > 0 && referenceTime < cutoff
            val usage = getStorageUsage()
            val tooLarge = maxMb > 0 && usage.totalBytes > maxBytes
            if (!tooOld && !tooLarge) break
            if (tooOld || tooLarge) {
                database.sessionDao().deleteById(session.id)
            }
        }
    }

    suspend fun clearAllLogs(activeSessionId: Long? = null) {
        val sessions = database.sessionDao().getAllOldestFirst()
        sessions.forEach { session ->
            if (session.id != activeSessionId) {
                database.sessionDao().deleteById(session.id)
            }
        }
    }

    suspend fun updateSessionSize(sessionId: Long) {
        val session = database.sessionDao().getById(sessionId) ?: return
        val samples = database.sampleDao().getForSession(sessionId)
        val cells = database.cellSnapshotDao().getAllForSession(sessionId)
        val estimated = 200L + samples.size * 120L + cells.sumOf { 100L + it.cellVoltagesJson.length }
        database.sessionDao().update(session.copy(sizeBytes = estimated))
    }
}
