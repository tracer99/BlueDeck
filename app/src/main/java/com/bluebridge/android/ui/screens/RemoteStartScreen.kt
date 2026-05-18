package com.bluebridge.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bluebridge.android.ui.components.CommandStatusBanner
import com.bluebridge.android.ui.components.ControlSection
import com.bluebridge.android.ui.components.ToggleControlRow
import com.bluebridge.android.ui.components.SeatHeatSelector
import com.bluebridge.android.ui.theme.*
import com.bluebridge.android.viewmodel.RemoteStartSettings
import com.bluebridge.android.viewmodel.VehicleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteStartScreen(
    vehicleViewModel: VehicleViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by vehicleViewModel.remoteStartSettings.collectAsStateWithLifecycle()
    var localSettings by remember(settings) { mutableStateOf(settings) }
    val commandState by vehicleViewModel.commandState.collectAsStateWithLifecycle()
    val vehicle by vehicleViewModel.selectedVehicle.collectAsStateWithLifecycle()
    val status by vehicleViewModel.vehicleStatus.collectAsStateWithLifecycle()
    val isEV = vehicle?.isEV == true
    val temperatureUnit by vehicleViewModel.temperatureUnit.collectAsStateWithLifecycle()
    var pendingLockPrompt by remember { mutableStateOf(false) }

    val climateOn = status?.airCtrlOn == true
    val statusDisplayTemp = status?.airTemp?.let { apiTemperatureToPreferredValue(it.value, it.unit, temperatureUnit) }

    LaunchedEffect(climateOn, statusDisplayTemp, temperatureUnit) {
        if (climateOn && statusDisplayTemp != null) {
            localSettings = localSettings.copy(tempF = climateFahrenheitFromDisplay(statusDisplayTemp, temperatureUnit).toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEV) "Climate Start" else "Remote Start", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CommandStatusBanner(commandState = commandState)

            if (climateOn) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        Text(statusDisplayTemp?.let { "Climate running · ${it}°${temperatureUnit}" } ?: "Climate running")
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.AcUnit, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }

            // ─── Climate Settings ──────────────────────────────────────────────
            ControlSection(title = "Climate") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToggleControlRow("HVAC On", Icons.Filled.AcUnit, localSettings.hvacOn) {
                        localSettings = localSettings.copy(hvacOn = it)
                    }

                    AnimatedVisibility(visible = localSettings.hvacOn) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Temperature", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    climateTemperatureLabelFromF(localSettings.tempF, temperatureUnit),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = climateDisplayValueFromF(localSettings.tempF, temperatureUnit).toFloat(),
                                onValueChange = { localSettings = localSettings.copy(tempF = climateFahrenheitFromDisplay(it.toInt(), temperatureUnit).toString()) },
                                valueRange = climateSliderRange(temperatureUnit),
                                steps = climateSliderSteps(temperatureUnit),
                                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }

                    ToggleControlRow("Defrost", Icons.Filled.AcUnit, localSettings.defrost) {
                        localSettings = localSettings.copy(defrost = it)
                    }
                }
            }

            // ─── Heating ──────────────────────────────────────────────────────
            ControlSection(title = "Seat & Wheel Climate") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToggleControlRow("Heated Steering Wheel", Icons.Filled.Straight, localSettings.heatedSteering) {
                        localSettings = localSettings.copy(heatedSteering = it)
                    }
                    SeatHeatSelector(
                        label = "Driver Seat",
                        value = localSettings.driverSeatHeat,
                        onValueChange = { localSettings = localSettings.copy(driverSeatHeat = it) }
                    )
                    SeatHeatSelector(
                        label = "Passenger Seat",
                        value = localSettings.passengerSeatHeat,
                        onValueChange = { localSettings = localSettings.copy(passengerSeatHeat = it) }
                    )
                    SeatHeatSelector(
                        label = "Rear Left Seat",
                        value = localSettings.rearLeftSeatHeat,
                        onValueChange = { localSettings = localSettings.copy(rearLeftSeatHeat = it) }
                    )
                    SeatHeatSelector(
                        label = "Rear Right Seat",
                        value = localSettings.rearRightSeatHeat,
                        onValueChange = { localSettings = localSettings.copy(rearRightSeatHeat = it) }
                    )
                }
            }

            // ─── Duration ─────────────────────────────────────────────────────
            ControlSection(title = "Duration") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Run Duration", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "${localSettings.durationMinutes} min",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = localSettings.durationMinutes.toFloat(),
                        onValueChange = { localSettings = localSettings.copy(durationMinutes = it.toInt()) },
                        valueRange = 5f..30f,
                        steps = 4,
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ─── Start Button ─────────────────────────────────────────────────
            Button(
                onClick = {
                    vehicleViewModel.updateRemoteStartSettings(localSettings)
                    if (status?.doorsLocked == false) {
                        pendingLockPrompt = true
                    } else {
                        vehicleViewModel.startEngine()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isEV) "Start Climate" else "Start Engine", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    if (pendingLockPrompt) {
        AlertDialog(
            onDismissRequest = { pendingLockPrompt = false },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = WarningAmber) },
            title = { Text("Lock vehicle first?", fontWeight = FontWeight.Bold) },
            text = { Text("Climate/remote start requires the doors to be locked. Lock ${vehicle?.displayName ?: "this vehicle"} first, then start automatically.") },
            confirmButton = {
                Button(onClick = {
                    pendingLockPrompt = false
                    vehicleViewModel.lockThenStartEngine()
                }) { Text("Lock & Start") }
            },
            dismissButton = {
                TextButton(onClick = { pendingLockPrompt = false }) { Text("Cancel") }
            }
        )
    }

}
