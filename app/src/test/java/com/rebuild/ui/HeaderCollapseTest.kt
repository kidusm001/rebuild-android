package com.rebuild.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.height
import com.rebuild.ui.theme.RebuildTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.launch
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The header gives its room to the body as the body scrolls, and comes back only at the top; collapsed, it keeps Back,
 * its actions and one heading, and nothing faded out is placed, so a screen reader (which skips unplaced nodes) never
 * reads it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h914dp-xxhdpi")
class HeaderCollapseTest {
    @get:Rule val compose = createComposeRule()

    private fun screen(topLevel: Boolean, action: Boolean = false, rtl: Boolean = false) = compose.setContent {
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        RebuildTheme {
            ScreenScaffold(
                title = if (topLevel) "History" else "Bodyweight",
                subtitle = "20 Oct · Baseline",
                onBack = if (topLevel) null else ({}),
                actions = { if (action) SecondaryButton(onClick = {}, modifier = Modifier.testTag("log")) { Text("Log") } }
            ) {
                ScrollBody {
                    Text("First row", Modifier.testTag("first"))
                    Spacer(Modifier.height(3000.dp))
                }
            }
        }
        }
    }

    private fun top(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().top
    private fun subtitleShown() =
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, "screen-subtitle"), useUnmergedTree = true)
            .fetchSemanticsNodes().any { it.layoutInfo.isPlaced }
    private fun headings() =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading), useUnmergedTree = true)
            .fetchSemanticsNodes().size

    private fun scrollDeep() {
        compose.onRoot().performTouchInput { swipeUp(startY = height * 0.8f, endY = height * 0.3f, durationMillis = 400) }
        compose.waitForIdle()
    }

    private fun scrollToTop() {
        repeat(4) { compose.onRoot().performTouchInput { swipeDown(startY = height * 0.3f, endY = height * 0.9f, durationMillis = 300) } }
        compose.waitForIdle()
    }

    @Test fun aTopLevelHeaderShrinksDeepInThePageAndReopensAtTheTop() {
        screen(topLevel = true)
        val bodyTop = top("first")
        assertTrue(subtitleShown())
        scrollDeep()
        assertTrue("the subtitle folds away and isn't left for a screen reader", !subtitleShown())
        assertEquals(1, headings())
        scrollToTop()
        assertTrue(subtitleShown())
        assertEquals(bodyTop, top("first"))
    }

    @Test fun aCollapsedPushedHeaderKeepsBackAndItsAction() {
        screen(topLevel = false, action = true)
        scrollDeep()
        assertTrue(!subtitleShown())
        assertEquals(1, headings())
        val back = compose.onNode(hasContentDescription("Back") and hasClickAction()).fetchSemanticsNode().touchBoundsInRoot
        assertTrue("Back keeps a 48 dp target", back.top >= 0f && back.height >= compose.density.run { 48.dp.toPx() })
        assertTrue(compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, "log")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun atLargeTextTheActionsJoinTheCompactRow() {
        RuntimeEnvironment.setFontScale(2f)
        screen(topLevel = false, action = true)
        scrollDeep()
        assertTrue(!subtitleShown())
        val log = compose.onNodeWithTag("log", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("Log keeps a 48 dp target", log.height >= 48.dp)
        val title = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading), useUnmergedTree = true)
            .fetchSemanticsNodes().single().boundsInRoot
        assertTrue("Log sits in the title's row, not a row below it", compose.density.run { log.top.toPx() } < title.bottom)
    }

    private fun titleBounds() = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading), useUnmergedTree = true)
        .fetchSemanticsNodes().single().boundsInRoot

    @Test fun aRightToLeftHeaderMirrorsBackAndItsActions() {
        screen(topLevel = false, action = true, rtl = true)
        for (collapsed in listOf(false, true)) {
            if (collapsed) scrollDeep()
            val title = titleBounds()
            val back = compose.onNode(hasContentDescription("Back") and hasClickAction()).fetchSemanticsNode().boundsInRoot
            val log = compose.onNodeWithTag("log").fetchSemanticsNode().boundsInRoot
            assertTrue("Back sits at the start, on the right (collapsed=$collapsed)", back.left >= title.right)
            assertTrue("the action sits at the end, on the left (collapsed=$collapsed)", log.right <= title.left)
        }
    }

    @Test fun aRightToLeftTitleShrinksTowardItsStartEdge() {
        screen(topLevel = true, rtl = true)
        val full = titleBounds()
        scrollDeep()
        val compact = titleBounds()
        assertTrue("the title shrinks", compact.width < full.width * 0.9f)
        assertEquals("it keeps its right-hand (start) edge", full.right, compact.right, 1f)
    }

    /** History's shape: a fixed tab row, then a list inside a page of its own. The list must stay below the tabs. */
    @Test fun aListBelowFixedControlsNeverReachesOverThem() {
        var tabTaps = 0
        var rowTaps = 0
        compose.setContent {
            RebuildTheme {
                ScreenScaffold(title = "History") {
                    androidx.compose.material3.Button(onClick = { tabTaps++ }, modifier = Modifier.testTag("tab")) { Text("Exercises") }
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        LazyBody {
                            items(60) { i ->
                                Text("Row $i", Modifier.fillMaxWidth().height(56.dp).clickable { rowTaps++ })
                            }
                        }
                    }
                }
            }
        }
        val tabTop = top("tab")
        scrollDeep()
        compose.onNodeWithTag("tab").performClick()
        compose.waitForIdle()
        assertEquals("the tab takes its own tap", 1, tabTaps)
        assertEquals("no row beneath the tab takes it", 0, rowTaps)
        assertTrue("the tab rises with the header only", top("tab") <= tabTop)
    }

    @Test fun theHeadersEmptySpaceNeverTapsARowBeneathIt() {
        var rowTaps = 0
        var backTaps = 0
        compose.setContent {
            RebuildTheme {
                ScreenScaffold(title = "Push", onBack = { backTaps++ }) {
                    LazyBody {
                        items(60) { i -> Text("Row $i", Modifier.fillMaxWidth().height(56.dp).clickable { rowTaps++ }) }
                    }
                }
            }
        }
        scrollDeep()
        // Right of the title, inside the header: rows are drawn beneath the glass there.
        compose.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.85f, 24.dp.toPx())) }
        compose.waitForIdle()
        assertEquals(0, rowTaps)
        compose.onNode(hasContentDescription("Back") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals(1, backTaps)
    }

    /** Only the body's own scrolling counts the strip under the header as hidden; what's inside it (dialogs) doesn't. */
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Test fun theStripUnderTheHeaderMeansNothingInsideTheBody() {
        val seen = mutableListOf<Any>()
        compose.setContent {
            RebuildTheme {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                        ScreenScaffold(title = "More") {
                            ScrollBody { seen += androidx.compose.foundation.gestures.LocalBringIntoViewSpec.current }
                        }
                    }
                    androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                        ScreenScaffold(title = "Push", onBack = {}) {
                            LazyBody { item { seen += androidx.compose.foundation.gestures.LocalBringIntoViewSpec.current } }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.none { it is UnderHeaderBringIntoView })
    }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Test fun bringingARowIntoViewClearsTheHeader() {
        val requester = androidx.compose.foundation.relocation.BringIntoViewRequester()
        var scope: kotlinx.coroutines.CoroutineScope? = null
        compose.setContent {
            RebuildTheme {
                scope = androidx.compose.runtime.rememberCoroutineScope()
                ScreenScaffold(title = "Push", onBack = {}) {
                    ScrollBody {
                        Text("First row", Modifier.testTag("first"))
                        Text("Target", Modifier.testTag("target").height(56.dp)
                            .bringIntoViewRequester(requester))
                        Spacer(Modifier.height(3000.dp))
                    }
                }
            }
        }
        scrollDeep()
        compose.runOnIdle { scope!!.launch { requester.bringIntoView() } }
        compose.waitForIdle()
        val header = compose.onNode(hasContentDescription("Back") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag("target", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the row lands below the header (${target.top} vs ${header.bottom})", target.top >= header.bottom)
    }

    @Test fun aScreenReaderScrollCollapsesTheHeaderToo() {
        screen(topLevel = true)
        compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 1500f) }
        compose.waitForIdle()
        assertTrue(!subtitleShown())
    }
}

