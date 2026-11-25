package com.rebuild.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import com.rebuild.data.MemoryPersistence
import com.rebuild.data.RebuildRepository
import com.rebuild.domain.Action
import com.rebuild.domain.Phase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The rest bar's Skip and its Undo, and the one-line bar in a short landscape window. */
// Clicks go through semantics: under Robolectric, injected touches don't reach the bars drawn over the layers.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class RestBarTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val now = 1_800_000_000_000L
    private val status = MutableStateFlow(PlatformStatus())

    private val platform = object : PlatformActions {
        override val status = this@RestBarTest.status
        override fun requestNotifications() = Unit
        override fun requestExactAlarms() = Unit
        override fun openBatterySettings() = Unit
        override fun exportBackup() = Unit
        override fun shareBackup() = Unit
        override fun chooseRestore() = Unit
        override val restAlert = MutableStateFlow(RestAlert())
        override fun setRestAlert(alert: RestAlert) { restAlert.value = alert }
        override val tips = MutableStateFlow(TipsState(enabled = false))
        override fun dismissTip(tip: Tip) { tips.value = tips.value.dismiss(tip) }
        override fun setTipsEnabled(enabled: Boolean) = Unit
        override fun resetTips() = Unit
        override fun previewRestAlert() = Unit
        override fun stopRestAlarm() = Unit
        override val alarmRingingSince = MutableStateFlow<Long?>(null)
        override fun announceRestEnd(timerId: String, shortPause: Boolean) = Unit
    }

    private fun resting(): RebuildRepository = runBlocking {
        val repo = RebuildRepository.fromPersistence(MemoryPersistence()) { now }
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        repo.execute(Action.StartWorkout("push", now - 60_000))
        val workout = requireNotNull(repo.state.value.activeWorkout)
        val first = workout.exercises.first()
        repo.execute(Action.SaveSet(workout.id, first.id, "s1", 40.0, 10, 2.0, false, now))
        repo
    }

    @Test fun skipKeepsTheBarInPlaceWithUndoThatBringsBackTheSameRest() {
        val repo = resting()
        val timer = requireNotNull(repo.state.value.restTimer)
        compose.setContent { RebuildApp(repo, platform, clock = { now }) }
        compose.waitForIdle()
        val restHeight = height("rest-bar")
        compose.onNodeWithContentDescription("Skip rest").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("rest-skipped").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(null, repo.state.value.restTimer)
        // Same height as the bar it replaces, so the dock above doesn't move.
        assertEquals(restHeight.value, height("rest-skipped").value, 0.5f)
        compose.onNodeWithContentDescription("Undo skip, bring the rest back")
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { repo.state.value.restTimer != null }
        assertEquals(timer, repo.state.value.restTimer)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("rest-skipped").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("rest-bar").assertExists()
    }

    @Test fun skipKeepsTheHeightOfABarThatCarriedATip() {
        platform.tips.value = TipsState(enabled = true)
        val repo = resting()
        compose.setContent { RebuildApp(repo, platform, clock = { now }) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("tip-rest-bar").fetchSemanticsNodes().isNotEmpty() }
        val restHeight = height("rest-bar")
        compose.onNodeWithContentDescription("Skip rest").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("rest-skipped").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(restHeight.value, height("rest-skipped").value, 0.5f)
    }

    @Test fun aHeldHeightFromAnotherWindowIsDropped() {
        platform.tips.value = TipsState(enabled = true)
        val repo = resting()
        compose.setContent { RebuildApp(repo, platform, clock = { now }) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("tip-rest-bar").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Skip rest").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("rest-skipped").fetchSemanticsNodes().isNotEmpty() }
        val held = height("rest-skipped")
        // As if the phone had been rotated: the same skip, measured in some other window.
        val runner = compose.activity.viewModelStore.keys().map { compose.activity.viewModelStore[it] }
            .filterIsInstance<ActionRunner>().single()
        val offer = requireNotNull(runner.skippedRest)
        compose.runOnUiThread { runner.offerUndoSkip(offer.timer, 60_000, offer.heightPx, heldIn = "elsewhere") }
        compose.waitForIdle()
        assert(height("rest-skipped") < held) { "the bar kept the old window's height" }
    }

    @Test fun skippingTheLastSecondsOffersNoUndo() {
        val repo = resting()
        val timer = requireNotNull(repo.state.value.restTimer)
        val late = timer.deadline - 3_000
        compose.setContent { RebuildApp(repo, platform, clock = { late }) }
        compose.onNodeWithContentDescription("Skip rest").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { repo.state.value.restTimer == null }
        compose.waitForIdle()
        compose.onAllNodesWithTag("rest-skipped").assertCountEquals(0)
    }

    private fun height(tag: String): Dp =
        compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().let { it.bottom - it.top }

    @Test fun roomyPortraitKeepsTheTwoLineBar() {
        val repo = resting()
        compose.setContent { RebuildApp(repo, platform, clock = { now }) }
        val seconds = ((requireNotNull(repo.state.value.restTimer).deadline - now) / 1000).toInt()
        compose.onAllNodesWithContentDescription("Rest ${formatRest(seconds)}").assertCountEquals(0)
        compose.onNodeWithText(formatRest(seconds)).assertExists()
    }

    @Test @Config(qualifiers = "w914dp-h411dp-land-xxhdpi")
    fun shortLandscapeFoldsToOneLineAndKeepsTheLimitationVisible() {
        status.value = PlatformStatus(notificationsAllowed = false)
        val repo = resting()
        val seconds = ((requireNotNull(repo.state.value.restTimer).deadline - now) / 1000).toInt()
        compose.setContent { RebuildApp(repo, platform, clock = { now }) }
        compose.onNodeWithContentDescription("Rest ${formatRest(seconds)}, alerts off").assertExists()
        val height = compose.onNodeWithTag("rest-bar").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        assert(height <= 72.dp) { "rest bar is $height tall" }
    }
}
