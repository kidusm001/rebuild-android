package com.rebuild.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.rebuild.domain.Exercise
import com.rebuild.domain.LoggedSet
import com.rebuild.domain.SetGroup
import com.rebuild.domain.SetMethod
import com.rebuild.domain.WorkEntry
import com.rebuild.ui.theme.RebuildTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** A logged set's value never runs under its pencil, however wide it is for the cell (issue #44). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LoggedRowLayoutTest {
    @get:Rule val compose = createComposeRule()

    private val press = Exercise("press", "Machine chest press")
    private val wide = LoggedSet("s1", 31.25, 12, null, false, 1)

    // Narrow enough that "31.25 kg × 12" fills the space beside the pencil, even in Robolectric's narrow font.
    private val cell = 120.dp

    private fun assertGapBeforePencil(text: String) {
        val value = compose.onNodeWithText(text, substring = true, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val pencil = compose.onNodeWithTag("edit-mark", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("value ends at ${value.right}, pencil starts at ${pencil.left}", pencil.left - value.right >= 8.dp)
    }

    @Test fun aWideSetLeavesAGapBeforeItsPencil() {
        compose.setContent {
            RebuildTheme {
                Box(Modifier.width(cell)) {
                    TodayCell(wide, "Set 1", extra = false, exercise = press, stacked = false, onEdit = {})
                }
            }
        }
        assertGapBeforePencil("31.25")
    }

    @Test fun aWideSetWithDropsLeavesAGapBeforeItsPencil() {
        val drop = LoggedSet("s2", 22.5, 8, null, false, 2, groupId = "g")
        val group = WorkEntry.Group(SetGroup("g", SetMethod.DROP, finishedAt = 3), listOf(wide.copy(groupId = "g"), drop))
        compose.setContent {
            RebuildTheme {
                Box(Modifier.width(cell)) { GroupRow(group, "Set 1", press, extra = false, onOpen = {}) }
            }
        }
        assertGapBeforePencil("31.25")
    }
}
