package com.bluedeck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bluedeck.ui.theme.*
import com.bluedeck.viewmodel.CommandState
import com.bluedeck.viewmodel.CommandStatus

@Composable
fun CommandStatusBanner(
    commandState: CommandState,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val dynamicColors = LocalBlueDeckDynamicColors.current
    val (bgColor, icon, iconTint, showSpinner) = when (commandState.status) {
        CommandStatus.LOADING -> Quad(
            dynamicColors.commandBanner,
            null,
            MaterialTheme.colorScheme.primary,
            true
        )
        CommandStatus.ACCEPTED -> Quad(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
            Icons.Filled.CloudDone,
            MaterialTheme.colorScheme.primary,
            false
        )
        CommandStatus.REFRESHING -> Quad(
            dynamicColors.commandBanner,
            null,
            MaterialTheme.colorScheme.primary,
            true
        )
        CommandStatus.SUCCESS -> Quad(
            dynamicColors.success.copy(alpha = 0.2f),
            Icons.Filled.CheckCircle,
            dynamicColors.success,
            false
        )
        CommandStatus.ERROR -> Quad(
            dynamicColors.error.copy(alpha = 0.2f),
            Icons.Filled.ErrorOutline,
            dynamicColors.error,
            false
        )
        CommandStatus.IDLE -> Quad(Color.Transparent, null, Color.Transparent, false)
    }

    val outerHorizontalPadding = if (compact) 14.dp else 24.dp
    val outerVerticalPadding = if (compact) 5.dp else 8.dp
    val innerHorizontalPadding = if (compact) 12.dp else 20.dp
    val innerVerticalPadding = if (compact) 8.dp else 12.dp
    val cornerRadius = if (compact) 16.dp else 24.dp
    val iconSize = if (compact) 14.dp else 16.dp
    val textGap = if (compact) 8.dp else 10.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = outerHorizontalPadding, vertical = outerVerticalPadding)
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgColor)
            .padding(horizontal = innerHorizontalPadding, vertical = innerVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (showSpinner) {
            CircularProgressIndicator(
                modifier = Modifier.size(iconSize),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )
        } else if (icon != null) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(iconSize))
        }
        Spacer(Modifier.width(textGap))
        Column(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 2.dp)
        ) {
            Text(
                text = commandState.message,
                color = MaterialTheme.colorScheme.onSurface,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (commandState.detail.isNotBlank()) {
                Text(
                    text = commandState.detail,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
