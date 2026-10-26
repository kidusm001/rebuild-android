package com.rebuild.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rebuild.domain.Action
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose stretches a clipped row's accessibility touch bounds up to 24dp past its scroll edge. A row half scrolled
 * under the top must not take over the lower half of the fixed controls above it in the platform's node bounds.
 */
@RunWith(AndroidJUnit4::class)
class ScrollEdgeAccessibilityTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun edgeToEdge() {
        rule.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
    }

    @After
    fun restoreOrientation() {
        rule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    @Test
    fun planPhaseChipsKeepTheirWholeTargetsOverARowScrolledUnderThem() {
        val repo = MainThreadCollection(introRepository())
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithText("Plan", substring = false).performClick()
        val chips = listOf("Intro 1", "Intro 2", "Five days").map { rule.onNodeWithText(it).fetchSemanticsNode() }
        tuckRowUnderTopEdge(hasScrollAction() and hasAnyDescendant(hasTestTag("plan-routines")), chips)
        chips.forEach { assertWholeProviderTarget(it.config[SemanticsProperties.Text].joinToString(), it) }
    }

    /** In landscape the short body scrolls its full-width rows under the rail. */
    @Test
    fun workoutRailKeepsItsWholeTargetsOverTheBodyScrolledUnderItInLandscape() {
        val repo = MainThreadCollection(introRepository {
            execute(Action.StartWorkout("intro-upper", FIXED_NOW))
            val workout = requireNotNull(state.value.activeWorkout)
            listOf(true, false, false).forEachIndexed { i, warmup ->
                execute(Action.SaveSet(workout.id, workout.exercises.first().id, "chest-$i", 40.0, 10, null, warmup,
                    FIXED_NOW + i))
            }
        })
        rule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        rule.waitUntil(5_000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        rule.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithText("Resume workout").performScrollTo().performClick()
        rule.onNodeWithTag("exercise-details").performScrollTo().performClick()
        val density = rule.activity.resources.displayMetrics.density
        val railPills = SemanticsMatcher("an exercise rail pill") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("exercise-chip-") == true
        }
        val rail = rule.onNodeWithTag("exercise-picker").fetchSemanticsNode().boundsInRoot
        // Placed pills wholly inside the rail: the lazy row also keeps unplaced (prefetched) items in the tree.
        val pills = rule.onAllNodes(railPills).fetchSemanticsNodes().filter {
            val pill = it.boundsInRoot
            it.layoutInfo.isPlaced && pill.width >= 47.5f * density && pill.left >= rail.left - 1 &&
                pill.right <= rail.right + 1 && pill.top >= rail.top - 1 && pill.bottom <= rail.bottom + 1
        }
        Log.i("RebuildAccessibility", "Rail $rail pills ${pills.map { it.config[SemanticsProperties.TestTag] }}")
        assertTrue("At least two pills are fully on screen: ${pills.map { it.boundsInRoot }}", pills.size >= 2)
        tuckRowUnderTopEdge(hasScrollAction() and hasAnyDescendant(hasTestTag("target-line")), pills)
        pills.forEach { assertWholeProviderTarget(it.config[SemanticsProperties.TestTag], it) }
    }

    /**
     * Scrolls a whole clickable row that spans at least one of [controls] 20dp under [scroller]'s top edge: partly
     * hidden, as mid-scroll on the phone. A narrower row would leave each control a whole strip.
     */
    private fun tuckRowUnderTopEdge(scroller: SemanticsMatcher, controls: List<SemanticsNode>) {
        val density = rule.activity.resources.displayMetrics.density
        val tuck = 20 * density
        val container = rule.onNode(scroller).fetchSemanticsNode()
        val edge = container.boundsInRoot.top
        val range = container.config[SemanticsProperties.VerticalScrollAxisRange]
        val room = range.maxValue() - range.value()
        val spans = controls.map { it.boundsInRoot }
        val rows = rule.onAllNodes(hasClickAction() and hasAnyAncestor(scroller)).fetchSemanticsNodes()
            .filter { it.boundsInRoot.top >= edge && it.boundsInRoot.height >= 47.5f * density }
            .filter { row -> spans.any { row.boundsInRoot.left <= it.left + 1 && row.boundsInRoot.right >= it.right - 1 } }
        val row = rows.filter { it.boundsInRoot.top - edge + tuck <= room }.minByOrNull { it.boundsInRoot.top }
            ?: error("No spanning row can be tucked: edge=$edge room=$room rows=${rows.map { it.boundsInRoot }}")
        val before = row.boundsInRoot
        rule.onNode(scroller).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, before.top - edge + tuck) }
        rule.waitForIdle()
        Log.i("RebuildAccessibility", "Tucked row ${row.id} from $before under edge $edge, controls $spans")
        val tucked = rule.onAllNodes(hasClickAction() and hasAnyAncestor(scroller)).fetchSemanticsNodes()
            .single { it.id == row.id }.boundsInRoot
        assertEquals("The row's visible part starts at the scroll edge", edge, tucked.top, 1f)
        assertEquals("20dp of the row is hidden under the edge: $tucked", before.height - tuck, tucked.height, 1.5f)
    }

    private fun assertWholeProviderTarget(label: String, node: SemanticsNode) {
        rule.runOnUiThread {
            val view = requireNotNull(composeView(rule.activity.window.decorView))
            val info = requireNotNull(view.accessibilityNodeProvider?.createAccessibilityNodeInfo(node.id)) {
                "$label has no platform node (${node.id} ${node.boundsInRoot})"
            }
            val rect = Rect().also(info::getBoundsInScreen)
            val minimum = 48 * rule.activity.resources.displayMetrics.density
            Log.i("RebuildAccessibility", "$label provider=$rect semantic=${node.boundsInRoot} minimumPx=$minimum")
            assertTrue("$label keeps its whole height in the platform tree: $rect vs ${node.boundsInRoot}",
                rect.height() >= maxOf(node.boundsInRoot.height, minimum) - 1.5f)
            assertTrue("$label is a whole 48dp target: $rect", rect.width() >= minimum - 1.5f)
        }
    }

    private fun composeView(view: View): View? {
        if (view.javaClass.simpleName == "AndroidComposeView") return view
        if (view is ViewGroup) repeat(view.childCount) { composeView(view.getChildAt(it))?.let { found -> return found } }
        return null
    }
}
