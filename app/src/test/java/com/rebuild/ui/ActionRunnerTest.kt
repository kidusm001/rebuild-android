package com.rebuild.ui

import androidx.lifecycle.ViewModelStore
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

    private class RecordingRepository : AppRepository {
        override val state = MutableStateFlow(AppState(exercises = emptyList(), templates = emptyList()))
        val actions = mutableListOf<Action>()
        var secondGate: CompletableDeferred<Unit>? = null
        private var attempts = 0
        override suspend fun execute(action: Action) {
            if (++attempts == 2) secondGate?.await()
            actions += action
        }
        override suspend fun exportBackup(): BackupDocument = error("Not used by action runner tests")
        override fun previewBackup(json: String): BackupPreview = error("Not used by action runner tests")
        override suspend fun restoreBackup(preview: BackupPreview, expectedRevision: Long) = error("Not used by action runner tests")
        override suspend fun consumeRestTimer(timerId: String, now: Long): Boolean = error("Not used by action runner tests")
    }
}
