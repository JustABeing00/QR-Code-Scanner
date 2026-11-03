package com.phonapps.qrscanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.phonapps.qrscanner.data.repo.ThemeMode

private val DarkScheme = darkColorScheme(
    primary = MintAccent,
    onPrimary = Color(0xFF06281A),
    primaryContainer = Color(0xFF0E3B27),
    onPrimaryContainer = Color(0xFFB9F4D4),
    secondary = Color(0xFF8FD6AE),
    secondaryContainer = Color(0xFF22402F),
    onSecondaryContainer = Color(0xFFC9EFDB),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = Color(0xFF0B0F13),
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurfaceHigh,
    surfaceContainerHigh = Color(0xFF2B3742),
    surfaceContainerHighest = Color(0xFF35414E),
    outline = Color(0xFF3A4753),
    outlineVariant = Color(0xFF2A343E),
    error = Color(0xFFFF8A80),
    tertiary = WarningAmber
)

private val LightScheme = lightColorScheme(
    primary = MintDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9EFDB),
    onPrimaryContainer = Color(0xFF06301F),
    secondary = Color(0xFF2E7D5B),
    secondaryContainer = Color(0xFFD9EDE2),
    onSecondaryContainer = Color(0xFF0B3D26),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurfaceHigh,
    surfaceContainerHigh = Color(0xFFDCE4EA),
    surfaceContainerHighest = Color(0xFFD2D9E0),
    outline = Color(0xFFC3CDD5),
    outlineVariant = Color(0xFFDFE6EC),
    error = DangerRed,
    tertiary = Color(0xFF9A6B1A)
)

@Composable
fun QrScannerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content
    )
}
