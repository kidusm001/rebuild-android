package com.rebuild.ui

import android.util.Log
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityViewCheckException
import com.rebuild.domain.Action
import com.rebuild.domain.Distance
import com.rebuild.domain.DistanceUnit
import com.rebuild.domain.EXTRA_WORKOUT_NAME
import com.rebuild.domain.Monotonic
import com.rebuild.domain.Phase
import com.rebuild.domain.RestTimer
import com.rebuild.domain.RoutineExercise
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs ATF against the native Compose accessibility tree, not a JVM substitute. */
@SdkSuppress(minSdkVersion = 34)
@RunWith(AndroidJUnit4::class)
class AccessibilityDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    /** The monotonic clock the measured test's stopwatch reads, moved by hand. */
    @Volatile private var elapsed = 1_000_000L

    @Before
    fun enableNativeChecks() {
        rule.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        rule.enableAccessibilityChecks(
            AccessibilityValidator().setRunChecksFromRootView(true)
                .setThrowExceptionFor(AccessibilityCheckResultType.ERROR)
        )
    }

    @Test
    fun unlabeledClickableNegativeControlFailsTheValidator() {
        rule.setContent {
            Box(Modifier.size(56.dp).background(Color.Gray).clickable {}.testTag("unlabeled"))
        }
        val failure = runCatching { rule.onRoot().tryPerformAccessibilityChecks() }.exceptionOrNull()
        assertTrue("The native validator must reject a genuinely unlabeled clickable, not silently pass",
            failure is AccessibilityViewCheckException)
        assertTrue("Failure must identify the missing accessibility label: $failure",
            failure.toString().contains("label", ignoreCase = true))
        Log.i("RebuildAccessibility", "Negative control rejected: $failure")
    }

    @Test
    fun phaseChoiceAndHomePassNativeChecks() {
        val repo = FakeRepository(seedState())
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithText("Choose a phase").assertIsNotEnabled()
        rule.onRoot().tryPerformAccessibilityChecks()
        captureEvidence("disabled-phase")
        rule.onNodeWithTag("phase-INTRO_ONE").performClick()
        rule.onNodeWithText("Start with Intro 1").performClick()
        rule.onRoot().tryPerformAccessibilityChecks()
        assertEquals(Phase.INTRO_ONE, repo.state.value.phase)
    }

    @Test
    fun setEntryErrorsSavingAndRestPassNativeChecks() {
        val repo = FakeRepository(seedState(Phase.INTRO_TWO).copy(
            restTimer = RestTimer("rest", "workout", "exercise", FIXED_NOW + 180_000)
        ))
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithTag("start-scheduled").performClick()
        rule.onNodeWithTag("entry-kg").performScrollTo().performTextInput("42.5")
        rule.onNodeWithTag("log-set").performScrollTo().performClick()
        rule.onNodeWithTag("entry-reps").assertIsFocused().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Error, "Enter the reps you completed (1 or more).")
        )
        rule.onRoot().tryPerformAccessibilityChecks()
        rule.onNodeWithTag("entry-reps").performTextInput("8")
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate
        try {
            rule.onNodeWithTag("log-set").performScrollTo().performClick()
            rule.onNodeWithTag("log-set").assertIsNotEnabled().assertTextContains("Saving", substring = true)
            rule.onRoot().tryPerformAccessibilityChecks()
            captureEvidence("disabled-saving")
        } finally {
            gate.complete(Unit)
        }
        rule.waitUntil { repo.state.value.workouts.single().exercises.first().sets.size == 1 }
        rule.onNodeWithContentDescription("Rest options").performClick()
        rule.onNodeWithText("Rest options").tryPerformAccessibilityChecks()
    }

    @Test
    fun largeTextEntryAndExpiredRestPassNativeChecks() {
        val repo = FakeRepository(seedState(Phase.INTRO_ONE).copy(
            restTimer = RestTimer("rest", "workout", "exercise", FIXED_NOW - 1)
        ))
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                RebuildApp(repo, FakePlatform(PlatformStatus(false, false, true)), clock = { FIXED_NOW })
            }
        }
        rule.onNodeWithTag("start-scheduled").performScrollTo().performClick()
        rule.onRoot().tryPerformAccessibilityChecks()
        rule.onNodeWithTag("entry-kg").performScrollTo().performTextInput("20")
        rule.onRoot().tryPerformAccessibilityChecks()
        rule.onNodeWithContentDescription("Rest options").performClick()
        rule.onNodeWithText("Rest options").tryPerformAccessibilityChecks()
    }

    @Test
    fun readFirstHistoryNoteAndCorrectionPassNativeChecks() {
        val repo = FakeRepository(seedState(Phase.INTRO_ONE))
        runBlocking {
            repo.execute(Action.StartWorkout("upper-a", FIXED_NOW))
            val workout = requireNotNull(repo.state.value.activeWorkout)
            repo.execute(Action.SaveSet(workout.id, workout.exercises.first().id, "set", 40.0, 8, null, false, FIXED_NOW))
            repo.execute(Action.FinishWorkout(FIXED_NOW + 60_000))
        }
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithText("History").performClick()
        rule.onNodeWithText("Upper A").performClick()
        rule.onNodeWithContentDescription("Bench press, ", substring = true).assertExists()
        rule.onNodeWithContentDescription("Options for Bench press").performClick()
        rule.onNodeWithContentDescription("Add missed set for Bench press").assertExists()
        // Choosing the note opens its field, which also closes the menu.
        rule.onNodeWithContentDescription("Add exercise note for Bench press").performClick()
        rule.onNodeWithTag("exercise-note").assertExists()
        rule.onRoot().tryPerformAccessibilityChecks()
        rule.onNodeWithText("Add workout note").performClick()
        rule.onNodeWithTag("workout-note").performTextInput("Comfortable set")
        rule.onRoot().tryPerformAccessibilityChecks()
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithTag("set-row").performScrollTo().performClick()
        rule.onNodeWithTag("edit-kg").tryPerformAccessibilityChecks()
        rule.onNodeWithTag("edit-kg").assertTextContains("40")
        captureEvidence("correction")
    }

    @Test
    fun exerciseLibrarySearchAndPlateEntryPassNativeChecks() {
        val repo = libraryRepository(activeCable = true)
        val ui = MainThreadCollection(repo)
        rule.setContent { RebuildApp(ui, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithText("More").performClick()
        rule.onNodeWithText("Exercises").performClick()
        rule.onNodeWithTag("library-search").performTextInput("pulldown")
        rule.onRoot().tryPerformAccessibilityChecks()
        captureEvidence("library-search")
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Today").performClick()
        rule.onNodeWithText("Resume workout").performClick()
        rule.onNodeWithTag("choose-load-unit").performScrollTo().performClick()
        rule.onNodeWithTag("unit-PLATES").performClick()
        rule.onNodeWithTag("entry-plates").performScrollTo().performTextInput("5")
        rule.onNodeWithTag("entry-reps").performTextInput("12")
        rule.onNodeWithTag("log-set").performScrollTo().performClick()
        rule.waitUntil(5000) { repo.state.value.activeWorkout!!.exercises.first().sets.size == 1 }
        rule.onRoot().tryPerformAccessibilityChecks()
        rule.onNodeWithTag("rest-status").assertTextContains("3:00", substring = true)
        assertRestAccessibilityTargets()
        captureEvidence("plates-entry")
        assertEquals(5, repo.state.value.activeWorkout!!.exercises.first().sets.single().plates)
    }

    @Test
    fun weekChecklistAndPartialCreditPassNativeChecks() {
        val repo = FakeRepository(seedState(Phase.INTRO_TWO))
        runBlocking {
            repo.execute(Action.StartWorkout("lower-a", FIXED_NOW - 7_200_000))
            val workout = requireNotNull(repo.state.value.activeWorkout)
            repo.execute(Action.SaveSet(workout.id, workout.exercises.first().id, "one", 20.0, 10, null, false,
                FIXED_NOW - 7_000_000))
            repo.execute(Action.FinishWorkout(FIXED_NOW - 6_000_000))
        }
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithTag("week-checklist").performScrollTo()
        rule.onNodeWithContentDescription("Start Lower A (Thursday)").assertExists()
        rule.onRoot().tryPerformAccessibilityChecks()
        captureEvidence("week-checklist")
        rule.onNodeWithText("History").performClick()
        rule.onNodeWithText("Lower A").performClick()
        rule.onNodeWithTag("week-credit-toggle").performScrollTo()
        rule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun openDropSetEntryRestPauseAndSetDetailsPassNativeChecks() {
        val repo = FakeRepository(seedState(Phase.INTRO_TWO))
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithTag("start-scheduled").performClick()
        rule.onNodeWithTag("set-type").performScrollTo().performClick()
        rule.onNodeWithTag("set-type-drop").tryPerformAccessibilityChecks()
        rule.onNodeWithTag("set-type-drop").performClick()
        rule.onNodeWithTag("entry-kg").performScrollTo().performTextInput("40")
        rule.onNodeWithTag("entry-reps").performTextInput("10")
        rule.onNodeWithTag("log-set").performScrollTo().performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkout!!.exercises.first().sets.size == 1 }
        rule.waitUntil(5_000) { SemanticsProperties.Disabled !in rule.onNodeWithTag("log-set").fetchSemanticsNode().config }
        rule.onNodeWithTag("log-set").assertTextContains("Add drop")
        rule.onNodeWithTag("finish-group").performScrollTo()
        rule.onRoot().tryPerformAccessibilityChecks()
        captureEvidence("open-drop-set")
        rule.onNodeWithTag("group-row").performScrollTo().performClick()
        rule.onNodeWithTag("group-stages").tryPerformAccessibilityChecks()
        rule.onNodeWithText("Rest-pause").performScrollTo().performClick()
        rule.waitUntil(5_000) {
            repo.state.value.activeWorkout!!.exercises.first().setGroups.single().method == com.rebuild.domain.SetMethod.REST_PAUSE
        }
        rule.onNodeWithTag("group-done").performClick()
        rule.onNodeWithTag("short-pause").performScrollTo()
        rule.onNodeWithTag("log-set").assertTextContains("Add mini-set")
        rule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun extraWorkoutPickerPerLegQuestionAndGoalDialogPassNativeChecks() {
        val repo = MainThreadCollection(introRepository())
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithTag("start-empty").performScrollTo().performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkoutId != null }
        rule.onNodeWithTag("add-first-exercise").assertExists()
        rule.onRoot().tryPerformAccessibilityChecks()
        captureEvidence("extra-empty")
        rule.onNodeWithTag("add-first-exercise").performClick()
        rule.onNodeWithTag("library-search").performTextInput("lunge")
        rule.onNodeWithTag("library-results").performScrollToNode(hasTestTag("library-lunge-db"))
        rule.onNodeWithTag("library-search").tryPerformAccessibilityChecks()
        captureEvidence("add-exercise-picker")
        rule.onNodeWithTag("library-lunge-db").performClick()
        rule.onNodeWithTag("per-leg-yes").tryPerformAccessibilityChecks()
        captureEvidence("per-leg-question")
        rule.onNodeWithTag("per-leg-yes").performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkout!!.exercises.size == 1 }
        rule.onNodeWithTag("target-line").assertTextContains("No target", substring = true)
        rule.onRoot().tryPerformAccessibilityChecks()
        rule.onNodeWithTag("exercise-details").performScrollTo().performClick()
        rule.onNodeWithTag("remove-entry").performScrollTo()
        rule.onRoot().tryPerformAccessibilityChecks()
        rule.onNodeWithTag("edit-goal").performScrollTo().performClick()
        rule.onNodeWithTag("goal-sets").performTextInput("3")
        rule.onNodeWithTag("goal-per-leg").assertExists()
        rule.onNodeWithTag("goal-sets").tryPerformAccessibilityChecks()
        captureEvidence("goal-dialog")
        rule.onNodeWithTag("goal-save").performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkout!!.exercises.single().targetSets == 3 }
        rule.onRoot().tryPerformAccessibilityChecks()
        assertTrue(repo.state.value.activeWorkout!!.exercises.single().perLeg)
    }

    @Test
    fun skippedPanelReorderAndSaveToPlanPassNativeChecks() {
        val repo = MainThreadCollection(introRepository {
            execute(Action.StartWorkout("intro-upper", FIXED_NOW))
            val workout = requireNotNull(state.value.activeWorkout)
            execute(Action.AddWorkoutExercise(workout.id, "added-face-pull", "face-pull"))
            execute(Action.SetSkipped(workout.id, workout.exercises.first().id, true))
        })
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithText("Resume workout").performScrollTo().performClick()
        rule.onNodeWithTag("exercise-picker").performScrollToNode(hasTestTag("exercise-chip-0"))
        rule.onNodeWithTag("exercise-chip-0").performClick()
        rule.onNodeWithTag("undo-skip").performScrollTo()
        rule.onRoot().tryPerformAccessibilityChecks()
        captureEvidence("skipped-panel")
        rule.onNodeWithContentDescription("More workout actions").performClick()
        rule.onNodeWithTag("menu-reorder").tryPerformAccessibilityChecks()
        rule.onNodeWithTag("menu-reorder").performClick()
        rule.onNodeWithTag("move-up-7").performScrollTo().performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkout!!.exercises[6].id == "added-face-pull" }
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasTestTag("move-up-6") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("reorder-done").tryPerformAccessibilityChecks()
        captureEvidence("reorder")
        rule.onNodeWithTag("reorder-done").performClick()
        rule.onNodeWithTag("exercise-picker").performScrollToNode(hasTestTag("exercise-chip-6"))
        rule.onNodeWithTag("exercise-chip-6").performClick()
        rule.onNodeWithTag("exercise-details").performScrollTo().performClick()
        rule.onNodeWithTag("save-addition").performScrollTo().performClick()
        rule.onNodeWithTag("goal-sets").performTextInput("2")
        rule.onNodeWithTag("goal-min").performTextInput("12")
        rule.onNodeWithTag("goal-max").performTextInput("15")
        rule.onNodeWithTag("plan-addition-save").tryPerformAccessibilityChecks()
        captureEvidence("save-to-plan")
        rule.onNodeWithTag("plan-addition-save").performClick()
        rule.waitUntil(5_000) { repo.state.value.planAdditions.isNotEmpty() }
        rule.onNodeWithTag("addition-in-plan").performScrollTo()
        rule.onRoot().tryPerformAccessibilityChecks()
        assertTrue("Today's entry stays optional", repo.state.value.activeWorkout!!.exercises[6].optional)
    }

    @Test
    fun planRemovalRestoreAndHistoryCorrectionPassNativeChecks() {
        val repo = MainThreadCollection(introRepository {
            execute(Action.StartWorkout("intro-upper", FIXED_NOW - 7_200_000))
            val workout = requireNotNull(state.value.activeWorkout)
            execute(Action.SaveSet(workout.id, workout.exercises.first().id, "chest-1", 40.0, 10, null, false,
                FIXED_NOW - 6_000_000))
            execute(Action.FinishWorkout(FIXED_NOW - 3_600_000))
            execute(Action.AddWorkoutExercise(workout.id, "forgotten-face-pull", "face-pull"))
            execute(Action.SaveSet(workout.id, "forgotten-face-pull", "pull-1", 15.0, 15, null, false,
                FIXED_NOW - 3_600_000))
            execute(Action.SavePlanAddition("addition-face-pull", Phase.INTRO_TWO, "intro-upper", "face-pull",
                2, 12, 20, 90, false))
            execute(Action.SetPlanSlotRemoved(Phase.INTRO_TWO, "intro-chest", true))
        })
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithText("Plan", substring = false).performClick()
        rule.onNodeWithTag("plan-addition-addition-face-pull").performScrollTo()
        rule.onNodeWithTag("plan-restore-intro-chest").performScrollTo()
        rule.onRoot().tryPerformAccessibilityChecks()
        captureEvidence("plan-removed-and-added")
        rule.onNodeWithTag("plan-addition-addition-face-pull").performScrollTo().performClick()
        rule.onNodeWithTag("plan-remove-addition").tryPerformAccessibilityChecks()
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithTag("plan-restore-intro-chest").performScrollTo().performClick()
        rule.waitUntil(5_000) { repo.state.value.planRemovals.isEmpty() }

        rule.onNodeWithText("History").performClick()
        rule.onNodeWithText("Upper", substring = false).performClick()
        val list = rule.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasTestTag("history-options-forgotten-face-pull"))
        rule.onRoot().tryPerformAccessibilityChecks()
        captureEvidence("history-added-exercise")
        rule.onNodeWithTag("history-options-forgotten-face-pull").performClick()
        rule.onNodeWithTag("history-remove-forgotten-face-pull").performClick()
        rule.onNodeWithTag("confirm-button").assertTextContains("Remove and delete 1 record")
            .tryPerformAccessibilityChecks()
        rule.onNodeWithTag("dialog-cancel").performClick()
        list.performScrollToNode(hasTestTag("history-add-exercise"))
        rule.onNodeWithTag("history-add-exercise").performClick()
        rule.onNodeWithTag("library-search").performTextInput("face pull")
        rule.onNodeWithTag("library-results").performScrollToNode(hasTestTag("library-face-pull"))
        rule.onNodeWithTag("library-face-pull").performClick()
        list.performScrollToNode(hasTestTag("workout-notice"))
        rule.onRoot().tryPerformAccessibilityChecks()
        assertEquals(1, repo.state.value.workouts.single().exercises.last().sets.size)
    }

    @Test
    fun routinesLibraryStartChooserScheduleConflictAndEditorPassNativeChecks() {
        val face = RoutineExercise("face-pull", 2, 12, 20, 90, false)
        val hammer = RoutineExercise("curl-hammer", 2, 10, 12, 60, false)
        val repo = MainThreadCollection(introRepository {
            execute(Action.SaveRoutine("r-arms", "Arms", listOf(face, hammer)))
            execute(Action.ScheduleRoutine("p-mon", Phase.INTRO_TWO, 1, "r-arms"))
            execute(Action.ScheduleRoutine("p-tue", Phase.INTRO_TWO, 2, "r-arms", alongside = true))
        })
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithTag("also-today").performScrollTo()
        rule.onRoot().tryPerformAccessibilityChecks()
        captureEvidence("today-two-routines")
        rule.onNodeWithTag("open-routines").performScrollTo().performClick()
        rule.onRoot().tryPerformAccessibilityChecks()
        captureEvidence("routines-library")
        rule.onNodeWithTag("routine-row-r-arms").performClick()
        rule.onRoot().tryPerformAccessibilityChecks()
        captureEvidence("routine-detail")
        rule.onNodeWithTag("routine-start").performClick()
        rule.onNodeWithTag("start-choice-confirm").assertIsNotEnabled().tryPerformAccessibilityChecks()
        captureEvidence("start-chooser")
        rule.onNodeWithTag("start-choice-p-tue").performClick()
        rule.onNodeWithTag("start-choice-confirm").assertIsEnabled().tryPerformAccessibilityChecks()
        rule.onNode(hasText("Cancel") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        rule.onNodeWithTag("routine-schedule").performScrollTo().performClick()
        rule.onNodeWithTag("schedule-day-4").performScrollTo().performClick()
        rule.onNodeWithTag("schedule-alongside").performScrollTo()
        rule.onNodeWithTag("schedule-confirm").assertIsNotEnabled().tryPerformAccessibilityChecks()
        captureEvidence("schedule-conflict")
        rule.onNode(hasText("Cancel") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        rule.onNodeWithTag("routine-edit").performScrollTo().performClick()
        rule.onRoot().tryPerformAccessibilityChecks()
        captureEvidence("routine-editor")
        rule.onNode(hasClickAction() and hasText("Cable face pull") and hasAnyAncestor(hasTestTag("draft-row-0")))
            .performClick()
        rule.onNodeWithTag("draft-targets-save").tryPerformAccessibilityChecks()
        captureEvidence("routine-targets")
        assertEquals("Looking around changes nothing", 2, repo.state.value.placements.size)
        assertEquals(null, repo.state.value.activeWorkoutId)
    }

    @Test
    fun planOccurrencesOptionsMoveConflictAndTakenOffListPassNativeChecks() {
        val face = RoutineExercise("face-pull", 2, 12, 20, 90, false)
        val hammer = RoutineExercise("curl-hammer", 2, 10, 12, 60, false)
        val repo = MainThreadCollection(introRepository {
            execute(Action.SaveRoutine("r-arms", "Arms", listOf(face, hammer)))
            execute(Action.ScheduleRoutine("p-mon", Phase.INTRO_TWO, 1, "r-arms"))
            execute(Action.SaveRoutine("r-arms", "Arms", listOf(face.copy(sets = 3), hammer)))
            execute(Action.RemoveFromWeeklyPlan(Phase.INTRO_TWO, "lower-b"))
        })
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithText("Plan", substring = false).performClick()
        rule.onNodeWithTag("plan-copy-note-p-mon").performScrollTo()
        rule.onRoot().tryPerformAccessibilityChecks()
        captureEvidence("plan-copy")
        rule.onNodeWithTag("plan-options-p-mon").performClick()
        rule.onNodeWithTag("plan-menu-update").tryPerformAccessibilityChecks()
        captureEvidence("plan-options")
        rule.onNodeWithTag("plan-menu-move").performClick()
        rule.onNodeWithTag("schedule-day-4").performScrollTo().performClick()
        rule.onNodeWithTag("schedule-replace-lower-a").performScrollTo()
        rule.onNodeWithTag("move-confirm").assertIsNotEnabled().tryPerformAccessibilityChecks()
        captureEvidence("move-conflict")
        rule.onNode(hasText("Cancel") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        rule.onNodeWithTag("plan-put-back-lower-b").performScrollTo()
        rule.onRoot().tryPerformAccessibilityChecks()
        captureEvidence("plan-taken-off")
        rule.onNodeWithTag("plan-put-back-lower-b").performClick()
        rule.waitUntil(5_000) { repo.state.value.scheduleRemovals.isEmpty() }
        rule.onNodeWithTag("plan-occurrence-lower-b").performScrollTo()
        rule.onRoot().tryPerformAccessibilityChecks()
        assertEquals(1, repo.state.value.placements.single().weekday)
    }

    @Test
    fun measuredEntryStopwatchReviewAndEffortCorrectionPassNativeChecks() {
        val earlier = FIXED_NOW - 2 * 86_400_000L
        val repo = MainThreadCollection(introRepository {
            execute(Action.StartExtraWorkout(earlier))
            val past = requireNotNull(state.value.activeWorkoutId)
            execute(Action.AddWorkoutExercise(past, "e-run", "treadmill"))
            execute(Action.SaveEffort(past, "e-run", "run-1", 1_200, Distance(5_000, DistanceUnit.KM), null,
                earlier + 1_200_000))
            execute(Action.SaveEffort(past, "e-run", "run-2", 600, Distance(1_000, DistanceUnit.MI), null,
                earlier + 2_000_000))
            execute(Action.FinishWorkout(earlier + 3_600_000))
            execute(Action.StartExtraWorkout(FIXED_NOW - 600_000))
            val today = requireNotNull(state.value.activeWorkoutId)
            execute(Action.AddWorkoutExercise(today, "e-carry", "farmers-carry"))
            execute(Action.AddWorkoutExercise(today, "e-treadmill", "treadmill"))
            execute(Action.SaveEffort(today, "e-carry", "carry-1", 75, Distance(40_000, DistanceUnit.M), 24.0,
                FIXED_NOW))
        })
        rule.setContent {
            RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }, monotonic = { Monotonic(7, "this-process", elapsed) })
        }
        rule.onNodeWithText("History").performClick()
        rule.onNode(hasText(EXTRA_WORKOUT_NAME) and hasAnyAncestor(hasScrollToIndexAction())).performClick()
        rule.onNodeWithContentDescription("Options for Treadmill").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Add missed effort for Treadmill").performClick()
        rule.onNodeWithTag("distance-unit-MI").assertIsSelected()
        rule.onNodeWithTag("edit-effort-minutes").tryPerformAccessibilityChecks()
        captureEvidence("missed-effort")
        rule.onNode(hasText("Cancel") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        rule.onNodeWithContentDescription("Treadmill, ", substring = true).performScrollTo().performClick()
        rule.onNodeWithTag("measured-latest").assert(hasContentDescription("Latest, ${formatDate(earlier)}: " +
            "20 minutes, 5 kilometres; 10 minutes, 1 mile. 1 session since ${formatDate(earlier)}"))
        // Scrolled until only the card's bottom edge is in view, it still speaks as a whole.
        val list = rule.onNode(hasScrollToIndexAction())
        val listTop = list.fetchSemanticsNode().boundsInRoot.top
        val cardBottom = rule.onNodeWithTag("measured-latest").fetchSemanticsNode().boundsInRoot.bottom
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, cardBottom - listTop - 8f) }
        rule.waitForIdle()
        val edge = rule.onNodeWithTag("measured-latest").fetchSemanticsNode()
        assertTrue("Only the card's edge is in view: ${edge.boundsInRoot} of ${edge.size}",
            edge.boundsInRoot.height > 0f && edge.boundsInRoot.height < edge.size.height / 4f)
        rule.onRoot().tryPerformAccessibilityChecks()
        list.performScrollToNode(hasTestTag("effort-chart-distance-MI"))
        rule.onRoot().tryPerformAccessibilityChecks()
        captureEvidence("measured-history")

        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Today").performClick()
        rule.onNodeWithText("Resume workout").performScrollTo().performClick()
        rule.onNodeWithTag("exercise-picker").performScrollToNode(hasTestTag("exercise-chip-0"))
        rule.onNodeWithTag("exercise-chip-0").performClick()
        rule.onNodeWithTag("distance-unit-M").assertIsSelected()
        scrolled("effort-distance").performTextReplacement("40")
        scrolled("effort-kg").assertTextContains("24")
        rule.onRoot().tryPerformAccessibilityChecks()
        captureEvidence("measured-entry")

        tap("start-stopwatch")
        rule.waitUntil(5_000) { repo.state.value.stopwatch != null }
        elapsed += 75_000
        scrolled("stopwatch-time")
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasTestTag("stopwatch-time") and hasContentDescription("Stopwatch 1 minute 15 seconds, timing"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onRoot().tryPerformAccessibilityChecks()
        captureEvidence("stopwatch-running")
        tap("stop-stopwatch")
        rule.waitUntil(5_000) { repo.state.value.stopwatch?.stopped == true }
        scrolled("stopwatch-review")
        rule.onRoot().tryPerformAccessibilityChecks()
        captureEvidence("stopwatch-review")
        tap("discard-stopwatch")
        rule.onNodeWithTag("confirm-button").tryPerformAccessibilityChecks()
        captureEvidence("stopwatch-discard")
        rule.onNodeWithTag("dialog-cancel").performClick()
        rule.waitForIdle()
        assertTrue("Cancel keeps the stopwatch", repo.state.value.stopwatch?.stopped == true)

        tap("log-effort")
        rule.waitUntil(5_000) { carryEfforts(repo).size == 2 }
        val logged = carryEfforts(repo).last()
        assertEquals("Exactly the time the stopwatch ran", 75, logged.durationSeconds)
        assertEquals(Distance(40_000, DistanceUnit.M), logged.distance)
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("effort-row")).fetchSemanticsNodes().size == 2 }
        rule.onAllNodes(hasTestTag("effort-row"))[1].performScrollTo().performClick()
        rule.onNodeWithTag("edit-effort-distance").assertTextContains("40")
        rule.onNodeWithTag("edit-effort-distance").tryPerformAccessibilityChecks()
        captureEvidence("effort-edit")
        rule.onNode(hasText("Cancel") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()

        rule.onNodeWithTag("exercise-picker").performScrollToNode(hasTestTag("exercise-chip-1"))
        rule.onNodeWithTag("exercise-chip-1").performClick()
        scrolled("effort-distance")
        rule.onRoot().tryPerformAccessibilityChecks()
        captureEvidence("cardio-entry")
        assertEquals(null, repo.state.value.stopwatch)
    }

    private fun carryEfforts(repo: com.rebuild.domain.AppRepository) =
        requireNotNull(repo.state.value.activeWorkout).exercises.single { it.id == "e-carry" }.efforts

    /** [tag]'s node, scrolled into view first when it is inside a scrolling container. */
    private fun scrolled(tag: String): SemanticsNodeInteraction {
        if (rule.onAllNodes(hasTestTag(tag) and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithTag(tag).performScrollTo()
        }
        return rule.onNodeWithTag(tag)
    }

    /** Taps [tag] once the app's busy guard allows it: a tap while it is closed would be ignored. */
    private fun tap(tag: String) {
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        scrolled(tag).performClick()
        rule.waitForIdle()
    }

    private fun assertRestAccessibilityTargets() {
        fun composeView(view: android.view.View): android.view.View? {
            if (view.javaClass.simpleName == "AndroidComposeView") return view
            if (view is android.view.ViewGroup) repeat(view.childCount) {
                composeView(view.getChildAt(it))?.let { found -> return found }
            }
            return null
        }
        listOf("Add 30 seconds", "Skip rest", "Rest options").forEach { label ->
            val node = rule.onNodeWithContentDescription(label).assert(
                SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Button)
            ).fetchSemanticsNode()
            rule.runOnUiThread {
                val view = requireNotNull(composeView(rule.activity.window.decorView))
                val info = requireNotNull(view.accessibilityNodeProvider?.createAccessibilityNodeInfo(node.id))
                val rect = android.graphics.Rect()
                info.getBoundsInScreen(rect)
                val minimum = 48 * rule.activity.resources.displayMetrics.density
                assertTrue("$label provider target is clipped: $rect", rect.height() >= minimum && rect.width() >= minimum)
                assertEquals(node.boundsInRoot.height, rect.height().toFloat(), 1f)
                Log.i("RebuildAccessibility", "$label provider=$rect semantic=${node.boundsInRoot} minimumPx=$minimum visible=${info.isVisibleToUser}")
            }
        }
    }

    private fun captureEvidence(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val evidence = File(instrumentation.targetContext.getExternalFilesDir(null), "a11y-$name.png")
        evidence.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
        instrumentation.uiAutomation.executeShellCommand(
            "cp ${evidence.absolutePath} /data/local/tmp/rebuild-015-a11y-$name.png"
        ).use { descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes() }
    }
}
