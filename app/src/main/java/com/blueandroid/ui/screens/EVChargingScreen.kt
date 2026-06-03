package com.blueandroid.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
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
import com.blueandroid.data.models.CommandHistoryEntry
import com.blueandroid.data.models.EVStatus
import com.blueandroid.ui.components.ControlSection
import com.blueandroid.ui.components.CommandStatusBanner
import com.blueandroid.ui.theme.*
import com.blueandroid.viewmodel.CommandStatus
import com.blueandroid.viewmodel.VehicleViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EVChargingScreen(
    vehicleViewModel: VehicleViewModel,
    onNavigateBack: () -> Unit
) {
    val status by vehicleViewModel.vehicleStatus.collectAsStateWithLifecycle()
    val evStatus = status?.evStatus
    val commandState by vehicleViewModel.commandState.collectAsStateWithLifecycle()
    val lastStatusRefresh by vehicleViewModel.lastStatusRefresh.collectAsStateWithLifecycle()
    val commandHistory by vehicleViewModel.commandHistory.collectAsStateWithLifecycle()
    val distanceUnit by vehicleViewModel.distanceUnit.collectAsStateWithLifecycle()
    val evCommandHistory = commandHistory
        .filter { entry -> entry.title.contains("charge", ignoreCase = true) }
        .take(5)
    var pendingChargeAction by remember { mutableStateOf<String?>(null) }

    val reportedAcTarget = evStatus?.acChargeTarget
    val reportedDcTarget = evStatus?.dcChargeTarget
    val reportedTargets = evStatus?.reservChargeInfos?.targets.orEmpty()

    var acTarget by remember(reportedAcTarget) { mutableStateOf((reportedAcTarget ?: 80).toFloat()) }
    var dcTarget by remember(reportedDcTarget) { mutableStateOf((reportedDcTarget ?: 80).toFloat()) }
    var pendingTargetConfirm by remember { mutableStateOf(false) }


    pendingChargeAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingChargeAction = null },
            title = { Text(if (action == "start") "Start Charging?" else "Stop Charging?") },
            text = {
                Text(
                    if (action == "start")
                        "Send the start charging command to the vehicle?"
                    else
                        "Send the stop charging command to the vehicle?"
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (action == "start") vehicleViewModel.startCharging() else vehicleViewModel.stopCharging()
                    pendingChargeAction = null
                }) {
                    Text(if (action == "start") "Start Charging" else "Stop Charging")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingChargeAction = null }) { Text("Cancel") }
            }
        )
    }

    if (pendingTargetConfirm) {
        AlertDialog(
            onDismissRequest = { pendingTargetConfirm = false },
            title = { Text("Set Charge Targets?") },
            text = {
                Text("Set AC charging to ${acTarget.toInt()}% and DC fast charging to ${dcTarget.toInt()}%?")
            },
            confirmButton = {
                Button(onClick = {
                    vehicleViewModel.setChargeTarget(
                        acTarget = acTarget.toInt(),
                        dcTarget = dcTarget.toInt()
                    )
                    pendingTargetConfirm = false
                }) {
                    Text("Set Targets")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTargetConfirm = false }) { Text("Cancel") }
            }
        )
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EV Charging", fontWeight = FontWeight.Bold) },
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
        ) {
            AnimatedVisibility(
                visible = commandState.status != CommandStatus.IDLE,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                CommandStatusBanner(
                    commandState = commandState,
                    compact = true
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Battery overview ──────────────────────────────────────────────
            ControlSection(title = "Battery") {
                if (evStatus != null) {
                    val isActivelyCharging = evStatus.batteryCharge || ((evStatus.chargingPowerKw ?: 0.0) > 0.0)
                    val displayedBatteryLevel = evStatus.batteryStatus
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AnimatedBatteryFillPanel(
                            batteryLevel = displayedBatteryLevel,
                            isCharging = isActivelyCharging,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (displayedBatteryLevel <= 20) {
                                            BatteryWarningBadge(
                                                batteryLevel = displayedBatteryLevel,
                                                modifier = Modifier.size(28.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        Text(
                                            "$displayedBatteryLevel%",
                                            style = MaterialTheme.typography.displayLarge.copy(fontSize = androidx.compose.ui.unit.TextUnit(48f, androidx.compose.ui.unit.TextUnitType.Sp)),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            if (isActivelyCharging) "Charging" else "Not charging",
                                            color = if (isActivelyCharging) ChargingGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            evStatus.plugStatusLabel,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                if (isActivelyCharging && evStatus.chargingSpeedLabel != "Unavailable from vehicle status") {
                                    Text(
                                        "Charging speed: ${evStatus.chargingSpeedLabel}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ChargingGreen,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        EVChargingDetailGrid(
                            evStatus = evStatus,
                            acTarget = reportedAcTarget,
                            dcTarget = reportedDcTarget,
                            lastStatusRefresh = lastStatusRefresh,
                            distanceUnit = distanceUnit
                        )

                        evStatus.remainChargeTime.firstOrNull()?.time?.let { time ->
                            InfoRow(
                                icon = Icons.Filled.Timer,
                                label = "Remaining charge time",
                                value = "~${time.value} ${if (time.unit == 1) "min" else "hr"}"
                            )
                        }
                    }
                } else {
                    Text(
                        "No battery data — refresh status on the dashboard first.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // ── Charge commands ───────────────────────────────────────────────
            ControlSection(title = "Charge Commands") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { pendingChargeAction = "start" },
                            modifier = Modifier.weight(1f),
                            enabled = evStatus != null
                        ) {
                            Icon(Icons.Filled.Power, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Start")
                        }
                        OutlinedButton(
                            onClick = { pendingChargeAction = "stop" },
                            modifier = Modifier.weight(1f),
                            enabled = evStatus != null
                        ) {
                            Icon(Icons.Filled.Stop, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Stop")
                        }
                    }
                }
            }


            // ── Charge targets ────────────────────────────────────────────────
            ControlSection(title = "Charge Targets") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val targetSummary = when {
                        reportedAcTarget != null || reportedDcTarget != null -> {
                            "Current vehicle targets: AC ${reportedAcTarget?.let { "$it%" } ?: "not reported"} • DC ${reportedDcTarget?.let { "$it%" } ?: "not reported"}"
                        }
                        reportedTargets.isNotEmpty() -> {
                            "Charge targets are reported by the vehicle, but BlueAndroid cannot confidently map them to AC/DC yet."
                        }
                        else -> "Current vehicle targets unavailable until the next vehicle-status refresh."
                    }

                    Text(
                        targetSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )


                    ChargeTargetSlider(
                        label = "AC target",
                        value = acTarget,
                        onValueChange = { acTarget = it },
                        icon = Icons.Filled.Power
                    )

                    ChargeTargetSlider(
                        label = "DC target",
                        value = dcTarget,
                        onValueChange = { dcTarget = it },
                        icon = Icons.Filled.Bolt
                    )

                    Button(
                        onClick = { pendingTargetConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = evStatus != null
                    ) {
                        Icon(Icons.Filled.Save, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Set AC ${acTarget.toInt()}% / DC ${dcTarget.toInt()}%")
                    }

                    OutlinedButton(
                        onClick = { vehicleViewModel.refreshStatus(forceFromServer = true) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Refresh Targets From Vehicle")
                    }

                    if (reportedTargets.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Raw target data from vehicle status",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            reportedTargets.forEach { target ->
                                val interpretedLabel = when (target.plugType) {
                                    1 -> "AC / slow"
                                    0 -> "DC / fast"
                                    2 -> "Plug type 2"
                                    3 -> "Plug type 3"
                                    else -> "Plug type ${target.plugType}"
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        interpretedLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                    )
                                    Text(
                                        "${target.targetSoc}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        "Allowed values are 50, 60, 70, 80, 90, and 100%. After setting targets, refresh once the vehicle has had a moment to sync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }


                if (evCommandHistory.isNotEmpty()) {
                    ControlSection(title = "Recent EV Commands") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            evCommandHistory.forEach { entry ->
                                CommandHistoryRow(entry = entry)
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun BatteryWarningBadge(
    batteryLevel: Int,
    modifier: Modifier = Modifier
) {
    val tint = if (batteryLevel <= 10) ErrorRed else WarningAmber
    Icon(
        imageVector = Icons.Filled.Warning,
        contentDescription = if (batteryLevel <= 10) "Critical battery warning" else "Low battery warning",
        tint = tint,
        modifier = modifier
    )
}

@Composable
private fun EVChargingDetailGrid(
    evStatus: EVStatus,
    acTarget: Int?,
    dcTarget: Int?,
    lastStatusRefresh: Long,
    distanceUnit: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            EVDetailTile(
                label = "Range",
                value = formatDistanceFromMiles(evStatus.rangeMiles, distanceUnit),
                icon = Icons.Filled.Route,
                modifier = Modifier.weight(1f)
            )
            EVDetailTile(
                label = "Power",
                value = evStatus.chargingSpeedLabel.takeUnless { it == "Unavailable from vehicle status" } ?: "—",
                icon = Icons.Filled.Bolt,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            EVDetailTile(
                label = "AC target",
                value = acTarget?.let { "$it%" } ?: "—",
                icon = Icons.Filled.Power,
                modifier = Modifier.weight(1f)
            )
            EVDetailTile(
                label = "DC target",
                value = dcTarget?.let { "$it%" } ?: "—",
                icon = Icons.Filled.Bolt,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            EVDetailTile(
                label = "Charge port",
                value = when (evStatus.chargePortDoorOpen) {
                    0 -> "Closed"
                    1 -> "Open"
                    else -> "—"
                },
                icon = Icons.Filled.MeetingRoom,
                modifier = Modifier.weight(1f)
            )
            EVDetailTile(
                label = "Updated",
                value = formatShortTime(lastStatusRefresh),
                icon = Icons.Filled.Schedule,
                modifier = Modifier.weight(1f)
            )
        }
        InfoRow(
            icon = Icons.Filled.EvStation,
            label = "Plug status",
            value = evStatus.plugStatusLabel
        )
        if (evStatus.batteryPrecondition) {
            InfoRow(
                icon = Icons.Filled.DeviceThermostat,
                label = "Battery preconditioning",
                value = "Active"
            )
        }
        if (evStatus.dischargingLimit > 0) {
            InfoRow(
                icon = Icons.Filled.Power,
                label = "Discharge limit",
                value = "${evStatus.dischargingLimit}%"
            )
        }
    }
}

@Composable
private fun EVDetailTile(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f))
                Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CommandHistoryRow(entry: CommandHistoryEntry) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (entry.successful) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = if (entry.successful) SuccessGreen else ErrorRed,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                if (entry.detail.isNotBlank()) {
                    Text(entry.detail, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                formatShortTime(entry.timestampMillis),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private fun formatShortTime(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return "—"
    return SimpleDateFormat("h:mm a", Locale.US).format(Date(timestampMillis))
}

@Composable
private fun ChargeTargetSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val accentColor = MaterialTheme.colorScheme.primary

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "${value.toInt()}%",
                color = accentColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = { raw ->
                val snapped = (raw / 10f).toInt().coerceIn(5, 10) * 10f
                onValueChange(snapped)
            },
            valueRange = 50f..100f,
            steps = 4
        )
    }
}

@Composable
private fun AnimatedBatteryFillPanel(
    batteryLevel: Int,
    isCharging: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val clampedBatteryLevel = batteryLevel.coerceIn(0, 100)
    val clampedProgress = clampedBatteryLevel / 100f
    val fillColor = batteryChargeLevelColor(clampedBatteryLevel)
    val fillStartColor = blendBatteryColors(fillColor, Color.Black, 0.18f)
    val fillEndColor = blendBatteryColors(fillColor, Color.White, 0.20f)

    val transition = rememberInfiniteTransition(label = "batteryPanelFill")
    val sweepProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "batteryPanelSweep"
    )
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.24f,
        targetValue = 0.52f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "batteryPanelPulse"
    )

    val cornerRadius = 22.dp
    val panelBaseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.065f)
    val sweepEdgeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    val sweepTrailingColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    val glossTopColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(panelBaseColor)
            .drawWithContent {
                val rawFilledWidth = size.width * clampedProgress
                val filledWidth = if (rawFilledWidth > 0f) rawFilledWidth.coerceAtLeast(10.dp.toPx()).coerceAtMost(size.width) else 0f
                val radiusPx = cornerRadius.toPx()

                if (filledWidth > 0f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                fillStartColor.copy(alpha = if (isCharging) 0.70f else 0.56f),
                                fillColor.copy(alpha = if (isCharging) 0.66f else 0.52f),
                                fillEndColor.copy(alpha = if (isCharging) 0.58f else 0.44f)
                            ),
                            startX = 0f,
                            endX = filledWidth
                        ),
                        size = Size(filledWidth, size.height),
                        cornerRadius = CornerRadius(radiusPx, radiusPx)
                    )

                    if (isCharging) {
                        clipRect(left = 0f, top = 0f, right = filledWidth, bottom = size.height) {
                            drawRoundRect(
                                color = ChargingGreen.copy(alpha = pulseAlpha),
                                size = Size(filledWidth, size.height),
                                cornerRadius = CornerRadius(radiusPx, radiusPx)
                            )

                            val sweepWidth = 96.dp.toPx()
                            val sweepX = (filledWidth + sweepWidth) * sweepProgress - sweepWidth
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        sweepEdgeColor,
                                        Color(0xFFC8FFD8).copy(alpha = 0.92f),
                                        sweepTrailingColor,
                                        Color.Transparent
                                    ),
                                    startX = sweepX,
                                    endX = sweepX + sweepWidth
                                ),
                                topLeft = Offset(sweepX, 0f),
                                size = Size(sweepWidth, size.height)
                            )
                        }
                    }
                }

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            glossTopColor,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.12f)
                        )
                    ),
                    size = size,
                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                )

                drawContent()
            }
    ) {
        content()
    }
}

private fun batteryChargeLevelColor(level: Int): Color {
    val clamped = level.coerceIn(0, 100)
    val brightRed = Color(0xFFFF1744)
    val orange = Color(0xFFFF9500)
    val yellow = Color(0xFFFFD60A)
    val brightGreen = Color(0xFF30D158)
    val lightBlue = Color(0xFF64D2FF)

    return when {
        clamped <= 10 -> brightRed
        clamped < 20 -> blendBatteryColors(brightRed, orange, (clamped - 10) / 10f)
        clamped < 30 -> blendBatteryColors(orange, yellow, (clamped - 20) / 10f)
        clamped < 50 -> blendBatteryColors(yellow, brightGreen, (clamped - 30) / 20f)
        clamped <= 80 -> brightGreen
        else -> blendBatteryColors(brightGreen, lightBlue, (clamped - 80) / 20f)
    }
}

private fun blendBatteryColors(start: Color, end: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * t,
        green = start.green + (end.green - start.green) * t,
        blue = start.blue + (end.blue - start.blue) * t,
        alpha = start.alpha + (end.alpha - start.alpha) * t
    )
}

