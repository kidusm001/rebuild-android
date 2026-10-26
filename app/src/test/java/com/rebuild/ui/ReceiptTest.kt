package com.rebuild.ui

import com.rebuild.data.MemoryPersistence
import com.rebuild.data.RebuildRepository
import com.rebuild.domain.Action
import com.rebuild.domain.Phase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptTest {
    private suspend fun finishedPush(repo: RebuildRepository, start: Long, kg: Double, reps: Int, warmupKg: Double? = null) {
        repo.execute(Action.StartWorkout("push", start))
        val workout = requireNotNull(repo.state.value.activeWorkout)
        val first = workout.exercises.first()
        warmupKg?.let { repo.execute(Action.SaveSet(workout.id, first.id, "w$start", it, 20, null, true, start + 1)) }
        repo.execute(Action.SaveSet(workout.id, first.id, "a$start", kg - 5, reps + 2, 2.0, false, start + 2))
        repo.execute(Action.SaveSet(workout.id, first.id, "b$start", kg, reps, 1.0, false, start + 3))
        repo.execute(Action.FinishWorkout(start + 60_000))
    }

    @Test fun receiptShowsTheBestSetAndOnlyARealGainOverLastTime() = runTest {
        val repo = RebuildRepository.fromPersistence(MemoryPersistence()) { 10_000_000 }
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        finishedPush(repo, 1_000_000, kg = 40.0, reps = 8)
        finishedPush(repo, 2_000_000, kg = 42.5, reps = 8, warmupKg = 60.0)

        val today = repo.state.value.workouts.maxBy { it.startedAt }
        val line = receiptLines(repo.state.value, today).single()
        assertEquals("a heavier warm-up never counts as the best set", 2, line.sets)
        assertEquals("+2.5 kg", line.gain)
        assertEquals(true, line.best.startsWith("42.5"))
    }

    @Test fun sameLoadMoreRepsIsAGainAndLessIsNothing() = runTest {
        val repo = RebuildRepository.fromPersistence(MemoryPersistence()) { 10_000_000 }
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        fun latest() = receiptLines(repo.state.value, repo.state.value.workouts.maxBy { it.startedAt }).single()
        finishedPush(repo, 1_000_000, kg = 40.0, reps = 8)
        finishedPush(repo, 2_000_000, kg = 40.0, reps = 10)
        assertEquals("+2 reps", latest().gain)
        finishedPush(repo, 3_000_000, kg = 40.0, reps = 9)
        assertNull("a lighter day is shown as it was, never as a loss", latest().gain)
    }
}
