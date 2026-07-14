package com.bluedeck.data.obd.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ObdSessionEntity::class,
        ObdSampleEntity::class,
        ObdCellSnapshotEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ObdDatabase : RoomDatabase() {
    abstract fun sessionDao(): ObdSessionDao
    abstract fun sampleDao(): ObdSampleDao
    abstract fun cellSnapshotDao(): ObdCellSnapshotDao
}
