package com.blueandroid.di

import android.content.Context
import com.blueandroid.data.repository.PreferencesManager
import com.blueandroid.data.repository.SecureCredentialsManager
import com.blueandroid.data.repository.VehicleRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePreferencesManager(@ApplicationContext context: Context): PreferencesManager =
        PreferencesManager(context)

    @Provides
    @Singleton
    fun provideVehicleRepository(
        preferencesManager: PreferencesManager,
        secureCredentialsManager: SecureCredentialsManager
    ): VehicleRepository =
        VehicleRepository(preferencesManager, secureCredentialsManager)
}
