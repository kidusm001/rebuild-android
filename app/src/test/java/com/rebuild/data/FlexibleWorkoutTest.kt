package com.rebuild.data

import com.rebuild.domain.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class FlexibleWorkoutTest {
    private val addis = ZoneId.of("Africa/Addis_Ababa")
    /** Tuesday 29 September 2026, 18:00 in Addis Ababa. */
    private val tue = ZonedDateTime.of(2026, 9, 29, 18, 0, 0, 0, addis).toInstant().toEpochMilli()
    private val tueDate = "2026-09-29"
    private val thursday = LocalDate.of(2026, 10, 1)

    private val RebuildRepository.active: Workout get() = state.value.activeWorkout!!
    private fun Workout.entry(id: String) = exercises.first { it.id == id }
    private fun Workout.of(exerciseId: String) = exercises.first { it.exercise.id == exerciseId }

    private suspend fun planned(template: String = "push", phase: Phase = Phase.BASELINE): RebuildRepository {
        val repo = repository()
        repo.execute(Action.SelectPhase(phase))
        repo.execute(Action.StartWorkout(template, tue, tueDate))
        return repo
    }

    private suspend fun RebuildRepository.set(entryId: String, setId: String, now: Long, kg: Double? = 20.0,
                                              reps: Int = 10, warmup: Boolean = false, workoutId: String = active.id) =
        execute(Action.SaveSet(workoutId, entryId, setId, kg, reps, null, warmup, now))

    /** Logs every required exercise's target in working sets; added entries are left alone. */
    private suspend fun RebuildRepository.meetTargets(prefix: String, now: Long) {
        var n = 0
        active.exercises.filter { !it.optional && !it.skipped }.forEach { e ->
            repeat((e.targetSets!! - e.workingCount()).coerceAtLeast(0)) { set(e.id, "$prefix-${n++}", now + n) }
        }
    }

    @Test fun extraWorkoutStartsEmptyLogsImmediatelyWithoutTargetsAndNeverCounts() = runTest {
        val repo = repository()
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        repo.execute(Action.StartExtraWorkout(tue, tueDate))
        val extra = repo.active
        assertTrue(extra.extra)
        assertNull(extra.templateId)
        assertEquals(EXTRA_WORKOUT_NAME, extra.name)
        assertEquals(tueDate, extra.startDate)
        assertTrue(extra.exercises.isEmpty())
        repo.execute(Action.AddWorkoutExercise(extra.id, "x-face", "face-pull"))
        val entry = repo.active.exercises.single()
        assertTrue(entry.optional)
        assertNull(entry.slotId)
        assertNull(entry.targetSets)
        assertNull(entry.minReps)
        assertNull(entry.maxReps)
        assertNull(entry.targetRir)
        assertNull(entry.baselineSets)
        assertNull(entry.upperSets)
        assertEquals(DEFAULT_REST_SECONDS, entry.restSeconds)
        assertNull("Adding an exercise never starts a timer", repo.state.value.restTimer)
        repo.set("x-face", "x-1", tue + 60_000, kg = 15.0, reps = 15)
        assertEquals(tue + 60_000 + 90_000, repo.state.value.restTimer!!.deadline)
        assertEquals("x-face", repo.state.value.restTimer!!.exerciseId)
        failure("never count") { repo.execute(Action.FinishWorkout(tue + 120_000, countForWeek = true)) }
        assertNotNull(repo.state.value.activeWorkoutId)
        repo.execute(Action.FinishWorkout(tue + 120_000))
        val done = repo.state.value.workouts.single()
        assertFalse(done.countsForWeek())
        assertFalse(done.targetsMet())
        assertTrue(done.hasWorkingEffort())
        failure("never count") { repo.execute(Action.SetWeekCredit(done.id, true)) }
        val week = repo.state.value.weekChecklist(thursday, addis)!!
        assertEquals(listOf(done.id), week.extras.map { it.id })
        assertTrue(week.other.isEmpty())
        assertEquals(0, week.done)
        assertTrue(week.routines.all { it.status == RoutineStatus.NOT_DONE })
        // It is real history: the next time face pulls are logged, they compare with this workout.
        assertEquals(listOf("x-1"), repo.state.value.previousComparable(entry.exercise, "other")!!.sets.map { it.id })
    }

    @Test fun emptyExtraWorkoutNeedsALoggedSetToFinishAndCanBeDiscarded() = runTest {
        val repo = repository()
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        repo.execute(Action.StartExtraWorkout(tue, tueDate))
        failure("already active") { repo.execute(Action.StartExtraWorkout(tue + 1, tueDate)) }
        failure("already active") { repo.execute(Action.StartWorkout("push", tue + 1, tueDate)) }
        failure("at least one set") { repo.execute(Action.FinishWorkout(tue + 1000)) }
        // An exercise with nothing logged is still an empty workout.
        repo.execute(Action.AddWorkoutExercise(repo.active.id, "x-row", "row-db"))
        val empty = repo.state.value
        failure("at least one set") { repo.execute(Action.FinishWorkout(tue + 3000)) }
        assertSame("A refused finish changes nothing", empty, repo.state.value)
        repo.execute(Action.DiscardWorkout)
        assertTrue(repo.state.value.workouts.isEmpty())
        assertNull(repo.state.value.activeWorkoutId)
        failure("phase") { repository().execute(Action.StartExtraWorkout(tue, tueDate)) }
    }

    @Test fun warmUpOnlyExtraWorkoutFinishesAndKeepsItsWarmUpsWithoutWorkingSetsRestOrCredit() = runTest {
        val store = MemoryPersistence()
        val repo = repository(store, clock = { tue + 60_000 })
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        repo.execute(Action.StartExtraWorkout(tue, tueDate))
        val id = repo.active.id
        repo.execute(Action.AddWorkoutExercise(id, "x-row", "row-db"))
        repo.execute(Action.AddWorkoutExercise(id, "x-face", "face-pull"))
        repo.set("x-row", "x-warm-1", tue + 2000, kg = 10.0, reps = 12, warmup = true)
        repo.set("x-row", "x-warm-2", tue + 3000, kg = 14.0, reps = 8, warmup = true)
        assertNull("Warm-ups never start the rest", repo.state.value.restTimer)
        val logged = repo.active.entry("x-row").sets
        failure("never count") { repo.execute(Action.FinishWorkout(tue + 4000, countForWeek = true)) }
        assertEquals(id, repo.state.value.activeWorkoutId)

        repo.execute(Action.FinishWorkout(tue + 4000))
        val finished = repo.state.value
        val done = finished.workouts.single()
        assertNull(finished.activeWorkoutId)
        assertNull(finished.restTimer)
        assertEquals(tue + 4000, done.finishedAt)
        assertEquals("The warm-ups are kept exactly as logged", logged, done.entry("x-row").sets)
        assertTrue(done.entry("x-face").sets.isEmpty())
        assertEquals(0, done.workingSetCount())
        assertEquals(2, done.warmupSetCount())
        assertFalse(done.hasWorkingEffort())
        assertTrue("No target is invented", done.exercises.all { it.targetSets == null && it.minReps == null })
        assertFalse(done.targetsMet())
        assertFalse(done.completesRoutine())
        assertFalse(done.countedForWeek)
        assertFalse(done.countsForWeek())
        failure("never count") { repo.execute(Action.SetWeekCredit(done.id, true)) }
        val week = finished.weekChecklist(thursday, addis)!!
        assertEquals(0, week.done)
        assertTrue(week.routines.all { it.status == RoutineStatus.NOT_DONE })
        assertTrue("The week line lists extra workouts with working sets", week.extras.isEmpty())

        // A restart reads the same workout back, and a backup restores it unchanged.
        assertEquals(finished, repository(store).state.value)
        val document = repo.exportBackup()
        val destination = repository(clock = { tue + 120_000 })
        val safety = destination.exportBackup()
        destination.execute(Action.MarkBackup(safety.revision, tue + 100_000))
        destination.restoreBackup(destination.previewBackup(document.json), safety.revision)
        assertEquals(finished.workouts, destination.state.value.workouts)
        assertNull(destination.state.value.restTimer)
    }

    @Test fun addedExercisesAreOptionalAndNeverBlockTheRoutine() = runTest {
        val repo = planned()
        val workout = repo.active
        repo.execute(Action.AddWorkoutExercise(workout.id, "add-face", "face-pull"))
        repo.execute(Action.SetEntryGoal(workout.id, "add-face", 3, 12, 20, 75, false))
        val added = repo.active.entry("add-face")
        assertTrue(added.optional)
        assertEquals(3, added.targetSets)
        assertEquals(75, added.restSeconds)
        assertEquals(workout.exercises.map { it.id } + "add-face", repo.active.exercises.map { it.id })
        repo.meetTargets("req", tue + 1000)
        assertTrue("An unmet optional goal never blocks the routine", repo.active.targetsMet())
        assertTrue(repo.active.entry("add-face").belowTarget())
        repo.execute(Action.FinishWorkout(tue + 3_600_000))
        val done = repo.state.value.workouts.single()
        assertTrue(done.completesRoutine())
        assertTrue(done.countsForWeek())
        assertFalse("Completion is automatic, never an explicit credit", done.countedForWeek)
        val week = repo.state.value.weekChecklist(thursday, addis)!!
        assertEquals(RoutineStatus.DONE, week.statusOf("push"))
        assertEquals(done.id, week.routines.first { it.template.id == "push" }.doneBy!!.id)
        assertTrue(week.extras.isEmpty())
    }

    @Test fun optionalOrSurplusSetsNeverStandInForAMissingPlannedSet() = runTest {
        // Automatic completion is per planned exercise, never a total: a set on an added exercise, or an extra set on
        // another planned exercise, can't replace a planned set that wasn't done.
        val repo = planned()
        val workout = repo.active
        fun status() = repo.state.value.weekChecklist(thursday, addis)!!.statusOf("push")
        repo.execute(Action.AddWorkoutExercise(workout.id, "add-face", "face-pull"))
        val required = repo.active.exercises.filterNot { it.optional }
        val short = required.last()
        var n = 0
        required.forEach { e ->
            val sets = if (e.id == short.id) e.targetSets!! - 1 else e.targetSets!!
            repeat(sets) { repo.set(e.id, "req-${n++}", tue + 1000L + n) }
        }
        repo.set(required.first().id, "surplus", tue + 50_000)
        repeat(2) { repo.set("add-face", "face-$it", tue + 60_000L + it) }
        assertTrue(repo.active.workingSetCount() > required.sumOf { it.targetSets!! })
        assertTrue(repo.active.entry(short.id).belowTarget())
        assertFalse("One planned set is still missing", repo.active.targetsMet())
        repo.execute(Action.FinishWorkout(tue + 3_600_000))
        val done = repo.state.value.workouts.single()
        assertFalse(done.targetsMet())
        assertFalse(done.completesRoutine())
        assertFalse(done.countsForWeek())
        assertEquals(RoutineStatus.PARTIAL, status())
        // The missing planned set itself, added later in History, completes the routine without any choice.
        repo.set(short.id, "missed", tue + 3_700_000, workoutId = done.id)
        val corrected = repo.state.value.workouts.single()
        assertTrue(corrected.targetsMet())
        assertTrue(corrected.countsForWeek())
        assertFalse(corrected.countedForWeek)
        assertEquals(RoutineStatus.DONE, status())
        assertNull("History corrections never start a timer", repo.state.value.restTimer)
    }

    @Test fun optionalOnlyWorkKeepsAPlannedWorkoutPartialUntilExplicitlyCredited() = runTest {
        val repo = planned()
        val workout = repo.active
        fun status() = repo.state.value.weekChecklist(thursday, addis)!!.statusOf("push")
        repo.execute(Action.AddWorkoutExercise(workout.id, "add-face", "face-pull"))
        repeat(3) { repo.set("add-face", "f-$it", tue + 1000L * (it + 1)) }
        assertEquals(0, repo.active.requiredWorkingSetCount())
        repo.execute(Action.FinishWorkout(tue + 10_000))
        var done = repo.state.value.workouts.single()
        assertTrue(done.hasWorkingEffort())
        assertFalse("Added work never meets the routine's own targets", done.targetsMet())
        assertFalse(done.completesRoutine())
        assertFalse(done.countedForWeek)
        assertFalse(done.countsForWeek())
        assertEquals(RoutineStatus.PARTIAL, status())
        // Counting a partial workout stays the user's explicit choice, whichever exercises its real work was on.
        repo.execute(Action.SetWeekCredit(done.id, true))
        assertTrue(repo.state.value.workouts.single().countsForWeek())
        assertEquals(RoutineStatus.DONE, status())
        assertEquals(done.id, repo.state.value.weekChecklist(thursday, addis)!!.routines
            .single { it.template.id == "push" }.doneBy!!.id)
        assertEquals("Credit never adds or changes sets", done.exercises, repo.state.value.workouts.single().exercises)
        repo.execute(Action.SetWeekCredit(done.id, false))
        assertEquals(RoutineStatus.PARTIAL, status())
        // A forgotten exercise added from History is optional too, never starts a timer and never changes the plan.
        val plan = repo.state.value.let { Triple(it.overrides, it.planAdditions, it.planRemovals) }
        repo.execute(Action.AddWorkoutExercise(done.id, "late-row", "row-db"))
        repo.set("late-row", "late-1", tue + 20_000, workoutId = done.id)
        done = repo.state.value.workouts.single()
        assertTrue(done.entry("late-row").optional)
        assertNull(repo.state.value.restTimer)
        assertEquals(plan, repo.state.value.let { Triple(it.overrides, it.planAdditions, it.planRemovals) })
        assertEquals("Added work never completes the routine by itself", RoutineStatus.PARTIAL, status())
        repo.execute(Action.SetWeekCredit(done.id, true))
        assertEquals(RoutineStatus.DONE, status())
        assertNull(repo.state.value.restTimer)
        // Deleting every working set takes the credit's effect away, and nothing left can be credited again.
        done.exercises.forEach { e -> e.sets.forEach { repo.execute(Action.DeleteSet(done.id, e.id, it.id)) } }
        done = repo.state.value.workouts.single()
        assertFalse(done.hasWorkingEffort())
        assertFalse(done.countsForWeek())
        assertEquals(RoutineStatus.NOT_DONE, status())
        failure("at least one working set") { repo.execute(Action.SetWeekCredit(done.id, true)) }
    }

    @Test fun optionalOnlyWorkCanBeCountedWhenFinishingButWarmUpsAndOpenSetsCannot() = runTest {
        val repo = planned()
        val id = repo.active.id
        repo.execute(Action.AddWorkoutExercise(id, "add-face", "face-pull"))
        repo.set("add-face", "warm-1", tue + 1000, kg = 10.0, warmup = true)
        val warmedUp = repo.state.value
        failure("at least one working set") { repo.execute(Action.FinishWorkout(tue + 2000, countForWeek = true)) }
        assertSame("A refused finish changes nothing", warmedUp, repo.state.value)
        // A rest-pause still open is not finished work, and a workout in progress can't be credited at all.
        repo.execute(Action.LogStage(id, "add-face", "rp", SetMethod.REST_PAUSE, "rp-1", 15.0, 12, null, tue + 3000))
        assertEquals(0, repo.active.workingSetCount())
        assertFalse(repo.active.hasWorkingEffort())
        failure("Only a finished workout") { repo.execute(Action.SetWeekCredit(id, true)) }
        repo.execute(Action.FinishGroup(id, "add-face", "rp", tue + 4000))
        repo.execute(Action.FinishWorkout(tue + 5000, countForWeek = true))
        val done = repo.state.value.workouts.single()
        assertTrue(done.countedForWeek)
        assertFalse("Counted by choice, not by its targets", done.targetsMet())
        assertEquals(1, done.workingSetCount())
        assertEquals(0, done.requiredWorkingSetCount())
        assertTrue(done.countsForWeek())
        assertEquals(RoutineStatus.DONE, repo.state.value.weekChecklist(thursday, addis)!!.statusOf("push"))
        // Stored data whose only work is a set still open never counts, whatever its credit flag says.
        val forged = repo.state.value.copy(workouts = listOf(done.copy(exercises = done.exercises.map { e ->
            e.copy(setGroups = e.setGroups.map { it.copy(finishedAt = null) })
        })))
        assertFalse(forged.workouts.single().hasWorkingEffort())
        assertFalse(forged.workouts.single().countsForWeek())
        assertEquals(RoutineStatus.NOT_DONE, forged.weekChecklist(thursday, addis)!!.statusOf("push"))
    }

    @Test fun duplicateSelectionIsRejectedReplayChangesNothingAndConflictingReuseFails() = runTest {
        val repo = planned()
        val workout = repo.active
        repo.execute(Action.AddWorkoutExercise(workout.id, "a1", "face-pull"))
        val saved = repo.state.value
        repo.execute(Action.AddWorkoutExercise(workout.id, "a1", "face-pull"))
        assertSame("A re-delivered add is ignored, without a new revision", saved, repo.state.value)
        failure("already in this workout") { repo.execute(Action.AddWorkoutExercise(workout.id, "a2", "face-pull")) }
        failure("already in this workout") { repo.execute(Action.AddWorkoutExercise(workout.id, "a3", "lateral-cable")) }
        failure("already used") { repo.execute(Action.AddWorkoutExercise(workout.id, "a1", "row-db")) }
        failure("already used") { repo.execute(Action.AddWorkoutExercise(workout.id, "a1", "face-pull", perLeg = true)) }
        failure("already used") {
            repo.execute(Action.AddWorkoutExercise(workout.id, workout.exercises.first().id, "row-db"))
        }
        failure("no longer exists") { repo.execute(Action.AddWorkoutExercise("missing", "a4", "row-db")) }
        failure("no longer exists") { repo.execute(Action.AddWorkoutExercise(workout.id, "a5", "missing")) }
        assertSame(saved, repo.state.value)
        repo.execute(Action.FinishWorkout(tue + 1000))
        repo.execute(Action.StartWorkout("pull", tue + 2000, tueDate))
        failure("already used") { repo.execute(Action.AddWorkoutExercise(repo.active.id, "a1", "row-db")) }
    }

    @Test fun adjustTodayStillSubstitutesAnExerciseAlreadyInTheWorkout() = runTest {
        // As in 0.1.7: only adding the same exercise a second time is refused, never a substitution for today.
        val repo = planned()
        val workout = repo.active
        val first = workout.exercises.first()
        val lateral = workout.exercises.single { it.exercise.id == "lateral-cable" }
        assertNotEquals(lateral.id, first.id)
        repo.execute(Action.EditToday(first.id, "lateral-cable", requireNotNull(first.targetSets), first.restSeconds))
        repo.active.let { swapped ->
            assertEquals(listOf(first.id, lateral.id),
                swapped.exercises.filter { it.exercise.id == "lateral-cable" }.map { it.id })
            assertFalse(swapped.entry(first.id).optional)
            assertEquals(first.slotId, swapped.entry(first.id).slotId)
        }
        failure("already in this workout") { repo.execute(Action.AddWorkoutExercise(workout.id, "a1", "lateral-cable")) }
    }

    @Test fun moveChangesOnlyThisSessionsOrder() = runTest {
        val repo = planned()
        val workout = repo.active
        val push = repo.state.value.templates.first { it.id == "push" }
        val planOrder = repo.state.value.plannedExercises(push, Phase.BASELINE).map { it.slotId }
        val plan = repo.state.value.let { Triple(it.overrides, it.planAdditions, it.planRemovals) }
        repo.execute(Action.AddWorkoutExercise(workout.id, "m1", "face-pull"))
        repo.execute(Action.MoveWorkoutExercise(workout.id, "m1", 0))
        assertEquals("m1", repo.active.exercises.first().id)
        val last = workout.exercises.last().id
        repo.execute(Action.MoveWorkoutExercise(workout.id, workout.exercises.first().id, 5))
        assertEquals(listOf("m1") + workout.exercises.drop(1).map { it.id } + workout.exercises.first().id,
            repo.active.exercises.map { it.id })
        val before = repo.state.value
        repo.execute(Action.MoveWorkoutExercise(workout.id, last, repo.active.exercises.indexOfFirst { it.id == last }))
        assertSame("Moving to the same place changes nothing", before, repo.state.value)
        failure("outside") { repo.execute(Action.MoveWorkoutExercise(workout.id, "m1", 6)) }
        failure("outside") { repo.execute(Action.MoveWorkoutExercise(workout.id, "m1", -1)) }
        failure("not part") { repo.execute(Action.MoveWorkoutExercise(workout.id, "missing", 0)) }
        assertEquals(plan, repo.state.value.let { Triple(it.overrides, it.planAdditions, it.planRemovals) })
        assertEquals(planOrder, repo.state.value.plannedExercises(push, Phase.BASELINE).map { it.slotId })
        repo.execute(Action.FinishWorkout(tue + 1000))
        failure("active workout") { repo.execute(Action.MoveWorkoutExercise(workout.id, "m1", 1)) }
        repo.execute(Action.StartWorkout("push", tue + 2000, tueDate))
        assertEquals("The next workout follows the plan's order", planOrder, repo.active.exercises.map { it.slotId })
    }

    @Test fun removingAnAddedEntryNeedsItsExactRecordCount() = runTest {
        val repo = planned(template = "pull")
        val workout = repo.active
        repo.execute(Action.AddWorkoutExercise(workout.id, "r1", "face-pull"))
        repo.set("r1", "r1-s1", tue + 1000)
        repo.execute(Action.LogStage(workout.id, "r1", "r1-g", SetMethod.DROP, "r1-d1", 30.0, 10, null, tue + 2000))
        repo.execute(Action.LogStage(workout.id, "r1", "r1-g", SetMethod.DROP, "r1-d2", 20.0, 8, null, tue + 2100))
        repo.execute(Action.FinishGroup(workout.id, "r1", "r1-g", tue + 2200))
        assertEquals("r1", repo.state.value.restTimer!!.exerciseId)
        val confirmed = repo.state.value
        failure("changed since you confirmed") { repo.execute(Action.RemoveWorkoutExercise(workout.id, "r1", 2)) }
        failure("changed since you confirmed") { repo.execute(Action.RemoveWorkoutExercise(workout.id, "r1", 4)) }
        failure("part of this routine's plan") {
            repo.execute(Action.RemoveWorkoutExercise(workout.id, workout.exercises.first().id, 0))
        }
        assertSame(confirmed, repo.state.value)
        repo.execute(Action.RemoveWorkoutExercise(workout.id, "r1", 3))
        assertTrue(repo.active.exercises.none { it.id == "r1" })
        assertNull("The removed exercise's rest ends with it", repo.state.value.restTimer)
        val removed = repo.state.value
        repo.execute(Action.RemoveWorkoutExercise(workout.id, "r1", 3))
        assertSame("Repeating a removal changes nothing", removed, repo.state.value)
        // Another exercise's rest is untouched by removing an entry.
        repo.execute(Action.AddWorkoutExercise(workout.id, "r2", "row-db"))
        repo.set(workout.exercises.first().id, "req-1", tue + 3000)
        val rest = repo.state.value.restTimer
        repo.execute(Action.RemoveWorkoutExercise(workout.id, "r2", 0))
        assertEquals(rest, repo.state.value.restTimer)
        repo.execute(Action.FinishWorkout(tue + 4000))
        // A History correction can be removed again; a finished workout keeps at least one exercise.
        repo.execute(Action.AddWorkoutExercise(workout.id, "late", "row-db"))
        repo.set("late", "late-1", tue + 5000, workoutId = workout.id)
        repo.execute(Action.RemoveWorkoutExercise(workout.id, "late", 1))
        assertTrue(repo.state.value.workouts.single().exercises.none { it.id == "late" })
        repo.execute(Action.StartExtraWorkout(tue + 6000, tueDate))
        val extra = repo.active.id
        repo.execute(Action.AddWorkoutExercise(extra, "only", "row-db"))
        repo.set("only", "only-1", tue + 7000)
        repo.execute(Action.FinishWorkout(tue + 8000))
        failure("keeps at least one exercise") { repo.execute(Action.RemoveWorkoutExercise(extra, "only", 1)) }
    }

    @Test fun skippingTodayKeepsTheTargetAndWorkAndStaysPartialUnlessCredited() = runTest {
        val repo = planned()
        val workout = repo.active
        val incline = workout.exercises[0]
        assertEquals(2, incline.targetSets)
        repo.set(incline.id, "k1", tue + 1000)
        repo.execute(Action.SetSkipped(workout.id, incline.id, true))
        var skipped = repo.active.entry(incline.id)
        assertTrue(skipped.skipped)
        assertEquals(2, skipped.targetSets)
        assertEquals(listOf("k1"), skipped.sets.map { it.id })
        val same = repo.state.value
        repo.execute(Action.SetSkipped(workout.id, incline.id, true))
        assertSame(same, repo.state.value)
        failure("skipped today") { repo.set(incline.id, "k2", tue + 2000) }
        failure("skipped today") {
            repo.execute(Action.LogStage(workout.id, incline.id, "k-g", SetMethod.DROP, "k-d1", 20.0, 8, null, tue + 2000))
        }
        // What was already logged stays editable.
        repo.execute(Action.SaveSet(workout.id, incline.id, "k1", 22.5, 9, null, false, tue + 2500))
        assertEquals(22.5, repo.active.entry(incline.id).sets.single().kg)
        // Resume and Next pass over it; it stays reachable and Undo brings it back.
        assertEquals(workout.exercises[1].id, repo.active.resumeExercise()!!.id)
        assertEquals(workout.exercises[1].id, repo.active.nextAfter(-1)!!.id)
        repo.execute(Action.SetSkipped(workout.id, incline.id, false))
        assertEquals(incline.id, repo.active.resumeExercise()!!.id)
        repo.execute(Action.SetSkipped(workout.id, incline.id, true))
        // Guards: optional entries, met targets and sets in progress.
        repo.execute(Action.AddWorkoutExercise(workout.id, "opt", "face-pull"))
        failure("already optional") { repo.execute(Action.SetSkipped(workout.id, "opt", true)) }
        val shoulder = workout.exercises[1]
        repo.set(shoulder.id, "sh-1", tue + 3000)
        repo.set(shoulder.id, "sh-2", tue + 3100)
        failure("already reached its target") { repo.execute(Action.SetSkipped(workout.id, shoulder.id, true)) }
        val lateral = workout.exercises[2]
        repo.execute(Action.LogStage(workout.id, lateral.id, "l-g", SetMethod.REST_PAUSE, "l-r1", 10.0, 12, null, tue + 4000))
        failure("Finish the rest-pause") { repo.execute(Action.SetSkipped(workout.id, lateral.id, true)) }
        repo.execute(Action.FinishGroup(workout.id, lateral.id, "l-g", tue + 4100))
        repo.meetTargets("rest", tue + 5000)
        assertFalse(repo.active.targetsMet())
        repo.execute(Action.FinishWorkout(tue + 3_600_000))
        val done = repo.state.value.workouts.single()
        skipped = done.entry(incline.id)
        assertTrue("History keeps the skip with its target and work", skipped.skipped)
        assertEquals(2, skipped.targetSets)
        assertEquals(1, skipped.workingCount())
        assertFalse(done.countsForWeek())
        assertEquals(RoutineStatus.PARTIAL, repo.state.value.weekChecklist(thursday, addis)!!.statusOf("push"))
        failure("active workout") { repo.execute(Action.SetSkipped(done.id, incline.id, false)) }
        repo.execute(Action.SetWeekCredit(done.id, true))
        assertEquals(RoutineStatus.DONE, repo.state.value.weekChecklist(thursday, addis)!!.statusOf("push"))
        repo.execute(Action.SetWeekCredit(done.id, false))
        assertEquals(RoutineStatus.PARTIAL, repo.state.value.weekChecklist(thursday, addis)!!.statusOf("push"))
    }

    @Test fun optionalGoalsAreOptionalInEveryPartValidatedAndNeverRequired() = runTest {
        val repo = planned()
        val workout = repo.active
        repo.execute(Action.AddWorkoutExercise(workout.id, "g1", "face-pull"))
        repo.execute(Action.SetEntryGoal(workout.id, "g1", null, null, null, 60, false))
        repo.active.entry("g1").let {
            assertNull(it.targetSets)
            assertNull(it.minReps)
            assertNull(it.maxReps)
            assertEquals(60, it.restSeconds)
        }
        repo.execute(Action.SetEntryGoal(workout.id, "g1", 3, null, null, 60, false))
        assertEquals(3, repo.active.entry("g1").targetSets)
        repo.execute(Action.SetEntryGoal(workout.id, "g1", null, 8, 12, 60, false))
        repo.active.entry("g1").let {
            assertNull(it.targetSets)
            assertEquals(8, it.minReps)
            assertEquals(12, it.maxReps)
        }
        val valid = repo.state.value
        failure("both ends") { repo.execute(Action.SetEntryGoal(workout.id, "g1", 3, 8, null, 60, false)) }
        failure("rep range is invalid") { repo.execute(Action.SetEntryGoal(workout.id, "g1", 3, 12, 8, 60, false)) }
        failure("between 1 and") { repo.execute(Action.SetEntryGoal(workout.id, "g1", 0, null, null, 60, false)) }
        failure("Rest must be") { repo.execute(Action.SetEntryGoal(workout.id, "g1", 3, null, null, 0, false)) }
        failure("Adjust today") {
            repo.execute(Action.SetEntryGoal(workout.id, workout.exercises.first().id, 3, null, null, 60, false))
        }
        assertSame(valid, repo.state.value)
        // The plan's way of counting reps is kept; an unsettled one may change only before reps are logged.
        repo.execute(Action.AddWorkoutExercise(workout.id, "g2", "split-squat"))
        assertTrue(repo.active.entry("g2").perLeg)
        failure("already says") { repo.execute(Action.SetEntryGoal(workout.id, "g2", null, null, null, 90, false)) }
        repo.execute(Action.AddWorkoutExercise(workout.id, "g3", "hip-thrust", perLeg = true))
        assertTrue(repo.active.entry("g3").perLeg)
        repo.execute(Action.SetEntryGoal(workout.id, "g3", null, null, null, 120, false))
        assertFalse(repo.active.entry("g3").perLeg)
        repo.set("g3", "g3-1", tue + 1000, kg = 60.0)
        failure("can't change now") { repo.execute(Action.SetEntryGoal(workout.id, "g3", null, null, null, 120, true)) }
        repo.execute(Action.SetEntryGoal(workout.id, "g3", 4, 6, 10, 150, false))
        assertTrue("Goals on added exercises never become required", repo.active.exercises.filter { it.optional }.all {
            it.slotId == null && it.baselineSets == null && it.targetRir == null
        })
        repo.execute(Action.FinishWorkout(tue + 2000))
        failure("active workout") { repo.execute(Action.SetEntryGoal(workout.id, "g1", 3, null, null, 60, false)) }
    }

    @Test fun saveToPlanChangesOnlyFutureWorkoutsOfThatRoutineAndPhase() = runTest {
        val repo = planned()
        val first = repo.active
        repo.execute(Action.AddWorkoutExercise(first.id, "today-face", "face-pull"))
        repo.execute(Action.SetEntryGoal(first.id, "today-face", 3, 12, 20, 75, false))
        val today = repo.active.entry("today-face")
        repo.execute(Action.SavePlanAddition("add-face", Phase.BASELINE, "push", "face-pull", 3, 12, 20, 75, false))
        assertEquals("Today it stays optional, exactly as it was", today, repo.active.entry("today-face"))
        assertEquals(listOf(PlanAddition("add-face", Phase.BASELINE, "push", "face-pull", 3, 12, 20, 75, false)),
            repo.state.value.planAdditions)
        val saved = repo.state.value
        repo.execute(Action.SavePlanAddition("add-face", Phase.BASELINE, "push", "face-pull", 3, 12, 20, 75, false))
        assertSame("A repeated save changes nothing", saved, repo.state.value)
        failure("another routine") {
            repo.execute(Action.SavePlanAddition("add-face", Phase.BASELINE, "pull", "face-pull", 3, 12, 20, 75, false))
        }
        failure("another routine") {
            repo.execute(Action.SavePlanAddition("add-face", Phase.FIVE_TWO, "push", "face-pull", 3, 12, 20, 75, false))
        }
        failure("already in Push") {
            repo.execute(Action.SavePlanAddition("add-face-2", Phase.BASELINE, "push", "face-pull", 3, 12, 20, 75, false))
        }
        failure("already in Push") {
            repo.execute(Action.SavePlanAddition("add-lateral", Phase.BASELINE, "push", "lateral-cable", 3, 12, 20, 75, false))
        }
        failure("already used") {
            repo.execute(Action.SavePlanAddition("push-fly", Phase.BASELINE, "push", "row-db", 3, 12, 20, 75, false))
        }
        failure("not available") {
            repo.execute(Action.SavePlanAddition("add-intro", Phase.INTRO_ONE, "push", "row-db", 3, 12, 20, 75, false))
        }
        failure("rep range is invalid") {
            repo.execute(Action.SavePlanAddition("add-row", Phase.BASELINE, "push", "row-db", 3, 12, 8, 75, false))
        }
        failure("between 1 and") {
            repo.execute(Action.SavePlanAddition("add-row", Phase.BASELINE, "push", "row-db", 0, 8, 12, 75, false))
        }
        failure("per leg") {
            repo.execute(Action.SavePlanAddition("add-split", Phase.BASELINE, "push", "split-squat", 2, 8, 12, 90, false))
        }
        assertSame(saved, repo.state.value)
        repo.execute(Action.FinishWorkout(tue + 1000))
        repo.execute(Action.StartWorkout("push", tue + 2000, tueDate))
        val next = repo.active.of("face-pull")
        assertEquals("add-face", next.slotId)
        assertFalse(next.optional)
        assertEquals(3, next.targetSets)
        assertEquals(12, next.minReps)
        assertEquals(20, next.maxReps)
        assertEquals(75, next.restSeconds)
        assertNull(next.baselineSets)
        assertNull(next.upperSets)
        assertEquals(Phase.BASELINE.targetRir, next.targetRir)
        assertEquals(today, repo.state.value.workouts.first().entry("today-face"))
        // Editing the saved exercise later changes only workouts started after it.
        repo.execute(Action.SavePlanAddition("add-face", Phase.BASELINE, "push", "face-pull", 4, 10, 15, 60, false))
        assertEquals(3, repo.active.of("face-pull").targetSets)
        repo.execute(Action.DiscardWorkout)
        repo.execute(Action.SelectPhase(Phase.FIVE_TWO))
        repo.execute(Action.StartWorkout("push", tue + 3000, tueDate))
        assertTrue("Other phases never get it", repo.active.exercises.none { it.exercise.id == "face-pull" })
        repo.execute(Action.DiscardWorkout)
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        repo.execute(Action.StartWorkout("push", tue + 4000, tueDate))
        assertEquals(4, repo.active.of("face-pull").targetSets)
        assertEquals(SnapshotCodec.decode(SnapshotCodec.encode(repo.state.value)), repo.state.value)
    }

    @Test fun removedPlanExercisesLeaveStartedSnapshotsValidAndAreNeverBroughtBackFromToday() = runTest {
        val repo = repository()
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        repo.execute(Action.SavePlanAddition("add-face", Phase.BASELINE, "push", "face-pull", 3, 12, 20, 75, false))
        repo.execute(Action.StartWorkout("push", tue, tueDate))
        val workout = repo.active
        val face = workout.of("face-pull")
        repo.set(face.id, "face-1", tue + 1000)
        repo.execute(Action.RemovePlanAddition("add-face"))
        assertTrue(repo.state.value.planAdditions.isEmpty())
        assertEquals("A started workout keeps what it began with", face.copy(sets = repo.active.entry(face.id).sets),
            repo.active.entry(face.id))
        assertFalse(repo.active.entry(face.id).optional)
        assertEquals(SnapshotCodec.decode(SnapshotCodec.encode(repo.state.value)), repo.state.value)
        val removed = repo.state.value
        repo.execute(Action.RemovePlanAddition("add-face"))
        assertSame("Removing it again changes nothing", removed, repo.state.value)
        failure("no longer in your plan") { repo.execute(Action.EditToday(face.id, "face-pull", 4, 75, saveToPlan = true)) }
        failure("already used") {
            repo.execute(Action.SavePlanAddition("add-face", Phase.BASELINE, "push", "face-pull", 3, 12, 20, 75, false))
        }
        assertSame(removed, repo.state.value)
        repo.execute(Action.EditToday(face.id, "face-pull", 4, 75))
        assertEquals(4, repo.active.entry(face.id).targetSets)
        assertTrue(repo.state.value.planAdditions.isEmpty())
        // An original slot left out of the future plan stays in workouts already started.
        val fly = workout.of("chest-fly-cable")
        repo.execute(Action.SetPlanSlotRemoved(Phase.BASELINE, "push-fly", true))
        assertEquals(fly, repo.active.entry(fly.id))
        val overrides = repo.state.value.overrides
        failure("Restore it in Plan") {
            repo.execute(Action.EditToday(fly.id, "chest-fly-cable", 2, 60, saveToPlan = true))
        }
        failure("Restore it first") { repo.execute(Action.EditPlan(Phase.BASELINE, "push-fly", "chest-fly-cable", 2, 60)) }
        assertEquals(overrides, repo.state.value.overrides)
        repo.execute(Action.EditToday(fly.id, "chest-fly-cable", 1, 60))
        assertEquals(overrides, repo.state.value.overrides)
        repo.execute(Action.FinishWorkout(tue + 2000))
        repo.execute(Action.StartWorkout("push", tue + 3000, tueDate))
        assertTrue(repo.active.exercises.none { it.exercise.id == "chest-fly-cable" || it.exercise.id == "face-pull" })
        val history = repo.state.value.workouts.first()
        assertEquals(listOf("push-incline", "push-shoulder", "push-lateral", "push-fly", "push-triceps", "add-face"),
            history.exercises.map { it.slotId })
        assertEquals(SnapshotCodec.decode(SnapshotCodec.encode(repo.state.value)), repo.state.value)
        repo.execute(Action.DiscardWorkout)
        // Restoring brings the slot back for future workouts; it never takes the place of the user's own exercise.
        repo.execute(Action.SavePlanAddition("add-fly", Phase.BASELINE, "push", "chest-fly-cable", 2, 10, 15, 90, false))
        failure("Remove that one first") { repo.execute(Action.SetPlanSlotRemoved(Phase.BASELINE, "push-fly", false)) }
        repo.execute(Action.RemovePlanAddition("add-fly"))
        repo.execute(Action.SetPlanSlotRemoved(Phase.BASELINE, "push-fly", false))
        repo.execute(Action.StartWorkout("push", tue + 4000, tueDate))
        assertEquals("push-fly", repo.active.of("chest-fly-cable").slotId)
        repo.execute(Action.DiscardWorkout)
        // A routine always keeps at least one exercise.
        listOf("pull-vertical", "pull-row", "pull-rear").forEach {
            repo.execute(Action.SetPlanSlotRemoved(Phase.BASELINE, it, true))
        }
        failure("needs at least one exercise") { repo.execute(Action.SetPlanSlotRemoved(Phase.BASELINE, "pull-curl", true)) }
        repo.execute(Action.SavePlanAddition("add-row", Phase.BASELINE, "pull", "row-db", 3, 8, 12, 120, false))
        repo.execute(Action.SetPlanSlotRemoved(Phase.BASELINE, "pull-curl", true))
        failure("needs at least one exercise") { repo.execute(Action.RemovePlanAddition("add-row")) }
        failure("not available") { repo.execute(Action.SetPlanSlotRemoved(Phase.INTRO_ONE, "pull-row", true)) }
        repo.execute(Action.StartWorkout("pull", tue + 5000, tueDate))
        assertEquals(listOf("add-row"), repo.active.exercises.map { it.slotId })
    }

    @Test fun adjustTodayCanSaveToAnExistingAdditionButNeverRelabelsReps() = runTest {
        val repo = repository()
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        repo.execute(Action.SavePlanAddition("add-face", Phase.BASELINE, "push", "face-pull", 3, 12, 20, 75, false))
        repo.execute(Action.SavePlanAddition("add-hip", Phase.BASELINE, "push", "hip-thrust", 2, 8, 12, 120, false))
        repo.execute(Action.StartWorkout("push", tue, tueDate))
        val face = repo.active.of("face-pull")
        repo.execute(Action.EditToday(face.id, "face-pull", 4, 60, saveToPlan = true))
        assertEquals(PlanAddition("add-face", Phase.BASELINE, "push", "face-pull", 4, 12, 20, 60, false),
            repo.state.value.planAdditions.first { it.id == "add-face" })
        repo.execute(Action.EditToday(face.id, "reverse-pec-deck", 4, 60, saveToPlan = true))
        assertEquals("reverse-pec-deck", repo.state.value.planAdditions.first { it.id == "add-face" }.exerciseId)
        val hip = repo.active.of("hip-thrust")
        val before = repo.state.value
        failure("per leg") { repo.execute(Action.EditToday(hip.id, "split-squat", 2, 120, saveToPlan = true)) }
        assertSame(before, repo.state.value)
        repo.execute(Action.EditToday(hip.id, "split-squat", 2, 120))
        assertEquals("split-squat", repo.active.entry(hip.id).exercise.id)
        assertEquals("hip-thrust", repo.state.value.planAdditions.first { it.id == "add-hip" }.exerciseId)
        repo.execute(Action.AddWorkoutExercise(repo.active.id, "opt", "row-db"))
        failure("optional goal") { repo.execute(Action.EditToday("opt", "row-db", 3, 90, saveToPlan = true)) }
        repo.execute(Action.DiscardWorkout)
        repo.execute(Action.StartExtraWorkout(tue + 1000, tueDate))
        repo.execute(Action.AddWorkoutExercise(repo.active.id, "x", "row-db"))
        failure("optional goal") { repo.execute(Action.EditToday("x", "row-db", 3, 90, saveToPlan = true)) }
    }

    @Test fun planSwapsNeverListOneExerciseTwiceInARoutine() = runTest {
        val repo = repository()
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        failure("already in Push") { repo.execute(Action.EditPlan(Phase.BASELINE, "push-fly", "incline-db", 2, 90)) }
        repo.execute(Action.EditPlan(Phase.BASELINE, "push-incline", "incline-db", 3, 150))
        repo.execute(Action.EditPlan(Phase.BASELINE, "push-incline", "incline-machine", 2, 180))
        repo.execute(Action.SavePlanAddition("add-face", Phase.BASELINE, "push", "face-pull", 3, 12, 20, 75, false))
        failure("as your own exercise") { repo.execute(Action.EditPlan(Phase.BASELINE, "push-fly", "face-pull", 2, 90)) }
        repo.execute(Action.EditPlan(Phase.FIVE_TWO, "push-fly", "face-pull", 2, 90))
        assertEquals("face-pull", repo.state.value.overrides.first { it.phase == Phase.FIVE_TWO }.exerciseId)
        // The same exercise may still appear in different routines.
        repo.execute(Action.EditPlan(Phase.BASELINE, "pull-rear", "face-pull", 2, 90))
    }

    @Test fun restSuggestionPrefersLastUsedThenPlanThenAVisibleDefault() = runTest {
        val repo = repository()
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        val fresh = repo.state.value
        assertEquals(RestSuggestion(DEFAULT_REST_SECONDS, RestSource.DEFAULT), fresh.restSuggestion("face-pull", Phase.BASELINE))
        assertEquals(RestSuggestion(180, RestSource.PLAN), fresh.restSuggestion("incline-db", Phase.BASELINE))
        assertEquals("An alternative uses its slot's prescription", RestSuggestion(90, RestSource.PLAN),
            fresh.restSuggestion("reverse-fly-db", Phase.BASELINE))
        repo.execute(Action.EditPlan(Phase.BASELINE, "push-incline", "incline-db", 2, 150))
        assertEquals(RestSuggestion(150, RestSource.PLAN), repo.state.value.restSuggestion("incline-db", Phase.BASELINE))
        assertEquals("Another phase keeps its own plan", RestSuggestion(180, RestSource.PLAN),
            repo.state.value.restSuggestion("incline-db", Phase.FIVE_TWO))
        repo.execute(Action.StartExtraWorkout(tue, tueDate))
        val extra = repo.active.id
        repo.execute(Action.AddWorkoutExercise(extra, "x-face", "face-pull"))
        assertEquals(90, repo.active.entry("x-face").restSeconds)
        repo.execute(Action.SetEntryGoal(extra, "x-face", null, null, null, 75, false))
        repo.set("x-face", "warm", tue + 1000, warmup = true)
        assertEquals("Warm-ups don't make a rest last used", RestSource.DEFAULT,
            repo.state.value.restSuggestion("face-pull", Phase.BASELINE).source)
        repo.set("x-face", "work", tue + 2000)
        assertEquals(RestSuggestion(75, RestSource.LAST_USED), repo.state.value.restSuggestion("face-pull", Phase.BASELINE))
        assertEquals(RestSource.DEFAULT,
            repo.state.value.restSuggestion("face-pull", Phase.BASELINE, excludingEntryId = "x-face").source)
        repo.execute(Action.FinishWorkout(tue + 3000))
        repo.execute(Action.StartWorkout("push", tue + 4000, tueDate))
        repo.execute(Action.AddWorkoutExercise(repo.active.id, "p-face", "face-pull"))
        assertEquals(75, repo.active.entry("p-face").restSeconds)
        repo.execute(Action.AddWorkoutExercise(repo.active.id, "p-split", "split-squat"))
        assertEquals("Its plan rest in another routine", 180, repo.active.entry("p-split").restSeconds)
    }

    @Test fun repsPerLegFollowThePlanThenHistoryOtherwiseTheUserIsAskedAndRepsAreNeverConverted() = runTest {
        val repo = repository()
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        val state = repo.state.value
        fun ex(id: String) = state.exercises.first { it.id == id }
        assertEquals(true, state.perLegConvention("split-squat"))
        assertFalse(state.asksPerLeg(ex("split-squat")))
        assertEquals(false, state.perLegConvention("leg-press"))
        assertFalse(state.asksPerLeg(ex("leg-press")))
        assertNull(state.perLegConvention("hip-thrust"))
        assertTrue("A leg exercise nothing settles is asked", state.asksPerLeg(ex("hip-thrust")))
        assertTrue(state.asksPerLeg(ex("lunge-db")))
        assertFalse("Other exercises use a plain count", state.asksPerLeg(ex("face-pull")))
        repo.execute(Action.StartWorkout("push", tue, tueDate))
        val workout = repo.active.id
        failure("per leg") { repo.execute(Action.AddWorkoutExercise(workout, "p1", "split-squat", perLeg = false)) }
        repo.execute(Action.AddWorkoutExercise(workout, "p1", "split-squat"))
        assertTrue(repo.active.entry("p1").perLeg)
        failure("without a per-leg label") { repo.execute(Action.AddWorkoutExercise(workout, "p2", "leg-press", perLeg = true)) }
        repo.execute(Action.AddWorkoutExercise(workout, "p3", "hip-thrust", perLeg = true))
        assertTrue(repo.active.entry("p3").perLeg)
        repo.set("p3", "hip-1", tue + 1000, kg = 60.0)
        repo.execute(Action.FinishWorkout(tue + 2000))
        assertEquals(true, repo.state.value.lastLoggedPerLeg("hip-thrust"))
        assertFalse("History settles it now", repo.state.value.asksPerLeg(ex("hip-thrust")))
        repo.execute(Action.StartWorkout("push", tue + 3000, tueDate))
        repo.execute(Action.AddWorkoutExercise(repo.active.id, "p4", "hip-thrust"))
        assertTrue(repo.active.entry("p4").perLeg)
        // Comparisons never pair reps counted per leg with a plain count.
        assertNotNull(repo.state.value.previousComparable(ex("hip-thrust"), repo.active.id, LoadUnit.KG, perLeg = true))
        assertNull(repo.state.value.previousComparable(ex("hip-thrust"), repo.active.id, LoadUnit.KG, perLeg = false))
        repo.execute(Action.DiscardWorkout)
        // When plan positions disagree, nothing settles it and the user is asked.
        repo.execute(Action.EditPlan(Phase.BASELINE, "lower-b-extension", "split-squat", 2, 90))
        assertNull(repo.state.value.perLegConvention("split-squat"))
        assertTrue(repo.state.value.asksPerLeg(ex("split-squat")))
        repo.execute(Action.StartWorkout("push", tue + 4000, tueDate))
        repo.execute(Action.AddWorkoutExercise(repo.active.id, "p5", "split-squat", perLeg = false))
        assertFalse(repo.active.entry("p5").perLeg)
    }

    @Test fun weekChecklistListsExtraWorkoutsApartFromRoutinesAndOtherWorkouts() = runTest {
        val repo = repository()
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        repo.execute(Action.StartExtraWorkout(tue, tueDate))
        repo.execute(Action.AddWorkoutExercise(repo.active.id, "x1", "face-pull"))
        assertNull("An unfinished extra workout isn't listed yet", repo.state.value.weekChecklist(thursday, addis)!!
            .extras.firstOrNull())
        repo.set("x1", "x1-1", tue + 1000)
        repo.execute(Action.FinishWorkout(tue + 2000))
        repo.execute(Action.StartWorkout("push", tue + 3000, tueDate))
        repo.meetTargets("p", tue + 4000)
        repo.execute(Action.FinishWorkout(tue + 5000))
        repo.execute(Action.StartExtraWorkout(tue + 6000, tueDate))
        repo.execute(Action.AddWorkoutExercise(repo.active.id, "x2", "row-db"))
        repo.set("x2", "x2-1", tue + 7000)
        repo.execute(Action.FinishWorkout(tue + 8000))
        val week = repo.state.value.weekChecklist(thursday, addis)!!
        val workouts = repo.state.value.workouts
        assertEquals(listOf(workouts[0].id, workouts[2].id), week.extras.map { it.id })
        assertTrue(week.other.isEmpty())
        assertEquals(1, week.done)
        assertEquals(RoutineStatus.DONE, week.statusOf("push"))
        assertTrue(repo.state.value.weekChecklist(LocalDate.of(2026, 10, 5), addis)!!.extras.isEmpty())
    }
}
