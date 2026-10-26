package com.rebuild.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rebuild.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.ZoneId

/** Monday–Sunday checklist on Today and Plan, derived from dated workouts and explicit credit (Tuesday 22 Sep). */
@RunWith(AndroidJUnit4::class)
class WeekChecklistDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun millis(at: LocalDateTime) = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** A finished seed workout with [sets] working sets on each of its exercises. */
    private fun finished(templateId: String, at: LocalDateTime, sets: Int): Workout {
        val template = seedState().templates.single { it.id == templateId }
        val startedAt = millis(at)
        return Workout(
            id = "w-$templateId-$startedAt", templateId = templateId, name = template.name, phase = Phase.INTRO_TWO,
            startedAt = startedAt, startDate = at.toLocalDate().toString(), finishedAt = startedAt + 3_600_000,
            exercises = template.slots.map { slot ->
                val exercise = seedState().exercises.single { it.id == slot.exerciseId }
                WorkoutExercise(
                    id = "we-${slot.id}-$startedAt", slotId = slot.id, exercise = exercise, targetSets = 2,
                    baselineSets = slot.baselineSets, upperSets = slot.upperSets, minReps = slot.minReps,
                    maxReps = slot.maxReps, targetRir = 3, restSeconds = slot.restSeconds,
                    sets = (1..sets).map { LoggedSet("s-${slot.id}-$startedAt-$it", 20.0, 10, null, false, startedAt + it * 60_000L) }
                )
            }
        )
    }

    private val tuesdayMorning = LocalDateTime.of(2026, 9, 22, 8, 0)

    private fun launch(repo: AppRepository) {
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
    }

    private fun rowText(templateId: String, text: String) =
        rule.onNode(hasText(text) and hasAnyAncestor(hasTestTag("week-row-$templateId")))

    @Test fun lowerADoneOnUpperDayTicksLowerAAndKeepsUpperAScheduled() {
        val repo = FakeRepository(seedState(Phase.INTRO_TWO, listOf(finished("lower-a", tuesdayMorning, sets = 2))))
        launch(repo)
        rule.onNodeWithText("Mon 21 Sep – Sun 27 Sep").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("week-progress").assertTextContains("1 of 2")
        rowText("lower-a", "Done · Tue 22 Sep").assertExists()
        rowText("upper-a", "Not done").assertExists()
        rule.onNodeWithTag("start-scheduled").assertTextContains("Start Upper A")
        rule.onNodeWithContentDescription("Repeat Lower A (Thursday)").performScrollTo().assertIsDisplayed()
        // Today's workout has its Start on the card, not twice.
        rule.onNodeWithContentDescription("Start Upper A (Tuesday)").assertDoesNotExist()
        assertTrue(repo.actions.isEmpty())
    }

    @Test fun doneTodayOffersTheNextUnfinishedAndAFullWeekOffersRestOrRepeatOnce() {
        val upper = finished("upper-a", tuesdayMorning, sets = 2)
        val repo = FakeRepository(seedState(Phase.INTRO_TWO, listOf(upper)))
        launch(repo)
        rule.onNodeWithText("Upper A is done this week · next unfinished").assertIsDisplayed()
        rule.onNodeWithTag("start-suggested").assertTextContains("Start Lower A")
        rule.onNodeWithTag("start-scheduled").assertDoesNotExist()

        // Monday 00:30 is this week; the week is complete, and nothing starts by itself.
        val lower = finished("lower-a", LocalDateTime.of(2026, 9, 21, 0, 30), sets = 2)
        repo.replace(repo.state.value.copy(workouts = listOf(upper, lower)))
        rule.waitForIdle()
        rule.onNodeWithTag("week-complete").assertIsDisplayed()
        rule.onNodeWithTag("week-progress").assertTextContains("2 of 2")
        rule.onNodeWithTag("start-suggested").assertDoesNotExist()
        rowText("lower-a", "Done · Mon 21 Sep").assertExists()

        rule.onNodeWithContentDescription("Repeat Upper A (Tuesday)").performScrollTo().performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkoutId != null }
        assertEquals(Action.StartWorkout("upper-a", FIXED_NOW), repo.actions.single())
        // An empty repeat can be finished, but it has no working sets, so it can't be counted.
        rule.onNodeWithText("Finish", substring = false).performClick()
        rule.onNodeWithText("Finish partial workout").assertIsDisplayed()
        rule.onNodeWithTag("count-for-week").assertDoesNotExist()
        rule.onNodeWithTag("confirm-button").performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkoutId == null }
        rule.waitForIdle()
        // A repeat (even an empty one) never counts twice or replaces the first.
        rule.onNodeWithTag("week-progress").assertTextContains("2 of 2")
        rowText("upper-a", "Done · Tue 22 Sep").assertExists()
    }

    @Test fun partialNeedsExplicitCreditAndLastWeekDoesNotCount() {
        val partial = finished("upper-a", tuesdayMorning, sets = 1)
        val lastSunday = finished("lower-a", LocalDateTime.of(2026, 9, 20, 23, 30), sets = 2)
        val repo = FakeRepository(seedState(Phase.INTRO_TWO, listOf(partial, lastSunday)))
        launch(repo)
        rule.onNodeWithTag("week-progress").assertTextContains("0 of 2")
        rowText("upper-a", "Partial · not counted").assertExists()
        rowText("lower-a", "Not done").assertExists()
        rule.onNodeWithTag("start-scheduled").assertTextContains("Start Upper A")
        rule.onNodeWithText("Partial Upper A this week · not counted").assertExists()

        rule.onNodeWithText("History").performClick()
        rule.onNodeWithText("Upper A").performClick()
        rule.onNodeWithTag("week-credit-toggle").performScrollTo().assertIsOff().performClick()
        rule.waitUntil(5_000) { repo.state.value.workouts.single { it.id == partial.id }.countedForWeek }
        assertEquals(Action.SetWeekCredit(partial.id, true), repo.actions.single())
        assertEquals("Credit never adds sets", partial.exercises, repo.state.value.workouts.single { it.id == partial.id }.exercises)
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Today").performClick()
        rule.onNodeWithTag("week-progress").assertTextContains("1 of 2")
        rowText("upper-a", "Done · Tue 22 Sep").assertExists()
        rule.onNodeWithTag("start-suggested").assertTextContains("Start Lower A")
    }

    @Test fun restDayStaysRestWithOptionalStartsAndMondayDerivesAFreshWeek() {
        val upper = finished("upper-a", tuesdayMorning, sets = 2)
        val repo = FakeRepository(seedState(Phase.INTRO_TWO, listOf(upper)))
        var now = millis(LocalDateTime.of(2026, 9, 25, 10, 0))
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { now }) }
        // Friday has nothing scheduled: rest is the honest answer, unfinished workouts stay a choice below.
        rule.onNodeWithText("Rest day").assertIsDisplayed()
        rule.onNodeWithText("Nothing scheduled for Friday.")
            .assertIsDisplayed()
        rule.onNodeWithTag("start-scheduled").assertDoesNotExist()
        rule.onNodeWithTag("start-suggested").assertDoesNotExist()
        rule.onNodeWithTag("week-progress").assertTextContains("1 of 2")
        rule.onNodeWithContentDescription("Repeat Upper A (Tuesday)").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Start Lower A (Thursday)").performScrollTo().assertIsDisplayed()
        assertTrue(repo.actions.isEmpty())

        // Monday 00:00 starts a new week from the dates alone: nothing is deleted, reset or carried over.
        now = millis(LocalDateTime.of(2026, 9, 28, 0, 0))
        rule.onNodeWithText("History").performClick()
        rule.onNodeWithText("Upper A").assertExists()
        rule.onNodeWithText("Today").performClick()
        rule.onNodeWithText("Mon 28 Sep – Sun 4 Oct").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("week-progress").assertTextContains("0 of 2")
        rowText("upper-a", "Not done").assertExists()
        rule.onNodeWithContentDescription("Start Upper A (Tuesday)").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Rest day").assertIsDisplayed()
        assertEquals(listOf(upper), repo.state.value.workouts)
        assertTrue(repo.actions.isEmpty())

        rule.onNodeWithContentDescription("Start Lower A (Thursday)").performScrollTo().performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkoutId != null }
        assertEquals(Action.StartWorkout("lower-a", now), repo.actions.single())
    }

    @Test fun inProgressChecklistIsReadOnly() {
        val repo = FakeRepository(seedState(Phase.INTRO_TWO))
        runBlocking { repo.execute(Action.StartWorkout("lower-a", FIXED_NOW - 60_000)) }
        launch(repo)
        rule.onNodeWithTag("active-banner").assertIsDisplayed()
        rowText("lower-a", "In progress").assertExists()
        rule.onNodeWithText("Start").assertDoesNotExist()
        rule.onNodeWithText("Repeat").assertDoesNotExist()
    }

    @Test fun routinesAreTemplatesAndAPhaseChangeSwitchesTheChecklistWithoutRewritingHistory() {
        val repo = libraryRepository()
        runBlocking {
            repo.execute(Action.StartWorkout("intro-upper", FIXED_NOW - 3_600_000))
            val workout = repo.state.value.activeWorkout!!
            repo.execute(Action.SaveSet(workout.id, workout.exercises.first().id, "one", 40.0, 10, null, false,
                FIXED_NOW - 3_000_000))
            repo.execute(Action.FinishWorkout(FIXED_NOW - 2_400_000, countForWeek = true))
        }
        launch(MainThreadCollection(repo))
        rule.onNodeWithTag("week-progress").assertTextContains("1 of 4")
        rowText("intro-upper", "Done · Tue 22 Sep").assertExists()
        rowText("upper", "Not done").assertExists()
        rule.onNodeWithText("Plan", substring = false).performClick()
        rule.onNodeWithText("7 exercises · 14 working sets · done this week", substring = true).assertExists()

        runBlocking { repo.execute(Action.SelectPhase(Phase.BASELINE)) }
        rule.onNodeWithText("Today").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("week-progress").assertTextContains("0 of 5")
        rowText("push", "Not done").assertExists()
        rule.onNodeWithText("Also this week, not on this phase's checklist: Upper (Tue 22 Sep).").performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithTag("start-scheduled").assertTextContains("Start Push")
        val kept = repo.state.value.workouts.single()
        assertEquals(Phase.INTRO_TWO, kept.phase)
        assertEquals("intro-upper", kept.templateId)
        assertTrue(kept.countedForWeek)
    }
}
