package com.rebuild.data

import com.rebuild.domain.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RepositoryTest {
    @Test fun requiresPhaseAndBlocksSecondActiveOrPhaseChange() = runTest {
        val repo = repository()
        failure("phase") { repo.execute(Action.StartWorkout("push", 1000)) }
        repo.start()
        val original = repo.state.value
        failure("already active") { repo.execute(Action.StartWorkout("pull", 2000)) }
        failure("changing phase") { repo.execute(Action.SelectPhase(Phase.INTRO_ONE)) }
        assertEquals(original, repo.state.value)
    }

    @Test fun introCannotStartEstablishedTemplate() = runTest {
        val repo = repository()
        repo.execute(Action.SelectPhase(Phase.INTRO_ONE))
        failure("not available") { repo.execute(Action.StartWorkout("push", 1000)) }
        repo.execute(Action.StartWorkout("intro-upper", 1000))
        assertTrue(repo.state.value.activeWorkout!!.exercises.all { it.targetSets == 1 && it.targetRir == 4 })
    }

    @Test fun seedRunsOnlyOnFirstOpenAndAllEditsResumeExactly() = runTest {
        val store = MemoryPersistence()
        val repo = repository(store)
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        val custom = Exercise("custom-press", "Custom press", custom = true)
        repo.execute(Action.AddExercise(custom))
        repo.execute(Action.EditPlan(Phase.BASELINE, "push-incline", custom.id, 3, 120))
        val workout = repo.start()
        repo.log(workout = workout)
        repo.execute(Action.WorkoutNote(workout.id, "Continue tomorrow"))
        repo.execute(Action.ExerciseNote(workout.id, workout.exercises.first().id, "Adjusted seat"))
        val captured = repo.state.value
        val writes = store.writes
        val reopened = repository(store)
        assertEquals(captured, reopened.state.value)
        assertEquals(writes, store.writes)
        assertEquals(custom, reopened.state.value.activeWorkout!!.exercises.first().exercise)
    }

    @Test fun definitionsAndTargetsAreSnapshotsNotLiveReferences() = runTest {
        val repo = repository()
        val workout = repo.start()
        val captured = workout.exercises.first()
        repo.execute(Action.EditPlan(Phase.BASELINE, captured.slotId!!, "incline-machine", 3, 240))
        repo.execute(Action.AddExercise(captured.exercise.copy(name = "Renamed for future workouts", perDumbbell = false)))
        assertEquals(captured, repo.state.value.activeWorkout!!.exercises.first())
        repo.execute(Action.FinishWorkout(3000))
        repo.execute(Action.StartWorkout("push", 4000))
        assertEquals("incline-machine", repo.state.value.activeWorkout!!.exercises.first().exercise.id)
        assertEquals(3, repo.state.value.activeWorkout!!.exercises.first().targetSets)
        assertEquals(captured, repo.state.value.workouts.first().exercises.first())
    }

    @Test fun changingPhaseAfterFinishDoesNotRewriteRecordedTargets() = runTest {
        val repo = repository()
        val intro = repo.start(Phase.INTRO_ONE, "intro-upper")
        repo.execute(Action.FinishWorkout(2000))
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        assertEquals(intro.copy(finishedAt = 2000), repo.state.value.workouts.single())
        assertTrue(repo.state.value.workouts.single().exercises.all { it.targetSets == 1 && it.targetRir == 4 })
    }

    @Test fun editingLibraryPersistsButNeverRelabelsActiveOrFinishedSets() = runTest {
        val store = MemoryPersistence()
        val repo = repository(store)
        val workout = repo.start()
        repo.log()
        val captured = repo.state.value.activeWorkout!!.exercises.first()
        val definition = captured.exercise.copy(name = "Customized press", perDumbbell = false)
        repo.execute(Action.AddExercise(definition))
        repo.execute(Action.EditToday(captured.id, definition.id, 2, 180))
        assertEquals(captured, repo.state.value.activeWorkout!!.exercises.first())
        repo.execute(Action.FinishWorkout(3000))
        val reopened = repository(store)
        reopened.execute(Action.StartWorkout("push", 4000))
        assertEquals(definition, reopened.state.value.activeWorkout!!.exercises.first().exercise)
        assertEquals(captured, reopened.state.value.workouts.first { it.id == workout.id }.exercises.first())
    }

    @Test fun concurrentDoubleTapUpsertsAndPreservesCompletionTime() = runTest {
        val repo = repository()
        val workout = repo.start()
        List(10) { async { repo.log(workout = workout, id = "stable-id") } }.awaitAll()
        assertEquals(1, repo.state.value.activeWorkout!!.exercises.first().sets.size)
        val timer = repo.state.value.restTimer
        repo.log(workout = workout, id = "stable-id", kg = 25.0, reps = 12, now = 9000)
        val edited = repo.state.value.activeWorkout!!.exercises.first().sets.single()
        assertEquals(2000L, edited.completedAt)
        assertEquals(25.0, edited.kg)
        assertEquals(12, edited.reps)
        assertEquals(timer, repo.state.value.restTimer)
    }

    @Test fun logsMayExceedBothTargetAndAdvisoryCap() = runTest {
        val repo = repository()
        repo.start()
        repeat(8) { repo.log(id = "extra-$it") }
        assertEquals(8, repo.state.value.activeWorkout!!.exercises.first().sets.size)
    }

    @Test fun capsRequireAcknowledgementForPlanAndTodayOnly() = runTest {
        val repo = repository()
        val workout = repo.start()
        val exercise = workout.exercises.first()
        failure("upper limit") { repo.execute(Action.EditPlan(Phase.BASELINE, exercise.slotId!!, exercise.exercise.id, 5, 90)) }
        failure("upper limit") { repo.execute(Action.EditToday(exercise.id, exercise.exercise.id, 5, 90)) }
        repo.execute(Action.EditToday(exercise.id, exercise.exercise.id, 5, 90, saveToPlan = true, allowAboveCap = true))
        assertEquals(5, repo.state.value.activeWorkout!!.exercises.first().targetSets)
        assertEquals(5, repo.state.value.overrides.single().sets)
        repo.execute(Action.EditPlan(Phase.BASELINE, exercise.slotId!!, exercise.exercise.id, 6, 100, allowAboveCap = true))
        assertEquals(5, repo.state.value.activeWorkout!!.exercises.first().targetSets)
        assertEquals(6, repo.state.value.overrides.single().sets)
    }

    @Test fun todayOnlyChangeDoesNotChangeFuturePlan() = runTest {
        val repo = repository()
        val workout = repo.start()
        val first = workout.exercises.first()
        repo.execute(Action.EditToday(first.id, "incline-machine", 3, 100))
        assertEquals("incline-machine", repo.state.value.activeWorkout!!.exercises.first().exercise.id)
        assertEquals(100, repo.state.value.activeWorkout!!.exercises.first().restSeconds)
        assertTrue(repo.state.value.overrides.isEmpty())
        repo.execute(Action.FinishWorkout(3000))
        repo.execute(Action.StartWorkout("push", 4000))
        assertEquals("incline-db", repo.state.value.activeWorkout!!.exercises.first().exercise.id)
    }

    @Test fun substitutionNeverConvertsLoggedWeightsEvenWarmups() = runTest {
        val repo = repository()
        val workout = repo.start()
        val first = workout.exercises.first()
        repo.log(warmup = true)
        val previous = repo.state.value
        failure("Remove") { repo.execute(Action.EditToday(first.id, "incline-machine", 2, 90)) }
        assertEquals(previous, repo.state.value)
        repo.execute(Action.DeleteSet(workout.id, first.id, "set-1"))
        repo.execute(Action.EditToday(first.id, "incline-machine", 2, 90))
        assertEquals("incline-machine", repo.state.value.activeWorkout!!.exercises.first().exercise.id)
        assertTrue(repo.state.value.activeWorkout!!.exercises.first().sets.isEmpty())
    }

    @Test fun partialFinishCreatesNoSetsAndHistoryEditsAreReal() = runTest {
        val repo = repository()
        val workout = repo.start()
        repo.log()
        repo.execute(Action.FinishWorkout(4000))
        val finished = repo.state.value.workouts.single()
        assertNull(repo.state.value.activeWorkout)
        assertNull(repo.state.value.restTimer)
        assertEquals(1, finished.exercises.sumOf { it.sets.size })
        repo.log(workout, workout.exercises.first(), id = "history-new", kg = 27.5, now = 8000)
        repo.log(workout, workout.exercises.first(), id = "set-1", kg = 22.5, now = 9000)
        repo.execute(Action.WorkoutNote(workout.id, "Retrospective"))
        repo.execute(Action.ExerciseNote(workout.id, workout.exercises.first().id, "Corrected weights"))
        val history = repo.state.value.workouts.single()
        assertEquals(2, history.exercises.first().sets.size)
        assertEquals(2000L, history.exercises.first().sets.first().completedAt)
        assertEquals("Retrospective", history.notes)
        assertEquals("Corrected weights", history.exercises.first().notes)
        assertNull(repo.state.value.restTimer)
        assertEquals(history.exercises.first(), repo.state.value.previousComparable(workout.exercises.first().exercise, "other"))
        repo.execute(Action.DeleteSet(workout.id, workout.exercises.first().id, "set-1"))
        assertEquals("history-new", repo.state.value.workouts.single().exercises.first().sets.single().id)
    }

    @Test fun finishingWithoutLogsDoesNotFabricateCompletion() = runTest {
        val repo = repository()
        repo.start()
        repo.execute(Action.FinishWorkout(2000))
        assertTrue(repo.state.value.workouts.single().exercises.all { it.sets.isEmpty() })
        assertNotNull(repo.state.value.workouts.single().finishedAt)
        assertNull(repo.state.value.activeWorkout)
    }

    @Test fun editsToHistoryWhileAnotherWorkoutIsActiveDoNotRestartItsTimer() = runTest {
        val repo = repository()
        val history = repo.start()
        repo.log()
        repo.execute(Action.FinishWorkout(3000))
        repo.execute(Action.StartWorkout("pull", 4000))
        repo.log(id = "current", now = 5000)
        val timer = repo.state.value.restTimer
        repo.log(workout = history, id = "history-added", now = 6000)
        assertEquals(timer, repo.state.value.restTimer)
    }

    @Test fun discardRemovesOnlyUnfinishedWorkout() = runTest {
        val repo = repository()
        repo.start()
        repo.log()
        repo.execute(Action.FinishWorkout(4000))
        val history = repo.state.value.workouts.single()
        repo.execute(Action.StartWorkout("pull", 5000))
        repo.log(id = "active-set", now = 6000)
        repo.execute(Action.DiscardWorkout)
        assertEquals(listOf(history), repo.state.value.workouts)
        assertNull(repo.state.value.activeWorkoutId)
        assertNull(repo.state.value.restTimer)
        val revision = repo.state.value.revision
        repo.execute(Action.DiscardWorkout)
        assertEquals(revision, repo.state.value.revision)
    }

    @Test fun externalAndAssistanceRequireNonnegativeFiniteLoadBodyweightRequiresNull() = runTest {
        val repo = repository()
        val workout = repo.start(template = "pull")
        repo.execute(Action.EditToday(workout.exercises.first().id, "pullup", 3, 180))
        failure("Bodyweight") { repo.log(kg = 0.0) }
        repo.log(kg = null)
        repo.execute(Action.DeleteSet(workout.id, workout.exercises.first().id, "set-1"))
        repo.execute(Action.EditToday(workout.exercises.first().id, "pullup-assisted", 3, 180))
        listOf(null, -0.1, Double.NaN, Double.POSITIVE_INFINITY, 10001.0).forEach { kg ->
            failure("Weight") { repo.log(kg = kg) }
        }
        repo.log(kg = 0.0)
        assertEquals(0.0, repo.state.value.activeWorkout!!.exercises.first().sets.single().kg)
        repo.execute(Action.DeleteSet(workout.id, workout.exercises.first().id, "set-1"))
        repo.execute(Action.EditToday(workout.exercises.first().id, "pulldown", 3, 180))
        failure("Weight") { repo.log(kg = null) }
        repo.log(kg = 40.0)
    }

    @Test fun invalidRepsRirNotesAndUnknownReferencesLeaveStateAlone() = runTest {
        val repo = repository()
        val workout = repo.start()
        val before = repo.state.value
        listOf(0, -1, 1001).forEach { failure("Reps") { repo.log(reps = it) } }
        listOf(-0.1, 10.1, Double.NaN, Double.NEGATIVE_INFINITY).forEach { failure("RIR") { repo.log(rir = it) } }
        failure("Notes") { repo.execute(Action.WorkoutNote(workout.id, "n".repeat(4097))) }
        failure("Notes") { repo.execute(Action.ExerciseNote(workout.id, workout.exercises.first().id, "n".repeat(4097))) }
        failure("no longer exists") { repo.execute(Action.WorkoutNote("missing", "note")) }
        failure("not part") { repo.execute(Action.ExerciseNote(workout.id, "missing", "note")) }
        assertEquals(before, repo.state.value)
        repo.log(rir = null)
    }

    @Test fun sameSetIdCannotBeInsertedInAnotherExercise() = runTest {
        val repo = repository()
        val workout = repo.start()
        repo.log()
        failure("already used") { repo.log(workout, workout.exercises[1]) }
        assertEquals(1, repo.state.value.activeWorkout!!.exercises.sumOf { it.sets.size })
    }

    @Test fun bodyweightHistoryCorrectionsRemainWeightlessAndNeverCompareToAssistance() = runTest {
        val repo = repository()
        val started = repo.start(template = "pull")
        repo.execute(Action.EditToday(started.exercises.first().id, "pullup", 3, 180))
        val workout = requireNotNull(repo.state.value.activeWorkout)
        val pullup = workout.exercises.first()
        repo.log(workout, pullup, kg = null, reps = 8)
        repo.execute(Action.FinishWorkout(3000))
        repo.log(workout, pullup, kg = null, reps = 12, now = 9000)
        val corrected = repo.state.value.workouts.single().exercises.first()
        assertEquals(12, corrected.sets.single().reps)
        assertNull(corrected.sets.single().kg)
        assertNull(repo.state.value.restTimer)
        failure("Bodyweight") { repo.log(workout, pullup, kg = 20.0) }
        assertEquals(corrected, repo.state.value.previousComparable(pullup.exercise, "other"))
        val assisted = repo.state.value.exercises.single { it.id == "pullup-assisted" }
        val pulldown = repo.state.value.exercises.single { it.id == "pulldown" }
        assertNull(repo.state.value.previousComparable(assisted, "other"))
        assertNull(repo.state.value.previousComparable(pulldown, "other"))
        assertNull(repo.state.value.previousComparable(pullup.exercise.copy(loading = LoadingType.ASSISTANCE), "other"))
    }

    @Test fun comparisonExcludesActiveWarmupOnlyAndDifferentDumbbellConvention() = runTest {
        val repo = repository()
        val first = repo.start()
        val definition = first.exercises.first().exercise
        repo.log()
        assertNull(repo.state.value.previousComparable(definition, "other"))
        repo.execute(Action.FinishWorkout(3000))
        assertNull(repo.state.value.previousComparable(definition, first.id))
        assertNull(repo.state.value.previousComparable(definition.copy(perDumbbell = false), "other"))
        val workingHistory = repo.state.value.workouts.single().exercises.first()
        repo.execute(Action.StartWorkout("push", 4000))
        repo.log(id = "warmup-later", warmup = true, now = 5000)
        repo.execute(Action.FinishWorkout(6000))
        assertEquals(workingHistory, repo.state.value.previousComparable(definition, "other"))
        repo.execute(Action.DeleteSet(first.id, first.exercises.first().id, "set-1"))
        assertNull(repo.state.value.previousComparable(definition, "other"))
    }

    @Test fun failedPersistenceDoesNotPublishOrAdvanceRevision() = runTest {
        val store = MemoryPersistence()
        val repo = repository(store)
        repo.start()
        val state = repo.state.value
        val disk = store.snapshot
        store.beforeWrite = { assertEquals(state, repo.state.value) }
        store.failWrites = true
        failure("previous data is unchanged") { repo.log() }
        assertEquals(state, repo.state.value)
        assertEquals(disk, store.snapshot)
        store.failWrites = false
        repo.log()
        assertEquals(state.revision + 1, repo.state.value.revision)
        assertEquals(repo.state.value, SnapshotCodec.decode(store.snapshot!!))
    }

    @Test fun corruptPersistenceNeverSeedsOverUserData() = runTest {
        val store = MemoryPersistence("corrupt")
        failure("has not been reset") { repository(store) }
        assertEquals("corrupt", store.snapshot)
        assertEquals(0, store.writes)
    }

    @Test fun cancellationDuringWriteCannotLeaveMemoryBehindDisk() = runTest {
        val store = MemoryPersistence()
        val repo = repository(store)
        repo.start()
        val writing = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        store.beforeWrite = {
            writing.complete(Unit)
            check(release.await(10, TimeUnit.SECONDS))
        }
        val job = launch(Dispatchers.Default) { repo.log() }
        writing.await()
        job.cancel()
        release.countDown()
        job.join()
        assertEquals(repo.state.value, SnapshotCodec.decode(store.snapshot!!))
        assertEquals(1, repo.state.value.activeWorkout!!.exercises.first().sets.size)
    }
}
