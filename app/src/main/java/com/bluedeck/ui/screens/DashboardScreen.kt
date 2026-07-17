package com.bluedeck.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bluedeck.R
import com.bluedeck.data.models.ClimatePreset
import com.bluedeck.data.models.ClimatePresetsStore
import com.bluedeck.data.models.ClimateSeatSettings
import com.bluedeck.data.models.CommandHistoryEntry
import com.bluedeck.data.models.MAX_CLIMATE_DURATION_MINUTES
import com.bluedeck.data.models.MIN_CLIMATE_DURATION_MINUTES
import com.bluedeck.data.models.Vehicle
import com.bluedeck.data.models.VehicleFeatureCapabilities
import com.bluedeck.data.models.VehicleStatusData
import com.bluedeck.data.models.clampClimateSeatSettings
import com.bluedeck.data.models.coerceClimateDurationMinutes
import com.bluedeck.data.models.hasFuelTelemetryFor
import com.bluedeck.data.models.resolveCapabilities
import com.bluedeck.ui.components.CommandStatusBanner
import com.bluedeck.ui.theme.*
import com.bluedeck.viewmodel.CommandStatus
import com.bluedeck.viewmodel.VehicleViewModel

import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SeatHeatOrange = Color(0xFFFF9800)
private val SeatVentBlue = Color(0xFF03A9F4)

