package com.rebuild.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Warm neutrals with a committed amber accent (sRGB equivalents). Amber carries primary actions, current selection
// and state; surfaces step clearly so docks, bars and lists separate without borders.
internal val Light = lightColorScheme(
    primary = Color(0xFF805300),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDDA6),
    onPrimaryContainer = Color(0xFF2B1700),
    secondary = Color(0xFF6B4E0E),
    onSecondary = Color(0xFFFFFAF1),
    secondaryContainer = Color(0xFFFFE3B0),
    onSecondaryContainer = Color(0xFF2B1700),
    tertiary = Color(0xFF52634A),
    onTertiary = Color(0xFFF8FBF2),
    tertiaryContainer = Color(0xFFD5E8C8),
    onTertiaryContainer = Color(0xFF10200B),
    error = Color(0xFFA6352B),
    onError = Color(0xFFFFF8F6),
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410001),
    background = Color(0xFFFAF9F7),
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFAF9F7),
    onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFE9E6E1),
    onSurfaceVariant = Color(0xFF514C45),
    surfaceContainerLowest = Color(0xFFFDFCFB),
    surfaceContainerLow = Color(0xFFF7F5F2),
    surfaceContainer = Color(0xFFEFEBE5),
    surfaceContainerHigh = Color(0xFFE7E2DB),
    surfaceContainerHighest = Color(0xFFDED8CF),
    outline = Color(0xFF6F675C),
    outlineVariant = Color(0xFFCEC8BF)
)

internal val Dark = darkColorScheme(
    primary = Color(0xFFFFB94A),
    onPrimary = Color(0xFF3A2500),
    primaryContainer = Color(0xFF5C3D00),
    onPrimaryContainer = Color(0xFFFFDDA6),
    secondary = Color(0xFFD4C5AC),
    onSecondary = Color(0xFF392F1E),
    secondaryContainer = Color(0xFF5A4318),
    onSecondaryContainer = Color(0xFFFFE3B0),
    tertiary = Color(0xFFB9CCAD),
    onTertiary = Color(0xFF253420),
    tertiaryContainer = Color(0xFF3B4B34),
    onTertiaryContainer = Color(0xFFD5E8C8),
    error = Color(0xFFFFB4A9),
    onError = Color(0xFF680003),
    errorContainer = Color(0xFF872019),
    onErrorContainer = Color(0xFFFFDAD5),
    background = Color(0xFF121010),
    onBackground = Color(0xFFEAE1D7),
    surface = Color(0xFF121010),
    onSurface = Color(0xFFEAE1D7),
    surfaceVariant = Color(0xFF4C4639),
    onSurfaceVariant = Color(0xFFD0C6B5),
    surfaceContainerLowest = Color(0xFF0C0A09),
    surfaceContainerLow = Color(0xFF1C1916),
    surfaceContainer = Color(0xFF221E1A),
    surfaceContainerHigh = Color(0xFF2D2823),
    surfaceContainerHighest = Color(0xFF38322B),
    outline = Color(0xFF999080),
    outlineVariant = Color(0xFF4C4639)
)

// Numbers line up everywhere: loads, reps, times and counts are read in columns and against last time.
private fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

/** One Roboto scale. Weight, not extra sizes, carries emphasis: titles and numbers lean heavy, body stays regular. */
internal val RebuildTypography = Typography().run {
    Typography(
        displayLarge = displayLarge.tabular(),
        displayMedium = displayMedium.copy(fontWeight = FontWeight.SemiBold).tabular(),
        displaySmall = displaySmall.copy(fontWeight = FontWeight.SemiBold).tabular(),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.SemiBold).tabular(),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold).tabular(),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp).tabular(),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold).tabular(),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Bold).tabular(),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.Bold).tabular(),
        bodyLarge = bodyLarge.tabular(),
        bodyMedium = bodyMedium.tabular(),
        bodySmall = bodySmall.tabular(),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold).tabular(),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.SemiBold).tabular(),
        labelSmall = labelSmall.tabular()
    )
}

/** Corner radii by component size: chips and fields 8, cards 12, sheets 16, dialogs 28. */
internal val RebuildShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun RebuildTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = RebuildTypography,
        shapes = RebuildShapes,
        content = content
    )
}
