package com.bluedeck.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bluedeck.data.api.Region
import com.bluedeck.data.models.CommandHistoryEntry
import com.bluedeck.data.models.Vehicle
import com.bluedeck.data.repository.PreferencesManager
import com.bluedeck.data.repository.Result
import com.bluedeck.data.repository.SecureCredentialsManager
import com.bluedeck.data.repository.VehicleRepository
import com.bluedeck.widget.VehicleWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class WalkAwayLockAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                executeLock(appContext)
            } finally {
                WalkAwayLockScheduler.clearPendingSuspending(appContext)
                WalkAwayBluetoothMonitorService.stop(appContext)
                pendingResult.finish()
            }
        }
    }

    private suspend fun executeLock(context: Context) {
        val preferencesManager = PreferencesManager(context)
        if (!preferencesManager.walkAwayLockEnabled.first()) {
            Log.i(TAG, "Walk-away lock disabled; skipping")
            return
        }

        val deviceName = preferencesManager.walkAwayBluetoothName.first() ?: "vehicle"
        val region = runCatching {
            Region.valueOf(preferencesManager.region.first())
        }.getOrDefault(Region.US_HYUNDAI)
        if (region.isCanada && preferencesManager.effectiveServicePin().isBlank()) {
            val message = "Canada Bluelink PIN is required for walk-away lock. Save your PIN in Settings."
            preferencesManager.addCommandHistoryEntry(
                CommandHistoryEntry(
                    timestampMillis = System.currentTimeMillis(),
                    title = "Walk-away lock",
                    detail = message,
                    successful = false,
                    vehicleName = deviceName
                )
            )
            preferencesManager.setWidgetMessage(message.take(42))
            VehicleWidgetProvider.refreshAll(context)
            WalkAwayNotifications.notifyEvent(context, "Walk-away lock failed", message)
            Log.w(TAG, message)
            return
        }

        preferencesManager.setWidgetMessage("Walk-away locking…")
        VehicleWidgetProvider.refreshAll(context)

        val result = withTimeoutOrNull(30_000L) {
            val repository = VehicleRepository(
                preferencesManager,
                SecureCredentialsManager(context),
                com.bluedeck.data.demo.DemoVehicleStore()
            )
            val vehicle = selectedVehicle(repository, preferencesManager)
                ?: return@withTimeoutOrNull Result.Error(
                    "Open BlueDeck and select a vehicle first"
                )

            when (
                val command = repository.lockDoors(
                    vin = vehicle.vin,
                    registrationId = vehicle.regId,
                    generation = vehicle.generation,
                    brandIndicator = vehicle.brandIndicator
                )
            ) {
                is Result.Success -> {
                    preferencesManager.addCommandHistoryEntry(
                        CommandHistoryEntry(
                            timestampMillis = System.currentTimeMillis(),
                            title = "Walk-away lock",
                            detail = acceptedByBrand(vehicle),
                            successful = true,
                            vehicleName = vehicle.nickname.ifBlank { deviceName }
                        )
                    )
                    preferencesManager.setWidgetMessage("Locked after walk-away")
                    VehicleWidgetProvider.refreshAll(context)
                    WalkAwayNotifications.notifyEvent(
                        context,
                        "Walk-away lock sent",
                        "Lock command accepted for ${vehicle.nickname.ifBlank { deviceName }}."
                    )
                    Log.i(TAG, "Walk-away lock succeeded for ${vehicle.vin}")
                    command
                }
                is Result.Error -> {
                    preferencesManager.addCommandHistoryEntry(
                        CommandHistoryEntry(
                            timestampMillis = System.currentTimeMillis(),
                            title = "Walk-away lock",
                            detail = command.message,
                            successful = false,
                            vehicleName = vehicle.nickname.ifBlank { deviceName }
                        )
                    )
                    preferencesManager.setWidgetMessage(command.message.take(42))
                    VehicleWidgetProvider.refreshAll(context)
                    WalkAwayNotifications.notifyEvent(
                        context,
                        "Walk-away lock failed",
                        command.message
                    )
                    Log.w(TAG, "Walk-away lock failed: ${command.message}")
                    command
                }
            }
        } ?: Result.Error("Walk-away lock timed out")

        if (result is Result.Error && result.message == "Walk-away lock timed out") {
            preferencesManager.addCommandHistoryEntry(
                CommandHistoryEntry(
                    timestampMillis = System.currentTimeMillis(),
                    title = "Walk-away lock",
                    detail = result.message,
                    successful = false,
                    vehicleName = deviceName
                )
            )
            preferencesManager.setWidgetMessage(result.message.take(42))
            VehicleWidgetProvider.refreshAll(context)
            WalkAwayNotifications.notifyEvent(context, "Walk-away lock failed", result.message)
            Log.w(TAG, result.message)
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

    private fun acceptedByBrand(vehicle: Vehicle): String = when (vehicle.brandIndicator.trim().uppercase()) {
        "K" -> "Accepted by Kia"
        "G" -> "Accepted by Genesis"
        else -> "Accepted by Hyundai"
    }

    companion object {
        private const val TAG = "WalkAwayLock"
    }
}
