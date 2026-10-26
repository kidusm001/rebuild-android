package com.rebuild.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rebuild.domain.Action
import com.rebuild.domain.LoggedSet
import com.rebuild.domain.Phase
import com.rebuild.domain.RestTimer
import com.rebuild.domain.Workout
import com.rebuild.domain.WorkoutExercise
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RebuildAppTest {
    @get:Rule
    val rule = createComposeRule()
    private var keyboard: androidx.compose.ui.platform.SoftwareKeyboardController? = null

    private fun launch(repo: FakeRepository, platform: FakePlatform = FakePlatform()) {
        rule.setContent {
            keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
            RebuildApp(repo, platform, clock = { FIXED_NOW })
        }
    }

    private fun FakeRepository.saves() = actions.filterIsInstance<Action.SaveSet>()

    private fun startScheduled() {
        rule.onNodeWithTag("start-scheduled").performClick()
        rule.onNodeWithTag("target-line").assertIsDisplayed()
    }

    @Test
    fun firstRunRequiresExplicitPhaseChoice() {
        val repo = FakeRepository(seedState())
        launch(repo)
        rule.onNodeWithText("Choose where to start").assertIsDisplayed()
        rule.onNodeWithText("Choose a phase").assertIsNotEnabled()
        Phase.entries.forEach { rule.onNodeWithTag("phase-${it.name}").assertIsDisplayed() }

        rule.onNodeWithTag("phase-INTRO_ONE").performClick()
        rule.onNodeWithText("Start with Intro 1").assertIsEnabled().performClick()
        rule.waitForIdle()

        assertEquals(listOf(Action.SelectPhase(Phase.INTRO_ONE)), repo.actions)
        rule.onNodeWithText("Start Upper A").assertIsDisplayed()
    }

    @Test
    fun startAndLogWorkingSet() {
        val repo = FakeRepository(seedState(Phase.INTRO_TWO))
        launch(repo)
        startScheduled()
        assertTrue(repo.actions.single() is Action.StartWorkout)
        rule.onNodeWithTag("target-line").assertTextContains("2 working sets", substring = true)

        rule.onNodeWithTag("entry-kg").performScrollTo().performTextInput("40")
        rule.onNodeWithTag("entry-reps").performTextInput("8")
        rule.onNodeWithTag("log-set").performScrollTo().performClick()
        rule.waitForIdle()

        val save = repo.saves().single()
        assertEquals(40.0, save.kg)
        assertEquals(8, save.reps)
        assertEquals(false, save.warmup)
        rule.onNodeWithTag("working-count").assertTextContains("Working sets: 1 of 2", substring = true)
    }

    @Test
    fun warmupIsLoggedButNotCounted() {
        val repo = FakeRepository(seedState(Phase.INTRO_ONE))
        launch(repo)
        startScheduled()
        rule.onNodeWithTag("entry-kg").performScrollTo().performTextInput("20")
        rule.onNodeWithTag("entry-reps").performTextInput("10")
        rule.onNodeWithTag("entry-warmup").performScrollTo().performClick()
        rule.onNodeWithTag("log-set").performScrollTo().performClick()
        rule.waitForIdle()

        assertTrue(repo.saves().single().warmup)
        rule.onNodeWithTag("working-count")
            .assertTextContains("Working sets: 0 of 1 · 1 warm-up", substring = true)
    }

    @Test
    fun previousComparablePrefillsButNeverAutoCompletes() {
        val previous = Workout(
            id = "old", templateId = "upper-a", name = "Upper A", phase = Phase.INTRO_ONE,
            startedAt = FIXED_NOW - 7 * 86_400_000L, finishedAt = FIXED_NOW - 7 * 86_400_000L + 3_600_000L,
            exercises = listOf(
                WorkoutExercise(
                    id = "old-we", slotId = "s-bench", exercise = BENCH, targetSets = 1, baselineSets = 3, upperSets = 4,
                    minReps = 6, maxReps = 10, targetRir = 4, restSeconds = 180,
                    sets = listOf(
                        LoggedSet("w1", 30.0, 12, null, warmup = true, completedAt = 1),
                        LoggedSet("s1", 50.0, 9, 3.0, warmup = false, completedAt = 2)
                    )
                )
            )
        )
        val repo = FakeRepository(seedState(Phase.INTRO_ONE, listOf(previous)))
        launch(repo)
        startScheduled()

        rule.onNodeWithContentDescription("Previous set 1: 50 kg × 9 @ 3 RIR").assertExists()
        rule.onNodeWithTag("entry-kg").assertTextContains("50")
        rule.onNodeWithTag("entry-reps").assertTextContains("9")
        rule.waitForIdle()
        assertTrue("prefill must not log anything", repo.saves().isEmpty())
        rule.onNodeWithTag("working-count").assertTextContains("Working sets: 0 of 1", substring = true)
    }

    @Test
    fun switchingExerciseRebindsWorkspaceEntryAndSave() {
        // Different previous sets per exercise, so a stale workspace or entry would show (and log) the wrong values.
        val previous = Workout(
            id = "old", templateId = "upper-a", name = "Upper A", phase = Phase.INTRO_ONE,
            startedAt = FIXED_NOW - 7 * 86_400_000L, finishedAt = FIXED_NOW - 7 * 86_400_000L + 3_600_000L,
            exercises = listOf(
                WorkoutExercise(
                    id = "old-bench", slotId = "s-bench", exercise = BENCH, targetSets = 1, baselineSets = 3, upperSets = 4,
                    minReps = 6, maxReps = 10, targetRir = 4, restSeconds = 180,
                    sets = listOf(LoggedSet("b1", 50.0, 9, 3.0, warmup = false, completedAt = 1))
                ),
                WorkoutExercise(
                    id = "old-db", slotId = "s-fly", exercise = DB_PRESS, targetSets = 1, baselineSets = 2, upperSets = 3,
                    minReps = 10, maxReps = 15, targetRir = 4, restSeconds = 90,
                    sets = listOf(LoggedSet("d1", 14.0, 12, null, warmup = false, completedAt = 2))
                )
            )
        )
        val repo = FakeRepository(seedState(Phase.INTRO_ONE, listOf(previous)))
        launch(repo)
        startScheduled()
        rule.onNodeWithTag("exercise-chip-0").assertIsSelected()
        rule.onNode(hasText("Bench press") and isHeading()).assertExists()
        rule.onNodeWithText("External load · kg total").assertExists()
        rule.onNodeWithTag("entry-kg").assertTextContains("50")

        rule.onNodeWithTag("exercise-chip-1").performClick()
        rule.onNodeWithTag("exercise-chip-1").assertIsSelected()
        rule.onNodeWithTag("exercise-chip-0").assertIsNotSelected()
        rule.onNodeWithContentDescription("exercise 2 of 2", substring = true).assertExists()
        rule.onNode(hasText("Dumbbell press") and isHeading()).assertExists()
        rule.onNode(hasText("Bench press") and isHeading()).assertDoesNotExist()
        rule.onNodeWithText("External load · kg per dumbbell").assertExists()
        rule.onNodeWithContentDescription("Previous set 1: 14", substring = true).assertExists()
        rule.onNodeWithTag("entry-kg").assertTextContains("14")
        rule.onNodeWithTag("entry-reps").assertTextContains("12")
        rule.onNodeWithTag("log-set").performScrollTo().performClick()
        rule.waitForIdle()

        val dbPress = repo.state.value.activeWorkout!!.exercises[1]
        val save = repo.saves().single()
        assertEquals(dbPress.id, save.exerciseId)
        assertEquals(14.0, save.kg)
        assertEquals(12, save.reps)
        assertEquals(1, dbPress.sets.size)
        rule.onNodeWithContentDescription("Set 1: 14", substring = true).assertExists()

        rule.onNodeWithTag("exercise-chip-0").performClick()
        rule.onNodeWithTag("exercise-chip-0").assertIsSelected()
        rule.onNode(hasText("Bench press") and isHeading()).assertExists()
        rule.onNodeWithText("External load · kg total").assertExists()
        rule.onNodeWithTag("entry-kg").assertTextContains("50")
        rule.onNodeWithTag("working-count").assertTextContains("Working sets: 0 of 1", substring = true)
        assertTrue(repo.state.value.activeWorkout!!.exercises[0].sets.isEmpty())
    }

    @Test
    fun externalLoadRequiresKgAndRepsBeforeSaving() {
        val repo = FakeRepository(seedState(Phase.INTRO_ONE))
        launch(repo)
        startScheduled()
        rule.onNodeWithTag("log-set").performScrollTo().performClick()
        rule.onNodeWithText("Enter the reps you completed (1 or more).").assertIsDisplayed()
        rule.onNodeWithTag("entry-reps").performScrollTo().performTextInput("8")
        rule.onNodeWithTag("log-set").performScrollTo().performClick()
        rule.onNodeWithText("Enter the weight in kg.").assertIsDisplayed()
        assertTrue(repo.saves().isEmpty())
    }

    @Test
    fun slowSaveBlocksDuplicateTaps() {
        val repo = FakeRepository(seedState(Phase.INTRO_ONE))
        launch(repo)
        startScheduled()
        rule.onNodeWithTag("entry-kg").performScrollTo().performTextInput("40")
        rule.onNodeWithTag("entry-reps").performTextInput("8")
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate
        rule.onNodeWithTag("log-set").performScrollTo().performClick()
        rule.onNodeWithTag("log-set").assertIsNotEnabled().performClick()
        gate.complete(Unit)
        rule.waitForIdle()
        assertEquals(1, repo.saves().size)
    }

    @Test
    fun activeWorkoutMustBeResolvedBeforeStartingAnother() {
        val repo = FakeRepository(seedState(Phase.INTRO_ONE))
        launch(repo)
        startScheduled()
        rule.onNodeWithContentDescription("Back").performClick()

        rule.onNodeWithTag("active-banner").assertIsDisplayed()
        rule.onNodeWithTag("start-scheduled").assertDoesNotExist()
        rule.onNodeWithText("Start").assertDoesNotExist()

        rule.onNodeWithText("Discard").performClick()
        rule.onNodeWithText("Discard workout?").assertIsDisplayed()
        rule.onNodeWithTag("confirm-button").performClick()
        rule.waitForIdle()

        assertTrue(repo.actions.last() == Action.DiscardWorkout)
        rule.onNodeWithTag("start-scheduled").assertIsDisplayed()
    }

    @Test
    fun freshResumeOpensFirstExerciseBelowTargetWhileAnOpenChoiceStays() {
        val repo = FakeRepository(seedState(Phase.INTRO_ONE))
        launch(repo)
        startScheduled()
        rule.onNodeWithTag("exercise-chip-0").assertIsSelected()
        rule.onNodeWithTag("entry-kg").performScrollTo().performTextInput("40")
        rule.onNodeWithTag("entry-reps").performTextInput("8")
        rule.onNodeWithTag("log-set").performScrollTo().performClick()
        rule.waitForIdle()

        // Meeting the target never moves the exercise that is already open.
        rule.onNodeWithTag("working-count").assertTextContains("Working sets: 1 of 1", substring = true)
        rule.onNodeWithTag("exercise-chip-0").assertIsSelected()

        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Opens Dumbbell press").assertExists()
        rule.onNodeWithText("Resume workout").performClick()
        rule.onNodeWithTag("exercise-chip-1").assertIsSelected()
        rule.onNodeWithContentDescription("exercise 2 of 2", substring = true).assertExists()
        rule.onNode(hasText("Dumbbell press") and isHeading()).assertExists()

        // Any exercise can still be chosen by hand; nothing was logged by resuming.
        rule.onNodeWithTag("exercise-chip-0").performClick()
        rule.onNodeWithTag("exercise-chip-0").assertIsSelected()
        rule.onNodeWithContentDescription("exercise 1 of 2", substring = true).assertExists()
        assertEquals(1, repo.saves().size)
    }

    @Test
    fun inputSurvivesRecreation() {
        val repo = FakeRepository(seedState(Phase.INTRO_ONE))
        val platform = FakePlatform()
        val tester = StateRestorationTester(rule)
        tester.setContent { RebuildApp(repo, platform, clock = { FIXED_NOW }) }
        startScheduled()
        rule.onNodeWithTag("entry-kg").performScrollTo().performTextReplacement("42.5")
        rule.onNodeWithTag("entry-reps").performTextInput("7")

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag("target-line").assertIsDisplayed()
        rule.onNodeWithTag("entry-kg").assertTextContains("42.5")
        rule.onNodeWithTag("entry-reps").assertTextContains("7")
        assertTrue(repo.saves().isEmpty())
    }

    @Test
    fun restoreReplacingWorkoutDropsStaleDraft() {
        val repo = FakeRepository(seedState(Phase.INTRO_ONE))
        launch(repo)
        startScheduled()
        rule.onNodeWithTag("entry-kg").performScrollTo().performTextInput("77")
        rule.onNodeWithTag("entry-reps").performTextInput("5")

        val active = repo.state.value.activeWorkout!!
        val first = active.exercises.first()
        val restored = active.copy(
            id = "restored",
            exercises = active.exercises.map {
                if (it.id == first.id) it.copy(sets = listOf(LoggedSet("r1", 42.0, 11, null, false, FIXED_NOW - 60_000))) else it
            }
        )
        repo.replace(repo.state.value.copy(workouts = listOf(restored), activeWorkoutId = "restored"))
        rule.waitForIdle()
        rule.onNodeWithTag("working-count").assertTextContains("Working sets: 1 of 1", substring = true)
        rule.onNodeWithTag("extra-set").performScrollTo().performClick()
        rule.onNodeWithTag("entry-kg").performScrollTo().assertTextContains("42")
        rule.onNodeWithTag("entry-reps").assertTextContains("11")

        repo.replace(repo.state.value.copy(workouts = emptyList(), activeWorkoutId = null))
        rule.waitForIdle()
        rule.onNodeWithTag("start-scheduled").assertIsDisplayed()
        assertTrue(repo.saves().isEmpty())
    }

    @Test
    fun shorteningRestPastZeroSkipsInsteadOfSchedulingThePast() {
        val timer = RestTimer("t1", "w", "we", FIXED_NOW + 20_000)
        val repo = FakeRepository(seedState(Phase.INTRO_ONE).copy(restTimer = timer))
        launch(repo)
        rule.onNodeWithTag("rest-status").assertTextContains("0:20", substring = true)
        rule.onNodeWithContentDescription("Rest options").performClick()
        rule.onNodeWithText("−30 s").performClick()
        rule.waitForIdle()
        assertEquals(listOf<Action>(Action.SkipRest), repo.actions)

        assertEquals(Action.AdjustRest("t1", FIXED_NOW + 1_000), restChange(timer, FIXED_NOW + 500, FIXED_NOW))
        assertEquals(Action.AdjustRest("t1", FIXED_NOW + 60_000), restChange(timer, FIXED_NOW + 60_000, FIXED_NOW))
        assertEquals(Action.SkipRest, restChange(timer, FIXED_NOW, FIXED_NOW))
    }

    @Test
    fun doubleTapAfterCommitDoesNotLogASecondSet() {
        val repo = FakeRepository(seedState(Phase.INTRO_TWO))
        launch(repo)
        startScheduled()
        rule.onNodeWithTag("entry-kg").performScrollTo().performTextInput("40")
        rule.onNodeWithTag("entry-reps").performTextInput("8")
        rule.onNodeWithTag("log-set").performScrollTo().performTouchInput {
            doubleClick(delayMillis = 100)
        }
        rule.waitForIdle()

        // Deliver two actual taps 100ms apart, rather than idle/scroll calls that can outlast the guard.
        rule.onNodeWithTag("working-count").assertTextContains("Working sets: 1 of 2", substring = true)
        rule.onNodeWithTag("entry-kg").assertTextContains("40")
        assertEquals(1, repo.saves().size)

        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodes(hasTestTag("log-set") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("log-set").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(2, repo.saves().size)
        assertEquals(2, repo.saves().map { it.setId }.toSet().size)
        rule.onNodeWithTag("working-count").assertTextContains("Working sets: 2 of 2", substring = true)
    }

    @Test
    fun draftRestoredAfterBackgroundCommitGetsFreshSetId() {
        val repo = FakeRepository(seedState(Phase.INTRO_TWO))
        var registry by mutableStateOf(SaveableStateRegistry(null) { true })
        var shown by mutableStateOf(true)
        rule.setContent {
            if (shown) {
                CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                    RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW })
                }
            }
        }
        startScheduled()
        rule.onNodeWithTag("entry-kg").performScrollTo().performTextInput("40")
        rule.onNodeWithTag("entry-reps").performTextInput("8")
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate
        rule.onNodeWithTag("log-set").performScrollTo().performClick()

        // State is saved while the write is still pending, then the process "dies" and the write lands.
        val saved = rule.runOnIdle { registry.performSave() }
        rule.runOnIdle { shown = false }
        gate.complete(Unit)
        rule.waitUntil(timeoutMillis = 5_000) { repo.state.value.activeWorkout!!.exercises.first().sets.size == 1 }
        rule.runOnIdle {
            registry = SaveableStateRegistry(saved) { true }
            shown = true
        }

        rule.onNodeWithTag("working-count").assertTextContains("Working sets: 1 of 2", substring = true)
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodes(hasTestTag("log-set") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("entry-kg").performScrollTo().performTextReplacement("45")
        rule.onNodeWithTag("entry-reps").performTextReplacement("9")
        rule.onNodeWithTag("log-set").performScrollTo().performClick()
        rule.waitForIdle()

        val ids = repo.saves().map { it.setId }
        assertEquals(2, ids.size)
        assertEquals("the restored form must not reuse the committed set's id", 2, ids.toSet().size)
        assertEquals(2, repo.state.value.activeWorkout!!.exercises.first().sets.size)
        assertEquals(40.0, repo.state.value.activeWorkout!!.exercises.first().sets[0].kg)
        assertEquals(8, repo.state.value.activeWorkout!!.exercises.first().sets[0].reps)
        assertEquals(45.0, repo.state.value.activeWorkout!!.exercises.first().sets[1].kg)
        assertEquals(9, repo.state.value.activeWorkout!!.exercises.first().sets[1].reps)
        rule.onNodeWithTag("working-count").assertTextContains("Working sets: 2 of 2", substring = true)
    }

    @Test
    fun exerciseNoteDraftSurvivesSwitchingAndIsNotLostOnBack() {
        val repo = FakeRepository(seedState(Phase.INTRO_ONE))
        launch(repo)
        startScheduled()
        val firstId = repo.state.value.activeWorkout!!.exercises.first().id
        rule.onNodeWithTag("edit-exercise-note").performScrollTo().performClick()
        rule.onNodeWithTag("exercise-note").performScrollTo().performTextInput("Left elbow ok")
        rule.runOnIdle { keyboard?.hide() }
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("exercise-chip-1")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("exercise-chip-1").performClick()
        rule.onNodeWithTag("edit-exercise-note").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("exercise-chip-0").performClick()
        rule.onNodeWithTag("exercise-note").performScrollTo().assertTextContains("Left elbow ok", substring = true)

        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Unsaved notes").assertIsDisplayed()
        val noteGate = CompletableDeferred<Unit>()
        repo.gate = noteGate
        rule.onNodeWithTag("save-notes").performClick()
        rule.onNodeWithTag("save-notes").assertIsNotEnabled()
        rule.onNodeWithText("Discard").assertIsNotEnabled()
        rule.onNodeWithText("Keep editing").assertIsNotEnabled()
        noteGate.complete(Unit)
        rule.waitForIdle()

        assertEquals(
            Action.ExerciseNote(repo.state.value.activeWorkout!!.id, firstId, "Left elbow ok"),
            repo.actions.filterIsInstance<Action.ExerciseNote>().single()
        )
        rule.onNodeWithTag("active-banner").assertIsDisplayed()
    }

    @Test
    fun historicalNoteEditingCannotBypassTheUnsavedGuardViaBottomTabs() {
        val repo = FakeRepository(seedState(Phase.INTRO_ONE))
        runBlocking {
            repo.execute(Action.StartWorkout(repo.state.value.templates.first().id, FIXED_NOW))
            repo.execute(Action.FinishWorkout(FIXED_NOW + 1_000))
        }
        launch(repo)
        rule.onNodeWithText("History").performClick()
        rule.onNodeWithText("Upper A").performClick()
        rule.onNodeWithText("Add workout note").performScrollTo().performClick()
        rule.onNodeWithTag("workout-note").performTextInput("Keep this history note")
        assertTrue(rule.onAllNodes(hasText("More")).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Unsaved notes").assertIsDisplayed()
        rule.onNodeWithText("Keep editing").performClick()
        rule.onNodeWithTag("workout-note").assertTextContains("Keep this history note")
    }

    @Test
    fun noteLimitPreservesWholeEmojiAtTheBoundary() {
        val repo = FakeRepository(seedState(Phase.INTRO_ONE))
        launch(repo)
        startScheduled()
        val prefix = "a".repeat(4095)
        rule.onNodeWithTag("edit-exercise-note").performScrollTo().performClick()
        rule.onNodeWithTag("exercise-note").performScrollTo().performTextReplacement(prefix + "\uD83D\uDE42")
        val limited = rule.onNodeWithTag("exercise-note").fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text
        assertEquals(4095, limited.length)
        assertEquals(prefix, limited)

        val exact = "a".repeat(4094) + "\uD83D\uDE42"
        rule.onNodeWithTag("exercise-note").performTextReplacement(exact)
        rule.onNodeWithText("Save note").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(exact, repo.actions.filterIsInstance<Action.ExerciseNote>().single().note)
    }

    @Test
    fun restoredMissedSetDialogClosesAfterItsBackgroundCommit() {
        val repo = FakeRepository(seedState(Phase.INTRO_ONE))
        runBlocking {
            repo.execute(Action.StartWorkout(repo.state.value.templates.first().id, FIXED_NOW))
            repo.execute(Action.FinishWorkout(FIXED_NOW + 1_000))
        }
        var registry by mutableStateOf(SaveableStateRegistry(null) { true })
        var shown by mutableStateOf(true)
        rule.setContent {
            if (shown) {
                CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                    RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW })
                }
            }
        }
        rule.onNodeWithText("History").performClick()
        rule.onNodeWithText("Upper A").performClick()
        rule.onNodeWithContentDescription("Options for Bench press").performScrollTo().performClick()
        rule.onNodeWithText("Add missed set").performClick()
        rule.onNodeWithTag("edit-kg").performTextInput("40")
        rule.onNodeWithTag("edit-reps").performTextInput("8")
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate
        rule.onNodeWithText("Save").performClick()
        val saved = rule.runOnIdle { registry.performSave() }
        rule.runOnIdle { shown = false }
        gate.complete(Unit)
        rule.waitUntil(timeoutMillis = 5_000) {
            repo.state.value.workouts.single().exercises.first().sets.size == 1
        }
        rule.runOnIdle {
            registry = SaveableStateRegistry(saved) { true }
            shown = true
        }
        rule.onNodeWithText("Add a missed set").assertDoesNotExist()
        rule.onNodeWithTag("edit-kg").assertDoesNotExist()
        rule.onNodeWithContentDescription("Options for Bench press").performScrollTo().performClick()
        rule.onNodeWithText("Add missed set").assertIsEnabled().performClick()
        rule.onNodeWithText("Add a missed set").assertIsDisplayed()
        rule.onNodeWithTag("edit-kg").performTextInput("45")
        rule.onNodeWithTag("edit-reps").performTextInput("9")
        rule.onNodeWithText("Save").performClick()
        rule.waitForIdle()
        val sets = repo.state.value.workouts.single().exercises.first().sets
        assertEquals(2, sets.map { it.id }.toSet().size)
        assertEquals(40.0, sets[0].kg)
        assertEquals(8, sets[0].reps)
        assertEquals(45.0, sets[1].kg)
        assertEquals(9, sets[1].reps)
    }

    @Test
    fun phaseChangeIsExplainedAndDisabledWhileWorkoutIsActive() {
        val repo = FakeRepository(seedState(Phase.INTRO_ONE))
        launch(repo)
        startScheduled()
        rule.onNodeWithContentDescription("Back").performClick()
        // Today no longer repeats the phase; Plan names it and offers the change.
        rule.onNodeWithText("Plan").performClick()
        rule.onNodeWithTag("plan-change-phase").performScrollTo().performClick()
        rule.onNodeWithTag("phase-locked").assertIsDisplayed()
        rule.onNodeWithTag("phase-BASELINE").performScrollTo().performClick()
        rule.onNodeWithText("Change phase").performScrollTo().assertIsNotEnabled()
        assertTrue(repo.actions.none { it is Action.SelectPhase })
    }

    @Test
    fun productionAdjustAndCustomDialogsOpenWithoutNestedScrollFailure() {
        val repo = FakeRepository(seedState(Phase.INTRO_ONE))
        launch(repo)
        startScheduled()
        rule.onNodeWithTag("exercise-details").performScrollTo().performClick()
        rule.onNodeWithText("Adjust today").performScrollTo().performClick()
        rule.onNodeWithText("Add custom exercise").performScrollTo().performClick()
        rule.onNodeWithText("Custom exercise").assertIsDisplayed()
        rule.onNodeWithText("How is it loaded?").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun returningNoteDraftCanBeClearedAndRetypedWithoutClosingEditor() {
        val repo = FakeRepository(seedState(Phase.INTRO_ONE))
        launch(repo)
        startScheduled()
        rule.onNodeWithTag("edit-exercise-note").performScrollTo().performClick()
        rule.onNodeWithTag("exercise-note").performTextInput("Original draft")
        rule.onNodeWithTag("exercise-chip-1").performClick()
        rule.onNodeWithTag("exercise-chip-0").performClick()
        rule.onNodeWithTag("exercise-note").performScrollTo().performTextReplacement("")
        rule.onNodeWithTag("exercise-note").assertIsDisplayed().performTextInput("Replacement draft")
        rule.onNodeWithTag("exercise-note").assertTextContains("Replacement draft")
    }

    @Test
    fun uiLimitsAcceptEveryValueTheRepositoryAccepts() {
        assertTrue(validateSet(BENCH, "2500", "5", "") is SetValidation.Valid)
        assertTrue(validateSet(BENCH, "10001", "5", "") is SetValidation.Invalid)
        assertEquals(1, parseRest("1"))
        assertEquals(3600, parseRest("3600"))
        assertEquals(null, parseRest("3601"))
    }
}