private const val DASHBOARD_REFRESH_INTERVAL_MS = 60_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    vehicleViewModel: VehicleViewModel,
    onNavigateToControls: () -> Unit,
    onNavigateToStatus: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToRemoteStart: () -> Unit,
    onNavigateToEVCharging: () -> Unit,
    onNavigateToLocation: () -> Unit,
    onNavigateToValetMode: () -> Unit,
    onNavigateToDriverProfiles: () -> Unit,
    onNavigateToSurroundView: () -> Unit,
    onNavigateToSeatPresets: () -> Unit,
    onNavigateToDigitalKey: () -> Unit,
    onNavigateToObd: () -> Unit = {},
    onLogout: () -> Unit
) {
    val vehicles by vehicleViewModel.vehicles.collectAsStateWithLifecycle()
    val selectedVehicle by vehicleViewModel.selectedVehicle.collectAsStateWithLifecycle()
    val vehicleStatus by vehicleViewModel.vehicleStatus.collectAsStateWithLifecycle()
    val isStatusLoading by vehicleViewModel.isStatusLoading.collectAsStateWithLifecycle()
    val commandState by vehicleViewModel.commandState.collectAsStateWithLifecycle()
    val commandHistory by vehicleViewModel.commandHistory.collectAsStateWithLifecycle()
    val showRecentCommands by vehicleViewModel.showRecentCommands.collectAsStateWithLifecycle()
    val temperatureUnit by vehicleViewModel.temperatureUnit.collectAsStateWithLifecycle()
    val distanceUnit by vehicleViewModel.distanceUnit.collectAsStateWithLifecycle()
    val customDashboardImageUri by vehicleViewModel.customDashboardImageUri.collectAsStateWithLifecycle()
    val region by vehicleViewModel.region.collectAsStateWithLifecycle()
    val remoteStartSettings by vehicleViewModel.remoteStartSettings.collectAsStateWithLifecycle()
    val featureCaps = remember(selectedVehicle, region) {
        selectedVehicle?.resolveCapabilities(region)
    }
    val context = LocalContext.current
    var pendingConfirmation by remember { mutableStateOf<DashboardConfirmationRequest?>(null) }

    val dashboardImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some providers do not grant persistable permissions. The URI is still saved for the current provider permission window.
            }
            vehicleViewModel.setCustomDashboardImage(uri.toString())
        }
    }

    LaunchedEffect(Unit) {
        vehicleViewModel.loadVehicles()
    }

    LaunchedEffect(selectedVehicle?.vin) {
        if (selectedVehicle == null) return@LaunchedEffect

        // Keep the dashboard fresh while it is visible. This coroutine is
        // cancelled automatically when navigating away from the dashboard.
        while (true) {
            delay(DASHBOARD_REFRESH_INTERVAL_MS)
            vehicleViewModel.refreshStatus(forceFromServer = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "BlueDeck",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        selectedVehicle?.let {
                            Text(
                                it.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                actions = {
                    // Vehicle picker if multiple vehicles
                    if (vehicles.size > 1) {
                        var showVehiclePicker by remember { mutableStateOf(false) }
                        IconButton(onClick = { showVehiclePicker = true }) {
                            Icon(Icons.Filled.SwapHoriz, "Switch vehicle", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        if (showVehiclePicker) {
                            VehiclePickerDialog(
                                vehicles = vehicles,
                                selectedVin = selectedVehicle?.vin,
                                onSelect = {
                                    vehicleViewModel.selectVehicle(it)
                                    showVehiclePicker = false
                                },
                                onDismiss = { showVehiclePicker = false }
                            )
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, "Settings", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = commandState.status != CommandStatus.IDLE,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    CommandStatusBanner(
                        commandState = commandState,
                        compact = true
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Vehicle status card
                    item {
                        VehicleStatusCard(
                            vehicle = selectedVehicle,
                            status = vehicleStatus,
                            isLoading = isStatusLoading,
                            distanceUnit = distanceUnit,
                            customDashboardImageUri = customDashboardImageUri,
                            onChooseCustomImage = { dashboardImagePicker.launch(arrayOf("image/*")) },
                            onClearCustomImage = { vehicleViewModel.setCustomDashboardImage(null) },
                            onRefresh = { vehicleViewModel.refreshStatus(forceFromServer = true) },
                            onTapDetails = onNavigateToStatus
                        )
                    }

                    // Quick actions grid
                    item {
                        Text(
                            "Quick Actions",
                            fontSize = 20.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        QuickActionsGrid(
                            vehicle = selectedVehicle,
                            status = vehicleStatus,
                            featureCaps = featureCaps,
                            onLock = {
                                pendingConfirmation = DashboardConfirmationRequest(
                                    title = "Lock doors?",
                                    message = "Lock all doors on ${selectedVehicle?.displayName ?: "this vehicle"}?",
                                    confirmLabel = "Lock",
                                    action = { vehicleViewModel.lockDoors() }
                                )
                            },
                            onUnlock = {
                                pendingConfirmation = DashboardConfirmationRequest(
                                    title = "Unlock doors?",
                                    message = "Unlock ${selectedVehicle?.displayName ?: "this vehicle"}?",
                                    confirmLabel = "Unlock",
                                    action = { vehicleViewModel.unlockDoors() }
                                )
                            },
                            onRemoteStart = {
                                val isEV = selectedVehicle?.isEV == true
                                if (vehicleStatus?.doorsLocked == false) {
                                    pendingConfirmation = DashboardConfirmationRequest(
                                        title = "Lock vehicle first?",
                                        message = "Climate/remote start requires the doors to be locked. Lock ${selectedVehicle?.displayName ?: "this vehicle"} first, then start automatically.",
                                        confirmLabel = "Lock & Start",
                                        action = { vehicleViewModel.lockThenStartEngine() }
                                    )
                                } else {
                                    pendingConfirmation = DashboardConfirmationRequest(
                                        title = if (isEV) "Start climate?" else "Remote start?",
                                        message = if (isEV) {
                                            "Start cabin climate preconditioning for ${selectedVehicle?.displayName ?: "this vehicle"}?"
                                        } else {
                                            "Remote start ${selectedVehicle?.displayName ?: "this vehicle"}?"
                                        },
                                        confirmLabel = if (isEV) "Start Climate" else "Start",
                                        action = { vehicleViewModel.startEngine() }
                                    )
                                }
                            },
                            onStopEngine = {
                                val isEV = selectedVehicle?.isEV == true
                                pendingConfirmation = DashboardConfirmationRequest(
                                    title = if (isEV) "Stop climate?" else "Stop engine?",
                                    message = if (isEV) {
                                        "Stop cabin climate preconditioning for ${selectedVehicle?.displayName ?: "this vehicle"}?"
                                    } else {
                                        "Stop the remote-start session for ${selectedVehicle?.displayName ?: "this vehicle"}?"
                                    },
                                    confirmLabel = if (isEV) "Stop Climate" else "Stop",
                                    action = { vehicleViewModel.stopEngine() }
                                )
                            },
                            onEVCharging = onNavigateToEVCharging,
                            onStartCharging = {
                                pendingConfirmation = DashboardConfirmationRequest(
                                    title = "Start charging?",
                                    message = "Ask ${selectedVehicle?.displayName ?: "this vehicle"} to start charging? This will only work if the vehicle is plugged in and able to accept charge.",
                                    confirmLabel = "Start Charging",
                                    action = { vehicleViewModel.startCharging() }
                                )
                            },
                            onStopCharging = {
                                pendingConfirmation = DashboardConfirmationRequest(
                                    title = "Stop charging?",
                                    message = "Ask ${selectedVehicle?.displayName ?: "this vehicle"} to stop charging?",
                                    confirmLabel = "Stop Charging",
                                    action = { vehicleViewModel.stopCharging() }
                                )
                            }
                        )
                    }

                    // Climate controls moved from Advanced Controls to the main dashboard
                    item {
                        DashboardClimateControls(
                            vehicle = selectedVehicle,
                            status = vehicleStatus,
                            featureCaps = featureCaps,
                            temperatureUnit = temperatureUnit,
                            defaultDurationMinutes = remoteStartSettings.durationMinutes,
                            onStartClimate = { tempF, defrost, heatedSteering, driverSeat, passengerSeat, rearLeftSeat, rearRightSeat, durationMinutes ->
                                if (vehicleStatus?.doorsLocked == false) {
                                    pendingConfirmation = DashboardConfirmationRequest(
                                        title = "Lock vehicle first?",
                                        message = "Climate start requires the doors to be locked. Lock ${selectedVehicle?.displayName ?: "this vehicle"} first, then start climate automatically.",
                                        confirmLabel = "Lock & Start Climate",
                                        action = {
                                            vehicleViewModel.lockThenStartClimate(
                                                tempF = tempF.toString(),
                                                defrost = defrost,
                                                heatedSteering = heatedSteering,
                                                driverSeat = driverSeat,
                                                passengerSeat = passengerSeat,
                                                rearLeftSeat = rearLeftSeat,
                                                rearRightSeat = rearRightSeat,
                                                durationMinutes = durationMinutes
                                            )
                                        }
                                    )
                                } else {
                                    pendingConfirmation = DashboardConfirmationRequest(
                                        title = "Start climate?",
                                        message = buildString {
                                            append("Start cabin climate at ${climateTemperatureLabelFromF(tempF.toString(), temperatureUnit)}")
                                            append(" for $durationMinutes min")
                                            if (defrost) append(" with defrost")
                                            if (heatedSteering) append(" with heated steering wheel")
                                            val seatSummary = listOf(
                                                "Driver" to driverSeat,
                                                "Passenger" to passengerSeat,
                                                "Rear L" to rearLeftSeat,
                                                "Rear R" to rearRightSeat
                                            ).filter { (_, value) -> value != 0 && value != 2 }
                                            if (seatSummary.isNotEmpty()) {
                                                append("\n\nSeats: ")
                                                append(seatSummary.joinToString { (seat, value) -> "$seat ${dashboardSeatClimateLabel(value)}" })
                                            }
                                            append("?")
                                        },
                                        confirmLabel = "Start Climate",
                                        action = {
                                            vehicleViewModel.startClimate(
                                                tempF = tempF.toString(),
                                                defrost = defrost,
                                                heatedSteering = heatedSteering,
                                                driverSeat = driverSeat,
                                                passengerSeat = passengerSeat,
                                                rearLeftSeat = rearLeftSeat,
                                                rearRightSeat = rearRightSeat,
                                                durationMinutes = durationMinutes
                                            )
                                        }
                                    )
                                }
                            },
                            onStopClimate = {
                                pendingConfirmation = DashboardConfirmationRequest(
                                    title = "Stop climate?",
                                    message = "Stop the current climate preconditioning session?",
                                    confirmLabel = "Stop Climate",
                                    action = { vehicleViewModel.stopClimate() }
                                )
                            },
                            onManageSeatPresets = onNavigateToSeatPresets
                        )
                    }

                    // Useful feature tiles
                    item {
                        Text(
                            "Features",
                            fontSize = 20.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        FeatureTilesGrid(
                            featureCaps = featureCaps,
                            onLocation = onNavigateToLocation,
                            onDriverProfiles = onNavigateToDriverProfiles,
                            onDigitalKey = onNavigateToDigitalKey,
                            onValetMode = onNavigateToValetMode,
                            onObd = onNavigateToObd
                        )
                    }

                    if (showRecentCommands && commandHistory.isNotEmpty()) {
                        item {
                            RecentCommandHistoryCard(
                                entries = commandHistory.take(5),
                                onClear = { vehicleViewModel.clearCommandHistory() }
                            )
                        }
                    }
                }
            }

            pendingConfirmation?.let { request ->
                DashboardCommandConfirmationDialog(
                    request = request,
                    onDismiss = { pendingConfirmation = null },
                    onConfirm = {
                        pendingConfirmation = null
                        request.action()
                    }
                )
            }
        }
    }
}

private data class DashboardConfirmationRequest(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val action: () -> Unit
)

@Composable
private fun DashboardCommandConfirmationDialog(
    request: DashboardConfirmationRequest,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = WarningAmber) },
        title = { Text(request.title, fontWeight = FontWeight.Bold) },
        text = { Text(request.message) },
        confirmButton = {
            Button(onClick = onConfirm) { Text(request.confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun RecentCommandHistoryCard(
    entries: List<CommandHistoryEntry>,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Recent Commands",
                    fontSize = 18.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onClear) { Text("Clear") }
            }
            entries.forEach { entry ->
                DashboardCommandHistoryRow(entry = entry)
            }
        }
    }
}

@Composable
private fun DashboardCommandHistoryRow(entry: CommandHistoryEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.045f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (entry.successful) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = if (entry.successful) SuccessGreen else ErrorRed,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            val detail = buildString {
                if (entry.vehicleName.isNotBlank()) append(entry.vehicleName)
                if (entry.detail.isNotBlank()) {
                    if (isNotBlank()) append(" · ")
                    append(entry.detail)
                }
            }
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            dashboardFormatCommandTime(entry.timestampMillis),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun dashboardFormatCommandTime(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return "—"
    return SimpleDateFormat("h:mm a", Locale.US).format(Date(timestampMillis))
}

@Composable
fun VehicleStatusCard(
    vehicle: Vehicle?,
    status: VehicleStatusData?,
    isLoading: Boolean,
    distanceUnit: String = "MI",
    customDashboardImageUri: String? = null,
    onChooseCustomImage: () -> Unit,
    onClearCustomImage: () -> Unit,
    onRefresh: () -> Unit,
    onTapDetails: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTapDetails),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            vehicle?.displayName ?: "No vehicle",
                            fontSize = 25.sp,
                            lineHeight = 27.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                        vehicle?.vin?.let {
                            Text(
                                "VIN: $it",
                                fontSize = 12.sp,
                                lineHeight = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                maxLines = 1
                            )
                        }
                    }
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Filled.Refresh, "Refresh", tint = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }

                VehicleDashboardImage(
                    vehicle = vehicle,
                    customImageUri = customDashboardImageUri,
                    onChooseCustomImage = onChooseCustomImage,
                    onClearCustomImage = onClearCustomImage
                )

                Spacer(Modifier.height(8.dp))

                if (status != null) {
                    val isEV = vehicle?.isEV == true

                    // Vehicle state chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatusChip(
                            label = if (status.doorsLocked) "Locked" else "Unlocked",
                            icon = if (status.doorsLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            tint = if (status.doorsLocked) SuccessGreen else WarningAmber
                        )
                        StatusChip(
                            label = if (isEV) {
                                if (status.airCtrlOn) "Climate On" else "Climate Off"
                            } else {
                                if (status.engineStatus) "Engine On" else "Engine Off"
                            },
                            icon = if (isEV) Icons.Filled.AcUnit else Icons.Filled.PowerSettingsNew,
                            tint = if ((isEV && status.airCtrlOn) || (!isEV && status.engineStatus)) SuccessGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )

                        if (isEV && status.evStatus != null) {
                            val ev = status.evStatus
                            val chargingPowerKw = ev.chargingPowerKw ?: 0.0
                            val isCharging = ev.batteryCharge || chargingPowerKw > 0.0
                            val isPluggedIn = ev.batteryPlugin != 0
                            StatusChip(
                                label = when {
                                    isCharging -> "Charging"
                                    ev.batteryPlugin == 4 -> "Paused"
                                    isPluggedIn -> "Plugged In"
                                    else -> "Unplugged"
                                },
                                icon = Icons.Filled.EvStation,
                                tint = when {
                                    isCharging -> ChargingGreen
                                    isPluggedIn -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                }
                            )
                        } else {
                            StatusChip(
                                label = if (status.airCtrlOn) "Climate On" else "Climate Off",
                                icon = Icons.Filled.AcUnit,
                                tint = if (status.airCtrlOn) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // EV/PHEV or fuel status
                    status.evStatus?.let { ev ->
                        val chargingPowerKw = ev.chargingPowerKw?.takeIf { it > 0.0 }
                        val isActivelyCharging = ev.batteryCharge || chargingPowerKw != null

                        EVStatusBar(
                            batteryLevel = ev.batteryStatus,
                            twelveVoltLevel = status.battery?.batteryLevel,
                            isCharging = isActivelyCharging,
                            rangeMiles = ev.rangeMiles,
                            distanceUnit = distanceUnit,
                            chargingMethodLabel = if (isActivelyCharging) ev.chargingMethodLabel else null,
                            chargingSpeedLabel = chargingPowerKw?.let {
                                "${String.format(java.util.Locale.US, "%.1f", it)} kW"
                            }
                        )

                        if (status.hasFuelTelemetryFor(vehicle)) {
                            Spacer(Modifier.height(8.dp))
                            val reportedFuelLevel = status.normalizedFuelLevelPercent
                            val fuelRangeMiles = status.fuelRangeMiles
                            if (reportedFuelLevel != null) {
                                FuelStatusBar(
                                    fuelLevel = reportedFuelLevel,
                                    rangeMiles = fuelRangeMiles,
                                    distanceUnit = distanceUnit,
                                    label = if (reportedFuelLevel <= 10 || status.lowFuelLight) "Low fuel" else "Fuel"
                                )
                            } else if (fuelRangeMiles > 0.0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.LocalGasStation,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Fuel ${formatDistanceFromMiles(fuelRangeMiles, distanceUnit)} range",
                                        fontSize = 16.sp,
                                        lineHeight = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    } ?: run {
                        val reportedFuelLevel = status.normalizedFuelLevelPercent
                        val fuelRangeMiles = status.fuelRangeMiles
                        if (reportedFuelLevel != null) {
                            FuelStatusBar(
                                fuelLevel = reportedFuelLevel,
                                rangeMiles = fuelRangeMiles,
                                distanceUnit = distanceUnit,
                                label = if (reportedFuelLevel <= 10 || status.lowFuelLight) "Low fuel" else "Fuel"
                            )
                        } else if (fuelRangeMiles > 0.0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.LocalGasStation,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "${formatDistanceFromMiles(fuelRangeMiles, distanceUnit)} range",
                                    fontSize = 16.sp,
                                    lineHeight = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        "Pull status to see vehicle info",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        fontSize = 16.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onRefresh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Refresh Status", color = MaterialTheme.colorScheme.secondary)
                    }
                }
        }
    }
}

@Composable
private fun VehicleDashboardImage(
    vehicle: Vehicle?,
    customImageUri: String?,
    onChooseCustomImage: () -> Unit,
    onClearCustomImage: () -> Unit
) {
    val hasCustomImage = !customImageUri.isNullOrBlank()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (hasCustomImage) "Custom image" else vehicleImageLabel(vehicle),
                fontSize = 12.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DashboardImagePillButton(
                    label = if (hasCustomImage) "Change" else "Custom image",
                    icon = Icons.Filled.PhotoCamera,
                    onClick = onChooseCustomImage
                )
                if (hasCustomImage) {
                    DashboardImagePillButton(
                        label = "Reset",
                        icon = Icons.Filled.Restore,
                        onClick = onClearCustomImage
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            if (hasCustomImage) {
                AndroidView(
                    factory = { context ->
                        ImageView(context).apply {
                            adjustViewBounds = true
                            scaleType = ImageView.ScaleType.FIT_CENTER
                        }
                    },
                    update = { imageView ->
                        imageView.setImageURI(Uri.parse(customImageUri))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            } else {
                Image(
                    painter = painterResource(id = vehicleImageResource(vehicle)),
                    contentDescription = vehicle?.let { "${it.displayName} vehicle image" } ?: "Vehicle image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
private fun DashboardImagePillButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.Black.copy(alpha = 0.5f),
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp))
            Text(
                label,
                fontSize = 11.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

private enum class DashboardVehicleImageProfile(
    val drawableRes: Int,
    val label: String
) {
    IONIQ_9(R.drawable.vehicle_ioniq_9_blue, "IONIQ 9"),
    IONIQ_5(R.drawable.vehicle_ioniq_5_blue, "IONIQ 5"),
    IONIQ_6(R.drawable.vehicle_ioniq_6_black, "IONIQ 6"),
    KONA_ELECTRIC(R.drawable.vehicle_kona_orange, "Kona Electric"),
    ELANTRA(R.drawable.vehicle_elantra_blue, "Elantra"),
    SONATA(R.drawable.vehicle_sonata_white, "Sonata"),
    TUCSON(R.drawable.vehicle_tucson_red, "Tucson"),
    VENUE(R.drawable.vehicle_generic_blue, "Venue"),
    KIA_EV6(R.drawable.vehicle_ev_generic_blue, "Kia EV6"),
    KIA_EV9(R.drawable.vehicle_ev_generic_blue, "Kia EV9"),
    KIA_K4(R.drawable.vehicle_generic_blue, "Kia K4"),
    KIA_K5(R.drawable.vehicle_generic_blue, "Kia K5"),
    KIA_SELTOS(R.drawable.vehicle_generic_blue, "Kia Seltos"),
    KIA_TELLURIDE(R.drawable.vehicle_generic_blue, "Kia Telluride"),
    KIA_CARNIVAL(R.drawable.vehicle_generic_blue, "Kia Carnival"),
    KIA_SORENTO(R.drawable.vehicle_generic_blue, "Kia Sorento"),
    KIA_SPORTAGE(R.drawable.vehicle_generic_blue, "Kia Sportage"),
    SANTA_FE(R.drawable.vehicle_santa_fe_red, "Santa Fe"),
    PALISADE(R.drawable.vehicle_palisade_blue, "Palisade"),
    SANTA_CRUZ(R.drawable.vehicle_santa_cruz_blue, "Santa Cruz"),
    GENERIC_EV(R.drawable.vehicle_ev_generic_blue, "EV image"),
    GENERIC(R.drawable.vehicle_generic_blue, "Vehicle image")
}

private fun vehicleModelLookupText(vehicle: Vehicle?): String = listOf(
    vehicle?.modelCode.orEmpty(),
    vehicle?.modelName.orEmpty(),
    vehicle?.displayName.orEmpty(),
    vehicle?.hmaModel.orEmpty()
).joinToString(" ")

private fun normalizedVin(vehicle: Vehicle?): String = vehicle?.vin
    .orEmpty()
    .trim()
    .uppercase(Locale.US)

private fun vehicleVds(vin: String): String = vin.drop(3).take(5)

private fun String.containsAnyVehicleText(vararg values: String): Boolean =
    values.any { contains(it, ignoreCase = true) }

private fun vehicleVinMatches(vin: String, wmiPrefixes: Set<String>, vdsCodes: Set<String>): Boolean {
    if (vin.length < 8) return false
    return wmiPrefixes.any { vin.startsWith(it) } && vehicleVds(vin) in vdsCodes
}

private fun dashboardVehicleImageProfile(vehicle: Vehicle?): DashboardVehicleImageProfile {
    val vin = normalizedVin(vehicle)
    val modelText = vehicleModelLookupText(vehicle)

    return when {
        isIoniq9(vin, modelText) -> DashboardVehicleImageProfile.IONIQ_9
        isIoniq5(vin, modelText) -> DashboardVehicleImageProfile.IONIQ_5
        isIoniq6(vin, modelText) -> DashboardVehicleImageProfile.IONIQ_6
        isKonaElectric(vin, modelText) -> DashboardVehicleImageProfile.KONA_ELECTRIC
        isSantaCruz(vin, modelText) -> DashboardVehicleImageProfile.SANTA_CRUZ
        isPalisade(vin, modelText) -> DashboardVehicleImageProfile.PALISADE
        isSantaFe(vin, modelText) -> DashboardVehicleImageProfile.SANTA_FE
        isTucson(vin, modelText) -> DashboardVehicleImageProfile.TUCSON
        isVenue(vin, modelText) -> DashboardVehicleImageProfile.VENUE
        isKiaEv9(vin, modelText) -> DashboardVehicleImageProfile.KIA_EV9
        isKiaEv6(vin, modelText) -> DashboardVehicleImageProfile.KIA_EV6
        isKiaK4(vin, modelText) -> DashboardVehicleImageProfile.KIA_K4
        isKiaK5(vin, modelText) -> DashboardVehicleImageProfile.KIA_K5
        isKiaTelluride(vin, modelText) -> DashboardVehicleImageProfile.KIA_TELLURIDE
        isKiaCarnival(vin, modelText) -> DashboardVehicleImageProfile.KIA_CARNIVAL
        isKiaSorento(vin, modelText) -> DashboardVehicleImageProfile.KIA_SORENTO
        isKiaSportage(vin, modelText) -> DashboardVehicleImageProfile.KIA_SPORTAGE
        isKiaSeltos(vin, modelText) -> DashboardVehicleImageProfile.KIA_SELTOS
        isElantra(vin, modelText) -> DashboardVehicleImageProfile.ELANTRA
        isSonata(vin, modelText) -> DashboardVehicleImageProfile.SONATA
        modelText.contains("IONIQ", ignoreCase = true) || vehicle?.isEV == true -> DashboardVehicleImageProfile.GENERIC_EV
        else -> DashboardVehicleImageProfile.GENERIC
    }
}

private fun isIoniq9(vin: String, modelText: String): Boolean =
    vin.startsWith("7YAMU") || modelText.contains("IONIQ 9", ignoreCase = true)

private fun isIoniq5(vin: String, modelText: String): Boolean =
    vin.startsWith("KM8KM") ||
            vin.startsWith("KM8KN") ||
            vin.startsWith("KM8KR") ||
            modelText.contains("IONIQ 5", ignoreCase = true)

private fun isIoniq6(vin: String, modelText: String): Boolean =
    vin.startsWith("KMHM") || modelText.contains("IONIQ 6", ignoreCase = true)

private fun isKonaElectric(vin: String, modelText: String): Boolean =
    vehicleVinMatches(vin, setOf("KM8"), KONA_ELECTRIC_VDS_CODES) ||
            (modelText.contains("KONA", ignoreCase = true) &&
                    modelText.containsAnyVehicleText("EV", "ELECTRIC"))

private fun isElantra(vin: String, modelText: String): Boolean =
    vehicleVinMatches(vin, setOf("KMH", "5NP"), ELANTRA_VDS_CODES) ||
            modelText.contains("ELANTRA", ignoreCase = true)

private fun isSonata(vin: String, modelText: String): Boolean =
    vehicleVinMatches(vin, setOf("KMH", "5NP"), SONATA_VDS_CODES) ||
            modelText.contains("SONATA", ignoreCase = true)

private fun isTucson(vin: String, modelText: String): Boolean =
    vehicleVinMatches(vin, setOf("KM8", "5NM"), TUCSON_VDS_CODES) ||
            modelText.contains("TUCSON", ignoreCase = true)

private fun isSantaFe(vin: String, modelText: String): Boolean =
    vehicleVinMatches(vin, setOf("KM8", "KMH", "5NM", "5XY", "TMA"), SANTA_FE_VDS_CODES) ||
            modelText.contains("SANTA FE", ignoreCase = true) ||
            modelText.contains("SANTAFE", ignoreCase = true)

private fun isVenue(vin: String, modelText: String): Boolean =
    vehicleVinMatches(vin, setOf("KMH"), VENUE_VDS_CODES) ||
            modelText.contains("VENUE", ignoreCase = true)

private fun isKiaEv6(vin: String, modelText: String): Boolean =
    vehicleVinMatches(vin, KIA_WMI_PREFIXES, KIA_EV6_VDS_CODES) ||
            modelText.containsAnyVehicleText("EV6", "EV 6")

private fun isKiaEv9(vin: String, modelText: String): Boolean =
    vehicleVinMatches(vin, KIA_WMI_PREFIXES, KIA_EV9_VDS_CODES) ||
            modelText.containsAnyVehicleText("EV9", "EV 9")

private fun isKiaK4(vin: String, modelText: String): Boolean =
    vehicleVinMatches(vin, KIA_WMI_PREFIXES, KIA_K4_VDS_CODES) ||
            modelText.contains("K4", ignoreCase = true)

private fun isKiaK5(vin: String, modelText: String): Boolean =
    vehicleVinMatches(vin, KIA_WMI_PREFIXES, KIA_K5_VDS_CODES) ||
            modelText.contains("K5", ignoreCase = true)

private fun isKiaSeltos(vin: String, modelText: String): Boolean =
    vehicleVinMatches(vin, KIA_WMI_PREFIXES, KIA_SELTOS_VDS_CODES) ||
            modelText.contains("SELTOS", ignoreCase = true)

private fun isKiaTelluride(vin: String, modelText: String): Boolean =
    vehicleVinMatches(vin, KIA_WMI_PREFIXES, KIA_TELLURIDE_VDS_CODES) ||
            modelText.contains("TELLURIDE", ignoreCase = true)

private fun isKiaCarnival(vin: String, modelText: String): Boolean =
    vehicleVinMatches(vin, KIA_WMI_PREFIXES, KIA_CARNIVAL_VDS_CODES) ||
            modelText.contains("CARNIVAL", ignoreCase = true)

private fun isKiaSorento(vin: String, modelText: String): Boolean =
    vehicleVinMatches(vin, KIA_WMI_PREFIXES, KIA_SORENTO_VDS_CODES) ||
            modelText.contains("SORENTO", ignoreCase = true)

private fun isKiaSportage(vin: String, modelText: String): Boolean =
    vehicleVinMatches(vin, KIA_WMI_PREFIXES, KIA_SPORTAGE_VDS_CODES) ||
            modelText.contains("SPORTAGE", ignoreCase = true)

private fun isPalisade(vin: String, modelText: String): Boolean =
    vehicleVinMatches(vin, setOf("KM8"), PALISADE_VDS_CODES) ||
            modelText.contains("PALISADE", ignoreCase = true)

private fun isSantaCruz(vin: String, modelText: String): Boolean =
    vehicleVinMatches(vin, setOf("5NT"), SANTA_CRUZ_VDS_CODES) ||
            modelText.contains("SANTA CRUZ", ignoreCase = true)

private fun vehicleImageResource(vehicle: Vehicle?): Int =
    dashboardVehicleImageProfile(vehicle).drawableRes

private fun vehicleImageLabel(vehicle: Vehicle?): String =
    dashboardVehicleImageProfile(vehicle).label

private val KONA_ELECTRIC_VDS_CODES = setOf(
    "K53AG", "K33AG", "K23AG", "HC3AB", "HE3A3", "HC3A6", "HB3AB", "HB3A7", "HE3A6"
)

private val ELANTRA_VDS_CODES = setOf(
    "JF24M", "JF34M", "JW24M", "JF25F", "JF35F", "JW35F", "DN45D", "DN55D", "DM55D", "DN46D",
    "DN56D", "DM46D", "DM56D", "DT46D", "DU46D", "DU45D", "DC86E", "DU4AD", "DU4BD", "DC8AE",
    "DB8BE", "DH4AE", "DB8AE", "D25LE", "DH6AE", "D35LE", "DH6AH", "DH4AH", "D35LH", "D25LH",
    "D84CF", "D84LF", "D74LF", "D94LA", "D04LB", "D04CB", "D74CF", "LM4AJ", "LM4AG", "LP4AG",
    "LN4AG", "LN4AJ", "LS4AG", "LL4AG", "LR4AF", "LM4DJ", "LM4DG", "LS4DG", "LL4DG", "LP4DG",
    "LN4DJ", "LR4DF"
)

private val SONATA_VDS_CODES = setOf(
    "BF22S", "CF24T", "CF24F", "WF25V", "WF35V", "WF25S", "WF35H", "WF25H", "ET46F", "EU46C",
    "ET46C", "EU46F", "ET4AC", "EU4AC", "EU4AF", "EC4AC", "EC4A4", "EB4AC", "EC4AB", "E34AF",
    "E34AB", "E24AF", "E24AA", "E34L1", "E14L2", "E24L1", "E54L2", "E24L3", "E34L3", "L54JJ",
    "EJ4A2", "EJ4J2", "EL4JA", "EG4JA", "EH4J2", "L24JJ", "EF4JA", "L34JJ", "L64JA", "EK4JC",
    "L14JA", "L34J2", "EL4J2", "L24JA", "L44J2", "L14JC", "L44JA", "L24AJ", "L54JC"
)

private val TUCSON_VDS_CODES = setOf(
    "JM12B", "JM12D", "JM72B", "JN12D", "JN72D", "JM72D", "JN12B", "JT3AC", "JU3AC", "JUCAC",
    "JT3AB", "JU3AG", "JT3AF", "JTCAF", "JUCAG", "J33A2", "J23A4", "J33A4", "J3CA2", "J3CA4",
    "J33AL", "J3CAL", "J2CA4", "JACAE", "JE3AE", "JFCA1", "JC3AE", "JFDA2", "JBCAE", "JFCAE",
    "JBDA2", "JBCA1", "JECAE", "JA3AE", "JB3AE", "JECA1", "JF3AE", "JCCAE", "JBCDE", "JF3DE",
    "JB3DE", "JFCDE", "JA3DE", "JBCD1", "JBDD2", "JE3DE", "JCCDE", "JECD1", "JACDE", "JECDE",
    "JFCD1", "JCCD1"
)

private val VENUE_VDS_CODES = setOf(
    "RB8A3", "RC8A3"
)

private val KIA_WMI_PREFIXES = setOf(
    "KNA", "KNC", "KND", "KNE", "KNG", "KNM", "KNN", "5XY", "5XX", "U5Y", "XWE"
)

private val KIA_EV6_VDS_CODES = setOf(
    "C44LA", "C34LB", "C34LA", "C3DLC", "C4DLC", "C5DLE"
)

private val KIA_EV9_VDS_CODES = setOf(
    "AEFS5", "ADFS5", "AFFS5", "AB5S1", "AA5S2"
)

private val KIA_K4_VDS_CODES = setOf(
    "F34AD", "F54AD", "F24AD", "F44AC"
)

private val KIA_K5_VDS_CODES = setOf(
    "G44J8", "G14J2", "G34J2", "G24J2", "G64J2", "G64A2"
)

private val KIA_SELTOS_VDS_CODES = setOf(
    "ERCAA", "ETCA2", "EUCA2", "EUCAA", "EU2AA", "EPCAA", "EP2AA", "ER2AA", "EUCA7", "ETCA7"
)

private val KIA_TELLURIDE_VDS_CODES = setOf(
    "P64HC", "P54HC", "P24HC", "P2DHC", "P5DHC", "P34HC", "P3DHC", "P6DHC", "P54GC", "P2DGC",
    "P64GC", "P6DGC", "P3DGC", "P5DGC", "P24GC", "P34GC"
)

private val KIA_CARNIVAL_VDS_CODES = setOf(
    "NB5H3", "NB4H3", "NC5H3", "NE5H3"
)

private val KIA_SORENTO_VDS_CODES = setOf(
    "JC733", "JD733", "JD736", "JC736", "JD735", "JC735", "KUDA2", "KU4A1", "KUDA1", "KU4A2",
    "KTDA1", "KU3A1", "KWDA2", "KT4A1", "KT3A1", "KT4A2", "KTCA1", "KTDA2", "KUCA1", "KW4A2",
    "KT3A2", "KTCA6", "KTDA6", "KUCA6", "KT4A6", "KUDA6", "KU3A6", "KT3A6", "KU4A6", "KU3A2",
    "KUDA7", "KU4A7", "KW4A7", "KT4A7", "KWDA7", "KTDA7", "PG4A3", "PK4A1", "PK4A5", "PGDA3",
    "PH4A5", "PG4A5", "PHDA1", "PH4A1", "PKDA1", "PGDA1", "PKDA5", "PGDA5", "PHDA5", "PHDA3",
    "RG4LC", "RK4LF", "RKDLF", "RL4LC", "RGDLF", "RGDLC", "RH4LG", "RG4LG", "RH4LF", "RLDLC",
    "RHDLF", "RGDCG", "RJDLH", "RHDCF", "RGDLG", "RKDCF", "RMDLH", "RHDLG", "RMDLG", "RKDLG"
)

private val KIA_SPORTAGE_VDS_CODES = setOf(
    "JA721", "JB723", "JA723", "JB623", "JA623", "JF724", "JF723", "JE724", "JE723", "JF722",
    "KH3A3", "KG3A3", "KGCA3", "KGCA4", "KG3A4", "KHCA3", "PCCA6", "PC3A6", "PC3A2", "PCCA2",
    "PBCA2", "PB3A2", "PC3AC", "PCCAC", "PBCAC", "PB3AC", "PMCAC", "PR3A6", "PRCA6", "PM3AC",
    "PR3NC", "PNCAC", "PN3AC", "P63AC", "P6CAC", "K43AF", "PXCAG", "K4CAF", "PVCAF", "K33AF",
    "PU3AG", "K7CAF", "K3CAF", "PUCAG", "K2CAF", "PVCAG", "PV3AF", "PYDAH", "PU3AF", "K5CAF",
    "K53AF", "K23AF", "PZDAH", "K6CAF", "PUCAF", "PV3DF", "K3CDF", "PXCDG", "PU3DF", "PVCDG",
    "K33DF", "K53DF", "K5CDF", "K43DF", "PVCDF", "PYDDH", "K6CDF", "PUCDF", "K7CDF", "PUCDG",
    "PU3DG", "PZDDH"
)

private val SANTA_FE_VDS_CODES = setOf(
    "SB82B", "SC83D", "SC73D", "SB73D", "SC13D", "SB12B", "SC13E", "SC73E", "SB73E", "SB13D",
    "SG13D", "SG73D", "SH13E", "SG13E", "SG73E", "SH73E", "SG3AB", "SK4AG", "SKDAG", "SHDAG",
    "SK3AB", "SH4AG", "SG4AG", "SGDAB", "ZKDAG", "ZK4AG", "ZGDAG", "ZHDAG", "ZGDAB", "ZG3AB",
    "ZK3AB", "ZH4AG", "ZG4AG", "ZTDLB", "SN4HF", "ZWDLA", "ZT3CB", "ZUDLB", "SRDHF", "SNDHF",
    "ZU3LA", "ZU3LB", "SM4HF", "ZUDLA", "SMDHF", "SR4HF", "ZT3LB", "ZW3LA", "ZW3CA", "ZU3CB",
    "ZW4LA", "ZUDCB", "ZU4LA", "S5CAD", "S3CAA", "S3CAD", "S53AA", "S53AD", "S33AD", "S5CAA",
    "S2CAD", "S23AD", "S23JD", "S33AA", "S24AJ", "S44AL", "S2DA1", "S3DAJ", "S34AJ", "S64AJ",
    "S5DA1", "S2DAJ", "S3DA1", "S6DAJ", "S1DAJ", "S54AL", "S14AJ", "S4DAL", "S5DAL", "S7DA2",
    "S3DAL", "S6DA2"
)

private val PALISADE_VDS_CODES = setOf(
    "R34HE", "R4DHE", "R14HE", "R3DHE", "R24HE", "R54HE", "R5DHE", "R1DHE", "R44HE", "R2DHE",
    "R74HE", "R7DHE", "R1DGE", "R5DGE", "R14GE", "R54GE", "R3DGE", "R24GE", "R74GE", "R2DGE",
    "R34GE", "R44GE", "R4DGE", "R7DGE"
)

private val SANTA_CRUZ_VDS_CODES = setOf(
    "JEDAF", "JDDAF", "JADAE", "JCDAE", "JBDAE", "JA4AE", "JB4AE", "JC4AE", "JCDAF", "JA4DE",
    "JCDDE", "JCDDF", "JB4DE", "JEDDF", "JADDE", "JBDDE", "JC4DE", "JDDDF"
)


@Composable
fun StatusChip(label: String, icon: ImageVector, tint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            fontSize = 12.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            maxLines = 1
        )
    }
}



@Composable
fun EVStatusBar(
    batteryLevel: Int,
    twelveVoltLevel: Int? = null,
    isCharging: Boolean,
    rangeMiles: Double = 0.0,
    distanceUnit: String = "MI",
    chargingMethodLabel: String? = null,
    chargingSpeedLabel: String? = null
) {
    val panelIsCharging = isCharging || !chargingSpeedLabel.isNullOrBlank()

    AnimatedBatteryFillPanel(
        batteryLevel = batteryLevel,
        isCharging = panelIsCharging,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                BatteryStatusLeadIcon(
                    batteryLevel = batteryLevel,
                    isCharging = panelIsCharging,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (rangeMiles > 0) "$batteryLevel% • ${formatDistanceFromMiles(rangeMiles, distanceUnit)}" else "$batteryLevel%",
                        fontSize = 40.sp,
                        lineHeight = 42.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    twelveVoltLevel?.takeIf { it > 0 }?.let { soc ->
                        Spacer(Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Battery5Bar,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "12V $soc%",
                                fontSize = 13.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (panelIsCharging) {
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = ChargingGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = listOfNotNull(chargingMethodLabel ?: "Charging", chargingSpeedLabel).joinToString(" • "),
                        fontSize = 13.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ChargingGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun FuelStatusBar(
    fuelLevel: Int,
    rangeMiles: Double = 0.0,
    distanceUnit: String = "MI",
    label: String = "Fuel"
) {
    val clampedFuelLevel = fuelLevel.coerceIn(0, 100)
    val lowFuel = clampedFuelLevel <= 10
    val iconTint = when {
        clampedFuelLevel <= 5 -> ErrorRed
        lowFuel -> WarningAmber
        else -> MaterialTheme.colorScheme.onSurface
    }

    AnimatedBatteryFillPanel(
        batteryLevel = clampedFuelLevel,
        isCharging = false,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Filled.LocalGasStation,
                    contentDescription = if (lowFuel) "Low fuel warning" else null,
                    tint = iconTint,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (rangeMiles > 0) "$clampedFuelLevel% • ${formatDistanceFromMiles(rangeMiles, distanceUnit)}" else "$clampedFuelLevel%",
                        fontSize = 40.sp,
                        lineHeight = 42.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (lowFuel) "Low fuel" else label,
                        fontSize = 13.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (lowFuel) iconTint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun BatteryStatusLeadIcon(
    batteryLevel: Int,
    isCharging: Boolean,
    modifier: Modifier = Modifier
) {
    val warningTint = when {
        batteryLevel <= 10 -> ErrorRed
        batteryLevel <= 20 -> WarningAmber
        else -> null
    }

    if (warningTint != null) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = if (batteryLevel <= 10) "Critical battery warning" else "Low battery warning",
            tint = warningTint,
            modifier = modifier
        )
    } else {
        Icon(
            if (isCharging) Icons.Filled.BatteryChargingFull else Icons.Filled.Battery5Bar,
            contentDescription = null,
            tint = if (isCharging) ChargingGreen else MaterialTheme.colorScheme.onSurface,
            modifier = modifier
        )
    }
}

@Composable
private fun AnimatedBatteryFillPanel(
    batteryLevel: Int,
    isCharging: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val clampedBatteryLevel = batteryLevel.coerceIn(0, 100)
    val clampedProgress = clampedBatteryLevel / 100f
    val fillColor = batteryChargeLevelColor(clampedBatteryLevel)
    val fillStartColor = blendBatteryColors(fillColor, Color.Black, 0.18f)
    val fillEndColor = blendBatteryColors(fillColor, Color.White, 0.20f)

    val transition = rememberInfiniteTransition(label = "batteryPanelFill")
    val sweepProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "batteryPanelSweep"
    )
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.24f,
        targetValue = 0.52f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "batteryPanelPulse"
    )

    val cornerRadius = 18.dp
    val panelBaseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.065f)
    val sweepEdgeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    val sweepTrailingColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    val glossTopColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(panelBaseColor)
            .drawWithContent {
                val rawFilledWidth = size.width * clampedProgress
                val filledWidth = if (rawFilledWidth > 0f) rawFilledWidth.coerceAtLeast(10.dp.toPx()).coerceAtMost(size.width) else 0f
                val radiusPx = cornerRadius.toPx()

                if (filledWidth > 0f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                fillStartColor.copy(alpha = if (isCharging) 0.70f else 0.56f),
                                fillColor.copy(alpha = if (isCharging) 0.66f else 0.52f),
                                fillEndColor.copy(alpha = if (isCharging) 0.58f else 0.44f)
                            ),
                            startX = 0f,
                            endX = filledWidth
                        ),
                        size = Size(filledWidth, size.height),
                        cornerRadius = CornerRadius(radiusPx, radiusPx)
                    )

                    if (isCharging) {
                        clipRect(left = 0f, top = 0f, right = filledWidth, bottom = size.height) {
                            drawRoundRect(
                                color = ChargingGreen.copy(alpha = pulseAlpha),
                                size = Size(filledWidth, size.height),
                                cornerRadius = CornerRadius(radiusPx, radiusPx)
                            )

                            val sweepWidth = 96.dp.toPx()
                            val sweepX = (filledWidth + sweepWidth) * sweepProgress - sweepWidth
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        sweepEdgeColor,
                                        Color(0xFFC8FFD8).copy(alpha = 0.92f),
                                        sweepTrailingColor,
                                        Color.Transparent
                                    ),
                                    startX = sweepX,
                                    endX = sweepX + sweepWidth
                                ),
                                topLeft = Offset(sweepX, 0f),
                                size = Size(sweepWidth, size.height)
                            )
                        }
                    }
                }

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            glossTopColor,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.12f)
                        )
                    ),
                    size = size,
                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                )

                drawContent()
            }
    ) {
        content()
    }
}

private fun batteryChargeLevelColor(level: Int): Color {
    val clamped = level.coerceIn(0, 100)
    val brightRed = Color(0xFFFF1744)
    val orange = Color(0xFFFF9500)
    val yellow = Color(0xFFFFD60A)
    val brightGreen = Color(0xFF30D158)
    val lightBlue = Color(0xFF64D2FF)

    return when {
        clamped <= 10 -> brightRed
        clamped < 20 -> blendBatteryColors(brightRed, orange, (clamped - 10) / 10f)
        clamped < 30 -> blendBatteryColors(orange, yellow, (clamped - 20) / 10f)
        clamped < 50 -> blendBatteryColors(yellow, brightGreen, (clamped - 30) / 20f)
        clamped <= 80 -> brightGreen
        else -> blendBatteryColors(brightGreen, lightBlue, (clamped - 80) / 20f)
    }
}

private fun blendBatteryColors(start: Color, end: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * t,
        green = start.green + (end.green - start.green) * t,
        blue = start.blue + (end.blue - start.blue) * t,
        alpha = start.alpha + (end.alpha - start.alpha) * t
    )
}

@Composable
private fun DashboardQuickActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    compact: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(tint.copy(alpha = if (enabled) 0.16f else 0.05f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                vertical = if (compact) 10.dp else 16.dp,
                horizontal = if (compact) 6.dp else 8.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) tint else tint.copy(alpha = 0.3f),
            modifier = Modifier.size(if (compact) 22.dp else 28.dp)
        )
        Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
        Text(
            text = label,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            fontSize = if (compact) 12.sp else 13.sp,
            lineHeight = if (compact) 13.sp else 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
fun QuickActionsGrid(
    vehicle: Vehicle?,
    status: VehicleStatusData?,
    featureCaps: VehicleFeatureCapabilities?,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onRemoteStart: () -> Unit,
    onStopEngine: () -> Unit,
    onEVCharging: () -> Unit,
    onStartCharging: () -> Unit,
    onStopCharging: () -> Unit
) {
    val isEV = vehicle?.isEV == true
    val engineOn = if (isEV) status?.airCtrlOn == true else status?.engineStatus == true

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DashboardQuickActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Lock,
                label = "Lock",
                tint = SuccessGreen,
                onClick = onLock,
                compact = true
            )
            DashboardQuickActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.LockOpen,
                label = "Unlock",
                tint = WarningAmber,
                onClick = onUnlock,
                compact = true
            )
            DashboardQuickActionButton(
                modifier = Modifier.weight(1f),
                icon = if (engineOn) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                label = when {
                    isEV && engineOn -> "Stop"
                    isEV -> "Climate"
                    engineOn -> "Stop"
                    else -> "Start"
                },
                tint = if (engineOn) ErrorRed else MaterialTheme.colorScheme.secondary,
                onClick = if (engineOn) onStopEngine else onRemoteStart,
                compact = true
            )
        }
        if (isEV && featureCaps?.showEvCharging != false) {
            val ev = status?.evStatus
            val plugCode = ev?.batteryPlugin ?: 0
            val isPluggedIn = plugCode != 0
            val isCharging = ev?.batteryCharge == true || ((ev?.chargingPowerKw ?: 0.0) > 0.0)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DashboardQuickActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.EvStation,
                    label = "Charging",
                    tint = ChargingGreen,
                    onClick = onEVCharging,
                    compact = true
                )

                if (isPluggedIn) {
                    DashboardQuickActionButton(
                        modifier = Modifier.weight(1f),
                        icon = if (isCharging) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        label = if (isCharging) "Stop Charge" else "Start Charge",
                        tint = if (isCharging) ErrorRed else ChargingGreen,
                        onClick = if (isCharging) onStopCharging else onStartCharging,
                        compact = true
                    )
                }
            }
        }
    }
}

