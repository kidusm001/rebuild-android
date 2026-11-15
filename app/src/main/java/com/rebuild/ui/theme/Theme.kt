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
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Ink + Ember. A warm tinted canvas with lighter (light) or raised (dark) cards, separated by tone, never borders.
// Filled actions are ink in light and amber in dark; signal amber (primaryContainer) is always a fill with ink on it,
// never a lone edge on the light canvas.
internal val Light = lightColorScheme(
    primary = Color(0xFF1C1915),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFB224),
    onPrimaryContainer = Color(0xFF231500),
    secondary = Color(0xFF6B4E0E),
    onSecondary = Color(0xFFFFFAF1),
    secondaryContainer = Color(0xFFFFDFA3),
    onSecondaryContainer = Color(0xFF2B1A00),
    tertiary = Color(0xFF2F6B3C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC8EBCB),
    onTertiaryContainer = Color(0xFF0F3A1C),
    error = Color(0xFFA3302A),
    onError = Color(0xFFFFF8F6),
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410001),
    background = Color(0xFFF3EFE9),
    onBackground = Color(0xFF1C1915),
    surface = Color(0xFFF3EFE9),
    onSurface = Color(0xFF1C1915),
    surfaceVariant = Color(0xFFE4DDD3),
    onSurfaceVariant = Color(0xFF4F483F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F5F0),
    surfaceContainer = Color(0xFFECE6DE),
    surfaceContainerHigh = Color(0xFFE4DDD3),
    surfaceContainerHighest = Color(0xFFDAD2C6),
    outline = Color(0xFF6F675C),
    outlineVariant = Color(0xFFD3CBC0)
)

internal val Dark = darkColorScheme(
    primary = Color(0xFFFFB94A),
    onPrimary = Color(0xFF2A1800),
    primaryContainer = Color(0xFFFFB224),
    onPrimaryContainer = Color(0xFF231500),
    secondary = Color(0xFFD4C5AC),
    onSecondary = Color(0xFF392F1E),
    secondaryContainer = Color(0xFF4A3410),
    onSecondaryContainer = Color(0xFFFFDFA3),
    tertiary = Color(0xFF9FD6A6),
    onTertiary = Color(0xFF0F3A1C),
    tertiaryContainer = Color(0xFF1F4027),
    onTertiaryContainer = Color(0xFFC8EBCB),
    error = Color(0xFFFFB4A9),
    onError = Color(0xFF680003),
    errorContainer = Color(0xFF5C1D18),
    onErrorContainer = Color(0xFFFFDAD5),
    background = Color(0xFF0F0E0D),
    onBackground = Color(0xFFEDE6DD),
    surface = Color(0xFF0F0E0D),
    onSurface = Color(0xFFEDE6DD),
    surfaceVariant = Color(0xFF34302C),
    onSurfaceVariant = Color(0xFFCFC5B7),
    surfaceContainerLowest = Color(0xFF1D1B19),
    surfaceContainerLow = Color(0xFF171514),
    surfaceContainer = Color(0xFF221F1D),
    surfaceContainerHigh = Color(0xFF2A2724),
    surfaceContainerHighest = Color(0xFF34302C),
    outline = Color(0xFF958B7E),
    outlineVariant = Color(0xFF3C3833)
)

// Numbers line up everywhere: loads, reps, times and counts are read in columns and against last time.
private fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

/**
 * Numbers the lifter reads at a glance (the rest countdown, set values, stat tiles) use the platform's condensed
 * sans, bold and tabular; it ships with Android, so the app stays offline, and its narrow figures survive 200% text.
 */
internal val Numerals = FontFamily(Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Bold))

/**
 * Roboto for words, condensed for numerals. Display and headline sizes track tight; overlines (labelMedium) are
 * small, bold and spaced.
 */
internal val RebuildTypography = Typography().run {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = Numerals, fontWeight = FontWeight.Bold, fontSize = 64.sp,
            lineHeight = 68.sp, letterSpacing = (-1.5).sp).tabular(),
        displayMedium = displayMedium.copy(fontFamily = Numerals, fontWeight = FontWeight.Bold, fontSize = 48.sp,
            lineHeight = 52.sp, letterSpacing = (-1).sp).tabular(),
        displaySmall = displaySmall.copy(fontFamily = Numerals, fontWeight = FontWeight.Bold, fontSize = 36.sp,
            lineHeight = 40.sp, letterSpacing = (-0.6).sp).tabular(),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp,
            letterSpacing = (-0.8).sp).tabular(),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp,
            letterSpacing = (-0.5).sp).tabular(),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp,
            letterSpacing = (-0.4).sp).tabular(),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp).tabular(),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp).tabular(),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.sp).tabular(),
        bodyLarge = bodyLarge.copy(letterSpacing = 0.sp).tabular(),
        bodyMedium = bodyMedium.copy(letterSpacing = 0.sp).tabular(),
        bodySmall = bodySmall.copy(letterSpacing = 0.1.sp).tabular(),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.1.sp).tabular(),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp).tabular(),
        labelSmall = labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp).tabular()
    )
}

/** Fields and chips 12, cards and list groups 20, docks and sheets 28, dialogs 32. Buttons are full pills. */
internal val RebuildShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
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
