package com.rebuild.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import com.rebuild.domain.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Tab roots are composed once and kept: switching back shows the same screen with its state, a hidden tab is absent
 * for screen readers and tests, and Back only reaches what is shown.
 */
@RunWith(AndroidJUnit4::class)
class TabKeepAliveDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun finishedUpper(): Workout {
        val at = LocalDateTime.of(2026, 9, 15, 8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val template = seedState().templates.first()
        return Workout(
            id = "w-past", templateId = template.id, name = template.name, phase = Phase.INTRO_TWO,
            startedAt = at, startDate = "2026-09-15", finishedAt = at + 3_600_000,
            exercises = template.slots.map { slot ->
                val exercise = seedState().exercises.single { it.id == slot.exerciseId }
                WorkoutExercise(
                    id = "we-${slot.id}", slotId = slot.id, exercise = exercise, targetSets = 2,
                    baselineSets = slot.baselineSets, upperSets = slot.upperSets, minReps = slot.minReps,
                    maxReps = slot.maxReps, targetRir = 3, restSeconds = slot.restSeconds,
                    sets = (1..2).map { LoggedSet("s-${slot.id}-$it", 20.0, 10, null, false, at + it * 60_000L) }
                )
            }
        )
    }

    private fun back() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
    }

    @Test fun aTabKeepsItsStateAndOnlyTheShownTabIsInTheTree() {
        val repo = FakeRepository(seedState(Phase.INTRO_TWO, listOf(finishedUpper())))
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithText("History").performClick()
        rule.onNodeWithTag("history-tab-2").performClick()
        rule.onNodeWithText("Bodyweight").assertExists()

        rule.onNodeWithText("Plan").performClick()
        rule.waitForIdle()
        // History is still composed but neither placed nor in the semantics tree.
        rule.onNodeWithTag("history-tab-2").assertDoesNotExist()
        rule.onNodeWithText("Bodyweight").assertDoesNotExist()

        rule.onNodeWithText("History").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("history-tab-2").assertIsSelected()
        rule.onNodeWithText("Bodyweight").assertIsDisplayed()
    }

    @Test fun backFromAKeptTabGoesHomeAndNeverReachesAHiddenOne() {
        val repo = FakeRepository(seedState(Phase.INTRO_TWO, listOf(finishedUpper())))
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithText("History").performClick()
        rule.onNodeWithText("Plan").performClick()
        rule.waitForIdle()
        back()
        rule.onNodeWithTag("week-progress").performScrollTo().assertIsDisplayed()
        // Nothing hidden takes this one: it closes the app.
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitUntil(5_000) { rule.activity.isFinishing || rule.activity.isDestroyed }
    }

    @Test fun historyTabsSwipeLeftAndRightButADragOnAChartReadsTheChart() {
        val day = 86_400_000L
        val weighIns = listOf(21L, 20L, 14L, 13L, 7L, 6L).mapIndexed { i, daysAgo ->
            val at = FIXED_NOW - daysAgo * day
            BodyweightEntry("w$i", at, localDateOf(at).toString(), 84.0 - i * 0.3, MassUnit.KG)
        }
        val repo = FakeRepository(seedState(Phase.INTRO_TWO, listOf(finishedUpper())).copy(bodyweights = weighIns))
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithText("History").performClick()
        rule.onNodeWithTag("history-tab-0").assertIsSelected()

        rule.onNodeWithTag("history-pager").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithTag("history-tab-1").assertIsSelected()
        rule.onNodeWithTag("history-exercise-search").assertIsDisplayed()

        rule.onNodeWithTag("history-pager").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithTag("history-tab-2").assertIsSelected()

        // On a chart, the same horizontal drag picks a point and stays on Body.
        rule.onNodeWithTag("trend-chart").performScrollTo().performTouchInput { swipeRight() }
        rule.waitForIdle()
        rule.onNodeWithTag("history-tab-2").assertIsSelected()
        rule.onNode(hasText("Touch to read")).assertDoesNotExist()

        // Off the chart (near the top of the page), the same drag turns the page back.
        rule.onNodeWithTag("history-pager").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(left + 8f, top + 24f), androidx.compose.ui.geometry.Offset(right - 8f, top + 24f), 300)
        }
        rule.waitForIdle()
        rule.onNodeWithTag("history-tab-1").assertIsSelected()
        // Tapping a tab still works, and the tab you left keeps its place.
        rule.onNodeWithTag("history-tab-0").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("history-tab-0").assertIsSelected()
    }
}
