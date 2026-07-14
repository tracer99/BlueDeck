package com.bluedeck.ui.screens

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bluedeck.data.obd.HkmcEvProfileId
import com.bluedeck.data.obd.ObdTransportType
import com.bluedeck.ui.components.ControlSection
import com.bluedeck.viewmodel.ObdViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ObdScannerSettingsSection(
    obdViewModel: ObdViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val config by obdViewModel.adapterConfig.collectAsStateWithLifecycle()
    val retentionDays by obdViewModel.retentionDays.collectAsStateWithLifecycle()
    val maxStorageMb by obdViewModel.maxStorageMb.collectAsStateWithLifecycle()
    val storageUsage by obdViewModel.storageUsage.collectAsStateWithLifecycle()
    val driveSyncEnabled by obdViewModel.driveSyncEnabled.collectAsStateWithLifecycle()
    val driveLastSyncAt by obdViewModel.driveLastSyncAt.collectAsStateWithLifecycle()

    var showBtPicker by remember { mutableStateOf(false) }
    var showProfilePicker by remember { mutableStateOf(false) }
    var showRetentionPicker by remember { mutableStateOf(false) }
    var showStoragePicker by remember { mutableStateOf(false) }
    var wifiHostInput by remember(config.wifiHost) { mutableStateOf(config.wifiHost) }
    var wifiPortInput by remember(config.wifiPort) { mutableStateOf(config.wifiPort.toString()) }
    var showClearDialog by remember { mutableStateOf(false) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* account available via getLastSignedInAccount */ }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val ok = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            grants[Manifest.permission.BLUETOOTH_CONNECT] == true
        if (ok) showBtPicker = true
    }

    LaunchedEffect(Unit) { obdViewModel.refreshStorageUsage() }

    ControlSection(title = "OBD Scanner") {
        ObdSettingsRow(
            icon = Icons.Filled.Bluetooth,
            label = "Transport",
            value = if (config.transportType == ObdTransportType.BLUETOOTH) "Bluetooth" else "Wi-Fi",
            onClick = {
                obdViewModel.setTransportType(
                    if (config.transportType == ObdTransportType.BLUETOOTH) ObdTransportType.WIFI else ObdTransportType.BLUETOOTH
                )
            }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))

        if (config.transportType == ObdTransportType.BLUETOOTH) {
            ObdSettingsRow(
                icon = Icons.Filled.Devices,
                label = "OBD adapter",
                value = config.bluetoothName ?: "Choose device",
                onClick = {
                    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                    if (needsPermission) {
                        btPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
                    } else {
                        showBtPicker = true
                    }
                }
            )
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Wifi, null, Modifier.padding(start = 4.dp, end = 12.dp))
                Column(Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = wifiHostInput,
                        onValueChange = { wifiHostInput = it },
                        label = { Text("Host") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = wifiPortInput,
                        onValueChange = { wifiPortInput = it.filter { ch -> ch.isDigit() }.take(5) },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = {
                        obdViewModel.setWifiEndpoint(
                            wifiHostInput.trim(),
                            wifiPortInput.toIntOrNull() ?: 35000
                        )
                    }) { Text("Save Wi-Fi endpoint") }
                    Text(
                        "Join the OBD dongle Wi-Fi network on your phone first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        ObdSettingsRow(
            icon = Icons.Filled.DirectionsCar,
            label = "Vehicle PID profile",
            value = config.profileId.displayName,
            onClick = { showProfilePicker = true }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        ObdSettingsRow(
            icon = Icons.Filled.Timer,
            label = "Sample interval",
            value = "${config.sampleIntervalSeconds}s",
            onClick = {
                val next = when (config.sampleIntervalSeconds) {
                    5 -> 10
                    10 -> 15
                    15 -> 30
                    else -> 5
                }
                obdViewModel.setSampleInterval(next)
            }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Link, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Auto-connect adapter", style = MaterialTheme.typography.bodyMedium)
                Text("Start logging when paired OBD adapter connects", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }
            Switch(checked = config.autoStartLogging, onCheckedChange = {
                obdViewModel.setAutoConnect(it)
                obdViewModel.setAutoStartLogging(it)
            })
        }
    }

    ControlSection(title = "OBD Log Storage") {
        val usageMb = String.format(Locale.US, "%.1f", storageUsage.totalBytes / (1024.0 * 1024.0))
        Text(
            "${storageUsage.sessionCount} sessions · $usageMb MB",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        ObdSettingsRow(
            icon = Icons.Filled.DateRange,
            label = "Keep logs for",
            value = if (retentionDays == 0) "Unlimited" else "$retentionDays days",
            onClick = { showRetentionPicker = true }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        ObdSettingsRow(
            icon = Icons.Filled.Storage,
            label = "Max storage",
            value = if (maxStorageMb == 0) "Unlimited" else "$maxStorageMb MB",
            onClick = { showStoragePicker = true }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        TextButton(
            onClick = { showClearDialog = true },
            modifier = Modifier.padding(horizontal = 8.dp)
        ) { Text("Clear all OBD logs", color = MaterialTheme.colorScheme.error) }
    }

    ControlSection(title = "Google Drive Sync") {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.CloudUpload, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Upload session CSVs", style = MaterialTheme.typography.bodyMedium)
                if (driveLastSyncAt > 0) {
                    val whenText = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(driveLastSyncAt))
                    Text("Last sync: $whenText", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            }
            Switch(checked = driveSyncEnabled, onCheckedChange = { obdViewModel.setDriveSyncEnabled(it) })
        }
        TextButton(onClick = { obdViewModel.signInToDrive { googleSignInLauncher.launch(it) } }) {
            Text("Sign in to Google")
        }
    }

    if (showBtPicker) {
        val devices = remember { obdPairedDevices(context) }
        AlertDialog(
            onDismissRequest = { showBtPicker = false },
            title = { Text("Choose OBD adapter") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (devices.isEmpty()) {
                        Text("Pair your OBD dongle in system Bluetooth settings first.")
                    } else {
                        devices.forEach { device ->
                            Text(
                                device.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        obdViewModel.setBluetoothDevice(device.name, device.address)
                                        showBtPicker = false
                                    }
                                    .padding(vertical = 12.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBtPicker = false }) { Text("Done") } }
        )
    }

    if (showProfilePicker) {
        AlertDialog(
            onDismissRequest = { showProfilePicker = false },
            title = { Text("PID profile") },
            text = {
                Column {
                    HkmcEvProfileId.entries.forEach { profile ->
                        TextButton(onClick = {
                            obdViewModel.setProfile(profile)
                            showProfilePicker = false
                        }) { Text(profile.displayName) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showProfilePicker = false }) { Text("Cancel") } }
        )
    }

    if (showRetentionPicker) {
        val options = listOf(0 to "Unlimited", 7 to "7 days", 14 to "14 days", 30 to "30 days", 90 to "90 days", 365 to "365 days")
        AlertDialog(
            onDismissRequest = { showRetentionPicker = false },
            title = { Text("Keep logs for") },
            text = {
                Column {
                    options.forEach { (days, label) ->
                        TextButton(onClick = {
                            obdViewModel.setRetentionDays(days)
                            showRetentionPicker = false
                        }) { Text(label) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRetentionPicker = false }) { Text("Cancel") } }
        )
    }

    if (showStoragePicker) {
        val options = listOf(0 to "Unlimited", 50 to "50 MB", 100 to "100 MB", 250 to "250 MB", 500 to "500 MB")
        AlertDialog(
            onDismissRequest = { showStoragePicker = false },
            title = { Text("Max storage") },
            text = {
                Column {
                    options.forEach { (mb, label) ->
                        TextButton(onClick = {
                            obdViewModel.setMaxStorageMb(mb)
                            showStoragePicker = false
                        }) { Text(label) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showStoragePicker = false }) { Text("Cancel") } }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear all OBD logs?") },
            text = { Text("This removes all locally stored OBD sessions from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    obdViewModel.clearAllLogs()
                    showClearDialog = false
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } }
        )
    }
}

private data class ObdPairedDevice(val name: String, val address: String)

@Suppress("MissingPermission")
private fun obdPairedDevices(context: Context): List<ObdPairedDevice> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
    ) {
        return emptyList()
    }
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        ?: return emptyList()
    return adapter.bondedDevices.orEmpty().map {
        ObdPairedDevice(it.name ?: "Unknown", it.address)
    }.sortedBy { it.name.lowercase() }
}

@Composable
private fun ObdSettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
    }
}
