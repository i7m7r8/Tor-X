package com.torx.theme

import androidx.compose.foundation.isSystemInDarkMode
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TorXDarkColorScheme = darkColorScheme(
    primary = Color(0xFF00D4FF),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF2A3F5F),
    onPrimaryContainer = Color(0xFF00D4FF),
    secondary = Color(0xFF7F8FA3),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1A1A2E),
    onSecondaryContainer = Color(0xFF7F8FA3),
    tertiary = Color(0xFF00D4FF),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF2A3F5F),
    onTertiaryContainer = Color(0xFF00D4FF),
    error = Color(0xFFFF6B6B),
    onError = Color.White,
    errorContainer = Color(0xFF8B0000),
    onErrorContainer = Color(0xFFFF6B6B),
    background = Color(0xFF0F0F1E),
    onBackground = Color.White,
    surface = Color(0xFF1A1A2E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2A3F5F),
    onSurfaceVariant = Color(0xFF7F8FA3),
    outline = Color(0xFF2A3F5F),
    outlineVariant = Color(0xFF1A1A2E),
    scrim = Color.Black
)

@Composable
fun TorXTheme(
    darkTheme: Boolean = isSystemInDarkMode(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) TorXDarkColorScheme else TorXDarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
