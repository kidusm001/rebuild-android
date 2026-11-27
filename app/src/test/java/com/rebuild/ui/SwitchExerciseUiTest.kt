package com.rebuild.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import com.rebuild.data.MemoryPersistence
import com.rebuild.data.RebuildRepository
import com.rebuild.domain.Action
import com.rebuild.domain.Phase
import com.rebuild.domain.swapSuggestions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class SwitchExerciseUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val out = System.getenv("REBUILD_SWITCH_SHOTS")?.let(::File)
    private val now = LocalDateTime.of(2026, 10, 20, 18, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val platform = object : PlatformActions {
        override val status = MutableStateFlow(PlatformStatus())
        override fun requestNotifications() = Unit
        override fun requestExactAlarms() = Unit
        override fun openBatterySettings() = Unit
        override fun exportBackup() = Unit
        override fun shareBackup() = Unit
        override fun chooseRestore() = Unit
        override val restAlert = MutableStateFlow(RestAlert())
        override fun setRestAlert(alert: RestAlert) { restAlert.value = alert }
        override val tips = MutableStateFlow(TipsState(enabled = false))
        override fun dismissTip(tip: Tip) = Unit
        override fun setTipsEnabled(enabled: Boolean) = Unit
        override fun resetTips() = Unit
        override fun previewRestAlert() = Unit
        override fun stopRestAlarm() = Unit
        override val alarmRingingSince = MutableStateFlow<Long?>(null)
        override fun announceRestEnd(timerId: String, shortPause: Boolean) = Unit
    }

    private fun startPush(withLoggedSetOnFirst: Boolean = false): RebuildRepository = runBlocking {
        val repo = RebuildRepository.fromPersistence(MemoryPersistence()) { now }
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        repo.execute(Action.StartWorkout("push", now - 60_000L))
        if (withLoggedSetOnFirst) {
            val w = requireNotNull(repo.state.value.activeWorkout)
            val first = w.exercises.first()
            repo.execute(Action.SaveSet(w.id, first.id, "s1", 30.0, 10, 2.0, false, now - 30_000L))
        }
        repo
    }

    @Test fun holdMenuSwitchesExerciseForTodayWithoutChangingPlan() {
        val repo = startPush()
        compose.setContent { RebuildApp(repo, platform, clock = { now }) }
        tap("Resume workout")
        compose.onNodeWithTag("exercise-picker").performScrollToNode(hasTestTag("exercise-chip-2"))
        compose.onNodeWithTag("exercise-chip-2").performTouchInput { longClick() }
        compose.onNodeWithTag("exercise-hold-menu").assertIsDisplayed()
        compose.onNodeWithTag("hold-switch").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("switch-exercise-sheet").assertIsDisplayed()
        compose.onNodeWithText("Same movement").assertIsDisplayed()
        compose.onNodeWithText("More for Shoulders").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("switch-option-lateral-db").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("lateral-db", repo.state.value.activeWorkout!!.exercises[2].exercise.id)
        assertTrue(repo.state.value.overrides.isEmpty())
    }

    @Test fun entryOptionsSwitchCanAlsoSaveToFuturePlanWithoutLeakingTodayTargets() {
        val repo = startPush()
        // Lower sets and rest for today only first; swapping with "Also use in future" must keep the plan's 3 sets / 90s.
        runBlocking {
            val lateral = repo.state.value.activeWorkout!!.exercises[2]
            repo.execute(Action.EditToday(lateral.id, lateral.exercise.id, sets = 2, restSeconds = 60, saveToPlan = false))
        }
        compose.setContent { RebuildApp(repo, platform, clock = { now }) }
        tap("Resume workout")
        compose.onNodeWithTag("exercise-picker").performScrollToNode(hasTestTag("exercise-chip-2"))
        compose.onNodeWithTag("exercise-chip-2").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("exercise-details").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("switch-exercise").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("switch-save-to-plan").performScrollTo().performClick()
        compose.onNodeWithTag("switch-option-lateral-db").performScrollTo().performClick()
        compose.waitForIdle()
        val updatedToday = repo.state.value.activeWorkout!!.exercises[2]
        assertEquals("lateral-db", updatedToday.exercise.id)
        assertEquals(2, updatedToday.targetSets)
        assertEquals(60, updatedToday.restSeconds)
        val savedOverride = repo.state.value.overrides.single()
        assertEquals("lateral-db", savedOverride.exerciseId)
        assertEquals(3, savedOverride.sets)
        assertEquals(90, savedOverride.restSeconds)
    }

    @Test fun loggedSetsLockHoldMenuAndOfferAddingSameMuscleInsteadInSheet() {
        val repo = startPush(withLoggedSetOnFirst = true)
        compose.setContent { RebuildApp(repo, platform, clock = { now }) }
        tap("Resume workout")
        // First exercise already has a logged set; resume opens exercise 0 (short of target 2).
        compose.onNodeWithTag("exercise-chip-0").performTouchInput { longClick() }
        compose.onNodeWithTag("hold-switch").assertIsNotEnabled()
        compose.onNodeWithText("Sets already logged").assertIsDisplayed()
        // Dismiss menu and open Switch from EntryOptions.
        compose.onNodeWithTag("exercise-chip-0").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(0f, 300f))
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("exercise-details").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("switch-exercise").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("switch-add-instead").assertIsDisplayed()
        compose.onNodeWithText("Add chest exercise instead").assertIsDisplayed()
        compose.onNodeWithTag("switch-add-instead").performClick()
        compose.waitForIdle()
        // Library picker opens pre-filtered to Chest.
        compose.onNodeWithTag("library-filter-muscle").assertIsDisplayed()
        compose.onNodeWithText("Chest").assertIsDisplayed()
    }

    @Test fun browseAllStartsFilteredToMuscleAndAdjustTodayShowsGroupedSuggestions() {
        val repo = startPush()
        compose.setContent { RebuildApp(repo, platform, clock = { now }) }
        tap("Resume workout")
        compose.onNodeWithTag("exercise-picker").performScrollToNode(hasTestTag("exercise-chip-2"))
        compose.onNodeWithTag("exercise-chip-2").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("exercise-details").performScrollTo().performClick()
        compose.waitForIdle()
        // Adjust today uses the same grouped suggestions.
        compose.onNodeWithText("Adjust today").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Same movement").assertIsDisplayed()
        compose.onNodeWithText("Dumbbell lateral raise").assertIsDisplayed()
        tap("Cancel")
        // Switch sheet Browse all opens filtered to Shoulders.
        compose.onNodeWithTag("switch-exercise").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("switch-browse-all").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Shoulders").assertIsDisplayed()
    }

    @Test fun planSlotDialogUsesSlotLabelAndGroupedSuggestions() {
        val repo = runBlocking {
            RebuildRepository.fromPersistence(MemoryPersistence()) { now }.also {
                it.execute(Action.SelectPhase(Phase.BASELINE))
            }
        }
        compose.setContent { RebuildApp(repo, platform, clock = { now }) }
        tap("Plan")
        compose.onNodeWithTag("plan-slot-push-lateral").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Side delts").assertIsDisplayed()
        compose.onNodeWithText("Same movement").assertIsDisplayed()
        compose.onNodeWithText("Dumbbell lateral raise").performScrollTo().performClick()
        tap("Save to plan")
        assertEquals("lateral-db", repo.state.value.overrides.single().exerciseId)
    }

    @Test fun swapChoiceSectionShowsGroupedSuggestionsAndExpandsMoreForMuscle() {
        val repo = runBlocking {
            RebuildRepository.fromPersistence(MemoryPersistence()) { now }.also {
                it.execute(Action.SelectPhase(Phase.BASELINE))
            }
        }
        val selected = androidx.compose.runtime.mutableStateOf("face-pull")
        compose.setContent {
            val env = testEnv(repo)
            val groups = env.state.swapSuggestions("face-pull", gear = env.state.gear)
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                SwapChoiceSection(
                    env = env,
                    options = listOfNotNull(env.exercise("face-pull")),
                    swapGroups = groups,
                    currentId = "face-pull",
                    selectedId = selected.value,
                    onSelect = { selected.value = it },
                    swapMuscle = com.rebuild.domain.MuscleGroup.SHOULDERS
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Same movement").assertIsDisplayed()
        compose.onNodeWithText("Reverse pec deck").performScrollTo().performClick()
        assertEquals("reverse-pec-deck", selected.value)
        compose.onNodeWithTag("expand-more-muscle").performScrollTo().assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Dumbbell lateral raise").performScrollTo().assertIsDisplayed().performClick()
        assertEquals("lateral-db", selected.value)
        // Collapsing More for Shoulders still works even when an item inside it is selected.
        compose.onNodeWithTag("expand-more-muscle").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Dumbbell lateral raise").assertDoesNotExist()
    }

    @androidx.compose.runtime.Composable
    private fun testEnv(repo: RebuildRepository): AppEnv {
        val state = repo.state.collectAsState().value
        val runner = androidx.compose.runtime.remember { ActionRunner(repo) }
        return AppEnv(state, PlatformStatus(), platform, runner, { now }, { com.rebuild.domain.Monotonic(null, "t", 0L) },
            EffortDrafts(), RestAlert(), TipsState(enabled = false))
    }

    @Test fun capturePlanAndRoutineAfterLight() {
        if (out == null) return
        capturePlanAndRoutine("light")
    }

    @Test fun capturePlanAndRoutineAfterDark() {
        if (out == null) return
        RuntimeEnvironment.setQualifiers("+night")
        capturePlanAndRoutine("dark")
    }

    private fun capturePlanAndRoutine(mode: String) {
        val repo = runBlocking {
            val r = RebuildRepository.fromPersistence(MemoryPersistence()) { now }
            r.execute(Action.SelectPhase(Phase.BASELINE))
            r.execute(Action.SavePlanAddition("add-face", Phase.BASELINE, "push", "face-pull", 3, 12, 20, 75, false))
            r.execute(Action.SaveRoutine("r-1", "Upper focus", listOf(
                com.rebuild.domain.RoutineExercise("lateral-cable", 3, 12, 20, 90, false),
                com.rebuild.domain.RoutineExercise("incline-db", 3, 8, 12, 180, false)
            )))
            r
        }
        compose.setContent { RebuildApp(repo, platform, clock = { now }) }
        tap("Plan")
        compose.onNodeWithTag("plan-slot-push-lateral").performScrollTo().performClick()
        compose.waitForIdle()
        shot("$mode-plan-slot-after")
        tap("Cancel")
        compose.onNodeWithTag("plan-addition-add-face").performScrollTo()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("plan-addition-add-face").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(500)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        compose.mainClock.advanceTimeByFrame()
        shotNow("$mode-plan-addition-after")
        compose.onAllNodes(hasText("Cancel") and hasClickAction()).onFirst().performClick()
        compose.mainClock.advanceTimeBy(500)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        tap("More")
        tap("Routines")
        tap("Upper focus")
        tap("Edit routine")
        compose.mainClock.autoAdvance = false
        compose.onNode(hasClickAction() and hasText("Cable lateral raise"))
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(500)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        compose.mainClock.advanceTimeByFrame()
        shotNow("$mode-routine-targets-after")
        compose.onNodeWithTag("draft-switch-exercise").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(500)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        compose.mainClock.advanceTimeByFrame()
        shotNow("$mode-routine-switch-after")
        compose.mainClock.autoAdvance = true
    }

    @Test fun addedTodayExerciseCanBeSwitchedFromOptionsAndHoldMenu() {
        val repo = runBlocking {
            val r = RebuildRepository.fromPersistence(MemoryPersistence()) { now }
            r.execute(Action.SelectPhase(Phase.BASELINE))
            r.execute(Action.StartWorkout("push", now - 60_000L))
            val w = requireNotNull(r.state.value.activeWorkout)
            r.execute(Action.AddWorkoutExercise(w.id, "added-1", "curl-db", sets = 3))
            r
        }
        compose.setContent { RebuildApp(repo, platform, clock = { now }) }
        tap("Resume workout")
        val lastIdx = repo.state.value.activeWorkout!!.exercises.lastIndex
        compose.onNodeWithTag("exercise-picker").performScrollToNode(hasTestTag("exercise-chip-$lastIdx"))
        compose.onNodeWithTag("exercise-chip-$lastIdx").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("exercise-details").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("switch-exercise").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Applies to today only.").assertIsDisplayed()
        // Picking the current exercise in Browse all quietly closes the sheet without an error.
        compose.onNodeWithTag("switch-browse-all").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("library-results").performScrollToNode(hasTestTag("library-curl-db"))
        compose.onNodeWithTag("library-curl-db").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("switch-exercise-sheet").assertDoesNotExist()
        // Switch via EntryOptions.
        compose.onNodeWithTag("switch-exercise").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("switch-option-curl-hammer").performScrollTo().performClick()
        compose.waitForIdle()
        val swapped = repo.state.value.activeWorkout!!.exercises.last()
        assertEquals("curl-hammer", swapped.exercise.id)
        assertTrue(swapped.optional)
        assertEquals(3, swapped.targetSets)
        // Switch back via the exercise hold menu on the optional chip.
        compose.onNodeWithTag("exercise-chip-$lastIdx").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithTag("hold-switch").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("switch-option-curl-db").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("curl-db", repo.state.value.activeWorkout!!.exercises.last().exercise.id)
        // Once a set is logged, the hold menu locks switching on the optional chip.
        runBlocking {
            val w = requireNotNull(repo.state.value.activeWorkout)
            repo.execute(Action.SaveSet(w.id, "added-1", "s-added", 12.0, 12, 2.0, false, now - 10_000L))
        }
        compose.waitForIdle()
        compose.onNodeWithTag("exercise-chip-$lastIdx").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithTag("hold-switch").assertIsNotEnabled()
        compose.onNodeWithText("Sets already logged").assertIsDisplayed()
    }

    @Test fun switchingAddedTodayToUnsettledUnilateralPromptsPerLeg() {
        val repo = runBlocking {
            val r = RebuildRepository.fromPersistence(MemoryPersistence()) { now }
            r.execute(Action.SelectPhase(Phase.BASELINE))
            r.execute(Action.StartExtraWorkout(now - 60_000L))
            val w = requireNotNull(r.state.value.activeWorkout)
            r.execute(Action.AddWorkoutExercise(w.id, "added-leg", "split-squat", sets = 3))
            r
        }
        compose.setContent { RebuildApp(repo, platform, clock = { now }) }
        tap("Resume workout")
        compose.onNodeWithTag("exercise-details").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("switch-exercise").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("switch-option-lunge-db").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("per-leg-yes").performClick()
        compose.waitForIdle()
        val updated = repo.state.value.activeWorkout!!.exercises.single()
        assertEquals("lunge-db", updated.exercise.id)
        assertTrue(updated.perLeg)
    }

    @Test fun captureAddedTodayAfterLight() {
        if (out == null) return
        captureAddedToday("light")
    }

    @Test fun captureAddedTodayAfterDark() {
        if (out == null) return
        RuntimeEnvironment.setQualifiers("+night")
        captureAddedToday("dark")
    }

    private fun captureAddedToday(mode: String) {
        val repo = runBlocking {
            val r = RebuildRepository.fromPersistence(MemoryPersistence()) { now }
            r.execute(Action.SelectPhase(Phase.BASELINE))
            r.execute(Action.StartWorkout("push", now - 60_000L))
            val w = requireNotNull(r.state.value.activeWorkout)
            r.execute(Action.AddWorkoutExercise(w.id, "added-1", "curl-db", sets = 3))
            r
        }
        compose.setContent { RebuildApp(repo, platform, clock = { now }) }
        tap("Resume workout")
        val lastIdx = repo.state.value.activeWorkout!!.exercises.lastIndex
        compose.onNodeWithTag("exercise-picker").performScrollToNode(hasTestTag("exercise-chip-$lastIdx"))
        compose.onNodeWithTag("exercise-chip-$lastIdx").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("exercise-details").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("switch-exercise").performScrollTo()
        shot("$mode-added-today-options-after")
    }

    @Test fun captureSwitchScreensLight() {
        if (out == null) return
        captureMode("light")
    }

    @Test fun captureSwitchScreensDark() {
        if (out == null) return
        RuntimeEnvironment.setQualifiers("+night")
        captureMode("dark")
    }

    @Test fun captureSwitchScreensLargeText() {
        if (out == null) return
        RuntimeEnvironment.setQualifiers("+night")
        RuntimeEnvironment.setFontScale(2f)
        captureMode("large")
    }

    @Test fun captureSwitchScreensLandscape() {
        if (out == null) return
        RuntimeEnvironment.setQualifiers("w914dp-h411dp-land-night")
        captureMode("land")
    }

    private fun captureMode(mode: String) {
        val repo = startPush()
        compose.setContent { RebuildApp(repo, platform, clock = { now }) }
        tap("Resume workout")
        compose.onNodeWithTag("exercise-picker").performScrollToNode(hasTestTag("exercise-chip-2"))
        compose.onNodeWithTag("exercise-chip-2").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("exercise-details").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("switch-exercise").performScrollTo()
        shot("$mode-entry-options-after")
        compose.onNodeWithText("Adjust today").performScrollTo().performClick()
        compose.waitForIdle()
        shot("$mode-adjust-today-after")
        tap("Cancel")
        compose.onNodeWithTag("switch-exercise").performScrollTo().performClick()
        compose.waitForIdle()
        shot("$mode-switch-sheet-after")
        compose.onNodeWithTag("switch-cancel").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("exercise-chip-2").performTouchInput { longClick() }
        compose.waitForIdle()
        shot("$mode-hold-menu-after")
    }

    private fun tap(text: String) {
        compose.onAllNodes(hasText(text) and hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        shotNow(name)
    }

    private fun shotNow(name: String) {
        val root = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        windows().forEach { view ->
            val params = view.layoutParams as? WindowManager.LayoutParams
            if (params != null && params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0) {
                canvas.drawColor(android.graphics.Color.argb((params.dimAmount * 255).toInt(), 0, 0, 0))
            }
            val at = IntArray(2).also { view.getLocationOnScreen(it) }
            canvas.save()
            canvas.translate(at[0].toFloat(), at[1].toFloat())
            view.draw(canvas)
            canvas.restore()
        }
        val dir = requireNotNull(out).apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Suppress("UNCHECKED_CAST")
    private fun windows(): List<View> {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        val views = global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<View>
        return views.filter { it.isShown && it.width > 0 }
    }
}
