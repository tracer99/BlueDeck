package com.bluedeck.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bluedeck.data.obd.ObdConnectionState
import com.bluedeck.data.obd.ObdTransportType
import com.bluedeck.data.obd.db.ObdSessionEntity
import com.bluedeck.viewmodel.ObdViewModel
import com.bluedeck.viewmodel.VehicleViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObdDiagnosticsScreen(
    onNavigateBack: () -> Unit,
    vehicleViewModel: VehicleViewModel = hiltViewModel(),
    obdViewModel: ObdViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val connectionState by obdViewModel.connectionState.collectAsStateWithLifecycle()
    val snapshot by obdViewModel.latestSnapshot.collectAsStateWithLifecycle()
    val sessions by obdViewModel.sessions.collectAsStateWithLifecycle()
    val config by obdViewModel.adapterConfig.collectAsStateWithLifecycle()
    val statusMessage by obdViewModel.statusMessage.collectAsStateWithLifecycle()
    val selectedVehicle by vehicleViewModel.selectedVehicle.collectAsStateWithLifecycle()

    var pendingBluetoothAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            grants[Manifest.permission.BLUETOOTH_CONNECT] == true
        val action = pendingBluetoothAction
        pendingBluetoothAction = null
        if (granted) {
            action?.invoke()
        } else {
            obdViewModel.reportStatus(
                "Bluetooth permission is required to connect to the OBD adapter"
            )
        }
    }

    fun withBluetoothPermission(action: () -> Unit) {
        if (config.transportType != ObdTransportType.BLUETOOTH) {
            action()
            return
        }
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingBluetoothAction = action
            btPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
        } else {
            action()
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { kotlinx.coroutines.delay(4000); obdViewModel.clearStatusMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OBD Diagnostics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        snackbarHost = {
            statusMessage?.let { msg ->
                Snackbar { Text(msg) }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ConnectionCard(
                    connectionState = connectionState,
                    adapterLabel = when (config.transportType) {
                        ObdTransportType.BLUETOOTH ->
                            config.bluetoothName ?: config.bluetoothAddress ?: "No adapter selected"
                        ObdTransportType.WIFI ->
                            "${config.wifiHost}:${config.wifiPort}"
                    },
                    onConnect = { withBluetoothPermission { obdViewModel.connect() } },
                    onDisconnect = { obdViewModel.disconnect() },
                    onStartLogging = {
                        withBluetoothPermission { obdViewModel.startLogging(selectedVehicle?.vin) }
                    },
                    onStopLogging = { obdViewModel.stopLogging() }
                )
            }

            if (connectionState == ObdConnectionState.LOGGING || connectionState == ObdConnectionState.CONNECTED) {
                item { MetricSection("Battery") {
                    MetricRow("12V aux", snapshot.auxVoltageV?.let { String.format(Locale.US, "%.1f V", it) } ?: "—")
                    MetricRow("12V state", snapshot.aux12vState.name.lowercase().replaceFirstChar { it.titlecase() })
                    MetricRow("Traction SOC", snapshot.tractionSocPercent?.let { "${it.toInt()}%" } ?: "—")
                    MetricRow("Traction SOH", snapshot.tractionSohPercent?.let { String.format(Locale.US, "%.1f%%", it) } ?: "—")
                    MetricRow("Charging", snapshot.isCharging?.let { if (it) "Yes" else "No" } ?: "—")
                    MetricRow("Temp min/max/avg", listOfNotNull(
                        snapshot.batteryTempMinC?.let { String.format(Locale.US, "%.0f", it) },
                        snapshot.batteryTempMaxC?.let { String.format(Locale.US, "%.0f", it) },
                        snapshot.batteryTempAvgC?.let { String.format(Locale.US, "%.0f", it) }
                    ).joinToString(" / ").ifBlank { "—" })
                    MetricRow("Heater", snapshot.batteryHeaterState.name.lowercase().replaceFirstChar { it.titlecase() })
                } }

                item { MetricSection("Cells") {
                    MetricRow("Min / max / avg", listOfNotNull(
                        snapshot.cellVoltageMinV?.let { String.format(Locale.US, "%.3f V", it) },
                        snapshot.cellVoltageMaxV?.let { String.format(Locale.US, "%.3f V", it) },
                        snapshot.cellVoltageAvgV?.let { String.format(Locale.US, "%.3f V", it) }
                    ).joinToString(" / ").ifBlank { "—" })
                    MetricRow("Deviation", snapshot.cellVoltageDeviationV?.let { String.format(Locale.US, "%.3f V", it) } ?: "—")
                } }

                item { MetricSection("Drivetrain") {
                    MetricRow("Front motor RPM", snapshot.frontMotorRpm?.toString() ?: "—")
                    MetricRow("Rear motor RPM", snapshot.rearMotorRpm?.toString() ?: "—")
                } }

                item { MetricSection("Lighting") {
                    if (snapshot.lightingSupported) {
                        MetricRow("Brake lights", snapshot.brakeLightOn?.let { if (it) "On" else "Off" } ?: "—")
                        MetricRow("Headlights", snapshot.headlightOn?.let { if (it) "On" else "Off" } ?: "—")
                    } else {
                        Text(
                            "Lighting status not available on this vehicle via OBD.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } }
            }

            item {
                Text("Session history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            if (sessions.isEmpty()) {
                item {
                    Text(
                        "No OBD sessions yet. Connect an adapter and start logging while driving.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            } else {
                items(sessions, key = { it.id }) { session ->
                    SessionRow(
                        session = session,
                        label = obdViewModel.sessionLabel(session),
                        onExport = {
                            obdViewModel.exportSession(session.id) { main, cells ->
                                shareCsv(context, main)
                                cells?.let { shareCsv(context, it) }
                            }
                        },
                        onSyncDrive = { obdViewModel.syncSessionToDrive(session.id) }
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ConnectionCard(
    connectionState: ObdConnectionState,
    adapterLabel: String,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onStartLogging: () -> Unit,
    onStopLogging: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Adapter", style = MaterialTheme.typography.labelLarge)
            Text(adapterLabel, style = MaterialTheme.typography.bodyLarge)
            Text(
                "Status: ${connectionState.name.lowercase().replace('_', ' ')}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onConnect, enabled = connectionState == ObdConnectionState.DISCONNECTED || connectionState == ObdConnectionState.ERROR) {
                    Text("Connect")
                }
                OutlinedButton(onClick = onDisconnect, enabled = connectionState != ObdConnectionState.DISCONNECTED) {
                    Text("Disconnect")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStartLogging,
                    enabled = connectionState != ObdConnectionState.LOGGING
                ) { Text("Start logging") }
                OutlinedButton(
                    onClick = onStopLogging,
                    enabled = connectionState == ObdConnectionState.LOGGING
                ) { Text("Stop") }
            }
        }
    }
}

@Composable
private fun MetricSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SessionRow(
    session: ObdSessionEntity,
    label: String,
    onExport: () -> Unit,
    onSyncDrive: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                session.vin?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                if (!session.driveFileId.isNullOrBlank()) {
                    Text("Synced to Drive", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            TextButton(onClick = onExport) { Text("CSV") }
            TextButton(onClick = onSyncDrive) { Text("Drive") }
        }
    }
}

private fun shareCsv(context: android.content.Context, file: java.io.File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export OBD log"))
}
