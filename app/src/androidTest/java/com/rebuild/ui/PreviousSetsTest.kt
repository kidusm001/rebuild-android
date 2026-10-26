package com.rebuild.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rebuild.domain.Action
import com.rebuild.domain.Exercise
import com.rebuild.domain.LoadingType
import com.rebuild.domain.LoggedSet
import com.rebuild.domain.Phase
import com.rebuild.domain.Workout
import com.rebuild.domain.WorkoutExercise
import com.rebuild.ui.theme.RebuildTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

internal fun comparisonHistory(exercise: Exercise = BENCH, count: Int = 2): Workout = Workout(
    id = "previous", templateId = "upper-a", name = "Upper A", phase = Phase.INTRO_TWO,
    startedAt = FIXED_NOW - 604_800_000, finishedAt = FIXED_NOW - 600_000_000,
    exercises = listOf(WorkoutExercise(
        id = "previous-exercise", slotId = "s-bench", exercise = exercise, targetSets = 2, baselineSets = 3,
        upperSets = 4, minReps = 6, maxReps = 10, targetRir = 3, restSeconds = 180,
        sets = listOf(LoggedSet("warmup", 10.0, 15, null, true, 1)) +
            (1..count).map { LoggedSet("previous-$it", if (exercise.loading == LoadingType.BODYWEIGHT) null else 40.0 + it,
                11 - it, if (it == 1) null else 2.0, false, it.toLong() + 1) }
    ))
)

@RunWith(AndroidJUnit4::class)
class PreviousSetsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun emptyHistoryShowsAnHonestEmptyState() {
        val today = comparisonHistory().exercises.first().copy(sets = emptyList())
        rule.setContent { RebuildTheme { SetLedger(today, null, null) } }
        rule.onNodeWithTag("previous-set-1").assertDoesNotExist()
        rule.onNodeWithTag("previous-comparable").assertDoesNotExist()
        // Unlogged ordinals are explicit placeholders, never projected values.
        rule.onNodeWithContentDescription("Set 1: up next, not logged yet").assertExists()
        rule.onNodeWithContentDescription("Set 2: planned, not logged yet").assertExists()
        rule.onNodeWithTag("set-row").assertDoesNotExist()
    }

    @Test
    fun onePriorWorkingSetKeepsDateAndMissingRirDistinctFromZero() {
        val previous = comparisonHistory(count = 1)
        val repo = FakeRepository(seedState(Phase.INTRO_TWO, listOf(previous)))
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithTag("start-scheduled").performClick()
        rule.onNodeWithTag("previous-date").assertTextContains(formatDate(previous.startedAt))
        rule.onNodeWithTag("previous-set-1")
            .assertContentDescriptionContains("Previous set 1: 41 kg × 10. RIR not recorded.", substring = true)
        rule.onNodeWithTag("previous-set-2").assertDoesNotExist()
        rule.onNodeWithTag("entry-kg").assertTextContains("41")
        assertTrue(repo.actions.none { it is Action.SaveSet })
    }

    @Test
    fun manyPreviousSetsExpandAndCollapseWithoutLogging() {
        val previous = comparisonHistory(count = 7)
        val repo = FakeRepository(seedState(Phase.INTRO_TWO, listOf(previous)))
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithTag("start-scheduled").performClick()
        rule.onNodeWithTag("previous-set-5").assertDoesNotExist()
        rule.onNodeWithTag("previous-expand").performScrollTo().performClick()
        rule.onNodeWithTag("previous-set-7").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("previous-expand").performScrollTo().performClick()
        rule.onNodeWithTag("previous-set-5").assertDoesNotExist()
        assertTrue(repo.actions.none { it is Action.SaveSet })
    }

    @Test
    fun workingOrdinalPrefillIgnoresWarmupsAndStillNeedsAnExplicitTap() {
        val repo = FakeRepository(seedState(Phase.INTRO_TWO, listOf(comparisonHistory())))
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithTag("start-scheduled").performClick()
        rule.onNodeWithTag("entry-kg").assertTextContains("41")
        rule.onNodeWithTag("entry-warmup").performScrollTo().performClick()
        rule.onNodeWithTag("log-set").performScrollTo().performClick()
        rule.waitUntil { repo.state.value.activeWorkout?.exercises?.first()?.sets?.size == 1 }
        rule.onNodeWithTag("entry-kg").assertTextContains("41")
        rule.waitUntil { SemanticsProperties.Disabled !in rule.onNodeWithTag("log-set").fetchSemanticsNode().config }
        rule.onNodeWithTag("log-set").performScrollTo().performClick()
        rule.waitUntil { repo.state.value.activeWorkout?.exercises?.first()?.sets?.size == 2 }
        rule.onNodeWithTag("entry-kg").assertTextContains("42")
        rule.onNodeWithTag("entry-reps").assertTextContains("9")
        rule.onNodeWithTag("entry-rir").assertTextContains("")
        assertEquals(2, repo.actions.filterIsInstance<Action.SaveSet>().size)
    }

    @Test
    fun pulldownDoesNotPresentAssistedPullupResults() {
        val pulldown = BENCH.copy(id = "pulldown", name = "Lat pulldown")
        val old = comparisonHistory(pulldown.copy(loading = LoadingType.ASSISTANCE))
        val state = seedState(Phase.INTRO_TWO, listOf(old))
        val repo = FakeRepository(state.copy(
            exercises = listOf(pulldown, DB_PRESS),
            templates = state.templates.map { t -> t.copy(slots = t.slots.map {
                if (it.exerciseId == BENCH.id) it.copy(exerciseId = "pulldown") else it
            }) }
        ))
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithTag("start-scheduled").performClick()
        rule.onNodeWithTag("previous-comparable").assertDoesNotExist()
        rule.onNodeWithTag("entry-kg").assertTextContains("")
        assertTrue(repo.actions.none { it is Action.SaveSet })
    }

    @Test
    fun largeTypeRowsReflowWithoutClippedValuesAndKeepLoadingUnits() {
        val previous = comparisonHistory(DB_PRESS)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, 2f)) {
                RebuildTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        SetLedger(previous.exercises.first().copy(sets = emptyList()),
                            previous.exercises.first(), previous.startedAt)
                    }
                }
            }
        }
        rule.onNodeWithTag("previous-set-2").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("42 kg/DB × 9", substring = true, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        val node = rule.onNodeWithText("42 kg/DB × 9", substring = true, useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(node.size.height.toFloat(), node.boundsInRoot.height, 1f)
        assertTrue(node.boundsInRoot.left >= 0 && node.boundsInRoot.right <= 1080)
    }

    @Test
    fun assistanceAndBodyweightKeepDistinctReadOnlyUnits() {
        val exercise = mutableStateOf(BENCH.copy(loading = LoadingType.ASSISTANCE))
        rule.setContent {
            RebuildTheme {
                val previous = comparisonHistory(exercise.value, count = 1)
                SetLedger(previous.exercises.first().copy(sets = emptyList()),
                    previous.exercises.first(), previous.startedAt)
            }
        }
        rule.onNodeWithText("41 kg assist × 10", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("previous-set-1")
            .assertContentDescriptionContains("41 kg assist", substring = true)
        rule.runOnIdle { exercise.value = BENCH.copy(loading = LoadingType.BODYWEIGHT) }
        rule.onNodeWithText("41 kg assist × 10", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText("kg", substring = true, useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText("Bodyweight × 10", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("previous-set-1")
            .assertContentDescriptionContains("Bodyweight × 10", substring = true)
    }
}
