package com.rebuild.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.ComposeTestRule

/**
 * Whether the app's input guard is open (no save running, no double-tap cooldown). Controls no longer grey out for a
 * normal save, so `isEnabled` alone doesn't say a tap would be taken; tests that wait for one check this too.
 */
internal fun ComposeTestRule.actionGuardOpen(): Boolean =
    onAllNodes(SemanticsMatcher.expectValue(ActionGuard, false)).fetchSemanticsNodes().isNotEmpty()

/**
 * A finished workout ends on its receipt, an in-app sheet that makes the app behind it inert (hidden from tests and
 * screen readers too). Closes it with Done when it is up; a discard shows none, so then this does nothing.
 */
internal fun ComposeTestRule.closeReceiptIfShown() {
    waitForIdle()
    if (onAllNodes(hasTestTag("receipt-done")).fetchSemanticsNodes().isEmpty()) return
    onNodeWithTag("receipt-done").performScrollTo().performClick()
    waitForIdle()
}
