package com.rebuild.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rebuild.domain.Phase
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ActionAffordanceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun restDayOutlinesAndScheduledPrimaryRetainAccessibleActions() {
        rule.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        val now = mutableLongStateOf(FIXED_NOW - 86_400_000)
        val repo = FakeRepository(seedState(Phase.INTRO_TWO))
        var density = 1f
        var dark = false
        rule.setContent {
            density = LocalDensity.current.density
            dark = isSystemInDarkTheme()
            RebuildApp(repo, FakePlatform(PlatformStatus(true, true, false)), clock = { now.longValue })
        }
        rule.onNodeWithTag("start-scheduled").assertDoesNotExist()
        val start = rule.onNodeWithContentDescription("Start Upper A (Tuesday)").assertIsDisplayed().fetchSemanticsNode()
        assertTrue(start.touchBoundsInRoot.height >= 48 * density - 1)
        // Today ends with the routines link outside the plan, below the fold, so scroll to it, then back to the top
        // for the scheduled day.
        val routines = rule.onNodeWithTag("open-routines").performScrollTo().assertIsDisplayed().fetchSemanticsNode()
        assertTrue(routines.touchBoundsInRoot.height >= 48 * density - 1)
        rule.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -100_000f) }
        rule.onNodeWithText("Rest day").assertIsDisplayed()
        rule.runOnIdle { now.longValue = FIXED_NOW }
        rule.onNodeWithTag("start-scheduled").assertIsDisplayed()
        rule.onNodeWithContentDescription("Start Lower A (Thursday)").assertIsDisplayed()
        assertTrue(repo.actions.isEmpty())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = File(instrumentation.targetContext.getExternalFilesDir(null),
            "scheduled-primary-${if (dark) "dark" else "light"}.png")
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}
