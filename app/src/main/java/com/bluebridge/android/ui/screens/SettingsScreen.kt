package com.bluebridge.android.ui.screens

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bluebridge.android.BuildConfig
import com.bluebridge.android.automation.WalkAwayBluetoothMonitorService
import com.bluebridge.android.automation.WalkAwayLockScheduler
import com.bluebridge.android.data.api.Region
import com.bluebridge.android.data.models.UiColorOverrides
import com.bluebridge.android.data.models.UiColorSlot
import com.bluebridge.android.ui.components.ControlSection
import com.bluebridge.android.ui.theme.*
import com.bluebridge.android.viewmodel.SettingsViewModel
import com.bluebridge.android.widget.VehicleWidgetProvider

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val region by viewModel.region.collectAsStateWithLifecycle()
    val tempUnit by viewModel.temperatureUnit.collectAsStateWithLifecycle()
    val distanceUnit by viewModel.distanceUnit.collectAsStateWithLifecycle()
    val timeZoneMode by viewModel.timeZoneMode.collectAsStateWithLifecycle()
    val timeFormat by viewModel.timeFormat.collectAsStateWithLifecycle()
    val biometricEnabled by viewModel.biometricEnabled.collectAsStateWithLifecycle()
    val biometricUnlockMode by viewModel.biometricUnlockMode.collectAsStateWithLifecycle()
    val stayLoggedIn30Days by viewModel.stayLoggedIn30Days.collectAsStateWithLifecycle()
    val walkAwayLockEnabled by viewModel.walkAwayLockEnabled.collectAsStateWithLifecycle()
    val walkAwayLockDelaySeconds by viewModel.walkAwayLockDelaySeconds.collectAsStateWithLifecycle()
    val walkAwayBluetoothName by viewModel.walkAwayBluetoothName.collectAsStateWithLifecycle()
    val walkAwayBluetoothAddress by viewModel.walkAwayBluetoothAddress.collectAsStateWithLifecycle()
    val selectedThemeId by viewModel.appTheme.collectAsStateWithLifecycle()
    val uiColorOverrides by viewModel.uiColorOverrides.collectAsStateWithLifecycle()
    val servicePin by viewModel.servicePin.collectAsStateWithLifecycle()
    val selectedTheme = AppTheme.fromId(selectedThemeId)
    val context = LocalContext.current

    LaunchedEffect(distanceUnit) {
        VehicleWidgetProvider.refreshAll(context)
    }

    var showRegionPicker by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    var showColorCustomizer by remember { mutableStateOf(false) }
    var editingColorSlot by remember { mutableStateOf<UiColorSlot?>(null) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showBluetoothPicker by remember { mutableStateOf(false) }
    var bluetoothPermissionMessage by remember { mutableStateOf<String?>(null) }
    var walkAwayDelayInput by remember(walkAwayLockDelaySeconds) {
        mutableStateOf(walkAwayLockDelaySeconds.coerceIn(0, 600).toString())
    }
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val bluetoothGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            grants[Manifest.permission.BLUETOOTH_CONNECT] == true ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (bluetoothGranted) {
            bluetoothPermissionMessage = null
            showBluetoothPicker = true
        } else {
            bluetoothPermissionMessage = "Bluetooth Nearby Devices permission is required for walk-away lock detection."
        }
    }

    fun openBluetoothPicker() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissions.isNotEmpty()) {
            bluetoothPermissionLauncher.launch(permissions.toTypedArray())
        } else {
            bluetoothPermissionMessage = null
            showBluetoothPicker = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
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

            // ── Account ───────────────────────────────────────────────────────
            ControlSection(title = "Account") {
                Column {
                    RegionSettingsRow(
                        value = Region.valueOf(region).label,
                        onClick = { showRegionPicker = true }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    SettingsRow(
                        icon = Icons.Filled.Lock,
                        label = "Bluelink PIN",
                        value = if (servicePin.isNullOrBlank()) "Not set" else "••••",
                        onClick = { showPinDialog = true }
                    )
                }
            }

            // ── Appearance ────────────────────────────────────────────────────
            ControlSection(title = "Appearance") {
                Column {
                    SettingsRow(
                        icon = Icons.Filled.Palette,
                        label = "Color Theme",
                        value = selectedTheme.displayName,
                        onClick = { showThemePicker = true }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    SettingsRow(
                        icon = Icons.Filled.ColorLens,
                        label = "Customize UI Colors",
                        value = when (val count = uiColorOverrides.activeCount()) {
                            0 -> "Default"
                            1 -> "1 override"
                            else -> "$count overrides"
                        },
                        onClick = { showColorCustomizer = true }
                    )
                }
            }

            // ── Preferences ───────────────────────────────────────────────────
            ControlSection(title = "Preferences") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Temperature unit toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Thermostat, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Temperature Unit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row {
                            FilterChip(
                                selected = tempUnit == "F",
                                onClick = { viewModel.setTemperatureUnit("F") },
                                label = { Text("°F") },
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            FilterChip(
                                selected = tempUnit == "C",
                                onClick = { viewModel.setTemperatureUnit("C") },
                                label = { Text("°C") }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))

                    // Distance unit toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Route, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Distance Unit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row {
                            FilterChip(
                                selected = distanceUnit == "MI",
                                onClick = {
                                    viewModel.setDistanceUnit("MI")
                                    VehicleWidgetProvider.refreshAll(context)
                                },
                                label = { Text("mi") },
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            FilterChip(
                                selected = distanceUnit == "KM",
                                onClick = {
                                    viewModel.setDistanceUnit("KM")
                                    VehicleWidgetProvider.refreshAll(context)
                                },
                                label = { Text("km") }
                            )
                        }
                    }



                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))

                    // Time zone display preference
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Time Zone", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    "Used for vehicle-reported timestamps such as tire readings",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TimeZoneChip("DEVICE", "Device", timeZoneMode, viewModel::setTimeZoneMode)
                            TimeZoneChip("UTC", "UTC", timeZoneMode, viewModel::setTimeZoneMode)
                            TimeZoneChip("AMERICA_NEW_YORK", "Eastern", timeZoneMode, viewModel::setTimeZoneMode)
                            TimeZoneChip("AMERICA_CHICAGO", "Central", timeZoneMode, viewModel::setTimeZoneMode)
                            TimeZoneChip("AMERICA_DENVER", "Mountain", timeZoneMode, viewModel::setTimeZoneMode)
                            TimeZoneChip("AMERICA_LOS_ANGELES", "Pacific", timeZoneMode, viewModel::setTimeZoneMode)
                            TimeZoneChip("AMERICA_HALIFAX", "Atlantic", timeZoneMode, viewModel::setTimeZoneMode)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))

                    // Time format display preference
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Time Format", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    "Used for vehicle-reported timestamps such as tire and odometer readings",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TimeFormatChip("12_HOUR", "12-hour AM/PM", timeFormat, viewModel::setTimeFormat)
                            TimeFormatChip("24_HOUR", "24-hour", timeFormat, viewModel::setTimeFormat)
                        }
                    }


                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))

                    // Stay logged in
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Stay Logged In for 30 Days",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Keep the app session open and refresh supported regional tokens when needed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = stayLoggedIn30Days,
                            onCheckedChange = { viewModel.setStayLoggedIn30Days(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            )
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))

                    // Biometric
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Fingerprint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Biometric Lock",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Control when BlueBridge asks for fingerprint while your account session is valid",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = biometricEnabled,
                            onCheckedChange = { viewModel.setBiometricEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            )
                        )
                    }

                    AnimatedVisibility(visible = biometricEnabled) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 32.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Fingerprint frequency",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "APP_OPEN" to "App open",
                                    "DAILY" to "Once per day",
                                    "COMMANDS_ONLY" to "Commands only",
                                    "NEVER" to "Never"
                                ).forEach { (mode, label) ->
                                    FilterChip(
                                        selected = biometricUnlockMode == mode,
                                        onClick = { viewModel.setBiometricUnlockMode(mode) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                            Text(
                                when (biometricUnlockMode) {
                                    "DAILY" -> "BlueBridge will suppress app-open fingerprint prompts for 24 hours after a successful unlock, as long as the account session is still valid."
                                    "COMMANDS_ONLY" -> "BlueBridge will not prompt when opening the app. Use this with the 30-day account session for fewer interruptions."
                                    "NEVER" -> "BlueBridge will not ask for fingerprint while the account session remains valid."
                                    else -> "BlueBridge will ask again after it has been in the background for about 5 minutes."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            )
                        }
                    }
                }
            }

            // ── Automation ─────────────────────────────────────────────────────
            ControlSection(title = "Automation") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Walk-Away Lock",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    if (walkAwayBluetoothAddress.isNullOrBlank()) "Select vehicle Bluetooth first" else "Lock after vehicle Bluetooth disconnects",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = walkAwayLockEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && (walkAwayBluetoothAddress.isNullOrBlank() ||
                                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                                    )) {
                                    openBluetoothPicker()
                                } else {
                                    viewModel.setWalkAwayLockEnabled(enabled)
                                    if (enabled) {
                                        WalkAwayBluetoothMonitorService.start(context)
                                    } else {
                                        WalkAwayBluetoothMonitorService.stop(context)
                                        WalkAwayLockScheduler.cancel(context)
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            )
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))

                    SettingsRow(
                        icon = Icons.Filled.Settings,
                        label = "Vehicle Bluetooth",
                        value = walkAwayBluetoothName ?: "Choose device",
                        onClick = { openBluetoothPicker() }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Timer,
                            null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Lock Delay Offset",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Extra seconds to wait before locking",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        OutlinedTextField(
                            value = walkAwayDelayInput,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }.take(3)
                                val bounded = digits.toIntOrNull()?.coerceIn(0, 600)
                                walkAwayDelayInput = bounded?.toString() ?: digits
                                bounded?.let { viewModel.setWalkAwayLockDelaySeconds(it) }
                            },
                            label = { Text("Seconds") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(128.dp)
                        )
                    }

                    Text(
                        "Allowed range: 0-600 seconds. The app/vehicle already has a short built-in delay; this value adds extra time on top of that.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.padding(start = 32.dp, end = 8.dp, bottom = 4.dp)
                    )

                    bluetoothPermissionMessage?.let { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 32.dp, end = 8.dp, bottom = 4.dp)
                        )
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Text(
                            "Walk-away lock is inactive until Android's Nearby Devices permission is granted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 32.dp, end = 8.dp, bottom = 4.dp)
                        )
                    }

                    Text(
                        "Experimental: Android background limits, phone battery optimization, network connectivity, or Hyundai/Kia API availability can prevent automatic locking. Always verify the vehicle is locked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // ── About ─────────────────────────────────────────────────────────
            ControlSection(title = "About") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SettingsInfoRow("Version", BuildConfig.VERSION_NAME)
                    SettingsInfoRow("API", "Hyundai Bluelink / Kia Connect")
                    SettingsInfoRow("Credit", "Nelwyn99")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "BlueBridge is an unofficial third-party app and is not affiliated with Hyundai or Kia. " +
                        "Use of this app is at your own risk.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    )
                }
            }

            // ── Sign out ──────────────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Filled.Logout, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sign Out")
            }
        }
    }


    // ── Walk-away Bluetooth picker ───────────────────────────────────────────
    if (showBluetoothPicker) {
        val pairedDevices = remember(showBluetoothPicker) { pairedBluetoothDevices(context) }
        AlertDialog(
            onDismissRequest = { showBluetoothPicker = false },
            title = { Text("Choose Vehicle Bluetooth") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (pairedDevices.isEmpty()) {
                        Text(
                            "No paired Bluetooth devices were found. Pair the phone with the vehicle first, then return here.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        pairedDevices.forEach { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setWalkAwayBluetoothDevice(device.name, device.address)
                                        viewModel.setWalkAwayLockEnabled(true)
                                        WalkAwayBluetoothMonitorService.start(context)
                                        showBluetoothPicker = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = device.address.equals(walkAwayBluetoothAddress, ignoreCase = true),
                                    onClick = {
                                        viewModel.setWalkAwayBluetoothDevice(device.name, device.address)
                                        viewModel.setWalkAwayLockEnabled(true)
                                        WalkAwayBluetoothMonitorService.start(context)
                                        showBluetoothPicker = false
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(device.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                    Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBluetoothPicker = false }) { Text("Done") } },
            dismissButton = {
                if (!walkAwayBluetoothAddress.isNullOrBlank()) {
                    TextButton(onClick = {
                        viewModel.clearWalkAwayBluetoothDevice()
                        WalkAwayBluetoothMonitorService.stop(context)
                        WalkAwayLockScheduler.cancel(context)
                        showBluetoothPicker = false
                    }) { Text("Clear") }
                }
            }
        )
    }

    // ── Bluelink PIN dialog ──────────────────────────────────────────────────
    if (showPinDialog) {
        var pinInput by remember(servicePin) { mutableStateOf(servicePin.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Bluelink PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Your 4-digit Bluelink PIN is required for unlock, remote start, horn, and lights.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.setServicePin(pinInput)
                    showPinDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Region picker dialog ──────────────────────────────────────────────────
    if (showRegionPicker) {
        AlertDialog(
            onDismissRequest = { showRegionPicker = false },
            title = { Text("Select Region & Brand") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Region.entries.filter { it != Region.AU }.forEach { r ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setRegion(r.name)
                                    showRegionPicker = false
                                }
                                .heightIn(min = 64.dp)
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = r.name == region, onClick = {
                                viewModel.setRegion(r.name)
                                showRegionPicker = false
                            })
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = r.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Visible,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (r != Region.entries.filter { it != Region.AU }.last()) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRegionPicker = false }) { Text("Cancel") } }
        )
    }

    // ── Theme picker dialog ───────────────────────────────────────────────────
    if (showThemePicker) {
        AlertDialog(
            onDismissRequest = { showThemePicker = false },
            title = { Text("Choose Color Theme") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    availableAppThemes().forEach { theme ->
                        ThemeOptionRow(
                            theme = theme,
                            selected = theme.id == selectedThemeId,
                            onClick = {
                                viewModel.setAppTheme(theme.id)
                                showThemePicker = false
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemePicker = false }) { Text("Cancel") } }
        )
    }

    // ── Custom color editor ───────────────────────────────────────────────────
    if (showColorCustomizer) {
        AlertDialog(
            onDismissRequest = { showColorCustomizer = false },
            title = { Text("Customize UI Colors") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Each color overrides the selected theme. Reset a row to fall back to ${selectedTheme.displayName}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    UiColorSlot.entries.forEach { slot ->
                        ColorOverrideRow(
                            slot = slot,
                            theme = selectedTheme,
                            overrides = uiColorOverrides,
                            onEdit = { editingColorSlot = slot },
                            onReset = { viewModel.resetUiColor(slot) }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showColorCustomizer = false }) { Text("Done") } },
            dismissButton = {
                TextButton(onClick = { viewModel.resetUiColors() }) { Text("Reset all") }
            }
        )
    }

    editingColorSlot?.let { slot ->
        ColorPickerDialog(
            slot = slot,
            theme = selectedTheme,
            overrides = uiColorOverrides,
            onDismiss = { editingColorSlot = null },
            onSave = { hex ->
                viewModel.setUiColor(slot, hex)
                editingColorSlot = null
            },
            onReset = {
                viewModel.resetUiColor(slot)
                editingColorSlot = null
            }
        )
    }

    // ── Logout confirm dialog ─────────────────────────────────────────────────
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.Filled.Logout, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Sign Out?") },
            text = { Text("You'll need to sign in again to control your vehicle.") },
            confirmButton = {
                Button(
                    onClick = { showLogoutDialog = false; onLogout() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Sign Out") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }
}


@Composable
fun RegionSettingsRow(value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Language,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Region / Brand",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                maxLines = 2,
                overflow = TextOverflow.Visible
            )
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun SettingsRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = true)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.weight(1f, fill = true)
            )
        }
        Spacer(Modifier.width(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.widthIn(max = 132.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun ThemeOptionRow(theme: AppTheme, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            ThemeColorDot(theme.primary)
            ThemeColorDot(theme.secondary)
            ThemeColorDot(theme.tertiary)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(theme.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(theme.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


@Composable
fun ColorOverrideRow(
    slot: UiColorSlot,
    theme: AppTheme,
    overrides: UiColorOverrides,
    onEdit: () -> Unit,
    onReset: () -> Unit
) {
    val overrideHex = overrides.valueFor(slot)
    val effectiveColor = colorFromHexOrNull(overrideHex) ?: defaultColorForSlot(theme, slot)
    val effectiveHex = overrideHex ?: colorToHex(effectiveColor)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onEdit)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(effectiveColor)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    slot.displayName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                if (!overrideHex.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    AssistChip(
                        onClick = onReset,
                        label = {
                            Text(
                                "Reset",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        },
                        modifier = Modifier.heightIn(min = 32.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                            labelColor = MaterialTheme.colorScheme.secondary
                        )
                    )
                }
            }
            Text(
                slot.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
            Text(
                if (overrideHex.isNullOrBlank()) "$effectiveHex • theme default" else "$effectiveHex • custom",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun ColorPickerDialog(
    slot: UiColorSlot,
    theme: AppTheme,
    overrides: UiColorOverrides,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onReset: () -> Unit
) {
    val initialColor = colorFromHexOrNull(overrides.valueFor(slot)) ?: defaultColorForSlot(theme, slot)

    var previewColor by remember(slot) { mutableStateOf(initialColor) }
    var hexInput by remember(slot) { mutableStateOf(colorToHex(initialColor)) }
    var hexError by remember(slot) { mutableStateOf(false) }

    fun channelValue(shift: Int): Int = (previewColor.toArgb() shr shift) and 0xFF

    fun updatePreviewColor(red: Int, green: Int, blue: Int) {
        previewColor = Color(red = red / 255f, green = green / 255f, blue = blue / 255f)
        hexInput = "#%02X%02X%02X".format(red, green, blue)
        hexError = false
    }

    fun updateFromHex(input: String) {
        val parsedColor = colorFromHexOrNull(input)
        if (parsedColor == null) {
            hexInput = input.uppercase()
            hexError = true
            return
        }

        previewColor = parsedColor
        hexInput = colorToHex(parsedColor)
        hexError = false
    }

    val red = channelValue(16)
    val green = channelValue(8)
    val blue = channelValue(0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(slot.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(74.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(previewColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        hexInput,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = readableTextOn(previewColor)
                    )
                }

                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { updateFromHex(it) },
                    label = { Text("Hex color") },
                    placeholder = { Text("#00D4FF") },
                    singleLine = true,
                    isError = hexError,
                    supportingText = {
                        Text(if (hexError) "Use #RRGGBB or RRGGBB" else slot.description)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                RgbSliderRow("Red", red) { updatePreviewColor(it, green, blue) }
                RgbSliderRow("Green", green) { updatePreviewColor(red, it, blue) }
                RgbSliderRow("Blue", blue) { updatePreviewColor(red, green, it) }
            }
        },
        confirmButton = {
            Button(
                enabled = !hexError,
                onClick = { normalizeHexColor(hexInput)?.let(onSave) }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}



private data class PairedBluetoothDevice(
    val name: String,
    val address: String
)

@Suppress("MissingPermission")
private fun pairedBluetoothDevices(context: Context): List<PairedBluetoothDevice> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
    ) {
        return emptyList()
    }
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = bluetoothManager?.adapter ?: return emptyList()
    return adapter.bondedDevices
        .mapNotNull { device ->
            val address = device.address ?: return@mapNotNull null
            PairedBluetoothDevice(
                name = device.name?.takeIf { it.isNotBlank() } ?: "Bluetooth device",
                address = address
            )
        }
        .distinctBy { it.address }
        .sortedBy { it.name.lowercase() }
}

@Composable
private fun TimeZoneChip(
    value: String,
    label: String,
    selectedValue: String,
    onSelected: (String) -> Unit
) {
    FilterChip(
        selected = selectedValue == value,
        onClick = { onSelected(value) },
        label = { Text(label) }
    )
}


@Composable
private fun TimeFormatChip(
    value: String,
    label: String,
    selectedValue: String,
    onSelected: (String) -> Unit
) {
    FilterChip(
        selected = selectedValue == value,
        onClick = { onSelected(value) },
        label = { Text(label) }
    )
}

@Composable
private fun RgbSliderRow(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
            Text(value.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            steps = 254
        )
    }
}

private fun readableTextOn(color: Color): Color {
    val argb = color.toArgb()
    val red = (argb shr 16) and 0xFF
    val green = (argb shr 8) and 0xFF
    val blue = argb and 0xFF
    val luminance = (0.299 * red + 0.587 * green + 0.114 * blue)
    return if (luminance > 150) Color(0xFF071018) else Color.White
}

@Composable
fun ThemeColorDot(color: Color) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .background(color, CircleShape)
    )
}
