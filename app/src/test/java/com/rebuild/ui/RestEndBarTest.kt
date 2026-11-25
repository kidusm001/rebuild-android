package com.rebuild.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.rebuild.data.MemoryPersistence
import com.rebuild.data.RebuildRepository
import com.rebuild.domain.Action
import com.rebuild.domain.Phase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** How the rest bar leaves when a rest ends by itself, for each on-screen alert. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class RestEndBarTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val start = 1_800_000_000_000L
    private var now = start
    private var repo: RebuildRepository? = null
    private val announced = mutableListOf<String>()

    private fun platform(visual: RestVisual) = object : PlatformActions {
        override val status = MutableStateFlow(PlatformStatus())
        override fun requestNotifications() = Unit
        override fun requestExactAlarms() = Unit
        override fun openBatterySettings() = Unit
        override fun exportBackup() = Unit
        override fun shareBackup() = Unit
        override fun chooseRestore() = Unit
        override val restAlert = MutableStateFlow(RestAlert(RestSound.CHIME, visual))
        override fun setRestAlert(alert: RestAlert) { restAlert.value = alert }
        override val tips = MutableStateFlow(TipsState(enabled = false))
        override fun dismissTip(tip: Tip) = Unit
        override fun setTipsEnabled(enabled: Boolean) = Unit
        override fun resetTips() = Unit
        override fun previewRestAlert() = Unit
        override fun stopRestAlarm() = Unit
        override val alarmRingingSince = MutableStateFlow<Long?>(null)
        // Like MainActivity: the first to see the deadline announces it and ends the rest.
        override fun announceRestEnd(timerId: String, shortPause: Boolean) {
            announced += timerId
            runBlocking { requireNotNull(repo).consumeRestTimer(timerId, now) }
        }
    }

    /**
     * Ends a rest on screen and counts the frames the rest bar is still drawn while it leaves: two when it is cut at
     * once, about a dozen when it collapses.
     */
    private fun framesTheBarLingers(visual: RestVisual): Int {
        val repo = runBlocking {
            RebuildRepository.fromPersistence(MemoryPersistence()) { now }.also {
                it.execute(Action.SelectPhase(Phase.BASELINE))
                it.execute(Action.StartWorkout("push", start - 60_000))
                val workout = requireNotNull(it.state.value.activeWorkout)
                val first = workout.exercises.first()
                // Logged so the rest ends a second after the screen opens.
                it.execute(Action.SaveSet(workout.id, first.id, "s1", 40.0, 10, 2.0, false,
                    start - first.restSeconds * 1000L + 1_000))
            }
        }
        this.repo = repo
        val timer = requireNotNull(repo.state.value.restTimer)
        compose.setContent { RebuildApp(repo, platform(visual), clock = { now }) }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        // The wall clock reaches the deadline; the watcher, waiting on the frame clock, sees it a second later.
        now = timer.deadline
        var frames = 0
        // Step frame by frame until the rest has ended and its bar is gone (or clearly not going).
        repeat(200) {
            compose.mainClock.advanceTimeByFrame()
            val ended = timer.id in announced && repo.state.value.restTimer == null
            val drawn = compose.onAllNodesWithTag("rest-bar").fetchSemanticsNodes().isNotEmpty()
            if (ended && drawn) frames++
            if (ended && !drawn) return frames
        }
        return frames
    }

    @Test fun anEdgeLightLeavesTheBarToCollapse() {
        val frames = framesTheBarLingers(RestVisual.FLASH)
        assertTrue("the bar vanished in $frames frames instead of collapsing", frames >= 6)
    }

    @Test fun noVisualLeavesTheBarToCollapse() {
        val frames = framesTheBarLingers(RestVisual.NONE)
        assertTrue("the bar vanished in $frames frames instead of collapsing", frames >= 6)
    }

    @Test fun upNextReplacesTheBarAtOnce() {
        val frames = framesTheBarLingers(RestVisual.SWEEP)
        // Two frames is "at once" here: the frame the rest ends in, and the one its effects run in.
        assertTrue("the takeover left the bar up for $frames frames", frames <= 2)
    }
}