/** How a header left part-way settles: which side, from what speed, and that a new touch takes over from it. */
class HeaderSettleTest {
    /** One 16 ms frame per call, yielding first so a touch can land between frames. */
    private class Frames : androidx.compose.runtime.MonotonicFrameClock {
        var nanos = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            kotlinx.coroutines.yield()
            nanos += 16_000_000L
            return onFrame(nanos)
        }
    }

    private fun header(offset: Float) = HeaderCollapse().apply {
        range = 100f
        connection.onPreScroll(androidx.compose.ui.geometry.Offset(0f, -offset), androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput)
    }

    private fun settle(header: HeaderCollapse, bodyVelocity: Float = 0f, left: Float = 0f) =
        kotlinx.coroutines.runBlocking(Frames()) {
            header.connection.onPostFling(androidx.compose.ui.unit.Velocity(0f, bodyVelocity - left),
                androidx.compose.ui.unit.Velocity(0f, left))
        }

    @Test fun aSlowLiftPastHalfwayCollapses() {
        val header = header(60f)
        settle(header)
        assertEquals(100f, header.offset)
    }

    @Test fun aSlowLiftBeforeHalfwayOpens() {
        val header = header(30f)
        settle(header)
        assertEquals(0f, header.offset)
    }

    @Test fun aGentleFlickThatStoppedPartWayGoesOnFromRest() {
        val header = header(40f)
        val steps = mutableListOf<Float>()
        kotlinx.coroutines.runBlocking(Frames()) {
            val job = launch {
                // Toward the end of the body (negative body velocity), already come to rest: nothing left over.
                header.connection.onPostFling(androidx.compose.ui.unit.Velocity(0f, -800f), androidx.compose.ui.unit.Velocity.Zero)
            }
            var last = header.offset
            while (job.isActive) {
                kotlinx.coroutines.yield()
                steps += header.offset - last
                last = header.offset
            }
        }
        assertEquals("the release's direction picks the side", 100f, header.offset)
        assertTrue("no step backward", steps.all { it >= 0f })
        assertTrue("it eases from rest instead of jumping on at the release speed (first step ${steps.firstOrNull { it > 0f }})",
            (steps.firstOrNull { it > 0f } ?: 0f) < 8f)
    }

    @Test fun aTouchDuringTheSettleTakesOver() {
        val header = header(60f)
        kotlinx.coroutines.runBlocking(Frames()) {
            val job = launch { header.connection.onPostFling(androidx.compose.ui.unit.Velocity.Zero, androidx.compose.ui.unit.Velocity.Zero) }
            repeat(6) { kotlinx.coroutines.yield() }
            assertTrue("the settle is under way before the touch", header.offset > 60f)
            // A finger pulls toward the top while the header settles: the body moves, the settle stops.
            header.connection.onPreScroll(androidx.compose.ui.geometry.Offset(0f, 5f), androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput)
            val held = header.offset
            job.join()
            assertEquals("nothing moves the header after the touch", held, header.offset)
            assertTrue(held < 100f)
        }
    }
}
