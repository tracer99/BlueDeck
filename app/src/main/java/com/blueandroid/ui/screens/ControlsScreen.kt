package com.blueandroid.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blueandroid.ui.components.ControlSection
import com.blueandroid.ui.components.ToggleControlRow
import com.blueandroid.ui.theme.*
import com.blueandroid.viewmodel.VehicleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlsScreen(
    vehicleViewModel: VehicleViewModel,
    onNavigateBack: () -> Unit
) {
    val status by vehicleViewModel.vehicleStatus.collectAsStateWithLifecycle()
    val vehicle by vehicleViewModel.selectedVehicle.collectAsStateWithLifecycle()
    val isEV = vehicle?.isEV == true
    val temperatureUnit by vehicleViewModel.temperatureUnit.collectAsStateWithLifecycle()
    var pendingConfirmation by remember { mutableStateOf<ControlsConfirmationRequest?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Controls", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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

            // ─── Doors & Security ─────────────────────────────────────────────
            ControlSection(title = "Doors & Security") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            pendingConfirmation = ControlsConfirmationRequest(
                                title = "Lock doors?",
                                message = "Lock all doors on ${vehicle?.displayName ?: "this vehicle"}?",
                                confirmLabel = "Lock",
                                action = { vehicleViewModel.lockDoors() }
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen.copy(alpha = 0.15f)),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                    ) {
                        Icon(Icons.Filled.Lock, null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Lock All", color = SuccessGreen)
                    }
                    Button(
                        onClick = {
                            pendingConfirmation = ControlsConfirmationRequest(
                                title = "Unlock doors?",
                                message = "Unlock ${vehicle?.displayName ?: "this vehicle"}?",
                                confirmLabel = "Unlock",
                                action = { vehicleViewModel.unlockDoors() }
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = WarningAmber.copy(alpha = 0.15f))
                    ) {
                        Icon(Icons.Filled.LockOpen, null, tint = WarningAmber, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Unlock", color = WarningAmber)
                    }
                }
            }


            // ─── Remote start / EV preconditioning ─────────────────────────────
            if (!isEV) {
                ControlSection(title = "Engine") {
                val engineOn = if (isEV) status?.airCtrlOn == true else status?.engineStatus == true
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (status?.doorsLocked == false) {
                                pendingConfirmation = ControlsConfirmationRequest(
                                    title = "Lock vehicle first?",
                                    message = "Climate/remote start requires the doors to be locked. Lock ${vehicle?.displayName ?: "this vehicle"} first, then start automatically.",
                                    confirmLabel = "Lock & Start",
                                    action = { vehicleViewModel.lockThenStartEngine() }
                                )
                            } else {
                                pendingConfirmation = ControlsConfirmationRequest(
                                    title = if (isEV) "Start climate?" else "Remote start?",
                                    message = if (isEV) "Start cabin climate preconditioning?" else "Remote start ${vehicle?.displayName ?: "this vehicle"}?",
                                    confirmLabel = if (isEV) "Start Climate" else "Start",
                                    action = { vehicleViewModel.startEngine() }
                                )
                            }
                        },
                        enabled = !engineOn,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isEV) "Start Climate" else "Remote Start")
                    }
                    OutlinedButton(
                        onClick = {
                            pendingConfirmation = ControlsConfirmationRequest(
                                title = if (isEV) "Stop climate?" else "Stop engine?",
                                message = if (isEV) "Stop cabin climate preconditioning?" else "Stop the remote-start session?",
                                confirmLabel = if (isEV) "Stop Climate" else "Stop",
                                action = { vehicleViewModel.stopEngine() }
                            )
                        },
                        enabled = engineOn,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Stop, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isEV) "Stop Climate" else "Stop Engine")
                    }
                }
                }
            }

            // ─── Climate ───────────────────────────────────────────────────────
            ControlSection(title = if (isEV) "EV Climate Preconditioning" else "Climate Settings") {
                val statusDisplayTemp = status?.airTemp?.let { airTemp ->
                    apiTemperatureToPreferredValue(airTemp.value, airTemp.unit, temperatureUnit)
                }
                val climateOn = status?.airCtrlOn == true
                var defrost by remember { mutableStateOf(false) }
                var heatedSteering by remember { mutableStateOf(false) }
                var displayTemp by remember(temperatureUnit) { mutableFloatStateOf((statusDisplayTemp ?: climateDisplayValueFromF("72", temperatureUnit)).toFloat()) }

                LaunchedEffect(climateOn, statusDisplayTemp, temperatureUnit) {
                    if (climateOn && statusDisplayTemp != null) {
                        displayTemp = statusDisplayTemp.toFloat()
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Temperature", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "${displayTemp.toInt()}°${temperatureUnit}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = displayTemp,
                        onValueChange = { displayTemp = it },
                        valueRange = climateSliderRange(temperatureUnit),
                        steps = climateSliderSteps(temperatureUnit),
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                    )

                    ToggleControlRow(
                        label = "Defrost",
                        icon = Icons.Filled.AcUnit,
                        checked = defrost,
                        onChecked = { defrost = it }
                    )

                    ToggleControlRow(
                        label = "Heated Steering Wheel",
                        icon = Icons.Filled.Straight,
                        checked = heatedSteering,
                        onChecked = { heatedSteering = it }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                val selectedDisplayTemp = displayTemp.toInt()
                                val selectedTempF = climateFahrenheitFromDisplay(selectedDisplayTemp, temperatureUnit)
                                val selectedDefrost = defrost
                                val selectedHeatedSteering = heatedSteering
                                if (status?.doorsLocked == false) {
                                    pendingConfirmation = ControlsConfirmationRequest(
                                        title = "Lock vehicle first?",
                                        message = "Climate start requires the doors to be locked. Lock ${vehicle?.displayName ?: "this vehicle"} first, then start climate automatically.",
                                        confirmLabel = "Lock & Start Climate",
                                        action = { vehicleViewModel.lockThenStartClimate(
                                            tempF = selectedTempF.toString(),
                                            defrost = selectedDefrost,
                                            heatedSteering = selectedHeatedSteering
                                        ) }
                                    )
                                } else {
                                    pendingConfirmation = ControlsConfirmationRequest(
                                        title = "Start climate?",
                                        message = buildString {
                                            append("Start cabin climate at ${selectedDisplayTemp}°${temperatureUnit}")
                                            if (selectedDefrost) append(" with defrost")
                                            if (selectedHeatedSteering) append(" and heated steering wheel")
                                            append("?")
                                        },
                                        confirmLabel = "Start Climate",
                                        action = {
                                            vehicleViewModel.startClimate(
                                                tempF = selectedTempF.toString(),
                                                defrost = selectedDefrost,
                                                heatedSteering = selectedHeatedSteering
                                            )
                                        }
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        ) {
                            Icon(Icons.Filled.AcUnit, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isEV) "Start Climate" else "Start", color = MaterialTheme.colorScheme.primary)
                        }
                        OutlinedButton(
                            onClick = {
                                pendingConfirmation = ControlsConfirmationRequest(
                                    title = "Stop climate?",
                                    message = "Stop the current climate preconditioning session?",
                                    confirmLabel = "Stop Climate",
                                    action = { vehicleViewModel.stopClimate() }
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isEV) "Stop Climate" else "Stop")
                        }
                    }
                }
            }
        }
    }

    pendingConfirmation?.let { request ->
        ControlsCommandConfirmationDialog(
            request = request,
            onDismiss = { pendingConfirmation = null },
            onConfirm = {
                pendingConfirmation = null
                request.action()
            }
        )
    }
}

private data class ControlsConfirmationRequest(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val action: () -> Unit
)

@Composable
private fun ControlsCommandConfirmationDialog(
    request: ControlsConfirmationRequest,
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