@Composable
fun VehiclePickerDialog(
    vehicles: List<Vehicle>,
    selectedVin: String?,
    onSelect: (Vehicle) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Vehicle") },
        text = {
            Column {
                vehicles.forEach { v ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(v) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = v.vin == selectedVin,
                            onClick = { onSelect(v) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(v.displayName, fontWeight = FontWeight.Medium)
                            Text(
                                v.vin,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (v != vehicles.last()) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}


@Composable
fun DashboardClimateControls(
    vehicle: Vehicle?,
    status: VehicleStatusData?,
    featureCaps: VehicleFeatureCapabilities?,
    temperatureUnit: String,
    defaultDurationMinutes: Int,
    onStartClimate: (Int, Boolean, Boolean, Int, Int, Int, Int, Int) -> Unit,
    onStopClimate: () -> Unit,
    onManageSeatPresets: () -> Unit
) {
    val isEV = vehicle?.isEV == true
    val climateOn = status?.airCtrlOn == true
    val statusDisplayTemp = status?.airTemp?.let { apiTemperatureToPreferredValue(it.value, it.unit, temperatureUnit) }
    var defrost by remember { mutableStateOf(false) }
    var heatedSteering by remember { mutableStateOf(false) }
    var displayTemp by remember(temperatureUnit) { mutableFloatStateOf((statusDisplayTemp ?: climateDisplayValueFromF("72", temperatureUnit)).toFloat()) }
    var durationMinutes by remember(defaultDurationMinutes) { mutableIntStateOf(defaultDurationMinutes) }
    var driverSeat by remember { mutableIntStateOf(2) }
    var passengerSeat by remember { mutableIntStateOf(2) }
    var rearLeftSeat by remember { mutableIntStateOf(2) }
    var rearRightSeat by remember { mutableIntStateOf(2) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var seatPresets by remember(temperatureUnit) {
        mutableStateOf(ClimatePresetsStore.load(context, temperatureUnit))
    }

    LaunchedEffect(climateOn, statusDisplayTemp, temperatureUnit) {
        if (climateOn && statusDisplayTemp != null) {
            displayTemp = statusDisplayTemp.toFloat()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                seatPresets = ClimatePresetsStore.load(context, temperatureUnit)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun applyPreset(preset: ClimatePreset) {
        if (preset.stopsClimate) {
            if (climateOn) {
                onStopClimate()
            } else {
                defrost = false
                heatedSteering = false
                driverSeat = 2
                passengerSeat = 2
                rearLeftSeat = 2
                rearRightSeat = 2
            }
            return
        }
        displayTemp = climateDisplayValueFromF(preset.tempF, temperatureUnit).toFloat()
        defrost = preset.defrost
        heatedSteering = preset.heatedSteering && featureCaps?.showHeatedSteering != false
        durationMinutes = coerceClimateDurationMinutes(preset.durationMinutes)
        val clamped = vehicle?.clampClimateSeatSettings(
            ClimateSeatSettings(
                heatedSteering = heatedSteering,
                driverSeat = preset.driverSeat,
                passengerSeat = preset.passengerSeat,
                rearLeftSeat = preset.rearLeftSeat,
                rearRightSeat = preset.rearRightSeat
            )
        )
        driverSeat = clamped?.driverSeat ?: preset.driverSeat
        passengerSeat = clamped?.passengerSeat ?: preset.passengerSeat
        rearLeftSeat = clamped?.rearLeftSeat ?: preset.rearLeftSeat
        rearRightSeat = clamped?.rearRightSeat ?: preset.rearRightSeat
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (isEV) "EV Climate" else "Climate",
                        fontSize = 20.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (climateOn) {
                            statusDisplayTemp?.let { "Climate is running · ${it}°${temperatureUnit}" } ?: "Climate is running"
                        } else {
                            "Precondition cabin before departure"
                        },
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilterChip(
                    selected = climateOn,
                    onClick = {
                        if (climateOn) {
                            onStopClimate()
                        } else {
                            onStartClimate(
                                climateFahrenheitFromDisplay(displayTemp.toInt(), temperatureUnit),
                                defrost,
                                heatedSteering,
                                driverSeat,
                                passengerSeat,
                                rearLeftSeat,
                                rearRightSeat,
                                durationMinutes
                            )
                        }
                    },
                    label = { Text(if (climateOn) "On" else "Off") },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.AcUnit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Temperature",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${displayTemp.toInt()}°${temperatureUnit}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = displayTemp,
                onValueChange = { displayTemp = it },
                valueRange = climateSliderRange(temperatureUnit),
                steps = climateSliderSteps(temperatureUnit),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Duration",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "$durationMinutes min",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = durationMinutes.toFloat(),
                onValueChange = { durationMinutes = coerceClimateDurationMinutes(it.toInt()) },
                valueRange = MIN_CLIMATE_DURATION_MINUTES.toFloat()..MAX_CLIMATE_DURATION_MINUTES.toFloat(),
                steps = 4,
                enabled = !climateOn,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.AcUnit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Defrost",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Switch(checked = defrost, onCheckedChange = { defrost = it }, enabled = !climateOn)
            }

            if (featureCaps?.showHeatedSteering != false) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Straight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Heated Steering Wheel",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Switch(
                        checked = heatedSteering,
                        onCheckedChange = { heatedSteering = it },
                        enabled = !climateOn
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 2.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )
            DashboardClimatePresetStrip(
                presets = seatPresets,
                climateOn = climateOn,
                onApply = { applyPreset(it) },
                onManage = onManageSeatPresets
            )

            if (featureCaps?.showSeatClimateControls != false && featureCaps != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
                val statusSeats = status?.seatHeaterVentInfo
                SeatClimateMap(
                    featureCaps = featureCaps,
                    driverSeat = if (climateOn) {
                        statusSeats?.driverSeatHeatState?.takeIf { it > 0 } ?: driverSeat
                    } else {
                        driverSeat
                    },
                    passengerSeat = if (climateOn) {
                        statusSeats?.passengerSeatHeatState?.takeIf { it > 0 } ?: passengerSeat
                    } else {
                        passengerSeat
                    },
                    rearLeftSeat = if (climateOn) {
                        statusSeats?.rearLeftSeatHeatState?.takeIf { it > 0 } ?: rearLeftSeat
                    } else {
                        rearLeftSeat
                    },
                    rearRightSeat = if (climateOn) {
                        statusSeats?.rearRightSeatHeatState?.takeIf { it > 0 } ?: rearRightSeat
                    } else {
                        rearRightSeat
                    },
                    enabled = !climateOn,
                    onDriverSeat = { driverSeat = it },
                    onPassengerSeat = { passengerSeat = it },
                    onRearLeftSeat = { rearLeftSeat = it },
                    onRearRightSeat = { rearRightSeat = it }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onStartClimate(
                            climateFahrenheitFromDisplay(displayTemp.toInt(), temperatureUnit),
                            defrost,
                            heatedSteering,
                            driverSeat,
                            passengerSeat,
                            rearLeftSeat,
                            rearRightSeat,
                            durationMinutes
                        )
                    },
                    enabled = !climateOn,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Start", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onStopClimate,
                    enabled = climateOn,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Stop, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Stop", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}



@Composable
private fun DashboardClimatePresetStrip(
    presets: List<ClimatePreset>,
    climateOn: Boolean,
    onApply: (ClimatePreset) -> Unit,
    onManage: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Climate Presets",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Warm/Cool apply settings · All Off stops climate",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = onManage,
                enabled = !climateOn,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Manage")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.take(3).forEach { preset ->
                val enabled = if (preset.stopsClimate) true else !climateOn
                OutlinedButton(
                    onClick = { onApply(preset) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f).height(42.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        preset.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SeatClimateMap(
    featureCaps: VehicleFeatureCapabilities,
    driverSeat: Int,
    passengerSeat: Int,
    rearLeftSeat: Int,
    rearRightSeat: Int,
    enabled: Boolean = true,
    onDriverSeat: (Int) -> Unit,
    onPassengerSeat: (Int) -> Unit,
    onRearLeftSeat: (Int) -> Unit,
    onRearRightSeat: (Int) -> Unit
) {
    val showDriver = featureCaps.driver.showHeat || featureCaps.driver.showVent
    val showPassenger = featureCaps.passenger.showHeat || featureCaps.passenger.showVent
    val showRearLeft = featureCaps.rearLeft.showHeat || featureCaps.rearLeft.showVent
    val showRearRight = featureCaps.rearRight.showHeat || featureCaps.rearRight.showVent
    if (!showDriver && !showPassenger && !showRearLeft && !showRearRight) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Seat Climate",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                if (enabled) "Tap seats to cycle" else "Active with climate",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showDriver || showPassenger) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showDriver) {
                        CompactSeatTile(
                            label = "Driver",
                            value = driverSeat,
                            onClick = {
                                onDriverSeat(
                                    nextSeatClimateLevel(
                                        driverSeat,
                                        ventCapable = featureCaps.driver.ventCapableForSelector
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            ventCapable = featureCaps.driver.ventCapableForSelector,
                            enabled = enabled
                        )
                    }
                    if (showPassenger) {
                        CompactSeatTile(
                            label = "Passenger",
                            value = passengerSeat,
                            onClick = {
                                onPassengerSeat(
                                    nextSeatClimateLevel(
                                        passengerSeat,
                                        ventCapable = featureCaps.passenger.ventCapableForSelector
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            ventCapable = featureCaps.passenger.ventCapableForSelector,
                            enabled = enabled
                        )
                    }
                }
            }
            if (showRearLeft || showRearRight) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showRearLeft) {
                        CompactSeatTile(
                            label = "Rear Left",
                            value = rearLeftSeat,
                            onClick = {
                                onRearLeftSeat(
                                    nextSeatClimateLevel(
                                        rearLeftSeat,
                                        ventCapable = featureCaps.rearLeft.ventCapableForSelector
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            ventCapable = featureCaps.rearLeft.ventCapableForSelector,
                            enabled = enabled
                        )
                    }
                    if (showRearRight) {
                        CompactSeatTile(
                            label = "Rear Right",
                            value = rearRightSeat,
                            onClick = {
                                onRearRightSeat(
                                    nextSeatClimateLevel(
                                        rearRightSeat,
                                        ventCapable = featureCaps.rearRight.ventCapableForSelector
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            ventCapable = featureCaps.rearRight.ventCapableForSelector,
                            enabled = enabled
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSeatTile(
    label: String,
    value: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ventCapable: Boolean,
    enabled: Boolean = true
) {
    val accent = dashboardSeatClimateColor(value)
    val tileAccent = MaterialTheme.colorScheme.primary
    val shortLabel = dashboardSeatClimateShortLabel(value)
    val icon = when (value) {
        6, 7, 8 -> Icons.Filled.Whatshot
        3, 4, 5 -> Icons.Filled.AcUnit
        else -> Icons.Filled.AirlineSeatReclineNormal
    }

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(78.dp),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = tileAccent.copy(alpha = if (value == 0 || value == 2) 0.08f else 0.14f),
            contentColor = accent,
            disabledContainerColor = tileAccent.copy(alpha = if (value == 0 || value == 2) 0.08f else 0.14f),
            disabledContentColor = accent
        ),
        border = BorderStroke(1.dp, tileAccent.copy(alpha = 0.55f))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                fontSize = 12.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                shortLabel,
                fontSize = 12.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                color = accent
            )
        }
    }
}

private fun nextSeatClimateLevel(current: Int, ventCapable: Boolean): Int {
    val options = if (ventCapable) {
        listOf(2, 6, 7, 8, 3, 4, 5)
    } else {
        listOf(2, 6, 7, 8)
    }
    val index = options.indexOf(current).takeIf { it >= 0 } ?: 0
    return options[(index + 1) % options.size]
}

private fun dashboardSeatClimateShortLabel(value: Int): String = when (value) {
    6 -> "Heat L"
    7 -> "Heat M"
    8 -> "Heat H"
    3 -> "Vent L"
    4 -> "Vent M"
    5 -> "Vent H"
    0, 2 -> "Off"
    else -> "Level $value"
}

@Composable
private fun dashboardSeatClimateColor(value: Int): Color = when (value) {
    6, 7, 8 -> SeatHeatOrange
    3, 4, 5 -> SeatVentBlue
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}


private fun dashboardSeatClimateLabel(value: Int): String = when (value) {
    6 -> "Heat Low"
    7 -> "Heat Medium"
    8 -> "Heat High"
    3 -> "Vent Low"
    4 -> "Vent Medium"
    5 -> "Vent High"
    0, 2 -> "Off"
    else -> "Level $value"
}

@Composable
fun FeatureTilesGrid(
    featureCaps: VehicleFeatureCapabilities?,
    onLocation: () -> Unit,
    onDriverProfiles: () -> Unit,
    onDigitalKey: () -> Unit,
    onValetMode: () -> Unit,
    onObd: () -> Unit
) {
    val showLocation = featureCaps?.showLocation != false
    val showDigitalKey = featureCaps?.showDigitalKey != false
    val showValet = featureCaps?.showValetMode == true
    if (!showLocation && !showDigitalKey && !showValet) {
        // Still show OBD row
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DashboardQuickActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Speed,
                label = "OBD Logs",
                tint = MaterialTheme.colorScheme.primary,
                onClick = onObd,
                compact = true
            )
            if (showLocation) {
                DashboardQuickActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.LocationOn,
                    label = "GPS Location",
                    tint = MaterialTheme.colorScheme.secondary,
                    onClick = onLocation,
                    compact = true
                )
            }
            DashboardQuickActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.People,
                label = "Profiles",
                tint = SuccessGreen,
                onClick = onDriverProfiles,
                compact = true
            )
            if (showDigitalKey) {
                DashboardQuickActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Key,
                    label = "Digital Key",
                    tint = MaterialTheme.colorScheme.tertiary,
                    onClick = onDigitalKey,
                    compact = true
                )
            }
        }
        if (showValet) {
            Row(modifier = Modifier.fillMaxWidth()) {
                DashboardQuickActionButton(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Security,
                    label = "Valet Mode",
                    tint = MaterialTheme.colorScheme.secondary,
                    onClick = onValetMode,
                    compact = true
                )
            }
        }
    }
}
