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
val CreditGreen = Color(0xFF4CD98A)
val PendingAmber = Color(0xFFFFC857)

val ParentBackground = Color(0xFF101417)
val ParentSurface = Color(0xFF171D22)
val ParentSurfaceVariant = Color(0xFF20272E)
val ParentPrimary = Color(0xFF8FB3D1)
val ParentSecondary = Color(0xFF6FAF9B)
val ParentOnPrimary = Color(0xFF0B0F14)
val ParentTextSecondary = Color(0xFF9BA7B2)

private val AuthColors = darkColorScheme(
    primary = NeonGreen,
    onPrimary = DarkBackground,
    primaryContainer = Color(0xFF0E3B25),
    onPrimaryContainer = NeonGreen,
    secondary = NeonCyan,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = DarkBackground,
    errorContainer = Color(0xFF4A1F1F),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val ChildColors = darkColorScheme(
    primary = NeonGreen,
    onPrimary = DarkBackground,
    primaryContainer = Color(0xFF0E3B25),
    onPrimaryContainer = NeonGreen,
    secondary = NeonCyan,
    onSecondary = DarkBackground,
    secondaryContainer = Color(0xFF07323A),
    onSecondaryContainer = NeonCyan,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = DarkBackground,
    errorContainer = Color(0xFF4A1F1F),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val ParentColors = darkColorScheme(
    primary = ParentPrimary,
    onPrimary = ParentOnPrimary,
    primaryContainer = Color(0xFF233746),
    onPrimaryContainer = ParentPrimary,
    secondary = ParentSecondary,
    onSecondary = ParentOnPrimary,
    secondaryContainer = Color(0xFF1D332D),
    onSecondaryContainer = ParentSecondary,
    background = ParentBackground,
    onBackground = TextPrimary,
    surface = ParentSurface,
    onSurface = TextPrimary,
    surfaceVariant = ParentSurfaceVariant,
    onSurfaceVariant = ParentTextSecondary,
    error = ErrorRed,
    onError = DarkBackground,
    errorContainer = Color(0xFF4A1F1F),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun BancoDoPaiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AuthColors,
        content = content
    )
}

@Composable
fun ChildTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ChildColors,
        content = content
    )
}

@Composable
fun ParentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ParentColors,
        content = content
    )
}
