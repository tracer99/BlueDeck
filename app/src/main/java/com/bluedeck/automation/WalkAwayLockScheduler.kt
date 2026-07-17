package com.bluedeck.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log

object WalkAwayLockScheduler {
    private const val TAG = "WalkAwayLock"
    const val ACTION_LOCK = "com.bluedeck.automation.WALK_AWAY_LOCK"
    private const val REQUEST_CODE = 7101

    @Volatile
    private var pending = false

    fun schedule(context: Context, delaySeconds: Int) {
        val appContext = context.applicationContext
        synchronized(this) {
            if (pending) {
                Log.d(TAG, "Lock already scheduled; ignoring duplicate disconnect")
                return
            }
            pending = true
        }

        val seconds = delaySeconds.coerceIn(0, 600)
        if (seconds <= 0) {
            Log.i(TAG, "Scheduling immediate walk-away lock")
            appContext.sendBroadcast(
                Intent(appContext, WalkAwayLockAlarmReceiver::class.java).setAction(ACTION_LOCK)
            )
            return
        }

        val triggerAt = SystemClock.elapsedRealtime() + seconds * 1000L
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: run {
            markNotPending()
            return
        }
        val pendingIntent = pendingIntent(appContext)

        try {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            }
            Log.i(TAG, "Walk-away lock scheduled in ${seconds}s (exact=$canExact)")
            WalkAwayNotifications.notifyEvent(
                appContext,
                "Walk-away lock scheduled",
                "Vehicle Bluetooth disconnected. Locking in $seconds seconds unless it reconnects."
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm not permitted; falling back to inexact", e)
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } catch (fallback: Exception) {
                Log.e(TAG, "Failed to schedule walk-away lock", fallback)
                markNotPending()
            }
        }
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        synchronized(this) {
            pending = false
        }
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = pendingIntent(appContext)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        Log.d(TAG, "Walk-away lock cancelled")
    }

    fun markNotPending() {
        synchronized(this) {
            pending = false
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WalkAwayLockAlarmReceiver::class.java).setAction(ACTION_LOCK)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
