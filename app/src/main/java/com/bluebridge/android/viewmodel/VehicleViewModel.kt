package com.bluebridge.android.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluebridge.android.data.models.*
import com.bluebridge.android.data.repository.PreferencesManager
import com.bluebridge.android.data.repository.Result
import com.bluebridge.android.data.repository.VehicleRepository
import com.bluebridge.android.widget.VehicleWidgetProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import javax.inject.Inject

enum class CommandStatus { IDLE, LOADING, ACCEPTED, REFRESHING, SUCCESS, ERROR }

data class CommandState(
    val status: CommandStatus = CommandStatus.IDLE,
    val message: String = "",
    val detail: String = ""
)

data class RemoteStartSettings(
    val tempF: String = "72",
    val hvacOn: Boolean = true,
    val defrost: Boolean = false,
    val heatedSteering: Boolean = false,
    val driverSeatHeat: Int = 2,
    val passengerSeatHeat: Int = 2,
    val rearLeftSeatHeat: Int = 2,
    val rearRightSeatHeat: Int = 2,
    val durationMinutes: Int = 10
)

data class FeatureNotice(
    val title: String,
    val message: String
)

@HiltViewModel
class VehicleViewModel @Inject constructor(
    private val repository: VehicleRepository,
    private val preferencesManager: PreferencesManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    // ─── Vehicle list ──────────────────────────────────────────────────────────
    private val _vehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val vehicles: StateFlow<List<Vehicle>> = _vehicles.asStateFlow()

    private val _selectedVehicle = MutableStateFlow<Vehicle?>(null)

    val selectedVehicle: StateFlow<Vehicle?> = _selectedVehicle.asStateFlow()

    // ─── Status ────────────────────────────────────────────────────────────────
    private val _vehicleStatus = MutableStateFlow<VehicleStatusData?>(null)
    val vehicleStatus: StateFlow<VehicleStatusData?> = _vehicleStatus.asStateFlow()

    private val _isStatusLoading = MutableStateFlow(false)
    val isStatusLoading: StateFlow<Boolean> = _isStatusLoading.asStateFlow()

    private val _statusError = MutableStateFlow<String?>(null)
    val statusError: StateFlow<String?> = _statusError.asStateFlow()

    val lastStatusRefresh = preferencesManager.lastStatusRefresh
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    // ─── Commands ──────────────────────────────────────────────────────────────
    private val _commandState = MutableStateFlow(CommandState())
    val commandState: StateFlow<CommandState> = _commandState.asStateFlow()

    val commandHistory = preferencesManager.commandHistory
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ─── Remote start settings ─────────────────────────────────────────────────
    private val _remoteStartSettings = MutableStateFlow(RemoteStartSettings())
    val remoteStartSettings: StateFlow<RemoteStartSettings> = _remoteStartSettings.asStateFlow()

    // ─── Settings ──────────────────────────────────────────────────────────────
    val temperatureUnit = preferencesManager.temperatureUnit
        .stateIn(viewModelScope, SharingStarted.Eagerly, "F")

    val distanceUnit = preferencesManager.distanceUnit
        .stateIn(viewModelScope, SharingStarted.Eagerly, "MI")

    val timeZoneMode = preferencesManager.timeZoneMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, "DEVICE")

    val timeFormat = preferencesManager.timeFormat
        .stateIn(viewModelScope, SharingStarted.Eagerly, "12_HOUR")

    val customDashboardImageUri = preferencesManager.customDashboardImageUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val valetModeEnabled = preferencesManager.valetModeEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _driverProfiles = MutableStateFlow(
        listOf(
            DriverProfile("primary", "Primary Driver", "Owner", isActive = true),
            DriverProfile("guest", "Guest Driver", "Guest"),
            DriverProfile("valet", "Valet", "Restricted")
        )
    )
    val driverProfiles: StateFlow<List<DriverProfile>> = _driverProfiles.asStateFlow()

    private val _surroundViewSnapshot = MutableStateFlow<SurroundViewSnapshot?>(null)
    val surroundViewSnapshot: StateFlow<SurroundViewSnapshot?> = _surroundViewSnapshot.asStateFlow()

    private val _featureNotice = MutableStateFlow<FeatureNotice?>(null)
    val featureNotice: StateFlow<FeatureNotice?> = _featureNotice.asStateFlow()

    init {
        viewModelScope.launch {
            restoreCachedSelectedVehicle()
            loadVehicles()
            val defaultTemp = preferencesManager.defaultClimateTemp.first()
            _remoteStartSettings.value = _remoteStartSettings.value.copy(tempF = defaultTemp)
            val activeProfile = preferencesManager.activeDriverProfileId.first()
            val profilePhotos = preferencesManager.driverProfilePhotoUris.first()
            _driverProfiles.value = _driverProfiles.value.map {
                it.copy(
                    isActive = it.id == activeProfile,
                    photoUri = profilePhotos[it.id]
                )
            }
        }
    }

    // ─── Load vehicles ─────────────────────────────────────────────────────────
    private suspend fun restoreCachedSelectedVehicle() {
        if (_selectedVehicle.value != null) return
        val snapshot = preferencesManager.widgetVehicleSnapshot.first()
        if (snapshot.vehicleVin.isBlank()) return
        val cachedVehicle = Vehicle(
            vin = snapshot.vehicleVin,
            vehicleIdentifier = snapshot.vehicleId.ifBlank { snapshot.registrationId },
            enrollmentId = snapshot.vehicleId.ifBlank { snapshot.registrationId },
            regId = snapshot.registrationId.ifBlank { snapshot.vehicleId },
            generation = snapshot.generation,
            nickname = snapshot.vehicleName.takeIf { it != "BlueBridge" }.orEmpty(),
            modelCode = snapshot.modelCode,
            modelName = snapshot.vehicleName.takeIf { it != "BlueBridge" }.orEmpty(),
            brandIndicator = snapshot.brandIndicator
        )
        _selectedVehicle.value = cachedVehicle
    }

    fun loadVehicles() {
        viewModelScope.launch {
            when (val result = repository.getVehicles()) {
                is Result.Success -> {
                    _vehicles.value = result.data
                    val savedVin = preferencesManager.selectedVin.first()
                    val vehicle = result.data.find { it.vin == savedVin } ?: result.data.firstOrNull()
                    vehicle?.let { selectVehicle(it) }
                }
                is Result.Error -> {
                    _statusError.value = result.message
                    if (result.message.contains("Session expired", ignoreCase = true)) {
                        _vehicleStatus.value = null
                        _selectedVehicle.value = null
                    }
                }
                else -> Unit
            }
        }
    }

    fun selectVehicle(vehicle: Vehicle) {
        _selectedVehicle.value = vehicle
        viewModelScope.launch {
            preferencesManager.setSelectedVin(vehicle.vin)
            preferencesManager.cacheWidgetSnapshot(
                WidgetVehicleSnapshot(
                    vehicleName = vehicle.displayName,
                    vehicleVin = vehicle.vin,
                    vehicleId = vehicle.vehicleIdentifier.ifBlank { vehicle.enrollmentId.ifBlank { vehicle.regId } },
                    registrationId = vehicle.regId.ifBlank { vehicle.enrollmentId.ifBlank { vehicle.vehicleIdentifier } },
                    generation = vehicle.generation,
                    brandIndicator = vehicle.brandIndicator,
                    modelCode = vehicle.modelCode,
                    message = "Open app to refresh",
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
            VehicleWidgetProvider.refreshAll(appContext)
            refreshStatus(forceFromServer = false)
        }
    }

    // ─── Status refresh ────────────────────────────────────────────────────────
    fun refreshSessionSnapshotOnAppOpen() {
        viewModelScope.launch {
            // Re-validate the persisted session and pull current vehicle/status data after the app is reopened.
            // This prevents a valid-looking local session from displaying stale or empty vehicle data.
            loadVehicles()
            delay(750)
            _selectedVehicle.value?.let { selected ->
                refreshStatusInternal(vehicle = selected, forceFromServer = true, showLoading = false)
            }
        }
    }

    fun refreshStatus(forceFromServer: Boolean = true) {
        val vehicle = _selectedVehicle.value ?: return
        viewModelScope.launch {
            refreshStatusInternal(vehicle = vehicle, forceFromServer = forceFromServer, showLoading = true)
        }
    }

    private suspend fun refreshStatusInternal(
        vehicle: Vehicle,
        forceFromServer: Boolean,
        showLoading: Boolean
    ): Result<VehicleStatusData> {
        if (showLoading) {
            _isStatusLoading.value = true
            _statusError.value = null
        }
        return try {
            when (val result = repository.getVehicleStatus(
                vin = vehicle.vin,
                forceRefresh = forceFromServer,
                registrationId = vehicle.regId,
                generation = vehicle.generation,
                brandIndicator = vehicle.brandIndicator
            )) {
                is Result.Success -> {
                    _vehicleStatus.value = result.data
                    cacheStatusForWidget(vehicle, result.data, "Updated from BlueBridge")
                    result
                }
                is Result.Error -> {
                    _statusError.value = result.message
                    preferencesManager.setWidgetMessage("Status refresh failed")
                    VehicleWidgetProvider.refreshAll(appContext)
                    result
                }
                else -> result
            }
        } finally {
            if (showLoading) _isStatusLoading.value = false
        }
    }

    fun refreshLocation() {
        val vehicle = _selectedVehicle.value ?: return
        viewModelScope.launch {
            _isStatusLoading.value = true
            _statusError.value = null
            when (val result = repository.getVehicleLocation(
                vin = vehicle.vin,
                registrationId = vehicle.regId,
                generation = vehicle.generation,
                brandIndicator = vehicle.brandIndicator
            )) {
                is Result.Success -> {
                    val current = _vehicleStatus.value ?: VehicleStatusData()
                    _vehicleStatus.value = current.copy(location = result.data)
                }
                is Result.Error -> _statusError.value = result.message
                else -> Unit
            }
            _isStatusLoading.value = false
        }
    }

    // ─── Lock / Unlock ─────────────────────────────────────────────────────────
    fun lockDoors() = sendCommand(
        title = "Lock doors",
        loadingMsg = "Sending lock request…",
        successMsg = "Doors locked"
    ) {
        val vehicle = _selectedVehicle.value ?: return@sendCommand Result.Error("No vehicle selected")
        repository.lockDoors(
            vin = vehicle.vin,
            registrationId = vehicle.regId,
            generation = vehicle.generation,
            brandIndicator = vehicle.brandIndicator
        )
    }

    fun unlockDoors() = sendCommand(
        title = "Unlock doors",
        loadingMsg = "Sending unlock request…",
        successMsg = "Doors unlocked"
    ) {
        val vehicle = _selectedVehicle.value ?: return@sendCommand Result.Error("No vehicle selected")
        repository.unlockDoors(
            vin = vehicle.vin,
            registrationId = vehicle.regId,
            generation = vehicle.generation,
            brandIndicator = vehicle.brandIndicator
        )
    }

    fun lockThenStartEngine() {
        val requestedSettings = _remoteStartSettings.value
        lockVehicleThenStart(
            nextActionLabel = if (_selectedVehicle.value?.isEV == true) "climate" else "remote start",
            afterLock = {
                _remoteStartSettings.value = requestedSettings
                startEngine()
            }
        )
    }

    fun lockThenStartClimate(
        tempF: String = "72",
        defrost: Boolean = false,
        heatedSteering: Boolean = false,
        driverSeat: Int = 2,
        passengerSeat: Int = 2,
        rearLeftSeat: Int = 2,
        rearRightSeat: Int = 2
    ) {
        lockVehicleThenStart(
            nextActionLabel = "climate",
            afterLock = {
                startClimate(
                    tempF = tempF,
                    defrost = defrost,
                    heatedSteering = heatedSteering,
                    driverSeat = driverSeat,
                    passengerSeat = passengerSeat,
                    rearLeftSeat = rearLeftSeat,
                    rearRightSeat = rearRightSeat
                )
            }
        )
    }

    private fun lockVehicleThenStart(
        nextActionLabel: String,
        afterLock: () -> Unit
    ) {
        viewModelScope.launch {
            val vehicle = _selectedVehicle.value
            if (vehicle == null) {
                _commandState.value = CommandState(CommandStatus.ERROR, "Lock failed", "No vehicle selected")
                delay(3000)
                _commandState.value = CommandState()
                return@launch
            }

            _commandState.value = CommandState(
                status = CommandStatus.LOADING,
                message = "Locking vehicle…",
                detail = "Climate/remote start requires locked doors. Sending lock request first…"
            )
            preferencesManager.setWidgetMessage("Locking vehicle")
            VehicleWidgetProvider.refreshAll(appContext)

            when (val lockResult = repository.lockDoors(
                vin = vehicle.vin,
                registrationId = vehicle.regId,
                generation = vehicle.generation,
                brandIndicator = vehicle.brandIndicator
            )) {
                is Result.Success -> {
                    val serviceBrand = vehicle.serviceBrandName
                    val lockedStatus = _vehicleStatus.value?.copy(doorLock = true, doorLockStatus = "LOCKED")
                    if (lockedStatus != null) {
                        _vehicleStatus.value = lockedStatus
                        cacheStatusForWidget(vehicle, lockedStatus, "Locked; starting ${nextActionLabel.lowercase()}")
                    }
                    addCommandHistory("Lock doors", "Accepted by $serviceBrand; starting $nextActionLabel", true)
                    _commandState.value = CommandState(
                        status = CommandStatus.ACCEPTED,
                        message = "Vehicle locked",
                        detail = "Starting $nextActionLabel next…"
                    )
                    delay(2500)
                    afterLock()
                }
                is Result.Error -> {
                    val detail = lockResult.message
                    _commandState.value = CommandState(
                        status = CommandStatus.ERROR,
                        message = "Lock failed",
                        detail = "Could not lock the vehicle, so $nextActionLabel was not started. $detail"
                    )
                    addCommandHistory("Lock doors", detail, false)
                    preferencesManager.setWidgetMessage("Lock failed")
                    VehicleWidgetProvider.refreshAll(appContext)
                    delay(4000)
                    _commandState.value = CommandState()
                }
                else -> Unit
            }
        }
    }

    // ─── Remote Start / Stop ───────────────────────────────────────────────────
    fun startEngine() {
        val vehicle = _selectedVehicle.value
        val title = if (vehicle?.isEV == true) "Start climate" else "Remote start"
        val loading = if (vehicle?.isEV == true) "Sending climate start…" else "Sending remote start…"
        val success = if (vehicle?.isEV == true) "Climate on" else "Engine started"
        val requestedSettings = _remoteStartSettings.value
        sendCommand(
            title = title,
            loadingMsg = loading,
            successMsg = success,
            forceRefreshAfterCommand = true,
            onAccepted = { applyOptimisticClimateStarted(requestedSettings) },
            mergeRefreshedStatus = { refreshed ->
                if (refreshed.airCtrlOn || refreshed.engineStatus) refreshed else optimisticClimateStartedStatus(refreshed, requestedSettings)
            }
        ) {
            val selected = _selectedVehicle.value ?: return@sendCommand Result.Error("No vehicle selected")
            val s = requestedSettings
            repository.startEngine(
                vin = selected.vin,
                tempF = s.tempF,
                hvacOn = s.hvacOn,
                defrost = s.defrost,
                heatedSteering = s.heatedSteering,
                driverSeatHeat = s.driverSeatHeat,
                passengerSeatHeat = s.passengerSeatHeat,
                rearLeftSeatHeat = s.rearLeftSeatHeat,
                rearRightSeatHeat = s.rearRightSeatHeat,
                durationMinutes = s.durationMinutes,
                isEv = selected.isEV,
                registrationId = selected.regId,
                generation = selected.generation,
                brandIndicator = selected.brandIndicator
            )
        }
    }

    fun stopEngine() {
        val vehicle = _selectedVehicle.value
        val title = if (vehicle?.isEV == true) "Stop climate" else "Stop engine"
        val loading = if (vehicle?.isEV == true) "Sending climate stop…" else "Sending engine stop…"
        val success = if (vehicle?.isEV == true) "Climate off" else "Engine stopped"
        sendCommand(
            title = title,
            loadingMsg = loading,
            successMsg = success,
            forceRefreshAfterCommand = true,
            onAccepted = { applyOptimisticClimateStopped() },
            mergeRefreshedStatus = { refreshed -> if (refreshed.airCtrlOn || refreshed.engineStatus) optimisticClimateStoppedStatus(refreshed) else refreshed }
        ) {
            val selected = _selectedVehicle.value ?: return@sendCommand Result.Error("No vehicle selected")
            if (selected.isEV) {
                repository.stopClimate(
                    vin = selected.vin,
                    isEv = true,
                    registrationId = selected.regId,
                    generation = selected.generation,
                    brandIndicator = selected.brandIndicator
                )
            } else {
                repository.stopEngine(
                    vin = selected.vin,
                    registrationId = selected.regId,
                    generation = selected.generation,
                    brandIndicator = selected.brandIndicator
                )
            }
        }
    }



    private fun formatScheduleHistoryTime(raw: String): String {
        val digits = raw.filter { it.isDigit() }.padStart(4, '0').takeLast(4)
        val hour = digits.take(2).toIntOrNull() ?: return raw
        val minute = digits.takeLast(2).toIntOrNull() ?: 0
        return "%02d:%02d".format(java.util.Locale.US, hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }

    private fun formatClimateHistoryTemp(tempF: String): String {
        val fahrenheit = tempF.toIntOrNull() ?: return "${tempF}°F"
        return if (temperatureUnit.value.equals("C", ignoreCase = true)) {
            val celsius = ((fahrenheit - 32) * 5.0 / 9.0).roundToInt()
            "${celsius}°C"
        } else {
            "${fahrenheit}°F"
        }
    }

    // ─── Climate ───────────────────────────────────────────────────────────────
    fun startClimate(
        tempF: String = "72",
        defrost: Boolean = false,
        heatedSteering: Boolean = false,
        driverSeat: Int = 2,
        passengerSeat: Int = 2,
        rearLeftSeat: Int = 2,
        rearRightSeat: Int = 2
    ) = sendCommand(
        title = "Start climate",
        loadingMsg = "Sending climate start…",
        successMsg = "Climate on",
        historyDetail = "Cabin ${formatClimateHistoryTemp(tempF)}${if (defrost) " · defrost" else ""}${if (heatedSteering) " · heated wheel" else ""}",
        forceRefreshAfterCommand = true,
        onAccepted = {
            applyOptimisticClimateStarted(
                RemoteStartSettings(
                    tempF = tempF,
                    hvacOn = true,
                    defrost = defrost,
                    heatedSteering = heatedSteering,
                    driverSeatHeat = driverSeat,
                    passengerSeatHeat = passengerSeat,
                    rearLeftSeatHeat = rearLeftSeat,
                    rearRightSeatHeat = rearRightSeat
                )
            )
        },
        mergeRefreshedStatus = { refreshed ->
            if (refreshed.airCtrlOn) refreshed else optimisticClimateStartedStatus(
                refreshed,
                RemoteStartSettings(
                    tempF = tempF,
                    hvacOn = true,
                    defrost = defrost,
                    heatedSteering = heatedSteering,
                    driverSeatHeat = driverSeat,
                    passengerSeatHeat = passengerSeat,
                    rearLeftSeatHeat = rearLeftSeat,
                    rearRightSeatHeat = rearRightSeat
                )
            )
        }
    ) {
        val vehicle = _selectedVehicle.value ?: return@sendCommand Result.Error("No vehicle selected")
        repository.startClimate(
            vin = vehicle.vin,
            tempF = tempF,
            defrost = defrost,
            heatedSteering = heatedSteering,
            driverSeatHeat = driverSeat,
            passengerSeatHeat = passengerSeat,
            rearLeftSeatHeat = rearLeftSeat,
            rearRightSeatHeat = rearRightSeat,
            isEv = vehicle.isEV,
            registrationId = vehicle.regId,
            generation = vehicle.generation,
            brandIndicator = vehicle.brandIndicator
        )
    }

    fun stopClimate() = sendCommand(
        title = "Stop climate",
        loadingMsg = "Sending climate stop…",
        successMsg = "Climate off",
        forceRefreshAfterCommand = true,
        onAccepted = { applyOptimisticClimateStopped() },
        mergeRefreshedStatus = { refreshed -> if (refreshed.airCtrlOn) optimisticClimateStoppedStatus(refreshed) else refreshed }
    ) {
        val vehicle = _selectedVehicle.value ?: return@sendCommand Result.Error("No vehicle selected")
        repository.stopClimate(
            vin = vehicle.vin,
            isEv = vehicle.isEV,
            registrationId = vehicle.regId,
            generation = vehicle.generation,
            brandIndicator = vehicle.brandIndicator
        )
    }

    // ─── EV ────────────────────────────────────────────────────────────────────
    fun startCharging() = sendCommand(
        title = "Start charging",
        loadingMsg = "Sending charge start…",
        successMsg = "Charging started"
    ) {
        val vehicle = _selectedVehicle.value ?: return@sendCommand Result.Error("No vehicle selected")
        repository.startCharging(
            vin = vehicle.vin,
            registrationId = vehicle.regId,
            generation = vehicle.generation,
            brandIndicator = vehicle.brandIndicator,
            vehicleId = vehicle.enrollmentId.ifBlank { vehicle.vehicleIdentifier.ifBlank { vehicle.regId } }
        )
    }

    fun stopCharging() = sendCommand(
        title = "Stop charging",
        loadingMsg = "Sending charge stop…",
        successMsg = "Charging stopped"
    ) {
        val vehicle = _selectedVehicle.value ?: return@sendCommand Result.Error("No vehicle selected")
        repository.stopCharging(
            vin = vehicle.vin,
            registrationId = vehicle.regId,
            generation = vehicle.generation,
            brandIndicator = vehicle.brandIndicator,
            vehicleId = vehicle.enrollmentId.ifBlank { vehicle.vehicleIdentifier.ifBlank { vehicle.regId } }
        )
    }

    fun setChargeTarget(acTarget: Int, dcTarget: Int) = sendCommand(
        title = "Set charge targets",
        loadingMsg = "Sending charge targets…",
        successMsg = "Charge targets set",
        historyDetail = "AC $acTarget% · DC $dcTarget%",
        forceRefreshAfterCommand = true
    ) {
        val vehicle = _selectedVehicle.value ?: return@sendCommand Result.Error("No vehicle selected")
        repository.setChargeTarget(
            vin = vehicle.vin,
            acTarget = acTarget,
            dcTarget = dcTarget,
            registrationId = vehicle.regId,
            generation = vehicle.generation,
            brandIndicator = vehicle.brandIndicator
        )
    }


    fun setChargingSchedule(
        chargeStartTime: String,
        chargeEndTime: String,
        offPeakStartTime: String,
        offPeakEndTime: String,
        offPeakOnly: Boolean
    ) {
        viewModelScope.launch {
            val vehicle = _selectedVehicle.value
            if (vehicle == null) {
                _commandState.value = CommandState(CommandStatus.ERROR, "Set charging schedule failed", "No vehicle selected")
                return@launch
            }
            _commandState.value = CommandState(
                status = CommandStatus.LOADING,
                message = "Sending charging schedule…",
                detail = "Sending request to vehicle service…"
            )
            preferencesManager.setWidgetMessage("Sending charging schedule")
            VehicleWidgetProvider.refreshAll(appContext)

            val serviceBrand = vehicle.serviceBrandName
            val requestedDetail = "Charge ${formatScheduleHistoryTime(chargeStartTime)}-${formatScheduleHistoryTime(chargeEndTime)} · Off-peak ${formatScheduleHistoryTime(offPeakStartTime)}-${formatScheduleHistoryTime(offPeakEndTime)}"
            when (val result = repository.setChargingSchedule(
                vin = vehicle.vin,
                chargeStartTime = chargeStartTime,
                chargeEndTime = chargeEndTime,
                offPeakStartTime = offPeakStartTime,
                offPeakEndTime = offPeakEndTime,
                offPeakOnly = offPeakOnly,
                registrationId = vehicle.regId,
                generation = vehicle.generation,
                brandIndicator = vehicle.brandIndicator,
                vehicleId = vehicle.enrollmentId.ifBlank { vehicle.vehicleIdentifier.ifBlank { vehicle.regId } }
            )) {
                is Result.Success -> {
                    _commandState.value = CommandState(
                        status = CommandStatus.REFRESHING,
                        message = "Schedule request accepted",
                        detail = "Refreshing vehicle status to verify the change…"
                    )
                    delay(1200)
                    when (val refreshResult = refreshStatusInternal(vehicle, forceFromServer = true, showLoading = false)) {
                        is Result.Success -> {
                            val verification = verifyChargingScheduleApplied(
                                refreshResult.data,
                                serviceBrand,
                                chargeStartTime,
                                chargeEndTime,
                                offPeakStartTime,
                                offPeakEndTime,
                                offPeakOnly
                            )
                            if (verification == null) {
                                _commandState.value = CommandState(
                                    status = CommandStatus.SUCCESS,
                                    message = "Charging schedule updated",
                                    detail = requestedDetail
                                )
                                addCommandHistory("Set charging schedule", requestedDetail, true)
                            } else {
                                _commandState.value = CommandState(
                                    status = CommandStatus.ERROR,
                                    message = "Schedule was not applied",
                                    detail = verification
                                )
                                addCommandHistory("Set charging schedule", "$serviceBrand accepted request, but status did not match. $requestedDetail", false)
                            }
                        }
                        is Result.Error -> {
                            _commandState.value = CommandState(
                                status = CommandStatus.ERROR,
                                message = "Could not verify schedule",
                                detail = "$serviceBrand accepted the request, but status refresh failed: ${refreshResult.message}"
                            )
                            addCommandHistory("Set charging schedule", "$serviceBrand accepted request, but it was not verified. $requestedDetail", false)
                        }
                        else -> Unit
                    }
                    delay(4500)
                    _commandState.value = CommandState()
                }
                is Result.Error -> {
                    _commandState.value = CommandState(
                        status = CommandStatus.ERROR,
                        message = "Set charging schedule failed",
                        detail = result.message
                    )
                    addCommandHistory("Set charging schedule", result.message, false)
                }
                else -> Unit
            }
        }
    }

    private fun verifyChargingScheduleApplied(
        status: VehicleStatusData,
        serviceBrand: String,
        chargeStartTime: String,
        chargeEndTime: String,
        offPeakStartTime: String,
        offPeakEndTime: String,
        offPeakOnly: Boolean
    ): String? {
        val reserv = status.evStatus?.reservChargeInfos
            ?: return "The refreshed status did not include charging schedule data. The request may not be supported for this vehicle/region."
        val expectedChargeStart = normalizeScheduleDigits(chargeStartTime)
        val expectedChargeEnd = normalizeScheduleDigits(chargeEndTime)
        val expectedOffPeakStart = normalizeScheduleDigits(offPeakStartTime)
        val expectedOffPeakEnd = normalizeScheduleDigits(offPeakEndTime)

        val actualChargeStart = normalizeScheduleDigits(
            reserv.chargeWindow?.start?.time?.time ?: reserv.reservChargeInfo?.reservInfo?.time?.time.orEmpty()
        )
        val actualChargeEnd = normalizeScheduleDigits(
            reserv.chargeWindow?.end?.time?.time ?: reserv.reserveChargeInfo2?.reservInfo?.time?.time.orEmpty()
        )
        val actualOffPeakStart = normalizeScheduleDigits(reserv.offPeakPowerInfo?.offPeakPowerTime1?.startTime?.time.orEmpty())
        val actualOffPeakEnd = normalizeScheduleDigits(reserv.offPeakPowerInfo?.offPeakPowerTime1?.endTime?.time.orEmpty())
        val actualOffPeakOnly = reserv.offPeakPowerInfo?.offPeakPowerFlag == 1

        val mismatches = mutableListOf<String>()
        if (actualChargeStart != expectedChargeStart) mismatches += "charge start stayed ${formatScheduleHistoryTime(actualChargeStart)}"
        if (actualChargeEnd.isNotBlank() && actualChargeEnd != expectedChargeEnd) mismatches += "charge end stayed ${formatScheduleHistoryTime(actualChargeEnd)}"
        if (actualOffPeakStart != expectedOffPeakStart) mismatches += "off-peak start stayed ${formatScheduleHistoryTime(actualOffPeakStart)}"
        if (actualOffPeakEnd != expectedOffPeakEnd) mismatches += "off-peak end stayed ${formatScheduleHistoryTime(actualOffPeakEnd)}"
        if (actualOffPeakOnly != offPeakOnly) mismatches += "off-peak mode stayed ${if (actualOffPeakOnly) "Only" else "Priority/disabled"}"

        return if (mismatches.isEmpty()) null else "$serviceBrand returned success, but refreshed status did not match: ${mismatches.joinToString("; ")}."
    }

    private fun normalizeScheduleDigits(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        if (digits.isBlank()) return ""
        val padded = if (digits.length >= 4) digits.takeLast(4) else digits.padStart(4, '0')
        val hour = padded.take(2).toIntOrNull() ?: return ""
        val minute = padded.takeLast(2).toIntOrNull() ?: return ""
        if (hour !in 0..23 || minute !in 0..59) return ""
        return "%02d%02d".format(java.util.Locale.US, hour, minute)
    }

    // ─── Horn / Lights ─────────────────────────────────────────────────────────
    fun hornAndLights() = sendCommand(
        title = "Horn & lights",
        loadingMsg = "Sending horn & lights…",
        successMsg = "Horn & lights accepted"
    ) {
        val vehicle = _selectedVehicle.value ?: return@sendCommand Result.Error("No vehicle selected")
        repository.hornAndLights(
            vin = vehicle.vin,
            registrationId = vehicle.regId,
            generation = vehicle.generation,
            brandIndicator = vehicle.brandIndicator
        )
    }

    fun flashLights() = sendCommand(
        title = "Flash lights",
        loadingMsg = "Sending flash lights…",
        successMsg = "Flash lights accepted"
    ) {
        val vehicle = _selectedVehicle.value ?: return@sendCommand Result.Error("No vehicle selected")
        repository.flashLights(
            vin = vehicle.vin,
            registrationId = vehicle.regId,
            generation = vehicle.generation,
            brandIndicator = vehicle.brandIndicator
        )
    }

    // ─── Official-app-style features ──────────────────────────────────────────
    fun setValetMode(enabled: Boolean) {
        val vin = _selectedVehicle.value?.vin
        viewModelScope.launch {
            preferencesManager.setValetModeEnabled(enabled)
            if (vin == null) {
                _commandState.value = CommandState(CommandStatus.ERROR, "No vehicle selected")
                addCommandHistory("Valet Mode", "No vehicle selected", false)
                delay(2500)
                _commandState.value = CommandState()
                return@launch
            }
            when (val result = repository.setValetMode(vin, enabled)) {
                is Result.Success -> {
                    val msg = if (enabled) "Valet Mode enabled" else "Valet Mode disabled"
                    _commandState.value = CommandState(CommandStatus.SUCCESS, msg)
                    addCommandHistory("Valet Mode", msg, true)
                }
                is Result.Error -> {
                    _commandState.value = CommandState(CommandStatus.ERROR, result.message)
                    addCommandHistory("Valet Mode", result.message, false)
                }
                else -> Unit
            }
            delay(3000)
            _commandState.value = CommandState()
        }
    }

    fun setActiveDriverProfile(profileId: String) {
        _driverProfiles.value = _driverProfiles.value.map { it.copy(isActive = it.id == profileId) }
        viewModelScope.launch {
            preferencesManager.setActiveDriverProfileId(profileId)
            _commandState.value = CommandState(CommandStatus.SUCCESS, "Driver profile selected")
            addCommandHistory("Driver profile", "Selected $profileId", true)
            delay(1500)
            _commandState.value = CommandState()
        }
    }

    fun updateDriverProfilePhoto(profileId: String, photoUri: String?) {
        _driverProfiles.value = _driverProfiles.value.map {
            if (it.id == profileId) it.copy(photoUri = photoUri) else it
        }
        viewModelScope.launch {
            preferencesManager.setDriverProfilePhotoUri(profileId, photoUri)
            _commandState.value = CommandState(CommandStatus.SUCCESS, "Profile photo updated")
            delay(1500)
            _commandState.value = CommandState()
        }
    }

    fun setCustomDashboardImage(photoUri: String?) {
        viewModelScope.launch {
            preferencesManager.setCustomDashboardImageUri(photoUri)
            _commandState.value = CommandState(
                status = CommandStatus.SUCCESS,
                message = if (photoUri.isNullOrBlank()) "Dashboard image reset" else "Dashboard image updated"
            )
            delay(1500)
            _commandState.value = CommandState()
        }
    }

    fun requestSurroundView() {
        val vin = _selectedVehicle.value?.vin ?: return
        viewModelScope.launch {
            _commandState.value = CommandState(CommandStatus.LOADING, "Requesting surround view…")
            when (val result = repository.getSurroundViewSnapshot(vin)) {
                is Result.Success -> {
                    _surroundViewSnapshot.value = result.data
                    _commandState.value = CommandState(CommandStatus.SUCCESS, "Surround view updated")
                    addCommandHistory("Surround view", "Snapshot updated", true)
                }
                is Result.Error -> {
                    _commandState.value = CommandState(CommandStatus.ERROR, result.message)
                    addCommandHistory("Surround view", result.message, false)
                }
                else -> Unit
            }
            delay(3000)
            _commandState.value = CommandState()
        }
    }

    fun clearFeatureNotice() {
        _featureNotice.value = null
    }

    // ─── Settings updates ──────────────────────────────────────────────────────
    fun updateRemoteStartSettings(settings: RemoteStartSettings) {
        _remoteStartSettings.value = settings
        viewModelScope.launch {
            preferencesManager.setDefaultClimateTemp(settings.tempF)
        }
    }

    fun clearCommandHistory() {
        viewModelScope.launch { preferencesManager.clearCommandHistory() }
    }

    private fun applyOptimisticClimateStarted(settings: RemoteStartSettings) {
        val base = _vehicleStatus.value ?: VehicleStatusData()
        _vehicleStatus.value = optimisticClimateStartedStatus(base, settings)
    }

    private fun optimisticClimateStartedStatus(
        base: VehicleStatusData,
        settings: RemoteStartSettings
    ): VehicleStatusData = base.copy(
        engineStatus = if (_selectedVehicle.value?.isEV == true) base.engineStatus else true,
        airCtrlOn = settings.hvacOn,
        airTemp = AirTemp(value = settings.tempF, unit = 1),
        defrost = settings.defrost,
        steerWheelHeat = if (settings.heatedSteering) 1 else base.steerWheelHeat,
        seatHeaterVentInfo = SeatHeaterVentInfo(
            driverSeatHeatState = settings.driverSeatHeat,
            passengerSeatHeatState = settings.passengerSeatHeat,
            rearLeftSeatHeatState = settings.rearLeftSeatHeat,
            rearRightSeatHeatState = settings.rearRightSeatHeat
        )
    )

    private fun applyOptimisticClimateStopped() {
        val base = _vehicleStatus.value ?: VehicleStatusData()
        _vehicleStatus.value = optimisticClimateStoppedStatus(base)
    }

    private fun optimisticClimateStoppedStatus(base: VehicleStatusData): VehicleStatusData = base.copy(
        engineStatus = if (_selectedVehicle.value?.isEV == true) base.engineStatus else false,
        airCtrlOn = false,
        defrost = false,
        steerWheelHeat = 0,
        seatHeaterVentInfo = SeatHeaterVentInfo()
    )

    // ─── Helper ────────────────────────────────────────────────────────────────
    private fun sendCommand(
        title: String,
        loadingMsg: String,
        successMsg: String,
        historyDetail: String = "",
        forceRefreshAfterCommand: Boolean = false,
        onAccepted: (() -> Unit)? = null,
        mergeRefreshedStatus: ((VehicleStatusData) -> VehicleStatusData)? = null,
        action: suspend () -> Result<Unit>
    ) {
        viewModelScope.launch {
            _commandState.value = CommandState(
                status = CommandStatus.LOADING,
                message = loadingMsg,
                detail = "Sending request to vehicle service…"
            )
            preferencesManager.setWidgetMessage(loadingMsg.removeSuffix("…"))
            VehicleWidgetProvider.refreshAll(appContext)

            when (val result = action()) {
                is Result.Success -> {
                    onAccepted?.invoke()
                    val selectedForBrand = _selectedVehicle.value
                    val serviceBrand = selectedForBrand?.serviceBrandName ?: "vehicle service"
                    val acceptedLabel = if (selectedForBrand != null) "Accepted by $serviceBrand" else "Accepted by vehicle service"
                    _commandState.value = CommandState(
                        status = CommandStatus.ACCEPTED,
                        message = acceptedLabel,
                        detail = "$title request was accepted by $serviceBrand. Waiting for vehicle status…"
                    )
                    addCommandHistory(title, historyDetail.ifBlank { acceptedLabel }, true)
                    delay(900)

                    val selected = _selectedVehicle.value
                    if (selected != null) {
                        _commandState.value = CommandState(
                            status = CommandStatus.REFRESHING,
                            message = "Waiting for vehicle update…",
                            detail = "Refreshing status after command"
                        )
                        when (val refreshResult = refreshStatusInternal(selected, forceFromServer = forceRefreshAfterCommand, showLoading = false)) {
                            is Result.Success -> {
                                val mergedStatus = mergeRefreshedStatus?.invoke(refreshResult.data)
                                if (mergedStatus != null && mergedStatus != refreshResult.data) {
                                    _vehicleStatus.value = mergedStatus
                                    cacheStatusForWidget(selected, mergedStatus, "Command accepted; awaiting vehicle-confirmed status")
                                }
                                _commandState.value = CommandState(
                                    status = CommandStatus.SUCCESS,
                                    message = successMsg,
                                    detail = if (mergedStatus != null && mergedStatus != refreshResult.data) {
                                        "Command accepted; vehicle status may take another refresh to confirm"
                                    } else {
                                        "Status refreshed"
                                    }
                                )
                            }
                            is Result.Error -> _commandState.value = CommandState(
                                status = CommandStatus.SUCCESS,
                                message = successMsg,
                                detail = "Command was accepted, but status refresh failed: ${refreshResult.message}"
                            )
                            else -> _commandState.value = CommandState(CommandStatus.SUCCESS, successMsg)
                        }
                    } else {
                        _commandState.value = CommandState(CommandStatus.SUCCESS, successMsg)
                    }

                    delay(2500)
                    _commandState.value = CommandState()
                }
                is Result.Error -> {
                    val detail = result.message
                    _commandState.value = CommandState(
                        status = CommandStatus.ERROR,
                        message = "$title failed",
                        detail = detail
                    )
                    addCommandHistory(title, detail, false)
                    preferencesManager.setWidgetMessage("$title failed")
                    VehicleWidgetProvider.refreshAll(appContext)
                    delay(3500)
                    _commandState.value = CommandState()
                }
                else -> Unit
            }
        }
    }

    private suspend fun addCommandHistory(title: String, detail: String, successful: Boolean) {
        val vehicle = _selectedVehicle.value
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

    private suspend fun cacheStatusForWidget(vehicle: Vehicle, status: VehicleStatusData, message: String) {
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
        preferencesManager.cacheWidgetSnapshot(
            WidgetVehicleSnapshot(
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
        )
        VehicleWidgetProvider.refreshAll(appContext)
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

    fun clearError() {
        _statusError.value = null
    }
}
