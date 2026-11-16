package com.rebuild.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import com.rebuild.ui.theme.Dark
import com.rebuild.ui.theme.InkPress
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

    private fun ripples(dark: Boolean): Pair<RippleConfiguration?, RippleConfiguration?> {
        var themed: RippleConfiguration? = null
        var onAmber: RippleConfiguration? = null
        compose.setContent {
            RebuildTheme(darkTheme = dark) {
                themed = LocalRippleConfiguration.current
                InkPress { onAmber = LocalRippleConfiguration.current; Box { Text("x") } }
                // The app's filled button provides the ink press itself.
                Button(onClick = {}) { Text("Log set") }
            }
        }
        compose.waitForIdle()
        return themed to onAmber
    }

    private fun check(dark: Boolean) {
        val scheme = if (dark) Dark else Light
        val (themed, onAmber) = ripples(dark)
        assertEquals(scheme.primaryContainer, themed!!.color)
        assertTrue("a press must be visible", themed.rippleAlpha!!.pressedAlpha >= 0.2f)
        // Unspecified: Material's ripple in the control's own content colour (ink on amber).
        assertEquals(Color.Unspecified, onAmber!!.color)
    }

    @Test fun lightPressesAreEmberAndInkOnAmberFills() = check(dark = false)

    @Test fun darkPressesAreEmberAndInkOnAmberFills() = check(dark = true)
}
