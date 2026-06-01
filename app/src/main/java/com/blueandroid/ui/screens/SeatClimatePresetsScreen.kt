package com.blueandroid.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AirlineSeatReclineNormal
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext

private val SeatHeatOrange = Color(0xFFFF9800)
private val SeatVentBlue = Color(0xFF03A9F4)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeatClimatePresetsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var presets by remember { mutableStateOf(loadSeatPresetEditorPresets(context)) }
    var resetDialog by remember { mutableStateOf(false) }

    fun updatePreset(index: Int, preset: SeatPresetEditorPreset) {
        val updated = presets.toMutableList()
        updated[index] = preset
        presets = updated
        saveSeatPresetEditorPresets(context, updated)
    }

    if (resetDialog) {
        AlertDialog(
            onDismissRequest = { resetDialog = false },
            title = { Text("Reset seat presets?") },
            text = { Text("This will restore the three default seat climate presets.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        presets = defaultSeatPresetEditorPresets()
                        saveSeatPresetEditorPresets(context, presets)
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
                title = { Text("Seat Presets", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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
                "Name each preset and choose the seat heat/vent level to send when you start climate. Rear seats are limited to heat because rear ventilation is not reported as supported.",
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            presets.take(3).forEachIndexed { index, preset ->
                SeatPresetEditorCard(
                    index = index,
                    preset = preset,
                    onPresetChange = { updatePreset(index, it) }
                )
            }
        }
    }
}

@Composable
private fun SeatPresetEditorCard(
    index: Int,
    preset: SeatPresetEditorPreset,
    onPresetChange: (SeatPresetEditorPreset) -> Unit
) {
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

            PresetSeatMap(
                driverSeat = preset.driverSeat,
                passengerSeat = preset.passengerSeat,
                rearLeftSeat = preset.rearLeftSeat,
                rearRightSeat = preset.rearRightSeat,
                onDriverSeat = { onPresetChange(preset.copy(driverSeat = it)) },
                onPassengerSeat = { onPresetChange(preset.copy(passengerSeat = it)) },
                onRearLeftSeat = { onPresetChange(preset.copy(rearLeftSeat = it)) },
                onRearRightSeat = { onPresetChange(preset.copy(rearRightSeat = it)) }
            )

            Text(
                preset.summary(),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PresetSeatMap(
    driverSeat: Int,
    passengerSeat: Int,
    rearLeftSeat: Int,
    rearRightSeat: Int,
    onDriverSeat: (Int) -> Unit,
    onPassengerSeat: (Int) -> Unit,
    onRearLeftSeat: (Int) -> Unit,
    onRearRightSeat: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetSeatTile(
                label = "Driver",
                value = driverSeat,
                ventCapable = true,
                onClick = { onDriverSeat(nextPresetSeatLevel(driverSeat, ventCapable = true)) },
                modifier = Modifier.weight(1f)
            )
            PresetSeatTile(
                label = "Passenger",
                value = passengerSeat,
                ventCapable = true,
                onClick = { onPassengerSeat(nextPresetSeatLevel(passengerSeat, ventCapable = true)) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetSeatTile(
                label = "Rear Left",
                value = rearLeftSeat,
                ventCapable = false,
                onClick = { onRearLeftSeat(nextPresetSeatLevel(rearLeftSeat, ventCapable = false)) },
                modifier = Modifier.weight(1f)
            )
            PresetSeatTile(
                label = "Rear Right",
                value = rearRightSeat,
                ventCapable = false,
                onClick = { onRearRightSeat(nextPresetSeatLevel(rearRightSeat, ventCapable = false)) },
                modifier = Modifier.weight(1f)
            )
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
    val tileAccent = MaterialTheme.colorScheme.primary
    val icon = when (value) {
        6, 7, 8 -> Icons.Filled.Whatshot
        3, 4, 5 -> Icons.Filled.AcUnit
        else -> Icons.Filled.AirlineSeatReclineNormal
    }

    Surface(
        modifier = modifier
            .height(82.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = tileAccent.copy(alpha = if (value == 0 || value == 2) 0.08f else 0.14f),
        contentColor = accent,
        border = BorderStroke(1.dp, tileAccent.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                fontSize = 12.sp,
                lineHeight = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                presetSeatLabel(value),
                fontSize = 12.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                color = accent
            )
            if (!ventCapable && value in 3..5) {
                // Should not happen through normal cycling, but keeps old saved values readable.
                Text("Unsupported", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private data class SeatPresetEditorPreset(
    val name: String,
    val driverSeat: Int = 2,
    val passengerSeat: Int = 2,
    val rearLeftSeat: Int = 2,
    val rearRightSeat: Int = 2
)

private fun defaultSeatPresetEditorPresets(): List<SeatPresetEditorPreset> = listOf(
    SeatPresetEditorPreset(name = "Warm", driverSeat = 7, passengerSeat = 7, rearLeftSeat = 7, rearRightSeat = 7),
    SeatPresetEditorPreset(name = "Cool", driverSeat = 4, passengerSeat = 4, rearLeftSeat = 2, rearRightSeat = 2),
    SeatPresetEditorPreset(name = "All Off", driverSeat = 2, passengerSeat = 2, rearLeftSeat = 2, rearRightSeat = 2)
)

private fun loadSeatPresetEditorPresets(context: android.content.Context): List<SeatPresetEditorPreset> {
    val prefs = context.getSharedPreferences("seat_climate_presets", android.content.Context.MODE_PRIVATE)
    return defaultSeatPresetEditorPresets().mapIndexed { index, fallback ->
        SeatPresetEditorPreset(
            name = prefs.getString("preset_${index}_name", fallback.name) ?: fallback.name,
            driverSeat = prefs.getInt("preset_${index}_driver", fallback.driverSeat),
            passengerSeat = prefs.getInt("preset_${index}_passenger", fallback.passengerSeat),
            rearLeftSeat = prefs.getInt("preset_${index}_rear_left", fallback.rearLeftSeat),
            rearRightSeat = prefs.getInt("preset_${index}_rear_right", fallback.rearRightSeat)
        )
    }
}

private fun saveSeatPresetEditorPresets(context: android.content.Context, presets: List<SeatPresetEditorPreset>) {
    context.getSharedPreferences("seat_climate_presets", android.content.Context.MODE_PRIVATE)
        .edit()
        .apply {
            presets.take(3).forEachIndexed { index, preset ->
                putString("preset_${index}_name", preset.name.trim().ifBlank { "Preset ${index + 1}" })
                putInt("preset_${index}_driver", preset.driverSeat)
                putInt("preset_${index}_passenger", preset.passengerSeat)
                putInt("preset_${index}_rear_left", preset.rearLeftSeat)
                putInt("preset_${index}_rear_right", preset.rearRightSeat)
            }
        }
        .apply()
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

private fun SeatPresetEditorPreset.summary(): String {
    val active = listOf(
        "Driver" to driverSeat,
        "Passenger" to passengerSeat,
        "Rear L" to rearLeftSeat,
        "Rear R" to rearRightSeat
    ).filter { (_, value) -> value != 0 && value != 2 }

    return if (active.isEmpty()) {
        "All seats off"
    } else {
        active.joinToString(" · ") { (seat, value) -> "$seat ${presetSeatLabel(value)}" }
    }
}
