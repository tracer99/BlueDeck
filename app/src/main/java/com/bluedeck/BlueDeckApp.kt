package com.blueandroid

import android.app.Application
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.IntentFilter
import android.os.Build
import com.blueandroid.automation.VehicleConnectionReceiver
import com.blueandroid.automation.WalkAwayBluetoothMonitorService
import com.blueandroid.data.repository.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BlueAndroidApp : Application() {
    private val vehicleConnectionReceiver = VehicleConnectionReceiver()

    override fun onCreate() {
        super.onCreate()
        registerWalkAwayReceiver()
        startWalkAwayMonitorIfEnabled()
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
