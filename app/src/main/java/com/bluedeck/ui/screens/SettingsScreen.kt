package com.bluedeck.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
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
import com.bluedeck.BuildConfig
import com.bluedeck.automation.WalkAwayBluetoothMonitorService
import com.bluedeck.automation.WalkAwayLockScheduler
import com.bluedeck.data.api.Region
import com.bluedeck.ui.components.ControlSection
import com.bluedeck.ui.theme.ThemeMode
import com.bluedeck.viewmodel.SettingsViewModel
import com.bluedeck.widget.VehicleWidgetProvider

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
    val walkAwayLockEnabled by viewModel.walkAwayLockEnabled.collectAsStateWithLifecycle()
    val walkAwayLockDelaySeconds by viewModel.walkAwayLockDelaySeconds.collectAsStateWithLifecycle()
    val walkAwayBluetoothName by viewModel.walkAwayBluetoothName.collectAsStateWithLifecycle()
    val walkAwayBluetoothAddress by viewModel.walkAwayBluetoothAddress.collectAsStateWithLifecycle()
    val themeModeKey by viewModel.themeMode.collectAsStateWithLifecycle()
    val useDynamicColor by viewModel.useDynamicColor.collectAsStateWithLifecycle()
    val themeMode = ThemeMode.fromKey(themeModeKey)
    val servicePin by viewModel.servicePin.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    LaunchedEffect(distanceUnit) {
        VehicleWidgetProvider.refreshAll(context)
    }

    var showRegionPicker by remember { mutableStateOf(false) }
    var showThemeModePicker by remember { mutableStateOf(false) }
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
                        icon = Icons.Filled.DarkMode,
                        label = "Theme",
                        value = themeMode.label,
                        onClick = { showThemeModePicker = true }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                Icons.Filled.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Dynamic color",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    if (supportsDynamicColor) {
                                        "Use Material You colors from your wallpaper"
                                    } else {
                                        "Requires Android 12 or newer"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                        Switch(
                            checked = useDynamicColor,
                            onCheckedChange = { viewModel.setUseDynamicColor(it) },
                            enabled = supportsDynamicColor
                        )
                    }
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
                                    "Control when BlueDeck asks for fingerprint while your account session is valid",
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
                                    "DAILY" -> "BlueDeck will suppress app-open fingerprint prompts for 24 hours after a successful unlock, as long as the account session is still valid."
                                    "COMMANDS_ONLY" -> "BlueDeck will not prompt when opening the app. Use this with the 30-day account session for fewer interruptions."
                                    "NEVER" -> "BlueDeck will not ask for fingerprint while the account session remains valid."
                                    else -> "BlueDeck will ask again after it has been in the background for about 5 minutes."
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
                    SettingsInfoRow("Original project", "BlueBridge by Nelwyn99")
                    SettingsInfoRow("Repository", "github.com/tracer99/BlueDeck")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "BlueDeck is a fork maintained independently from BlueBridge. It is an unofficial third-party app and is not affiliated with Hyundai or Kia. Use at your own risk.",
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
                Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(18.dp))
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

    if (showThemeModePicker) {
        AlertDialog(
            onDismissRequest = { showThemeModePicker = false },
            title = { Text("Theme") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setThemeMode(mode.storageKey)
                                    showThemeModePicker = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = mode == themeMode,
                                onClick = {
                                    viewModel.setThemeMode(mode.storageKey)
                                    showThemeModePicker = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(mode.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeModePicker = false }) { Text("Cancel") } }
        )
    }

    // ── Logout confirm dialog ─────────────────────────────────────────────────
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = MaterialTheme.colorScheme.error) },
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

