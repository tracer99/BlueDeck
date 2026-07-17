package com.bluedeck.automation

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.bluedeck.data.repository.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Lightweight foreground service that keeps walk-away monitoring visible and
 * helps the process stay eligible to receive Bluetooth disconnect events.
 * Detection itself is event-driven via [VehicleConnectionReceiver].
 */
class WalkAwayBluetoothMonitorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                // Must call startForeground promptly after startForegroundService.
                promoteToForeground(deviceName = null)
                serviceScope.launch {
                    val prefs = PreferencesManager(applicationContext)
                    val enabled = prefs.walkAwayLockEnabled.first()
                    val address = prefs.walkAwayBluetoothAddress.first()
                    if (!enabled || address.isNullOrBlank()) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        return@launch
                    }
                    val deviceName = prefs.walkAwayBluetoothName.first()
                    promoteToForeground(deviceName)
                }
            }
        }
        return START_STICKY
    }

    private fun promoteToForeground(deviceName: String?) {
        val notification = WalkAwayNotifications.monitorNotification(this, deviceName)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    WalkAwayNotifications.MONITOR_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(WalkAwayNotifications.MONITOR_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Receivers + AlarmManager still handle disconnect → lock without FGS.
            Log.w(TAG, "Could not start walk-away foreground service; continuing with receivers only", e)
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WalkAwayLock"
        private const val ACTION_STOP = "com.bluedeck.automation.WALK_AWAY_STOP"

        fun start(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, WalkAwayBluetoothMonitorService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to start walk-away monitor service", e)
            }
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            try {
                val intent = Intent(appContext, WalkAwayBluetoothMonitorService::class.java)
                    .setAction(ACTION_STOP)
                // startForegroundService is not required for stop; prefer startService.
                appContext.startService(intent)
            } catch (_: Exception) {
                appContext.stopService(Intent(appContext, WalkAwayBluetoothMonitorService::class.java))
            }
        }
    }
}
