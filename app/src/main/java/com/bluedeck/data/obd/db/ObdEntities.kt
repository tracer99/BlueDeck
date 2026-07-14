package com.bluedeck.data.obd.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "obd_sessions")
data class ObdSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vin: String? = null,
    val adapterId: String? = null,
    val profileId: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val sampleCount: Int = 0,
    val driveFileId: String? = null,
    val lightingSupported: Boolean = true,
    val sizeBytes: Long = 0
)

@Entity(
    tableName = "obd_samples",
    foreignKeys = [
        ForeignKey(
            entity = ObdSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class ObdSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val auxVoltageV: Double? = null,
    val aux12vState: String? = null,
    val tractionSoc: Double? = null,
    val tractionSoh: Double? = null,
    val isCharging: Boolean? = null,
    val batteryTempMinC: Double? = null,
    val batteryTempMaxC: Double? = null,
    val batteryTempAvgC: Double? = null,
    val batteryHeaterState: String? = null,
    val batteryFanMode: Int? = null,
    val cellVoltageMinV: Double? = null,
    val cellVoltageMaxV: Double? = null,
    val cellVoltageAvgV: Double? = null,
    val cellVoltageDeviationV: Double? = null,
    val frontMotorRpm: Int? = null,
    val rearMotorRpm: Int? = null,
    val brakeLightOn: Boolean? = null,
    val headlightOn: Boolean? = null
)

@Entity(
    tableName = "obd_cell_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = ObdSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class ObdCellSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val cellVoltagesJson: String
)
