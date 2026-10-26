package com.rebuild.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Low-chroma warm neutrals with a muted amber accent (sRGB equivalents).
private val Light = lightColorScheme(
    primary = Color(0xFF7A5A12),
    onPrimary = Color(0xFFFFFAF1),
    primaryContainer = Color(0xFFF6E3BE),
    onPrimaryContainer = Color(0xFF2A1C00),
    secondary = Color(0xFF6A5D48),
    onSecondary = Color(0xFFFFFAF1),
    secondaryContainer = Color(0xFFEFE2CC),
    onSecondaryContainer = Color(0xFF231A0B),
    tertiary = Color(0xFF52634A),
    onTertiary = Color(0xFFF8FBF2),
    tertiaryContainer = Color(0xFFD5E8C8),
    onTertiaryContainer = Color(0xFF10200B),
    error = Color(0xFFA6352B),
    onError = Color(0xFFFFF8F6),
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410001),
    background = Color(0xFFFBF8F3),
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFBF8F3),
    onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFEDE5D9),
    onSurfaceVariant = Color(0xFF4C4639),
    surfaceContainerLowest = Color(0xFFFFFCF8),
    surfaceContainerLow = Color(0xFFF6F2EB),
    surfaceContainer = Color(0xFFF1ECE4),
    surfaceContainerHigh = Color(0xFFEBE6DE),
    surfaceContainerHighest = Color(0xFFE5E0D8),
    outline = Color(0xFF7E7667),
    outlineVariant = Color(0xFFD0C6B5)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFE6C27A),
    onPrimary = Color(0xFF412D00),
    primaryContainer = Color(0xFF5D4300),
    onPrimaryContainer = Color(0xFFF6E3BE),
    secondary = Color(0xFFD4C5AC),
    onSecondary = Color(0xFF392F1E),
    secondaryContainer = Color(0xFF514533),
    onSecondaryContainer = Color(0xFFEFE2CC),
    tertiary = Color(0xFFB9CCAD),
    onTertiary = Color(0xFF253420),
    tertiaryContainer = Color(0xFF3B4B34),
    onTertiaryContainer = Color(0xFFD5E8C8),
    error = Color(0xFFFFB4A9),
    onError = Color(0xFF680003),
    errorContainer = Color(0xFF872019),
    onErrorContainer = Color(0xFFFFDAD5),
    background = Color(0xFF171410),
    onBackground = Color(0xFFEAE1D7),
    surface = Color(0xFF171410),
    onSurface = Color(0xFFEAE1D7),
    surfaceVariant = Color(0xFF4C4639),
    onSurfaceVariant = Color(0xFFD0C6B5),
    surfaceContainerLowest = Color(0xFF110E0B),
    surfaceContainerLow = Color(0xFF1F1B16),
    surfaceContainer = Color(0xFF231F1A),
    surfaceContainerHigh = Color(0xFF2E2924),
    surfaceContainerHighest = Color(0xFF39342E),
    outline = Color(0xFF999080),
    outlineVariant = Color(0xFF4C4639)
)

@Composable
fun RebuildTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, content = content)
}
