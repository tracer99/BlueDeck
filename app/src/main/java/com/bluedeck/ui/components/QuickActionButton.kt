package com.blueandroid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun QuickActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    compact: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(tint.copy(alpha = if (enabled) 0.1f else 0.04f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                vertical = if (compact) 10.dp else 16.dp,
                horizontal = if (compact) 6.dp else 8.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) tint else tint.copy(alpha = 0.3f),
            modifier = Modifier.size(if (compact) 22.dp else 28.dp)
        )
        Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
        Text(
            text = label,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
