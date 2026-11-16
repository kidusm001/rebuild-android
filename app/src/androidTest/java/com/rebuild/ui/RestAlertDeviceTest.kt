package com.rebuild.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rebuild.domain.Action
import com.rebuild.domain.Phase
import com.rebuild.domain.RestTimer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestAlertDeviceTest {
    @get:Rule val rule = createComposeRule()

    @Test fun settingsKeepSoundAndScreenChoicesFromContradictingEachOther() {
        val platform = FakePlatform()
        rule.setContent { RebuildApp(FakeRepository(seedState(Phase.INTRO_ONE)), platform, clock = { FIXED_NOW }) }
        rule.onNodeWithText("More").performClick()
        rule.onNodeWithTag("more-rest-alert").performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithTag("rest-sound-buzz").assertIsSelected()
        rule.onNodeWithTag("rest-visual-none").assertIsSelected()

        // Silent with nothing on screen would give no sign at all, so it can't be had.
        rule.onNodeWithTag("rest-sound-silent").performScrollTo().performClick()
        assertEquals(RestAlert(RestSound.SILENT, RestVisual.FLASH), platform.restAlert.value)
        rule.onNodeWithTag("rest-visual-flash").assertIsSelected()
        rule.onNodeWithTag("rest-visual-none").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("A silent alert needs something on screen.").assertIsDisplayed()

        rule.onNodeWithTag("rest-sound-alarm").performScrollTo().performClick()
        assertEquals(RestAlert(RestSound.ALARM, RestVisual.FLASH), platform.restAlert.value)

        rule.onNodeWithTag("rest-alert-try").performScrollTo().performClick()
        assertTrue("previewAlert" in platform.calls)
        rule.onNodeWithTag("rest-end-stop").assertIsDisplayed().performClick()
        assertTrue("stopAlarm" in platform.calls)
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("rest-end-overlay")).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("rest-end-alarm").assertDoesNotExist()
    }

    @Test fun aRestEndingOnScreenShowsTheChosenAlertAndAnAlarmWaitsForStop() {
        val platform = FakePlatform().apply { restAlert.value = RestAlert(RestSound.ALARM, RestVisual.FLASH) }
        val repo = FakeRepository(seedState(Phase.INTRO_ONE))
        runBlocking { repo.execute(Action.StartWorkout("upper-a", FIXED_NOW)) }
        val workout = repo.state.value.activeWorkout!!
        val ends = System.currentTimeMillis() + 1_500
        repo.replace(repo.state.value.copy(restTimer = RestTimer("t1", workout.id, workout.exercises.first().id, ends)))
        rule.setContent { RebuildApp(repo, platform) }

        rule.onNodeWithTag("rest-end-overlay").assertDoesNotExist()
        // On screen the rest ends at its deadline, not whenever Android's alarm arrives.
        rule.waitUntil(5_000) { "announce" in platform.calls }
        // The real repository ends the rest; the fake one is told to.
        repo.replace(repo.state.value.copy(restTimer = null))
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("rest-end-stop")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("rest-end-alarm").assertIsDisplayed()
        rule.onNodeWithTag("rest-bar").assertDoesNotExist()
        // The alarm's bar stays past the few seconds a flash lasts.
        rule.mainClock.advanceTimeBy(6_000)
        rule.onNodeWithTag("rest-end-stop").assertIsDisplayed().performClick()
        assertTrue("stopAlarm" in platform.calls)
        rule.waitUntil(2_000) { rule.onAllNodes(hasTestTag("rest-end-alarm")).fetchSemanticsNodes().isEmpty() }
    }

    @Test fun aFlashCoversTheScreenBrieflyAndThenGetsOutOfTheWay() {
        val platform = FakePlatform().apply { restAlert.value = RestAlert(RestSound.CHIME, RestVisual.FLASH) }
        val repo = FakeRepository(seedState(Phase.INTRO_ONE))
        runBlocking { repo.execute(Action.StartWorkout("upper-a", FIXED_NOW)) }
        val workout = repo.state.value.activeWorkout!!
        val ends = System.currentTimeMillis() + 1_000
        repo.replace(repo.state.value.copy(restTimer = RestTimer("t1", workout.id, workout.exercises.first().id, ends)))
        // Time is stepped by hand, so the check sees the flash while it lasts rather than racing a fast clock.
        rule.mainClock.autoAdvance = false
        rule.setContent { RebuildApp(repo, platform) }
        rule.mainClock.advanceTimeBy(1_500)
        rule.onNodeWithTag("rest-end-overlay").assertExists()
        assertTrue("announce" in platform.calls)
        rule.onNodeWithTag("rest-end-stop").assertDoesNotExist()
        // Three beats a second apart, then gone: it never lingers over the workout.
        rule.mainClock.advanceTimeBy(3_500)
        rule.onNodeWithTag("rest-end-overlay").assertDoesNotExist()
    }

    @Test fun aSweepPassesAndLeavesTheScreenAsItWas() {
        val platform = FakePlatform().apply { restAlert.value = RestAlert(RestSound.BUZZ, RestVisual.SWEEP) }
        rule.setContent { RebuildApp(FakeRepository(seedState(Phase.INTRO_ONE)), platform, clock = { FIXED_NOW }) }
        rule.onNodeWithText("More").performClick()
        rule.onNodeWithTag("more-rest-alert").performScrollTo().performClick()
        rule.onNodeWithTag("rest-visual-sweep").assertIsSelected()
        rule.onNodeWithTag("rest-alert-try").performScrollTo().performClick()
        rule.onNodeWithTag("rest-end-overlay").assertExists()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("rest-end-overlay")).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("rest-alert-try").assertIsDisplayed()
    }

    @Test fun theDefaultBuzzAddsNothingOnScreen() {
        val platform = FakePlatform()
        val repo = FakeRepository(seedState(Phase.INTRO_ONE))
        runBlocking { repo.execute(Action.StartWorkout("upper-a", FIXED_NOW)) }
        val workout = repo.state.value.activeWorkout!!
        val ends = System.currentTimeMillis() + 500
        repo.replace(repo.state.value.copy(restTimer = RestTimer("t1", workout.id, workout.exercises.first().id, ends)))
        rule.setContent { RebuildApp(repo, platform) }
        Thread.sleep(1_500)
        rule.waitForIdle()
        rule.onNodeWithTag("rest-end-overlay").assertDoesNotExist()
    }
}
