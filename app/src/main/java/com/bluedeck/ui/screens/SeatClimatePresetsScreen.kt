package com.bluedeck.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AirlineSeatReclineNormal
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bluedeck.data.models.ClimatePreset
import com.bluedeck.data.models.ClimatePresetsStore
import com.bluedeck.data.models.MAX_CLIMATE_DURATION_MINUTES
import com.bluedeck.data.models.MIN_CLIMATE_DURATION_MINUTES
import com.bluedeck.data.models.coerceClimateDurationMinutes
import com.bluedeck.data.models.VehicleFeatureCapabilities
import com.bluedeck.data.models.resolveCapabilities
import com.bluedeck.ui.theme.climateDisplayValueFromF
import com.bluedeck.ui.theme.climateFahrenheitFromDisplay
import com.bluedeck.ui.theme.climateSliderRange
import com.bluedeck.ui.theme.climateSliderSteps
import com.bluedeck.ui.theme.climateTemperatureLabelFromF
import com.bluedeck.viewmodel.VehicleViewModel

private val SeatHeatOrange = Color(0xFFFF9800)
private val SeatVentBlue = Color(0xFF03A9F4)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClimatePresetsScreen(
    vehicleViewModel: VehicleViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val vehicle by vehicleViewModel.selectedVehicle.collectAsStateWithLifecycle()
    val region by vehicleViewModel.region.collectAsStateWithLifecycle()
    val temperatureUnit by vehicleViewModel.temperatureUnit.collectAsStateWithLifecycle()
    val featureCaps = remember(vehicle, region) { vehicle?.resolveCapabilities(region) }
    var presets by remember(temperatureUnit) {
        mutableStateOf(ClimatePresetsStore.load(context, temperatureUnit))
    }
    var resetDialog by remember { mutableStateOf(false) }

    fun updatePreset(index: Int, preset: ClimatePreset) {
        val updated = presets.toMutableList()
        updated[index] = preset
        presets = updated
        ClimatePresetsStore.save(context, updated)
    }

    if (resetDialog) {
        AlertDialog(
            onDismissRequest = { resetDialog = false },
            title = { Text("Reset climate presets?") },
            text = { Text("This will restore the default Warm and Cool climate presets. All Off always stops climate.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        presets = ClimatePresetsStore.defaults(temperatureUnit)
                        ClimatePresetsStore.save(context, presets)
                        resetDialog = false
                    }
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { resetDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Climate Presets", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { resetDialog = true }) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = "Reset presets")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Each start preset sets cabin temperature, duration, and comfort options applied on the dashboard before you start climate. All Off always stops climate.",
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            presets.filterNot { it.stopsClimate }.forEach { preset ->
                val index = presets.indexOf(preset)
                ClimatePresetEditorCard(
                    index = index,
                    preset = preset,
                    featureCaps = featureCaps,
                    temperatureUnit = temperatureUnit,
                    onPresetChange = { updatePreset(index, it) }
                )
            }

            ClimateOffPresetInfoCard()
        }
    }
}

/** Kept for navigation call sites that still reference the old name. */
@Composable
fun SeatClimatePresetsScreen(
    vehicleViewModel: VehicleViewModel,
    onNavigateBack: () -> Unit
) = ClimatePresetsScreen(vehicleViewModel, onNavigateBack)

@Composable
private fun ClimateOffPresetInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Off",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                    .wrapContentSize(Alignment.Center)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "All Off",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Stops cabin climate. Not configurable — it always turns climate off.",
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ClimatePresetEditorCard(
    index: Int,
    preset: ClimatePreset,
    featureCaps: VehicleFeatureCapabilities?,
    temperatureUnit: String,
    onPresetChange: (ClimatePreset) -> Unit
) {
    val displayTemp = climateDisplayValueFromF(preset.tempF, temperatureUnit).toFloat()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "${index + 1}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                        .wrapContentSize(Alignment.Center)
                )
                OutlinedTextField(
                    value = preset.name,
                    onValueChange = { onPresetChange(preset.copy(name = it.take(24))) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Preset name") }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        climateTemperatureLabelFromF(preset.tempF, temperatureUnit),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = displayTemp,
                    onValueChange = {
                        val tempF = climateFahrenheitFromDisplay(it.toInt(), temperatureUnit).toString()
                        onPresetChange(preset.copy(tempF = tempF))
                    },
                    valueRange = climateSliderRange(temperatureUnit),
                    steps = climateSliderSteps(temperatureUnit),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

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
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Defrost", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
                Switch(
                    checked = preset.defrost,
                    onCheckedChange = { onPresetChange(preset.copy(defrost = it)) }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Run Duration",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${preset.durationMinutes} min",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = preset.durationMinutes.toFloat(),
                    onValueChange = {
                        onPresetChange(
                            preset.copy(durationMinutes = coerceClimateDurationMinutes(it.toInt()))
                        )
                    },
                    valueRange = MIN_CLIMATE_DURATION_MINUTES.toFloat()..MAX_CLIMATE_DURATION_MINUTES.toFloat(),
                    steps = 4,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
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
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Heated Steering Wheel", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                    Switch(
                        checked = preset.heatedSteering,
                        onCheckedChange = { onPresetChange(preset.copy(heatedSteering = it)) }
                    )
                }
            }

            if (featureCaps == null || featureCaps.showSeatClimateControls) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Text(
                    "Seat climate",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PresetSeatMap(
                    featureCaps = featureCaps,
                    driverSeat = preset.driverSeat,
                    passengerSeat = preset.passengerSeat,
                    rearLeftSeat = preset.rearLeftSeat,
                    rearRightSeat = preset.rearRightSeat,
                    onDriverSeat = { onPresetChange(preset.copy(driverSeat = it)) },
                    onPassengerSeat = { onPresetChange(preset.copy(passengerSeat = it)) },
                    onRearLeftSeat = { onPresetChange(preset.copy(rearLeftSeat = it)) },
                    onRearRightSeat = { onPresetChange(preset.copy(rearRightSeat = it)) }
                )
            }

            Text(
                preset.summary(temperatureUnit, featureCaps),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PresetSeatMap(
    featureCaps: VehicleFeatureCapabilities?,
    driverSeat: Int,
    passengerSeat: Int,
    rearLeftSeat: Int,
    rearRightSeat: Int,
    onDriverSeat: (Int) -> Unit,
    onPassengerSeat: (Int) -> Unit,
    onRearLeftSeat: (Int) -> Unit,
    onRearRightSeat: (Int) -> Unit
) {
    val showDriver = featureCaps?.driver?.let { it.showHeat || it.showVent } ?: true
    val showPassenger = featureCaps?.passenger?.let { it.showHeat || it.showVent } ?: true
    val showRearLeft = featureCaps?.rearLeft?.let { it.showHeat || it.showVent } ?: false
    val showRearRight = featureCaps?.rearRight?.let { it.showHeat || it.showVent } ?: false
    if (!showDriver && !showPassenger && !showRearLeft && !showRearRight) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showDriver || showPassenger) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showDriver) {
                    val ventCapable = featureCaps?.driver?.ventCapableForSelector ?: false
                    PresetSeatTile(
                        label = "Driver",
                        value = driverSeat,
                        ventCapable = ventCapable,
                        onClick = { onDriverSeat(nextPresetSeatLevel(driverSeat, ventCapable = ventCapable)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (showPassenger) {
                    val ventCapable = featureCaps?.passenger?.ventCapableForSelector ?: false
                    PresetSeatTile(
                        label = "Passenger",
                        value = passengerSeat,
                        ventCapable = ventCapable,
                        onClick = { onPassengerSeat(nextPresetSeatLevel(passengerSeat, ventCapable = ventCapable)) },
                        modifier = Modifier.weight(1f)
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
                    val ventCapable = featureCaps?.rearLeft?.ventCapableForSelector ?: false
                    PresetSeatTile(
                        label = "Rear Left",
                        value = rearLeftSeat,
                        ventCapable = ventCapable,
                        onClick = { onRearLeftSeat(nextPresetSeatLevel(rearLeftSeat, ventCapable = ventCapable)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (showRearRight) {
                    val ventCapable = featureCaps?.rearRight?.ventCapableForSelector ?: false
                    PresetSeatTile(
                        label = "Rear Right",
                        value = rearRightSeat,
                        ventCapable = ventCapable,
                        onClick = { onRearRightSeat(nextPresetSeatLevel(rearRightSeat, ventCapable = ventCapable)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetSeatTile(
    label: String,
    value: Int,
    ventCapable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = presetSeatColor(value)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = if (value == 0 || value == 2) 0.06f else 0.12f),
            contentColor = accent
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = when (value) {
                    6, 7, 8 -> Icons.Filled.Whatshot
                    3, 4, 5 -> Icons.Filled.AcUnit
                    else -> Icons.Filled.AirlineSeatReclineNormal
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
            Text(presetSeatLabel(value), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent)
            if (!ventCapable && value in 3..5) {
                Text("Unsupported", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun nextPresetSeatLevel(current: Int, ventCapable: Boolean): Int {
    val options = if (ventCapable) listOf(2, 6, 7, 8, 3, 4, 5) else listOf(2, 6, 7, 8)
    val index = options.indexOf(current).takeIf { it >= 0 } ?: 0
    return options[(index + 1) % options.size]
}

private fun presetSeatLabel(value: Int): String = when (value) {
    6 -> "Heat Low"
    7 -> "Heat Med"
    8 -> "Heat High"
    3 -> "Vent Low"
    4 -> "Vent Med"
    5 -> "Vent High"
    0, 2 -> "Off"
    else -> "Level $value"
}

@Composable
private fun presetSeatColor(value: Int): Color = when (value) {
    6, 7, 8 -> SeatHeatOrange
    3, 4, 5 -> SeatVentBlue
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun ClimatePreset.summary(
    temperatureUnit: String,
    featureCaps: VehicleFeatureCapabilities?
): String {
    val parts = mutableListOf(
        climateTemperatureLabelFromF(tempF, temperatureUnit),
        "${durationMinutes} min"
    )
    if (defrost) parts += "Defrost"
    if (heatedSteering && featureCaps?.showHeatedSteering != false) parts += "Heated wheel"
    val seats = listOf(
        "Driver" to driverSeat,
        "Passenger" to passengerSeat,
        "Rear L" to rearLeftSeat,
        "Rear R" to rearRightSeat
    ).filter { (_, value) -> value != 0 && value != 2 }
    if (seats.isEmpty()) {
        parts += "Seats off"
    } else {
        parts += seats.joinToString { (seat, value) -> "$seat ${presetSeatLabel(value)}" }
    }
    return parts.joinToString(" · ")
}
