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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rebuild.data.RebuildRepository
import com.rebuild.data.SnapshotCodec
import com.rebuild.domain.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The user's own routines on the real reducer and a real snapshot store: building and reviewing one, starting it as an
 * extra session, scheduling copies on a phase's week with an explicit choice for a taken day, the start chooser, the
 * Plan occurrence actions, Save as routine from History, and Today with two routines on one day. Large text is the
 * system font scale, as on the phone, so dialogs get it too; the keyboard is the device's real one.
 */
@RunWith(AndroidJUnit4::class)
class RoutinesDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var store: InMemorySnapshots
    private var keyboard: SoftwareKeyboardController? = null
    @Volatile private var imeBottom = 0
    private var scaledFont = false

    private val face = RoutineExercise("face-pull", 2, 12, 20, 90, false)
    private val hammer = RoutineExercise("curl-hammer", 2, 10, 12, 60, false)

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
            SideEffect { imeBottom = bottom }
            RebuildApp(ui, FakePlatform(), clock = { FIXED_NOW })
        }
    }

    private fun landscape() {
        rule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        rule.waitUntil(5_000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        productionWindow()
    }

    private fun active(repo: AppRepository) = requireNotNull(repo.state.value.activeWorkout)

    private fun weekIds(repo: AppRepository) = repo.state.value.scheduledRoutines(Phase.INTRO_TWO).map { it.id }

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

    /** A dialog is its own window: wait (bounded) for its first frame, then require [text] displayed inside it. */
    private fun assertDialogShows(text: String): SemanticsNodeInteraction {
        val inDialog = hasText(text) and hasAnyAncestor(isDialog())
        rule.waitUntil(5_000) {
            rule.onAllNodes(inDialog).fetchSemanticsNodes().size == 1 && rule.onNode(inDialog).isDisplayed()
        }
        return rule.onNode(inDialog).assertIsDisplayed()
    }

    private fun dialogButton(text: String) = rule.onNode(hasText(text) and hasClickAction() and hasAnyAncestor(isDialog()))

    /** The editor row's tappable part, which opens its targets. */
    private fun draftRow(index: Int, name: String): SemanticsNodeInteraction {
        rule.onNodeWithTag("routine-editor").performScrollToNode(hasTestTag("draft-row-$index"))
        return rule.onNode(hasClickAction() and hasText(name) and hasAnyAncestor(hasTestTag("draft-row-$index")))
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

    /** Fully visible where it is, without the test scrolling to it. */
    private fun assertWholeInPlace(tag: String) {
        val node = rule.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
        assertEquals("$tag must be fully visible without scrolling: size=${node.size}, bounds=${node.boundsInRoot}",
            node.size.height.toFloat(), node.boundsInRoot.height, 1f)
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

    private fun openRoutine(id: String) {
        rule.onNodeWithTag("open-routines").performScrollTo().performClick()
        rule.onNodeWithTag("routine-row-$id").performScrollTo().performClick()
        rule.waitForIdle()
    }

    @Test
    fun builtRoutineNeedsReviewedTargetsAndStartsAsAnExtraSession() {
        val repo = repository()
        launch(repo)
        rule.onNodeWithTag("open-routines").performScrollTo().assertTextContains("Your routines").performClick()
        rule.onNodeWithTag("routines-empty").assertIsDisplayed()
        rule.onNodeWithTag("routine-new").performClick()

        // Nothing is saved until it has a name and reviewed exercises.
        rule.onNodeWithTag("routine-save").assertTextContains("Save routine").performClick()
        rule.onNodeWithText("Name the routine.").assertIsDisplayed()
        rule.onNodeWithTag("routine-name").performTextReplacement("Arms")
        hideKeyboard()
        rule.onNodeWithTag("routine-save").performClick()
        rule.onNodeWithText("Add at least one exercise.").assertIsDisplayed()
        assertTrue(repo.state.value.routines.isEmpty())

        // A new exercise starts with blank targets and the visible default rest; it can't be added blank.
        rule.onNodeWithTag("routine-add-exercise").performScrollTo().performClick()
        pick("face pull", "face-pull")
        assertDialogShows("Add Cable face pull")
        assertEquals("", editable("goal-sets"))
        assertEquals("", editable("goal-min"))
        assertEquals("", editable("goal-max"))
        assertEquals("90", editable("goal-rest"))
        rule.onNodeWithText("Suggested 1:30 · default").assertExists()
        rule.onNodeWithTag("draft-targets-save").assertTextContains("Add to routine").performClick()
        rule.onNodeWithText("Enter working sets from 1 to", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("goal-sets").performTextReplacement("2")
        rule.onNodeWithTag("goal-min").performTextReplacement("12")
        rule.onNodeWithTag("goal-max").performTextReplacement("20")
        rule.onNodeWithTag("draft-targets-save").performClick()
        rule.waitForIdle()
        draftRow(0, "Cable face pull").assert(hasText("2 × 12–20 · rest 1:30"))

        // Choosing it again opens the one already listed instead of adding a second.
        rule.onNodeWithTag("routine-add-exercise").performScrollTo().performClick()
        pick("face pull", "face-pull")
        assertDialogShows("Cable face pull")
        assertEquals("2", editable("goal-sets"))
        rule.onNodeWithTag("draft-targets-save").assertTextContains("Done")
        dialogButton("Cancel").performClick()
        rule.onNodeWithText("Cable face pull is already in this routine.").assertExists()
        rule.onNodeWithTag("draft-row-1").assertDoesNotExist()

        rule.onNodeWithTag("routine-add-exercise").performScrollTo().performClick()
        pick("hammer", "curl-hammer")
        assertDialogShows("Add Dumbbell hammer curl")
        rule.onNodeWithTag("goal-sets").performTextReplacement("2")
        rule.onNodeWithTag("goal-min").performTextReplacement("10")
        rule.onNodeWithTag("goal-max").performTextReplacement("12")
        rule.onNodeWithTag("goal-rest").performTextReplacement("60")
        rule.onNodeWithTag("draft-targets-save").performClick()
        rule.waitForIdle()
        hideKeyboard()

        // Order is the user's; a removal can be undone in place.
        rule.onNodeWithTag("draft-up-1").performScrollTo().performClick()
        draftRow(0, "Dumbbell hammer curl").assertExists()
        rule.onNodeWithTag("draft-remove-0").performScrollTo().performClick()
        rule.onNodeWithTag("routine-undo").performScrollTo()
            .assert(hasAnyDescendant(hasText("Removed Dumbbell hammer curl.")))
        draftRow(0, "Cable face pull").assertExists()
        rule.onNodeWithTag("routine-undo-button").performClick()
        draftRow(0, "Dumbbell hammer curl").assert(hasText("2 × 10–12 · rest 1:00"))
        draftRow(1, "Cable face pull").assertExists()
        assertTrue("The editor saves nothing on its own", repo.state.value.routines.isEmpty())

        rule.onNodeWithTag("routine-save").performClick()
        rule.waitUntil(5_000) { repo.state.value.routines.size == 1 }
        rule.waitForIdle()
        val routine = repo.state.value.routines.single()
        assertEquals("Arms", routine.name)
        assertEquals(listOf(hammer, face), routine.exercises)
        assertTrue("Saving a routine schedules nothing", repo.state.value.placements.isEmpty())
        assertEquals(listOf("intro-upper", "lower-a", "upper", "lower-b"), weekIds(repo))
        assertSaved(repo)

        // Its own page: not on the week, so it starts as an extra session.
        rule.onNode(hasText("Arms") and isHeading()).assertExists()
        rule.onNodeWithTag("routine-start-note")
            .assertTextContains("Not on your Intro 2 week, so it runs as an extra session", substring = true)
        rule.onNodeWithTag("routine-start").assertTextContains("Start as extra session").performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkoutId != null }
        rule.waitForIdle()
        active(repo).let {
            assertNull("An extra session belongs to no routine on the week", it.templateId)
            assertEquals("Arms", it.name)
            assertEquals(listOf("curl-hammer", "face-pull"), it.exercises.map { e -> e.exercise.id })
            assertTrue("Its exercises are optional ones with the routine's goals", it.exercises.all { e -> e.optional })
            assertEquals(listOf(2, 2), it.exercises.map { e -> e.targetSets })
            assertEquals(listOf(10, 12), it.exercises.map { e -> e.minReps })
            assertEquals(listOf(12, 20), it.exercises.map { e -> e.maxReps })
            assertEquals(listOf(60, 90), it.exercises.map { e -> e.restSeconds })
            assertTrue("Nothing is logged for the user", it.exercises.all { e -> e.sets.isEmpty() && e.setGroups.isEmpty() })
        }
        assertNull("Starting starts no timer", repo.state.value.restTimer)
        rule.onNodeWithContentDescription("exercise 1 of 2", substring = true).assertExists()
        rule.onNode(hasText("Dumbbell hammer curl") and isHeading()).assertExists()
        assertSaved(repo)
    }

    @Test
    fun schedulingAsksAboutATakenDayAndTheStartChooserNeverGuesses() {
        val repo = repository { execute(Action.SaveRoutine("r-arms", "Arms", listOf(face, hammer))) }
        launch(repo)
        rule.onNodeWithText("More").performClick()
        rule.onNodeWithTag("more-routines").performClick()
        rule.onNodeWithTag("routine-row-r-arms").assert(hasText("Not on a weekly plan", substring = true)).performClick()
        rule.onNodeWithTag("routine-start").assertTextContains("Start as extra session")

        // A free day: shown as a rest day and added directly.
        rule.onNodeWithTag("routine-schedule").performScrollTo().performClick()
        assertDialogShows("Add Arms to a week")
        rule.onNodeWithTag("schedule-phase-INTRO_TWO").assert(hasContentDescription("Intro 2, current phase"))
        rule.onNodeWithTag("schedule-confirm").assertIsNotEnabled()
        rule.onNodeWithTag("schedule-day-1").performScrollTo().assertTextContains("Rest day").performClick()
        rule.onNodeWithTag("schedule-conflict").assertDoesNotExist()
        rule.onNodeWithTag("schedule-confirm").assertIsEnabled().assertTextEquals("Add to Intro 2 week").performClick()
        rule.waitUntil(5_000) { repo.state.value.placements.size == 1 }
        rule.waitForIdle()
        val monday = repo.state.value.placements.single()
        assertEquals(Phase.INTRO_TWO, monday.phase)
        assertEquals(1, monday.weekday)
        assertEquals("Arms", monday.name)
        assertEquals("r-arms", monday.routineId)
        assertFalse(monday.removedFromPlan)
        assertEquals(listOf(face, hammer), repo.state.value.placementExercises(monday))
        rule.onNodeWithText("Added to the Intro 2 week on Monday.").assertIsDisplayed()

        // A taken day never loses its routine silently: alongside or an explicit replacement.
        rule.onNodeWithTag("routine-schedule").performScrollTo().performClick()
        assertDialogShows("Add Arms to a week")
        rule.onNodeWithTag("schedule-day-1").performScrollTo().assertTextContains("Arms")
        rule.onNodeWithTag("schedule-day-2").performScrollTo().assertTextContains("Upper").performClick()
        rule.onNodeWithTag("schedule-conflict").performScrollTo()
            .assertTextEquals("Tuesday already has Upper. Add this as another workout that day, or replace one.")
        rule.onNodeWithTag("schedule-replace-intro-upper").assertExists()
        rule.onNodeWithTag("schedule-confirm").assertIsNotEnabled()
        rule.onNodeWithTag("schedule-alongside").performScrollTo().performClick()
        rule.onNodeWithTag("schedule-alongside").assertIsSelected()
        rule.onNodeWithTag("schedule-confirm").assertIsEnabled().performClick()
        rule.waitUntil(5_000) { repo.state.value.placements.size == 2 }
        rule.waitForIdle()
        val tuesday = repo.state.value.placements.single { it.id != monday.id }
        assertEquals(2, tuesday.weekday)
        assertTrue("Alongside takes nothing off", repo.state.value.scheduleRemovals.isEmpty())
        assertEquals(listOf(monday.id, "intro-upper", tuesday.id, "lower-a", "upper", "lower-b"), weekIds(repo))
        rule.onNodeWithText("Added to the Intro 2 week on Tuesday.").assertIsDisplayed()
        rule.onNodeWithTag("routine-start").assertTextContains("Start Arms")
        rule.onNodeWithTag("routine-start-note")
            .assertTextContains("On your Intro 2 week on Monday, Tuesday.", substring = true)
        assertSaved(repo)

        // Today: one checklist row per occurrence, with today's first routine featured.
        rule.onNodeWithText("Today").performClick()
        rule.onNodeWithTag("week-progress").performScrollTo().assertTextContains("0 of 6")
        rule.onNodeWithTag("start-scheduled").assertTextContains("Start Upper")
        rule.onNodeWithTag("also-today")
            .assertTextEquals("Also on Tuesday: Arms (not done)")
        rule.onNodeWithTag("week-row-${monday.id}").performScrollTo().assert(hasAnyDescendant(hasText("Arms")))
        rule.onNodeWithTag("week-row-${tuesday.id}").performScrollTo().assert(hasAnyDescendant(hasText("Arms")))

        // Starting the library routine asks which workout it is: nothing preselected.
        rule.onNodeWithTag("open-routines").performScrollTo().assertTextContains("Start one of your routines").performClick()
        rule.onNodeWithTag("routine-row-r-arms").assert(hasText("Intro 2: Mon, Tue", substring = true)).performClick()
        rule.onNodeWithTag("routine-start").performClick()
        assertDialogShows("Start Arms")
        rule.onNodeWithTag("start-choice-${monday.id}").assertTextContains("Monday · Arms").assertIsNotSelected()
        rule.onNodeWithTag("start-choice-${tuesday.id}").assertTextContains("Tuesday · Arms").assertIsNotSelected()
        rule.onNodeWithTag("start-choice-extra").assertIsNotSelected()
        rule.onNodeWithTag("start-choice-confirm").assertIsNotEnabled()
        rule.onNodeWithTag("start-choice-${tuesday.id}").performClick()
        rule.onNodeWithTag("start-choice-confirm").assertIsEnabled().performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkoutId != null }
        rule.waitForIdle()
        active(repo).let {
            assertEquals("The chosen day's occurrence", tuesday.id, it.templateId)
            assertEquals("Arms", it.name)
            assertEquals(listOf("face-pull", "curl-hammer"), it.exercises.map { e -> e.exercise.id })
            assertTrue("A scheduled copy's exercises are required", it.exercises.none { e -> e.optional })
            assertEquals(listOf(2, 2), it.exercises.map { e -> e.targetSets })
        }
        val week = requireNotNull(repo.state.value.weekChecklist(localDateOf(FIXED_NOW)))
        assertEquals(RoutineStatus.IN_PROGRESS, week.statusOf(tuesday.id))
        assertEquals(RoutineStatus.NOT_DONE, week.statusOf(monday.id))
        assertEquals(RoutineStatus.NOT_DONE, week.statusOf("intro-upper"))
        assertNull(repo.state.value.restTimer)
        assertSaved(repo)
    }

    @Test
    fun planOccurrenceUpdatesMovesTakesOffAndPutsBackExplicitly() {
        val repo = repository {
            execute(Action.SaveRoutine("r-arms", "Arms", listOf(face, hammer)))
            execute(Action.ScheduleRoutine("p-mon", Phase.INTRO_TWO, 1, "r-arms"))
            execute(Action.SaveRoutine("r-arms", "Arms", listOf(face.copy(sets = 3), hammer)))
        }
        launch(repo)
        rule.onNodeWithText("Plan", substring = false).performClick()
        rule.onNodeWithTag("plan-copy-note-p-mon").performScrollTo()
            .assertTextEquals("Your routine · differs from Arms in your library")
        assertEquals("The library edit never reached the copy", listOf(face, hammer),
            repo.state.value.placementExercises(repo.state.value.placements.single()))

        rule.onNodeWithTag("plan-options-p-mon").performScrollTo()
            .assert(hasContentDescription("Options for Arms, Monday")).performClick()
        rule.onNodeWithTag("plan-menu-update").performClick()
        assertDialogShows("Update from Arms?")
        rule.onNodeWithTag("confirm-button").assertTextContains("Update").performClick()
        rule.waitUntil(5_000) { !repo.state.value.libraryDiffers(repo.state.value.placements.single()) }
        rule.waitForIdle()
        assertEquals(listOf(face.copy(sets = 3), hammer), repo.state.value.placementExercises(repo.state.value.placements.single()))
        rule.onNodeWithTag("plan-copy-note-p-mon").performScrollTo()
            .assertTextEquals("Your routine · copy of Arms in your library")

        // Moving onto a taken day asks the same question; replacing takes off exactly that routine.
        rule.onNodeWithTag("plan-options-p-mon").performScrollTo().performClick()
        rule.onNodeWithTag("plan-menu-update").assertDoesNotExist()
        rule.onNodeWithTag("plan-menu-move").performClick()
        assertDialogShows("Change day · Arms")
        rule.onNodeWithTag("schedule-day-1").assertTextContains("Its day now").assertIsSelected()
        rule.onNodeWithTag("move-confirm").assertIsNotEnabled()
        rule.onNodeWithTag("schedule-day-4").performScrollTo().performClick()
        rule.onNodeWithTag("schedule-conflict").performScrollTo()
            .assertTextEquals("Thursday already has Lower A. Add this as another workout that day, or replace one.")
        rule.onNodeWithTag("move-confirm").assertIsNotEnabled()
        rule.onNodeWithTag("schedule-replace-lower-a").performScrollTo().performClick()
        rule.onNodeWithTag("move-confirm").assertIsEnabled().performClick()
        rule.waitUntil(5_000) { repo.state.value.placements.single().weekday == 4 }
        rule.waitForIdle()
        assertEquals("p-mon", repo.state.value.placements.single().id)
        assertEquals(listOf(ScheduleRemoval(Phase.INTRO_TWO, "lower-a")), repo.state.value.scheduleRemovals)
        assertEquals(listOf("intro-upper", "p-mon", "upper", "lower-b"), weekIds(repo))
        rule.onNodeWithTag("plan-occurrence-lower-a").assertDoesNotExist()

        // Put back onto its day, which the copy now holds: explicit again.
        rule.onNodeWithTag("plan-taken-off-lower-a").performScrollTo().assert(hasAnyDescendant(hasText("Thursday")))
        rule.onNodeWithTag("plan-put-back-lower-a").performClick()
        assertDialogShows("Put Lower A back")
        rule.onNodeWithTag("schedule-conflict")
            .assertTextEquals("Thursday already has Arms. Add this as another workout that day, or replace one.")
        rule.onNodeWithTag("restore-confirm-week").assertIsNotEnabled()
        rule.onNodeWithTag("schedule-alongside").performScrollTo().performClick()
        rule.onNodeWithTag("restore-confirm-week").assertIsEnabled().performClick()
        rule.waitUntil(5_000) { repo.state.value.scheduleRemovals.isEmpty() }
        rule.waitForIdle()
        assertEquals(listOf("intro-upper", "lower-a", "p-mon", "upper", "lower-b"), weekIds(repo))
        rule.onNodeWithTag("plan-taken-off-lower-a").assertDoesNotExist()

        // An original routine has no copy actions; taking it off leaves the same-named Saturday routine.
        rule.onNodeWithTag("plan-options-intro-upper").performScrollTo().performClick()
        rule.onNodeWithTag("plan-menu-edit").assertDoesNotExist()
        rule.onNodeWithTag("plan-menu-move").assertDoesNotExist()
        rule.onNodeWithTag("plan-menu-take-off").performClick()
        assertDialogShows("Take Upper off the Intro 2 week?")
        rule.onNodeWithTag("confirm-button").assertTextContains("Take off").performClick()
        rule.waitUntil(5_000) { repo.state.value.scheduleRemovals.isNotEmpty() }
        rule.waitForIdle()
        assertEquals(listOf(ScheduleRemoval(Phase.INTRO_TWO, "intro-upper")), repo.state.value.scheduleRemovals)
        assertEquals(listOf("lower-a", "p-mon", "upper", "lower-b"), weekIds(repo))
        rule.onNodeWithTag("plan-occurrence-upper").performScrollTo().assertExists()
        rule.onNodeWithTag("plan-taken-off-intro-upper").performScrollTo().assert(hasAnyDescendant(hasText("Upper")))
        assertSaved(repo)

        // Schedule changes reach Today at once: Tuesday is now an honest rest day.
        rule.onNodeWithText("Today").performClick()
        rule.onNodeWithText("Rest day").assertIsDisplayed()
        rule.onNodeWithTag("start-scheduled").assertDoesNotExist()
        rule.onNodeWithTag("week-progress").performScrollTo().assertTextContains("0 of 4")
        rule.onNodeWithTag("week-row-intro-upper").assertDoesNotExist()
    }

    @Test
    fun historySaveAsRoutineNeedsReviewedTargetsAndNeverChangesTheWorkout() {
        val repo = repository {
            execute(Action.StartWorkout("intro-upper", FIXED_NOW - 7_200_000))
            val w = requireNotNull(state.value.activeWorkout)
            execute(Action.SaveSet(w.id, w.exercises.first().id, "chest-1", 40.0, 10, null, false, FIXED_NOW - 7_000_000))
            execute(Action.AddWorkoutExercise(w.id, "added-face-pull", "face-pull"))
            execute(Action.SaveSet(w.id, "added-face-pull", "face-1", 15.0, 15, null, false, FIXED_NOW - 6_900_000))
            execute(Action.FinishWorkout(FIXED_NOW - 3_600_000, countForWeek = true))
        }
        val workout = repo.state.value.workouts.single()
        assertTrue(workout.countedForWeek)
        launch(repo)
        rule.onNodeWithText("History").performClick()
        rule.onNodeWithText("Upper", substring = false).performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("save-as-routine"))
        rule.onNodeWithTag("save-as-routine").performClick()

        rule.onNode(hasText("Save as routine") and isHeading()).assertExists()
        assertEquals("Upper", editable("routine-name"))
        rule.onNodeWithText("Exercises · 8").assertExists()
        rule.onNodeWithTag("routine-save").performClick()
        rule.onNodeWithText("Cable face pull: Needs working sets and a rep range.").assertIsDisplayed()
        assertTrue(repo.state.value.routines.isEmpty())

        // Logged loads and reps never become targets: the added exercise's targets are typed in.
        draftRow(7, "Cable face pull").assert(hasText("Needs working sets and a rep range.")).performClick()
        assertDialogShows("Cable face pull")
        assertEquals("", editable("goal-sets"))
        assertEquals("", editable("goal-min"))
        assertEquals("90", editable("goal-rest"))
        rule.onNodeWithTag("goal-sets").performTextReplacement("2")
        rule.onNodeWithTag("goal-min").performTextReplacement("12")
        rule.onNodeWithTag("goal-max").performTextReplacement("20")
        rule.onNodeWithTag("draft-targets-save").assertTextContains("Done").performClick()
        rule.waitForIdle()
        hideKeyboard()
        rule.onNodeWithTag("routine-save").performClick()
        rule.waitUntil(5_000) { repo.state.value.routines.size == 1 }
        rule.waitForIdle()

        val routine = repo.state.value.routines.single()
        assertEquals("Upper", routine.name)
        val expected = workout.exercises.map {
            if (it.id == "added-face-pull") face
            else RoutineExercise(it.exercise.id, it.targetSets!!, it.minReps!!, it.maxReps!!, it.restSeconds, it.perLeg)
        }
        assertEquals("The workout's own targets, in its order, plus the reviewed one", expected, routine.exercises)
        assertEquals("The workout and its credit stay exactly as they were", workout, repo.state.value.workouts.single())
        assertTrue(repo.state.value.placements.isEmpty())
        assertEquals(RoutineStatus.DONE,
            requireNotNull(repo.state.value.weekChecklist(localDateOf(FIXED_NOW))).statusOf("intro-upper"))
        rule.onNodeWithTag("routine-start").assertTextContains("Start as extra session")
        assertSaved(repo)
    }

    @Test
    fun largeTextEditorAndScheduleDialogKeepEveryActionWhole() {
        systemFontScale(2f)
        val repo = repository { execute(Action.SaveRoutine("r-arms", "Arms", listOf(face, hammer))) }
        launch(repo)
        openRoutine("r-arms")
        assertWhole("routine-start")
        assertWhole("routine-schedule")

        rule.onNodeWithTag("routine-edit").performScrollTo().performClick()
        // Large text moves each row's controls under its name, so neither is squeezed.
        val text = draftRow(1, "Dumbbell hammer curl").fetchSemanticsNode().boundsInRoot
        listOf("draft-up-1", "draft-down-1", "draft-remove-1").forEach { tag ->
            val control = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag sits below the row's text with large text: $control vs $text", control.top >= text.bottom - 1)
            assertWhole(tag)
        }
        assertWhole("routine-add-exercise")
        assertWhole("routine-save")
        rule.onNodeWithContentDescription("Back").performClick()

        rule.onNodeWithTag("routine-schedule").performScrollTo().performClick()
        assertDialogShows("Add Arms to a week")
        assertWhole("schedule-day-7")
        rule.onNodeWithTag("schedule-day-2").performScrollTo().performClick()
        rule.onNodeWithTag("schedule-conflict").performScrollTo().assertIsDisplayed()
        assertWhole("schedule-alongside")
        assertWhole("schedule-replace-intro-upper")
        rule.onNodeWithTag("schedule-alongside").performClick()
        // The dialog's actions stay whole outside its scrolling body.
        assertWhole("schedule-confirm")
        rule.onNodeWithTag("schedule-confirm").assertIsEnabled().performClick()
        rule.waitUntil(5_000) { repo.state.value.placements.size == 1 }
        rule.waitForIdle()
        assertEquals(2, repo.state.value.placements.single().weekday)
        assertTrue(repo.state.value.scheduleRemovals.isEmpty())
        assertSaved(repo)
    }

    @Test
    fun aTakenDayLowInTheListBringsItsQuestionIntoView() {
        val repo = repository {
            execute(Action.SaveRoutine("r-arms", "Arms", listOf(face, hammer)))
            execute(Action.ScheduleRoutine("p-mon", Phase.INTRO_TWO, 1, "r-arms"))
        }
        launch(repo)
        val before = repo.state.value

        // Change day: the confirm button waits for the choice, so the question shows without the user scrolling.
        rule.onNodeWithText("Plan", substring = false).performClick()
        rule.onNodeWithTag("plan-options-p-mon").performScrollTo().performClick()
        rule.onNodeWithTag("plan-menu-move").performClick()
        assertDialogShows("Change day · Arms")
        rule.onNodeWithTag("schedule-day-7").performScrollTo().assertTextContains("Lower B").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("schedule-conflict").assertIsDisplayed()
            .assertTextEquals("Sunday already has Lower B. Add this as another workout that day, or replace one.")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        assertWholeInPlace("schedule-alongside")
        assertWholeInPlace("schedule-replace-lower-b")
        rule.onNodeWithTag("move-confirm").assertIsNotEnabled()
        dialogButton("Cancel").performClick()
        rule.waitForIdle()

        // From the library, below the phase choices: the last day, then another taken day.
        rule.onNodeWithText("Today").performClick()
        openRoutine("r-arms")
        rule.onNodeWithTag("routine-schedule").performScrollTo().performClick()
        assertDialogShows("Add Arms to a week")
        rule.onNodeWithTag("schedule-day-7").performScrollTo().assertTextContains("Lower B").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("schedule-conflict").assertIsDisplayed()
            .assertTextEquals("Sunday already has Lower B. Add this as another workout that day, or replace one.")
        assertWholeInPlace("schedule-alongside")
        assertWholeInPlace("schedule-replace-lower-b")
        rule.onNodeWithTag("schedule-confirm").assertIsNotEnabled()
        rule.onNodeWithTag("schedule-day-6").performScrollTo().assertTextContains("Upper").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("schedule-conflict").assertIsDisplayed()
            .assertTextEquals("Saturday already has Upper. Add this as another workout that day, or replace one.")
        assertWholeInPlace("schedule-alongside")
        assertWholeInPlace("schedule-replace-upper")
        rule.onNodeWithTag("schedule-confirm").assertIsNotEnabled()
        rule.onNodeWithTag("schedule-alongside").performClick()
        rule.onNodeWithTag("schedule-confirm").assertIsEnabled()
        dialogButton("Cancel").performClick()
        rule.waitForIdle()
        assertEquals("Looking at the choices changes nothing", before, repo.state.value)
        assertSaved(repo)
    }

    @Test
    fun landscapeEditorKeepsTheNameAboveTheKeyboardAndSaveReturnsAfterIt() {
        val repo = repository { execute(Action.SaveRoutine("r-arms", "Arms", listOf(face, hammer))) }
        landscape()
        launch(repo)
        openRoutine("r-arms")
        rule.onNodeWithTag("routine-edit").performScrollTo().performClick()
        rule.onNodeWithTag("routine-save").assertTextContains("Save changes").assertIsDisplayed()
        rule.onNodeWithTag("routine-name").performClick()
        waitForSettledIme()
        val imeTop = imeTopOnScreen()
        val name = onScreen("routine-name")
        Log.i("RebuildRoutines", "landscape editor with IME: imeTop=$imeTop ime=$imeBottom name=$name")
        assertTrue("The name being typed stays on screen above the keyboard: $name vs $imeTop",
            name.top >= 0f && name.bottom <= imeTop + 1)
        // Too short for both, the bottom action steps aside while typing.
        rule.onNodeWithTag("routine-save").assertDoesNotExist()
        rule.onNodeWithTag("routine-name").performTextReplacement("Arms day")
        rule.onNodeWithTag("routine-name").performImeAction()
        hideKeyboard()
        assertWhole("routine-save")
        rule.onNodeWithTag("routine-save").performClick()
        rule.waitUntil(5_000) { repo.state.value.routines.single().name == "Arms day" }
        rule.waitForIdle()
        assertEquals(listOf(face, hammer), repo.state.value.routines.single().exercises)
        rule.onNodeWithTag("routine-start").assertExists()
        assertSaved(repo)
    }

    @Test
    fun todayFeaturesTheRemainingRoutineOfADayAndResumeWinsAfterStarting() {
        val repo = repository {
            execute(Action.SaveRoutine("r-arms", "Arms", listOf(face, hammer)))
            execute(Action.ScheduleRoutine("p-tue", Phase.INTRO_TWO, 2, "r-arms", alongside = true))
            execute(Action.StartWorkout("intro-upper", FIXED_NOW - 7_200_000))
            val w = requireNotNull(state.value.activeWorkout)
            var t = FIXED_NOW - 7_000_000
            w.exercises.forEach { e ->
                repeat(requireNotNull(e.targetSets)) { i ->
                    execute(Action.SaveSet(w.id, e.id, "${e.id}-$i", 20.0, requireNotNull(e.minReps), null, false, ++t))
                }
            }
            execute(Action.FinishWorkout(FIXED_NOW - 3_600_000))
        }
        val week = requireNotNull(repo.state.value.weekChecklist(localDateOf(FIXED_NOW)))
        assertEquals(RoutineStatus.DONE, week.statusOf("intro-upper"))
        launch(repo)
        rule.onNodeWithTag("start-scheduled").performScrollTo().assertTextContains("Start Arms")
        rule.onNodeWithTag("also-today")
            .assertTextEquals("Also on Tuesday: Upper (done)")
        rule.onNodeWithTag("week-progress").performScrollTo().assertTextContains("1 of 5")
        rule.onNodeWithTag("week-row-intro-upper").performScrollTo().assert(hasAnyDescendant(hasText("Done", substring = true)))

        rule.onNodeWithTag("start-scheduled").performScrollTo().performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkoutId != null }
        rule.waitForIdle()
        assertEquals("p-tue", active(repo).templateId)
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithTag("active-banner").assertIsDisplayed()
        rule.onNodeWithText("Resume workout").assertIsDisplayed()
        rule.onNodeWithTag("start-scheduled").assertDoesNotExist()
        rule.onNodeWithTag("open-routines").assertDoesNotExist()
        rule.onNodeWithTag("week-row-p-tue").performScrollTo().assert(hasAnyDescendant(hasText("In progress", substring = true)))
        assertEquals(RoutineStatus.IN_PROGRESS,
            requireNotNull(repo.state.value.weekChecklist(localDateOf(FIXED_NOW))).statusOf("p-tue"))
        assertSaved(repo)
    }
}
