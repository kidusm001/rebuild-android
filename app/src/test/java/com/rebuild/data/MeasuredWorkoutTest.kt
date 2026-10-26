package com.rebuild.data

import com.rebuild.domain.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class MeasuredWorkoutTest {
    private val addis = ZoneId.of("Africa/Addis_Ababa")
    /** Tuesday 29 September 2026, 18:00 in Addis Ababa. */
    private val tue = ZonedDateTime.of(2026, 9, 29, 18, 0, 0, 0, addis).toInstant().toEpochMilli()
    private val tueDate = "2026-09-29"
    private val tuesday = LocalDate.of(2026, 9, 29)
    private val rest = DEFAULT_REST_SECONDS * 1000L

    private val RebuildRepository.active: Workout get() = state.value.activeWorkout!!
    private fun Workout.entry(id: String) = exercises.first { it.id == id }
    private fun m(thousandths: Long) = Distance(thousandths, DistanceUnit.M)
    private fun km(thousandths: Long) = Distance(thousandths, DistanceUnit.KM)
    private fun mi(thousandths: Long) = Distance(thousandths, DistanceUnit.MI)

    /** A monotonic reading: [ms] since boot [boot], taken in app process [session]. */
    private fun clock(ms: Long, boot: Int? = 7, session: String = "process-1") = Monotonic(boot, session, ms)

    /** Two planks of at least 30 s, then cardio adding up to 10 minutes and 2 km. */
    private val core = listOf(
        RoutineExercise("plank", 2, null, null, 60, false, effortSeconds = 30),
        RoutineExercise("treadmill", null, null, null, 90, false, totalSeconds = 600, totalDistance = km(2000)))

    private suspend fun extra(vararg entries: Pair<String, String>, store: MemoryPersistence = MemoryPersistence()):
        RebuildRepository {
        val repo = repository(store)
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        repo.execute(Action.StartExtraWorkout(tue, tueDate))
        entries.forEach { (entry, exercise) -> repo.execute(Action.AddWorkoutExercise(repo.active.id, entry, exercise)) }
        return repo
    }

    /** The Core routine scheduled on Tuesday beside Push, started. */
    private suspend fun coreDay(): RebuildRepository {
        val repo = repository()
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        repo.execute(Action.SaveRoutine("r-core", "Core", core))
        repo.execute(Action.ScheduleRoutine("p-core", Phase.BASELINE, 2, "r-core", alongside = true))
        repo.execute(Action.StartWorkout("p-core", tue, tueDate))
        return repo
    }

    private suspend fun RebuildRepository.effort(
        entryId: String,
        id: String,
        now: Long,
        seconds: Int? = null,
        distance: Distance? = null,
        kg: Double? = null,
        workoutId: String = active.id,
        step: Int? = null
    ) = execute(Action.SaveEffort(workoutId, entryId, id, seconds, distance, kg, now, watchStep = step))

    @Test fun effortsAreLoggedExactlyAsEnteredAndOnlyHoldsAndCarriesStartTheirRest() = runTest {
        val store = MemoryPersistence()
        val repo = extra("x-plank" to "plank", "x-carry" to "farmers-carry", "x-tread" to "treadmill", store = store)
        val workout = repo.active
        assertEquals(listOf(Measure.TIMED, Measure.DISTANCE, Measure.CARDIO), workout.exercises.map { it.exercise.measure })
        assertTrue(workout.exercises.all {
            it.optional && it.efforts.isEmpty() && !it.perLeg && it.targetSets == null && it.measuredGoal.empty
        })
        assertEquals("Nothing logged or planned yet: the visible default rest", List(3) { DEFAULT_REST_SECONDS },
            workout.exercises.map { it.restSeconds })

        repo.effort("x-plank", "p1", tue + 10_000, seconds = 45)
        assertEquals(listOf(Effort("p1", 45, null, null, LoadUnit.KG, null, tue + 10_000)),
            repo.active.entry("x-plank").efforts)
        val holdRest = repo.state.value.restTimer!!
        assertEquals("x-plank", holdRest.exerciseId)
        assertEquals(tue + 10_000 + rest, holdRest.deadline)
        assertFalse(holdRest.shortPause)
        val logged = repo.state.value
        val writes = store.writes
        repo.effort("x-plank", "p1", tue + 99_000, seconds = 45)
        assertSame("A repeated request changes nothing, not even the rest", logged, repo.state.value)
        assertEquals(writes, store.writes)
        repo.effort("x-plank", "p1", tue + 99_000, seconds = 50)
        assertEquals("A correction keeps when the effort was logged",
            listOf(Effort("p1", 50, null, null, LoadUnit.KG, null, tue + 10_000)), repo.active.entry("x-plank").efforts)
        assertEquals("A correction never restarts the rest", holdRest, repo.state.value.restTimer)

        repo.effort("x-carry", "c1", tue + 20_000, distance = m(40_000), kg = 24.0)
        assertEquals(Effort("c1", null, m(40_000), 24.0, LoadUnit.KG, null, tue + 20_000),
            repo.active.entry("x-carry").efforts.single())
        assertEquals(RestTimer(repo.state.value.restTimer!!.id, workout.id, "x-carry", tue + 20_000 + rest),
            repo.state.value.restTimer)

        repo.execute(Action.SkipRest)
        repo.effort("x-tread", "t1", tue + 30_000, seconds = 1200, distance = km(2400))
        assertNull("Cardio never starts a rest by itself", repo.state.value.restTimer)
        repo.effort("x-tread", "t2", tue + 40_000, distance = mi(500))
        assertEquals("A distance without a time stays without one", listOf(1200, null),
            repo.active.entry("x-tread").efforts.map { it.durationSeconds })
        assertNull(repo.state.value.restTimer)
        repo.execute(Action.StartRest("r-1", workout.id, "x-tread", tue + 41_000))
        val asked = RestTimer("r-1", workout.id, "x-tread", tue + 41_000 + rest)
        assertEquals("Rest between cardio intervals only when asked for", asked, repo.state.value.restTimer)
        repo.execute(Action.StartRest("r-1", workout.id, "x-tread", tue + 50_000))
        assertEquals("A repeated request never moves the rest", asked, repo.state.value.restTimer)
        failure("Rest starts by itself") { repo.execute(Action.StartRest("r-2", workout.id, "x-plank", tue + 50_000)) }

        repo.execute(Action.FinishWorkout(tue + 60_000))
        val done = repo.state.value.workouts.single()
        assertEquals("Efforts are real work but never working sets", 0, done.workingSetCount())
        assertEquals(4, done.performedCount())
        assertTrue(done.hasWorkingEffort())
        assertFalse(done.countsForWeek())
        val week = repo.state.value.weekChecklist(tuesday, addis)!!
        assertEquals(listOf(done.id), week.extras.map { it.id })
        assertEquals(0, week.done)
        assertEquals(done, SnapshotCodec.decode(store.snapshot!!).workouts.single())
    }

    @Test fun eachEffortRecordsWhatItsMeasurementNeedsAndSharesOneIdSpaceWithSets() = runTest {
        val repo = extra("x-plank" to "plank", "x-weighted" to "plank-weighted", "x-carry" to "farmers-carry",
            "x-tread" to "treadmill", "x-face" to "face-pull")
        val id = repo.active.id
        val before = repo.state.value
        failure("how long") { repo.effort("x-plank", "e", tue) }
        failure("not a distance") { repo.effort("x-plank", "e", tue, seconds = 30, distance = m(1000)) }
        failure("1 second to 24 hours") { repo.effort("x-plank", "e", tue, seconds = 0) }
        failure("1 second to 24 hours") { repo.effort("x-plank", "e", tue, seconds = MAX_EFFORT_SECONDS + 1) }
        failure("Bodyweight") { repo.effort("x-plank", "e", tue, seconds = 30, kg = 10.0) }
        failure("Weight or assistance") { repo.effort("x-weighted", "e", tue, seconds = 30) }
        failure("distance covered") { repo.effort("x-carry", "e", tue, seconds = 60, kg = 24.0) }
        failure("Weight or assistance") { repo.effort("x-carry", "e", tue, distance = m(40_000)) }
        failure("more than 0") { repo.effort("x-carry", "e", tue, distance = m(0), kg = 24.0) }
        failure("at most 1,000 mi") { repo.effort("x-carry", "e", tue, distance = mi(1_000_001), kg = 24.0) }
        failure("Plate counts are only available") {
            repo.execute(Action.SaveEffort(id, "x-carry", "e", null, m(40_000), null, tue, LoadUnit.PLATES, 4))
        }
        failure("a duration, a distance or both") { repo.effort("x-tread", "e", tue) }
        failure("Cardio doesn't record a load") { repo.effort("x-tread", "e", tue, seconds = 600, kg = 5.0) }
        failure("sets of reps") { repo.effort("x-face", "e", tue, seconds = 30) }
        failure("logged as efforts") { repo.execute(Action.SaveSet(id, "x-plank", "e", null, 10, null, false, tue)) }
        assertSame("Refused requests change nothing", before, repo.state.value)

        repo.effort("x-weighted", "w1", tue + 1000, seconds = 40, kg = 10.0)
        repo.execute(Action.SaveSet(id, "x-face", "s1", 20.0, 12, null, false, tue + 2000))
        repo.execute(Action.SkipRest)
        val logged = repo.state.value
        failure("already used") { repo.effort("x-plank", "s1", tue + 3000, seconds = 30) }
        failure("already used") { repo.effort("x-carry", "w1", tue + 3000, distance = m(10_000), kg = 20.0) }
        failure("already used") { repo.execute(Action.SaveSet(id, "x-face", "w1", 20.0, 12, null, false, tue + 3000)) }
        failure("already used") {
            repo.execute(Action.LogStage(id, "x-face", "g1", SetMethod.DROP, "w1", 20.0, 8, null, tue + 3000))
        }
        failure("already used") { repo.execute(Action.StartStopwatch("s1", id, "x-plank", clock(1000), tue + 3000)) }
        assertSame(logged, repo.state.value)
    }

    @Test fun theStopwatchCountsOnlyPerformedTimeAndStoppingLeavesAnUnsavedReview() = runTest {
        val store = MemoryPersistence()
        val repo = extra("x-plank" to "plank", "x-tread" to "treadmill", store = store)
        val id = repo.active.id
        repo.effort("x-plank", "p1", tue + 1000, seconds = 30)
        assertNotNull(repo.state.value.restTimer)
        repo.execute(Action.StartStopwatch("sw-1", id, "x-plank", clock(10_000), tue + 5000))
        val started = repo.state.value
        assertEquals(Stopwatch("sw-1", id, "x-plank", 0, 10_000, 7, "process-1", false, false, tue + 5000, 0),
            started.stopwatch)
        assertNull("Starting an effort ends the rest", started.restTimer)
        repo.execute(Action.StartStopwatch("sw-1", id, "x-plank", clock(12_000), tue + 7000))
        assertSame("A repeated Start changes nothing", started, repo.state.value)
        failure("already has a timed effort") {
            repo.execute(Action.StartStopwatch("sw-2", id, "x-plank", clock(12_000), tue + 7000))
        }
        failure("The stopwatch has an effort on Plank") {
            repo.execute(Action.StartStopwatch("sw-2", id, "x-tread", clock(12_000), tue + 7000))
        }

        repo.execute(Action.PauseStopwatch("sw-1", 0, clock(40_500)))
        val paused = repo.state.value
        assertEquals(Stopwatch("sw-1", id, "x-plank", 30_500, null, null, null, false, false, tue + 5000, 1), paused.stopwatch)
        repo.execute(Action.PauseStopwatch("sw-1", 1, clock(90_000)))
        assertSame("Paused time is never counted", paused, repo.state.value)
        repo.execute(Action.ResumeStopwatch("sw-1", 1, clock(100_000, session = "process-2")))
        val resumed = repo.state.value
        assertEquals(Stopwatch("sw-1", id, "x-plank", 30_500, 100_000, 7, "process-2", false, false, tue + 5000, 2),
            resumed.stopwatch)
        // Requests shown for any other stopwatch never touch this one.
        repo.execute(Action.PauseStopwatch("sw-old", 2, clock(101_000)))
        repo.execute(Action.ResumeStopwatch("sw-old", 2, clock(101_000)))
        repo.execute(Action.StopStopwatch("sw-old", 2, clock(101_000)))
        repo.execute(Action.DiscardStopwatch("sw-old", 2))
        repo.execute(Action.RecoverStopwatch("sw-old", 2, clock(101_000, boot = 99)))
        // A late reading from before the resume, in the same process, is ignored rather than trusted.
        repo.execute(Action.StopStopwatch("sw-1", 2, clock(99_000, session = "process-2")))
        assertSame(resumed, repo.state.value)

        repo.execute(Action.StopStopwatch("sw-1", 2, clock(115_999, session = "process-2")))
        val review = repo.state.value
        assertEquals(Stopwatch("sw-1", id, "x-plank", 46_499, null, null, null, true, false, tue + 5000, 3), review.stopwatch)
        assertEquals("Stopping logs nothing", listOf("p1"), review.activeWorkout!!.entry("x-plank").efforts.map { it.id })
        assertNull("Stopping starts no rest", review.restTimer)
        repo.execute(Action.StopStopwatch("sw-1", 3, clock(200_000)))
        repo.execute(Action.PauseStopwatch("sw-1", 3, clock(200_000)))
        assertSame(review, repo.state.value)
        assertEquals("The review survives the app restarting", review, repository(store).state.value)

        repo.effort("x-plank", "sw-1", tue + 120_000, seconds = wholeSeconds(46_499).toInt(), step = 3)
        val logged = repo.state.value
        assertNull(logged.stopwatch)
        assertEquals(Effort("sw-1", 46, null, null, LoadUnit.KG, null, tue + 120_000),
            logged.activeWorkout!!.entry("x-plank").efforts.last())
        assertEquals("Logging the hold starts its rest", tue + 120_000 + rest, logged.restTimer!!.deadline)
        repo.effort("x-plank", "sw-1", tue + 130_000, seconds = 46, step = 3)
        repo.execute(Action.StartStopwatch("sw-1", id, "x-plank", clock(300_000), tue + 130_000))
        assertSame("A repeated Log or Start of a logged effort changes nothing", logged, repo.state.value)
    }

    @Test fun nothingNewIsLoggedWhileTimingAndPausingKeepsTheTimeForLater() = runTest {
        val repo = extra("x-plank" to "plank", "x-tread" to "treadmill", "x-face" to "face-pull", "x-row" to "row-db")
        val id = repo.active.id
        repo.execute(Action.StartStopwatch("sw", id, "x-plank", clock(0), tue))
        val timing = repo.state.value
        failure("Pause or stop it first") { repo.execute(Action.SaveSet(id, "x-face", "s1", 20.0, 12, null, false, tue + 1)) }
        failure("Pause or stop it first") {
            repo.execute(Action.LogStage(id, "x-face", "g1", SetMethod.DROP, "s1", 20.0, 12, null, tue + 1))
        }
        failure("Pause or stop it first") { repo.effort("x-tread", "t1", tue + 1, seconds = 60) }
        failure("Pause or stop it first") { repo.execute(Action.StartRest("r1", id, "x-tread", tue + 1)) }
        failure("Stop the stopwatch before logging") { repo.effort("x-plank", "sw", tue + 1, seconds = 60, step = 0) }
        failure("isn't logged") { repo.execute(Action.RemoveWorkoutExercise(id, "x-plank", 0)) }
        assertSame(timing, repo.state.value)
        // Adding an exercise logs nothing, so it doesn't wait for the stopwatch.
        repo.execute(Action.AddWorkoutExercise(id, "x-hang", "dead-hang"))
        assertTrue(repo.state.value.stopwatch!!.running)

        repo.execute(Action.PauseStopwatch("sw", 0, clock(20_000)))
        failure("Stop the stopwatch before logging") { repo.effort("x-plank", "sw", tue + 2, seconds = 20, step = 1) }
        repo.execute(Action.LogStage(id, "x-face", "g1", SetMethod.DROP, "s1", 20.0, 12, null, tue + 2))
        failure("Finish the drop set on Cable face pull") { repo.execute(Action.ResumeStopwatch("sw", 1, clock(30_000))) }
        repo.execute(Action.FinishGroup(id, "x-face", "g1", tue + 3))
        assertNotNull("A finished drop set rests while the stopwatch is paused", repo.state.value.restTimer)
        repo.execute(Action.ResumeStopwatch("sw", 1, clock(30_000)))
        assertNull("Resuming the effort ends the rest", repo.state.value.restTimer)
        assertEquals(20_000L, repo.state.value.stopwatch!!.elapsedMs)
        repo.execute(Action.DiscardStopwatch("sw", 2))
        assertNull(repo.state.value.stopwatch)
        assertTrue("Discarding logs nothing", repo.active.entry("x-plank").efforts.isEmpty())
        failure("clock reading is invalid") { repo.execute(Action.StartStopwatch("sw-2", id, "x-tread", clock(-1), tue + 4)) }
        assertNull(repo.state.value.stopwatch)

        repo.execute(Action.LogStage(id, "x-face", "g2", SetMethod.DROP, "s2", 18.0, 10, null, tue + 4))
        failure("Finish the drop set on Cable face pull") {
            repo.execute(Action.StartStopwatch("sw-2", id, "x-tread", clock(40_000), tue + 5))
        }
        failure("logged in sets of reps") { repo.execute(Action.StartStopwatch("sw-2", id, "x-row", clock(40_000), tue + 5)) }
    }

    @Test fun aStopwatchWhoseTimeCantBeTrustedStopsForATypedDurationWithNoEstimate() = runTest {
        val store = MemoryPersistence()
        val repo = extra("x-plank" to "plank", "x-tread" to "treadmill", store = store)
        val id = repo.active.id
        repo.execute(Action.StartStopwatch("sw-1", id, "x-plank", clock(5_000), tue))
        // The app process was killed; on the same boot the monotonic clock kept counting, so the time is trusted.
        val restarted = repository(store)
        restarted.execute(Action.RecoverStopwatch("sw-1", 0, clock(50_000, session = "process-2")))
        assertEquals(repo.state.value, restarted.state.value)
        restarted.execute(Action.StopStopwatch("sw-1", 0, clock(65_000, session = "process-2")))
        assertEquals(60_000L, restarted.state.value.stopwatch!!.elapsedMs)
        assertFalse(restarted.state.value.stopwatch!!.needsConfirmation)
        restarted.effort("x-plank", "sw-1", tue + 70_000, seconds = 60, workoutId = id, step = 1)
        restarted.execute(Action.SkipRest)

        restarted.execute(Action.StartStopwatch("sw-2", id, "x-tread", clock(80_000, session = "process-2"), tue + 80_000))
        restarted.execute(Action.PauseStopwatch("sw-2", 0, clock(140_000, session = "process-2")))
        restarted.execute(Action.ResumeStopwatch("sw-2", 1, clock(150_000, session = "process-2")))
        // The phone restarted: the time since the last resume can't be known.
        val rebooted = repository(store)
        rebooted.execute(Action.RecoverStopwatch("sw-2", 2, clock(3_000, boot = 8, session = "process-3")))
        val untrusted = rebooted.state.value.stopwatch!!
        assertEquals("Only the time recorded before the restart is kept",
            Stopwatch("sw-2", id, "x-tread", 60_000, null, null, null, true, true, tue + 80_000, 3), untrusted)
        failure("couldn't be measured") {
            rebooted.execute(Action.ResumeStopwatch("sw-2", 3, clock(4_000, boot = 8, session = "process-3")))
        }
        rebooted.execute(Action.StopStopwatch("sw-2", 3, clock(5_000, boot = 8, session = "process-3")))
        rebooted.execute(Action.PauseStopwatch("sw-2", 3, clock(5_000, boot = 8, session = "process-3")))
        rebooted.execute(Action.RecoverStopwatch("sw-2", 3, clock(5_000, boot = 7, session = "process-1")))
        assertEquals(untrusted, rebooted.state.value.stopwatch)
        assertEquals("It stays unconfirmed across restarts", untrusted, repository(store).state.value.stopwatch)
        // The user types the duration they know.
        rebooted.effort("x-tread", "sw-2", tue + 900_000, seconds = 900, distance = km(2500), workoutId = id, step = 3)
        assertNull(rebooted.state.value.stopwatch)
        assertEquals(Effort("sw-2", 900, km(2500), null, LoadUnit.KG, null, tue + 900_000),
            rebooted.state.value.activeWorkout!!.entry("x-tread").efforts.single())
        assertNull("Cardio never starts the rest", rebooted.state.value.restTimer)
    }

    @Test fun finishingNeedsAnUnloggedEffortDiscardedExplicitlyAndDiscardingTheWorkoutClearsIt() = runTest {
        val repo = extra("x-plank" to "plank")
        val id = repo.active.id
        repo.execute(Action.StartStopwatch("sw", id, "x-plank", clock(0), tue))
        repo.execute(Action.StopStopwatch("sw", 0, clock(45_000)))
        failure("isn't logged. Log it, or confirm discarding it") { repo.execute(Action.FinishWorkout(tue + 60_000)) }
        failure("Log at least one set or effort") {
            repo.execute(Action.FinishWorkout(tue + 60_000, discardStopwatch = true))
        }
        assertEquals("A refused finish keeps the review", 45_000L, repo.state.value.stopwatch!!.elapsedMs)
        repo.effort("x-plank", "p1", tue + 50_000, seconds = 20)
        repo.execute(Action.FinishWorkout(tue + 60_000, discardStopwatch = true))
        val finished = repo.state.value
        assertNull(finished.stopwatch)
        assertNull(finished.restTimer)
        assertNull(finished.activeWorkoutId)
        assertEquals("Only what was logged is history", listOf("p1"),
            finished.workouts.single().exercises.single().efforts.map { it.id })

        repo.execute(Action.StartExtraWorkout(tue + 100_000, tueDate))
        repo.execute(Action.AddWorkoutExercise(repo.active.id, "y-plank", "plank"))
        repo.execute(Action.StartStopwatch("sw-2", repo.active.id, "y-plank", clock(100_000), tue + 100_000))
        repo.execute(Action.DiscardWorkout)
        assertNull(repo.state.value.stopwatch)
        assertEquals(finished.workouts, repo.state.value.workouts)
    }

    @Test fun aRoutineOfHoldsAndCardioIsDoneOnlyByActualMeasurementsAndAddedGoalsNeverBlockIt() = runTest {
        val repo = coreDay()
        val workout = repo.active
        val (plank, tread) = workout.exercises
        assertEquals(listOf("plank", "treadmill"), workout.exercises.map { it.exercise.id })
        assertTrue(workout.exercises.none { it.optional })
        assertEquals(MeasuredGoal(effortSeconds = 30), plank.measuredGoal)
        assertEquals(listOf(2, null, null, null, 60), listOf(plank.targetSets, plank.minReps, plank.maxReps,
            plank.targetRir, plank.restSeconds))
        assertEquals(MeasuredGoal(totalSeconds = 600, totalDistance = km(2000)), tread.measuredGoal)
        assertNull(tread.targetSets)
        repo.execute(Action.AddWorkoutExercise(workout.id, "o-hang", "dead-hang"))
        repo.execute(Action.SetEntryGoal(workout.id, "o-hang", 1, null, null, 45, false, effortSeconds = 60))
        assertEquals(MeasuredGoal(effortSeconds = 60), repo.active.entry("o-hang").measuredGoal)

        repo.effort(plank.id, "p1", tue + 1000, seconds = 29)
        repo.effort(plank.id, "p2", tue + 2000, seconds = 30)
        assertEquals("29 s is short of the 30 s minimum", 1, repo.active.entry(plank.id).qualifyingCount())
        repo.effort(tread.id, "t1", tue + 3000, seconds = 300, distance = km(1200))
        repo.effort(tread.id, "t2", tue + 4000, seconds = 300, distance = mi(500))
        assertTrue("10 minutes and 2,004.672 m", repo.active.entry(tread.id).goalReached())
        assertFalse(repo.active.targetsMet())
        // Today's target changes within its kind of measurement, and logged efforts are never converted.
        failure("measured the same way") { repo.execute(Action.EditToday(plank.id, "face-pull", 2, 60)) }
        failure("Logged efforts cannot be converted") { repo.execute(Action.EditToday(plank.id, "side-plank", 2, 60)) }
        failure("total time or distance instead of sets") { repo.execute(Action.EditToday(tread.id, "treadmill", 3, 90)) }
        failure("already reached its target") { repo.execute(Action.SetSkipped(workout.id, tread.id, true)) }
        repo.execute(Action.EditToday(plank.id, "plank", 3, 60))
        assertEquals(3, repo.active.entry(plank.id).targetSets)
        assertEquals("The minimum stays as planned", 30, repo.active.entry(plank.id).effortSeconds)
        repo.execute(Action.EditToday(tread.id, "treadmill", null, 120, saveToPlan = true))
        val plan = repo.state.value.planAdditions.filter { it.templateId == "p-core" }
        assertEquals("Only an explicit save reaches the plan", listOf(2 to 60, null to 120),
            plan.map { it.sets to it.restSeconds })
        assertEquals(listOf(MeasuredGoal(effortSeconds = 30), MeasuredGoal(totalSeconds = 600, totalDistance = km(2000))),
            plan.map { it.measuredGoal })
        repo.effort(plank.id, "p3", tue + 5000, seconds = 31)
        assertFalse("Today's target is three", repo.active.targetsMet())
        repo.effort(plank.id, "p4", tue + 6000, seconds = 95)
        assertTrue("The added hang's own goal never blocks the routine", repo.active.targetsMet())
        assertFalse(repo.active.entry("o-hang").goalReached())

        repo.execute(Action.FinishWorkout(tue + 10_000))
        val done = repo.state.value.workouts.single()
        assertTrue(done.completesRoutine())
        assertTrue(done.countsForWeek())
        assertFalse("Done by its targets, not by a choice", done.countedForWeek)
        val week = repo.state.value.weekChecklist(tuesday, addis)!!
        assertEquals(RoutineStatus.DONE, week.statusOf("p-core"))
        assertEquals(done.id, week.routines.single { it.template.id == "p-core" }.doneBy!!.id)
        assertEquals(RoutineStatus.NOT_DONE, week.statusOf("push"))
    }

    @Test fun anEffortShortOfItsGoalIsRealWorkThatCountsOnlyByExplicitCredit() = runTest {
        val repo = coreDay()
        val workout = repo.active
        val (plank, tread) = workout.exercises
        repo.execute(Action.StartStopwatch("sw", workout.id, plank.id, clock(0), tue))
        failure("before swapping it") { repo.execute(Action.EditToday(plank.id, "side-plank", 2, 60)) }
        failure("before skipping it") { repo.execute(Action.SetSkipped(workout.id, plank.id, true)) }
        repo.execute(Action.StopStopwatch("sw", 0, clock(20_400)))
        repo.effort(plank.id, "sw", tue + 21_000, seconds = 20, step = 1)
        repo.execute(Action.SetSkipped(workout.id, tread.id, true))
        failure("skipped today") { repo.effort(tread.id, "t1", tue + 30_000, seconds = 60) }
        failure("skipped today") {
            repo.execute(Action.StartStopwatch("sw-2", workout.id, tread.id, clock(30_000), tue + 30_000))
        }
        repo.execute(Action.FinishWorkout(tue + 60_000))
        val done = repo.state.value.workouts.single()
        assertTrue(done.hasWorkingEffort())
        assertFalse(done.targetsMet())
        assertFalse(done.countsForWeek())
        assertEquals("A skipped exercise keeps its goal", MeasuredGoal(totalSeconds = 600, totalDistance = km(2000)),
            done.exercises[1].measuredGoal)
        assertEquals(RoutineStatus.PARTIAL, repo.state.value.weekChecklist(tuesday, addis)!!.statusOf("p-core"))
        repo.execute(Action.SetWeekCredit(done.id, true))
        assertEquals(RoutineStatus.DONE, repo.state.value.weekChecklist(tuesday, addis)!!.statusOf("p-core"))
        repo.execute(Action.SetWeekCredit(done.id, false))
        assertEquals(RoutineStatus.PARTIAL, repo.state.value.weekChecklist(tuesday, addis)!!.statusOf("p-core"))
    }

    @Test fun aForgottenEffortIsAddedInHistoryWithoutTimersRestOrPlanChanges() = runTest {
        val repo = extra("x-face" to "face-pull")
        val first = repo.active.id
        repo.execute(Action.SaveSet(first, "x-face", "s1", 20.0, 12, null, false, tue + 1000))
        repo.execute(Action.FinishWorkout(tue + 60_000))
        repo.execute(Action.StartExtraWorkout(tue + 3_600_000, tueDate))
        val current = repo.active.id
        repo.execute(Action.AddWorkoutExercise(current, "y-plank", "plank"))
        val plan = repo.state.value.let { Triple(it.planAdditions, it.routines, it.placements) }

        repo.execute(Action.AddWorkoutExercise(first, "h-plank", "plank"))
        repo.execute(Action.AddWorkoutExercise(first, "h-tread", "treadmill"))
        failure("active workout") {
            repo.execute(Action.StartStopwatch("sw", first, "h-plank", clock(0), tue + 3_700_000))
        }
        failure("Rest is timed in the active workout") {
            repo.execute(Action.StartRest("r", first, "h-tread", tue + 3_700_000))
        }
        repo.effort("h-plank", "hp1", tue + 3_700_000, seconds = 60, workoutId = first)
        repo.effort("h-tread", "ht1", tue + 3_700_000, distance = km(3000), workoutId = first)
        assertNull("A correction never starts the rest", repo.state.value.restTimer)
        repo.effort("h-plank", "hp1", tue + 3_800_000, seconds = 65, workoutId = first)
        repo.execute(Action.DeleteEffort(first, "h-tread", "ht1"))
        val corrected = repo.state.value.workouts.first { it.id == first }
        assertEquals(listOf(65), corrected.entry("h-plank").efforts.map { it.durationSeconds })
        assertTrue(corrected.entry("h-tread").efforts.isEmpty())
        assertEquals(tue + 60_000, corrected.finishedAt)
        assertEquals(plan, repo.state.value.let { Triple(it.planAdditions, it.routines, it.placements) })
        // The correction is real history: the live plank compares with it.
        val plankDefinition = repo.active.entry("y-plank").exercise
        assertEquals(listOf("hp1"), repo.state.value.previousEfforts(plankDefinition, current, LoadUnit.KG)!!
            .efforts.map { it.id })
        // Correcting history doesn't wait for, or touch, the live stopwatch.
        repo.execute(Action.StartStopwatch("sw-live", current, "y-plank", clock(0), tue + 3_900_000))
        repo.effort("h-plank", "hp2", tue + 3_900_000, seconds = 30, workoutId = first)
        assertTrue(repo.state.value.stopwatch!!.running)
        assertNull(repo.state.value.restTimer)
    }

    @Test fun removingAnAddedMeasuredExerciseConfirmsExactlyTheEffortsItDeletes() = runTest {
        val repo = extra("x-plank" to "plank", "x-tread" to "treadmill")
        val id = repo.active.id
        repo.effort("x-plank", "p1", tue + 1000, seconds = 30)
        repo.effort("x-plank", "p2", tue + 2000, seconds = 35)
        assertEquals(2, repo.active.entry("x-plank").recordCount())
        failure("changed since you confirmed") { repo.execute(Action.RemoveWorkoutExercise(id, "x-plank", 1)) }
        repo.execute(Action.RemoveWorkoutExercise(id, "x-plank", 2))
        assertNull("Removing the exercise ends its rest", repo.state.value.restTimer)
        assertEquals(listOf("x-tread"), repo.active.exercises.map { it.id })
        repo.execute(Action.StartStopwatch("sw", id, "x-tread", clock(0), tue + 3000))
        repo.execute(Action.StopStopwatch("sw", 0, clock(60_000)))
        failure("isn't logged") { repo.execute(Action.RemoveWorkoutExercise(id, "x-tread", 0)) }
        repo.execute(Action.DiscardStopwatch("sw", 1))
        repo.execute(Action.RemoveWorkoutExercise(id, "x-tread", 0))
        assertTrue(repo.active.exercises.isEmpty())
    }

    @Test fun aStopwatchPastTheDayLimitStaysReviewableAndLogsOnlyATypedTime() = runTest {
        val store = MemoryPersistence()
        val repo = extra("x-plank" to "plank", store = store)
        val id = repo.active.id
        repo.execute(Action.StartStopwatch("sw", id, "x-plank", clock(0), tue))
        // Left running past a day: Stop still ends timing, and the review keeps the whole measured time.
        repo.execute(Action.StopStopwatch("sw", 0, clock(86_405_000)))
        val review = repo.state.value
        assertEquals(Stopwatch("sw", id, "x-plank", 86_405_000, null, null, null, true, false, tue, 1), review.stopwatch)
        failure("24 hours") { repo.effort("x-plank", "sw", tue + 86_406_000, seconds = 86_405, step = 1) }
        assertSame("Refused, the review stays to be corrected", review, repo.state.value)
        assertEquals("It survives the app restarting", review, repository(store).state.value)
        repo.effort("x-plank", "sw", tue + 86_406_000, seconds = 86_400, step = 1)
        assertNull(repo.state.value.stopwatch)
        assertEquals(listOf(86_400), repo.active.entry("x-plank").efforts.map { it.durationSeconds })

        // Past what the stopwatch can hold, the time can't be trusted, but it still stops and can be discarded.
        repo.execute(Action.StartStopwatch("sw-2", id, "x-plank", clock(90_000_000), tue + 86_500_000))
        repo.execute(Action.StopStopwatch("sw-2", 0, clock(90_000_000 + MAX_STOPWATCH_MS + 1)))
        val untrusted = repo.state.value.stopwatch!!
        assertTrue(untrusted.stopped && untrusted.needsConfirmation)
        assertEquals(0L, untrusted.elapsedMs)
        repo.execute(Action.DiscardStopwatch("sw-2", untrusted.step))
        assertNull(repo.state.value.stopwatch)
        assertEquals(listOf(86_400), repo.active.entry("x-plank").efforts.map { it.durationSeconds })
    }

    @Test fun aPausedStopwatchKeepsItsTimeThroughRestartsAndAResumeAfterAReboot() = runTest {
        val store = MemoryPersistence()
        val repo = extra("x-plank" to "plank", store = store)
        val id = repo.active.id
        repo.execute(Action.StartStopwatch("sw", id, "x-plank", clock(0), tue))
        repo.execute(Action.PauseStopwatch("sw", 0, clock(30_000)))
        val paused = repo.state.value
        // The app was recreated, then the phone restarted: paused time has no running part to doubt.
        val restarted = repository(store)
        assertEquals(paused, restarted.state.value)
        restarted.execute(Action.RecoverStopwatch("sw", 1, clock(5_000, boot = 8, session = "process-2")))
        assertEquals(paused, restarted.state.value)
        restarted.execute(Action.ResumeStopwatch("sw", 1, clock(1_000, boot = 8, session = "process-2")))
        restarted.execute(Action.StopStopwatch("sw", 2, clock(11_000, boot = 8, session = "process-2")))
        val review = restarted.state.value.stopwatch!!
        assertEquals("30 s before the restart and 10 s after it", 40_000L, review.elapsedMs)
        assertFalse(review.needsConfirmation)
    }

    @Test fun aFailedSaveKeepsTheStopwatchTheEffortsAndTheRestExactlyAsTheyWere() = runTest {
        val store = MemoryPersistence()
        val repo = extra("x-plank" to "plank", store = store)
        val id = repo.active.id
        repo.effort("x-plank", "p1", tue + 1000, seconds = 30)
        val resting = repo.state.value
        assertNotNull(resting.restTimer)
        store.failWrites = true
        failure("Could not save") { repo.execute(Action.StartStopwatch("sw", id, "x-plank", clock(10_000), tue + 5000)) }
        assertSame("A start that wasn't saved doesn't end the rest", resting, repo.state.value)
        store.failWrites = false
        repo.execute(Action.StartStopwatch("sw", id, "x-plank", clock(10_000), tue + 5000))
        val running = repo.state.value
        store.failWrites = true
        failure("Could not save") { repo.execute(Action.PauseStopwatch("sw", 0, clock(20_000))) }
        failure("Could not save") { repo.execute(Action.StopStopwatch("sw", 0, clock(40_000))) }
        assertSame("Still running, with all its time", running, repo.state.value)
        store.failWrites = false
        repo.execute(Action.StopStopwatch("sw", 0, clock(40_000)))
        val review = repo.state.value
        assertEquals(30_000L, review.stopwatch!!.elapsedMs)
        store.failWrites = true
        failure("Could not save") { repo.effort("x-plank", "sw", tue + 41_000, seconds = 30, step = 1) }
        failure("Could not save") { repo.execute(Action.DiscardStopwatch("sw", 1)) }
        failure("Could not save") { repo.execute(Action.ResumeStopwatch("sw", 1, clock(45_000))) }
        assertSame("The review and the earlier effort stay", review, repo.state.value)
        store.failWrites = false
        assertEquals("Storage still holds the review", review, repository(store).state.value)
        repo.effort("x-plank", "sw", tue + 41_000, seconds = 30, step = 1)
        assertEquals(listOf("p1", "sw"), repo.active.entry("x-plank").efforts.map { it.id })
    }

    @Test fun aMeasuredWorkoutSavedAsARoutineKeepsOnlyItsReviewedGoals() = runTest {
        val repo = coreDay()
        val workout = repo.active
        val (plank, tread) = workout.exercises
        repo.execute(Action.AddWorkoutExercise(workout.id, "o-carry", "farmers-carry"))
        repo.execute(Action.SetEntryGoal(workout.id, "o-carry", 3, null, null, 120, false, effortDistance = m(40_000)))
        repo.execute(Action.AddWorkoutExercise(workout.id, "o-hang", "dead-hang"))
        repo.effort(plank.id, "p1", tue + 1000, seconds = 45)
        repo.effort(plank.id, "p2", tue + 2000, seconds = 50)
        repo.effort(tread.id, "t1", tue + 3000, seconds = 900, distance = km(3000))
        repo.effort("o-carry", "c1", tue + 4000, distance = m(45_000), kg = 24.0)
        repo.effort("o-hang", "h1", tue + 5000, seconds = 60)
        repo.execute(Action.FinishWorkout(tue + 10_000))
        val state = repo.state.value
        val draft = routineDraftFrom(state.workouts.single())
        assertEquals("Core", draft.name)
        assertEquals("Performed times, distances and loads never become goals", listOf(
            DraftExercise(plank.id, "plank", 2, null, null, 60, false, effortSeconds = 30),
            DraftExercise(tread.id, "treadmill", null, null, null, 90, false, totalSeconds = 600,
                totalDistance = km(2000)),
            DraftExercise("o-carry", "farmers-carry", 3, null, null, 120, false, effortDistance = m(40_000)),
            DraftExercise("o-hang", "dead-hang", null, null, null, DEFAULT_REST_SECONDS, false)
        ), draft.exercises)
        assertEquals("The hang had no goal, so it needs one entered",
            mapOf("o-hang" to setOf(DraftProblem.NEEDS_TARGETS)), state.draftProblems(draft, null))
        assertNull(state.confirmedExercises(draft))
        val reviewed = draft.copy(exercises = draft.exercises.map {
            if (it.key == "o-hang") it.copy(sets = 1, effortSeconds = 45) else it
        })
        assertEquals(listOf(
            RoutineExercise("plank", 2, null, null, 60, false, effortSeconds = 30),
            RoutineExercise("treadmill", null, null, null, 90, false, totalSeconds = 600, totalDistance = km(2000)),
            RoutineExercise("farmers-carry", 3, null, null, 120, false, effortDistance = m(40_000)),
            RoutineExercise("dead-hang", 1, null, null, DEFAULT_REST_SECONDS, false, effortSeconds = 45)
        ), state.confirmedExercises(reviewed))
    }
}
