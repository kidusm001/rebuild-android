package com.rebuild.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rebuild.data.RebuildRepository
import com.rebuild.data.SnapshotCodec
import com.rebuild.domain.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Weigh-ins, the weekly measuring form and pound loads, on the real reducer and saved snapshot (Tuesday 22 Sep). */
@RunWith(AndroidJUnit4::class)
class BodyAndPoundsDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val store = InMemorySnapshots(Seed.initialState().copy(phase = Phase.INTRO_TWO))

    private fun launch(repo: RebuildRepository) {
        val ui = MainThreadCollection(repo)
        rule.setContent { RebuildApp(ui, FakePlatform(), clock = { FIXED_NOW }) }
    }

    private fun assertSaved(repo: AppRepository) =
        assertEquals("The saved snapshot must be exactly what the app shows", SnapshotCodec.decode(store.json), repo.state.value)

    private fun tap(tag: String) {
        // Tabs and top-bar actions don't scroll; everything else is scrolled into view first.
        rule.onNodeWithTag(tag).also { runCatching { it.performScrollTo() } }.performClick()
        rule.waitForIdle()
    }

    @Test fun weighInInPoundsIsKeptAsEnteredAndShownAsTheWeeksAverage() {
        val repo = introRepository(store)
        launch(repo)
        rule.onNodeWithTag("today-bodyweight").performScrollTo().assertTextContains("Not logged today", substring = true)
        tap("today-log-weight")
        // Nothing is prefilled: a weigh-in that never happened can't pass as real.
        assertEquals("", rule.onNodeWithTag("bodyweight-value").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
        rule.onNodeWithTag("bodyweight-value").performTextReplacement("181.5")
        rule.onNodeWithTag("mass-LB").performClick()
        rule.onNodeWithTag("bodyweight-save").performClick()
        rule.waitUntil(5_000) { repo.state.value.bodyweights.isNotEmpty() }
        rule.waitForIdle()

        val entry = repo.state.value.bodyweights.single()
        assertEquals(181.5, entry.value, 0.0)
        assertEquals(MassUnit.LB, entry.unit)
        assertEquals(localDateOf(FIXED_NOW).toString(), entry.date)
        assertSaved(repo)
        rule.onNodeWithTag("today-bodyweight").performScrollTo().assertTextContains("181.5\u00A0lb", substring = true)

        rule.onNodeWithText("History").performClick()
        tap("history-tab-2")
        rule.onNodeWithTag("weight-summary").assertTextContains("181.5\u00A0lb", substring = true)
        rule.onNodeWithTag("weight-summary").assertTextContains("Average of 1 day", substring = true)
    }

    @Test fun weeklyMeasurementsSaveOnceAndAreCorrectedInPlace() {
        val repo = introRepository(store)
        launch(repo)
        tap("today-measure")
        rule.onNodeWithTag("measure-WAIST").performTextReplacement("90.5")
        tap("measure-save")
        rule.waitUntil(5_000) { repo.state.value.measurements.isNotEmpty() }
        rule.waitForIdle()
        val first = repo.state.value.measurements.single()
        assertEquals(BodySite.WAIST, first.site)
        assertEquals(90.5, first.value, 0.0)
        assertEquals(LengthUnit.CM, first.unit)
        assertSaved(repo)
        // Measured this week: Today stops asking.
        rule.onNodeWithTag("today-measure").assertDoesNotExist()

        rule.onNodeWithText("History").performClick()
        tap("history-tab-2")
        tap("body-measurements")
        tap("measure-this-week")
        rule.onNodeWithTag("measure-WAIST").assertTextContains("90.5")
        rule.onNodeWithTag("measure-WAIST").performTextReplacement("90")
        tap("measure-save")
        rule.waitUntil(5_000) { repo.state.value.measurements.single().value == 90.0 }
        assertEquals("A correction replaces this week's measurement", first.id, repo.state.value.measurements.single().id)
        assertSaved(repo)
    }

    @Test fun aSetLoggedInPoundsKeepsItsUnit() {
        val repo = introRepository(store) {
            execute(Action.StartWorkout("intro-upper", FIXED_NOW))
            val we = requireNotNull(state.value.activeWorkout).exercises.first()
            execute(Action.EditToday(we.id, "row-cable", 2, 180))
        }
        launch(repo)
        rule.onNodeWithText("Resume workout").performScrollTo().performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasTestTag("choose-load-unit") and isEnabled()).fetchSemanticsNodes().size == 1
        }
        tap("choose-load-unit")
        tap("unit-LB")
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("unit-LB").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("entry-kg").performScrollTo().performTextReplacement("95")
        rule.onNodeWithTag("entry-reps").performTextReplacement("10")
        tap("log-set")
        rule.waitUntil(5_000) { repo.state.value.activeWorkout!!.exercises.first().sets.isNotEmpty() }

        val set = repo.state.value.activeWorkout!!.exercises.first().sets.single()
        assertEquals(LoadUnit.LB, set.loadUnit)
        assertEquals(95.0, set.lb!!, 0.0)
        assertNull("A pound set is never converted to kg", set.kg)
        assertSaved(repo)
    }

    @Test fun touchingTheTrendReadsThatWeeksAverage() {
        val week = 7 * 86_400_000L
        val repo = introRepository(store) {
            listOf(3 to 84.0, 3 to 84.4, 2 to 83.2, 1 to 82.6).forEachIndexed { i, (weeksAgo, kg) ->
                val at = FIXED_NOW - weeksAgo * week + i * 60_000L
                execute(Action.LogBodyweight("w$i", at, kg, MassUnit.KG, "", localDateOf(at).toString()))
            }
        }
        launch(repo)
        rule.onNodeWithText("History").performClick()
        tap("history-tab-2")
        tap("body-weight-trend")
        rule.onNodeWithTag("chart-readout").assertExists()
        rule.onNode(hasText("Touch to read")).assertExists()
        // A real finger at the chart's left edge: the oldest week, the mean of its two weigh-ins.
        rule.onNodeWithTag("trend-chart").performTouchInput { click(androidx.compose.ui.geometry.Offset(left + 4f, centerY)) }
        rule.waitForIdle()
        rule.onNode(hasText("84.2\u00A0kg average")).assertExists()
        rule.onNode(hasText("Touch to read")).assertDoesNotExist()
    }
}
