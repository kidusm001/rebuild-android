package com.rebuild.data

import com.rebuild.domain.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Late or repeated stopwatch requests act only on the stopwatch state they were made for. Each request carries the
 * step its screen showed, exactly as the buttons send it.
 */
class StopwatchReplayTest {
    /** Tuesday 29 September 2026, 18:00 in Addis Ababa. */
    private val start = ZonedDateTime.of(2026, 9, 29, 18, 0, 0, 0, ZoneId.of("Africa/Addis_Ababa"))
        .toInstant().toEpochMilli()
    private val RebuildRepository.watch: Stopwatch? get() = state.value.stopwatch
    private val RebuildRepository.step: Int get() = watch!!.step

    private fun clock(ms: Long, boot: Int? = 7, session: String = "process-1") = Monotonic(boot, session, ms)

    private suspend fun extra(store: MemoryPersistence = MemoryPersistence()): RebuildRepository {
        val repo = repository(store)
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        repo.execute(Action.StartExtraWorkout(start, "2026-09-29"))
        repo.execute(Action.AddWorkoutExercise(repo.state.value.activeWorkout!!.id, "x-plank", "plank"))
        repo.execute(Action.AddWorkoutExercise(repo.state.value.activeWorkout!!.id, "x-face", "face-pull"))
        return repo
    }

    private val RebuildRepository.workoutId: String get() = state.value.activeWorkout!!.id
    private fun RebuildRepository.efforts() = state.value.activeWorkout!!.exercises.single { it.id == "x-plank" }.efforts

    /** The Log button's request for the stopwatch's effort, as checked at [step]. */
    private fun RebuildRepository.log(id: String, seconds: Int, step: Int?, now: Long) =
        Action.SaveEffort(workoutId, "x-plank", id, seconds, null, null, now, watchStep = step)

    /** Runs [action] and returns its refusal, if any, so a test can check what changed before how it was refused. */
    private suspend fun RebuildRepository.attempt(action: Action): String? =
        try {
            execute(action)
            null
        } catch (error: RepositoryException) {
            error.message
        }

    /** The stopwatch's effort "a" on the plank, stopped at 30 s; returns its Log as the review showed it. */
    private suspend fun RebuildRepository.reviewedA(): Action.SaveEffort {
        execute(Action.StartStopwatch("a", workoutId, "x-plank", clock(0), start))
        execute(Action.StopStopwatch("a", step, clock(30_000)))
        return log("a", 30, step, start + 31_000)
    }

    @Test fun aReplayedResumeNeverRestartsAStoppedReview() = runTest {
        val repo = extra()
        repo.execute(Action.StartStopwatch("w", repo.workoutId, "x-plank", clock(0), start))
        repo.execute(Action.PauseStopwatch("w", repo.step, clock(30_000)))
        val resume = Action.ResumeStopwatch("w", repo.step, clock(40_000))
        repo.execute(resume)
        repo.execute(Action.StopStopwatch("w", repo.step, clock(50_000)))
        val review = repo.state.value
        assertEquals(40_000L, review.stopwatch!!.elapsedMs)
        assertTrue(review.stopwatch!!.stopped)

        repo.execute(resume)
        assertEquals("The replayed resume changes nothing", review, repo.state.value)
        repo.execute(Action.StopStopwatch("w", repo.step, clock(60_000)))
        assertEquals("No fresh resume, so no more time", 40_000L, repo.watch!!.elapsedMs)
    }

    @Test fun aReplayedResumeNeverCountsAPausedStretchTwice() = runTest {
        val repo = extra()
        repo.execute(Action.StartStopwatch("w", repo.workoutId, "x-plank", clock(0), start))
        repo.execute(Action.PauseStopwatch("w", repo.step, clock(30_000)))
        val resume = Action.ResumeStopwatch("w", repo.step, clock(40_000))
        repo.execute(resume)
        repo.execute(Action.PauseStopwatch("w", repo.step, clock(45_000)))
        val paused = repo.state.value
        assertEquals(35_000L, paused.stopwatch!!.elapsedMs)
        repo.execute(resume)
        assertEquals(paused, repo.state.value)
    }

