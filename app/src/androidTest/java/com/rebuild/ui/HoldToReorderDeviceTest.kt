package com.rebuild.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
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

    @Test fun aMenuMoveMadeWhileTheHoldIsStillSettlingShowsInTheStrip() {
        val repo = workout()
        rule.onNodeWithTag("exercise-chip-0").assertIsDisplayed()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("exercise-chip-0").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithTag("exercise-chip-0").performTouchInput { up() }
        // The menu is up while the pill is still easing back down (180 ms): move it right away.
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("hold-move-later").performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.autoAdvance = true
        rule.waitUntil(5_000) { names(repo) == listOf("db-press", "bench") }
        rule.waitForIdle()
        // The strip shows the saved order, not the one from before the move.
        rule.onNodeWithTag("exercise-chip-0").assert(hasContentDescription(DB_PRESS.name, substring = true))
        rule.onNodeWithTag("exercise-chip-1").assert(hasContentDescription(BENCH.name, substring = true))
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

    /** Five exercises today, so a pill can be dragged past more than one neighbour. */
    private fun longWorkout(): FakeRepository {
        val extra = listOf("pulldown" to "Lat pulldown", "lateral" to "Lateral raise", "curl" to "Cable curl")
            .map { (id, name) -> com.rebuild.domain.Exercise(id, name) }
        val base = seedState(Phase.INTRO_ONE)
        val repo = FakeRepository(base.copy(exercises = base.exercises + extra))
        runBlocking {
            repo.execute(Action.StartWorkout("upper-a", FIXED_NOW))
            val id = repo.state.value.activeWorkout!!.id
            extra.forEach { repo.execute(Action.AddWorkoutExercise(id, "add-${it.id}", it.id)) }
        }
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithText("Resume workout").performClick()
        return repo
    }

    /** A pill's full bounds, even where it runs off the screen (bounds in root are clipped to it). */
    private fun box(index: Int): androidx.compose.ui.geometry.Rect {
        val node = rule.onNodeWithTag("exercise-chip-$index").fetchSemanticsNode()
        return androidx.compose.ui.geometry.Rect(node.positionInRoot,
            androidx.compose.ui.geometry.Size(node.size.width.toFloat(), node.size.height.toFloat()))
    }

    @Test fun aQuickDragLandsWhereThePillIsDropped() {
        val repo = longWorkout()
        val first = names(repo).first()
        // Where the first pill's centre must get to before it passes the third pill: with the first pill taken out
        // the second starts where the first did, and the third sits one dragged pill further right than that.
        val (a, b, c) = listOf(box(0), box(1), box(2))
        val gap = b.left - a.right
        val thirdLeft = a.left + b.width + gap
        val passesThird = thirdLeft + a.width + gap + c.width / 2f
        val distance = passesThird + 30f - a.center.x
        rule.onNodeWithTag("exercise-chip-0").performTouchInput { down(center) }
        // The hold is recognised on the clock, so let it pass before the moves arrive.
        rule.mainClock.advanceTimeBy(1_000)
        // Ten quick moves with no frame between them, the way a flick arrives, then the drop.
        rule.onNodeWithTag("exercise-chip-0").performTouchInput {
            repeat(10) { moveBy(androidx.compose.ui.geometry.Offset(distance / 10f, 0f)) }
            up()
        }
        rule.waitUntil(5_000) { names(repo).indexOf(first) != 0 }
        rule.waitForIdle()
        assertEquals("dropped just past the third pill", 2, names(repo).indexOf(first))
        assertEquals(1, repo.actions.count { it is Action.MoveWorkoutExercise })
    }

    @Test fun scrollingAPillOutOfTheStripAndBackNeverOpensItsMenu() {
        val repo = longWorkout()
        val picker = rule.onNodeWithTag("exercise-picker")
        // A swipe that starts on the first pill wakes its hold detector without ever becoming a hold.
        rule.onNodeWithTag("exercise-chip-0").performTouchInput {
            down(center)
            repeat(5) { moveBy(androidx.compose.ui.geometry.Offset(-width / 10f, 0f)) }
            up()
        }
        rule.waitForIdle()
        rule.onNodeWithTag("exercise-hold-menu").assertDoesNotExist()
        // Scrolled far enough to leave the strip, the pill's detector stops; that is not a hold let go, so coming
        // back into view it shows no menu.
        picker.performScrollToIndex(repo.state.value.activeWorkout!!.exercises.size)
        rule.waitForIdle()
        picker.performScrollToIndex(0)
        rule.waitForIdle()
        rule.onNodeWithTag("exercise-hold-menu").assertDoesNotExist()
        assertTrue(repo.actions.none { it is Action.MoveWorkoutExercise || it is Action.SetSkipped })
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
        assertEquals(listOf("Move later", "Switch exercise", "Remove from today"), actions.map { it.label })
        rule.runOnIdle { actions.first { it.label == "Move later" }.action() }
        rule.waitUntil(5_000) { names(repo) == listOf("db-press", "bench") }
        assertTrue(repo.actions.any { it is Action.MoveWorkoutExercise })
    }

    @Test fun switchingFromTheHoldMenuReplacesTheExerciseForToday() {
        val base = com.rebuild.domain.Seed.initialState().copy(phase = Phase.BASELINE)
        val repo = FakeRepository(base)
        runBlocking { repo.execute(Action.StartWorkout("push", FIXED_NOW)) }
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithText("Resume workout").performClick()
        rule.onNodeWithTag("exercise-picker").performScrollToIndex(2)
        rule.onNodeWithTag("exercise-chip-2").performTouchInput { longClick() }
        rule.onNodeWithTag("exercise-hold-menu").assertIsDisplayed()
        rule.onNodeWithTag("hold-switch").performClick()
        rule.onNodeWithTag("switch-exercise-sheet").assertIsDisplayed()
        rule.onNodeWithTag("switch-option-lateral-db").performScrollTo().performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkout!!.exercises[2].exercise.id == "lateral-db" }
        assertTrue(repo.state.value.overrides.isEmpty())
    }

    @Test fun aMoveThatFailsToSavePutsTheStripBackAndSaysSo() {
        val repo = workout()
        repo.failNext = Action.MoveWorkoutExercise::class.java to "Couldn't save the new order."
        rule.onNodeWithTag("exercise-chip-0").performTouchInput {
            down(center)
            advanceEventTime(700)
            repeat(20) { moveBy(androidx.compose.ui.geometry.Offset(width / 6f, 0f)) }
            up()
        }
        rule.waitUntil(5_000) { repo.actions.any { it is Action.MoveWorkoutExercise } }
        rule.waitForIdle()
        // Nothing was saved, and the strip shows the saved order again: Bench press is still first.
        assertEquals(listOf("bench", "db-press"), names(repo))
        // Said once, in the snackbar, not twice.
        rule.onNodeWithText("Couldn't save the new order.", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("exercise-chip-0").assert(hasContentDescription("Bench press", substring = true))
    }
}
