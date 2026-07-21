package com.bluedeck.automation

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.bluedeck.data.repository.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Shared helpers for Walk-Away Lock Bluetooth connection checks and
 * connect-gated monitor starts.
 */
internal object WalkAwayBluetooth {
    private const val TAG = "WalkAwayLock"

    fun hasConnectPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun isDeviceConnected(context: Context, address: String): Boolean {
        if (address.isBlank() || !hasConnectPermission(context)) return false
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return false
        if (!adapter.isEnabled) return false
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) {
            return false
        }
        return isAclConnected(device)
    }

    @SuppressLint("MissingPermission", "PrivateApi")
    private fun isAclConnected(device: BluetoothDevice): Boolean {
        return try {
            val method = BluetoothDevice::class.java.getMethod("isConnected")
            method.invoke(device) as Boolean
        } catch (e: Exception) {
            Log.d(TAG, "Unable to read Bluetooth connection state", e)
            false
        }
    }

    /**
     * Starts the monitor foreground service only when walk-away is enabled and
     * the selected vehicle Bluetooth device is currently connected.
     */
    fun startMonitorIfConnected(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            startMonitorIfConnectedSuspending(appContext)
        }
    }

    suspend fun startMonitorIfConnectedSuspending(context: Context) {
        val prefs = PreferencesManager(context.applicationContext)
        if (!prefs.walkAwayLockEnabled.first()) return
        val address = prefs.walkAwayBluetoothAddress.first()
        if (address.isNullOrBlank()) return
        if (isDeviceConnected(context, address)) {
            Log.i(TAG, "Selected vehicle Bluetooth connected; starting monitor")
            WalkAwayBluetoothMonitorService.start(context)
        } else {
            Log.d(TAG, "Walk-away enabled but vehicle Bluetooth not connected; monitor idle")
        }
    }
}
