package com.bluebridge.android.ui.screens

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
import com.bluebridge.android.ui.components.ControlSection
import com.bluebridge.android.ui.components.ToggleControlRow
import com.bluebridge.android.ui.TemperatureDisplay
import com.bluebridge.android.ui.theme.*
import com.bluebridge.android.viewmodel.VehicleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlsScreen(
    vehicleViewModel: VehicleViewModel,
    onNavigateBack: () -> Unit
) {
    val status by vehicleViewModel.vehicleStatus.collectAsStateWithLifecycle()
    val vehicle by vehicleViewModel.selectedVehicle.collectAsStateWithLifecycle()
    val temperatureUnit by vehicleViewModel.temperatureUnit.collectAsStateWithLifecycle()
    val isEV = vehicle?.isEV == true
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
                            pendingConfirmation = ControlsConfirmationRequest(
                                title = if (isEV) "Start climate?" else "Remote start?",
                                message = if (isEV) "Start cabin climate preconditioning?" else "Remote start ${vehicle?.displayName ?: "this vehicle"}?",
                                confirmLabel = if (isEV) "Start Climate" else "Start",
                                action = { vehicleViewModel.startEngine() }
                            )
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
                var defrost by remember { mutableStateOf(false) }
                var tempF by remember { mutableFloatStateOf(72f) }
                val sliderRange = TemperatureDisplay.hvacSliderRange(temperatureUnit)
                val sliderSteps = TemperatureDisplay.hvacSliderSteps(temperatureUnit)

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Temperature", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            TemperatureDisplay.formatHvacSetpoint(tempF, temperatureUnit),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = TemperatureDisplay.sliderDisplayValue(tempF, temperatureUnit),
                        onValueChange = { tempF = TemperatureDisplay.setpointFahrenheitFromSlider(it, temperatureUnit) },
                        valueRange = sliderRange,
                        steps = sliderSteps,
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                    )

                    ToggleControlRow(
                        label = "Defrost",
                        icon = Icons.Filled.AcUnit,
                        checked = defrost,
                        onChecked = { defrost = it }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                val selectedTemp = tempF.toInt()
                                val selectedDefrost = defrost
                                pendingConfirmation = ControlsConfirmationRequest(
                                    title = "Start climate?",
                                    message = buildString {
                                        append("Start cabin climate at ")
                                        append(TemperatureDisplay.formatHvacSetpoint(selectedTemp.toFloat(), temperatureUnit))
                                        if (selectedDefrost) append(" with defrost")
                                        append("?")
                                    },
                                    confirmLabel = "Start Climate",
                                    action = { vehicleViewModel.startClimate(selectedTemp.toString(), selectedDefrost) }
                                )
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
