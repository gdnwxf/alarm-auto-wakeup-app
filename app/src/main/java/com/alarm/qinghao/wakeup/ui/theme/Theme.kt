package com.alarm.qinghao.wakeup.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF6C4CF1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E0FF),
    onPrimaryContainer = Color(0xFF1D1237),
    secondary = Color(0xFF5B5A6B),
    background = Color(0xFFF7F6FB),
    onBackground = Color(0xFF1B1A21),
    surface = Color(0xFFF7F6FB),
    onSurface = Color(0xFF1B1A21),
    surfaceVariant = Color(0xFFE9E7F2),
    onSurfaceVariant = Color(0xFF4A4757),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9A6FF),
    onPrimary = Color(0xFF241A45),
    primaryContainer = Color(0xFF3A2C6B),
    onPrimaryContainer = Color(0xFFE7E0FF),
    secondary = Color(0xFFC8C4D9),
    background = Color(0xFF121016),
    onBackground = Color(0xFFE7E3EE),
    surface = Color(0xFF121016),
    onSurface = Color(0xFFE7E3EE),
    surfaceVariant = Color(0xFF262230),
    onSurfaceVariant = Color(0xFFC9C4D6),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

@Composable
fun AlarmQinghaoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
