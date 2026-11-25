package com.rebuild.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TabRow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.rebuild.ui.theme.RebuildTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** What large text does to History's tabs and the chart readout: nothing cut off, nothing moving. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LargeTextFitTest {
    @get:Rule val compose = createComposeRule()

    private val labels = listOf("Workouts", "Exercises", "Body")

    private fun scaled(fontScale: Float, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                RebuildTheme { content() }
            }
        }
    }

    private fun layoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().config
            .getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.single()
    }

    private var titleSmall: TextUnit = TextUnit.Unspecified

    /** History's tab row on a 360 dp phone (20 dp of margin each side), built as the History screen builds it. */
    private fun tabs(fontScale: Float) = scaled(fontScale) {
        titleSmall = MaterialTheme.typography.titleSmall.fontSize
        BoxWithConstraints(Modifier.width(320.dp)) {
            val style = fittedTabStyle(labels, constraints.maxWidth, padding = 8.dp)
            TabRow(selectedTabIndex = 0, indicator = {}, divider = {}) {
                labels.forEachIndexed { i, label ->
                    PillTab(selected = i == 0, onClick = {}, label = label, style = style, labelPadding = 8.dp)
                }
            }
        }
    }

    @Test fun atDoubleTextEveryTabLabelFitsOnOneLine() {
        tabs(2f)
        labels.forEach { label ->
            val layout = layoutOf(label)
            assertEquals("$label on one line", 1, layout.lineCount)
            // The label's natural one-line width against its box: a laid-out line is clamped to the box, so it can't
            // show a clip, and centred text reports a width "overflow" even when it fits.
            val ink = layout.multiParagraph.intrinsics.maxIntrinsicWidth
            assertTrue("$label fits across ($ink > ${layout.size.width})", ink <= layout.size.width + 1f) // a pixel of rounding
            assertTrue("$label fits down", layout.getLineBottom(0) <= layout.size.height + 0.5f)
            assertFalse("$label clipped in height", layout.didOverflowHeight)
            assertTrue("$label never below half size", layout.layoutInput.style.fontSize.value >= titleSmall.value * 0.5f - 0.01f)
        }
        // "Exercises" is too wide for its third at full size, so all three step down together and still match.
        val sizes = labels.map { layoutOf(it).layoutInput.style.fontSize.value }
        assertTrue("shrank ($sizes)", sizes.first() < titleSmall.value)
        assertEquals("all match", 1, sizes.toSet().size)
    }

    @Test fun atNormalTextTheTabsKeepTheirSize() {
        tabs(1f)
        labels.forEach { assertEquals(it, titleSmall.value, layoutOf(it).layoutInput.style.fontSize.value, 0.01f) }
    }

    /** The 1M–All row on the narrowest phone the app supports: 320 dp less the 16 dp page gutters. */
    @Test fun atDoubleTextOnANarrowPhoneEveryRangeLabelFits() {
        scaled(2f) { RangeChips(ChartRange.M3, {}, Modifier.width(288.dp)) }
        ChartRange.entries.forEach { range ->
            val layout = layoutOf(range.label)
            // The label's natural one-line width, not the line as laid out (which a narrow box clamps).
            val ink = layout.multiParagraph.intrinsics.maxIntrinsicWidth
            assertEquals("${range.label} on one line", 1, layout.lineCount)
            assertTrue("${range.label} fits across ($ink > ${layout.size.width})", ink <= layout.size.width + 1f)
        }
    }

    private val start = LocalDate.parse("2026-09-07")
    private val weekly = listOf(84.0, 83.6, 83.1, 82.7).mapIndexed { i, v -> ChartPoint(start.plusWeeks(i.toLong()), v) }

    private fun readoutHeights(fontScale: Float): Pair<Int, Int> {
        scaled(fontScale) {
            TrendChart(emptyList(), listOf(weekly), start, start.plusWeeks(3), { oneDecimal(it) }, "Weekly average",
                modifier = Modifier.width(360.dp),
                readout = { p -> "${oneDecimal(p.value)} kg" to "Mon ${p.date} – Sun ${p.date.plusDays(6)} · 3 days" })
        }
        val before = compose.onNodeWithTag("chart-readout", useUnmergedTree = true).fetchSemanticsNode().size.height
        compose.onNodeWithTag("trend-chart").performTouchInput { click(Offset(left + 2f, centerY)) }
        compose.waitForIdle()
        val after = compose.onNodeWithTag("chart-readout", useUnmergedTree = true).fetchSemanticsNode().size.height
        return before to after
    }

    @Test fun touchingTheChartNeverChangesTheReadoutsHeight() {
        val (before, after) = readoutHeights(1f)
        assertEquals("normal text", before, after)
    }

    @Test fun atDoubleTextTouchingStillNeverChangesTheReadoutsHeight() {
        val (before, after) = readoutHeights(2f)
        assertEquals("200% text", before, after)
    }
}
