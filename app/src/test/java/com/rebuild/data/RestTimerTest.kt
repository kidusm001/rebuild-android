package com.rebuild.data

import com.rebuild.domain.Action
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RestTimerTest {
    @Test fun newWorkingSetStartsFreshTimerButWarmupAndEditsDoNot() = runTest {
        val repo = repository()
        repo.start()
        repo.log(id = "warmup", warmup = true)
        assertNull(repo.state.value.restTimer)
        repo.log(id = "warmup", warmup = false, now = 3000)
        assertNull(repo.state.value.restTimer)
        repo.log(id = "working", now = 4000)
        val first = repo.state.value.restTimer!!
        assertEquals(184000L, first.deadline)
        repo.log(id = "working", reps = 9, now = 9000)
        assertEquals(first, repo.state.value.restTimer)
        repo.log(id = "next", now = 10000)
        val next = repo.state.value.restTimer!!
        assertNotEquals(first.id, next.id)
        assertEquals(190000L, next.deadline)
        repo.log(id = "another-warmup", warmup = true, now = 12000)
        assertEquals(next, repo.state.value.restTimer)
    }

    @Test fun timerPersistsAcrossReopenAndCanBeAdjustedAndSkipped() = runTest {
        val store = MemoryPersistence()
        val original = repository(store)
        original.start()
        original.log()
        val repo = repository(store)
        val first = repo.state.value.restTimer!!
        repo.execute(Action.AdjustRest("old-id", 300000))
        assertEquals(first, repo.state.value.restTimer)
        repo.execute(Action.AdjustRest(first.id, 300000))
        assertEquals(300000L, repo.state.value.restTimer!!.deadline)
        repo.execute(Action.SkipRest)
        assertNull(repo.state.value.restTimer)
        assertFalse(repo.consumeRestTimer(first.id, 300000))
    }

    @Test fun earlyOrMismatchedReceiversCannotClearOrNotify() = runTest {
        val repo = repository()
        repo.start()
        repo.log()
        val timer = repo.state.value.restTimer!!
        val before = repo.state.value
        assertFalse(repo.consumeRestTimer(timer.id, timer.deadline - 1))
        assertFalse(repo.consumeRestTimer("outdated", timer.deadline))
        assertEquals(before, repo.state.value)
    }

    @Test fun delayedRecoveryForTimerACannotClearTheNewTimerB() = runTest {
        val store = MemoryPersistence()
        val repo = repository(store)
        repo.start()
        repo.log(id = "set-a", now = 2000)
        val timerA = repo.state.value.restTimer!!
        repo.log(id = "set-b", now = 5000)
        val timerB = repo.state.value.restTimer!!
        val before = repo.state.value
        val disk = store.snapshot
        val recoveryAt = timerA.deadline + 300001

        assertFalse(repo.consumeRestTimer(timerA.id, recoveryAt))
        assertEquals(before, repo.state.value)
        assertEquals(disk, store.snapshot)
        assertEquals(timerB, repo.state.value.restTimer)
        assertTrue(repo.consumeRestTimer(timerB.id, recoveryAt))
        assertNull(repo.state.value.restTimer)
    }

    @Test fun matchingDueTimerIsConsumedExactlyOnceIncludingConcurrentReceivers() = runTest {
        val repo = repository()
        repo.start()
        repo.log()
        val timer = repo.state.value.restTimer!!
        val results = List(10) { async { repo.consumeRestTimer(timer.id, timer.deadline) } }.awaitAll()
        assertEquals(1, results.count { it })
        assertNull(repo.state.value.restTimer)
        assertFalse(repo.consumeRestTimer(timer.id, timer.deadline))
    }

    @Test fun fiveMinuteBoundaryNotifiesButStaleTimerOnlyClears() = runTest {
        val repo = repository()
        repo.start()
        repo.log()
        val timer = repo.state.value.restTimer!!
        assertTrue(repo.consumeRestTimer(timer.id, timer.deadline + 300000))
        repo.log(id = "second", now = timer.deadline + 400000)
        val later = repo.state.value.restTimer!!
        assertFalse(repo.consumeRestTimer(later.id, later.deadline + 300001))
        assertNull(repo.state.value.restTimer)
    }

    @Test fun failedConsumptionNeverSignalsNotificationOrClearsTimer() = runTest {
        val store = MemoryPersistence()
        val repo = repository(store)
        repo.start()
        repo.log()
        val before = repo.state.value
        val timer = before.restTimer!!
        store.failWrites = true
        failure("Could not save") { repo.consumeRestTimer(timer.id, timer.deadline) }
        assertEquals(before, repo.state.value)
    }

    @Test fun finishingOrDiscardingCancelsNotification() = runTest {
        val repo = repository()
        repo.start()
        repo.log()
        val timer = repo.state.value.restTimer!!
        repo.execute(Action.FinishWorkout(5000))
        assertFalse(repo.consumeRestTimer(timer.id, timer.deadline))
        repo.execute(Action.StartWorkout("push", 6000))
        repo.log(id = "new-set", now = 7000)
        val second = repo.state.value.restTimer!!
        repo.execute(Action.DiscardWorkout)
        assertFalse(repo.consumeRestTimer(second.id, second.deadline))
    }
}
