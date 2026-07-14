package com.bluedeck.data.obd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bluedeck.data.repository.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ObdAdapterConnectReceiver : BroadcastReceiver() {

    @Inject lateinit var preferencesManager: PreferencesManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED) return
        val device = intent.getParcelableExtra<android.bluetooth.BluetoothDevice>(
            android.bluetooth.BluetoothDevice.EXTRA_DEVICE
        ) ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val autoConnect = preferencesManager.obdAutoConnect.first()
                val autoStart = preferencesManager.obdAutoStartLogging.first()
                val savedAddress = preferencesManager.obdBluetoothAddress.first()
                val transport = preferencesManager.obdTransportType.first()
                if (autoConnect &&
                    autoStart &&
                    transport == ObdTransportType.BLUETOOTH.name &&
                    !savedAddress.isNullOrBlank() &&
                    device.address.equals(savedAddress, ignoreCase = true)
                ) {
                    ObdLoggingService.start(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
