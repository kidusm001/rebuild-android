package com.rebuild.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.rebuild.ui.theme.Dark
import com.rebuild.ui.theme.Light
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    @Test
    fun allExplicitDarkRolesMatchThe0112BolderAnchor() {
        val actual = with(Dark) {
            listOf(primary, onPrimary, primaryContainer, onPrimaryContainer,
                secondary, onSecondary, secondaryContainer, onSecondaryContainer,
                tertiary, onTertiary, tertiaryContainer, onTertiaryContainer,
                error, onError, errorContainer, onErrorContainer,
                background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant,
                surfaceContainerLowest, surfaceContainerLow, surfaceContainer, surfaceContainerHigh,
                surfaceContainerHighest, outline, outlineVariant)
        }
        val expected = listOf("FFB94A", "3A2500", "5C3D00", "FFDDA6",
            "D4C5AC", "392F1E", "5A4318", "FFE3B0", "B9CCAD", "253420", "3B4B34", "D5E8C8",
            "FFB4A9", "680003", "872019", "FFDAD5", "121010", "EAE1D7", "121010", "EAE1D7",
            "4C4639", "D0C6B5", "0C0A09", "1C1916", "221E1A", "2D2823", "38322B", "999080", "4C4639")
        assertEquals(expected.map { (0xFF000000L or it.toLong(16)).toInt() }, actual.map { it.toArgb() })
    }

    @Test
    fun lightActiveTextFocusSelectionAndErrorPairsMeetContrastThresholds() {
        with(Light) {
            listOf(surface, surfaceContainerLowest, surfaceContainerLow, surfaceContainer,
                surfaceContainerHigh, surfaceContainerHighest).forEach { base ->
                listOf(onSurface, onSurfaceVariant, primary, secondary, error).forEach {
                    contrast(it, base, 4.5)
                }
                contrast(outline, base, 3.0)
                contrast(secondary, base, 3.0)
            }
            listOf(onPrimary to primary, onPrimaryContainer to primaryContainer,
                onSecondaryContainer to secondaryContainer, onTertiaryContainer to tertiaryContainer,
                onErrorContainer to errorContainer, onError to error).forEach { (text, base) ->
                contrast(text, base, 4.5)
            }
        }
    }

    @Test
    fun darkActiveTextAndContainerPairsMeetContrastThresholds() {
        with(Dark) {
            listOf(surface, surfaceContainerLowest, surfaceContainerLow, surfaceContainer,
                surfaceContainerHigh, surfaceContainerHighest).forEach { base ->
                listOf(onSurface, onSurfaceVariant, primary, error).forEach { contrast(it, base, 4.5) }
                contrast(outline, base, 3.0)
            }
            listOf(onPrimary to primary, onPrimaryContainer to primaryContainer,
                onSecondaryContainer to secondaryContainer, onTertiaryContainer to tertiaryContainer,
                onErrorContainer to errorContainer, onError to error).forEach { (text, base) ->
                contrast(text, base, 4.5)
            }
        }
    }

    @Test
    fun quietSecondaryActionsRemainReadableInBothThemes() {
        listOf(Light, Dark).forEach {
            contrast(it.onSurfaceVariant, it.surface, 4.5)
            contrast(it.onSurfaceVariant, it.secondaryContainer, 4.5)
        }
    }

    private fun contrast(text: Color, base: Color, minimum: Double) {
        val a = text.luminance().toDouble()
        val b = base.luminance().toDouble()
        val ratio = (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
        assertTrue("Contrast $ratio below $minimum for ${text.toArgb()} / ${base.toArgb()}", ratio >= minimum)
    }
}
