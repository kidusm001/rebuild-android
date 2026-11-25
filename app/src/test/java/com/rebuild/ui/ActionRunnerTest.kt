package com.rebuild.ui

import androidx.compose.ui.MotionDurationScale
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.rebuild.ui.theme.Motion
import kotlinx.coroutines.withContext
import com.rebuild.domain.Action
import com.rebuild.domain.AppRepository
import com.rebuild.domain.AppState
import com.rebuild.domain.BackupDocument
import com.rebuild.domain.BackupPreview
import com.rebuild.domain.Phase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ActionRunnerTest {
    private val store = ViewModelStore()
    private val repository = RecordingRepository()
    private lateinit var runner: ActionRunner

    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        runner = ActionRunner(repository)
        store.put("runner", runner)
    }

    @After fun cleanup() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test fun undoSkipWindowEndsOnItsOwnAndANewerSkipRestartsIt() = runTest {
        val first = com.rebuild.domain.RestTimer("a", "w", "e", 100_000)
        val second = first.copy(id = "b")
        runner.offerUndoSkip(first, 4_000)
        advanceTimeBy(3_000)
        runner.offerUndoSkip(second, 4_000)
        advanceTimeBy(2_000)
        runCurrent()
        // The first window would have ended by now; the newer skip keeps its own.
        assertEquals(second, runner.skippedRest?.timer)
        advanceTimeBy(2_001)
        runCurrent()
        assertEquals(null, runner.skippedRest)
        runner.offerUndoSkip(first, 4_000)
        runner.endUndoSkip()
        assertEquals(null, runner.skippedRest)
    }

    @Test fun chainedNoteSavesKeepGuardUntilTheNextWriteFinishes() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.secondGate = gate
        runner.run(Action.ExerciseNote("workout", "first", "note")) {
            runner.run(Action.ExerciseNote("workout", "second", "another note"))
        }
        runCurrent()
        assertEquals(1, repository.actions.size)
        assertTrue("The first callback must not clear the second pending write's guard", runner.busy)
        assertFalse(runner.run(Action.SelectPhase(Phase.BASELINE)))
        gate.complete(Unit)
        runCurrent()
        assertEquals(2, repository.actions.size)
        assertFalse(runner.busy)
    }

    @Test fun newSetCooldownBlocksPostCommitTapsForExactlyTheConfiguredWindow() = runTest {
        val first = Action.SaveSet("workout", "exercise", "first", 40.0, 8, null, false, 1000)
        val second = first.copy(setId = "second")
        assertTrue(runner.run(first, cooldownMs = ActionRunner.NEW_SET_COOLDOWN_MS))
        runCurrent()
        assertEquals(listOf(first), repository.actions)
        advanceTimeBy(100)
        assertFalse(runner.run(second))
        advanceTimeBy(ActionRunner.NEW_SET_COOLDOWN_MS - 101)
        runCurrent()
        assertTrue(runner.busy)
        assertFalse(runner.run(second))
        advanceTimeBy(1)
        runCurrent()
        assertFalse(runner.busy)
        assertTrue(runner.run(second))
        runCurrent()
        assertEquals(listOf(first, second), repository.actions)
    }

    @Test fun aNormalSaveNeverShowsBusyAndTheCooldownKeepsTheGuardWithoutTheLook() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.firstGate = gate
        val set = Action.SaveSet("workout", "exercise", "first", 40.0, 8, null, false, 1000)
        var saved = false
        assertTrue(runner.run(set, cooldownMs = ActionRunner.NEW_SET_COOLDOWN_MS, tag = "log-set", onSaved = { saved = true }))
        runCurrent()
        assertTrue(runner.busy)
        assertFalse("No busy look before the threshold", runner.showsBusy)
        advanceTimeBy(Motion.BusyAfterMs - 100)
        runCurrent()
        assertFalse(runner.showsBusy)
        gate.complete(Unit)
        runCurrent()
        assertTrue("onSaved runs as the write lands, before the cooldown", saved)
        assertTrue("The guard stays closed through the cooldown", runner.busy)
        assertFalse(runner.run(set.copy(setId = "second")))
        advanceTimeBy(Motion.BusyAfterMs)
        runCurrent()
        assertFalse("The cooldown never shows the busy look", runner.showsBusy)
        assertFalse(runner.showsBusy("log-set"))
    }

    @Test fun aSlowSaveShowsBusyOnlyOnItsOwnControlAfterTheRealTimeThreshold() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.firstGate = gate
        var saved = false
        assertTrue(runner.run(Action.SelectPhase(Phase.BASELINE), tag = "log-set", onSaved = { saved = true }))
        runCurrent()
        advanceTimeBy(Motion.BusyAfterMs - 1)
        runCurrent()
        assertFalse(runner.showsBusy)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(runner.showsBusy)
        assertTrue(runner.showsBusy("log-set"))
        assertFalse("Only the control that started it says Saving", runner.showsBusy("log-effort"))
        assertFalse(saved)
        gate.complete(Unit)
        runCurrent()
        assertTrue(saved)
        assertFalse(runner.showsBusy)
        assertFalse(runner.busy)
    }

    @Test fun theBusyThresholdIsWallClockWhateverTheAnimatorScale() = runTest {
        // Animations read their scale from MotionDurationScale; the runner's threshold must not.
        val gate = CompletableDeferred<Unit>()
        repository.firstGate = gate
        withContext(object : MotionDurationScale { override val scaleFactor = 0f }) {
            runner.run(Action.SelectPhase(Phase.BASELINE))
        }
        runCurrent()
        assertFalse(runner.showsBusy)
        advanceTimeBy(Motion.BusyAfterMs)
        runCurrent()
        assertTrue(runner.showsBusy)
        gate.complete(Unit)
        runCurrent()
    }

    @Test fun aFinishedWorkoutsReceiptIsRememberedAcrossRecreation() {
        val handle = SavedStateHandle()
        val first = ActionRunner(repository, handle)
        first.receiptFor("w1")
        assertEquals("w1", ActionRunner(repository, handle).receiptPending)
        first.receiptShown()
        assertEquals(null, ActionRunner(repository, handle).receiptPending)
    }

    @Test fun theReceiptIsDrawnUntilItIsClosed() {
        val runner = ActionRunner(repository)
        runner.receiptFor("w1")
        assertEquals(null, runner.receiptOnScreen)
        runner.receiptDrawn("w1")
        assertEquals("w1", runner.receiptOnScreen)
        runner.receiptShown()
        assertEquals(null, runner.receiptOnScreen)
    }

    @Test fun aSetsTickWaitsForThatSetAndFiresOnce() {
        val runner = ActionRunner(repository)
        runner.tickWhenShown("s1")
        assertEquals("s1", runner.tickFor)
        // A tick for an older set never clears a newer one.
        runner.ticked("s0")
        assertEquals("s1", runner.tickFor)
        runner.ticked("s1")
        assertEquals(null, runner.tickFor)
    }

    private class RecordingRepository : AppRepository {
        override val state = MutableStateFlow(AppState(exercises = emptyList(), templates = emptyList()))
        val actions = mutableListOf<Action>()
        var secondGate: CompletableDeferred<Unit>? = null
        var firstGate: CompletableDeferred<Unit>? = null
        private var attempts = 0
        override suspend fun execute(action: Action) {
            if (++attempts == 1) firstGate?.await()
            if (attempts == 2) secondGate?.await()
            actions += action
        }
        override suspend fun exportBackup(): BackupDocument = error("Not used by action runner tests")
        override fun previewBackup(json: String): BackupPreview = error("Not used by action runner tests")
        override suspend fun restoreBackup(preview: BackupPreview, expectedRevision: Long) = error("Not used by action runner tests")
        override suspend fun consumeRestTimer(timerId: String, now: Long): Boolean = error("Not used by action runner tests")
    }
}
