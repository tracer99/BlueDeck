package com.blueandroid.data.models

enum class SupportState {
    SUPPORTED,
    NOT_SUPPORTED,
    NOT_REPORTED;

    fun showInUi(): Boolean = this != NOT_SUPPORTED
}

data class SeatClimateCapabilities(
    val config: SeatConfig?,
    val heatState: SupportState,
    val ventState: SupportState
) {
    val showHeat: Boolean get() = heatState.showInUi()
    val showVent: Boolean get() = ventState.showInUi()
    val ventCapableForSelector: Boolean get() = ventState == SupportState.SUPPORTED
}

data class VehicleFeatureCapabilities(
    val driver: SeatClimateCapabilities,
    val passenger: SeatClimateCapabilities,
    val rearLeft: SeatClimateCapabilities,
    val rearRight: SeatClimateCapabilities,
    val showHeatedSteering: Boolean,
    val showSeatClimateControls: Boolean,
    val showSeatClimatePresets: Boolean,
    val showEvCharging: Boolean,
    val showDigitalKey: Boolean,
    val showLocation: Boolean,
    val showValetMode: Boolean
)

data class ClimateSeatSettings(
    val heatedSteering: Boolean = false,
    val driverSeat: Int = 2,
    val passengerSeat: Int = 2,
    val rearLeftSeat: Int = 2,
    val rearRightSeat: Int = 2
)

fun Vehicle.resolveCapabilities(regionCode: String? = null): VehicleFeatureCapabilities {
    val configs = seatConfigurations?.seatConfigs.orEmpty()
    val driver = resolveSeatClimate(configs, "1", "D", "DRIVER", "FRONT_LEFT", "FRONTLEFT", "FL")
    val passenger = resolveSeatClimate(configs, "2", "P", "PASSENGER", "FRONT_RIGHT", "FRONTRIGHT", "FR", "ASSISTANT")
    val rearLeft = resolveSeatClimate(configs, "3", "RL", "REAR_LEFT", "REARLEFT", "BACK_LEFT", "BACKLEFT", "LEFT_REAR", "LEFTREAR")
    val rearRight = resolveSeatClimate(configs, "4", "RR", "REAR_RIGHT", "REARRIGHT", "BACK_RIGHT", "BACKRIGHT", "RIGHT_REAR", "RIGHTREAR")

    val showHeatedSteering = resolveShowHeatedSteering(configs, driver, passenger)
    val showSeatClimateControls = showHeatedSteering ||
        driver.showHeat || driver.showVent ||
        passenger.showHeat || passenger.showVent ||
        rearLeft.showHeat || rearLeft.showVent ||
        rearRight.showHeat || rearRight.showVent

    val details = additionalDetails
    return VehicleFeatureCapabilities(
        driver = driver,
        passenger = passenger,
        rearLeft = rearLeft,
        rearRight = rearRight,
        showHeatedSteering = showHeatedSteering,
        showSeatClimateControls = showSeatClimateControls,
        showSeatClimatePresets = showSeatClimateControls,
        showEvCharging = resolveShowEvCharging(),
        showDigitalKey = details?.digitalKeyCapable.toSupportState().showInUi(),
        showLocation = resolveShowLocation(regionCode),
        showValetMode = details?.valetActivateCapable.toSupportState().showInUi()
    )
}

fun Vehicle.clampClimateSeatSettings(settings: ClimateSeatSettings): ClimateSeatSettings {
    val caps = resolveCapabilities()
    return ClimateSeatSettings(
        heatedSteering = settings.heatedSteering && caps.showHeatedSteering,
        driverSeat = clampSeatLevel(settings.driverSeat, caps.driver),
        passengerSeat = clampSeatLevel(settings.passengerSeat, caps.passenger),
        rearLeftSeat = clampSeatLevel(settings.rearLeftSeat, caps.rearLeft),
        rearRightSeat = clampSeatLevel(settings.rearRightSeat, caps.rearRight)
    )
}

