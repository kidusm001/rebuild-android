package com.rebuild.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.rebuild.ui.theme.RebuildTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** History detail's and Plan's stat tiles sit side by side, and stack full width above 130% font scale. */
@RunWith(RobolectricTestRunner::class)
class StatTilesLayoutTest {
    @get:Rule val compose = createComposeRule()

    private fun tilesAt(fontScale: Float) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            RebuildTheme {
                Box(Modifier.width(300.dp).testTag("box")) {
                    StatTiles { tile ->
                        StatTile("52", "Minutes", tile.testTag("minutes"))
                        StatTile("14", "Working sets", tile.testTag("working"), emphasized = true)
                        StatTile("5", "Exercises", tile.testTag("exercises"))
                    }
                }
            }
        }
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    @Test fun atDefaultTextTheTilesShareOneRow() {
        tilesAt(1f)
        val (minutes, working, exercises) = listOf("minutes", "working", "exercises").map(::bounds)
        assertEquals(minutes.top, working.top)
        assertEquals(working.top, exercises.top)
        assertTrue(minutes.right <= working.left && working.right <= exercises.left)
    }

    @Test fun atThirtyPercentLargerTheTilesStillShareOneRow() {
        tilesAt(1.3f)
        assertEquals(bounds("minutes").top, bounds("exercises").top)
    }

    @Test fun atLargeTextEachTileTakesTheFullWidth() {
        tilesAt(2f)
        val (minutes, working, exercises) = listOf("minutes", "working", "exercises").map(::bounds)
        val full = bounds("box").width
        listOf(minutes, working, exercises).forEach { assertEquals(full, it.width) }
        assertTrue(minutes.bottom <= working.top && working.bottom <= exercises.top)
    }
}
