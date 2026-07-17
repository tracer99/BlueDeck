package com.bluedeck.automation

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.bluedeck.data.repository.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class VehicleConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "BLUETOOTH_CONNECT missing; ignoring Bluetooth event")
            return
        }

        val device = bluetoothDevice(intent) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = PreferencesManager(context.applicationContext)
                if (!prefs.walkAwayLockEnabled.first()) return@launch
                val selected = prefs.walkAwayBluetoothAddress.first()
                if (selected.isNullOrBlank() ||
                    !device.address.equals(selected, ignoreCase = true)
                ) {
                    return@launch
                }

                when (action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        Log.i(TAG, "Selected vehicle Bluetooth connected; cancelling pending lock")
                        WalkAwayLockScheduler.cancel(context)
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        Log.i(TAG, "Selected vehicle Bluetooth ACL disconnected")
                        WalkAwayLockScheduler.schedule(
                            context,
                            prefs.walkAwayLockDelaySeconds.first()
                        )
                    }
                    BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
                    BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                        when (state) {
                            BluetoothProfile.STATE_CONNECTED -> {
                                Log.i(TAG, "Selected vehicle profile connected; cancelling pending lock")
                                WalkAwayLockScheduler.cancel(context)
                            }
                            BluetoothProfile.STATE_DISCONNECTED -> {
                                Log.i(TAG, "Selected vehicle profile disconnected")
                                WalkAwayLockScheduler.schedule(
                                    context,
                                    prefs.walkAwayLockDelaySeconds.first()
                                )
                            }
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun bluetoothDevice(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    companion object {
        private const val TAG = "WalkAwayLock"

        private val HANDLED_ACTIONS = setOf(
            BluetoothDevice.ACTION_ACL_CONNECTED,
            BluetoothDevice.ACTION_ACL_DISCONNECTED,
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED
        )
    }
}