fun SeatConfig?.heatSupportState(): SupportState {
    if (this == null) return SupportState.NOT_REPORTED
    val explicit = heatingCapable.toSupportState()
    if (explicit != SupportState.NOT_REPORTED) return explicit
    val levels = supportedLevelCodes()
    return when {
        levels.any { it in 6..8 } -> SupportState.SUPPORTED
        levels.isNotEmpty() -> SupportState.NOT_SUPPORTED
        else -> SupportState.NOT_REPORTED
    }
}

fun SeatConfig?.ventSupportState(): SupportState {
    if (this == null) return SupportState.NOT_REPORTED
    val explicit = ventCapable.toSupportState()
    if (explicit != SupportState.NOT_REPORTED) return explicit
    val levels = supportedLevelCodes()
    return when {
        levels.any { it in 3..5 } -> SupportState.SUPPORTED
        levels.isNotEmpty() -> SupportState.NOT_SUPPORTED
        else -> SupportState.NOT_REPORTED
    }
}

fun String?.toSupportState(): SupportState = when (this?.trim()?.uppercase()) {
    "Y", "YES", "TRUE", "1", "2", "SUPPORTED", "CAPABLE", "ENABLED", "AVAILABLE" -> SupportState.SUPPORTED
    "N", "NO", "FALSE", "0", "NOT_SUPPORTED", "UNSUPPORTED", "NOT SUPPORTED", "DISABLED", "UNAVAILABLE" -> SupportState.NOT_SUPPORTED
    else -> SupportState.NOT_REPORTED
}

fun List<SeatConfig>.seatConfigFor(vararg ids: String): SeatConfig? {
    val normalized = ids.map { it.normalizeSeatId() }.toSet()
    return firstOrNull { it.seatLocationId.normalizeSeatId() in normalized }
}

private fun resolveSeatClimate(configs: List<SeatConfig>, vararg ids: String): SeatClimateCapabilities {
    val config = configs.seatConfigFor(*ids)
    // Partial seat list from the API (e.g. front seats only) means absent positions are not equipped.
    val heatState = when {
        config != null -> config.heatSupportState()
        configs.isNotEmpty() -> SupportState.NOT_SUPPORTED
        else -> SupportState.NOT_REPORTED
    }
    val ventState = when {
        config != null -> config.ventSupportState()
        configs.isNotEmpty() -> SupportState.NOT_SUPPORTED
        else -> SupportState.NOT_REPORTED
    }
    return SeatClimateCapabilities(
        config = config,
        heatState = heatState,
        ventState = ventState
    )
}

private fun resolveShowHeatedSteering(
    configs: List<SeatConfig>,
    driver: SeatClimateCapabilities,
    passenger: SeatClimateCapabilities
): Boolean {
    if (configs.isEmpty()) return true
    val driverHeat = driver.heatState
    val passengerHeat = passenger.heatState
    if (driverHeat == SupportState.NOT_REPORTED || passengerHeat == SupportState.NOT_REPORTED) return true
    return driverHeat == SupportState.SUPPORTED || passengerHeat == SupportState.SUPPORTED
}

private fun Vehicle.resolveShowEvCharging(): Boolean {
    val energyConsole = additionalDetails?.energyConsoleCapable.toSupportState()
    if (energyConsole == SupportState.NOT_SUPPORTED) return false
    return isEV || isPlugInHybrid
}

private fun resolveShowLocation(regionCode: String?): Boolean {
    val region = regionCode?.trim()?.uppercase().orEmpty()
    if (region.startsWith("EU_") || region == "AU" || region.startsWith("AU_")) return false
    return true
}

private fun clampSeatLevel(level: Int, seat: SeatClimateCapabilities): Int {
    val isHeat = level in 6..8
    val isVent = level in 3..5
    return when {
        isHeat && !seat.showHeat -> 2
        isVent && !seat.showVent -> 2
        !isHeat && !isVent && level != 2 && level != 0 -> {
            if (seat.showHeat) level else 2
        }
        else -> level
    }
}

private fun SeatConfig.supportedLevelCodes(): List<Int> = supportedLevels
    .split(',', '|', ';', ' ')
    .mapNotNull { it.trim().toIntOrNull() }

private fun String.normalizeSeatId(): String = trim()
    .uppercase()
    .replace('-', '_')
    .replace(' ', '_')
