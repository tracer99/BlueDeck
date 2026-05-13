package com.bluebridge.android.di

import android.content.Context
import com.bluebridge.android.data.repository.PreferencesManager
import com.bluebridge.android.data.repository.SecureCredentialsManager
import com.bluebridge.android.data.repository.VehicleRepository
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
