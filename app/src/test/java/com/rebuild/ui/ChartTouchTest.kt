package com.rebuild.ui

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.rebuild.ui.theme.RebuildTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChartTouchTest {
    @get:Rule val compose = createComposeRule()

    private val start = LocalDate.parse("2026-09-07")
    private val weekly = listOf(84.0, 83.6, 83.1, 82.7).mapIndexed { i, v -> ChartPoint(start.plusWeeks(i.toLong()), v) }

    private fun readout() = compose.onNodeWithTag("chart-readout", useUnmergedTree = true)
        .fetchSemanticsNode().let { node ->
            node.children.joinToString(" | ") { it.config.getOrNull(SemanticsProperties.Text)?.joinToString().orEmpty() }
        }

    private fun trend() {
        compose.setContent {
            RebuildTheme {
                TrendChart(emptyList(), listOf(weekly), start, start.plusWeeks(3), { oneDecimal(it) }, "Weekly average",
                    modifier = Modifier.width(360.dp),
                    readout = { p -> "${oneDecimal(p.value)} kg" to p.date.toString() })
            }
        }
    }

    @Test fun beforeATouchTheReadoutIsTheLatestPointAndSaysTheChartCanBeTouched() {
        trend()
        val text = readout()
        assertEquals("82.7 kg | 2026-09-28 | Touch to read", text)
    }

    @Test fun aTapReadsTheNearestPointAndADragFollowsTheFinger() {
        trend()
        val chart = compose.onNodeWithTag("trend-chart")
        chart.performTouchInput { click(Offset(left + 2f, centerY)) }
        assertEquals("84 kg | 2026-09-07", readout())
        chart.performTouchInput { swipe(Offset(left + 2f, centerY), Offset(right - 2f, centerY), durationMillis = 300) }
        assertEquals("82.7 kg | 2026-09-28", readout())
        chart.performTouchInput { click(Offset(width * 0.36f, centerY)) }
        assertEquals("83.6 kg | 2026-09-14", readout())
    }

    @Test fun screenReadersStepThroughThePointsLikeASlider() {
        trend()
        val chart = compose.onNodeWithTag("trend-chart")
        val range = chart.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(3f, range.current)
        assertEquals(0f..3f, range.range)
        assertEquals("82.7 kg, 2026-09-28", chart.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        chart.performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        assertEquals("83.6 kg, 2026-09-14", chart.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        assertEquals("Weekly average", chart.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single())
    }

    @Test fun aSessionReadsInOrderWithEqualSetsGrouped() {
        assertEquals("27.5 kg × 2 · 25 kg", sessionText(listOf(27.5, 27.5, 25.0)) { "${formatKg(it)} kg" })
        assertEquals("10 · 8 · 10", sessionText(listOf(10.0, 8.0, 10.0)) { formatKg(it) })
    }

    @Test fun aSessionChartReadsEverySetOfTheTouchedSession() {
        val day = 86_400_000L
        compose.setContent {
            RebuildTheme {
                SetChart("Weight · kg", listOf(listOf(40.0, 37.5), listOf(42.5), listOf(45.0, 42.5, 40.0)),
                    listOf(0L, 7 * day, 14 * day), readFormat = { "${formatKg(it)} kg" },
                    modifier = Modifier.width(360.dp).testTag("set-chart"))
            }
        }
        assertTrue(readout(), readout().startsWith("45 kg · 42.5 kg · 40 kg | "))
        compose.onNode(hasProgressBarRangeInfo()).performTouchInput { click(Offset(left + 2f, centerY)) }
        assertTrue(readout(), readout().startsWith("40 kg · 37.5 kg | "))
        assertTrue(readout(), readout().contains("2 sets"))
    }

    private fun hasProgressBarRangeInfo() = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
}
