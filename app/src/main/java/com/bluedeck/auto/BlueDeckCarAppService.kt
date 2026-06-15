package com.blueandroid.auto

import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.blueandroid.data.models.Vehicle
import com.blueandroid.data.models.VehicleStatusData
import com.blueandroid.data.repository.PreferencesManager
import com.blueandroid.data.repository.Result
import com.blueandroid.data.repository.VehicleRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Read-only Android Auto companion for BlueAndroid.
 *
 * This intentionally exposes status only. Remote commands such as lock, unlock,
 * climate, charge start/stop, and seat controls are not available on Android Auto.
 */
@AndroidEntryPoint
class BlueAndroidCarAppService : CarAppService() {
    @Inject lateinit var vehicleRepository: VehicleRepository
    @Inject lateinit var preferencesManager: PreferencesManager

    override fun createHostValidator(): HostValidator {
        // Private/sideload testing. Use a stricter validator for a public release.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return object : Session() {
            override fun onCreateScreen(intent: android.content.Intent): Screen {
                return BlueAndroidAutoScreen(
                    carContext = carContext,
                    vehicleRepository = vehicleRepository,
                    preferencesManager = preferencesManager
                )
            }
        }
    }
}

private class AutoSnapshot(
    val vehicle: Vehicle? = null,
    val status: VehicleStatusData? = null,
    val lastUpdated: Long = 0L,
    val error: String? = null,
    val loading: Boolean = false
)

private open class BlueAndroidAutoScreen(
    carContext: CarContext,
    protected val vehicleRepository: VehicleRepository,
    protected val preferencesManager: PreferencesManager
) : Screen(carContext) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    protected var snapshot: AutoSnapshot = AutoSnapshot(loading = true)

    init {
        refreshStatus()
    }


    override fun onGetTemplate(): Template {
        val snap = snapshot
        val list = ItemList.Builder()
            .addItem(refreshRow())

        if (snap.loading) {
            list.addItem(Row.Builder().setTitle("Loading vehicle status…").build())
        }

        snap.error?.let { error ->
            list.addItem(
                Row.Builder()
                    .setTitle("Status unavailable")
                    .addText(error)
                    .build()
            )
        }

        val vehicle = snap.vehicle
        val status = snap.status
        val distanceUnit = runCatching { kotlinx.coroutines.runBlocking { preferencesManager.distanceUnit.first() } }.getOrDefault("MI")
        if (vehicle != null && status != null) {
            val ev = status.evStatus
            list.addItem(
                Row.Builder()
                    .setTitle(vehicle.displayName.ifBlank { "BlueAndroid" })
                    .addText("VIN ${vehicle.vin.takeLast(6)} · ${status.lockLabel()}")
                    .addText("Updated ${snap.lastUpdated.formatAutoTime()}")
                    .build()
            )

            list.addItem(
                Row.Builder()
                    .setTitle("Battery & Range")
                    .addText("HV ${ev?.batteryStatus ?: 0}% · ${ev?.rangeMiles?.formatAutoDistance(distanceUnit) ?: "--"}")
                    .addText("12V ${status.battery?.batteryLevel?.let { "$it%" } ?: "--"}")
                    .build()
            )

            list.addItem(
                Row.Builder()
                    .setTitle("Charging")
                    .addText(ev?.chargingMethodLabel ?: "Not available")
                    .addText("Plug ${ev?.plugStatusLabel ?: "--"} · Speed ${ev?.chargingSpeedLabel ?: "--"}")
                    .build()
            )

            list.addItem(
                Row.Builder()
                    .setTitle("Climate")
                    .addText(if (status.airCtrlOn) "Climate on" else "Climate off")
                    .addText("Cabin setting ${status.airTemp?.value ?: "--"}")
                    .build()
            )

            list.addItem(
                Row.Builder()
                    .setTitle("Tires")
                    .addText(status.tirePressureStatus?.autoTireSummary() ?: "No tire pressure data")
                    .addText(if (status.tirePressureLamp?.anyLow == true) "Warning lamp active" else "No tire warnings")
                    .build()
            )

            list.addItem(
                Row.Builder()
                    .setTitle("Diagnostics")
                    .addText("Odometer ${status.totalMileage.formatAutoDistance(distanceUnit)}")
                    .addText("Preconditioning ${if (ev?.batteryPrecondition == true) "active" else "inactive"}")
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setTitle("BlueAndroid")
            .setSingleList(list.build())
            .build()
    }

    private fun refreshRow(): Row = Row.Builder()
        .setTitle("Refresh status")
        .addText("Read-only Android Auto view")
        .setOnClickListener { refreshStatus(forceFromServer = true) }
        .build()

    protected fun refreshStatus(forceFromServer: Boolean = false) {
        snapshot = AutoSnapshot(
            vehicle = snapshot.vehicle,
            status = snapshot.status,
            lastUpdated = snapshot.lastUpdated,
            loading = true,
            error = null
        )
        invalidate()
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                val vehiclesResult = vehicleRepository.getVehicles()
                val vehicles = when (vehiclesResult) {
                    is Result.Success -> vehiclesResult.data
                    is Result.Error -> throw IllegalStateException(vehiclesResult.message)
                    else -> emptyList()
                }
                val selectedVin = preferencesManager.selectedVin.first()
                val vehicle = vehicles.firstOrNull { it.vin == selectedVin } ?: vehicles.firstOrNull()
                    ?: throw IllegalStateException("No vehicle found. Open BlueAndroid on the phone first.")
                val statusResult = vehicleRepository.getVehicleStatus(
                    vin = vehicle.vin,
                    forceRefresh = forceFromServer,
                    registrationId = vehicle.regId,
                    generation = vehicle.generation,
                    brandIndicator = vehicle.brandIndicator
                )
                when (statusResult) {
                    is Result.Success -> AutoSnapshot(
                        vehicle = vehicle,
                        status = statusResult.data,
                        lastUpdated = System.currentTimeMillis(),
                        loading = false
                    )
                    is Result.Error -> AutoSnapshot(error = statusResult.message, loading = false)
                    else -> AutoSnapshot(loading = false)
                }
            }.getOrElse { throwable ->
                AutoSnapshot(error = throwable.message ?: throwable.javaClass.simpleName, loading = false)
            }
            scope.launch(Dispatchers.Main) {
                snapshot = result
                invalidate()
            }
        }
    }
}

private fun VehicleStatusData.lockLabel(): String = if (doorsLocked) "Locked" else "Unlocked"

private fun Double.formatAutoDistance(distanceUnit: String): String {
    val value = if (distanceUnit.equals("KM", ignoreCase = true)) this * 1.609344 else this
    val unit = if (distanceUnit.equals("KM", ignoreCase = true)) "km" else "mi"
    return String.format(Locale.US, "%.0f %s", value, unit)
}

private fun Int.formatAutoDistance(distanceUnit: String): String = this.toDouble().formatAutoDistance(distanceUnit)

private fun Long.formatAutoTime(): String {
    if (this <= 0L) return "--"
    return SimpleDateFormat("h:mm a", Locale.US).format(Date(this))
}

private fun com.blueandroid.data.models.TirePressureStatus.autoTireSummary(): String {
    return "FL ${frontLeftPsi} · FR ${frontRightPsi} · RL ${rearLeftPsi} · RR ${rearRightPsi} PSI"
}
