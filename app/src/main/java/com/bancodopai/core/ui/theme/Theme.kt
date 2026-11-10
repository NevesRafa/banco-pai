package com.bancodopai.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NeonGreen = Color(0xFF00E676)
val NeonCyan = Color(0xFF00E5FF)
val DarkBackground = Color(0xFF0B0F14)
val DarkSurface = Color(0xFF121820)
val DarkSurfaceVariant = Color(0xFF1C2530)
val TextPrimary = Color(0xFFF2F6FA)
val TextSecondary = Color(0xFF9AA7B4)
val ErrorRed = Color(0xFFFF5252)

private val DarkColors = darkColorScheme(
    primary = NeonGreen,
    onPrimary = DarkBackground,
    secondary = NeonCyan,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = DarkBackground
)

@Composable
fun BancoDoPaiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
