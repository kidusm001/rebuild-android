package com.rebuild.ui

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.rebuild.ui.theme.RebuildTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Next keeps "Next: name" on one line when it fits, and only a long name moves it onto a row of its own. */
@RunWith(RobolectricTestRunner::class)
class NextButtonLayoutTest {
    @get:Rule val compose = createComposeRule()

    private fun nextAt(width: Dp, name: String) = compose.setContent {
        RebuildTheme {
            Button(onClick = {}, contentPadding = NextButtonPadding, modifier = Modifier.width(width).testTag("next")) {
                NextExerciseLabel(name)
            }
        }
    }

    private fun skippedRow(width: Dp, name: String) = compose.setContent {
        RebuildTheme {
            androidx.compose.foundation.layout.Box(Modifier.width(width)) {
                SideBySideOrStacked(
                    gap = 12.dp,
                    first = { OutlinedButton(onClick = {}, modifier = it.testTag("undo")) { Text("Undo skip") } },
                    second = {
                        Button(onClick = {}, contentPadding = NextButtonPadding, modifier = it.testTag("next")) {
                            NextExerciseLabel(name)
                        }
                    }
                )
            }
        }
    }

    // Robolectric's stand-in font is far narrower than a phone's, so "too long" has to be very long here.
    private val long = "Overhead cable triceps extension with a rope attachment at the low pulley, kneeling. ".repeat(3).trim()

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    @Test fun aNameThatFitsStaysOnOneLine() {
        nextAt(380.dp, "Seated cable row")
        compose.onNodeWithTag("next").assertTextEquals("Next: Seated cable row")
        // One line keeps Button's own height: no second line stacked inside it.
        assertTrue(bounds("next").height < 56.dp)
    }

    @Test fun aLongNameStacksUnderNextAndIsStillReadAsOnePhrase() {
        nextAt(200.dp, long)
        compose.onNodeWithTag("next").assertTextEquals("Next: $long")
        assertTrue("stacked label is taller: ${bounds("next").height}", bounds("next").height > 56.dp)
    }

    @Test fun undoSitsBesideNextAtTheSameHeightWhenTheNameFits() {
        skippedRow(320.dp, "Lat pulldown")
        val undo = bounds("undo"); val next = bounds("next")
        assertEquals(undo.top, next.top)
        assertEquals(undo.height, next.height)
        assertTrue(undo.right < next.left)
    }

    @Test fun aLongNameGivesNextItsOwnFullWidthRowAboveUndo() {
        skippedRow(320.dp, long)
        val undo = bounds("undo"); val next = bounds("next")
        assertTrue("Next above Undo: $next $undo", next.bottom <= undo.top)
        assertEquals(320.dp, next.width)
        compose.onNodeWithTag("next").assertTextEquals("Next: $long")
    }
}
