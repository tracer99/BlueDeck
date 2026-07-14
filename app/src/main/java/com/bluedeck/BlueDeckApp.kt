package com.bluedeck

import android.app.Application
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.IntentFilter
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.bluedeck.automation.VehicleConnectionReceiver
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
    private val vehicleConnectionReceiver = VehicleConnectionReceiver()

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        registerWalkAwayReceiver()
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

    private fun registerWalkAwayReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(vehicleConnectionReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(vehicleConnectionReceiver, filter)
        }
    }
}
