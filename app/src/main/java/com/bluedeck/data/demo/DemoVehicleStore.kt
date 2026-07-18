package com.bluedeck.data.demo

import com.bluedeck.data.models.AirTemp
import com.bluedeck.data.models.ChargeScheduleWindow
import com.bluedeck.data.models.ChargeTarget
import com.bluedeck.data.models.Coordinate
import com.bluedeck.data.models.DriveDistance
import com.bluedeck.data.models.EVStatus
import com.bluedeck.data.models.MockVehicleProfile
import com.bluedeck.data.models.OffPeakPowerInfo
import com.bluedeck.data.models.OffPeakPowerTime
import com.bluedeck.data.models.RangeByFuel
import com.bluedeck.data.models.RangeValue
import com.bluedeck.data.models.ReservChargeInfos
import com.bluedeck.data.models.ScheduleEndpoint
import com.bluedeck.data.models.ScheduleTime
import com.bluedeck.data.models.SeatHeaterVentInfo
import com.bluedeck.data.models.Speed
import com.bluedeck.data.models.Vehicle
import com.bluedeck.data.models.VehicleLocation
import com.bluedeck.data.models.VehicleStatusData
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory fake fleet for Google Play demo mode. Never talks to Blue Link.
 *
 * Process-wide shared state so Hilt, widgets, and receivers see the same fleet.
 */
@Singleton
class DemoVehicleStore @Inject constructor() {
    companion object {
        const val DEMO_USERNAME = "demo@bluedeck.app"
        const val DEMO_PIN = "0000"
        private const val COMMAND_DELAY_MS = 550L

        private val mutex = Mutex()
        private val vehicles = linkedMapOf<String, Vehicle>()
        private val statuses = linkedMapOf<String, VehicleStatusData>()

        private val DEMO_LOCATION = VehicleLocation(
            coord = Coordinate(lat = 33.7490, lon = -84.3880, alt = 320.0),
            speed = Speed(unit = 1, value = 0.0),
            heading = 90
        )

        fun resetShared() {
            vehicles.clear()
            statuses.clear()
            listOf(MockVehicleProfile.IONIQ_5_UNPLUGGED, MockVehicleProfile.EV6_UNPLUGGED).forEach { profile ->
                val vehicle = profile.toVehicle(null)
                vehicles[vehicle.vin] = vehicle
                statuses[vehicle.vin] = (profile.toStatus() ?: VehicleStatusData()).copy(
                    location = DEMO_LOCATION
                )
            }
        }

        fun clearShared() {
            vehicles.clear()
            statuses.clear()
        }

        private fun ensureInitializedLocked() {
            if (vehicles.isEmpty()) resetShared()
        }

        private fun normalizeHhmm(raw: String): String {
            val digits = raw.filter { it.isDigit() }
            return when {
                digits.length >= 4 -> digits.takeLast(4)
                digits.length == 3 -> digits.padStart(4, '0')
                digits.length == 2 -> digits.padEnd(4, '0')
                else -> "0000"
            }
        }

        private fun scaledRange(ev: EVStatus, soc: Int): List<DriveDistance> {
            val baseRange = ev.drvDistance.firstOrNull()?.rangeByFuel?.evModeRange?.value
                ?: ev.drvDistance.firstOrNull()?.rangeByFuel?.totalAvailableRange?.value
                ?: 180.0
            val previousSoc = ev.batteryStatus.coerceAtLeast(1)
            val miles = (baseRange * soc / previousSoc).coerceAtLeast(1.0)
            return listOf(
                DriveDistance(
                    rangeByFuel = RangeByFuel(
                        totalAvailableRange = RangeValue(value = miles, unit = 1),
                        evModeRange = RangeValue(value = miles, unit = 1)
                    )
                )
            )
        }
    }

    fun reset() = resetShared()

    fun clear() = clearShared()

    fun isInitialized(): Boolean = vehicles.isNotEmpty()

    suspend fun getVehicles(): List<Vehicle> = mutex.withLock {
        ensureInitializedLocked()
        vehicles.values.toList()
    }

    suspend fun getStatus(vin: String): VehicleStatusData = mutex.withLock {
        ensureInitializedLocked()
        statuses[vin] ?: statuses.values.first()
    }

    suspend fun getLocation(vin: String): VehicleLocation = mutex.withLock {
        ensureInitializedLocked()
        statuses[vin]?.location ?: DEMO_LOCATION
    }

    suspend fun lockDoors(vin: String) = mutate(vin) { status ->
        status.copy(doorLock = true, doorLockStatus = "LOCKED")
    }

