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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
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
 * Timed, distance and cardio exercises and the activity stopwatch on the real reducer and a real snapshot store, with
 * the phone's monotonic clock moved by hand: paused time is never counted, stopping only fills the fields for review,
 * nothing is logged or rested until Log, a time that can't be confirmed is entered by the user, and History
 * corrections never start a rest or the stopwatch. Large text, landscape and a real keyboard keep the entry whole, and a
 * goal's distance spans its dialog with the unit choice underneath.
 */
@RunWith(AndroidJUnit4::class)
class MeasuredDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var store: InMemorySnapshots
    private var keyboard: SoftwareKeyboardController? = null
    @Volatile private var imeBottom = 0
    private var scaledFont = false

    /** The monotonic clock the stopwatch reads; each test moves it by hand. */
    @Volatile private var elapsed = 1_000_000L
    @Volatile private var boot: Int? = 7
    @Volatile private var session = "this-process"
    private fun reading() = Monotonic(boot, session, elapsed)

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

    private fun landscape() {
        rule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        rule.waitUntil(5_000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        productionWindow()
    }

    /** The seeded intro plan (Upper on Tuesday 22 Sep) in a real repository writing to [store]. */
    private fun repository(setup: suspend RebuildRepository.() -> Unit = {}): RebuildRepository {
        store = InMemorySnapshots(Seed.initialState().copy(phase = Phase.INTRO_TWO))
        return introRepository(store, setup)
    }

    @OptIn(ExperimentalLayoutApi::class)
    private fun launch(repo: AppRepository, restoration: StateRestorationTester? = null) {
        val ui = MainThreadCollection(repo)
        val content: @Composable () -> Unit = {
            keyboard = LocalSoftwareKeyboardController.current
            val bottom = WindowInsets.ime.getBottom(LocalDensity.current)
            SideEffect { imeBottom = bottom }
            RebuildApp(ui, FakePlatform(), clock = { FIXED_NOW }, monotonic = ::reading)
        }
        if (restoration != null) restoration.setContent(content) else rule.setContent(content)
    }

    private fun active(repo: AppRepository) = requireNotNull(repo.state.value.activeWorkout)
    private fun entry(repo: AppRepository, id: String) = active(repo).exercises.single { it.id == id }
    private fun watch(repo: AppRepository) = repo.state.value.stopwatch

    private fun assertSaved(repo: AppRepository) =
        assertEquals("The saved snapshot must be exactly what the app shows", SnapshotCodec.decode(store.json), repo.state.value)

    private fun editable(tag: String) =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun description(node: SemanticsNodeInteraction): String? =
        node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()

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

    /** [tag]'s node, scrolled into view first when it is inside a scrolling container. */
    private fun scrolled(tag: String): SemanticsNodeInteraction {
        if (rule.onAllNodes(hasTestTag(tag) and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithTag(tag).performScrollTo()
        }
        return rule.onNodeWithTag(tag)
    }

    private fun waitEnabled(tag: String) = rule.waitUntil(5_000) {
        rule.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
    }

    /** Taps [tag] once the app's busy guard allows it: a tap while it is closed would be ignored. */
    private fun tap(tag: String) {
        waitEnabled(tag)
        scrolled(tag).performClick()
        rule.waitForIdle()
    }

    private fun type(tag: String, text: String) {
        scrolled(tag).performTextReplacement(text)
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

    /** Starts an empty extra workout from Home and adds library exercise [id] as its first. */
    private fun startExtraWith(repo: AppRepository, query: String, id: String): WorkoutExercise {
        rule.onNodeWithTag("start-empty").performScrollTo().performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkoutId != null }
        rule.onNodeWithTag("add-first-exercise").performClick()
        pick(query, id)
        rule.waitUntil(5_000) { active(repo).exercises.size == 1 }
        rule.waitForIdle()
        return active(repo).exercises.single()
    }

    private fun addExercise(repo: AppRepository, query: String, id: String): WorkoutExercise {
        val count = active(repo).exercises.size
        menu("menu-add-exercise")
        pick(query, id)
        rule.waitUntil(5_000) { active(repo).exercises.size == count + 1 }
        rule.waitForIdle()
        return active(repo).exercises.last()
    }

    /** Logs the entry's effort, then waits out the app's double-tap guard. */
    private fun logEffort(repo: AppRepository, entryId: String) {
        val before = entry(repo, entryId).efforts.size
        tap("log-effort")
        rule.waitUntil(5_000) { entry(repo, entryId).efforts.size == before + 1 }
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasTestTag("log-effort")).fetchSemanticsNodes().isEmpty() ||
                rule.onAllNodes(hasTestTag("log-effort") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
    }

    private fun startWatch(repo: AppRepository): Stopwatch {
        tap("start-stopwatch")
        rule.waitUntil(5_000) { watch(repo) != null }
        rule.waitForIdle()
        return requireNotNull(watch(repo))
    }

    /** Taps a stopwatch control and waits for the change it makes: every change advances the stopwatch's step. */
    private fun stepTo(repo: AppRepository, tag: String, step: Int): Stopwatch {
        tap(tag)
        rule.waitUntil(5_000) { watch(repo)?.step == step }
        rule.waitForIdle()
        return requireNotNull(watch(repo))
    }

    private fun errorShown(message: String) = rule.waitUntil(5_000) {
        rule.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty()
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

    /**
     * Fully visible, after scrolling if it scrolls (never clipped by its container); a control also needs a 48 dp
     * target, which a line of text such as the stopwatch's time doesn't.
     */
    private fun assertWhole(tag: String) = assertWhole(tag, control = true)

    private fun assertTextWhole(tag: String) = assertWhole(tag, control = false)

    private fun assertWhole(tag: String, control: Boolean) {
        scrolled(tag)
        val node = rule.onNodeWithTag(tag).fetchSemanticsNode()
        assertTrue("$tag must be displayed: bounds=${node.boundsInRoot}, size=${node.size}",
            rule.onNodeWithTag(tag).isDisplayed())
        assertEquals("$tag must be fully visible: size=${node.size}, bounds=${node.boundsInRoot}",
            node.size.height.toFloat(), node.boundsInRoot.height, 1f)
        assertEquals("$tag must not be cut off sideways: size=${node.size}, bounds=${node.boundsInRoot}",
            node.size.width.toFloat(), node.boundsInRoot.width, 1f)
        if (!control) return
        val minimum = 48 * rule.activity.resources.displayMetrics.density
        assertTrue("$tag needs a 48 dp target: ${node.touchBoundsInRoot} vs $minimum",
            node.touchBoundsInRoot.height >= minimum - 1 && node.touchBoundsInRoot.width >= minimum - 1)
    }

    private fun onScreen(tag: String): Rect {
        val node = rule.onNodeWithTag(tag).fetchSemanticsNode()
        val origin = node.positionOnScreen
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

    /** [tag] is whole (never clipped by scrolling) and above the keyboard and any rest or stopwatch bar under it. */
    private fun assertWholeAboveKeyboard(tag: String, imeTop: Float) {
        val node = rule.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
        val bounds = onScreen(tag)
        assertEquals("$tag must be whole with the keyboard up: size=${node.size}, visible=${node.boundsInRoot}",
            node.size.height.toFloat(), node.boundsInRoot.height, 1f)
        assertTrue("$tag must stay above the keyboard: $bounds vs $imeTop", bounds.bottom <= imeTop + 1 && bounds.top >= 0f)
        rule.onAllNodes(hasTestTag("rest-bar")).fetchSemanticsNodes().singleOrNull()?.let { bar ->
            assertTrue("$tag must stay above the rest bar: ${node.boundsInRoot} vs ${bar.boundsInRoot}",
                node.boundsInRoot.bottom <= bar.boundsInRoot.top + 1)
        }
    }

    /** [tag] inside the open dialog: the workout's own fields with the same tags stay in the tree under it. */
    private fun inDialog(tag: String) = rule.onNode(hasTestTag(tag) and hasAnyAncestor(isDialog()))

    /**
     * [label]'s own Text inside the open dialog shows every character and breaks only between words. (Its semantics
     * layout lays the paragraph out at the full constraint width, so `hasVisualOverflow` reports a single short line
     * as overflowing; truncation and line ends are what matter.)
     */
    private fun assertDialogLabelWhole(label: String) {
        val node = rule.onNode(hasText(label) and hasAnyAncestor(isDialog()), useUnmergedTree = true)
            .fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        val ends = (0 until layout.lineCount).map { layout.getLineEnd(it) }
        Log.i("RebuildMeasured", "label \"$label\": lines=$ends size=${layout.size} of ${layout.layoutInput.constraints}")
        assertFalse("\"$label\" must not be cut short: $ends", layout.multiParagraph.didExceedMaxLines ||
            layout.didOverflowHeight)
        assertEquals("\"$label\" must show every character: $ends", label.length, ends.last())
        ends.dropLast(1).forEach { end ->
            assertFalse("\"$label\" must not break inside a word: $ends",
                label[end - 1].isLetterOrDigit() && label[end].isLetterOrDigit())
        }
    }

    /**
     * The goal's distance spans the dialog with each unit whole underneath: beside the three-way unit choice, a goal
     * dialog at default text size left the field about a fifth of its width and broke its label mid-word.
     */
    private fun assertDistanceGoalWhole(label: String) {
        val field = inDialog("goal-distance").performScrollTo().fetchSemanticsNode()
        assertDialogLabelWhole(label)
        val body = rule.onNodeWithTag("dialog-body").fetchSemanticsNode()
        assertEquals("The distance field spans the dialog: ${field.size} vs ${body.size}",
            body.size.width.toFloat(), field.size.width.toFloat(), 1f)
        val minimum = 48 * rule.activity.resources.displayMetrics.density
        DistanceUnit.entries.forEach { unit ->
            val choice = inDialog("distance-unit-${unit.name}").performScrollTo().fetchSemanticsNode()
            val fieldBottom = inDialog("goal-distance").fetchSemanticsNode().let { it.positionInRoot.y + it.size.height }
            assertTrue("$unit sits under the field: ${choice.positionInRoot} vs $fieldBottom",
                choice.positionInRoot.y >= fieldBottom - 1)
            assertEquals("$unit is not cut off: ${choice.size} vs ${choice.boundsInRoot}",
                choice.size.width.toFloat(), choice.boundsInRoot.width, 1f)
            assertTrue("$unit keeps a 48 dp target: ${choice.touchBoundsInRoot}",
                choice.touchBoundsInRoot.height >= minimum - 1 && choice.touchBoundsInRoot.width >= minimum - 1)
        }
    }

    @Test
    fun distanceGoalsSpanTheirDialogWithTheUnitUnderneathTodayAndInThePlan() {
        val repo = repository { execute(Action.StartWorkout("intro-upper", FIXED_NOW)) }
        launch(repo)
        rule.onNodeWithText("Resume workout").performScrollTo().performClick()
        rule.waitForIdle()

        val run = addExercise(repo, "treadmill", "treadmill")
        tap("exercise-details")
        tap("edit-goal")
        assertDialogShows("Goal and rest · Treadmill")
        assertDistanceGoalWhole("Total distance (optional) (km)")
        inDialog("goal-distance").performTextReplacement("5")
        tap("goal-save")
        rule.waitUntil(5_000) { entry(repo, run.id).measuredGoal.totalDistance != null }
        rule.waitForIdle()
        assertEquals(MeasuredGoal(totalDistance = Distance(5_000, DistanceUnit.KM)), entry(repo, run.id).measuredGoal)

        tap("save-addition")
        assertDialogShows("Add to Upper")
        assertEquals("5", editable("goal-distance"))
        assertDistanceGoalWhole("Total distance (km)")
        inDialog("distance-unit-MI").performClick()
        rule.waitForIdle()
        inDialog("distance-unit-MI").assertIsSelected()
        assertEquals("The number stays; nothing is converted", "5", editable("goal-distance"))
        assertDistanceGoalWhole("Total distance (mi)")
        tap("plan-addition-save")
        rule.waitUntil(5_000) { repo.state.value.planAdditions.isNotEmpty() }
        rule.waitForIdle()
        repo.state.value.planAdditions.single().let {
            assertEquals("treadmill", it.exerciseId)
            assertEquals(MeasuredGoal(totalDistance = Distance(5_000, DistanceUnit.MI)), it.measuredGoal)
        }
        assertTrue("Today it stays optional", entry(repo, run.id).optional)
        assertEquals("Today's goal is unchanged", Distance(5_000, DistanceUnit.KM),
            entry(repo, run.id).measuredGoal.totalDistance)
        hideKeyboard()

        val carry = addExercise(repo, "farmer", "farmers-carry")
        tap("exercise-details")
        tap("edit-goal")
        assertDialogShows("Goal and rest · Farmer's carry")
        assertDistanceGoalWhole("Minimum distance for each (m)")
        inDialog("goal-sets").performTextReplacement("2")
        inDialog("goal-distance").performTextReplacement("40")
        tap("goal-save")
        rule.waitUntil(5_000) { entry(repo, carry.id).targetSets == 2 }
        rule.waitForIdle()
        assertEquals(MeasuredGoal(effortDistance = Distance(40_000, DistanceUnit.M)), entry(repo, carry.id).measuredGoal)
        assertSaved(repo)
    }

    @Test
    fun theStopwatchCountsOnlyRunningTimeAndNothingIsLoggedOrRestedUntilLog() {
        val repo = repository()
        launch(repo)
        val plank = startExtraWith(repo, "plank", "plank")
        assertEquals(Measure.TIMED, plank.exercise.measure)
        assertEquals("No history or plan for it: the visible 90-second default", DEFAULT_REST_SECONDS, plank.restSeconds)
        rule.onNodeWithTag("target-line").assertTextEquals("No target · log what you do")
        rule.onNodeWithTag("entry-context").assertTextContains("Timed set 1", substring = true)
        rule.onNodeWithTag("effort-kg").assertDoesNotExist()
        rule.onNodeWithTag("effort-distance").assertDoesNotExist()

        val started = startWatch(repo)
        assertEquals(Stopwatch(started.id, active(repo).id, plank.id, 0, 1_000_000, 7, "this-process", stopped = false,
            needsConfirmation = false, startedAt = FIXED_NOW, step = 0), started)
        assertTrue(entry(repo, plank.id).efforts.isEmpty())
        assertNull(repo.state.value.restTimer)
        assertSaved(repo)
        // Timing: the fields give way to the time, Pause and Stop.
        rule.onNodeWithTag("effort-minutes").assertDoesNotExist()
        rule.onNodeWithTag("log-effort").assertDoesNotExist()
        rule.onNodeWithTag("stopwatch-time").assert(hasContentDescription("Stopwatch 0 seconds, timing"))

        elapsed += 30_000
        rule.waitUntil(5_000) { description(rule.onNodeWithTag("stopwatch-time")) == "Stopwatch 30 seconds, timing" }
        val paused = stepTo(repo, "pause-stopwatch", 1)
        assertNull(paused.runningSince)
        assertEquals(30_000L, paused.elapsedMs)
        assertFalse(paused.stopped)
        rule.onNodeWithTag("stopwatch-time").assert(hasContentDescription("Stopwatch 30 seconds, paused"))
        rule.onNodeWithTag("entry-context").assertTextContains("stopwatch paused", substring = true)

        elapsed += 600_000
        rule.waitForIdle()
        rule.onNodeWithTag("stopwatch-time").assert(hasContentDescription("Stopwatch 30 seconds, paused"))
        val resumed = stepTo(repo, "resume-stopwatch", 2)
        assertEquals("Ten paused minutes are never counted", 30_000L, resumed.elapsedMs)
        assertEquals(elapsed, resumed.runningSince)
        elapsed += 15_000
        val stopped = stepTo(repo, "stop-stopwatch", 3)
        assertTrue(stopped.stopped)
        assertNull(stopped.runningSince)
        assertEquals(45_000L, stopped.elapsedMs)
        rule.onNodeWithTag("stopwatch-review").assertTextEquals(
            "Stopped at 0:45. Not saved until you log it.")
        assertEquals("0", editable("effort-minutes"))
        assertEquals("45", editable("effort-seconds"))
        assertTrue("Stopping logs nothing", entry(repo, plank.id).efforts.isEmpty())
        assertNull("and starts no rest", repo.state.value.restTimer)
        assertSaved(repo)
        rule.onNodeWithTag("resume-stopwatch").assertExists()
        rule.onNodeWithTag("discard-stopwatch").assertExists()

        logEffort(repo, plank.id)
        assertEquals("The stopwatch's effort keeps its id and exactly the time shown",
            Effort(stopped.id, 45, null, null, LoadUnit.KG, null, FIXED_NOW), entry(repo, plank.id).efforts.single())
        assertNull("Logged, the stopwatch is done", watch(repo))
        requireNotNull(repo.state.value.restTimer).let {
            assertEquals(plank.id, it.exerciseId)
            assertEquals("A hold rests after Log", FIXED_NOW + 90_000, it.deadline)
        }
        assertSaved(repo)
        rule.onNodeWithTag("effort-count").assertTextEquals("Timed sets: 1")
        rule.onNode(hasTestTag("effort-row") and hasContentDescription("Timed set 1: 45 seconds", substring = true))
            .assertExists()
        assertEquals("No time is offered for the next one", "", editable("effort-minutes"))
        assertEquals("", editable("effort-seconds"))
    }

    @Test
    fun cardioLogsATimeADistanceOrBothAndRestsOnlyWhenAsked() {
        val repo = repository()
        launch(repo)
        val run = startExtraWith(repo, "treadmill", "treadmill")
        assertEquals(Measure.CARDIO, run.exercise.measure)
        rule.onNodeWithTag("distance-unit-KM").assertIsSelected()
        rule.onNodeWithTag("effort-kg").assertDoesNotExist()
        rule.onNodeWithTag("cardio-rest").assertDoesNotExist()

        tap("log-effort")
        errorShown("Enter a time, a distance or both.")
        assertTrue("Nothing is logged without a measurement", entry(repo, run.id).efforts.isEmpty())
        hideKeyboard()

        tap("distance-unit-MI")
        type("effort-distance", "1.5")
        logEffort(repo, run.id)
        assertEquals(Effort(entry(repo, run.id).efforts.single().id, null, Distance(1_500, DistanceUnit.MI), null,
            LoadUnit.KG, null, FIXED_NOW), entry(repo, run.id).efforts.single())
        assertNull("Cardio never rests by itself", repo.state.value.restTimer)
        assertEquals("No distance is offered for the next effort", "", editable("effort-distance"))
        rule.onNodeWithTag("distance-unit-MI").assertIsSelected()

        type("effort-minutes", "10")
        logEffort(repo, run.id)
        entry(repo, run.id).efforts.last().let {
            assertEquals(600, it.durationSeconds)
            assertNull(it.distance)
        }
        rule.onNodeWithTag("distance-unit-MI").assertIsSelected()
        rule.onNodeWithTag("effort-count").assertTextEquals("Logged: 10:00 · 1.5 mi · 2 efforts")
        assertNull(repo.state.value.restTimer)
        hideKeyboard()

        rule.onNodeWithTag("cardio-rest").assertTextContains("Start rest 1:30")
        tap("cardio-rest")
        rule.waitUntil(5_000) { repo.state.value.restTimer != null }
        rule.waitForIdle()
        requireNotNull(repo.state.value.restTimer).let {
            assertEquals(run.id, it.exerciseId)
            assertEquals(FIXED_NOW + 90_000, it.deadline)
        }
        rule.onNodeWithTag("cardio-rest").assertDoesNotExist()
        assertSaved(repo)
    }

    @Test
    fun aCarryTimedByTheStopwatchKeepsItsTypedDistanceAndLoadAndOffersOnlyTheLoadNext() {
        val repo = repository()
        launch(repo)
        val carry = startExtraWith(repo, "farmer", "farmers-carry")
        assertEquals(Measure.DISTANCE, carry.exercise.measure)
        rule.onNodeWithTag("distance-unit-M").assertIsSelected()
        type("effort-distance", "40")
        type("effort-kg", "24")
        hideKeyboard()

        val started = startWatch(repo)
        rule.onNodeWithTag("effort-distance").assertDoesNotExist()
        elapsed += 20_000
        stepTo(repo, "stop-stopwatch", 1)
        rule.onNodeWithTag("stopwatch-review").assertTextContains("Stopped at 0:20.", substring = true)
        assertEquals(listOf("40", "0", "20", "24"),
            listOf("effort-distance", "effort-minutes", "effort-seconds", "effort-kg").map(::editable))
        logEffort(repo, carry.id)
        assertEquals(Effort(started.id, 20, Distance(40_000, DistanceUnit.M), 24.0, LoadUnit.KG, null, FIXED_NOW),
            entry(repo, carry.id).efforts.single())
        assertEquals("A carry rests after Log", FIXED_NOW + 90_000, repo.state.value.restTimer?.deadline)
        assertEquals("No distance is offered", "", editable("effort-distance"))
        assertEquals("The load is offered from this workout", "24", editable("effort-kg"))
        rule.onNodeWithTag("entry-context").assertTextContains("load from this workout", substring = true)
        rule.onNodeWithTag("effort-count").assertTextEquals("Distance sets: 1")
        assertSaved(repo)
    }

    @Test
    fun aStopwatchPastTwentyFourHoursAsksForTheTimeInstead() {
        val repo = repository()
        launch(repo)
        val plank = startExtraWith(repo, "plank", "plank")
        val started = startWatch(repo)
        elapsed += 86_405_000
        stepTo(repo, "stop-stopwatch", 1)
        assertEquals(86_405_000L, watch(repo)?.elapsedMs)
        rule.onNodeWithTag("stopwatch-review").assertTextEquals("The stopwatch ran 24:00:05, longer than the 24 hours " +
            "one effort can record. Enter the time to log, or discard it.")
        assertEquals("", editable("effort-minutes"))
        assertEquals("", editable("effort-seconds"))
        rule.onNodeWithTag("resume-stopwatch").assertDoesNotExist()
        rule.onNodeWithTag("use-measured-time").assertDoesNotExist()

        tap("log-effort")
        errorShown("Enter the time: minutes, seconds or both.")
        assertTrue(entry(repo, plank.id).efforts.isEmpty())
        assertNotNull("The stopwatch's effort waits for its time", watch(repo))
        type("effort-minutes", "1")
        type("effort-seconds", "30")
        logEffort(repo, plank.id)
        assertEquals(Effort(started.id, 90, null, null, LoadUnit.KG, null, FIXED_NOW), entry(repo, plank.id).efforts.single())
        assertNull(watch(repo))
        assertSaved(repo)
    }

    @Test
    fun discardAsksFirstAndFinishingNeedsTheTickWhileAnEffortIsUnlogged() {
        val repo = repository()
        launch(repo)
        val plank = startExtraWith(repo, "plank", "plank")
        type("effort-seconds", "30")
        logEffort(repo, plank.id)
        hideKeyboard()
        assertNotNull(repo.state.value.restTimer)

        val first = startWatch(repo)
        assertNull("Starting the stopwatch ends the rest", repo.state.value.restTimer)
        elapsed += 12_000
        stepTo(repo, "stop-stopwatch", 1)
        tap("discard-stopwatch")
        assertDialogShows("Discard this timed set?")
        assertDialogShows("Its time won't be logged. Nothing else changes. This can't be undone.")
        rule.onNodeWithTag("dialog-cancel").performClick()
        rule.waitForIdle()
        assertEquals("Cancel keeps it", 1, watch(repo)?.step)
        tap("discard-stopwatch")
        assertDialogShows("Discard this timed set?")
        rule.onNodeWithTag("confirm-button").performClick()
        rule.waitUntil(5_000) { watch(repo) == null }
        rule.waitForIdle()
        assertEquals("Discarding logs nothing and removes nothing", 1, entry(repo, plank.id).efforts.size)
        assertNull(repo.state.value.restTimer)
        assertSaved(repo)

        val second = startWatch(repo)
        assertNotEquals(first.id, second.id)
        elapsed += 8_000
        stepTo(repo, "pause-stopwatch", 1)
        clickFinish()
        assertDialogShows("Finish extra workout")
        rule.onNodeWithTag("finish-stopwatch").assertTextEquals("The stopwatch on Plank holds an effort that isn't " +
            "logged (paused at 0:08). Log it there first, or tick below to finish without it.")
        rule.onNodeWithTag("confirm-button").assertIsNotEnabled()
        rule.onNodeWithTag("finish-discard-stopwatch").performClick()
        rule.onNodeWithTag("confirm-button").assertIsEnabled().performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkoutId == null }
        rule.waitForIdle()
        val finished = repo.state.value.workouts.single().exercises.single()
        assertEquals("Only the logged effort is saved", listOf(30), finished.efforts.map { it.durationSeconds })
        assertNull(watch(repo))
        assertSaved(repo)
        rule.onNodeWithTag("stopwatch-bar").assertDoesNotExist()
    }

    @Test
    fun theStopwatchStaysOneTapAwayOnAnotherExerciseAndOutsideTheWorkout() {
        val repo = repository()
        launch(repo)
        val plank = startExtraWith(repo, "plank", "plank")
        val run = addExercise(repo, "treadmill", "treadmill")
        chip(0).performClick()
        rule.waitForIdle()
        startWatch(repo)
        elapsed += 5_000

        // While it times, another exercise offers the way back and Pause instead of an entry.
        chip(1).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("stopwatch-elsewhere").assertExists()
        rule.onNodeWithTag("log-effort").assertDoesNotExist()
        rule.onNodeWithTag("go-to-stopwatch").assertTextContains("Go to Plank")
        tap("pause-stopwatch-elsewhere")
        rule.waitUntil(5_000) { watch(repo)?.step == 1 }
        rule.waitForIdle()
        assertEquals(5_000L, watch(repo)?.elapsedMs)
        // Paused, this exercise can be logged by typing, and the stopwatch stays in sight.
        rule.onNodeWithTag("start-stopwatch").assertDoesNotExist()
        rule.onNodeWithTag("go-to-stopwatch-entry").assertExists()
        rule.onNodeWithTag("stopwatch-strip").assertTextContains("Plank · stopwatch paused at 0:05", substring = true)
        type("effort-minutes", "5")
        logEffort(repo, run.id)
        assertEquals(300, entry(repo, run.id).efforts.single().durationSeconds)
        assertEquals("The paused stopwatch is untouched", 1, watch(repo)?.step)
        hideKeyboard()

        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("stopwatch-bar").assertIsDisplayed()
        rule.onNodeWithTag("opens-line").performScrollTo().assertTextEquals("Opens Plank · stopwatch paused at 0:05")
        val barTime = rule.onNode(hasTestTag("stopwatch-time") and hasAnyAncestor(hasTestTag("stopwatch-bar")))
        barTime.assertTextEquals("Stopwatch paused at 0:05")
        tap("bar-resume-stopwatch")
        rule.waitUntil(5_000) { watch(repo)?.step == 2 }
        elapsed += 3_000
        rule.waitUntil(5_000) {
            description(rule.onNode(hasTestTag("stopwatch-time") and hasAnyAncestor(hasTestTag("stopwatch-bar")))) ==
                "Stopwatch 8 seconds, timing"
        }
        tap("bar-pause-stopwatch")
        rule.waitUntil(5_000) { watch(repo)?.step == 3 }
        assertEquals(8_000L, watch(repo)?.elapsedMs)
        tap("bar-open-workout")
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("stopwatch-bar")).fetchSemanticsNodes().isEmpty() }
        rule.waitForIdle()
        if (rule.onAllNodes(hasTestTag("stopwatch-strip")).fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithTag("stopwatch-strip").performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithTag("stopwatch-time").assert(hasContentDescription("Stopwatch 8 seconds, paused"))
        rule.onNodeWithTag("stopwatch-strip").assertDoesNotExist()
        assertEquals(plank.id, watch(repo)?.exerciseId)
        assertSaved(repo)
    }

    @Test
    fun aRunningStopwatchAndTypedFieldsSurviveRecreation() {
        val repo = repository()
        val restoration = StateRestorationTester(rule)
        launch(repo, restoration)
        val carry = startExtraWith(repo, "farmer", "farmers-carry")
        type("effort-distance", "30")
        type("effort-kg", "20")
        hideKeyboard()
        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        assertEquals("30", editable("effort-distance"))
        assertEquals("20", editable("effort-kg"))

        val started = startWatch(repo)
        elapsed += 7_000
        restoration.emulateSavedInstanceStateRestore()
        rule.waitUntil(5_000) { description(rule.onNodeWithTag("stopwatch-time")) == "Stopwatch 7 seconds, timing" }
        assertEquals("Recreation changes nothing that is saved", started, watch(repo))
        elapsed += 2_000
        stepTo(repo, "stop-stopwatch", 1)
        assertEquals(listOf("30", "0", "9", "20"),
            listOf("effort-distance", "effort-minutes", "effort-seconds", "effort-kg").map(::editable))

        // A typed-over review survives too, and the measured time stays one tap away.
        type("effort-seconds", "10")
        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        assertEquals("10", editable("effort-seconds"))
        rule.onNodeWithTag("stopwatch-review").assertTextEquals("The stopwatch measured 0:09. Your typed time is logged instead.")
        rule.onNodeWithTag("use-measured-time").assertTextContains("Use the measured 0:09")
        tap("use-measured-time")
        assertEquals("9", editable("effort-seconds"))
        logEffort(repo, carry.id)
        assertEquals(Effort(started.id, 9, Distance(30_000, DistanceUnit.M), 20.0, LoadUnit.KG, null, FIXED_NOW),
            entry(repo, carry.id).efforts.single())
        assertSaved(repo)
    }

    @Test
    fun aRunningTimeThatCantBeConfirmedAfterARestartIsEnteredByTheUser() {
        lateinit var workoutId: String
        val repo = repository {
            execute(Action.StartExtraWorkout(FIXED_NOW - 600_000))
            workoutId = requireNotNull(state.value.activeWorkoutId)
            execute(Action.AddWorkoutExercise(workoutId, "e-plank", "plank"))
            // Timed in an earlier app process on a phone that doesn't report its boot count.
            val before = Monotonic(null, "earlier-process", 500_000)
            execute(Action.StartStopwatch("sw-restart", workoutId, "e-plank", before, FIXED_NOW - 300_000))
            execute(Action.PauseStopwatch("sw-restart", 0, before.copy(elapsedMs = 520_000)))
            execute(Action.ResumeStopwatch("sw-restart", 1, before.copy(elapsedMs = 530_000)))
        }
        boot = null
        session = "new-process"
        elapsed = 2_000
        launch(repo)
        rule.waitUntil(5_000) { watch(repo)?.needsConfirmation == true }
        rule.waitForIdle()
        assertEquals("Nothing is estimated: only the 20 seconds measured before it last resumed are kept",
            Stopwatch("sw-restart", workoutId, "e-plank", 20_000, null, null, null, stopped = true,
                needsConfirmation = true, startedAt = FIXED_NOW - 300_000, step = 3), watch(repo))
        assertSaved(repo)
        rule.onNodeWithTag("opens-line").performScrollTo().assertTextEquals("Opens Plank · stopwatch time to enter")
        rule.onNodeWithText("Resume workout").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("stopwatch-review").assertTextEquals("The stopwatch couldn't confirm its running time after " +
            "a restart, so it stopped without one. It started at ${formatTime(FIXED_NOW - 300_000)}. 0:20 was timed " +
            "before it last resumed. Enter the time, or discard it.")
        rule.onNodeWithTag("entry-context").assertTextContains("enter its time", substring = true)
        assertEquals("", editable("effort-minutes"))
        assertEquals("", editable("effort-seconds"))
        rule.onNodeWithTag("resume-stopwatch").assertDoesNotExist()

        type("effort-minutes", "2")
        logEffort(repo, "e-plank")
        assertEquals(Effort("sw-restart", 120, null, null, LoadUnit.KG, null, FIXED_NOW),
            entry(repo, "e-plank").efforts.single())
        assertNull(watch(repo))
        assertEquals(FIXED_NOW + 90_000, repo.state.value.restTimer?.deadline)
        assertSaved(repo)
    }

    @Test
    fun aMissedEffortAddedInHistoryStartsNothingAndChartsKeepEachUnitApart() {
        val earlier = FIXED_NOW - 2 * 86_400_000L
        val repo = repository {
            execute(Action.StartExtraWorkout(earlier))
            val w = requireNotNull(state.value.activeWorkoutId)
            execute(Action.AddWorkoutExercise(w, "e-run", "treadmill"))
            execute(Action.SaveEffort(w, "e-run", "run-1", 1_200, Distance(5_000, DistanceUnit.KM), null, earlier + 1_200_000))
            execute(Action.SaveEffort(w, "e-run", "run-2", 600, Distance(1_000, DistanceUnit.MI), null, earlier + 2_000_000))
            execute(Action.FinishWorkout(earlier + 3_600_000))
        }
        launch(repo)
        rule.onNodeWithText("History").performClick()
        rule.onNodeWithText(EXTRA_WORKOUT_NAME).performClick()
        rule.onNodeWithContentDescription("Options for Treadmill").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Add missed effort for Treadmill").performClick()
        assertDialogShows("Add a missed effort")
        assertDialogShows("It is added after the others and starts no rest or stopwatch.")
        rule.onNodeWithTag("distance-unit-MI").assertIsSelected()
        rule.onNodeWithTag("edit-effort-minutes").performTextReplacement("5")
        rule.onNodeWithTag("save-effort").performClick()
        rule.waitUntil(5_000) { repo.state.value.workouts.single().exercises.single().efforts.size == 3 }
        rule.waitForIdle()
        val finished = repo.state.value.workouts.single()
        assertEquals("Dated with the workout, after the others",
            Effort(finished.exercises.single().efforts.last().id, 300, null, null, LoadUnit.KG, null, earlier + 3_600_000),
            finished.exercises.single().efforts.last())
        assertNull(repo.state.value.restTimer)
        assertNull(watch(repo))
        assertNull(repo.state.value.activeWorkoutId)
        assertSaved(repo)

        rule.onNodeWithContentDescription("Treadmill, ", substring = true).performScrollTo().performClick()
        rule.onNodeWithTag("measured-history-note").assertTextEquals(
            "Each effort exactly as logged. Nothing unrecorded counts as zero.")
        listOf("effort-chart-time", "effort-chart-distance-KM", "effort-chart-distance-MI").forEach {
            rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag(it))
            rule.onNodeWithTag(it).assertIsDisplayed()
        }
        rule.onNodeWithTag("effort-chart-distance-M").assertDoesNotExist()
        rule.onNodeWithTag("effort-chart-load").assertDoesNotExist()
    }

    @Test
    fun largeTextMeasuredEntryKeepsItsFieldsAndActionsWholeWithTheKeyboard() {
        systemFontScale(2f)
        val repo = repository {
            execute(Action.StartExtraWorkout(FIXED_NOW))
            val w = requireNotNull(state.value.activeWorkoutId)
            execute(Action.AddWorkoutExercise(w, "e-carry", "farmers-carry"))
            execute(Action.SaveEffort(w, "e-carry", "carry-1", 50, Distance(40_000, DistanceUnit.M), 24.0, FIXED_NOW))
        }
        launch(repo)
        rule.onNodeWithText("Resume workout").performScrollTo().performClick()
        rule.waitForIdle()
        assertNotNull("A rest is running under the entry", repo.state.value.restTimer)
        listOf("effort-distance", "distance-unit-M", "distance-unit-KM", "distance-unit-MI", "effort-minutes",
            "effort-seconds", "effort-kg", "log-effort", "start-stopwatch").forEach(::assertWhole)
        val distance = rule.onNodeWithTag("effort-distance").fetchSemanticsNode().boundsInRoot
        val minutes = rule.onNodeWithTag("effort-minutes").fetchSemanticsNode().boundsInRoot
        assertTrue("Large text stacks the fields: $distance $minutes", distance.bottom <= minutes.top + 1)

        // Typing each field keeps it and Log whole above the keyboard and the rest bar.
        for (tag in listOf("effort-distance", "effort-seconds", "effort-kg")) {
            scrolled(tag).performClick()
            waitForSettledIme()
            val imeTop = imeTopOnScreen()
            Log.i("RebuildMeasured", "large text $tag with IME: imeTop=$imeTop ime=$imeBottom field=${onScreen(tag)} " +
                "log=${onScreen("log-effort")}")
            assertWholeAboveKeyboard(tag, imeTop)
        }
        rule.onNodeWithTag("effort-distance").performTextReplacement("35")
        rule.onNodeWithTag("effort-seconds").performTextReplacement("41")
        rule.onNodeWithTag("effort-kg").performTextReplacement("26")
        rule.onNodeWithTag("effort-kg").performImeAction()
        rule.waitUntil(5_000) { entry(repo, "e-carry").efforts.size == 2 }
        assertEquals(Effort(entry(repo, "e-carry").efforts.last().id, 41, Distance(35_000, DistanceUnit.M), 26.0,
            LoadUnit.KG, null, FIXED_NOW), entry(repo, "e-carry").efforts.last())
        hideKeyboard()

        startWatch(repo)
        assertTextWhole("stopwatch-time")
        listOf("pause-stopwatch", "stop-stopwatch").forEach(::assertWhole)
        elapsed += 33_000
        stepTo(repo, "stop-stopwatch", 1)
        assertTextWhole("stopwatch-review")
        listOf("effort-seconds", "log-effort", "resume-stopwatch", "discard-stopwatch").forEach(::assertWhole)
        assertSaved(repo)
    }

    @Test
    fun landscapeMeasuredEntryKeepsItsUnitChoiceAndActionsWholeWithTheKeyboard() {
        val repo = repository {
            execute(Action.StartExtraWorkout(FIXED_NOW))
            val w = requireNotNull(state.value.activeWorkoutId)
            execute(Action.AddWorkoutExercise(w, "e-carry", "farmers-carry"))
            execute(Action.AddWorkoutExercise(w, "e-run", "treadmill"))
        }
        landscape()
        launch(repo)
        rule.onNodeWithText("Resume workout").performScrollTo().performClick()
        rule.waitForIdle()
        chip(0).performClick()
        rule.waitForIdle()
        listOf("effort-distance", "effort-minutes", "effort-seconds", "effort-kg", "log-effort", "start-stopwatch",
            "distance-unit-M", "distance-unit-KM", "distance-unit-MI").forEach(::assertWhole)
        tap("distance-unit-KM")
        rule.onNodeWithTag("distance-unit-KM").assertIsSelected()

        for (tag in listOf("effort-distance", "effort-kg")) {
            scrolled(tag).performClick()
            waitForSettledIme()
            val imeTop = imeTopOnScreen()
            Log.i("RebuildMeasured", "landscape $tag with IME: imeTop=$imeTop ime=$imeBottom field=${onScreen(tag)} " +
                "log=${onScreen("log-effort")}")
            assertWholeAboveKeyboard(tag, imeTop)
            assertWholeAboveKeyboard("log-effort", imeTop)
        }
        rule.onNodeWithTag("effort-distance").performTextReplacement("0.05")
        rule.onNodeWithTag("effort-kg").performTextReplacement("22")
        rule.onNodeWithTag("effort-kg").performImeAction()
        rule.waitUntil(5_000) { entry(repo, "e-carry").efforts.size == 1 }
        assertEquals(Effort(entry(repo, "e-carry").efforts.single().id, null, Distance(50, DistanceUnit.KM), 22.0,
            LoadUnit.KG, null, FIXED_NOW), entry(repo, "e-carry").efforts.single())
        hideKeyboard()

        // Cardio's distance comes after its time; its unit is chosen right there too.
        chip(1).performClick()
        rule.waitForIdle()
        listOf("effort-minutes", "effort-seconds", "effort-distance", "distance-unit-M", "distance-unit-KM",
            "distance-unit-MI", "log-effort").forEach(::assertWhole)
        rule.onNodeWithTag("distance-unit-KM").assertIsSelected()
        tap("distance-unit-MI")
        scrolled("effort-distance").performClick()
        waitForSettledIme()
        assertWholeAboveKeyboard("effort-distance", imeTopOnScreen())
        assertWholeAboveKeyboard("distance-unit-MI", imeTopOnScreen())
        rule.onNodeWithTag("effort-distance").performTextReplacement("2.5")
        rule.onNodeWithTag("effort-distance").performImeAction()
        rule.waitUntil(5_000) { entry(repo, "e-run").efforts.size == 1 }
        assertEquals(Effort(entry(repo, "e-run").efforts.single().id, null, Distance(2_500, DistanceUnit.MI), null,
            LoadUnit.KG, null, FIXED_NOW), entry(repo, "e-run").efforts.single())
        hideKeyboard()

        chip(0).performClick()
        rule.waitForIdle()
        startWatch(repo)
        assertTextWhole("stopwatch-time")
        listOf("pause-stopwatch", "stop-stopwatch").forEach(::assertWhole)
        elapsed += 61_000
        stepTo(repo, "stop-stopwatch", 1)
        listOf("effort-minutes", "effort-seconds", "log-effort", "resume-stopwatch", "discard-stopwatch")
            .forEach(::assertWhole)
        assertEquals(listOf("1", "1"), listOf("effort-minutes", "effort-seconds").map(::editable))
        assertSaved(repo)
    }
}
