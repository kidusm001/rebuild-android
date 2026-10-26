package com.rebuild.ui

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rebuild.domain.Phase
import com.rebuild.domain.Seed
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanIconDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before fun window() {
        rule.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
    }

    @Test fun wednesdayStartsPulldownAndPrescribedSelectorHasNoPullups() {
        val repo = FakeRepository(Seed.initialState().copy(phase = Phase.BASELINE))
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW + 86_400_000 }) }
        rule.onNodeWithTag("start-scheduled").assertTextContains("Start Pull").performClick()
        rule.onNodeWithTag("target-line").assertTextContains("Today's target: 3 working sets · 8–12 reps · 2 RIR")
        rule.onNodeWithTag("entry-kg").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        assertEquals("pulldown", repo.state.value.activeWorkout!!.exercises.first().exercise.id)
        for (label in listOf("Back", "More workout actions")) {
            val bounds = rule.onNodeWithContentDescription(label).assertIsDisplayed().fetchSemanticsNode().touchBoundsInRoot
            val minimum = with(rule.density) { 48.dp.toPx() }
            assertTrue("$label must retain its 48 dp touch region: $bounds",
                bounds.width >= minimum && bounds.height >= minimum)
        }
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNode(hasText("Plan") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
        rule.onNodeWithTag("plan-slot-pull-vertical").performScrollTo().performClick()
        rule.onNodeWithText("Pull-up").assertDoesNotExist()
        rule.onNodeWithText("Assisted pull-up").assertDoesNotExist()
        rule.onNode(hasText("Lat pulldown") and isSelectable()).assertExists()
    }

    @Test fun outlinedNavigationRetainsLabelsSelectedStateAndLargeTextHitTargets() {
        val repo = FakeRepository(Seed.initialState().copy(phase = Phase.INTRO_ONE))
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, 2f)) {
                RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW })
            }
        }
        for (label in listOf("History", "Plan", "More", "Today")) {
            val tab = rule.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            tab.assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
            tab.assertIsSelected()
        }
    }
}
