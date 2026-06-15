package com.bluedeck.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.RemoteViews
import com.bluedeck.MainActivity
import com.bluedeck.R
import com.bluedeck.data.models.CommandHistoryEntry
import com.bluedeck.data.models.Vehicle
import com.bluedeck.data.models.VehicleStatusData
import com.bluedeck.data.models.WidgetVehicleSnapshot
import com.bluedeck.data.models.hasFuelTelemetryFor
import com.bluedeck.data.models.totalRangeMilesFor
import com.bluedeck.data.repository.PreferencesManager
import com.bluedeck.data.repository.Result
import com.bluedeck.data.repository.SecureCredentialsManager
import com.bluedeck.data.repository.VehicleRepository
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
        private const val ACTION_REFRESH = "com.bluedeck.widget.REFRESH"
        private const val ACTION_LOCK = "com.bluedeck.widget.LOCK"
        private const val ACTION_UNLOCK = "com.bluedeck.widget.UNLOCK"
        private const val ACTION_CLIMATE = "com.bluedeck.widget.CLIMATE"

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
                val distanceUnit = preferencesManager.distanceUnit.first()
                val views = buildViews(context, snapshot, kind, distanceUnit)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun buildViews(context: Context, snapshot: WidgetVehicleSnapshot, kind: WidgetKind, distanceUnit: String): RemoteViews {
            val views = RemoteViews(context.packageName, kind.layoutRes)
            applyWidgetTheme(context, views)
            val openPendingIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return when (kind) {
                WidgetKind.FULL -> buildFullWidget(context, views, snapshot, openPendingIntent, distanceUnit)
                WidgetKind.BATTERY_COMPACT -> buildBatteryCompactWidget(context, views, snapshot, openPendingIntent, distanceUnit)
                WidgetKind.BATTERY_WIDE -> buildBatteryWideWidget(context, views, snapshot, openPendingIntent, distanceUnit)
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
            openPendingIntent: PendingIntent,
            distanceUnit: String
        ): RemoteViews {
            views.setOnClickPendingIntent(R.id.widget_root, openPendingIntent)
            views.setTextViewText(R.id.widget_vehicle_name, snapshot.vehicleName.ifBlank { "BlueDeck" })
            views.setTextViewText(R.id.widget_lock_status, verboseLockStatus(snapshot))
            setBatteryViews(views, snapshot, includeBatteryPrefix = false, distanceUnit = distanceUnit)
            views.setTextViewText(R.id.widget_battery_meta, compactUpdatedStatus(snapshot))
            views.setTextViewText(R.id.widget_battery_substatus, verboseBatterySubstatus(snapshot))
            views.setTextViewText(R.id.widget_charging, verboseChargeStatus(snapshot))
            setFullDetailViews(views, snapshot)
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
            openPendingIntent: PendingIntent,
            distanceUnit: String
        ): RemoteViews {
            views.setOnClickPendingIntent(R.id.widget_root, openPendingIntent)
            views.setTextViewText(R.id.widget_vehicle_name, snapshot.vehicleName.ifBlank { "BlueDeck" })
            setBatteryViews(views, snapshot, includeBatteryPrefix = false, distanceUnit = distanceUnit)
            views.setTextViewText(R.id.widget_updated, updatedStatus(snapshot))
            views.setOnClickPendingIntent(R.id.widget_refresh_button, widgetPendingIntent(context, ACTION_REFRESH, 30))
            views.setContentDescription(R.id.widget_refresh_button, "Refresh vehicle status")
            return views
        }

        private fun buildBatteryWideWidget(
            context: Context,
            views: RemoteViews,
            snapshot: WidgetVehicleSnapshot,
            openPendingIntent: PendingIntent,
            distanceUnit: String
        ): RemoteViews {
            views.setOnClickPendingIntent(R.id.widget_root, openPendingIntent)
            views.setTextViewText(R.id.widget_vehicle_name, snapshot.vehicleName.ifBlank { "BlueDeck" })
            setBatteryViews(views, snapshot, includeBatteryPrefix = false, distanceUnit = distanceUnit)
            views.setTextViewText(R.id.widget_lock_status, lockStatus(snapshot))
            views.setTextViewText(R.id.widget_charging, compactChargeStatus(snapshot))
            setCompactDetailViews(views, snapshot)
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
            views.setTextViewText(R.id.widget_vehicle_name, snapshot.vehicleName.ifBlank { "BlueDeck" })
            views.setTextViewText(R.id.widget_lock_status, lockStatus(snapshot))
            views.setTextViewText(R.id.widget_updated, updatedStatus(snapshot))
            views.setOnClickPendingIntent(R.id.widget_lock_button, widgetPendingIntent(context, ACTION_LOCK, 40))
            views.setOnClickPendingIntent(R.id.widget_unlock_button, widgetPendingIntent(context, ACTION_UNLOCK, 41))
            views.setContentDescription(R.id.widget_lock_button, "Lock vehicle doors")
            views.setContentDescription(R.id.widget_unlock_button, "Unlock vehicle doors")
            return views
        }



        private fun normalStatusText(value: String): String {
            val trimmed = value.trim()
            return if (trimmed.equals("Ready", ignoreCase = true)) "All Systems Normal" else trimmed
        }

        private fun isWidgetActionMessage(value: String): Boolean {
            val text = value.trim()
            return text.endsWith("…") ||
                text.contains("from widget", ignoreCase = true) ||
                text.contains("timed out", ignoreCase = true) ||
                text.contains("Open BlueDeck", ignoreCase = true)
        }

        private fun statusText(vararg values: String): String {
            val normalized = values.map(::normalStatusText)
            normalized.firstOrNull { it.isNotBlank() && isWidgetActionMessage(it) }?.let { return it }
            return normalized.firstOrNull { it.isNotBlank() } ?: "All Systems Normal"
        }

        private fun compactUpdatedStatus(snapshot: WidgetVehicleSnapshot): String {
            return if (snapshot.updatedAtMillis > 0L) {
                "Updated ${formatWidgetTime(snapshot.updatedAtMillis)}"
            } else {
                "Updated —"
            }
        }

        private fun compactBatterySubstatus(snapshot: WidgetVehicleSnapshot): String {
            val chargeState = statusText(snapshot.chargingLabel, snapshot.message)
            val detail = snapshot.detailThree.ifBlank { snapshot.detailOne }.ifBlank { lockStatus(snapshot) }
            return listOf(chargeState, detail)
                .filter { it.isNotBlank() && it != "—" }
                .joinToString(" · ")
                .ifBlank { "Status —" }
        }

        private fun setBatteryViews(views: RemoteViews, snapshot: WidgetVehicleSnapshot, includeBatteryPrefix: Boolean, distanceUnit: String) {
            val batteryPercent = snapshot.batteryPercent?.coerceIn(0, 100)
            val batteryText = batteryPercent?.let { if (includeBatteryPrefix) "Battery $it%" else "$it%" }
                ?: if (includeBatteryPrefix) "Battery —" else "—"
            views.setTextViewText(R.id.widget_battery, batteryText)
            views.setTextViewText(R.id.widget_range, snapshot.rangeMiles?.let { "Range ${formatWidgetDistance(it, distanceUnit)}" } ?: "Range —")
            views.setProgressBar(R.id.widget_battery_progress, 100, batteryPercent ?: 0, batteryPercent == null)
            views.setContentDescription(
                R.id.widget_battery_progress,
                batteryPercent?.let { "Vehicle battery $it percent" } ?: "Vehicle battery level unavailable"
            )
        }

        private fun formatWidgetDistance(miles: Int, distanceUnit: String): String {
            return if (distanceUnit.equals("KM", ignoreCase = true)) {
                "${(miles * 1.609344).roundToInt()} km"
            } else {
                "$miles mi"
            }
        }

        private fun setDetailViews(views: RemoteViews, snapshot: WidgetVehicleSnapshot) {
            views.setTextViewText(R.id.widget_detail_one, snapshot.detailOne.ifBlank { "Doors —" })
            views.setTextViewText(R.id.widget_detail_two, snapshot.detailTwo.ifBlank { "Climate —" })
            views.setTextViewText(R.id.widget_detail_three, snapshot.detailThree.ifBlank { "Tires —" })
        }

        private fun setFullDetailViews(views: RemoteViews, snapshot: WidgetVehicleSnapshot) {
            views.setTextViewText(R.id.widget_detail_one, verboseOpeningStatus(snapshot))
            views.setTextViewText(R.id.widget_detail_two, verboseClimateStatus(snapshot))
            views.setTextViewText(R.id.widget_detail_three, verboseAlertStatus(snapshot))
            views.setTextViewText(R.id.widget_detail_four, verboseUpdatedStatus(snapshot))
            views.setTextViewText(R.id.widget_updated, verboseLockStatus(snapshot))
        }

        private fun setCompactDetailViews(views: RemoteViews, snapshot: WidgetVehicleSnapshot) {
            views.setTextViewText(R.id.widget_detail_one, compactOpeningStatus(snapshot))
            views.setTextViewText(R.id.widget_detail_two, compactClimateStatus(snapshot))
            views.setTextViewText(R.id.widget_detail_three, compactAlertStatus(snapshot))
            views.setTextViewText(R.id.widget_detail_four, compactUpdatedStatus(snapshot))
            views.setTextViewText(R.id.widget_updated, lockStatus(snapshot))
        }


        private fun verboseBatterySubstatus(snapshot: WidgetVehicleSnapshot): String {
            val chargeState = verboseChargeStatus(snapshot)
            val lock = verboseLockStatus(snapshot)
            return listOf(chargeState, lock)
                .filter { it.isNotBlank() && it != "—" }
                .joinToString(" · ")
                .ifBlank { "Vehicle Status Unavailable" }
        }

        private fun verboseChargeStatus(snapshot: WidgetVehicleSnapshot): String {
            val value = statusText(snapshot.chargingLabel, snapshot.message)
            return when {
                value.equals("All Systems Normal", ignoreCase = true) -> "All Systems Normal"
                value.equals("Climate on", ignoreCase = true) -> "Climate Running"
                value.contains("charging", ignoreCase = true) -> value.replace(" · ", ": ")
                value.contains("plugged", ignoreCase = true) -> value.replace("AC", "AC Charge").replace("DC fast", "DC Fast Charge")
                value.length > 30 -> value.take(29).trimEnd() + "…"
                else -> value
            }
        }

        private fun verboseOpeningStatus(snapshot: WidgetVehicleSnapshot): String {
            val value = snapshot.detailOne.ifBlank { "Closed" }
            return when {
                value == "Closed" -> "Doors/Hood/Trunk Closed"
                value.startsWith("Open:") -> "Open Items: ${value.removePrefix("Open: ")}"
                value.length > 32 -> value.take(31).trimEnd() + "…"
                else -> value
            }
        }

        private fun verboseClimateStatus(snapshot: WidgetVehicleSnapshot): String {
            return when (val value = snapshot.detailTwo.ifBlank { "Climate off" }) {
                "Climate on" -> "Climate Control On"
                "Climate off" -> "Climate Control Off"
                "Vehicle on" -> "Vehicle Powered On"
                else -> value.take(32)
            }
        }

        private fun verboseAlertStatus(snapshot: WidgetVehicleSnapshot): String {
            val value = snapshot.detailThree.ifBlank { "No alerts" }
            return when {
                value == "No alerts" -> "No Vehicle Alerts"
                value == "Unplugged" -> "Charge Cable Unplugged"
                value.equals("AC plugged in", ignoreCase = true) -> "AC Charge Plug Connected"
                value.equals("DC fast plugged in", ignoreCase = true) -> "DC Fast Charge Plug Connected"
                value.contains("tire", ignoreCase = true) -> "Tire Pressure Alert"
                value.contains("key battery", ignoreCase = true) -> "Key Battery Low"
                value.contains("plugged", ignoreCase = true) -> value
                value.length > 32 -> value.take(31).trimEnd() + "…"
                else -> value
            }
        }

        private fun verboseUpdatedStatus(snapshot: WidgetVehicleSnapshot): String {
            return if (snapshot.updatedAtMillis > 0L) {
                "Last Updated ${formatWidgetTime(snapshot.updatedAtMillis)}"
            } else {
                "Last Updated —"
            }
        }

        private fun verboseLockStatus(snapshot: WidgetVehicleSnapshot): String = when (snapshot.doorsLocked) {
            true -> "Doors Locked"
            false -> "Doors Unlocked"
            null -> "Lock Status —"
        }

        private fun compactChargeStatus(snapshot: WidgetVehicleSnapshot): String {
            val value = statusText(snapshot.chargingLabel, snapshot.message)
            return when {
                value.equals("All Systems Normal", ignoreCase = true) -> "All Systems Normal"
                value.contains("charging", ignoreCase = true) -> value.replace("Charging · ", "Chg ").replace("Charging", "Charging")
                value.contains("climate", ignoreCase = true) -> "Climate"
                value.length > 28 -> value.take(27).trimEnd() + "…"
                else -> value
            }
        }

        private fun compactOpeningStatus(snapshot: WidgetVehicleSnapshot): String {
            val value = snapshot.detailOne.ifBlank { "Closed" }
            return when {
                value == "Closed" -> "Closed"
                value.startsWith("Open:") -> value.replace("Open: ", "Open ").replace(", ", "/").take(18)
                value.length > 18 -> value.take(17).trimEnd() + "…"
                else -> value
            }
        }

        private fun compactClimateStatus(snapshot: WidgetVehicleSnapshot): String {
            return when (snapshot.detailTwo.ifBlank { "Climate off" }) {
                "Climate on" -> "Climate On"
                "Climate off" -> "Climate Off"
                "Vehicle on" -> "Vehicle On"
                else -> snapshot.detailTwo.take(18)
            }
        }

        private fun compactAlertStatus(snapshot: WidgetVehicleSnapshot): String {
            val value = snapshot.detailThree.ifBlank { "No alerts" }
            return when {
                value == "No alerts" -> "No Alerts"
                value == "Unplugged" -> "Unplugged"
                value.contains("plugged", ignoreCase = true) -> value.replace(" fast", "")
                value.length > 18 -> value.take(17).trimEnd() + "…"
                else -> value
            }
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


        private fun acceptedByBrand(vehicle: Vehicle): String = when (vehicle.brandIndicator.trim().uppercase()) {
            "K" -> "Accepted by Kia"
            "G" -> "Accepted by Genesis"
            else -> "Accepted by Hyundai"
        }

        private suspend fun handleWidgetAction(context: Context, action: String) {
            val preferencesManager = PreferencesManager(context.applicationContext)
            val repository = VehicleRepository(preferencesManager, SecureCredentialsManager(context.applicationContext))
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
                    ?: return@withTimeoutOrNull Result.Error("Open BlueDeck to select a vehicle")

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
                            recordWidgetHistory(preferencesManager, vehicle, actionLabel, acceptedByBrand(vehicle), true)
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
                            recordWidgetHistory(preferencesManager, vehicle, actionLabel, acceptedByBrand(vehicle), true)
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
            val selectedVin = preferencesManager.selectedVin.first().orEmpty()
            val snapshot = preferencesManager.widgetVehicleSnapshot.first()

            val vehicles = when (val vehiclesResult = repository.getVehicles()) {
                is Result.Success -> vehiclesResult.data
                else -> emptyList()
            }
            if (vehicles.isNotEmpty()) {
                return vehicles.find { it.vin == selectedVin }
                    ?: vehicles.find { it.vin == snapshot.vehicleVin }
                    ?: vehicles.firstOrNull()
            }

            val cachedVin = snapshot.vehicleVin.ifBlank { selectedVin }
            if (cachedVin.isBlank()) return null

            val cachedRegistrationId = snapshot.registrationId.ifBlank { snapshot.vehicleId }
            return Vehicle(
                vin = cachedVin,
                vehicleIdentifier = snapshot.vehicleId,
                enrollmentId = snapshot.vehicleId,
                regId = cachedRegistrationId,
                generation = snapshot.generation.ifBlank { "3" },
                nickname = snapshot.vehicleName.takeIf { it.isNotBlank() && it != "BlueDeck" }.orEmpty(),
                brandIndicator = snapshot.brandIndicator.ifBlank { "H" },
                modelCode = snapshot.modelCode
            )
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
            val rangeMiles = status.totalRangeMilesFor(vehicle).takeIf { it > 0.0 }?.roundToInt()
            val chargingLabel = when {
                evStatus?.batteryCharge == true -> evStatus.chargingSpeedLabel.takeIf { it != "Unavailable from vehicle status" }
                    ?.let { "Charging · $it" } ?: "Charging"
                evStatus != null && evStatus.batteryPlugin != 0 -> evStatus.plugStatusLabel
                status.hasFuelTelemetryFor(vehicle) -> status.normalizedFuelLevelPercent?.let { "Fuel $it%" } ?: "Fuel range"
                status.airCtrlOn -> "Climate on"
                else -> "All Systems Normal"
            }
            return WidgetVehicleSnapshot(
                vehicleName = vehicle.displayName,
                vehicleVin = vehicle.vin,
                vehicleId = vehicle.vehicleIdentifier.ifBlank { vehicle.enrollmentId.ifBlank { vehicle.regId } },
                registrationId = vehicle.regId.ifBlank { vehicle.enrollmentId.ifBlank { vehicle.vehicleIdentifier } },
                generation = vehicle.generation,
                brandIndicator = vehicle.brandIndicator,
                modelCode = vehicle.modelCode,
                doorsLocked = status.doorsLocked,
                batteryPercent = batteryPercent,
                rangeMiles = rangeMiles,
                chargingLabel = chargingLabel,
                message = message,
                detailOne = openingsLabel(status),
                detailTwo = climateLabel(status),
                detailThree = tireOrPlugLabel(status),
                updatedAtMillis = System.currentTimeMillis()
            )
        }

        private fun openingsLabel(status: VehicleStatusData): String {
            val openItems = buildList {
                if (status.doorOpenStatus?.anyOpen == true) add("Door")
                if (status.trunkOpenStatus) add("Trunk")
                if (status.hoodOpenStatus) add("Hood")
                if (status.windowOpenStatus?.anyOpen == true) add("Window")
            }
            return if (openItems.isEmpty()) "Closed" else "Open: ${openItems.joinToString(", ")}"
        }

        private fun climateLabel(status: VehicleStatusData): String {
            return when {
                status.airCtrlOn -> "Climate on"
                status.ignitionOn || status.engineStatus -> "Vehicle on"
                else -> "Climate off"
            }
        }

        private fun tireOrPlugLabel(status: VehicleStatusData): String {
            return when {
                status.tirePressureLamp?.anyLow == true -> "Tire alert"
                status.evStatus?.batteryPlugin != null && status.evStatus.batteryPlugin != 0 -> status.evStatus.plugStatusLabel
                status.evStatus != null -> "Unplugged"
                status.smartKeyBatteryWarning -> "Key battery low"
                else -> "No alerts"
            }
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

        private fun applyWidgetTheme(context: Context, views: RemoteViews) {
            val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val background = context.getColor(
                if (night) R.color.widget_background_dark else R.color.widget_background_light
            )
            val primaryText = context.getColor(
                if (night) R.color.widget_text_primary_dark else R.color.widget_text_primary_light
            )
            views.setInt(R.id.widget_root, "setBackgroundColor", background)
            listOf(
                R.id.widget_vehicle_name,
                R.id.widget_battery,
                R.id.widget_range,
                R.id.widget_action_title,
                R.id.widget_action_status
            ).forEach { id ->
                runCatching { views.setTextColor(id, primaryText) }
            }
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
