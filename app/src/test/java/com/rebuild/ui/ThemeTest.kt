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
    fun allExplicitDarkRolesMatchTheInkAndEmberAnchor() {
        val actual = with(Dark) {
            listOf(primary, onPrimary, primaryContainer, onPrimaryContainer,
                secondary, onSecondary, secondaryContainer, onSecondaryContainer,
                tertiary, onTertiary, tertiaryContainer, onTertiaryContainer,
                error, onError, errorContainer, onErrorContainer,
                background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant,
                surfaceContainerLowest, surfaceContainerLow, surfaceContainer, surfaceContainerHigh,
                surfaceContainerHighest, outline, outlineVariant)
        }
        val expected = listOf("FFB94A", "2A1800", "EBA42A", "231500",
            "D4C5AC", "392F1E", "4A3410", "FFDFA3", "9FD6A6", "0F3A1C", "1F4027", "C8EBCB",
            "FFB4A9", "680003", "5C1D18", "FFDAD5", "0F0E0D", "EDE6DD", "0F0E0D", "EDE6DD",
            "34302C", "CFC5B7", "1D1B19", "171514", "221F1D", "2A2724", "34302C", "958B7E", "3C3833")
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
