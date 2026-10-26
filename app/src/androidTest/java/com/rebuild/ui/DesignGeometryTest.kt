package com.rebuild.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rebuild.domain.Action
import com.rebuild.domain.Phase
import com.rebuild.domain.RestTimer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesignGeometryTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun resetOrientation() {
        rule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    @Test fun portrait360Normal() = verify(360f, 1f)
    @Test fun portrait360Medium() = verify(360f, 1.3f)
    @Test fun portrait360Large() = verify(360f, 2f)
    @Test fun portrait411Normal() = verify(411f, 1f)
    @Test fun portrait411Medium() = verify(411f, 1.3f)
    @Test fun portrait411Large() = verify(411f, 2f)
    @Test fun landscapeNormal() = verify(411f, 1f, landscape = true)

    @OptIn(ExperimentalLayoutApi::class)
    private fun verify(widthDp: Float, fontScale: Float, landscape: Boolean = false) {
        if (landscape) {
            rule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            rule.waitUntil(5_000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        }
        rule.activityRule.scenario.onActivity {
            it.enableEdgeToEdge()
            it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val density = 1080f / widthDp
        var ime = 0
        var target = 0
        val repo = FakeRepository(seedState(Phase.INTRO_TWO, listOf(comparisonHistory())).copy(
            restTimer = RestTimer("rest", "workout", "exercise", FIXED_NOW + 180_000)
        ))
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                val bottom = WindowInsets.ime.getBottom(LocalDensity.current)
                val destination = WindowInsets.imeAnimationTarget.getBottom(LocalDensity.current)
                SideEffect { ime = bottom; target = destination }
                RebuildApp(repo, FakePlatform(PlatformStatus(false, false, true)), clock = { FIXED_NOW })
            }
        }
        rule.onNodeWithTag("start-scheduled").performScrollTo().performClick()
        rule.onNodeWithTag("log-set").performScrollTo().assertIsDisplayed()
        assertWholeEntry("Before keyboard", density, includeFields = false)
        rule.onNodeWithTag("entry-kg").performScrollTo().performClick()
        rule.waitUntil(5_000) { ime > 0 && ime == target }
        rule.waitForIdle()
        assertWholeEntry("With keyboard", density, includeFields = true)
        if (landscape) rule.onNodeWithTag("compact-entry-context").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitUntil(5_000) { ime == 0 }
        rule.onNodeWithTag("exercise-picker").assertIsDisplayed()
        assertEquals("41", rule.onNodeWithTag("entry-kg").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertTrue(repo.actions.none { it is Action.SaveSet || it is Action.AdjustRest || it is Action.SkipRest })
    }

    private fun assertWholeEntry(stage: String, density: Float, includeFields: Boolean) {
        val rest = rule.onNodeWithTag("rest-bar").fetchSemanticsNode().boundsInRoot
        val tags = if (includeFields) listOf("entry-kg", "entry-reps", "entry-rir", "log-set") else listOf("log-set")
        tags.forEach { tag ->
            val node = rule.onNodeWithTag(tag).fetchSemanticsNode()
            assertEquals("$stage: $tag clipped", node.size.height.toFloat(), node.boundsInRoot.height, 1f)
            assertTrue("$stage: $tag under rest", node.boundsInRoot.bottom <= rest.top + 1)
        }
        listOf("Add 30 seconds", "Skip rest", "Rest options").forEach { label ->
            val node = rule.onNodeWithContentDescription(label).assertIsDisplayed().fetchSemanticsNode()
            assertTrue("$stage: $label hit bounds", node.touchBoundsInRoot.width >= 48 * density - 1 &&
                node.touchBoundsInRoot.height >= 48 * density - 1)
        }
    }
}
