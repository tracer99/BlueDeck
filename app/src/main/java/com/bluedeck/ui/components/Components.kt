package com.bluedeck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val SeatHeatOrange = Color(0xFFFF9800)
private val SeatVentBlue = Color(0xFF03A9F4)

// ── Section card wrapper ──────────────────────────────────────────────────────

@Composable
fun ControlSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp)
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}

// ── On/off toggle row ─────────────────────────────────────────────────────────

@Composable
fun ToggleControlRow(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.tertiary,
                checkedTrackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
            )
        )
    }
}

// ── Seat heat / vent level selector ─────────────────────────────────────────

@Composable
fun SeatHeatSelector(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    ventCapable: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(
                seatClimateLabel(value),
                style = MaterialTheme.typography.labelMedium,
                color = when (value) {
                    6, 7, 8 -> SeatHeatOrange
                    3, 4, 5 -> SeatVentBlue
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                },
                fontWeight = FontWeight.SemiBold
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val options = buildList {
                add(2 to "Off")
                add(6 to "Heat L")
                add(7 to "Heat M")
                add(8 to "Heat H")
                if (ventCapable) {
                    add(3 to "Vent L")
                    add(4 to "Vent M")
                    add(5 to "Vent H")
                }
            }
            options.forEach { (level, lbl) ->
                val selected = value == level || (value == 0 && level == 2)
                FilterChip(
                    selected = selected,
                    onClick = { onValueChange(level) },
                    label = { Text(lbl, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (level) {
                            6, 7, 8 -> SeatHeatOrange.copy(alpha = 0.25f)
                            3, 4, 5 -> SeatVentBlue.copy(alpha = 0.25f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        selectedLabelColor = when (level) {
                            6, 7, 8 -> SeatHeatOrange
                            3, 4, 5 -> SeatVentBlue
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                )
            }
        }
    }
}

fun seatClimateLabel(value: Int): String = when (value) {
    6 -> "Heat Low"
    7 -> "Heat Medium"
    8 -> "Heat High"
    3 -> "Vent Low"
    4 -> "Vent Medium"
    5 -> "Vent High"
    0, 2 -> "Off"
    else -> "Level $value"
}
