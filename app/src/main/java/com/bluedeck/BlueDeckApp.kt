package com.bluedeck

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.bluedeck.automation.WalkAwayBluetooth
import com.bluedeck.data.obd.ObdRetentionWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BlueDeckApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Connect-gated: only show the monitor while vehicle Bluetooth is connected.
        WalkAwayBluetooth.startMonitorIfConnected(applicationContext)
        ObdRetentionWorker.schedule(applicationContext)
    }
}
