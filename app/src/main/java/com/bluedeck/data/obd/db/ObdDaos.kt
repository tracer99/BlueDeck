package com.bluedeck.data.obd.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ObdSessionDao {
    @Insert
    suspend fun insert(session: ObdSessionEntity): Long

    @Update
    suspend fun update(session: ObdSessionEntity): Int

    @Query("SELECT * FROM obd_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<ObdSessionEntity>>

    @Query("SELECT * FROM obd_sessions WHERE id = :id")
    suspend fun getById(id: Long): ObdSessionEntity?

    @Query("SELECT * FROM obd_sessions ORDER BY COALESCE(endedAt, startedAt) ASC")
    suspend fun getAllOldestFirst(): List<ObdSessionEntity>

    @Query("SELECT COUNT(*) FROM obd_sessions")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM obd_sessions")
    suspend fun totalSizeBytes(): Long

    @Query("DELETE FROM obd_sessions WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM obd_sessions")
    suspend fun deleteAll(): Int

    @Query("SELECT * FROM obd_sessions WHERE endedAt IS NULL LIMIT 1")
    suspend fun getActiveSession(): ObdSessionEntity?
}

@Dao
interface ObdSampleDao {
    @Insert
    suspend fun insert(sample: ObdSampleEntity): Long

    @Query("SELECT * FROM obd_samples WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getForSession(sessionId: Long): List<ObdSampleEntity>

    @Query("SELECT * FROM obd_samples WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun observeForSession(sessionId: Long): Flow<List<ObdSampleEntity>>
}

@Dao
interface ObdCellSnapshotDao {
    @Insert
    suspend fun insert(snapshot: ObdCellSnapshotEntity): Long

    @Query("SELECT * FROM obd_cell_snapshots WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestForSession(sessionId: Long): ObdCellSnapshotEntity?

    @Query("SELECT * FROM obd_cell_snapshots WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getAllForSession(sessionId: Long): List<ObdCellSnapshotEntity>
}
