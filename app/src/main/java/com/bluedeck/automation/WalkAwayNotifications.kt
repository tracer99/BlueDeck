package com.bluedeck.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.bluedeck.MainActivity
import com.bluedeck.R

internal object WalkAwayNotifications {
    const val MONITOR_CHANNEL_ID = "walk_away_monitor"
    const val EVENT_CHANNEL_ID = "walk_away_events"
    const val MONITOR_NOTIFICATION_ID = 7102
    private const val EVENT_NOTIFICATION_ID = 7103

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                MONITOR_CHANNEL_ID,
                "Walk-away lock",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows while connected to the selected vehicle Bluetooth for walk-away lock"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                EVENT_CHANNEL_ID,
                "Walk-away lock events",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when walk-away lock is scheduled or completes"
            }
        )
    }

    fun monitorNotification(context: Context, deviceName: String?): Notification {
        ensureChannels(context)
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val label = deviceName?.takeIf { it.isNotBlank() } ?: "vehicle Bluetooth"
        return NotificationCompat.Builder(context, MONITOR_CHANNEL_ID)
            .setContentTitle("Walk-away lock active")
            .setContentText("Monitoring $label")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    fun notifyEvent(context: Context, title: String, text: String) {
        ensureChannels(context)
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, EVENT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            ?.notify(EVENT_NOTIFICATION_ID, notification)
    }
}
