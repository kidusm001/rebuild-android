package com.rebuild.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebuild.ui.theme.RebuildTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FitTextTest {
    @get:Rule val compose = createComposeRule()

    private val style = TextStyle(fontSize = 24.sp, lineHeight = 28.sp)

    private val width = mutableIntStateOf(0)

    private fun layoutIn(width: Int, text: String): TextLayoutResult {
        if (this.width.intValue == 0) {
            compose.setContent { RebuildTheme { Box(Modifier.width(this.width.intValue.dp)) { FitText(text, style) } } }
        }
        this.width.intValue = width
        compose.waitForIdle()
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)
            ?.action?.invoke(results)
        return results.single()
    }

    @Test fun textThatFitsKeepsItsSize() {
        val layout = layoutIn(400, "6 plates × 15")
        assertEquals(24f, layout.layoutInput.style.fontSize.value, 0.01f)
        assertEquals(1, layout.lineCount)
    }

    @Test fun textALittleTooWideStepsDownToStayOnOneLine() {
        // Measure the full-size width, then give it 90% of that: one step or two down, still one line.
        val full = layoutIn(1000, "6 plates × 15").size.width
        val density = compose.density.density
        val layout = layoutIn((full * 0.9f / density).toInt(), "6 plates × 15")
        val size = layout.layoutInput.style.fontSize.value
        assertTrue("shrank below 24 sp ($size)", size < 24f)
        assertTrue("never below 75% ($size)", size >= 18f - 0.01f)
        assertEquals(1, layout.lineCount)
    }

    @Test fun textTooLongEvenAtTheSmallestSizeWrapsInsteadOfClipping() {
        val layout = layoutIn(80, "12 plates × 15")
        assertEquals(18f, layout.layoutInput.style.fontSize.value, 0.01f)
        assertTrue("wraps (${layout.lineCount} lines)", layout.lineCount > 1)
        assertTrue("nothing hidden", !layout.hasVisualOverflow)
    }
}
