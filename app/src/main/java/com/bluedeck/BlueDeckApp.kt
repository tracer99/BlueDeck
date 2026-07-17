package com.bluedeck

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.bluedeck.automation.WalkAwayBluetoothMonitorService
import com.bluedeck.data.obd.ObdRetentionWorker
import com.bluedeck.data.repository.PreferencesManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class BlueDeckApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        startWalkAwayMonitorIfEnabled()
        ObdRetentionWorker.schedule(applicationContext)
    }

    private fun startWalkAwayMonitorIfEnabled() {
        CoroutineScope(Dispatchers.IO).launch {
            val prefs = PreferencesManager(applicationContext)
            val enabled = prefs.walkAwayLockEnabled.first()
            val selectedDevice = prefs.walkAwayBluetoothAddress.first()
            if (enabled && !selectedDevice.isNullOrBlank()) {
                WalkAwayBluetoothMonitorService.start(applicationContext)
            }
        }
    }
}