    suspend fun unlockDoors(vin: String) = mutate(vin) { status ->
        status.copy(doorLock = false, doorLockStatus = "UNLOCKED")
    }

    suspend fun startClimate(
        vin: String,
        tempF: String,
        defrost: Boolean,
        heatedSteering: Boolean,
        driverSeatHeat: Int,
        passengerSeatHeat: Int,
        rearLeftSeatHeat: Int,
        rearRightSeatHeat: Int,
        isEv: Boolean
    ) = mutate(vin) { status ->
        status.copy(
            airCtrlOn = true,
            defrost = defrost,
            airTemp = AirTemp(value = tempF, unit = 1),
            steerWheelHeat = if (heatedSteering) 1 else 0,
            engineStatus = !isEv,
            ignitionStatus = if (isEv) "OFF" else "ON",
            seatHeaterVentInfo = SeatHeaterVentInfo(
                driverSeatHeatState = driverSeatHeat,
                passengerSeatHeatState = passengerSeatHeat,
                rearLeftSeatHeatState = rearLeftSeatHeat,
                rearRightSeatHeatState = rearRightSeatHeat
            )
        )
    }

    suspend fun stopClimate(vin: String) = mutate(vin) { status ->
        status.copy(
            airCtrlOn = false,
            defrost = false,
            engineStatus = false,
            ignitionStatus = "OFF",
            steerWheelHeat = 0,
            seatHeaterVentInfo = null
        )
    }

    suspend fun startCharging(vin: String) = mutate(vin) { status ->
        val ev = status.evStatus ?: return@mutate status
        val soc = (ev.batteryStatus + 1).coerceAtMost(100)
        status.copy(
            evStatus = ev.copy(
                batteryCharge = true,
                batteryPlugin = 2,
                batteryStatus = soc,
                chargingPowerKw = 7.4,
                batteryPrecondition = soc <= 20,
                drvDistance = scaledRange(ev, soc)
            )
        )
    }

    suspend fun stopCharging(vin: String) = mutate(vin) { status ->
        val ev = status.evStatus ?: return@mutate status
        status.copy(
            evStatus = ev.copy(
                batteryCharge = false,
                batteryPlugin = 0,
                chargingPowerKw = null
            )
        )
    }

    suspend fun setChargeTargets(vin: String, acTarget: Int, dcTarget: Int) = mutate(vin) { status ->
        val ev = status.evStatus ?: return@mutate status
        val reserv = ev.reservChargeInfos ?: ReservChargeInfos()
        status.copy(
            evStatus = ev.copy(
                reservChargeInfos = reserv.copy(
                    targets = listOf(
                        ChargeTarget(plugType = 1, targetSoc = acTarget),
                        ChargeTarget(plugType = 0, targetSoc = dcTarget)
                    )
                )
            )
        )
    }

    suspend fun setChargingSchedule(
        vin: String,
        chargeStartTime: String,
        chargeEndTime: String,
        offPeakStartTime: String,
        offPeakEndTime: String,
        offPeakOnly: Boolean
    ) = mutate(vin) { status ->
        val ev = status.evStatus ?: return@mutate status
        val reserv = ev.reservChargeInfos ?: ReservChargeInfos()
        status.copy(
            evStatus = ev.copy(
                reservChargeInfos = reserv.copy(
                    chargeWindow = ChargeScheduleWindow(
                        start = ScheduleEndpoint(time = ScheduleTime(time = normalizeHhmm(chargeStartTime))),
                        end = ScheduleEndpoint(time = ScheduleTime(time = normalizeHhmm(chargeEndTime)))
                    ),
                    offPeakPowerInfo = OffPeakPowerInfo(
                        offPeakPowerFlag = if (offPeakOnly) 1 else 0,
                        offPeakPowerTime1 = OffPeakPowerTime(
                            startTime = ScheduleTime(time = normalizeHhmm(offPeakStartTime)),
                            endTime = ScheduleTime(time = normalizeHhmm(offPeakEndTime))
                        )
                    )
                )
            )
        )
    }

    suspend fun hornAndLights(vin: String) {
        delay(COMMAND_DELAY_MS)
        mutex.withLock { ensureInitializedLocked() }
    }

    suspend fun flashLights(vin: String) {
        delay(COMMAND_DELAY_MS)
        mutex.withLock { ensureInitializedLocked() }
    }

    private suspend fun mutate(vin: String, transform: (VehicleStatusData) -> VehicleStatusData) {
        delay(COMMAND_DELAY_MS)
        mutex.withLock {
            ensureInitializedLocked()
            val key = if (statuses.containsKey(vin)) vin else statuses.keys.first()
            statuses[key] = transform(statuses.getValue(key))
        }
    }
}
