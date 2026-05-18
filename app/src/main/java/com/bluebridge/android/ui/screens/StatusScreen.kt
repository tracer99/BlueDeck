package com.bluebridge.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bluebridge.android.ui.components.ControlSection
import com.bluebridge.android.data.models.*
import com.bluebridge.android.ui.theme.*
import com.bluebridge.android.viewmodel.VehicleViewModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SeatHeatOrange = Color(0xFFFF9800)
private val SeatVentBlue = Color(0xFF03A9F4)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(
    vehicleViewModel: VehicleViewModel,
    onNavigateBack: () -> Unit
) {
    val status by vehicleViewModel.vehicleStatus.collectAsStateWithLifecycle()
    val selectedVehicle by vehicleViewModel.selectedVehicle.collectAsStateWithLifecycle()
    val commandState by vehicleViewModel.commandState.collectAsStateWithLifecycle()
    val statusError by vehicleViewModel.statusError.collectAsStateWithLifecycle()
    val isLoading by vehicleViewModel.isStatusLoading.collectAsStateWithLifecycle()
    val temperatureUnit by vehicleViewModel.temperatureUnit.collectAsStateWithLifecycle()
    val distanceUnit by vehicleViewModel.distanceUnit.collectAsStateWithLifecycle()
    val timeZoneMode by vehicleViewModel.timeZoneMode.collectAsStateWithLifecycle()
    val timeFormat by vehicleViewModel.timeFormat.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vehicle Status", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { vehicleViewModel.refreshStatus(true) }) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Filled.Refresh, "Refresh", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (status == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.DirectionsCar, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No status data", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vehicleViewModel.refreshStatus(true) }) {
                        Text("Refresh Now")
                    }
                }
            }
        } else {
            val s = status!!
            val vehicle = selectedVehicle
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ─── Doors ─────────────────────────────────────────────────────
                ControlSection(title = "Doors & Security") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusRow(
                            icon = Icons.Filled.Lock,
                            label = "Door Locks",
                            value = if (s.doorsLocked) "Locked" else "Unlocked",
                            valueColor = if (s.doorsLocked) SuccessGreen else WarningAmber
                        )
                        s.doorOpenStatus?.let { doors ->
                            StatusRow(Icons.Filled.DoorFront, "Front Left Door",
                                if (doors.frontLeft == 1) "Open" else "Closed",
                                if (doors.frontLeft == 1) WarningAmber else SuccessGreen)
                            StatusRow(Icons.Filled.DoorFront, "Front Right Door",
                                if (doors.frontRight == 1) "Open" else "Closed",
                                if (doors.frontRight == 1) WarningAmber else SuccessGreen)
                            StatusRow(Icons.Filled.DoorBack, "Rear Left Door",
                                if (doors.backLeft == 1) "Open" else "Closed",
                                if (doors.backLeft == 1) WarningAmber else SuccessGreen)
                            StatusRow(Icons.Filled.DoorBack, "Rear Right Door",
                                if (doors.backRight == 1) "Open" else "Closed",
                                if (doors.backRight == 1) WarningAmber else SuccessGreen)
                        }
                        StatusRow(Icons.Filled.Inventory2, "Trunk",
                            if (s.trunkOpenStatus) "Open" else "Closed",
                            if (s.trunkOpenStatus) WarningAmber else SuccessGreen)

                        StatusRow(Icons.Filled.DirectionsCar, "Hood",
                            if (s.hoodOpenStatus) "Open" else "Closed",
                            if (s.hoodOpenStatus) WarningAmber else SuccessGreen)
                    }
                }

                // ─── Engine ────────────────────────────────────────────────────
                ControlSection(title = "Engine & Ignition") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusRow(Icons.Filled.PowerSettingsNew, "Engine",
                            if (s.engineStatus) "Running" else "Off",
                            if (s.engineStatus) SuccessGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        StatusRow(Icons.Filled.Key, "Ignition",
                            if (s.ignitionOn) "On" else "Off",
                            if (s.ignitionOn) SuccessGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }

                // ─── Climate ───────────────────────────────────────────────────
                ControlSection(title = "Climate") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusRow(Icons.Filled.AcUnit, "Climate Control",
                            if (s.airCtrlOn) "On" else "Off",
                            if (s.airCtrlOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        s.airTemp?.let { temp ->
                            StatusRow(Icons.Filled.Thermostat, "Set Temperature",
                                apiTemperatureToPreferredLabel(temp.value, temp.unit, temperatureUnit), MaterialTheme.colorScheme.onSurface)
                        }
                        StatusRow(Icons.Filled.AcUnit, "Defrost",
                            if (s.defrost) "On" else "Off",
                            if (s.defrost) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        StatusRow(Icons.Filled.Waves, "Rear Defroster",
                            if (s.sideBackWindowHeat == 1) "On" else "Off",
                            if (s.sideBackWindowHeat == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        StatusRow(Icons.Filled.Straight, "Steering Wheel Heat",
                            if (s.steerWheelHeat == 1) "On" else "Off",
                            if (s.steerWheelHeat == 1) WarningAmber else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }


                // ─── Seat Comfort ─────────────────────────────────────────────
                ControlSection(title = "Seat Comfort") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val seats = s.seatHeaterVentInfo
                        if (seats != null) {
                            StatusRow(Icons.Filled.Info, "Driver Seat", seatLevelLabel(seats.driverSeatHeatState), seatLevelColor(seats.driverSeatHeatState))
                            StatusRow(Icons.Filled.Info, "Passenger Seat", seatLevelLabel(seats.passengerSeatHeatState), seatLevelColor(seats.passengerSeatHeatState))
                            StatusRow(Icons.Filled.Info, "Rear Left Seat", seatLevelLabel(seats.rearLeftSeatHeatState), seatLevelColor(seats.rearLeftSeatHeatState))
                            StatusRow(Icons.Filled.Info, "Rear Right Seat", seatLevelLabel(seats.rearRightSeatHeatState), seatLevelColor(seats.rearRightSeatHeatState))
                        } else {
                            StackedStatusRow(
                                label = "Current seat state",
                                value = "Not reported by this vehicle/status response"
                            )
                        }
                        SeatCapabilityMap(seatConfigs = vehicle?.seatConfigurations?.seatConfigs.orEmpty())
                    }
                }

                // ─── Tires ─────────────────────────────────────────────────────
                if (s.tirePressureLamp != null || s.tirePressureStatus != null) {
                    TireStatusSection(
                        warning = s.tirePressureLamp,
                        pressure = s.tirePressureStatus,
                        timeZoneMode = timeZoneMode,
                        timeFormat = timeFormat
                    )
                }

                // ─── EV Battery ────────────────────────────────────────────────
                s.evStatus?.let { ev ->
                    ControlSection(title = "EV Battery") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusRow(Icons.Filled.BatteryFull, "Battery Level",
                                "${ev.batteryStatus}%", MaterialTheme.colorScheme.onSurface)
                            StatusRow(Icons.Filled.EvStation, "Charging",
                                if (ev.batteryCharge) "Yes" else "No",
                                if (ev.batteryCharge) ChargingGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            StatusRow(Icons.Filled.Power, "Plug Status",
                                ev.plugStatusLabel,
                                if (ev.batteryPlugin > 0) ChargingGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            if (ev.rangeKm > 0) {
                                StatusRow(Icons.Filled.Route, "EV Range",
                                    formatDistanceFromMiles(ev.rangeMiles, distanceUnit),
                                    MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }

                // ─── Fuel / PHEV Gas Range ─────────────────────────────────────
                if (s.hasFuelTelemetryFor(vehicle)) {
                    ControlSection(title = if (s.isPlugInHybridStatusFor(vehicle)) "PHEV Fuel" else "Fuel") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            s.normalizedFuelLevelPercent?.let { fuelLevel ->
                                StatusRow(
                                    Icons.Filled.LocalGasStation,
                                    "Fuel Level",
                                    "$fuelLevel%",
                                    if (fuelLevel <= 10 || s.lowFuelLight) WarningAmber else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (s.fuelRangeMiles > 0.0) {
                                StatusRow(
                                    Icons.Filled.Route,
                                    "Fuel Range",
                                    formatDistanceFromMiles(s.fuelRangeMiles, distanceUnit),
                                    MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (s.lowFuelLight) {
                                StatusRow(Icons.Filled.Warning, "Low Fuel Warning", "On", WarningAmber)
                            }
                        }
                    }
                }

                // ─── Additional Telemetry / Diagnostics ─────────────────────────
                ControlSection(title = "Vehicle Telemetry") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        s.battery?.let { battery ->
                            StatusRow(Icons.Filled.BatteryFull, "12V Battery SoC",
                                "${battery.batteryLevel}%", MaterialTheme.colorScheme.onSurface)
                            StatusRow(Icons.Filled.Info, "12V Battery State Code",
                                battery.batteryState.toString(), MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                            StatusRow(Icons.Filled.Info, "12V Auto Cut Mode",
                                battery.powerAutoCutMode.toString(), MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                            battery.batSignalReferenceValue?.let { ref ->
                                if (ref.warningThreshold > 0) {
                                    StatusRow(Icons.Filled.Warning, "12V Warning Threshold",
                                        "${ref.warningThreshold}%", WarningAmber)
                                }
                            }
                        }

                        s.windowOpenStatus?.let { windows ->
                            val value = if (windows.anyOpen) {
                                "FL ${windows.frontLeftLevel}% · FR ${windows.frontRightLevel}% · RL ${windows.rearLeftLevel}% · RR ${windows.rearRightLevel}%"
                            } else {
                                "Closed"
                            }
                            StatusRow(Icons.Filled.Info, "Window Levels", value,
                                if (windows.anyOpen) WarningAmber else SuccessGreen)
                        }

                        StatusRow(Icons.Filled.Info, "Washer Fluid",
                            if (s.washerFluidStatus) "Warning" else "OK",
                            if (s.washerFluidStatus) WarningAmber else SuccessGreen)
                        StatusRow(Icons.Filled.Info, "Smart Key Battery",
                            if (s.smartKeyBatteryWarning) "Warning" else "OK",
                            if (s.smartKeyBatteryWarning) WarningAmber else SuccessGreen)

                    }
                }


                vehicle?.let { v ->
                    ControlSection(title = "Vehicle Metadata") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusRow(Icons.Filled.Info, "Model", listOf(v.modelYear, v.modelName.ifBlank { v.modelCode }, v.hmaModel).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Not reported" }, MaterialTheme.colorScheme.onSurface)
                            StatusRow(Icons.Filled.Info, "Exterior", listOf(v.colorName, v.sapColorCode).filter { it.isNotBlank() }.joinToString(" / ").ifBlank { "Not reported" }, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                            StatusRow(Icons.Filled.Info, "Interior", v.interiorColor.ifBlank { "Not reported" }, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                            StatusRow(Icons.Filled.Info, "Head Unit", v.accessoryCode.ifBlank { "Not reported" }, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                            StatusRow(Icons.Filled.Info, "Map Provider", v.mapProvider.ifBlank { "Not reported" }, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                            StatusRow(Icons.Filled.Info, "Generation", v.generation.ifBlank { "Not reported" }, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                            StatusRow(Icons.Filled.Info, "Enrollment", v.bluelinkEnrollmentStatus.ifBlank { v.enrollmentStatus.ifBlank { "Not reported" } }, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                            v.additionalDetails?.vehicleModemType?.takeIf { it.isNotBlank() }?.let { modem ->
                                StackedStatusRow("Modem", modem)
                            }
                        }
                    }

                    v.additionalDetails?.let { details ->
                        ControlSection(title = "Vehicle Capabilities") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                DigitalKeyStatusRow(
                                    value = "Capable ${yesNoShort(details.digitalKeyCapable)} · Enrolled ${yesNoShort(details.digitalKeyEnrolled)}",
                                    valueColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                )
                                StatusRow(Icons.Filled.Info, "V2L", yesNo(details.v2lOption), capabilityColor(details.v2lOption))
                                StatusRow(Icons.Filled.Info, "Wi-Fi Hotspot", yesNo(details.wifiHotspotCapable), capabilityColor(details.wifiHotspotCapable))
                                StatusRow(Icons.Filled.Info, "Battery Preconditioning", capabilityOption(details.batteryPreconditioningOption), capabilityColor(details.batteryPreconditioningOption))
                                StatusRow(Icons.Filled.Info, "Charge Port Door", yesNo(details.chargePortDoorOption), capabilityColor(details.chargePortDoorOption))
                                StatusRow(Icons.Filled.Info, "Frunk", yesNo(details.frunkOption), capabilityColor(details.frunkOption))
                                StatusRow(Icons.Filled.Info, "NACS Eligible", yesNo(details.nacsEligible), capabilityColor(details.nacsEligible))
                                StatusRow(Icons.Filled.Info, "Valet Activation", yesNo(details.valetActivateCapable), capabilityColor(details.valetActivateCapable))
                                StatusRow(Icons.Filled.Info, "Map OTA", yesNo(details.mapOtaPackage), capabilityColor(details.mapOtaPackage))
                                StatusRow(Icons.Filled.Info, "EV Trip", yesNo(details.evTripCapable), capabilityColor(details.evTripCapable))
                                if (details.targetSocLevelMax > 0) {
                                    StatusRow(Icons.Filled.BatteryFull, "Max Charge Target", "${details.targetSocLevelMax}%", MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                                }
                            }
                        }
                    }

                    if (v.packageDetails.isNotEmpty()) {
                        ControlSection(title = "Subscriptions & Packages") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                v.packageDetails.forEach { pkg ->
                                    val name = pkg.displayCategory.substringBefore("||").ifBlank { pkg.packageType.ifBlank { "Package" } }
                                    val status = listOf(pkg.subscriptionType, formatHyundaiDate(pkg.renewalDate).takeIf { it.isNotBlank() }?.let { "until $it" }.orEmpty())
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · ")
                                        .ifBlank { "Not reported" }
                                    StackedStatusRow(name, status)
                                }
                            }
                        }
                    }
                }

                s.evStatus?.reservChargeInfos?.let { reserv ->
                    val fatc = reserv.reservChargeInfo?.detail?.reservFatcSet ?: reserv.reserveChargeInfo2?.detail?.reservFatcSet
                    if (fatc != null) {
                        ControlSection(title = "Scheduled Climate") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusRow(Icons.Filled.Info, "Reserved Climate", if (fatc.airCtrl > 0) "On" else "Off", if (fatc.airCtrl > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                StatusRow(Icons.Filled.AcUnit, "Reserved Defrost", if (fatc.defrost) "On" else "Off", if (fatc.defrost) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                fatc.airTemp?.let { temp ->
                                    StatusRow(Icons.Filled.Thermostat, "Reserved Temp", temp.value.ifBlank { "OFF" }, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                                }
                            }
                        }
                    }
                }

                ControlSection(title = "Service Diagnostics") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusRow(Icons.Filled.Info, "Status Refresh", if (isLoading) "Refreshing" else "Loaded", if (isLoading) MaterialTheme.colorScheme.primary else SuccessGreen)
                        statusError?.let { err ->
                            StackedStatusRow("Last status error", err)
                        }
                        StatusRow(Icons.Filled.Info, "Last Command", commandState.status.name.lowercase().replaceFirstChar { it.uppercase() }, commandStatusColor(commandState.status.name))
                        if (commandState.message.isNotBlank()) {
                            StackedStatusRow("Command message", commandState.message)
                        }
                    }
                }

                s.evStatus?.let { ev ->
                    ControlSection(
                        title = "EV Details",
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusRow(Icons.Filled.Power, "Charge Port Door",
                                chargePortDoorLabel(ev.chargePortDoorOpen), MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                            StatusRow(Icons.Filled.Info, "Battery Preconditioning",
                                if (ev.batteryPrecondition) "Active" else "Inactive",
                                if (ev.batteryPrecondition) ChargingGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            StatusRow(Icons.Filled.Power, "V2L / Discharge",
                                if (ev.batteryDisCharge || ev.batteryDisChargePlugin > 0) "Active" else "Inactive",
                                if (ev.batteryDisCharge || ev.batteryDisChargePlugin > 0) ChargingGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            if (ev.dischargingLimit > 0) {
                                StatusRow(Icons.Filled.BatteryFull, "Discharge Limit",
                                    "${ev.dischargingLimit}%", MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                            }
                        }
                    }

                    val scheduledWindow = ev.reservChargeInfos?.chargeWindow
                    val offPeakWindow = ev.reservChargeInfos?.offPeakPowerInfo?.offPeakPowerTime1
                    if (scheduledWindow != null || offPeakWindow != null) {
                        ControlSection(
                                title = "Charging Schedule",
                                modifier = Modifier.fillMaxWidth()
                            ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                scheduledWindow?.let { window ->
                                    StackedStatusRow(
                                        label = "Scheduled charge",
                                        value = "${formatScheduleTime(window.start?.time)} → ${formatScheduleTime(window.end?.time)}"
                                    )
                                }
                                offPeakWindow?.let { offPeak ->
                                    StackedStatusRow(
                                        label = "Off-peak window",
                                        value = "${formatScheduleTime(offPeak.startTime)} → ${formatScheduleTime(offPeak.endTime)}"
                                    )
                                }
                            }
                        }
                    }
                }

                s.lampWireStatus?.let { lamps ->
                    ControlSection(
                        title = "Lamp Diagnostics",
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusRow(Icons.Filled.Info, "Head lamps", onOff(lamps.headLamp?.active == true), MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                            StatusRow(Icons.Filled.Info, "Brake lamps", onOff(lamps.stopLamp?.active == true), MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                            StatusRow(Icons.Filled.Info, "Turn signals", onOff(lamps.turnSignalLamp?.active == true), MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                        }
                    }
                }

                // ─── Location ──────────────────────────────────────────────────
                s.location?.coord?.let { coord ->
                    if (coord.lat != 0.0 || coord.lon != 0.0) {
                        ControlSection(title = "Location") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusRow(Icons.Filled.Place, "Latitude", coord.lat.toString(), MaterialTheme.colorScheme.onSurface)
                                StatusRow(Icons.Filled.Place, "Longitude", coord.lon.toString(), MaterialTheme.colorScheme.onSurface)
                                if (s.location.heading != 0) {
                                    StatusRow(Icons.Filled.Explore, "Heading", "${s.location.heading}°", MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }

                // ─── Odometer ──────────────────────────────────────────────────
                if (s.totalMileage > 0) {
                    ControlSection(title = "Odometer") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusRow(Icons.Filled.Speed, "Total Mileage",
                                formatOdometerFromMiles(s.totalMileage, distanceUnit), MaterialTheme.colorScheme.onSurface)
                            vehicle?.odometerUpdateDate?.takeIf { it.isNotBlank() }?.let { updated ->
                                OdometerUpdatedRow(formatOdometerTimestamp(updated, timeZoneMode, timeFormat))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun onOff(value: Boolean): String = if (value) "On" else "Off"

private fun chargePortDoorLabel(code: Int): String = when (code) {
    0 -> "Closed / inactive (code 0)"
    1 -> "Open (code 1)"
    2 -> "Closed (code 2)"
    3 -> "Opening / closing (code 3)"
    -1 -> "Not reported"
    else -> "Code $code"
}

private fun formatHyundaiTimestamp(raw: String, timeZoneMode: String = "DEVICE", timeFormat: String = "12_HOUR"): String {
    if (raw.isBlank()) return raw
    val displayZone = resolveDisplayZone(timeZoneMode)
    val trimmed = raw.trim()
    return parseVehicleTimestamp(
        raw = trimmed,
        displayZone = displayZone,
        timeFormat = timeFormat,
        treatNaiveTimestampAsUtc = true
    ) ?: trimmed.removeSuffix("Z").replace('T', ' ').trim()
}

private fun formatOdometerTimestamp(raw: String, timeZoneMode: String = "DEVICE", timeFormat: String = "12_HOUR"): String {
    if (raw.isBlank()) return raw
    val displayZone = resolveDisplayZone(timeZoneMode)
    val trimmed = raw.trim()
    return parseVehicleTimestamp(
        raw = trimmed,
        displayZone = displayZone,
        timeFormat = timeFormat,
        treatNaiveTimestampAsUtc = false
    ) ?: trimmed.removeSuffix("Z").replace('T', ' ').trim()
}

private fun parseVehicleTimestamp(
    raw: String,
    displayZone: ZoneId,
    timeFormat: String,
    treatNaiveTimestampAsUtc: Boolean
): String? {
    val output = DateTimeFormatter.ofPattern(timestampPattern(timeFormat), Locale.US)
    val normalizedIso = raw.replace(' ', 'T')
    return runCatching {
        when {
            normalizedIso.endsWith("Z", ignoreCase = true) -> Instant.parse(normalizedIso.uppercase(Locale.US))
                .atZone(displayZone)
                .format(output)
            raw.contains(Regex("[+-]\\d{2}:?\\d{2}$")) -> ZonedDateTime.parse(normalizedIso)
                .withZoneSameInstant(displayZone)
                .format(output)
            else -> {
                val digits = raw.filter { it.isDigit() }
                if (digits.length < 12) return@runCatching null
                val padded = digits.padEnd(14, '0')
                val local = LocalDateTime.of(
                    padded.substring(0, 4).toInt(),
                    padded.substring(4, 6).toInt(),
                    padded.substring(6, 8).toInt(),
                    padded.substring(8, 10).toInt(),
                    padded.substring(10, 12).toInt(),
                    padded.substring(12, 14).toInt()
                )
                if (treatNaiveTimestampAsUtc) {
                    local.atZone(ZoneId.of("UTC")).withZoneSameInstant(displayZone).format(output)
                } else {
                    local.atZone(displayZone).format(output)
                }
            }
        }
    }.getOrNull()
}

private fun timestampPattern(timeFormat: String): String =
    if (timeFormat == "24_HOUR") "MMM d, yyyy HH:mm z" else "MMM d, yyyy h:mm a z"

private fun resolveDisplayZone(timeZoneMode: String): ZoneId = when (timeZoneMode) {
    "UTC" -> ZoneId.of("UTC")
    "AMERICA_NEW_YORK" -> ZoneId.of("America/New_York")
    "AMERICA_CHICAGO" -> ZoneId.of("America/Chicago")
    "AMERICA_DENVER" -> ZoneId.of("America/Denver")
    "AMERICA_LOS_ANGELES" -> ZoneId.of("America/Los_Angeles")
    "AMERICA_HALIFAX" -> ZoneId.of("America/Halifax")
    else -> ZoneId.systemDefault()
}

private fun formatScheduleTime(time: com.bluebridge.android.data.models.ScheduleTime?): String {
    val raw = time?.time.orEmpty().filter { it.isDigit() }
    if (raw.isBlank() || raw == "0000") return "Not set"
    val padded = raw.padStart(4, '0').takeLast(4)
    val hour = padded.take(2).toIntOrNull() ?: return raw
    val minute = padded.takeLast(2).toIntOrNull() ?: 0
    val suffix = if (hour >= 12) "PM" else "AM"
    val displayHour = when (val h = hour % 12) { 0 -> 12 else -> h }
    return String.format(java.util.Locale.US, "%d:%02d %s", displayHour, minute, suffix)
}

@Composable
private fun SeatCapabilityMap(seatConfigs: List<SeatConfig>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Seat capabilities",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Reported if available",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SeatCapabilityTile(
                    label = "Driver",
                    seatConfig = seatConfigs.seatConfigFor("1", "D", "DRIVER", "FRONT_LEFT", "FRONTLEFT", "FL"),
                    modifier = Modifier.weight(1f)
                )
                SeatCapabilityTile(
                    label = "Passenger",
                    seatConfig = seatConfigs.seatConfigFor("2", "P", "PASSENGER", "FRONT_RIGHT", "FRONTRIGHT", "FR", "ASSISTANT"),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SeatCapabilityTile(
                    label = "Rear Left",
                    seatConfig = seatConfigs.seatConfigFor("3", "RL", "REAR_LEFT", "REARLEFT", "BACK_LEFT", "BACKLEFT", "LEFT_REAR", "LEFTREAR"),
                    modifier = Modifier.weight(1f)
                )
                SeatCapabilityTile(
                    label = "Rear Right",
                    seatConfig = seatConfigs.seatConfigFor("4", "RR", "REAR_RIGHT", "REARRIGHT", "BACK_RIGHT", "BACKRIGHT", "RIGHT_REAR", "RIGHTREAR"),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}


private fun List<SeatConfig>.seatConfigFor(vararg ids: String): SeatConfig? {
    val normalized = ids.map { it.normalizeSeatId() }.toSet()
    return firstOrNull { it.seatLocationId.normalizeSeatId() in normalized }
}

private fun String.normalizeSeatId(): String = trim()
    .uppercase()
    .replace('-', '_')
    .replace(' ', '_')

@Composable
private fun SeatCapabilityTile(
    label: String,
    seatConfig: SeatConfig?,
    modifier: Modifier = Modifier
) {
    val heatState = seatConfig.heatSupportState()
    val ventState = seatConfig.ventSupportState()
    val anySupported = heatState == SeatSupportState.SUPPORTED || ventState == SeatSupportState.SUPPORTED
    val tileAccent = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier.height(98.dp),
        shape = RoundedCornerShape(18.dp),
        color = tileAccent.copy(alpha = if (anySupported) 0.08f else 0.045f),
        border = BorderStroke(1.dp, tileAccent.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.AirlineSeatReclineNormal,
                contentDescription = null,
                tint = tileAccent.copy(alpha = if (anySupported) 0.90f else 0.48f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                CapabilityPill(
                    supportedLabel = "Heat",
                    unsupportedLabel = "No heat",
                    unknownLabel = "Heat ?",
                    state = heatState,
                    supportedColor = SeatHeatOrange,
                    icon = Icons.Filled.Whatshot
                )
                CapabilityPill(
                    supportedLabel = "Vent",
                    unsupportedLabel = "No vent",
                    unknownLabel = "Vent ?",
                    state = ventState,
                    supportedColor = SeatVentBlue,
                    icon = Icons.Filled.AcUnit
                )
            }
        }
    }
}

@Composable
private fun CapabilityPill(
    supportedLabel: String,
    unsupportedLabel: String,
    unknownLabel: String,
    state: SeatSupportState,
    supportedColor: Color,
    icon: ImageVector
) {
    val tint = when (state) {
        SeatSupportState.SUPPORTED -> supportedColor
        SeatSupportState.NOT_SUPPORTED -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.46f)
        SeatSupportState.NOT_REPORTED -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f)
    }
    val label = when (state) {
        SeatSupportState.SUPPORTED -> supportedLabel
        SeatSupportState.NOT_SUPPORTED -> unsupportedLabel
        SeatSupportState.NOT_REPORTED -> unknownLabel
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = tint.copy(alpha = when (state) {
            SeatSupportState.SUPPORTED -> 0.18f
            SeatSupportState.NOT_SUPPORTED -> 0.07f
            SeatSupportState.NOT_REPORTED -> 0.05f
        }),
        border = BorderStroke(1.dp, tint.copy(alpha = if (state == SeatSupportState.SUPPORTED) 0.52f else 0.16f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(11.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StackedStatusRow(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            fontWeight = FontWeight.Medium
        )
    }
}


private enum class SeatSupportState { SUPPORTED, NOT_SUPPORTED, NOT_REPORTED }

private fun SeatConfig?.heatSupportState(): SeatSupportState {
    if (this == null) return SeatSupportState.NOT_REPORTED
    val explicit = heatingCapable.supportFlagState()
    if (explicit != SeatSupportState.NOT_REPORTED) return explicit
    val levels = supportedLevelCodes()
    return when {
        levels.any { it in 6..8 } -> SeatSupportState.SUPPORTED
        levels.isNotEmpty() -> SeatSupportState.NOT_SUPPORTED
        else -> SeatSupportState.NOT_REPORTED
    }
}

private fun SeatConfig?.ventSupportState(): SeatSupportState {
    if (this == null) return SeatSupportState.NOT_REPORTED
    val explicit = ventCapable.supportFlagState()
    if (explicit != SeatSupportState.NOT_REPORTED) return explicit
    val levels = supportedLevelCodes()
    return when {
        levels.any { it in 3..5 } -> SeatSupportState.SUPPORTED
        levels.isNotEmpty() -> SeatSupportState.NOT_SUPPORTED
        else -> SeatSupportState.NOT_REPORTED
    }
}

private fun SeatConfig.supportedLevelCodes(): List<Int> = supportedLevels
    .split(',', '|', ';', ' ')
    .mapNotNull { it.trim().toIntOrNull() }

private fun String?.supportFlagState(): SeatSupportState = when (this?.trim()?.uppercase()) {
    "Y", "YES", "TRUE", "1", "2", "SUPPORTED", "CAPABLE", "ENABLED", "AVAILABLE" -> SeatSupportState.SUPPORTED
    "N", "NO", "FALSE", "0", "NOT_SUPPORTED", "UNSUPPORTED", "NOT SUPPORTED", "DISABLED", "UNAVAILABLE" -> SeatSupportState.NOT_SUPPORTED
    else -> SeatSupportState.NOT_REPORTED
}

private fun String?.isSupportedFlag(): Boolean = supportFlagState() == SeatSupportState.SUPPORTED

private fun yesNo(raw: String): String = when (raw.trim().uppercase()) {
    "Y", "YES", "TRUE", "1" -> "Supported"
    "N", "NO", "FALSE", "0" -> "Not supported"
    "" -> "Not reported"
    else -> raw
}

private fun yesNoShort(raw: String): String = when (raw.trim().uppercase()) {
    "Y", "YES", "TRUE", "1" -> "Yes"
    "N", "NO", "FALSE", "0" -> "No"
    "" -> "—"
    else -> raw
}

private fun capabilityOption(raw: String): String = raw.ifBlank { "Not reported" }

private fun capabilityColor(raw: String): Color = when (raw.trim().uppercase()) {
    "Y", "YES", "TRUE", "1", "2" -> SuccessGreen
    "N", "NO", "FALSE", "0" -> Color(0xFFD0D6E0).copy(alpha = 0.5f)
    else -> Color(0xFFD0D6E0).copy(alpha = 0.85f)
}

private fun seatName(id: String): String = when (id) {
    "1" -> "Driver"
    "2" -> "Passenger"
    "3" -> "Rear Left"
    "4" -> "Rear Right"
    else -> "Seat $id"
}

private fun seatLevelLabel(level: Int): String = when (level) {
    0, 2 -> "Off"
    6 -> "Heat Low"
    7 -> "Heat Medium"
    8 -> "Heat High"
    3 -> "Vent Low"
    4 -> "Vent Medium"
    5 -> "Vent High"
    else -> "Unknown ($level)"
}

private fun seatLevelColor(level: Int): Color = when (level) {
    0, 2 -> Color(0xFFD0D6E0).copy(alpha = 0.5f)
    6, 7, 8 -> SeatHeatOrange
    3, 4, 5 -> SeatVentBlue
    else -> Color(0xFFD0D6E0).copy(alpha = 0.75f)
}

private fun supportedSeatLevelsLabel(raw: String): String {
    if (raw.isBlank()) return "Not reported"
    return raw.split(",")
        .mapNotNull { it.trim().toIntOrNull() }
        .distinct()
        .sortedWith(compareBy<Int> {
            when (it) {
                2, 0 -> 0
                6 -> 1
                7 -> 2
                8 -> 3
                3 -> 4
                4 -> 5
                5 -> 6
                else -> 99
            }
        }.thenBy { it })
        .joinToString(" · ") { seatLevelLabel(it) }
        .ifBlank { "Not reported" }
}


@Composable
private fun TireStatusSection(
    warning: TirePressure?,
    pressure: TirePressureStatus?,
    timeZoneMode: String,
    timeFormat: String
) {
    ControlSection(title = "Tires") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            latestTireReadingTime(pressure)?.let { readingTime ->
                LastReadingRow(
                    value = formatHyundaiTimestamp(readingTime, timeZoneMode, timeFormat)
                )
            }

            TireStatusRow("Front Left", warning?.frontLeft, pressure?.frontLeftPsi)
            TireStatusRow("Front Right", warning?.frontRight, pressure?.frontRightPsi)
            TireStatusRow("Rear Left", warning?.rearLeft, pressure?.rearLeftPsi)
            TireStatusRow("Rear Right", warning?.rearRight, pressure?.rearRightPsi)
        }
    }
}

@Composable
private fun TireStatusRow(
    label: String,
    warningCode: Int?,
    psi: Int?
) {
    val isLow = warningCode == 1
    val state = if (isLow) "Low" else "OK"
    val pressureText = psi
        ?.takeIf { it > 0 }
        ?.let { "$it PSI" }
        ?: "No pressure telemetry"
    StatusRow(
        Icons.Filled.Circle,
        label,
        "$state · $pressureText",
        if (isLow) ErrorRed else SuccessGreen
    )
}

private fun latestTireReadingTime(pressure: TirePressureStatus?): String? = pressure?.let { tire ->
    listOf(tire.frontLeftTime, tire.frontRightTime, tire.rearLeftTime, tire.rearRightTime)
        .filter { it.isNotBlank() }
        .maxOrNull()
}

private fun commandStatusColor(name: String): Color = when (name.uppercase()) {
    "SUCCESS" -> SuccessGreen
    "ERROR" -> ErrorRed
    "LOADING" -> ChargingGreen
    else -> Color(0xFFD0D6E0).copy(alpha = 0.5f)
}

private fun formatHyundaiDate(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    if (digits.length < 8) return raw
    val date = "${digits.substring(0, 4)}-${digits.substring(4, 6)}-${digits.substring(6, 8)}"
    if (digits.length < 12) return date
    val hour = digits.substring(8, 10)
    val minute = digits.substring(10, 12)
    return "$date $hour:$minute"
}

@Composable
private fun OdometerUpdatedRow(value: String) {
    LastReadingRow(value = value)
}

@Composable
private fun LastReadingRow(value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.widthIn(min = 88.dp)
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Last Read",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun StatusRow(icon: ImageVector, label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1.75f)
                .widthIn(min = 208.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            modifier = Modifier.weight(0.95f),
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun DigitalKeyStatusRow(value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.widthIn(min = 116.dp)
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Digital Key",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}
