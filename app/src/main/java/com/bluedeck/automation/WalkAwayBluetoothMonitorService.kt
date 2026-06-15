package com.bluedeck.automation

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

class WalkAwayBluetoothMonitorService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            context.startService(Intent(context, WalkAwayBluetoothMonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WalkAwayBluetoothMonitorService::class.java))
        }
    }
}
