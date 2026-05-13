package com.bluebridge.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.bluebridge.android.MainActivity
import com.bluebridge.android.R
import com.bluebridge.android.data.models.CommandHistoryEntry
import com.bluebridge.android.data.models.Vehicle
import com.bluebridge.android.data.models.VehicleStatusData
import com.bluebridge.android.data.models.WidgetVehicleSnapshot
import com.bluebridge.android.data.repository.PreferencesManager
import com.bluebridge.android.data.repository.Result
import com.bluebridge.android.data.repository.SecureCredentialsManager
import com.bluebridge.android.data.repository.VehicleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

open class VehicleWidgetProvider : AppWidgetProvider() {

    protected open val widgetKind: WidgetKind = WidgetKind.FULL

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context.applicationContext, appWidgetManager, appWidgetId, widgetKind)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        refreshAll(context.applicationContext)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action !in widgetActions) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleWidgetAction(context.applicationContext, action)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val ACTION_REFRESH = "com.bluebridge.android.widget.REFRESH"
        private const val ACTION_LOCK = "com.bluebridge.android.widget.LOCK"
        private const val ACTION_UNLOCK = "com.bluebridge.android.widget.UNLOCK"
        private const val ACTION_CLIMATE = "com.bluebridge.android.widget.CLIMATE"

        private val widgetActions = setOf(ACTION_REFRESH, ACTION_LOCK, ACTION_UNLOCK, ACTION_CLIMATE)

        private val providers = listOf(
            VehicleWidgetProvider::class.java,
            BatteryWidgetProvider::class.java,
            BatteryWideWidgetProvider::class.java,
            LockWidgetProvider::class.java,
            UnlockWidgetProvider::class.java,
            ClimateWidgetProvider::class.java,
            RefreshWidgetProvider::class.java,
            ControlsWidgetProvider::class.java
        )

        fun refreshAll(context: Context) {
            val appContext = context.applicationContext
            val appWidgetManager = AppWidgetManager.getInstance(appContext)
            providers.forEach { providerClass ->
                val ids = appWidgetManager.getAppWidgetIds(ComponentName(appContext, providerClass))
                val kind = kindForProvider(providerClass)
                ids.forEach { appWidgetId -> updateWidget(appContext, appWidgetManager, appWidgetId, kind) }
            }
        }

        private fun kindForProvider(providerClass: Class<out AppWidgetProvider>): WidgetKind = when (providerClass) {
            BatteryWidgetProvider::class.java -> WidgetKind.BATTERY_COMPACT
            BatteryWideWidgetProvider::class.java -> WidgetKind.BATTERY_WIDE
            LockWidgetProvider::class.java -> WidgetKind.LOCK
            UnlockWidgetProvider::class.java -> WidgetKind.UNLOCK
            ClimateWidgetProvider::class.java -> WidgetKind.CLIMATE
            RefreshWidgetProvider::class.java -> WidgetKind.REFRESH
            ControlsWidgetProvider::class.java -> WidgetKind.CONTROLS
            else -> WidgetKind.FULL
        }

        private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, kind: WidgetKind) {
            CoroutineScope(Dispatchers.IO).launch {
                val preferencesManager = PreferencesManager(context.applicationContext)
                val snapshot = preferencesManager.widgetVehicleSnapshot.first()
                val views = buildViews(context, snapshot, kind)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun buildViews(context: Context, snapshot: WidgetVehicleSnapshot, kind: WidgetKind): RemoteViews {
            val views = RemoteViews(context.packageName, kind.layoutRes)
            val openPendingIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return when (kind) {
                WidgetKind.FULL -> buildFullWidget(context, views, snapshot, openPendingIntent)
                WidgetKind.BATTERY_COMPACT -> buildBatteryCompactWidget(context, views, snapshot, openPendingIntent)
                WidgetKind.BATTERY_WIDE -> buildBatteryWideWidget(context, views, snapshot, openPendingIntent)
                WidgetKind.LOCK -> buildActionWidget(context, views, snapshot, "Lock", lockStatus(snapshot), ACTION_LOCK, 21, "Lock vehicle doors")
                WidgetKind.UNLOCK -> buildActionWidget(context, views, snapshot, "Unlock", lockStatus(snapshot), ACTION_UNLOCK, 22, "Unlock vehicle doors")
                WidgetKind.CLIMATE -> buildActionWidget(context, views, snapshot, "Climate", snapshot.chargingLabel.ifBlank { snapshot.message }, ACTION_CLIMATE, 23, "Start cabin climate")
                WidgetKind.REFRESH -> buildActionWidget(context, views, snapshot, "Refresh", updatedStatus(snapshot), ACTION_REFRESH, 24, "Refresh vehicle status")
                WidgetKind.CONTROLS -> buildControlsWidget(context, views, snapshot, openPendingIntent)
            }
        }

        private fun buildFullWidget(
            context: Context,
            views: RemoteViews,
            snapshot: WidgetVehicleSnapshot,
            openPendingIntent: PendingIntent
        ): RemoteViews {
            views.setOnClickPendingIntent(R.id.widget_root, openPendingIntent)
            views.setTextViewText(R.id.widget_vehicle_name, snapshot.vehicleName.ifBlank { "BlueBridge" })
            views.setTextViewText(R.id.widget_lock_status, lockStatus(snapshot))
            setBatteryViews(views, snapshot, includeBatteryPrefix = true)
            views.setTextViewText(R.id.widget_charging, snapshot.chargingLabel.ifBlank { snapshot.message })
            views.setTextViewText(R.id.widget_updated, updatedStatus(snapshot))
            views.setOnClickPendingIntent(R.id.widget_refresh_button, widgetPendingIntent(context, ACTION_REFRESH, 10))
            views.setOnClickPendingIntent(R.id.widget_lock_button, widgetPendingIntent(context, ACTION_LOCK, 11))
            views.setOnClickPendingIntent(R.id.widget_unlock_button, widgetPendingIntent(context, ACTION_UNLOCK, 12))
            views.setOnClickPendingIntent(R.id.widget_climate_button, widgetPendingIntent(context, ACTION_CLIMATE, 13))
            views.setContentDescription(R.id.widget_refresh_button, "Refresh vehicle status")
            views.setContentDescription(R.id.widget_lock_button, "Lock vehicle doors")
            views.setContentDescription(R.id.widget_unlock_button, "Unlock vehicle doors")
            views.setContentDescription(R.id.widget_climate_button, "Start cabin climate")
            return views
        }

        private fun buildBatteryCompactWidget(
            context: Context,
            views: RemoteViews,
            snapshot: WidgetVehicleSnapshot,
            openPendingIntent: PendingIntent
        ): RemoteViews {
            views.setOnClickPendingIntent(R.id.widget_root, openPendingIntent)
            views.setTextViewText(R.id.widget_vehicle_name, snapshot.vehicleName.ifBlank { "BlueBridge" })
            setBatteryViews(views, snapshot, includeBatteryPrefix = false)
            views.setTextViewText(R.id.widget_updated, updatedStatus(snapshot))
            views.setOnClickPendingIntent(R.id.widget_refresh_button, widgetPendingIntent(context, ACTION_REFRESH, 30))
            views.setContentDescription(R.id.widget_refresh_button, "Refresh vehicle status")
            return views
        }

        private fun buildBatteryWideWidget(
            context: Context,
            views: RemoteViews,
            snapshot: WidgetVehicleSnapshot,
            openPendingIntent: PendingIntent
        ): RemoteViews {
            views.setOnClickPendingIntent(R.id.widget_root, openPendingIntent)
            views.setTextViewText(R.id.widget_vehicle_name, snapshot.vehicleName.ifBlank { "BlueBridge" })
            setBatteryViews(views, snapshot, includeBatteryPrefix = true)
            views.setTextViewText(R.id.widget_lock_status, lockStatus(snapshot))
            views.setTextViewText(R.id.widget_charging, snapshot.chargingLabel.ifBlank { snapshot.message })
            views.setTextViewText(R.id.widget_updated, updatedStatus(snapshot))
            views.setOnClickPendingIntent(R.id.widget_refresh_button, widgetPendingIntent(context, ACTION_REFRESH, 31))
            views.setContentDescription(R.id.widget_refresh_button, "Refresh vehicle status")
            return views
        }

        private fun buildActionWidget(
            context: Context,
            views: RemoteViews,
            snapshot: WidgetVehicleSnapshot,
            title: String,
            status: String,
            action: String,
            requestCode: Int,
            description: String
        ): RemoteViews {
            views.setOnClickPendingIntent(R.id.widget_root, widgetPendingIntent(context, action, requestCode))
            views.setTextViewText(R.id.widget_action_title, title)
            views.setTextViewText(R.id.widget_action_status, status)
            views.setTextViewText(R.id.widget_action_button, title)
            views.setOnClickPendingIntent(R.id.widget_action_button, widgetPendingIntent(context, action, requestCode + 100))
            views.setContentDescription(R.id.widget_action_button, description)
            return views
        }

        private fun buildControlsWidget(
            context: Context,
            views: RemoteViews,
            snapshot: WidgetVehicleSnapshot,
            openPendingIntent: PendingIntent
        ): RemoteViews {
            views.setOnClickPendingIntent(R.id.widget_root, openPendingIntent)
            views.setTextViewText(R.id.widget_vehicle_name, snapshot.vehicleName.ifBlank { "BlueBridge" })
            views.setTextViewText(R.id.widget_lock_status, lockStatus(snapshot))
            views.setTextViewText(R.id.widget_updated, updatedStatus(snapshot))
            views.setOnClickPendingIntent(R.id.widget_lock_button, widgetPendingIntent(context, ACTION_LOCK, 40))
            views.setOnClickPendingIntent(R.id.widget_unlock_button, widgetPendingIntent(context, ACTION_UNLOCK, 41))
            views.setContentDescription(R.id.widget_lock_button, "Lock vehicle doors")
            views.setContentDescription(R.id.widget_unlock_button, "Unlock vehicle doors")
            return views
        }

        private fun setBatteryViews(views: RemoteViews, snapshot: WidgetVehicleSnapshot, includeBatteryPrefix: Boolean) {
            val batteryPercent = snapshot.batteryPercent?.coerceIn(0, 100)
            val batteryText = batteryPercent?.let { if (includeBatteryPrefix) "Battery $it%" else "$it%" }
                ?: if (includeBatteryPrefix) "Battery —" else "—"
            views.setTextViewText(R.id.widget_battery, batteryText)
            views.setTextViewText(R.id.widget_range, snapshot.rangeMiles?.let { "Range $it mi" } ?: "Range —")
            views.setProgressBar(R.id.widget_battery_progress, 100, batteryPercent ?: 0, batteryPercent == null)
            views.setContentDescription(
                R.id.widget_battery_progress,
                batteryPercent?.let { "Vehicle battery $it percent" } ?: "Vehicle battery level unavailable"
            )
        }

        private fun lockStatus(snapshot: WidgetVehicleSnapshot): String = when (snapshot.doorsLocked) {
            true -> "Locked"
            false -> "Unlocked"
            null -> "—"
        }

        private fun updatedStatus(snapshot: WidgetVehicleSnapshot): String {
            return if (snapshot.updatedAtMillis > 0L) {
                "${snapshot.message} · ${formatWidgetTime(snapshot.updatedAtMillis)}"
            } else {
                snapshot.message
            }
        }

        private fun widgetPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, VehicleWidgetProvider::class.java).apply {
                this.action = action
                setPackage(context.packageName)
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private suspend fun handleWidgetAction(context: Context, action: String) {
            val preferencesManager = PreferencesManager(context.applicationContext)
            val secureCredentialsManager = SecureCredentialsManager(context.applicationContext)
            val repository = VehicleRepository(preferencesManager, secureCredentialsManager)
            val actionLabel = when (action) {
                ACTION_REFRESH -> "Widget refresh"
                ACTION_LOCK -> "Widget lock"
                ACTION_UNLOCK -> "Widget unlock"
                ACTION_CLIMATE -> "Widget climate"
                else -> "Widget action"
            }

            preferencesManager.setWidgetMessage(
                when (action) {
                    ACTION_REFRESH -> "Refreshing…"
                    ACTION_LOCK -> "Sending lock…"
                    ACTION_UNLOCK -> "Sending unlock…"
                    ACTION_CLIMATE -> "Sending climate…"
                    else -> "Working…"
                }
            )
            refreshAll(context)

            val result = withTimeoutOrNull(25_000L) {
                val vehicle = selectedVehicle(repository, preferencesManager)
                    ?: return@withTimeoutOrNull Result.Error("Open BlueBridge to select a vehicle")

                when (action) {
                    ACTION_REFRESH -> refreshVehicleStatus(repository, preferencesManager, context, vehicle, "Refreshed from widget")
                    ACTION_LOCK -> {
                        val command = repository.lockDoors(
                            vin = vehicle.vin,
                            registrationId = vehicle.regId,
                            generation = vehicle.generation,
                            brandIndicator = vehicle.brandIndicator
                        )
                        if (command is Result.Success) {
                            recordWidgetHistory(preferencesManager, vehicle, actionLabel, "Accepted by Hyundai", true)
                            refreshVehicleStatus(repository, preferencesManager, context, vehicle, "Lock sent from widget")
                        } else command
                    }
                    ACTION_UNLOCK -> {
                        val command = repository.unlockDoors(
                            vin = vehicle.vin,
                            registrationId = vehicle.regId,
                            generation = vehicle.generation,
                            brandIndicator = vehicle.brandIndicator
                        )
                        if (command is Result.Success) {
                            recordWidgetHistory(preferencesManager, vehicle, actionLabel, "Accepted by Hyundai", true)
                            refreshVehicleStatus(repository, preferencesManager, context, vehicle, "Unlock sent from widget")
                        } else command
                    }
                    ACTION_CLIMATE -> {
                        val temp = preferencesManager.defaultClimateTemp.first()
                        val command = repository.startClimate(
                            vin = vehicle.vin,
                            tempF = temp,
                            isEv = vehicle.isEV,
                            registrationId = vehicle.regId,
                            generation = vehicle.generation,
                            brandIndicator = vehicle.brandIndicator
                        )
                        if (command is Result.Success) {
                            recordWidgetHistory(preferencesManager, vehicle, actionLabel, "Cabin ${temp}°F", true)
                            refreshVehicleStatus(repository, preferencesManager, context, vehicle, "Climate sent from widget")
                        } else command
                    }
                    else -> Result.Error("Unsupported widget action")
                }
            } ?: Result.Error("Widget action timed out")

            if (result is Result.Error) {
                recordWidgetHistory(preferencesManager, null, actionLabel, result.message, false)
                preferencesManager.setWidgetMessage(result.message.take(42))
                refreshAll(context)
            }
        }

        private suspend fun selectedVehicle(
            repository: VehicleRepository,
            preferencesManager: PreferencesManager
        ): Vehicle? {
            val vehicles = when (val vehiclesResult = repository.getVehicles()) {
                is Result.Success -> vehiclesResult.data
                else -> emptyList()
            }
            if (vehicles.isEmpty()) return null
            val selectedVin = preferencesManager.selectedVin.first()
            return vehicles.find { it.vin == selectedVin } ?: vehicles.firstOrNull()
        }

        private suspend fun refreshVehicleStatus(
            repository: VehicleRepository,
            preferencesManager: PreferencesManager,
            context: Context,
            vehicle: Vehicle,
            message: String
        ): Result<VehicleStatusData> {
            return when (val result = repository.getVehicleStatus(
                vin = vehicle.vin,
                forceRefresh = true,
                registrationId = vehicle.regId,
                generation = vehicle.generation,
                brandIndicator = vehicle.brandIndicator
            )) {
                is Result.Success -> {
                    preferencesManager.cacheWidgetSnapshot(statusToSnapshot(vehicle, result.data, message))
                    refreshAll(context)
                    result
                }
                is Result.Error -> {
                    preferencesManager.setWidgetMessage(result.message.take(42))
                    refreshAll(context)
                    result
                }
                else -> result
            }
        }

        private fun statusToSnapshot(vehicle: Vehicle, status: VehicleStatusData, message: String): WidgetVehicleSnapshot {
            val evStatus = status.evStatus
            val batteryPercent = evStatus?.batteryStatus?.takeIf { it > 0 }
                ?: status.battery?.batteryLevel?.takeIf { it > 0 }
            val rangeMiles = evStatus?.rangeMiles?.takeIf { it > 0.0 }?.roundToInt()
                ?: status.dte?.value?.takeIf { it > 0.0 }?.roundToInt()
            val chargingLabel = when {
                evStatus?.batteryCharge == true -> evStatus.chargingSpeedLabel.takeIf { it != "Unavailable from vehicle status" }
                    ?.let { "Charging · $it" } ?: "Charging"
                evStatus != null && evStatus.batteryPlugin != 0 -> evStatus.plugStatusLabel
                status.airCtrlOn -> "Climate on"
                else -> "Ready"
            }
            return WidgetVehicleSnapshot(
                vehicleName = vehicle.displayName,
                doorsLocked = status.doorsLocked,
                batteryPercent = batteryPercent,
                rangeMiles = rangeMiles,
                chargingLabel = chargingLabel,
                message = message,
                updatedAtMillis = System.currentTimeMillis()
            )
        }

        private suspend fun recordWidgetHistory(
            preferencesManager: PreferencesManager,
            vehicle: Vehicle?,
            title: String,
            detail: String,
            successful: Boolean
        ) {
            preferencesManager.addCommandHistoryEntry(
                CommandHistoryEntry(
                    timestampMillis = System.currentTimeMillis(),
                    title = title,
                    detail = detail,
                    successful = successful,
                    vehicleName = vehicle?.displayName.orEmpty()
                )
            )
        }

        private fun formatWidgetTime(timestampMillis: Long): String {
            return SimpleDateFormat("h:mm a", Locale.US).format(Date(timestampMillis))
        }
    }
}