    @Test fun aReplayedResumeLeavesANewerRestAlone() = runTest {
        val repo = extra()
        repo.execute(Action.StartStopwatch("w", repo.workoutId, "x-plank", clock(0), start))
        repo.execute(Action.PauseStopwatch("w", repo.step, clock(10_000)))
        val resume = Action.ResumeStopwatch("w", repo.step, clock(20_000))
        repo.execute(resume)
        repo.execute(Action.StopStopwatch("w", repo.step, clock(30_000)))
        repo.execute(Action.SaveSet(repo.workoutId, "x-face", "s1", 20.0, 12, null, false, start + 40_000))
        val resting = repo.state.value
        assertNotNull(resting.restTimer)
        repo.execute(resume)
        assertEquals("The review and the newer rest stay", resting, repo.state.value)
    }

    @Test fun aStaleStopNeverTurnsALaterPauseIntoAReview() = runTest {
        val repo = extra()
        repo.execute(Action.StartStopwatch("w", repo.workoutId, "x-plank", clock(0), start))
        repo.execute(Action.PauseStopwatch("w", repo.step, clock(10_000)))
        repo.execute(Action.ResumeStopwatch("w", repo.step, clock(15_000)))
        val stop = Action.StopStopwatch("w", repo.step, clock(18_000))
        repo.execute(Action.PauseStopwatch("w", repo.step, clock(20_000)))
        val paused = repo.state.value
        repo.execute(stop)
        assertEquals(paused, repo.state.value)
    }

    @Test fun aStaleDiscardNeverDeletesAResumedEffort() = runTest {
        val repo = extra()
        repo.execute(Action.StartStopwatch("w", repo.workoutId, "x-plank", clock(0), start))
        repo.execute(Action.StopStopwatch("w", repo.step, clock(30_000)))
        val discard = Action.DiscardStopwatch("w", repo.step)
        repo.execute(Action.ResumeStopwatch("w", repo.step, clock(40_000)))
        val resumed = repo.state.value
        failure("changed") { repo.execute(discard) }
        assertEquals(resumed, repo.state.value)
        // Confirmed for the state it now shows, it discards; repeated, it changes nothing.
        val current = Action.DiscardStopwatch("w", repo.step)
        repo.execute(current)
        assertNull(repo.watch)
        val discarded = repo.state.value
        repo.execute(current)
        repo.execute(discard)
        assertSame(discarded, repo.state.value)
        assertTrue("Discarding logs nothing", repo.efforts().isEmpty())
    }

    @Test fun aStaleRecoveryNeverStopsALaterResume() = runTest {
        val repo = extra()
        repo.execute(Action.StartStopwatch("w", repo.workoutId, "x-plank", clock(0, boot = null), start))
        val recover = Action.RecoverStopwatch("w", repo.step, clock(5_000, boot = null))
        repo.execute(Action.PauseStopwatch("w", repo.step, clock(10_000, boot = null)))
        repo.execute(Action.ResumeStopwatch("w", repo.step, clock(20_000, boot = null, session = "process-2")))
        val resumed = repo.state.value
        repo.execute(recover)
        assertEquals(resumed, repo.state.value)
        // Checked for the state it now shows, a reading from another process with no boot is still never trusted.
        repo.execute(Action.RecoverStopwatch("w", repo.step, clock(25_000, boot = null, session = "process-3")))
        assertEquals(resumed.stopwatch!!.copy(runningSince = null, session = null, stopped = true,
            needsConfirmation = true, step = resumed.stopwatch!!.step + 1), repo.watch)
    }

