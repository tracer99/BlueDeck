package com.bluebridge.android.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluebridge.android.data.models.*
import com.bluebridge.android.data.api.Region
import com.bluebridge.android.data.api.usesCanadianTods
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

    val accountRegion = preferencesManager.region
        .stateIn(viewModelScope, SharingStarted.Eagerly, "US_HYUNDAI")

    private suspend fun apiRegistration(vehicle: Vehicle): String {
        val r = runCatching { Region.valueOf(preferencesManager.region.first()) }
            .getOrDefault(Region.US_HYUNDAI)
        return if (r.usesCanadianTods()) vehicle.vehicleIdentifier.ifBlank { vehicle.regId } else vehicle.regId
    }

    // ─── Remote start settings ─────────────────────────────────────────────────
    private val _remoteStartSettings = MutableStateFlow(RemoteStartSettings())
    val remoteStartSettings: StateFlow<RemoteStartSettings> = _remoteStartSettings.asStateFlow()

    // ─── Settings ──────────────────────────────────────────────────────────────
    val temperatureUnit = preferencesManager.temperatureUnit
        .stateIn(viewModelScope, SharingStarted.Eagerly, "F")

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
    fun loadVehicles() {
        viewModelScope.launch {
            when (val result = repository.getVehicles()) {
                is Result.Success -> {
                    _vehicles.value = result.data
                    val savedVin = preferencesManager.selectedVin.first()
                    val vehicle = result.data.find { it.vin == savedVin } ?: result.data.firstOrNull()
                    vehicle?.let { selectVehicle(it) }
                }
                is Result.Error -> _statusError.value = result.message
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
                    message = "Open app to refresh",
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
            VehicleWidgetProvider.refreshAll(appContext)
            refreshStatus(forceFromServer = false)
        }
    }

    // ─── Status refresh ────────────────────────────────────────────────────────
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
                registrationId = apiRegistration(vehicle),
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
                registrationId = apiRegistration(vehicle),
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
            registrationId = apiRegistration(vehicle),
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
            registrationId = apiRegistration(vehicle),
            generation = vehicle.generation,
            brandIndicator = vehicle.brandIndicator
        )
    }

    // ─── Remote Start / Stop ───────────────────────────────────────────────────
    fun startEngine() {
        val vehicle = _selectedVehicle.value
        val title = if (vehicle?.isEV == true) "Start climate" else "Remote start"
        val loading = if (vehicle?.isEV == true) "Sending climate start…" else "Sending remote start…"
        val success = if (vehicle?.isEV == true) "Climate on" else "Engine started"
        sendCommand(title, loading, success) {
            val selected = _selectedVehicle.value ?: return@sendCommand Result.Error("No vehicle selected")
            val s = _remoteStartSettings.value
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
                registrationId = apiRegistration(selected),
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
        sendCommand(title, loading, success) {
            val selected = _selectedVehicle.value ?: return@sendCommand Result.Error("No vehicle selected")
            if (selected.isEV) {
                repository.stopClimate(
                    vin = selected.vin,
                    isEv = true,
                    registrationId = apiRegistration(selected),
                    generation = selected.generation,
                    brandIndicator = selected.brandIndicator
                )
            } else {
                repository.stopEngine(
                    vin = selected.vin,
                    registrationId = apiRegistration(selected),
                    generation = selected.generation,
                    brandIndicator = selected.brandIndicator
                )
            }
        }
    }

    // ─── Climate ───────────────────────────────────────────────────────────────
    fun startClimate(
        tempF: String = "72",
        defrost: Boolean = false,
        driverSeat: Int = 2,
        passengerSeat: Int = 2,
        rearLeftSeat: Int = 2,
        rearRightSeat: Int = 2
    ) = sendCommand(
        title = "Start climate",
        loadingMsg = "Sending climate start…",
        successMsg = "Climate on",
        historyDetail = "Cabin ${tempF}°F${if (defrost) " · defrost" else ""}"
    ) {
        val vehicle = _selectedVehicle.value ?: return@sendCommand Result.Error("No vehicle selected")
        repository.startClimate(
            vin = vehicle.vin,
            tempF = tempF,
            defrost = defrost,
            driverSeatHeat = driverSeat,
            passengerSeatHeat = passengerSeat,
            rearLeftSeatHeat = rearLeftSeat,
            rearRightSeatHeat = rearRightSeat,
            isEv = vehicle.isEV,
            registrationId = apiRegistration(vehicle),
            generation = vehicle.generation,
            brandIndicator = vehicle.brandIndicator
        )
    }

    fun stopClimate() = sendCommand(
        title = "Stop climate",
        loadingMsg = "Sending climate stop…",
        successMsg = "Climate off"
    ) {
        val vehicle = _selectedVehicle.value ?: return@sendCommand Result.Error("No vehicle selected")
        repository.stopClimate(
            vin = vehicle.vin,
            isEv = vehicle.isEV,
            registrationId = apiRegistration(vehicle),
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
            registrationId = apiRegistration(vehicle),
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
            registrationId = apiRegistration(vehicle),
            generation = vehicle.generation,
            brandIndicator = vehicle.brandIndicator,
            vehicleId = vehicle.enrollmentId.ifBlank { vehicle.vehicleIdentifier.ifBlank { vehicle.regId } }
        )
    }

    fun setChargeTarget(acTarget: Int, dcTarget: Int) = sendCommand(
        title = "Set charge targets",
        loadingMsg = "Sending charge targets…",
        successMsg = "Charge targets set",
        historyDetail = "AC $acTarget% · DC $dcTarget%"
    ) {
        val vehicle = _selectedVehicle.value ?: return@sendCommand Result.Error("No vehicle selected")
        repository.setChargeTarget(
            vin = vehicle.vin,
            acTarget = acTarget,
            dcTarget = dcTarget,
            registrationId = apiRegistration(vehicle),
            generation = vehicle.generation,
            brandIndicator = vehicle.brandIndicator
        )
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
            registrationId = apiRegistration(vehicle),
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
            registrationId = apiRegistration(vehicle),
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

    // ─── Helper ────────────────────────────────────────────────────────────────
    private fun sendCommand(
        title: String,
        loadingMsg: String,
        successMsg: String,
        historyDetail: String = "",
        action: suspend () -> Result<Unit>
    ) {
        viewModelScope.launch {
            _commandState.value = CommandState(
                status = CommandStatus.LOADING,
                message = loadingMsg,
                detail = "Sending request to Hyundai…"
            )
            preferencesManager.setWidgetMessage(loadingMsg.removeSuffix("…"))
            VehicleWidgetProvider.refreshAll(appContext)

            when (val result = action()) {
                is Result.Success -> {
                    _commandState.value = CommandState(
                        status = CommandStatus.ACCEPTED,
                        message = "Accepted by Hyundai",
                        detail = "$title request was accepted. Waiting for vehicle status…"
                    )
                    addCommandHistory(title, historyDetail.ifBlank { "Accepted by Hyundai" }, true)
                    delay(900)

                    val selected = _selectedVehicle.value
                    if (selected != null) {
                        _commandState.value = CommandState(
                            status = CommandStatus.REFRESHING,
                            message = "Waiting for vehicle update…",
                            detail = "Refreshing status after command"
                        )
                        when (val refreshResult = refreshStatusInternal(selected, forceFromServer = false, showLoading = false)) {
                            is Result.Success -> _commandState.value = CommandState(
                                status = CommandStatus.SUCCESS,
                                message = successMsg,
                                detail = "Status refreshed"
                            )
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
        preferencesManager.cacheWidgetSnapshot(
            WidgetVehicleSnapshot(
                vehicleName = vehicle.displayName,
                doorsLocked = status.doorsLocked,
                batteryPercent = batteryPercent,
                rangeMiles = rangeMiles,
                chargingLabel = chargingLabel,
                message = message,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
        VehicleWidgetProvider.refreshAll(appContext)
    }

    fun clearError() {
        _statusError.value = null
    }
}