enum class WidgetKind(val layoutRes: Int) {
    FULL(R.layout.widget_vehicle),
    BATTERY_COMPACT(R.layout.widget_battery_compact),
    BATTERY_WIDE(R.layout.widget_battery_wide),
    LOCK(R.layout.widget_action_small),
    UNLOCK(R.layout.widget_action_small),
    CLIMATE(R.layout.widget_action_small),
    REFRESH(R.layout.widget_action_small),
    CONTROLS(R.layout.widget_controls_small)
}

class BatteryWidgetProvider : VehicleWidgetProvider() {
    override val widgetKind: WidgetKind = WidgetKind.BATTERY_COMPACT
}

class BatteryWideWidgetProvider : VehicleWidgetProvider() {
    override val widgetKind: WidgetKind = WidgetKind.BATTERY_WIDE
}

class LockWidgetProvider : VehicleWidgetProvider() {
    override val widgetKind: WidgetKind = WidgetKind.LOCK
}

class UnlockWidgetProvider : VehicleWidgetProvider() {
    override val widgetKind: WidgetKind = WidgetKind.UNLOCK
}

class ClimateWidgetProvider : VehicleWidgetProvider() {
    override val widgetKind: WidgetKind = WidgetKind.CLIMATE
}

class RefreshWidgetProvider : VehicleWidgetProvider() {
    override val widgetKind: WidgetKind = WidgetKind.REFRESH
}

class ControlsWidgetProvider : VehicleWidgetProvider() {
    override val widgetKind: WidgetKind = WidgetKind.CONTROLS
}

class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            VehicleWidgetProvider.refreshAll(context)
        }
    }
}
