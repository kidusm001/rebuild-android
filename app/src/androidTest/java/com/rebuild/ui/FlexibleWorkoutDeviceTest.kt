package com.rebuild.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rebuild.data.RebuildRepository
import com.rebuild.data.SnapshotCodec
import com.rebuild.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Flexible strength workouts on the real reducer and a real snapshot store: an extra workout outside the plan,
 * exercises added to a planned workout, skipping, session-only order, optional goals, explicit Save to plan, History
 * corrections and future-plan edits, with large text, a real keyboard and landscape. Large text is the system font
 * scale, as on the phone, so dialogs (separate windows that take their density from the system) get it too.
 */
@RunWith(AndroidJUnit4::class)
class FlexibleWorkoutDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var store: InMemorySnapshots
    private var keyboard: SoftwareKeyboardController? = null
    @Volatile private var imeBottom = 0
    @Volatile private var imeTarget = 0
    private var scaledFont = false

    @Before
    fun productionWindow() {
        rule.activityRule.scenario.onActivity {
            it.enableEdgeToEdge()
            it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    @After
    fun restoreDevice() {
        rule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        if (scaledFont) {
            shell("settings put system font_scale 1.0")
            rule.waitUntil(10_000) { rule.activity.resources.configuration.fontScale == 1f }
        }
    }

    private fun shell(command: String) {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
    }

    /** The system font scale; before [launch], since the change recreates the activity. */
    private fun systemFontScale(scale: Float) {
        scaledFont = true
        shell("settings put system font_scale $scale")
        rule.waitUntil(10_000) { rule.activity.resources.configuration.fontScale == scale }
        productionWindow()
    }

    /** The seeded intro plan (Upper on Tuesday 22 Sep) in a real repository writing to [store]. */
    private fun repository(setup: suspend RebuildRepository.() -> Unit = {}): RebuildRepository {
        store = InMemorySnapshots(Seed.initialState().copy(phase = Phase.INTRO_TWO))
        return introRepository(store, setup)
    }

    @OptIn(ExperimentalLayoutApi::class)
    private fun launch(repo: AppRepository) {
        val ui = MainThreadCollection(repo)
        rule.setContent {
            keyboard = LocalSoftwareKeyboardController.current
            val bottom = WindowInsets.ime.getBottom(LocalDensity.current)
            val target = WindowInsets.imeAnimationTarget.getBottom(LocalDensity.current)
            SideEffect { imeBottom = bottom; imeTarget = target }
            RebuildApp(ui, FakePlatform(), clock = { FIXED_NOW })
        }
    }

    private fun landscape() {
        rule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        rule.waitUntil(5_000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        productionWindow()
    }

    private fun active(repo: AppRepository) = requireNotNull(repo.state.value.activeWorkout)

    private fun assertSaved(repo: AppRepository) =
        assertEquals("The saved snapshot must be exactly what the app shows", SnapshotCodec.decode(store.json), repo.state.value)

    private fun editable(tag: String) =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun hideKeyboard() {
        rule.runOnIdle {
            keyboard?.hide()
            val window = rule.activity.window
            WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
        }
        rule.waitUntil(5_000) { imeBottom == 0 }
        rule.waitForIdle()
    }

    /** The keyboard is up and has stopped moving. */
    private fun waitForSettledIme() {
        var last = -1
        var since = 0L
        rule.waitUntil(8_000) {
            val now = SystemClock.uptimeMillis()
            val current = imeBottom
            if (current != last) {
                last = current
                since = now
            }
            current > 0 && now - since >= 600
        }
        rule.waitForIdle()
    }

    /** A rail chip, scrolled into the lazy rail first. */
    private fun chip(index: Int): SemanticsNodeInteraction {
        rule.onNodeWithTag("exercise-picker").performScrollToNode(hasTestTag("exercise-chip-$index"))
        return rule.onNodeWithTag("exercise-chip-$index")
    }

    /** Searches the shared library picker and chooses [id]. */
    private fun pick(query: String, id: String) {
        rule.onNodeWithTag("library-search").performTextReplacement(query)
        rule.onNodeWithTag("library-results").performScrollToNode(hasTestTag("library-$id"))
        rule.onNodeWithTag("library-$id").performClick()
        rule.waitForIdle()
        // A rep-based exercise added to a live workout offers working sets; these flows add it without a goal.
        if (rule.onAllNodesWithTag("add-sets-none").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithTag("add-sets-none").performClick()
            rule.waitForIdle()
        }
    }

    private fun menu(tag: String) {
        rule.onNodeWithContentDescription("More workout actions").performClick()
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun details() {
        rule.onNodeWithTag("exercise-details").performScrollTo().performClick()
        rule.waitForIdle()
    }

    private fun waitEnabled(tag: String) = rule.waitUntil(5_000) {
        rule.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
    }

    private fun addFacePull(repo: AppRepository, expectedCount: Int): WorkoutExercise {
        menu("menu-add-exercise")
        pick("face pull", "face-pull")
        rule.waitUntil(5_000) { active(repo).exercises.size == expectedCount }
        rule.waitForIdle()
        return active(repo).exercises.last()
    }

    private fun logSet(repo: AppRepository, entryId: String, kg: String, reps: String) {
        val before = active(repo).exercises.single { it.id == entryId }.sets.size
        rule.onNodeWithTag("entry-kg").performScrollTo().performTextReplacement(kg)
        rule.onNodeWithTag("entry-reps").performScrollTo().performTextReplacement(reps)
        rule.onNodeWithTag("log-set").performScrollTo().performClick()
        rule.waitUntil(5_000) { active(repo).exercises.single { it.id == entryId }.sets.size == before + 1 }
        // The app keeps its double-tap guard briefly after a logged set; taps before it lifts are ignored.
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasTestTag("log-set")).fetchSemanticsNodes().isEmpty() ||
                rule.onAllNodes(hasTestTag("log-set") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
    }

    /** A dialog is its own window: wait (bounded) for its first frame, then require [text] displayed inside it. */
    private fun assertDialogShows(text: String): SemanticsNodeInteraction {
        val inDialog = hasText(text) and hasAnyAncestor(isDialog())
        rule.waitUntil(5_000) {
            rule.onAllNodes(inDialog).fetchSemanticsNodes().size == 1 && rule.onNode(inDialog).isDisplayed()
        }
        return rule.onNode(inDialog).assertIsDisplayed()
    }

    /** The workout's own Finish, once enabled: a tap while the app's busy guard is closed would be ignored. */
    private fun clickFinish() {
        val finish = hasText("Finish") and hasClickAction() and !hasAnyAncestor(isDialog())
        rule.waitUntil(5_000) { rule.onAllNodes(finish and isEnabled()).fetchSemanticsNodes().size == 1 }
        rule.onNode(finish).performClick()
        rule.waitForIdle()
    }

    /** Fully visible, after scrolling if it scrolls (never clipped by its container), with a 48 dp target. */
    private fun assertWhole(tag: String) {
        if (rule.onAllNodes(hasTestTag(tag) and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithTag(tag).performScrollTo()
        }
        val node = rule.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
        assertEquals("$tag must be fully visible: size=${node.size}, bounds=${node.boundsInRoot}",
            node.size.height.toFloat(), node.boundsInRoot.height, 1f)
        assertEquals("$tag must not be cut off sideways: size=${node.size}, bounds=${node.boundsInRoot}",
            node.size.width.toFloat(), node.boundsInRoot.width, 1f)
        val minimum = 48 * rule.activity.resources.displayMetrics.density
        assertTrue("$tag needs a 48 dp target: ${node.touchBoundsInRoot} vs $minimum",
            node.touchBoundsInRoot.height >= minimum - 1 && node.touchBoundsInRoot.width >= minimum - 1)
    }

    private fun onScreen(tag: String): Rect {
        val node = rule.onNodeWithTag(tag).fetchSemanticsNode()
        val origin = node.positionOnScreen
        return Rect(origin.x, origin.y, origin.x + node.size.width, origin.y + node.size.height)
    }

    /** Where [tag] is laid out in the root, even while a scrolling container clips it from view. */
    private fun layoutBounds(tag: String): Rect {
        val node = rule.onNodeWithTag(tag).fetchSemanticsNode()
        val origin = node.positionInRoot
        return Rect(origin.x, origin.y, origin.x + node.size.width, origin.y + node.size.height)
    }

    /** The keyboard's top edge in screen coordinates, from the activity window's measured IME inset. */
    private fun imeTopOnScreen(): Float {
        var windowBottom = 0
        rule.runOnUiThread {
            val decor = rule.activity.window.decorView
            val location = IntArray(2).also(decor::getLocationOnScreen)
            windowBottom = location[1] + decor.height
        }
        return (windowBottom - imeBottom).toFloat()
    }

    @Test
    fun extraWorkoutLogsWithoutTargetsShowsInHistoryAndNeverCountsForTheWeek() {
        val repo = repository()
        launch(repo)
        rule.onNodeWithTag("start-empty").performScrollTo().assertTextContains("Start empty workout").performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkoutId != null }
        rule.waitForIdle()
        active(repo).let {
            assertNull("An extra workout belongs to no routine", it.templateId)
            assertEquals(EXTRA_WORKOUT_NAME, it.name)
            assertTrue(it.exercises.isEmpty())
            assertEquals(FIXED_NOW, it.startedAt)
        }
        rule.onNodeWithText("Add your first exercise").assertIsDisplayed()
        rule.onNodeWithText("Outside your plan · started", substring = true).assertExists()
        rule.onNodeWithTag("exercise-picker").assertDoesNotExist()
        rule.onNodeWithTag("add-first-exercise").performClick()
        pick("face pull", "face-pull")
        rule.waitUntil(5_000) { active(repo).exercises.size == 1 }
        rule.waitForIdle()

        val entry = active(repo).exercises.single()
        assertEquals("face-pull", entry.exercise.id)
        assertTrue(entry.optional)
        assertNull(entry.slotId)
        assertNull("No target is invented", entry.targetSets)
        assertNull(entry.minReps)
        assertEquals("No history or plan for it: the visible 90-second default", DEFAULT_REST_SECONDS, entry.restSeconds)
        assertFalse(entry.perLeg)
        assertNull("Adding an exercise starts no timer", repo.state.value.restTimer)
        rule.onNodeWithContentDescription("exercise 1 of 1", substring = true).assertExists()
        rule.onNode(hasText("Cable face pull") and isHeading()).assertExists()
        rule.onNodeWithTag("target-line").assertTextEquals("No target · log what you do")
        rule.onNodeWithTag("exercise-details").assertTextContains("Goal, rest and options · rest 1:30")
        rule.onNodeWithTag("exercise-chip-0")
            .assert(hasContentDescription("Exercise 1 of 1: Cable face pull, 0 working sets, optional"))

        logSet(repo, entry.id, "15", "15")
        active(repo).exercises.single().sets.single().let {
            assertEquals(15.0, it.kg)
            assertEquals(15, it.reps)
            assertEquals(LoadUnit.KG, it.loadUnit)
            assertFalse(it.warmup)
            assertEquals(FIXED_NOW, it.completedAt)
        }
        requireNotNull(repo.state.value.restTimer).let {
            assertEquals(entry.id, it.exerciseId)
            assertEquals("Rest starts from the entry's own rest", FIXED_NOW + 90_000, it.deadline)
            assertFalse(it.shortPause)
        }
        assertSaved(repo)
        hideKeyboard()
        rule.onNodeWithTag("exercise-chip-0")
            .assert(hasContentDescription("Exercise 1 of 1: Cable face pull, 1 working set, optional"))
        // Without a target there is always a next set to log.
        rule.onNodeWithTag("log-set").performScrollTo().assertIsDisplayed()

        clickFinish()
        assertDialogShows("Finish extra workout")
        rule.onNodeWithText("Save 1 working set across 1 exercise?", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("count-for-week").assertDoesNotExist()
        rule.onNodeWithTag("finish-discard").assertDoesNotExist()
        rule.onNodeWithTag("confirm-button").assertIsEnabled().performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkoutId == null }
        rule.onNodeWithTag("receipt-done").performClick()
        rule.waitForIdle()

        val finished = repo.state.value.workouts.single()
        assertEquals(FIXED_NOW, finished.finishedAt)
        assertFalse(finished.countedForWeek)
        val week = requireNotNull(repo.state.value.weekChecklist(localDateOf(FIXED_NOW)))
        assertEquals(0, week.done)
        assertEquals(listOf(finished.id), week.extras.map { it.id })
        assertTrue(week.routines.all { it.status == RoutineStatus.NOT_DONE })
        assertNull(repo.state.value.restTimer)
        assertSaved(repo)
        rule.onNodeWithTag("week-progress").performScrollTo().assertTextContains("0 of 4")
        rule.onNodeWithTag("week-extras").performScrollTo()
            .assertTextContains("Outside the plan: 1 extra workout (Tue 22 Sep) · not counted")
        rule.onNode(hasText("Not done") and hasAnyAncestor(hasTestTag("week-row-intro-upper"))).assertExists()

        rule.onNodeWithText("History").performClick()
        rule.onNodeWithText("Outside the plan · 1 working set").assertExists()
        rule.onNodeWithText(EXTRA_WORKOUT_NAME).performClick()
        rule.onNodeWithText("Outside the plan").assertExists()
        rule.onNodeWithTag("week-credit-toggle").assertDoesNotExist()
        rule.onNodeWithContentDescription(", No target", substring = true).assertExists()
        rule.onNodeWithContentDescription("Cable face pull, ", substring = true).performScrollTo().performClick()
        rule.onNode(hasText("Latest · ${formatDate(FIXED_NOW)}") and hasText("15 kg × 15")).assertExists()
    }

    @Test
    fun emptyExtraWorkoutCannotFinishButCanBeDiscarded() {
        val repo = repository()
        launch(repo)
        rule.onNodeWithTag("start-empty").performScrollTo().performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkoutId != null }
        rule.waitForIdle()
        clickFinish()
        assertDialogShows("Finish extra workout")
        rule.onNodeWithTag("finish-needs-set").assertIsDisplayed()
        rule.onNodeWithTag("confirm-button").assertIsNotEnabled()
        rule.onNodeWithTag("count-for-week").assertDoesNotExist()
        rule.onNodeWithTag("finish-discard").performScrollTo().performClick()
        assertDialogShows("Discard workout?")
        rule.onNodeWithTag("confirm-button").performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkoutId == null }
        rule.waitForIdle()
        assertTrue("Nothing is kept from an empty extra workout", repo.state.value.workouts.isEmpty())
        rule.onNodeWithTag("start-empty").performScrollTo().assertIsDisplayed()
        assertSaved(repo)
    }

    @Test
    fun warmUpOnlyExtraWorkoutFinishesAndStaysInHistory() {
        val repo = repository()
        launch(repo)
        rule.onNodeWithTag("start-empty").performScrollTo().performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkoutId != null }
        rule.waitForIdle()
        rule.onNodeWithTag("add-first-exercise").performClick()
        pick("face pull", "face-pull")
        rule.waitUntil(5_000) { active(repo).exercises.size == 1 }
        rule.waitForIdle()
        val entry = active(repo).exercises.single()
        rule.onNodeWithTag("entry-warmup").performScrollTo().performClick()
        rule.onNodeWithTag("log-set").assertTextContains("Log warm-up")
        logSet(repo, entry.id, "10", "15")
        active(repo).exercises.single().sets.single().let {
            assertTrue(it.warmup)
            assertEquals(10.0, it.kg)
            assertEquals(15, it.reps)
        }
        assertNull("Warm-ups never start the rest", repo.state.value.restTimer)
        assertSaved(repo)
        hideKeyboard()

        // Real warm-ups are enough to end the session: nothing has to be discarded or relabeled.
        clickFinish()
        assertDialogShows("Finish extra workout")
        rule.onNodeWithTag("finish-warmups-only").assertTextEquals("Save 1 warm-up set across 1 exercise? No working sets yet.")
        rule.onNodeWithTag("finish-needs-set").assertDoesNotExist()
        rule.onNodeWithTag("count-for-week").assertDoesNotExist()
        rule.onNodeWithTag("finish-discard").assertDoesNotExist()
        rule.onNodeWithTag("confirm-button").assertIsEnabled().performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkoutId == null }
        rule.onNodeWithTag("receipt-done").performClick()
        rule.waitForIdle()

        val finished = repo.state.value.workouts.single()
        assertEquals(FIXED_NOW, finished.finishedAt)
        finished.exercises.single().let {
            assertEquals(entry.id, it.id)
            assertTrue(it.optional)
            assertNull("No target is invented", it.targetSets)
            assertNull(it.minReps)
            assertNull(it.maxReps)
        }
        assertEquals(0, finished.workingSetCount())
        assertEquals(1, finished.warmupSetCount())
        assertFalse(finished.countedForWeek)
        assertNull(repo.state.value.restTimer)
        val week = requireNotNull(repo.state.value.weekChecklist(localDateOf(FIXED_NOW)))
        assertEquals(0, week.done)
        assertTrue("Today names extra workouts with working sets", week.extras.isEmpty())
        assertSaved(repo)
        rule.onNodeWithTag("week-progress").performScrollTo().assertTextContains("0 of 4")
        rule.onNodeWithTag("week-extras").assertDoesNotExist()
        rule.onNodeWithTag("start-empty").performScrollTo().assertIsDisplayed()

        rule.onNodeWithText("History").performClick()
        rule.onNodeWithText("Outside the plan · 0 working sets, 1 warm-up set").assertExists()
        rule.onNodeWithText(EXTRA_WORKOUT_NAME).performClick()
        rule.onNodeWithText("0 working sets, 1 warm-up set", substring = true).assertExists()
        val list = rule.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasTestTag("week-credit"))
        rule.onNodeWithText("Outside the plan").assertExists()
        rule.onNodeWithTag("week-credit-toggle").assertDoesNotExist()
        list.performScrollToNode(hasTestTag("working-count"))
        rule.onNodeWithTag("working-count").assertTextEquals("Working sets: 0 · 1 warm-up (not counted)")
        list.performScrollToNode(hasTestTag("set-row"))
        rule.onNodeWithTag("set-row").assert(hasContentDescription("Warm-up: 10 kg × 15", substring = true))
        assertSaved(repo)
    }

    @Test
    fun optionalOnlyWorkInAPlannedWorkoutCountsOnlyWhenChosenAtFinishOrInHistory() {
        val repo = repository { execute(Action.StartWorkout("intro-upper", FIXED_NOW)) }
        launch(repo)
        rule.onNodeWithText("Resume workout").performScrollTo().performClick()
        val added = addFacePull(repo, expectedCount = 8)
        logSet(repo, added.id, "15", "12")
        assertEquals("All of today's working sets are on the added exercise", 0, active(repo).requiredWorkingSetCount())
        assertEquals(1, active(repo).workingSetCount())
        hideKeyboard()

        clickFinish()
        assertDialogShows("Finish partial workout")
        rule.onNodeWithTag("finish-short-toggle").performClick()
        rule.onNodeWithText("Machine chest press: 0 of 2", substring = true).assertExists()
        // Real working sets, wherever they are, make counting it a choice; it is never made for the user.
        rule.onNodeWithTag("count-for-week").performScrollTo().assertIsOff().performClick()
        rule.onNodeWithTag("count-for-week").assertIsOn()
        rule.onNodeWithTag("confirm-button").performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkoutId == null }
        rule.waitForIdle()
        val finished = repo.state.value.workouts.single()
        fun status() = requireNotNull(repo.state.value.weekChecklist(localDateOf(FIXED_NOW))).statusOf("intro-upper")
        assertTrue(finished.countedForWeek)
        assertFalse("Counted by choice, not by its targets", finished.targetsMet())
        assertEquals(RoutineStatus.DONE, status())
        assertNull(repo.state.value.restTimer)
        assertSaved(repo)
        rule.onNodeWithTag("week-progress").performScrollTo().assertTextContains("1 of 4")
        rule.onNode(hasText("Done · Tue 22 Sep") and hasAnyAncestor(hasTestTag("week-row-intro-upper"))).assertExists()

        // The choice stays the user's in History: taking it back leaves the workout partial, and it can be redone.
        rule.onNodeWithText("History").performClick()
        rule.onNodeWithText("Upper", substring = false).performClick()
        val list = rule.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasTestTag("week-credit-toggle"))
        rule.onNodeWithTag("week-credit-toggle").assertIsOn().performClick()
        rule.waitUntil(5_000) { !repo.state.value.workouts.single().countedForWeek }
        rule.waitForIdle()
        assertEquals(RoutineStatus.PARTIAL, status())
        assertEquals("Credit never adds or changes sets", finished.exercises, repo.state.value.workouts.single().exercises)
        rule.onNodeWithTag("week-credit-toggle").assertIsOff()
        waitEnabled("week-credit-toggle")
        rule.onNodeWithTag("week-credit-toggle").performClick()
        rule.waitUntil(5_000) { repo.state.value.workouts.single().countedForWeek }
        rule.waitForIdle()
        rule.onNodeWithTag("week-credit-toggle").assertIsOn()
        assertEquals(RoutineStatus.DONE, status())
        assertEquals(finished, repo.state.value.workouts.single())
        assertSaved(repo)
    }

    @Test
    fun plannedWorkoutAddsOptionalExerciseFocusesDuplicateAndReordersOnlyThisSession() {
        val repo = repository()
        launch(repo)
        rule.onNodeWithTag("start-scheduled").performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkoutId != null }
        rule.waitForIdle()
        val started = active(repo)
        assertEquals("intro-upper", started.templateId)
        assertEquals(7, started.exercises.size)

        val added = addFacePull(repo, expectedCount = 8)
        assertEquals("face-pull", added.exercise.id)
        assertTrue(added.optional)
        assertNull(added.slotId)
        assertNull(added.targetSets)
        assertEquals(DEFAULT_REST_SECONDS, added.restSeconds)
        assertNull(repo.state.value.restTimer)
        rule.onNodeWithContentDescription("exercise 8 of 8", substring = true).assertExists()
        rule.onNodeWithTag("target-line").assertTextEquals("Added today · optional, no target")
        chip(7).assert(hasContentDescription("Exercise 8 of 8: Cable face pull, 0 working sets, optional"))
        chip(0).assert(hasContentDescription("Exercise 1 of 8: Machine chest press, 0 of 2 working sets"))

        // The same exercise again opens the one already here instead of splitting its sets.
        rule.onNodeWithTag("exercise-picker").performScrollToNode(hasTestTag("add-exercise"))
        rule.onNodeWithTag("add-exercise").performClick()
        rule.onNodeWithTag("library-search").performTextReplacement("lateral")
        rule.onNodeWithTag("library-results").performScrollToNode(hasTestTag("library-lateral-cable"))
        rule.onNodeWithTag("library-lateral-cable").assert(hasContentDescription("in this workout", substring = true))
            .performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Cable lateral raise is already in this workout, so it's open now.").assertExists()
        rule.onNodeWithContentDescription("exercise 4 of 8", substring = true).assertExists()
        rule.onNode(hasText("Cable lateral raise") and isHeading()).assertExists()
        assertEquals(8, active(repo).exercises.size)

        // Appended last, then moved up twice for this session only.
        menu("menu-reorder")
        rule.onNodeWithTag("move-up-7").performScrollTo().assert(hasContentDescription("Move Cable face pull up"))
            .performClick()
        rule.waitUntil(5_000) { active(repo).exercises[6].id == added.id }
        waitEnabled("move-up-6")
        rule.onNodeWithTag("move-up-6").performScrollTo().assert(hasContentDescription("Move Cable face pull up"))
            .performClick()
        rule.waitUntil(5_000) { active(repo).exercises[5].id == added.id }
        rule.waitForIdle()
        rule.onNode(hasText("Cable face pull") and hasAnyAncestor(hasTestTag("reorder-row-5"))).assertExists()
        rule.onNode(hasText("Added today") and hasAnyAncestor(hasTestTag("reorder-row-5"))).assertExists()
        rule.onNodeWithTag("move-down-7").assertIsNotEnabled()
        rule.onNodeWithTag("reorder-done").performClick()
        rule.waitForIdle()
        val reordered = active(repo)
        assertEquals(started.exercises.map { it.id }, reordered.exercises.filterNot { it.optional }.map { it.id })
        val state = repo.state.value
        val template = state.templates.single { it.id == "intro-upper" }
        assertEquals("The plan keeps its own order", template.slots.map { it.id },
            state.plannedExercises(template, Phase.INTRO_TWO).map { it.slotId })
        assertTrue(state.planAdditions.isEmpty())
        assertTrue(state.overrides.isEmpty())
        assertTrue(state.planRemovals.isEmpty())
        assertSaved(repo)

        // The original routine completes on its own targets; the optional exercise never blocks it.
        runBlocking {
            reordered.exercises.filterNot { it.optional }.forEach { we ->
                repeat(2) { i ->
                    repo.execute(Action.SaveSet(reordered.id, we.id, "${we.id}-$i", 20.0, 10, null, false, FIXED_NOW + i))
                }
            }
        }
        rule.waitForIdle()
        hideKeyboard()
        clickFinish()
        rule.onNode(hasText("Finish workout") and hasAnyAncestor(isDialog())).assertExists()
        rule.onNodeWithText("All target working sets are logged. Finish and save this workout?").assertExists()
        rule.onNodeWithTag("count-for-week").assertDoesNotExist()
        rule.onNodeWithTag("confirm-button").performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkoutId == null }
        rule.waitForIdle()
        val finished = repo.state.value.workouts.single()
        assertFalse("A complete workout counts by itself, never by a stored credit", finished.countedForWeek)
        assertEquals(RoutineStatus.DONE,
            requireNotNull(repo.state.value.weekChecklist(localDateOf(FIXED_NOW))).statusOf("intro-upper"))
        assertEquals("The session order is kept in History", 5, finished.exercises.indexOfFirst { it.id == added.id })
        assertTrue(finished.exercises[5].optional)
        assertTrue(finished.exercises[5].sets.isEmpty())
        rule.onNodeWithTag("week-progress").performScrollTo().assertTextContains("1 of 4")
        rule.onNode(hasText("Done · Tue 22 Sep") and hasAnyAncestor(hasTestTag("week-row-intro-upper"))).assertExists()
        assertSaved(repo)
    }

    @Test
    fun skippingTodayKeepsTheTargetMovesOnAndCanBeUndone() {
        val repo = repository()
        launch(repo)
        rule.onNodeWithTag("start-scheduled").performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkoutId != null }
        rule.waitForIdle()
        val chest = active(repo).exercises.first()
        rule.onNodeWithContentDescription("exercise 1 of 7", substring = true).assertExists()
        details()
        rule.onNodeWithTag("skip-today").performScrollTo().assertTextContains("Skip today · target stays").performClick()
        rule.waitUntil(5_000) { active(repo).exercises.first().skipped }
        rule.waitForIdle()
        // Skipping moves on to the next exercise still to do.
        rule.onNodeWithContentDescription("exercise 2 of 7", substring = true).assertExists()
        rule.onNode(hasText("Seated cable row") and isHeading()).assertExists()
        chip(0).assert(hasContentDescription("Exercise 1 of 7: Machine chest press, 0 of 2 working sets, skipped today"))
        assertEquals("The target and the entry stay", chest.copy(skipped = true), active(repo).exercises.first())
        assertSaved(repo)

        chip(0).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("target-line").assertTextEquals("Skipped today · target 2 working sets · 8–12 reps")
        rule.onNodeWithTag("skipped-panel").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("entry-kg").assertDoesNotExist()
        rule.onNodeWithTag("log-set").assertDoesNotExist()
        rule.onNodeWithTag("next-exercise").assertTextContains("Next: Seated cable row")

        rule.onNodeWithTag("undo-skip").performScrollTo().performClick()
        rule.waitUntil(5_000) { !active(repo).exercises.first().skipped }
        rule.waitForIdle()
        assertEquals(chest, active(repo).exercises.first())
        rule.onNodeWithTag("target-line").assertTextEquals("Today's target: 2 working sets · 8–12 reps · 3 RIR")
        rule.onNodeWithTag("skipped-panel").assertDoesNotExist()
        rule.onNodeWithTag("entry-kg").performScrollTo().assertIsDisplayed()

        // Skipped again, it is still below target when finishing: partial, and never counted by itself.
        details()
        rule.onNodeWithTag("skip-today").performScrollTo().performClick()
        rule.waitUntil(5_000) { active(repo).exercises.first().skipped }
        rule.waitForIdle()
        clickFinish()
        assertDialogShows("Finish partial workout")
        rule.onNodeWithTag("finish-short-toggle").performClick()
        rule.onNodeWithText("Machine chest press: 0 of 2 (skipped)", substring = true).assertExists()
        rule.onNodeWithTag("count-for-week").assertDoesNotExist()
        rule.onNodeWithTag("dialog-cancel").performClick()
        rule.waitForIdle()
        assertEquals(chest.copy(skipped = true), active(repo).exercises.first())
        assertSaved(repo)
    }

    @Test
    fun removingAnAddedExerciseWithLoggedWorkNeedsExplicitDeletion() {
        val repo = repository { execute(Action.StartWorkout("intro-upper", FIXED_NOW)) }
        launch(repo)
        rule.onNodeWithText("Resume workout").performScrollTo().performClick()
        val added = addFacePull(repo, expectedCount = 8)
        logSet(repo, added.id, "15", "12")
        assertEquals(added.id, repo.state.value.restTimer?.exerciseId)
        hideKeyboard()
        details()
        rule.onNodeWithTag("remove-entry").performScrollTo().performClick()
        assertDialogShows("Remove Cable face pull?")
        rule.onNodeWithText("This also deletes what you logged for it in this workout: 1 set. It can't be undone. " +
            "Your plan doesn't change.").assertExists()
        rule.onNodeWithTag("confirm-button").assertTextEquals("Remove and delete 1 record")
        rule.onNodeWithTag("dialog-cancel").performClick()
        rule.waitForIdle()
        assertEquals(8, active(repo).exercises.size)
        assertEquals(1, active(repo).exercises.last().sets.size)

        rule.onNodeWithTag("remove-entry").performScrollTo().performClick()
        rule.onNodeWithTag("confirm-button").performClick()
        rule.waitUntil(5_000) { active(repo).exercises.size == 7 }
        rule.waitForIdle()
        assertTrue(active(repo).exercises.none { it.id == added.id })
        assertNull("Its rest goes with it", repo.state.value.restTimer)
        rule.onNodeWithContentDescription("exercise 1 of 7", substring = true).assertExists()
        rule.onNode(hasText("Machine chest press") and isHeading()).assertExists()
        assertSaved(repo)
    }

    @Test
    fun optionalGoalStaysTodayAndSaveToPlanRequiresItOnlyInFutureWorkouts() {
        val repo = repository { execute(Action.StartWorkout("intro-upper", FIXED_NOW)) }
        launch(repo)
        rule.onNodeWithText("Resume workout").performScrollTo().performClick()
        val added = addFacePull(repo, expectedCount = 8)
        details()
        rule.onNodeWithTag("edit-goal").performScrollTo().performClick()
        assertDialogShows("Goal and rest · Cable face pull")
        assertEquals("", editable("goal-sets"))
        assertEquals("90", editable("goal-rest"))
        rule.onNodeWithText("Suggested 1:30 · default", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("goal-sets").performTextReplacement("3")
        rule.onNodeWithTag("goal-min").performTextReplacement("12")
        rule.onNodeWithTag("goal-max").performTextReplacement("15")
        rule.onNodeWithTag("goal-save").performClick()
        rule.waitUntil(5_000) { active(repo).exercises.last().targetSets == 3 }
        rule.waitForIdle()
        active(repo).exercises.last().let {
            assertTrue(it.optional)
            assertNull(it.slotId)
            assertEquals(12, it.minReps)
            assertEquals(15, it.maxReps)
            assertEquals(90, it.restSeconds)
        }
        assertTrue("A goal is for today only", repo.state.value.planAdditions.isEmpty())
        rule.onNodeWithTag("target-line").assertTextEquals("Optional goal: 3 working sets · 12–15 reps")

        rule.onNodeWithTag("save-addition").performScrollTo().assertTextContains("Save to Upper plan…").performClick()
        assertDialogShows("Add to Upper")
        assertEquals("3", editable("goal-sets"))
        assertEquals("12", editable("goal-min"))
        assertEquals("15", editable("goal-max"))
        assertEquals("90", editable("goal-rest"))
        rule.onNodeWithTag("plan-addition-save").performClick()
        rule.waitUntil(5_000) { repo.state.value.planAdditions.isNotEmpty() }
        rule.waitForIdle()
        val addition = repo.state.value.planAdditions.single()
        assertEquals(PlanAddition(addition.id, Phase.INTRO_TWO, "intro-upper", "face-pull", 3, 12, 15, 90, false),
            addition)
        active(repo).exercises.last().let {
            assertEquals(added.id, it.id)
            assertTrue("Today's entry stays optional", it.optional)
            assertNull(it.slotId)
        }
        rule.onNodeWithTag("addition-in-plan").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("save-addition").assertDoesNotExist()
        assertSaved(repo)

        hideKeyboard()
        clickFinish()
        rule.onNode(hasText("Finish partial workout") and hasAnyAncestor(isDialog())).assertExists()
        rule.onNodeWithTag("confirm-button").performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkoutId == null }
        rule.waitForIdle()
        val today = repo.state.value.workouts.single()
        assertTrue("The finished workout keeps it optional", today.exercises.last().optional)
        runBlocking { repo.execute(Action.StartWorkout("intro-upper", FIXED_NOW + 60_000)) }
        val next = active(repo)
        assertEquals(8, next.exercises.size)
        next.exercises.last().let {
            assertEquals("face-pull", it.exercise.id)
            assertFalse("Future workouts require it", it.optional)
            assertEquals(addition.id, it.slotId)
            assertEquals(3, it.targetSets)
            assertEquals(12, it.minReps)
            assertEquals(15, it.maxReps)
            assertEquals(90, it.restSeconds)
            assertNull(it.baselineSets)
        }
        assertEquals(today, repo.state.value.workouts.single { it.id == today.id })
        assertSaved(repo)
    }

    @Test
    fun historyCorrectionAddsAForgottenExerciseWithoutTimersPlanChangesOrCredit() {
        val repo = repository {
            execute(Action.StartWorkout("intro-upper", FIXED_NOW - 7_200_000))
            val w = requireNotNull(state.value.activeWorkout)
            execute(Action.SaveSet(w.id, w.exercises.first().id, "chest-1", 40.0, 10, null, false, FIXED_NOW - 6_000_000))
            execute(Action.FinishWorkout(FIXED_NOW - 3_600_000))
        }
        val before = repo.state.value
        val workout = before.workouts.single()
        launch(repo)
        rule.onNodeWithText("History").performClick()
        rule.onNodeWithText("Upper", substring = false).performClick()
        val list = rule.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasTestTag("history-add-exercise"))
        rule.onNodeWithTag("history-add-exercise").performClick()
        pick("lunge", "lunge-db")
        // Nothing settles how its reps are counted, so the user says; Rebuild never converts reps.
        assertDialogShows("How do you count Dumbbell reverse lunge reps?")
        rule.onNodeWithTag("per-leg-yes").performClick()
        rule.waitUntil(5_000) { repo.state.value.workouts.single().exercises.size == 8 }
        assertDialogShows("Add a missed set")
        rule.onNodeWithTag("edit-kg").performTextReplacement("12")
        rule.onNodeWithTag("edit-reps").performTextReplacement("10")
        rule.onNode(hasText("Save") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        rule.waitUntil(5_000) { repo.state.value.workouts.single().exercises.last().sets.size == 1 }
        rule.waitForIdle()

        val corrected = repo.state.value.workouts.single()
        val lunge = corrected.exercises.last()
        assertEquals("lunge-db", lunge.exercise.id)
        assertTrue(lunge.optional)
        assertTrue("The answer stays with the entry", lunge.perLeg)
        assertNull(lunge.slotId)
        assertNull(lunge.targetSets)
        lunge.sets.single().let {
            assertEquals(12.0, it.kg)
            assertEquals(10, it.reps)
            assertFalse(it.warmup)
            assertEquals("A correction is timed at the workout's end, not now", workout.finishedAt, it.completedAt)
        }
        assertEquals(workout.exercises, corrected.exercises.dropLast(1))
        assertEquals(workout.finishedAt, corrected.finishedAt)
        assertFalse(corrected.countedForWeek)
        repo.state.value.let {
            assertNull("History corrections never start a timer", it.restTimer)
            assertNull(it.activeWorkoutId)
            assertEquals(before.templates, it.templates)
            assertEquals(before.overrides, it.overrides)
            assertEquals(before.planAdditions, it.planAdditions)
            assertEquals(before.planRemovals, it.planRemovals)
            assertEquals("An added exercise never turns a partial workout into a done one", RoutineStatus.PARTIAL,
                requireNotNull(it.weekChecklist(localDateOf(FIXED_NOW))).statusOf("intro-upper"))
        }
        assertSaved(repo)
        list.performScrollToNode(hasTestTag("week-credit-toggle"))
        rule.onNodeWithTag("week-credit-toggle").assertIsOff()

        // Picking it again points to the entry already there.
        list.performScrollToNode(hasTestTag("history-add-exercise"))
        rule.onNodeWithTag("history-add-exercise").performClick()
        pick("lunge", "lunge-db")
        list.performScrollToNode(hasTestTag("workout-notice"))
        rule.onNodeWithText("Dumbbell reverse lunge is already in this workout. Add its missed sets there.").assertExists()
        rule.onNodeWithText("How do you count", substring = true).assertDoesNotExist()
        assertEquals(8, repo.state.value.workouts.single().exercises.size)

        // Taking it back out deletes its set only after naming it.
        list.performScrollToNode(hasTestTag("history-options-${lunge.id}"))
        rule.onNodeWithTag("history-options-${lunge.id}").performClick()
        rule.onNodeWithTag("history-remove-${lunge.id}").performClick()
        assertDialogShows("Remove Dumbbell reverse lunge?")
        rule.onNodeWithTag("confirm-button").assertTextEquals("Remove and delete 1 record").performClick()
        rule.waitUntil(5_000) { repo.state.value.workouts.single().exercises.size == 7 }
        rule.waitForIdle()
        assertEquals(workout, repo.state.value.workouts.single())
        assertNull(repo.state.value.restTimer)
        assertSaved(repo)
    }

    @Test
    fun futurePlanAdditionsAndRemovalsNeverReachTheStartedWorkoutAndCanBeUndone() {
        val repo = repository { execute(Action.StartWorkout("intro-upper", FIXED_NOW)) }
        val snapshot = active(repo)
        launch(repo)
        rule.onNodeWithText("Plan", substring = false).performClick()
        rule.onNodeWithTag("plan-add-intro-upper").performScrollTo().assertTextContains("Add exercise to Upper")
            .performClick()
        pick("face pull", "face-pull")
        assertDialogShows("Add to Upper")
        assertEquals("", editable("goal-sets"))
        assertEquals("90", editable("goal-rest"))
        rule.onNodeWithText("Suggested 1:30 · default", useUnmergedTree = true).assertExists()
        // Targets are required for the plan: nothing is saved until they are entered.
        rule.onNodeWithTag("plan-addition-save").performClick()
        rule.onNodeWithText("Enter working sets from 1 to", substring = true).assertExists()
        assertTrue(repo.state.value.planAdditions.isEmpty())
        rule.onNodeWithTag("goal-sets").performTextReplacement("2")
        rule.onNodeWithTag("goal-min").performTextReplacement("12")
        rule.onNodeWithTag("goal-max").performTextReplacement("20")
        // Done on the last field saves, like the button.
        rule.onNodeWithTag("goal-rest").performScrollTo().performImeAction()
        rule.waitUntil(5_000) { repo.state.value.planAdditions.isNotEmpty() }
        rule.waitForIdle()
        val addition = repo.state.value.planAdditions.single()
        assertEquals(PlanAddition(addition.id, Phase.INTRO_TWO, "intro-upper", "face-pull", 2, 12, 20, 90, false),
            addition)
        rule.onNodeWithTag("plan-addition-${addition.id}").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("8 exercises · 16 working sets · in progress").assertExists()

        rule.onNodeWithTag("plan-slot-intro-chest").performScrollTo().performClick()
        rule.onNodeWithTag("plan-remove-slot").performScrollTo().performClick()
        assertDialogShows("Remove Machine chest press?")
        rule.onNodeWithTag("confirm-button").performClick()
        rule.waitUntil(5_000) { repo.state.value.planRemovals.isNotEmpty() }
        rule.waitForIdle()
        assertEquals(listOf(PlanRemoval(Phase.INTRO_TWO, "intro-chest")), repo.state.value.planRemovals)
        rule.onNodeWithTag("plan-slot-intro-chest").assertDoesNotExist()
        val template = repo.state.value.templates.single { it.id == "intro-upper" }
        assertEquals(template.slots.drop(1).map { it.id } + addition.id,
            repo.state.value.plannedExercises(template, Phase.INTRO_TWO).map { it.slotId })
        assertEquals("A started workout keeps what it started with", snapshot, active(repo))

        rule.onNodeWithTag("plan-restore-intro-chest").performScrollTo()
            .assert(hasContentDescription("Restore Machine chest press")).performClick()
        rule.waitUntil(5_000) { repo.state.value.planRemovals.isEmpty() }
        rule.waitForIdle()
        rule.onNodeWithTag("plan-removed-intro-chest").assertDoesNotExist()
        rule.onNodeWithTag("plan-slot-intro-chest").performScrollTo().assertIsDisplayed()
        assertEquals(template.slots.map { it.id } + addition.id,
            repo.state.value.plannedExercises(template, Phase.INTRO_TWO).map { it.slotId })
        assertEquals(snapshot, active(repo))
        assertSaved(repo)
    }

    @Test
    fun largeTextEmptyWorkoutAndGoalDialogWithKeyboardStayReachable() {
        systemFontScale(2f)
        val repo = repository()
        launch(repo)
        rule.onNodeWithTag("start-empty").performScrollTo().performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkoutId != null }
        rule.waitForIdle()
        assertWhole("add-first-exercise")
        rule.onNodeWithTag("add-first-exercise").performClick()
        pick("face pull", "face-pull")
        rule.waitUntil(5_000) { active(repo).exercises.size == 1 }
        hideKeyboard()
        details()
        rule.onNodeWithTag("edit-goal").performScrollTo().performClick()
        rule.onNodeWithText("Goal and rest · Cable face pull").assertExists()
        // Large text stacks the rep range so each field keeps its whole label. Layout bounds, not clipped ones: on a
        // shorter window the high end starts below the dialog's scrolling viewport until it is scrolled to.
        val low = layoutBounds("goal-min")
        val high = layoutBounds("goal-max")
        assertTrue("Rep range fields stack with large text: $low $high", low.bottom <= high.top + 1)
        assertEquals("Stacked rep fields share the full width: $low $high", low.width, high.width, 1f)
        assertWhole("goal-min")
        assertWhole("goal-max")
        // Without the keyboard the dialog's actions are whole, outside the scrolling body.
        assertWhole("goal-save")

        // Like every dialog, the window pans for the keyboard: the field being typed stays whole above it, and
        // Done on this last field saves, so the keyboard alone finishes the dialog.
        rule.onNodeWithTag("goal-rest").performScrollTo().performClick()
        waitForSettledIme()
        val imeTop = imeTopOnScreen()
        val rest = onScreen("goal-rest")
        Log.i("RebuildFlexible", "goal dialog with IME: imeTop=$imeTop ime=$imeBottom target=$imeTarget rest=$rest")
        assertTrue("The field being typed in stays above the keyboard: $rest vs $imeTop", rest.bottom <= imeTop + 1)
        assertTrue("The field being typed in stays on screen: $rest", rest.top >= 0)
        rule.onNodeWithTag("goal-rest").performTextReplacement("120")
        rule.onNodeWithTag("goal-rest").performImeAction()
        rule.waitUntil(5_000) { active(repo).exercises.single().restSeconds == 120 }
        assertNull(active(repo).exercises.single().targetSets)
        assertSaved(repo)
    }

    @Test
    fun largeTextSkippedPanelAndReorderDialogKeepEveryActionReachable() {
        systemFontScale(2f)
        val repo = repository {
            execute(Action.StartWorkout("intro-upper", FIXED_NOW))
            val w = requireNotNull(state.value.activeWorkout)
            execute(Action.AddWorkoutExercise(w.id, "added-face-pull", "face-pull"))
            execute(Action.SetSkipped(w.id, w.exercises.first().id, true))
        }
        launch(repo)
        rule.onNodeWithText("Resume workout").performScrollTo().performClick()
        // Resume passes over the skipped exercise.
        rule.onNodeWithContentDescription("exercise 2 of 8", substring = true).assertExists()
        chip(0).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("skipped-panel").performScrollTo()
        assertWhole("next-exercise")
        assertWhole("undo-skip")
        val next = rule.onNodeWithTag("next-exercise").fetchSemanticsNode().boundsInRoot
        val undo = rule.onNodeWithTag("undo-skip").fetchSemanticsNode().boundsInRoot
        assertTrue("Stacked with large text, never overlapping: $next $undo", next.bottom <= undo.top + 1)

        menu("menu-reorder")
        assertWhole("move-up-7")
        assertWhole("reorder-done")
        rule.onNodeWithTag("move-up-7").performClick()
        rule.waitUntil(5_000) { active(repo).exercises[6].id == "added-face-pull" }
        rule.waitForIdle()
        assertWhole("reorder-done")
        rule.onNodeWithTag("reorder-done").performClick()
        assertSaved(repo)
    }

    @Test
    fun landscapeEmptyWorkoutKeepsItsAddActionWhole() {
        val repo = repository { execute(Action.StartExtraWorkout(FIXED_NOW)) }
        landscape()
        launch(repo)
        rule.onNodeWithText("Resume workout").performScrollTo().performClick()
        rule.onNodeWithText("Add your first exercise").performScrollTo().assertIsDisplayed()
        assertWhole("add-first-exercise")
        rule.onNodeWithTag("add-first-exercise").performClick()
        rule.onNodeWithTag("library-search").assertExists()
    }

    @Test
    fun landscapeGoalDialogKeepsTheFieldBeingTypedVisibleAndDoneSaves() {
        val repo = repository {
            execute(Action.StartExtraWorkout(FIXED_NOW))
            execute(Action.AddWorkoutExercise(requireNotNull(state.value.activeWorkoutId), "added-face-pull", "face-pull"))
        }
        landscape()
        launch(repo)
        rule.onNodeWithText("Resume workout").performScrollTo().performClick()
        details()
        rule.onNodeWithTag("edit-goal").performScrollTo().performClick()
        rule.onNodeWithTag("goal-rest").performScrollTo().performClick()
        waitForSettledIme()
        val imeTop = imeTopOnScreen()
        val rest = onScreen("goal-rest")
        Log.i("RebuildFlexible", "landscape goal dialog with IME: imeTop=$imeTop ime=$imeBottom rest=$rest")
        // Too short to resize, the dialog pans: the field being typed in stays on screen above the keyboard.
        assertTrue("The field being typed in stays above the keyboard: $rest vs $imeTop",
            rest.top >= 0f && rest.bottom <= imeTop + 1)
        rule.onNodeWithTag("goal-rest").performTextReplacement("75")
        rule.onNodeWithTag("goal-rest").performImeAction()
        rule.waitUntil(5_000) { active(repo).exercises.single().restSeconds == 75 }
        rule.waitForIdle()
        rule.onNodeWithTag("goal-save").assertDoesNotExist()
        assertNull(active(repo).exercises.single().targetSets)
        assertSaved(repo)
    }

    @Test
    fun landscapeSkippedPanelKeepsUndoAndNextWhole() {
        val repo = repository {
            execute(Action.StartWorkout("intro-upper", FIXED_NOW))
            val w = requireNotNull(state.value.activeWorkout)
            execute(Action.SetSkipped(w.id, w.exercises.first().id, true))
        }
        landscape()
        launch(repo)
        rule.onNodeWithText("Resume workout").performScrollTo().performClick()
        rule.onNodeWithContentDescription("exercise 2 of 7", substring = true).assertExists()
        chip(0).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("skipped-panel").performScrollTo()
        assertWhole("undo-skip")
        assertWhole("next-exercise")
        rule.onNodeWithTag("next-exercise").assertTextContains("Next: Seated cable row")
        rule.onNodeWithTag("undo-skip").performClick()
        rule.waitUntil(5_000) { !active(repo).exercises.first().skipped }
        rule.waitForIdle()
        rule.onNodeWithTag("entry-kg").performScrollTo().assertIsDisplayed()
        assertSaved(repo)
    }
}
