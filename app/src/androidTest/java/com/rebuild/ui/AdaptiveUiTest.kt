package com.rebuild.ui

import android.view.WindowManager
import android.view.KeyEvent
import android.graphics.Bitmap
import android.util.Log
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rebuild.domain.Action
import com.rebuild.domain.LoadUnit
import com.rebuild.domain.Phase
import com.rebuild.domain.RestTimer
import com.rebuild.domain.SetMethod
import kotlinx.coroutines.runBlocking
import com.rebuild.ui.theme.RebuildTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AdaptiveUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun matchProductionWindow() {
        rule.activityRule.scenario.onActivity {
            it.enableEdgeToEdge()
            it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    @After
    fun restoreOrientation() {
        rule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    @Test
    fun largeTextRestStatusLeavesRoomForLogging() {
        val repo = FakeRepository(seedState(Phase.INTRO_ONE).copy(
            restTimer = RestTimer("timer", "workout", "exercise", FIXED_NOW + 180_000)
        ))
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, 2f)) {
                RebuildApp(repo, FakePlatform(PlatformStatus(true, true, true)), clock = { FIXED_NOW })
            }
        }
        val bar = rule.onNodeWithTag("rest-bar").fetchSemanticsNode().boundsInRoot
        assertTrue("Compact rest bar must not consume the workout viewport: ${bar.height / 3} dp",
            bar.height / 3 <= 120)
    }

    @Test
    fun largeTextHeaderDoesNotClipItsSubtitle() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, 2f)) {
                RebuildTheme { ScreenScaffold("Upper", subtitle = "Intro 2") {} }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText("Intro 2").assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val bounds = rule.onNodeWithText("Intro 2").fetchSemanticsNode().boundsInRoot
        assertFalse("Subtitle cannot be ellipsized", layout.isLineEllipsized(0))
        assertTrue("Whole subtitle line must fit visible bounds: $bounds vs ${layout.multiParagraph.height}",
            bounds.height >= layout.multiParagraph.height - 1)
        assertTrue("Subtitle must fit horizontally, allowing pixel rounding",
            layout.getLineRight(0) <= bounds.width + 1)
    }

    @Test
    fun largeTextSafetyActionsDoNotOverlapAndWholeExplanationCanScroll() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, 2f)) {
                RebuildTheme {
                    AdaptiveDialog(
                        onDismissRequest = {},
                        title = { Text("Preview replacement") },
                        text = {
                            Column {
                                Text("Restore replaces all data, never merges. ".repeat(10))
                                Text("Only the sets you logged are saved.")
                            }
                        },
                        confirmButton = { Button({}, modifier = Modifier.testTag("save-safety")) { Text("Save current backup") } },
                        dismissButton = { TextButton({}, modifier = Modifier.testTag("cancel-safety")) { Text("Cancel") } }
                    )
                }
            }
        }
        val save = rule.onNodeWithTag("save-safety").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val cancel = rule.onNodeWithTag("cancel-safety").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Safety actions must have disjoint geometric bounds",
            save.bottom <= cancel.top || cancel.bottom <= save.top || save.right <= cancel.left || cancel.right <= save.left)
        rule.onNodeWithText("Only the sets you logged are saved.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun invalidRepsHasFieldErrorAndPreservesWeight() {
        val repo = FakeRepository(seedState(Phase.INTRO_ONE))
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithTag("start-scheduled").performClick()
        rule.onNodeWithTag("entry-kg").performTextInput("42.5")
        rule.onNodeWithTag("log-set").performScrollTo().performClick()
        rule.onNodeWithTag("entry-reps").assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Enter the reps you completed (1 or more)."))
        assertTrue(repo.actions.none { it is com.rebuild.domain.Action.SaveSet })
        assertTrue(rule.onNodeWithTag("entry-kg").fetchSemanticsNode().config[SemanticsProperties.EditableText].text == "42.5")
    }

    @Test
    fun largeTextNumericImeKeepsWholeEntryAndRestActionsVisible() {
        assertImeEntryGeometry(2f)
    }

    @Test
    fun landscapeNumericImeKeepsWholeEntryAndRestActionsVisible() {
        rule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        rule.waitUntil(5_000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        matchProductionWindow()
        assertImeEntryGeometry(1f)
    }

    @OptIn(ExperimentalLayoutApi::class)
    private fun assertImeEntryGeometry(fontScale: Float) {
        var imeBottom = 0
        var imeTarget = 0
        val repo = FakeRepository(seedState(Phase.INTRO_ONE).copy(
            restTimer = RestTimer("timer", "workout", "exercise", FIXED_NOW + 180_000)
        ))
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                val bottom = WindowInsets.ime.getBottom(LocalDensity.current)
                val target = WindowInsets.imeAnimationTarget.getBottom(LocalDensity.current)
                SideEffect { imeBottom = bottom; imeTarget = target }
                RebuildApp(repo, FakePlatform(PlatformStatus(false, false, true)), clock = { FIXED_NOW })
            }
        }
        rule.onNodeWithTag("start-scheduled").performScrollTo().performClick()
        rule.onNodeWithTag("entry-kg").performScrollTo().performClick()
        // Typing while the keyboard still slides in can crash Compose 1.8's scroll-into-view (two animations at once).
        rule.waitUntil(5_000) { imeBottom > 0 && imeBottom == imeTarget }
        rule.onNodeWithTag("entry-kg").performTextInput("42.5")
        rule.waitUntil(5_000) { imeBottom > 0 && imeBottom == imeTarget }
        rule.waitForIdle()
        val rest = rule.onNodeWithTag("rest-bar").fetchSemanticsNode()
        listOf("entry-kg", "entry-reps", "entry-rir", "log-set").forEach { tag ->
            val node = rule.onNodeWithTag(tag).fetchSemanticsNode()
            if (node.size.height - node.boundsInRoot.height > 1) {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                File(instrumentation.targetContext.getExternalFilesDir(null), "ime-clipping.png")
                    .outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
                Log.e("RebuildGeometry", "$tag size=${node.size}, visible=${node.boundsInRoot}, " +
                    "parent=${node.parent?.boundsInRoot}, rest=${rest.boundsInRoot}, " +
                    "ime=$imeBottom, target=$imeTarget, configuration=${rule.activity.resources.configuration}")
            }
            assertEquals("$tag must be fully visible, not clipped by scrolling or rest", node.size.height.toFloat(),
                node.boundsInRoot.height, 1f)
            assertTrue("$tag must be above rest", node.boundsInRoot.bottom <= rest.boundsInRoot.top + 1)
        }
        listOf("Add 30 seconds", "Skip rest", "Rest options").forEach { label ->
            val node = rule.onNodeWithContentDescription(label).assertIsDisplayed().fetchSemanticsNode()
            assertTrue("$label needs a 48dp hit region", node.touchBoundsInRoot.width >= 144 && node.touchBoundsInRoot.height >= 144)
        }
        if (fontScale == 1f) {
            val context = rule.onNodeWithTag("compact-entry-context").assertIsDisplayed().fetchSemanticsNode()
            assertEquals("Exercise/loading context: size=${context.size}, bounds=${context.boundsInRoot}, " +
                "parent=${context.parent?.boundsInRoot}", context.size.height.toFloat(),
                context.boundsInRoot.height, 1f)
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitUntil(5_000) { imeBottom == 0 }
        rule.onNodeWithTag("exercise-picker").assertIsDisplayed()
        assertEquals("42.5", rule.onNodeWithTag("entry-kg").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertTrue(repo.actions.none { it is com.rebuild.domain.Action.SaveSet || it is com.rebuild.domain.Action.AdjustRest })
    }

    @Test
    fun largeTextImeKeepsAnOpenSetsEntryVisibleAndItsActionsReachable() {
        assertOpenSetImeGeometry(2f)
    }

    @Test
    fun landscapeImeKeepsAnOpenSetsEntryVisibleAndItsActionsReachable() {
        rule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        rule.waitUntil(5_000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        matchProductionWindow()
        assertOpenSetImeGeometry(1f)
    }

    @Test
    fun pinnedDockImeKeepsAnOpenSetsEntryVisibleAndItsActionsReachable() {
        assertOpenSetImeGeometry(1f)
    }

    /**
     * A rest-pause set in progress during its short pause: the fields and the next stage's Log stay whole above the
     * pause bar with the keyboard up, and Finish set and the pause control are reachable, 48 dp and never overlap.
     */
    @OptIn(ExperimentalLayoutApi::class)
    private fun assertOpenSetImeGeometry(fontScale: Float) {
        var imeBottom = 0
        var imeTarget = 0
        val repo = FakeRepository(seedState(Phase.INTRO_ONE))
        runBlocking {
            repo.execute(Action.StartWorkout("upper-a", FIXED_NOW - 600_000))
            val workout = requireNotNull(repo.state.value.activeWorkout)
            val bench = workout.exercises.first()
            repo.execute(Action.LogStage(workout.id, bench.id, "rp", SetMethod.REST_PAUSE, "rp-0", 40.0, 8, null,
                FIXED_NOW - 30_000, LoadUnit.KG, null))
            repo.execute(Action.StartShortPause(workout.id, bench.id, SHORT_PAUSE_SECONDS, FIXED_NOW))
        }
        val seeded = repo.actions.toList()
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                val bottom = WindowInsets.ime.getBottom(LocalDensity.current)
                val target = WindowInsets.imeAnimationTarget.getBottom(LocalDensity.current)
                SideEffect { imeBottom = bottom; imeTarget = target }
                RebuildApp(repo, FakePlatform(PlatformStatus(false, false, true)), clock = { FIXED_NOW })
            }
        }
        rule.onNodeWithText("Resume workout").performScrollTo().performClick()
        rule.onNodeWithTag("log-set").performScrollTo().assertTextContains("Add mini-set")
        rule.onNodeWithTag("entry-reps").performScrollTo().performClick()
        // The keyboard settles before the field changes, as in assertImeEntryGeometry.
        rule.waitUntil(5_000) { imeBottom > 0 && imeBottom == imeTarget }
        rule.onNodeWithTag("entry-reps").performTextReplacement("4")
        rule.waitUntil(5_000) { imeBottom > 0 && imeBottom == imeTarget }
        rule.waitForIdle()
        val rest = rule.onNodeWithTag("rest-bar").fetchSemanticsNode()
        rule.onNodeWithTag("rest-status").assertTextContains("Pause", substring = true)
        listOf("entry-kg", "entry-reps", "entry-rir", "log-set").forEach { tag ->
            val node = rule.onNodeWithTag(tag).fetchSemanticsNode()
            assertEquals("$tag must be fully visible while typing: size=${node.size}, bounds=${node.boundsInRoot}, " +
                "rest=${rest.boundsInRoot}, ime=$imeBottom", node.size.height.toFloat(), node.boundsInRoot.height, 1f)
            assertTrue("$tag must be above the pause bar", node.boundsInRoot.bottom <= rest.boundsInRoot.top + 1)
        }
        val actions = listOf("log-set", "finish-group", "short-pause")
        actions.forEach { tag ->
            val node = rule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().fetchSemanticsNode()
            assertEquals("$tag must scroll fully into view: size=${node.size}, bounds=${node.boundsInRoot}",
                node.size.height.toFloat(), node.boundsInRoot.height, 1f)
            assertTrue("$tag must be above the pause bar: ${node.boundsInRoot} vs ${rest.boundsInRoot}",
                node.boundsInRoot.bottom <= rest.boundsInRoot.top + 1)
            assertTrue("$tag needs a 48dp hit region: ${node.touchBoundsInRoot}",
                node.touchBoundsInRoot.height >= 144 && node.touchBoundsInRoot.width >= 144)
        }
        // Unclipped layout positions in one scroll state: the actions never overlap, visible or not.
        val placed = actions.associateWith { tag ->
            val node = rule.onNodeWithTag(tag).fetchSemanticsNode()
            val origin = node.positionInRoot
            Rect(origin.x, origin.y, origin.x + node.size.width, origin.y + node.size.height)
        }
        placed.entries.forEach { (a, ra) ->
            placed.entries.filter { it.key > a }.forEach { (b, rb) ->
                assertFalse("$a $ra and $b $rb must not overlap", ra.intersect(rb).let { it.width > 1 && it.height > 1 })
            }
        }
        listOf("Add 30 seconds", "Skip rest", "Rest options").forEach { label ->
            val node = rule.onNodeWithContentDescription(label).assertIsDisplayed().fetchSemanticsNode()
            assertTrue("$label needs a 48dp hit region", node.touchBoundsInRoot.width >= 144 && node.touchBoundsInRoot.height >= 144)
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitUntil(5_000) { imeBottom == 0 }
        rule.waitForIdle()
        assertEquals("4", rule.onNodeWithTag("entry-reps").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertEquals("Checking the layout must not log, finish or change the pause", seeded, repo.actions)
    }

    @Test
    fun targetCompletionPrioritizesNextButExtraSetsRemainTruthful() {
        val repo = FakeRepository(seedState(Phase.INTRO_TWO))
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithTag("start-scheduled").performClick()
        rule.onNodeWithTag("entry-kg").performTextInput("40")
        rule.onNodeWithTag("entry-reps").performTextInput("8")
        rule.onNodeWithTag("log-set").performScrollTo().performClick()
        rule.waitUntil { repo.state.value.workouts.single().exercises.first().sets.size == 1 }
        rule.waitUntil { SemanticsProperties.Disabled !in rule.onNodeWithTag("log-set").fetchSemanticsNode().config }
        rule.onNodeWithTag("log-set").performScrollTo().performClick()
        rule.waitUntil { repo.state.value.workouts.single().exercises.first().sets.size == 2 }
        rule.onNodeWithTag("next-exercise").performScrollTo().assertIsDisplayed()
        assertEquals(2, repo.state.value.workouts.single().exercises.first().sets.size)
        rule.onNodeWithTag("extra-set").performScrollTo().performClick()
        rule.onNodeWithTag("log-set").performScrollTo().assertIsDisplayed()
        assertEquals("Revealing an extra entry must not record a set", 2, repo.state.value.workouts.single().exercises.first().sets.size)
    }
}
