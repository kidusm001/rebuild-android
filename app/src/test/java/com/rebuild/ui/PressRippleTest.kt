package com.rebuild.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import com.rebuild.ui.theme.Dark
import com.rebuild.ui.theme.Light
import com.rebuild.ui.theme.RebuildTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Presses tint ember everywhere, so a tap never flashes Material's neutral grey/white before the amber selection. */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
class PressRippleTest {
    @get:Rule val compose = createComposeRule()

    private class Seen {
        var themed: RippleConfiguration? = null
        var filledButton: RippleConfiguration? = null
        var confirmPill: RippleConfiguration? = null
    }

    private fun ripples(dark: Boolean): Seen {
        val seen = Seen()
        compose.setContent {
            RebuildTheme(darkTheme = dark) {
                seen.themed = LocalRippleConfiguration.current
                // Read inside the real controls, so dropping either one's InkPress fails here.
                Button(onClick = {}) { seen.filledButton = LocalRippleConfiguration.current; Text("Log set") }
                CompositionLocalProvider(LocalDialogSlot provides DialogSlot.CONFIRM) {
                    TextButton(onClick = {}) { seen.confirmPill = LocalRippleConfiguration.current; Text("Save") }
                }
            }
        }
        compose.waitForIdle()
        return seen
    }

    private fun check(dark: Boolean) {
        val scheme = if (dark) Dark else Light
        val seen = ripples(dark)
        assertEquals(scheme.primaryContainer, seen.themed!!.color)
        val alpha = seen.themed!!.rippleAlpha!!
        // Visible, but on dark below the selection pill's strength; focus never as strong as a press.
        if (dark) assertTrue("dark press ${alpha.pressedAlpha}", alpha.pressedAlpha in 0.15f..0.2f)
        else assertTrue("light press ${alpha.pressedAlpha}", alpha.pressedAlpha >= 0.2f)
        assertTrue("focus under press", alpha.focusedAlpha <= alpha.pressedAlpha * 0.7f)
        // Unspecified: Material's ripple in the control's own content colour (ink on amber), not the ember press.
        assertEquals(Color.Unspecified, seen.filledButton!!.color)
        assertEquals(Color.Unspecified, seen.confirmPill!!.color)
    }

    @Test fun lightPressesAreEmberAndInkOnAmberFills() = check(dark = false)

    @Test fun darkPressesAreEmberAndInkOnAmberFills() = check(dark = true)
}
