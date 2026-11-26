package com.rebuild.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
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

/**
 * `performScrollTo()` lines a row up with the top of its scroll area, and a body that scrolls under the glass header
 * has its top behind the header, where a tap lands on the header and goes no further. This scrolls the row on until it
 * clears the header, as a finger would before tapping it.
 */
internal fun SemanticsNodeInteraction.performScrollClearOfHeader(rule: ComposeTestRule): SemanticsNodeInteraction {
    performScrollTo()
    repeat(3) {
        rule.waitForIdle()
        val header = rule.onAllNodes(hasTestTag("screen-header")).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
            ?: return this
        val node = fetchSemanticsNode()
        val overlap = header.bottom - node.boundsInRoot.top
        if (overlap <= 0f) return this
        var scrollable: SemanticsNode? = node.parent
        while (scrollable != null && scrollable.config.getOrNull(SemanticsActions.ScrollBy) == null) scrollable = scrollable.parent
        val scrollBy = scrollable?.config?.getOrNull(SemanticsActions.ScrollBy)?.action ?: return this
        rule.runOnIdle { scrollBy(0f, -overlap) }
    }
    rule.waitForIdle()
    return this
}
