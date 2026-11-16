package com.rebuild.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rebuild.domain.Action
import com.rebuild.domain.Phase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Holding an exercise pill on the workout: drag to reorder today, or let go for a menu to move or remove it. */
@RunWith(AndroidJUnit4::class)
class HoldToReorderDeviceTest {
    @get:Rule val rule = createComposeRule()

    private fun workout(): FakeRepository {
        val repo = FakeRepository(seedState(Phase.INTRO_ONE))
        runBlocking { repo.execute(Action.StartWorkout("upper-a", FIXED_NOW)) }
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithText("Resume workout").performClick()
        return repo
    }

    private fun names(repo: FakeRepository) = repo.state.value.activeWorkout!!.exercises.map { it.exercise.id }

    @Test fun holdingAndLettingGoOpensAMenuThatMovesTheExercise() {
        val repo = workout()
        assertEquals(listOf("bench", "db-press"), names(repo))
        rule.onNodeWithTag("exercise-chip-0").performTouchInput { longClick() }
        rule.onNodeWithTag("exercise-hold-menu").assertIsDisplayed()
        // The first exercise can't move earlier.
        rule.onNodeWithTag("hold-move-earlier").assertIsNotEnabled()
        rule.onNodeWithTag("hold-move-later").performClick()
        rule.waitUntil(5_000) { names(repo) == listOf("db-press", "bench") }
        rule.onNodeWithTag("exercise-hold-menu").assertDoesNotExist()
    }

    @Test fun holdingAndDraggingPastTheNextPillReordersTodayOnDrop() {
        val repo = workout()
        rule.onNodeWithTag("exercise-chip-0").performTouchInput {
            down(center)
            advanceEventTime(700)
            repeat(20) { moveBy(androidx.compose.ui.geometry.Offset(width / 6f, 0f)) }
            up()
        }
        rule.waitUntil(5_000) { names(repo) == listOf("db-press", "bench") }
        // One move, saved once on drop: never a stream of reorders while the finger moves.
        assertEquals(1, repo.actions.count { it is Action.MoveWorkoutExercise })
        rule.onNodeWithTag("exercise-hold-menu").assertDoesNotExist()
    }

    @Test fun removingAPlannedExerciseSkipsItForTodayAndItCanComeBack() {
        val repo = workout()
        rule.onNodeWithTag("exercise-chip-1").performTouchInput { longClick() }
        rule.onNodeWithText("Skipped today; it stays in your plan").assertIsDisplayed()
        // Holding the second pill opens its menu without switching to it: the first is still the one open.
        rule.onNodeWithTag("exercise-chip-0").assertIsSelected()
        rule.onNodeWithTag("hold-remove").performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkout!!.exercises[1].skipped }
        rule.onNodeWithTag("exercise-chip-1").performTouchInput { longClick() }
        rule.onNodeWithText("Bring back to today").assertIsDisplayed()
        rule.onNodeWithTag("hold-remove").performClick()
        rule.waitUntil(5_000) { !repo.state.value.activeWorkout!!.exercises[1].skipped }
    }

    @Test fun screenReadersGetTheSameActionsOnEachPill() {
        val repo = workout()
        val actions = rule.onNodeWithTag("exercise-chip-0").fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
        // The first pill can't move earlier, so it isn't offered.
        assertEquals(listOf("Move later", "Remove from today"), actions.map { it.label })
        rule.runOnIdle { actions.first { it.label == "Move later" }.action() }
        rule.waitUntil(5_000) { names(repo) == listOf("db-press", "bench") }
        assertTrue(repo.actions.any { it is Action.MoveWorkoutExercise })
    }
}
