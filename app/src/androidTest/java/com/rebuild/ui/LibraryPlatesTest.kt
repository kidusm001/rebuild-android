package com.rebuild.ui

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rebuild.data.RebuildRepository
import com.rebuild.data.SnapshotCodec
import com.rebuild.data.SnapshotPersistence
import com.rebuild.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

internal fun libraryRepository(activeCable: Boolean = false): RebuildRepository = runBlocking {
    val persistence = object : SnapshotPersistence {
        var json: String = SnapshotCodec.encode(Seed.initialState().copy(phase = Phase.INTRO_TWO))
        override fun read() = json
        override fun write(snapshot: String) { json = snapshot }
    }
    RebuildRepository.fromPersistence(persistence, clock = { FIXED_NOW }).also { repo ->
        if (activeCable) {
            repo.execute(Action.StartWorkout("intro-upper", FIXED_NOW))
            val we = requireNotNull(repo.state.value.activeWorkout).exercises.first()
            repo.execute(Action.EditToday(we.id, "row-cable", 2, 180))
        }
    }
}

@RunWith(AndroidJUnit4::class)
class LibraryPlatesTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun launch(repo: RebuildRepository) {
        val ui = MainThreadCollection(repo)
        rule.setContent { RebuildApp(ui, FakePlatform(), clock = { FIXED_NOW }) }
    }
    private fun switch(unit: LoadUnit) {
        rule.waitUntil(5000) {
            rule.onAllNodes(hasTestTag("choose-load-unit") and isEnabled()).fetchSemanticsNodes().size == 1
        }
        rule.onNodeWithTag("choose-load-unit").performScrollTo().assertIsEnabled().performClick()
        rule.onNodeWithTag("unit-${unit.name}").performScrollTo().performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTag("unit-${unit.name}").fetchSemanticsNodes().isEmpty() }
        rule.waitForIdle()
    }
    private fun logPlates(repo: RebuildRepository) {
        val previousCount = repo.state.value.activeWorkout!!.exercises.first().sets.size
        rule.onNodeWithTag("entry-plates").performScrollTo().performTextReplacement("5")
        rule.onNodeWithTag("entry-reps").performTextReplacement("12")
        rule.onNodeWithTag("log-set").performScrollTo().performClick()
        rule.waitUntil(5000) {
            repo.state.value.activeWorkout!!.exercises.first().sets.size == previousCount + 1
        }
        rule.waitForIdle()
    }

    @Test fun librarySearchAliasesEmptyResetAndFiltersWithoutStartingWorkout() {
        val repo = libraryRepository()
        launch(repo)
        rule.onNodeWithText("More").performClick()
        rule.onNodeWithText("Exercises", substring = false).performClick()
        rule.onNodeWithTag("library-search").performTextReplacement("lat pull-down")
        rule.onNodeWithTag("library-pulldown").assertIsDisplayed()
        rule.onNodeWithTag("library-search").performTextReplacement("no such exercise zzz")
        rule.onNodeWithText("No exercises match.", substring = true).assertExists()
        rule.onNodeWithText("Reset filters").performClick()
        rule.onNodeWithTag("library-filter-muscle").performClick()
        rule.onNodeWithText(MuscleGroup.BACK.title).performClick()
        rule.onNodeWithTag("library-filter-equipment").performClick()
        rule.onNodeWithText(Equipment.CABLE.title, substring = false).performClick()
        rule.onNodeWithTag("library-search").performTextReplacement("pulldown")
        rule.onNodeWithTag("library-pulldown").assertIsDisplayed()
        assertNull(repo.state.value.activeWorkoutId)
        assertTrue(repo.state.value.overrides.isEmpty())
    }

    @Test fun sharedLibraryPickerSavesOnlyFuturePlanWhenAsked() {
        val repo = libraryRepository()
        launch(repo)
        rule.onNodeWithText("Plan", substring = false).performClick()
        rule.onNodeWithTag("plan-slot-intro-chest").performScrollTo().performClick()
        rule.onNodeWithText("All exercises").performScrollTo().performClick()
        rule.onNodeWithTag("library-search").performTextReplacement("barbell bench")
        rule.onNodeWithTag("library-bench-barbell").performClick()
        rule.onNodeWithText("Save to plan").performClick()
        rule.waitUntil(5000) { repo.state.value.overrides.isNotEmpty() }
        rule.waitForIdle()
        assertEquals("bench-barbell", repo.state.value.overrides.single().exerciseId)
        assertNull(repo.state.value.activeWorkoutId)
    }

    @Test fun sharedLibraryPickerAdjustsTodayWithoutChangingFuturePlan() {
        val repo = libraryRepository(activeCable = true)
        val before = repo.state.value.overrides
        launch(repo)
        rule.onNodeWithText("Resume workout").performClick()
        rule.onNodeWithTag("exercise-details").performScrollTo().performClick()
        rule.onNodeWithText("Adjust today").performScrollTo().performClick()
        rule.onNodeWithText("All exercises").performScrollTo().performClick()
        rule.onNodeWithTag("library-search").performTextReplacement("lat pull-down")
        rule.onNodeWithTag("library-pulldown").performClick()
        rule.onNodeWithText("Apply").performClick()
        rule.waitUntil(5000) { repo.state.value.activeWorkout!!.exercises.first().exercise.id == "pulldown" }
        rule.waitForIdle()
        assertEquals("pulldown", repo.state.value.activeWorkout!!.exercises.first().exercise.id)
        assertEquals(before, repo.state.value.overrides)
    }

    @Test fun customCableMetadataAndFuturePreferenceAreExplicit() {
        val repo = libraryRepository()
        launch(repo)
        rule.onNodeWithText("More").performClick()
        rule.onNodeWithText("Exercises", substring = false).performClick()
        rule.onNodeWithTag("library-search").performTextReplacement("new custom name zzz")
        rule.onNodeWithText("Add custom exercise").performScrollTo().performClick()
        rule.onNodeWithText("Name", substring = false).performTextInput("My cable row")
        rule.onNodeWithText("Equipment: ${Equipment.UNKNOWN.title}").performScrollTo().performClick()
        rule.onNodeWithText(Equipment.CABLE.title, substring = false).performClick()
        rule.onNodeWithText("Add", substring = false).performClick()
        rule.waitUntil(5000) { repo.state.value.exercises.any { it.custom } }
        rule.waitForIdle()
        val created = repo.state.value.exercises.single { it.custom }
        assertEquals(Equipment.CABLE, created.equipment)
        assertEquals(MuscleGroup.UNKNOWN, created.primaryMuscle)
        assertEquals(LoadUnit.KG, created.preferredLoadUnit)
        rule.onNodeWithTag("unit-PLATES").performScrollTo().performClick()
        rule.waitUntil(5000) { repo.state.value.exercises.single { it.custom }.preferredLoadUnit == LoadUnit.PLATES }
        rule.onNodeWithText("Done").performClick()
        rule.waitForIdle()
        assertEquals(LoadUnit.PLATES, repo.state.value.exercises.single { it.custom }.preferredLoadUnit)
        assertTrue(repo.state.value.overrides.isEmpty())
        assertNull(repo.state.value.activeWorkout)
    }

    @Test fun mixedUnitLogRecreationAndHistoryCorrectionPreserveKgAndRest() {
        val repo = libraryRepository(activeCable = true)
        val workout = repo.state.value.activeWorkout!!
        val we = workout.exercises.first()
        runBlocking { repo.execute(Action.SaveSet(workout.id, we.id, "old-kg", 42.5, 8, 2.0, false, FIXED_NOW)) }
        val oldSet = repo.state.value.activeWorkout!!.exercises.first().sets.single()
        val restoration = StateRestorationTester(rule)
        val ui = MainThreadCollection(repo)
        restoration.setContent { RebuildApp(ui, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithText("Resume workout").performClick()
        switch(LoadUnit.PLATES)
        rule.onNodeWithTag("entry-plates").assertTextContains("")
        assertEquals("", rule.onNodeWithTag("entry-plates").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        logPlates(repo)
        val logged = repo.state.value.activeWorkout!!.exercises.first()
        assertEquals(oldSet, logged.sets.first())
        assertEquals(5, logged.sets.last().plates)
        assertNull(logged.sets.last().kg)
        val rest = repo.state.value.restTimer
        assertNotNull(rest)
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(2, repo.state.value.activeWorkout!!.exercises.first().sets.size)
        switch(LoadUnit.KG)
        rule.onNodeWithTag("extra-set").performScrollTo().performClick()
        rule.onNodeWithTag("entry-kg").performScrollTo().assertTextContains("42.5")
        runBlocking { repo.execute(Action.FinishWorkout(FIXED_NOW + 1000)) }
        rule.waitForIdle()
        rule.onNodeWithText("History", substring = false).performClick()
        rule.onNodeWithText("Upper", substring = false).performClick()
        rule.onNodeWithContentDescription("Set 2: 5 plates/stack × 12").performScrollTo().performClick()
        rule.onNodeWithTag("edit-plates").performTextReplacement("6")
        rule.onNodeWithText("Save", substring = false).performClick()
        rule.waitUntil(5000) { repo.state.value.workouts.single().exercises.first().sets.last().plates == 6 }
        rule.waitForIdle()
        val corrected = repo.state.value.workouts.single().exercises.first().sets
        assertEquals(oldSet, corrected.first())
        assertEquals(6, corrected.last().plates)
        assertEquals(logged.sets.last().completedAt, corrected.last().completedAt)
        assertNull(repo.state.value.restTimer)
    }

    @Test fun unitSwitchDropsUnsavedLoadAndPlateDraftSurvivesRecreation() {
        val repo = libraryRepository(activeCable = true)
        val restoration = StateRestorationTester(rule)
        val ui = MainThreadCollection(repo)
        restoration.setContent { RebuildApp(ui, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithText("Resume workout").performClick()
        rule.onNodeWithTag("entry-kg").performScrollTo().performTextReplacement("77")
        switch(LoadUnit.PLATES)
        rule.onNodeWithTag("entry-plates").performScrollTo()
        assertEquals("", rule.onNodeWithTag("entry-plates").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        rule.onNodeWithTag("entry-plates").performTextReplacement("5")
        rule.onNodeWithTag("entry-reps").performTextReplacement("12")
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("entry-plates").assertTextContains("5")
        assertTrue(repo.state.value.activeWorkout!!.exercises.first().sets.isEmpty())
        rule.onNodeWithTag("log-set").performScrollTo().performClick()
        rule.waitUntil(5000) { repo.state.value.activeWorkout!!.exercises.first().sets.size == 1 }
        rule.waitForIdle()
        val set = repo.state.value.activeWorkout!!.exercises.first().sets.single()
        assertEquals(LoadUnit.PLATES, set.loadUnit)
        assertEquals(5, set.plates)
        assertNull(set.kg)
    }

    @Test fun historicalUnitCorrectionDoesNotChangeOtherSetsOrPreference() {
        val repo = libraryRepository(activeCable = true)
        val w = repo.state.value.activeWorkout!!
        val e = w.exercises.first()
        runBlocking {
            repo.execute(Action.SaveSet(w.id, e.id, "kg1", 5.0, 10, null, false, FIXED_NOW))
            repo.execute(Action.SaveSet(w.id, e.id, "kg2", 10.0, 12, null, false, FIXED_NOW))
            repo.execute(Action.FinishWorkout(FIXED_NOW + 1000))
        }
        launch(repo)
        rule.onNodeWithText("History").performClick()
        rule.onNodeWithText("Upper", substring = false).performClick()
        rule.onNodeWithContentDescription("Set 1: 5 kg × 10").performScrollTo().performClick()
        rule.onNodeWithTag("unit-PLATES").performClick()
        assertEquals("", rule.onNodeWithTag("edit-plates").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        rule.onNodeWithTag("edit-plates").performTextReplacement("3")
        rule.onNodeWithText("Save", substring = false).performClick()
        rule.waitUntil(5000) { repo.state.value.workouts.single().exercises.first().sets.first().plates == 3 }
        rule.waitForIdle()
        val sets = repo.state.value.workouts.single().exercises.first().sets
        assertEquals(3, sets.first().plates)
        assertEquals(10.0, sets.last().kg)
        assertEquals(LoadUnit.KG, repo.state.value.exercises.single { it.id == "row-cable" }.preferredLoadUnit)
    }

    /**
     * Switching between a kg-only machine and a plate-capable cable exercise must rebind the whole workspace: the
     * selected pill, heading, loading line, same-unit comparison and prefill, and the exercise a Log tap actually
     * persists to. (With Compose 1.8.0 a switch once left the previous exercise's content and entry in place.)
     */
    @Test fun switchingBetweenKgAndPlateExercisesRebindsEntryAndPersistsToSelectedExercise() {
        val persistence = object : SnapshotPersistence {
            var json: String = SnapshotCodec.encode(Seed.initialState().copy(phase = Phase.INTRO_TWO))
            override fun read() = json
            override fun write(snapshot: String) { json = snapshot }
        }
        val lastWeek = FIXED_NOW - 7 * 86_400_000L
        val real = runBlocking {
            RebuildRepository.fromPersistence(persistence, clock = { FIXED_NOW }).also { r ->
                // Last time: the machine press in kg and the cable row in plates per stack.
                r.execute(Action.StartWorkout("intro-upper", lastWeek))
                val last = requireNotNull(r.state.value.activeWorkout)
                r.execute(Action.SaveSet(last.id, last.exercises[0].id, "last-press", 40.0, 10, null, false, lastWeek + 60_000))
                r.execute(Action.SetLoadUnit(last.id, last.exercises[1].id, LoadUnit.PLATES))
                r.execute(Action.SaveSet(last.id, last.exercises[1].id, "last-row", null, 12, null, false,
                    lastWeek + 120_000, LoadUnit.PLATES, 7))
                r.execute(Action.FinishWorkout(lastWeek + 180_000))
                r.execute(Action.StartWorkout("intro-upper", FIXED_NOW))
            }
        }
        val saves = CopyOnWriteArrayList<Action.SaveSet>()
        val repo = object : AppRepository by real {
            override suspend fun execute(action: Action) {
                if (action is Action.SaveSet) saves += action
                real.execute(action)
            }
        }
        val today = requireNotNull(real.state.value.activeWorkout)
        val press = today.exercises[0]
        val row = today.exercises[1]
        assertEquals("Machine chest press", press.exercise.name)
        assertFalse(press.exercise.supportsPlates)
        assertEquals("Seated cable row", row.exercise.name)
        assertEquals(LoadUnit.PLATES, row.entryLoadUnit)

        var keyboard: SoftwareKeyboardController? = null
        val ui = MainThreadCollection(repo)
        rule.setContent {
            keyboard = LocalSoftwareKeyboardController.current
            RebuildApp(ui, FakePlatform(), clock = { FIXED_NOW })
        }
        rule.onNodeWithText("Resume workout").performClick()
        fun editable(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        fun persisted() = SnapshotCodec.decode(persistence.json).workouts.single { it.id == today.id }.exercises
        fun select(index: Int) {
            // Typing hides the rail with the keyboard; close it the way a user would before switching.
            rule.runOnIdle { keyboard?.hide() }
            rule.waitUntil(5000) { rule.onAllNodesWithTag("exercise-chip-$index").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("exercise-chip-$index").performClick()
            rule.waitForIdle()
        }

        rule.onNodeWithTag("exercise-chip-0").assertIsSelected()
        rule.onNode(hasText("Machine chest press") and isHeading()).assertExists()
        rule.onNodeWithText("External load · kg total").assertExists()
        rule.onNodeWithTag("entry-plates").assertDoesNotExist()
        assertEquals("40", editable("entry-kg"))

        select(1)
        rule.onNodeWithTag("exercise-chip-1").assertIsSelected()
        rule.onNodeWithTag("exercise-chip-0").assertIsNotSelected()
        rule.onNode(hasText("Seated cable row") and isHeading()).assertExists()
        rule.onNode(hasText("Machine chest press") and isHeading()).assertDoesNotExist()
        rule.onNodeWithText("External load · plates per stack").assertExists()
        rule.onNodeWithContentDescription("Previous set 1: 7 plates/stack × 12", substring = true).assertExists()
        rule.onNodeWithTag("entry-kg").assertDoesNotExist()
        assertEquals("7", editable("entry-plates"))
        assertEquals("12", editable("entry-reps"))
        rule.onNodeWithTag("entry-reps").performTextReplacement("11")
        rule.onNodeWithTag("log-set").performScrollTo().performClick()
        rule.waitUntil(5000) { persisted()[1].sets.size == 1 }
        rule.waitForIdle()
        saves.single().let {
            assertEquals(today.id, it.workoutId)
            assertEquals(row.id, it.exerciseId)
            assertEquals(LoadUnit.PLATES, it.loadUnit)
            assertEquals(7, it.plates)
            assertNull(it.kg)
            assertEquals(11, it.reps)
        }
        persisted().let { exercises ->
            assertTrue(exercises[0].sets.isEmpty())
            val set = exercises[1].sets.single()
            assertEquals(saves.single().setId, set.id)
            assertEquals(LoadUnit.PLATES, set.loadUnit)
            assertEquals(7, set.plates)
            assertNull(set.kg)
        }

        select(0)
        rule.onNodeWithTag("exercise-chip-0").assertIsSelected()
        rule.onNodeWithTag("exercise-chip-1").assertIsNotSelected()
        rule.onNode(hasText("Machine chest press") and isHeading()).assertExists()
        rule.onNode(hasText("Seated cable row") and isHeading()).assertDoesNotExist()
        rule.onNodeWithText("External load · kg total").assertExists()
        rule.onNodeWithContentDescription("Previous set 1: 40 kg × 10", substring = true).assertExists()
        rule.onNodeWithTag("entry-plates").assertDoesNotExist()
        assertEquals("40", editable("entry-kg"))
        assertEquals("10", editable("entry-reps"))
        rule.onNodeWithTag("entry-reps").performTextReplacement("9")
        rule.onNodeWithTag("log-set").performScrollTo().performClick()
        rule.waitUntil(5000) { persisted()[0].sets.size == 1 }
        rule.waitForIdle()
        assertEquals(2, saves.size)
        saves.last().let {
            assertEquals(press.id, it.exerciseId)
            assertEquals(LoadUnit.KG, it.loadUnit)
            assertEquals(40.0, it.kg)
            assertNull(it.plates)
            assertEquals(9, it.reps)
        }
        persisted().let { exercises ->
            assertEquals(saves.last().setId, exercises[0].sets.single().id)
            assertEquals(LoadUnit.KG, exercises[0].sets.single().loadUnit)
            assertEquals(listOf(saves.first().setId), exercises[1].sets.map { it.id })
        }
    }

    @Test fun pastedFractionalPlateCountIsNotSilentlyChangedToWholeNumber() {
        val repo = libraryRepository(activeCable = true)
        launch(repo)
        rule.onNodeWithText("Resume workout").performClick()
        switch(LoadUnit.PLATES)
        rule.onNodeWithTag("entry-plates").performScrollTo().performTextReplacement("5.5")
        rule.onNodeWithTag("entry-reps").performTextReplacement("12")
        rule.onNodeWithTag("log-set").performScrollTo().performClick()
        rule.onNodeWithTag("entry-plates").assertTextContains("5.5")
        rule.onNodeWithText("Enter whole plates per stack", substring = true).assertExists()
        assertTrue(repo.state.value.activeWorkout!!.exercises.first().sets.isEmpty())
    }

    @Test fun previousPlateGridPrefillAndChartsStaySeparateFromKg() {
        val repo = libraryRepository(activeCable = true)
        val old = repo.state.value.activeWorkout!!
        val exercise = old.exercises.first()
        runBlocking {
            repo.execute(Action.SaveSet(old.id, exercise.id, "kg", 40.0, 8, null, false, FIXED_NOW))
            repo.execute(Action.SaveSet(old.id, exercise.id, "plates", null, 12, null, false,
                FIXED_NOW, LoadUnit.PLATES, 5))
            repo.execute(Action.FinishWorkout(FIXED_NOW + 1))
            repo.execute(Action.StartWorkout("intro-upper", FIXED_NOW + 2))
            val next = repo.state.value.activeWorkout!!.exercises.first()
            repo.execute(Action.EditToday(next.id, "row-cable", 2, 180))
        }
        launch(repo)
        rule.onNodeWithText("Resume workout").performClick()
        rule.onNodeWithTag("entry-kg").performScrollTo().assertTextContains("40")
        switch(LoadUnit.PLATES)
        rule.onNodeWithContentDescription("Previous set 1: 5 plates/stack × 12. RIR not recorded.").assertExists()
        rule.onNodeWithTag("previous-date").assertTextContains(formatDate(FIXED_NOW))
        rule.onNodeWithTag("entry-plates").performScrollTo().assertTextContains("5")
        rule.onNodeWithTag("entry-reps").assertTextContains("12")
        assertTrue(repo.state.value.activeWorkout!!.exercises.first().sets.isEmpty())
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("History").performClick()
        rule.onNodeWithText("Upper", substring = false).performClick()
        rule.onAllNodesWithContentDescription("Seated cable row, ", substring = true)[0].performScrollTo().performClick()
        rule.onNodeWithText("Weight · kg").assertExists()
        rule.onNodeWithTag("unit-PLATES").performClick()
        rule.onNodeWithText("Load · plates per stack").assertExists()
        rule.onNodeWithText("Weight · kg").assertDoesNotExist()
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Test fun largeFontLibraryPickerSearchWithImeSelectsNewCommonExercise() {
        rule.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        val repo = libraryRepository(activeCable = true)
        val originalDumbbellIds = setOf("incline-db", "shoulder-db", "reverse-fly-db", "curl-db",
            "split-squat", "bench-db")
        val chosen = Seed.exercises.first { it.perDumbbell && it.id !in originalDumbbellIds }
        var ime = 0
        val ui = MainThreadCollection(repo)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, 2f)) {
                val bottom = WindowInsets.ime.getBottom(LocalDensity.current)
                SideEffect { ime = bottom }
                RebuildApp(ui, FakePlatform(), clock = { FIXED_NOW })
            }
        }
        rule.onNodeWithText("Resume workout").performScrollTo().performClick()
        rule.onNodeWithTag("exercise-details").performScrollTo().performClick()
        rule.onNodeWithText("Adjust today").performScrollTo().performClick()
        rule.onNodeWithText("All exercises").performScrollTo().performClick()
        rule.onNodeWithTag("library-search").performTextReplacement(chosen.name)
        // A real keyboard must open; Back hides it, without exiting the picker or losing the query.
        rule.waitUntil(5000) { ime > 0 }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        rule.onNodeWithTag("library-results").performScrollToNode(hasTestTag("library-${chosen.id}"))
        rule.onNodeWithTag("library-${chosen.id}").performClick()
        rule.onNodeWithText("Apply").performClick()
        rule.waitUntil(5000) { repo.state.value.activeWorkout!!.exercises.first().exercise.id == chosen.id }
        rule.waitForIdle()
        assertEquals(chosen.id, repo.state.value.activeWorkout!!.exercises.first().exercise.id)
        assertTrue(repo.state.value.overrides.isEmpty())
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Test fun largeFontPlateEntryKeepsRealImeAndLogAboveRest() {
        rule.activityRule.scenario.onActivity {
            it.enableEdgeToEdge()
            it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val repo = libraryRepository(activeCable = true)
        val w = repo.state.value.activeWorkout!!
        val e = w.exercises.first()
        runBlocking {
            repo.execute(Action.SetLoadUnit(w.id, e.id, LoadUnit.PLATES))
            repo.execute(Action.SaveSet(w.id, e.id, "p", null, 12, null, false, FIXED_NOW, LoadUnit.PLATES, 5))
        }
        var ime = 0
        var target = 0
        val ui = MainThreadCollection(repo)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, 2f)) {
                val bottom = WindowInsets.ime.getBottom(LocalDensity.current)
                val end = WindowInsets.imeAnimationTarget.getBottom(LocalDensity.current)
                SideEffect { ime = bottom; target = end }
                RebuildApp(ui, FakePlatform(), clock = { FIXED_NOW })
            }
        }
        rule.onNodeWithText("Resume workout").performScrollTo().performClick()
        rule.onNodeWithTag("entry-plates").performScrollTo().performClick()
        rule.waitUntil(5000) { ime > 0 && ime == target }
        rule.waitForIdle()
        val rest = rule.onNodeWithTag("rest-bar").fetchSemanticsNode().boundsInRoot
        listOf("entry-plates", "entry-reps", "entry-rir", "log-set").forEach {
            val node = rule.onNodeWithTag(it).fetchSemanticsNode()
            assertEquals("$it clipped", node.size.height.toFloat(), node.boundsInRoot.height, 1f)
            assertTrue("$it under rest", node.boundsInRoot.bottom <= rest.top + 1f)
        }
    }
}
