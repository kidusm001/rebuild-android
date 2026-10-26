package com.rebuild.platform

import com.rebuild.data.RebuildRepository
import com.rebuild.data.SnapshotPersistence
import com.rebuild.domain.Action
import com.rebuild.domain.AppRepository
import com.rebuild.domain.Phase
import com.rebuild.domain.availableTemplates
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestRecoveryTest {
    private suspend fun repositoryWithTimer(): RebuildRepository {
        val persistence = object : SnapshotPersistence {
            var saved: String? = null
            override fun read() = saved
            override fun write(snapshot: String) { saved = snapshot }
        }
        val repository = RebuildRepository.fromPersistence(persistence) { 10_000L }
        repository.execute(Action.SelectPhase(Phase.INTRO_ONE))
        repository.execute(Action.StartWorkout(repository.state.value.availableTemplates().first().id, 10_000L))
        val workout = requireNotNull(repository.state.value.activeWorkout)
        repository.execute(Action.SaveSet(workout.id, workout.exercises.first().id, "old-set", 10.0, 10, null, false, 10_000L))
        return repository
    }

    @Test fun expiredRecoveryCannotClearNewSetTimerInstalledAfterSnapshotRead() = runTest {
        val repository = repositoryWithTimer()
        val old = requireNotNull(repository.state.value.restTimer)
        val now = old.deadline + 1_000L
        var newDeadline: Long? = null
        suspend fun installNewTimer() {
            val workout = requireNotNull(repository.state.value.activeWorkout)
            repository.execute(Action.SaveSet(workout.id, workout.exercises.first().id, "new-set", 10.0, 9, null, false, now))
            newDeadline = repository.state.value.restTimer?.deadline
            assertNotEquals(old.id, repository.state.value.restTimer?.id)
            assertTrue(requireNotNull(newDeadline) > now)
        }
        val interleaving = object : AppRepository by repository {
            override suspend fun execute(action: Action) {
                if (action == Action.SkipRest) installNewTimer()
                repository.execute(action)
            }
            override suspend fun consumeRestTimer(timerId: String, now: Long): Boolean {
                installNewTimer()
                return repository.consumeRestTimer(timerId, now)
            }
        }
        recoverRestAlarm(interleaving, now) { _, _ -> }
        assertNotNull("Recovery of expired A must preserve newly committed B", repository.state.value.restTimer)
        assertEquals(newDeadline, repository.state.value.restTimer?.deadline)
        assertEquals(2, repository.state.value.activeWorkout?.exercises?.first()?.sets?.size)
    }

    @Test fun expiredRecoveryClearsOnlyItsOwnTimerWithoutNotification() = runTest {
        val repository = repositoryWithTimer()
        val deadline = requireNotNull(repository.state.value.restTimer).deadline
        var scheduled = false
        recoverRestAlarm(repository, deadline + 1) { _, _ -> scheduled = true }
        assertNull(repository.state.value.restTimer)
        assertEquals(false, scheduled)
    }

    @Test fun futureDeadlineIsRearmedWithoutChangingSavedState() = runTest {
        val repository = repositoryWithTimer()
        val before = repository.state.value
        var scheduledDeadline: Long? = null
        recoverRestAlarm(repository, 10_001L) { timer, _ -> scheduledDeadline = timer?.deadline }
        assertEquals(before.restTimer?.deadline, scheduledDeadline)
        assertEquals(before, repository.state.value)
    }
}