    @Test fun anIntentionalResumeFromReviewKeepsTimingAndLogsTheWholeEffort() = runTest {
        val repo = extra()
        repo.execute(Action.StartStopwatch("w", repo.workoutId, "x-plank", clock(0), start))
        repo.execute(Action.StopStopwatch("w", repo.step, clock(30_000)))
        assertTrue(repo.watch!!.stopped)
        repo.execute(Action.ResumeStopwatch("w", repo.step, clock(40_000)))
        assertTrue("Resume from the review times on", repo.watch!!.running)
        repo.execute(Action.StopStopwatch("w", repo.step, clock(50_000)))
        assertEquals("30 s, then 10 s more; the stopped stretch never counts", 40_000L, repo.watch!!.elapsedMs)
        repo.execute(repo.log("w", 40, repo.step, start + 60_000))
        assertNull(repo.watch)
        assertEquals(listOf(40), repo.efforts().map { it.durationSeconds })
    }

    @Test fun aStalePauseNeverCutsTheTimeOfALaterResume() = runTest {
        val repo = extra()
        repo.execute(Action.StartStopwatch("w", repo.workoutId, "x-plank", clock(0), start))
        val pause = Action.PauseStopwatch("w", repo.step, clock(10_000))
        repo.execute(pause)
        repo.execute(Action.ResumeStopwatch("w", repo.step, clock(20_000)))
        val resumed = repo.state.value
        repo.execute(pause)
        assertEquals("The running time is kept", resumed, repo.state.value)
        repo.execute(Action.StopStopwatch("w", repo.step, clock(30_000)))
        assertEquals(20_000L, repo.watch!!.elapsedMs)
    }

    @Test fun aLogForAnOlderCheckOfTheTimeIsRefusedAndKeepsTheReview() = runTest {
        val repo = extra()
        repo.execute(Action.StartStopwatch("w", repo.workoutId, "x-plank", clock(0), start))
        repo.execute(Action.StopStopwatch("w", repo.step, clock(30_000)))
        val checked = repo.log("w", 30, repo.step, start + 31_000)
        repo.execute(Action.ResumeStopwatch("w", repo.step, clock(40_000)))
        failure("Stop the stopwatch before logging") { repo.execute(checked) }
        repo.execute(Action.StopStopwatch("w", repo.step, clock(50_000)))
        val review = repo.state.value
        failure("changed since you checked its time") { repo.execute(checked) }
        failure("changed since you checked its time") { repo.execute(repo.log("w", 40, null, start + 51_000)) }
        assertSame("Nothing is logged and the review stays", review, repo.state.value)
        assertTrue(repo.efforts().isEmpty())
        repo.execute(repo.log("w", 40, repo.step, start + 52_000))
        assertEquals(listOf(40), repo.efforts().map { it.durationSeconds })
    }

    @Test fun aReplayedLogNeverOverwritesACorrectionOrTouchesTheNextStopwatch() = runTest {
        val repo = extra()
        repo.execute(Action.StartStopwatch("w", repo.workoutId, "x-plank", clock(0), start))
        repo.execute(Action.StopStopwatch("w", repo.step, clock(30_000)))
        val log = repo.log("w", 30, repo.step, start + 31_000)
        repo.execute(log)
        val rest = repo.state.value.restTimer
        assertNotNull(rest)
        // Corrected from the ledger, which never names a stopwatch step.
        repo.execute(repo.log("w", 32, null, start + 31_000))
        val corrected = repo.state.value
        assertEquals(listOf(32), repo.efforts().map { it.durationSeconds })
        assertEquals("A correction leaves the rest alone", rest, corrected.restTimer)
        repo.execute(log)
        assertSame("The replayed log keeps the correction", corrected, repo.state.value)

        repo.execute(Action.StartStopwatch("w2", repo.workoutId, "x-plank", clock(60_000), start + 60_000))
        val timing = repo.state.value
        assertNull("The next effort ends the rest", timing.restTimer)
        repo.execute(log)
        assertSame("The next stopwatch keeps running, with no rest", timing, repo.state.value)
        assertEquals(listOf(32), repo.efforts().map { it.durationSeconds })
    }

