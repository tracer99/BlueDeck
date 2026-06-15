package com.bluedeck.di

import android.content.Context
import androidx.room.Room
import com.bluedeck.data.obd.db.ObdDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ObdModule {

    @Provides
    @Singleton
    fun provideObdDatabase(@ApplicationContext context: Context): ObdDatabase =
        Room.databaseBuilder(context, ObdDatabase::class.java, "obd_logs.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideObdSessionDao(database: ObdDatabase) = database.sessionDao()

    @Provides
    fun provideObdSampleDao(database: ObdDatabase) = database.sampleDao()

    @Provides
    fun provideObdCellSnapshotDao(database: ObdDatabase) = database.cellSnapshotDao()
}
