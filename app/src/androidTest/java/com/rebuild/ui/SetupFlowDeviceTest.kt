package com.rebuild.ui

import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rebuild.domain.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A fresh install's setup: a welcome, three questions, the plan they lead to, then rest alerts. */
@RunWith(AndroidJUnit4::class)
class SetupFlowDeviceTest {
    @get:Rule val rule = createComposeRule()

    private fun launch(platform: FakePlatform = FakePlatform()): FakeRepository {
        val repo = FakeRepository(Seed.initialState())
        rule.setContent { RebuildApp(repo, platform, clock = { FIXED_NOW }) }
        return repo
    }

    private fun next() = rule.onNodeWithTag("setup-action").assertIsEnabled().performClick()

    private fun back() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
    }

    @Test fun answeringThreeQuestionsShowsOnePlanAndStartingSavesItInOneChange() {
        val platform = FakePlatform(PlatformStatus(notificationsAllowed = false))
        val repo = launch(platform)
        rule.onNodeWithText("Rebuild").assertIsDisplayed()
        next()

        // Nothing is chosen for you, and nothing moves on until you continue.
        rule.onNodeWithTag("setup-step").assertContentDescriptionEquals("Step 1 of 4")
        rule.onNodeWithTag("setup-action").assertIsNotEnabled()
        rule.onNodeWithTag("setup-experience-new").performClick().assertIsSelected()
        next()

        rule.onNodeWithTag("setup-step").assertContentDescriptionEquals("Step 2 of 4")
        rule.onNodeWithTag("setup-days-5").performScrollTo().performClick()
        rule.onNodeWithTag("setup-days-note").assertIsDisplayed()
        rule.onNodeWithTag("setup-days-3").performScrollTo().performClick()
        rule.onNodeWithTag("setup-days-note").assertDoesNotExist()
        next()

        rule.onNodeWithTag("setup-step").assertContentDescriptionEquals("Step 3 of 4")
        rule.onNodeWithTag("setup-gear-dumbbells").performClick()
        rule.onNodeWithTag("setup-unit-lb").performScrollTo().performClick()
        next()

        rule.onNodeWithTag("setup-step").assertContentDescriptionEquals("Step 4 of 4")
        rule.onNodeWithTag("split-name").assertTextEquals("Full body, 3 days")
        rule.onNodeWithTag("split-gear-note").performScrollTo().assertIsDisplayed()
        // A day opens to its exercises, swapped for dumbbells.
        rule.onNodeWithTag("split-day-full-body-3-a").performScrollTo().performClick()
        rule.onNode(hasContentDescription("Goblet squat, 1 set of 10 to 15 reps", substring = true)).assertExists()
        assertTrue("nothing is saved before Start", repo.actions.isEmpty())

        next()
        rule.waitForIdle()
        assertEquals(listOf(Action.CompleteSetup(Experience.NEW, "full-body-3", Gear.DUMBBELLS, MassUnit.LB,
            localDateOf(FIXED_NOW).toString())), repo.actions)
        assertEquals(Phase.INTRO_ONE, repo.state.value.phase)

        // The last step asks for notifications only now, with the reason beside it, and can be skipped.
        rule.onNodeWithText("Know when to lift again").assertIsDisplayed()
        rule.onNodeWithTag("setup-rest-allow").performClick()
        assertEquals(listOf("notifications"), platform.calls)
        rule.onNodeWithTag("setup-rest-denied").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("setup-rest-done").performClick()
        rule.onNodeWithText("Today").assertIsDisplayed()
    }

    @Test fun backGoesOneStepBackKeepingEveryAnswerAndAPlanChangeComesBackToThePlan() {
        launch()
        next()
        rule.onNodeWithTag("setup-experience-experienced").performClick()
        next()
        rule.onNodeWithTag("setup-days-3").performClick()
        next()
        rule.onNodeWithTag("setup-gear-gym").performClick()
        next()
        rule.onNodeWithTag("split-name").assertTextEquals("Push, pull, legs")

        back()
        rule.onNodeWithTag("setup-gear-gym").assertIsSelected()
        back()
        rule.onNodeWithTag("setup-days-3").assertIsSelected()
        back()
        rule.onNodeWithTag("setup-experience-experienced").assertIsSelected()
        back()
        rule.onNodeWithText("Get started").assertIsDisplayed()

        // From the plan, changing one answer comes straight back to the plan.
        next(); next(); next(); next()
        rule.onNode(hasContentDescription("Change days", substring = true)).performScrollTo().performClick()
        rule.onNodeWithTag("setup-days-4").performClick()
        next()
        rule.onNodeWithTag("split-name").assertTextEquals("Upper and lower")
    }

    @Test fun restoreFromWelcomeOpensTheRestoreFlowAndSkippingAlertsLandsOnToday() {
        val platform = FakePlatform()
        val repo = launch(platform)
        rule.onNodeWithTag("setup-restore").performClick()
        assertEquals(listOf("restore"), platform.calls)

        next()
        rule.onNodeWithTag("setup-experience-returning").performClick(); next()
        rule.onNodeWithTag("setup-days-5").performScrollTo().performClick(); next()
        rule.onNodeWithTag("setup-gear-gym").performClick(); next()
        // Coming back on 5 days with a gym gets the original plan, which starts on 4 days.
        rule.onNodeWithTag("split-name").assertTextEquals("Rebuild's gym plan")
        next()
        rule.onNodeWithTag("setup-rest-done").performClick()
        assertEquals(ORIGINAL_SPLIT, repo.state.value.activeSplitId)
        rule.onNodeWithTag("week-progress").performScrollTo().assertIsDisplayed()
    }

    @Test fun aQuickSecondTapWhileTheNextPageSlidesInNeverSavesAPlanNobodySaw() {
        val repo = launch()
        next()
        rule.onNodeWithTag("setup-experience-new").performClick(); next()
        rule.onNodeWithTag("setup-days-3").performClick(); next()
        rule.onNodeWithTag("setup-gear-gym").performClick()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("setup-action").performClick()
        rule.mainClock.advanceTimeBy(100)
        // Mid-slide, only the incoming page is in the tree, and its Start waits until it has settled.
        rule.onNodeWithTag("setup-action").assertIsNotEnabled().performClick()
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertTrue(repo.actions.isEmpty())
        rule.onNodeWithTag("split-name").assertTextEquals("Full body, 3 days")
        rule.onNodeWithTag("setup-action").assertIsEnabled()
    }

    @Test fun planChangeSplitPreviewsTheNewSplitAndSwitchesAfterConfirming() {
        val start = com.rebuild.data.StateChanges.apply(Seed.initialState(),
            Action.CompleteSetup(Experience.NEW, "full-body-3", Gear.GYM, MassUnit.KG, localDateOf(FIXED_NOW).toString()))
        val repo = FakeRepository(start)
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithText("Plan").performClick()
        rule.onNodeWithTag("plan-split-name").assertTextEquals("Full body, 3 days")
        rule.onNodeWithTag("plan-change-split").performClick()
        rule.onNodeWithTag("split-unchanged").assertExists()
        rule.onNodeWithTag("setup-days-4").performScrollTo().performClick()
        rule.onNodeWithTag("split-name").performScrollTo().assertTextEquals("Upper and lower")
        rule.onNodeWithTag("split-switch").assertIsEnabled().performClick()
        rule.onNodeWithTag("confirm-button").performClick()
        rule.waitForIdle()
        assertEquals("upper-lower", repo.state.value.activeSplitId)
        assertEquals("the phase stays", Phase.INTRO_ONE, repo.state.value.phase)
        rule.onNodeWithTag("plan-split-name").assertTextEquals("Upper and lower")
    }

    @Test fun aSaturdaySignUpIsOfferedThePlansFirstWorkoutToday() {
        val saturday = java.time.LocalDateTime.of(2026, 9, 26, 10, 0).atZone(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val start = com.rebuild.data.StateChanges.apply(Seed.initialState(),
            Action.CompleteSetup(Experience.NEW, "full-body-3", Gear.GYM, MassUnit.KG, localDateOf(saturday).toString()))
        val repo = FakeRepository(start)
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { saturday }) }
        rule.onNodeWithTag("start-suggested").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Monday", substring = true).assertExists()
    }

    @Test fun changeSplitKeepsASplitPickedByHand() {
        val start = com.rebuild.data.StateChanges.apply(Seed.initialState(),
            Action.CompleteSetup(Experience.EXPERIENCED, "full-body-3", Gear.GYM, MassUnit.KG, localDateOf(FIXED_NOW).toString()))
        val repo = FakeRepository(start)
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithText("Plan").performClick()
        rule.onNodeWithTag("plan-change-split").performClick()
        // The quiz would suggest Push, pull, legs for an experienced 3-day lifter; the hand-picked split stays chosen.
        rule.onNodeWithTag("split-pick-full-body-3").performScrollTo().assertIsSelected()
        rule.onNodeWithTag("split-unchanged").assertExists()
    }
}