    @Test fun aLogForADiscardedReviewLogsNothingAndLeavesTheRestAlone() = runTest {
        val store = MemoryPersistence()
        val repo = extra(store)
        val log = repo.reviewedA()
        repo.execute(Action.DiscardStopwatch("a", repo.step))
        repo.execute(Action.SaveSet(repo.workoutId, "x-face", "s1", 20.0, 12, null, false, start + 40_000))
        val discarded = repo.state.value
        val saved = store.snapshot
        assertNotNull(discarded.restTimer)

        val refusal = repo.attempt(log)
        assertEquals("The discarded effort is never logged", emptyList<Effort>(), repo.efforts())
        assertEquals("The face pull's rest stays", discarded.restTimer, repo.state.value.restTimer)
        assertSame("Nothing changes", discarded, repo.state.value)
        assertEquals("Nothing is saved", saved, store.snapshot)
        assertTrue("Refused clearly: $refusal", refusal.orEmpty().contains("discarded or deleted"))
    }

    @Test fun aLogForADiscardedReviewLeavesANewerStopwatchAlone() = runTest {
        val store = MemoryPersistence()
        val repo = extra(store)
        val log = repo.reviewedA()
        repo.execute(Action.DiscardStopwatch("a", repo.step))
        repo.execute(Action.StartStopwatch("b", repo.workoutId, "x-plank", clock(40_000), start + 40_000))
        suspend fun refused(state: String, step: Int) {
            val before = repo.state.value
            val saved = store.snapshot
            val refusal = repo.attempt(log)
            assertEquals("$state: the discarded effort is never logged", emptyList<Effort>(), repo.efforts())
            assertEquals("$state: the newer stopwatch is untouched", before.stopwatch, repo.watch)
            assertNull("$state: no rest starts", repo.state.value.restTimer)
            assertSame("$state: nothing changes", before, repo.state.value)
            assertEquals("$state: nothing is saved", saved, store.snapshot)
            assertTrue("$state: refused clearly: $refusal", refusal.orEmpty().contains("discarded or deleted"))
            assertEquals("b", repo.watch!!.id)
            assertEquals(step, repo.step)
        }
        // Paused or stopped, the newer stopwatch isn't timing, so nothing else stands in the old Log's way.
        repo.execute(Action.PauseStopwatch("b", repo.step, clock(50_000)))
        refused("paused", 1)
        repo.execute(Action.StopStopwatch("b", repo.step, clock(50_000)))
        refused("stopped", 2)
        repo.execute(Action.ResumeStopwatch("b", repo.step, clock(60_000)))
        refused("running", 3)
        // The newer stopwatch's own Log still works.
        repo.execute(Action.StopStopwatch("b", repo.step, clock(65_000)))
        repo.execute(repo.log("b", 15, repo.step, start + 70_000))
        assertEquals(listOf("b" to 15), repo.efforts().map { it.id to it.durationSeconds })
    }

    @Test fun aLogForAStopwatchEffortThatWasLoggedThenDeletedNeverBringsItBack() = runTest {
        val store = MemoryPersistence()
        val repo = extra(store)
        val log = repo.reviewedA()
        repo.execute(log)
        val rest = repo.state.value.restTimer
        assertNotNull(rest)
        repo.execute(Action.DeleteEffort(repo.workoutId, "x-plank", "a"))
        val deleted = repo.state.value
        val saved = store.snapshot
        assertTrue(repo.efforts().isEmpty())

        val refusal = repo.attempt(log)
        assertEquals("The deleted effort stays deleted", emptyList<Effort>(), repo.efforts())
        assertEquals("Its rest is not started again", rest, repo.state.value.restTimer)
        assertSame("Nothing changes", deleted, repo.state.value)
        assertEquals("Nothing is saved", saved, store.snapshot)
        assertTrue("Refused clearly: $refusal", refusal.orEmpty().contains("discarded or deleted"))
    }
}
