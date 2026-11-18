package com.rebuild.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule

/**
 * Whether the app's input guard is open (no save running, no double-tap cooldown). Controls no longer grey out for a
 * normal save, so `isEnabled` alone doesn't say a tap would be taken; tests that wait for one check this too.
 */
internal fun ComposeTestRule.actionGuardOpen(): Boolean =
    onAllNodes(SemanticsMatcher.expectValue(ActionGuard, false)).fetchSemanticsNodes().isNotEmpty()

