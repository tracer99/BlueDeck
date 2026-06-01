package com.blueandroid.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode(val storageKey: String, val label: String) {
    SYSTEM("system", "System default"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.storageKey == key } ?: SYSTEM
    }
}

val SuccessGreen = Color(0xFF2E7D32)
val WarningAmber = Color(0xFFED6C02)
val ErrorRed = Color(0xFFC62828)
val ChargingGreen = Color(0xFF00897B)

data class BlueAndroidDynamicColors(
    val success: Color = SuccessGreen,
    val warning: Color = WarningAmber,
    val error: Color = ErrorRed,
    val charging: Color = ChargingGreen,
    val commandBanner: Color = Color(0xFF1E293B),
    val dashboardCardBlend: Color = Color(0xFF0B1B48)
)

val LocalBlueAndroidDynamicColors = staticCompositionLocalOf { BlueAndroidDynamicColors() }

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF002C5F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD0E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF546E7A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFE5F1),
    onSecondaryContainer = Color(0xFF071E28),
    tertiary = Color(0xFF006876),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB2EBF2),
    onTertiaryContainer = Color(0xFF003740),
    error = ErrorRed,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8FAFE),
    onBackground = Color(0xFF1A1C23),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C23),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C7CF),
    inverseSurface = Color(0xFF303033),
    inverseOnSurface = Color(0xFFF2F0F4),
    inversePrimary = Color(0xFF9ECAFF),
    surfaceTint = Color(0xFF002C5F),
    scrim = Color(0xFF000000),
    surfaceContainer = Color(0xFFEDF2F7),
    surfaceContainerHigh = Color(0xFFE7ECF2),
    surfaceContainerHighest = Color(0xFFE1E6EC)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00AAD2),
    onPrimary = Color(0xFF003547),
    primaryContainer = Color(0xFF004D65),
    onPrimaryContainer = Color(0xFFB3E5FC),
    secondary = Color(0xFFB0BEC5),
    onSecondary = Color(0xFF1C3040),
    secondaryContainer = Color(0xFF334A5A),
    onSecondaryContainer = Color(0xFFCDE7F8),
    tertiary = Color(0xFF80DEEA),
    onTertiary = Color(0xFF003740),
    tertiaryContainer = Color(0xFF004E5B),
    onTertiaryContainer = Color(0xFFA6EEFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F0F1E),
    onBackground = Color(0xFFE2E2EC),
    surface = Color(0xFF141425),
    onSurface = Color(0xFFE2E2EC),
    surfaceVariant = Color(0xFF1E2030),
    onSurfaceVariant = Color(0xFFC3C6CF),
    outline = Color(0xFF404759),
    outlineVariant = Color(0xFF44474F),
    inverseSurface = Color(0xFFE2E2EC),
    inverseOnSurface = Color(0xFF1B1B23),
    inversePrimary = Color(0xFF002C5F),
    surfaceTint = Color(0xFF00AAD2),
    scrim = Color(0xFF000000),
    surfaceContainer = Color(0xFF1E1E35),
    surfaceContainerHigh = Color(0xFF252540),
    surfaceContainerHighest = Color(0xFF2D2D4A)
)

@Composable
fun BlueAndroidTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val dynamicColors = BlueAndroidDynamicColors(
        commandBanner = if (darkTheme) Color(0xFF111827) else Color(0xFFE2E8F0),
        dashboardCardBlend = if (darkTheme) Color(0xFF0B1B48) else Color(0xFFD0E4FF)
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalBlueAndroidDynamicColors provides dynamicColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BlueAndroidTypography,
            content = content
        )
    }
}
