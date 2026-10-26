package com.rebuild.data

import com.rebuild.domain.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PlateLoadTest {
    private val cable = Seed.exercises.single { it.id == "pulldown" }

    @Test fun plateValidationIsStrictAndKilogramAndBodyweightRulesRemainUnchanged() {
        listOf(1, 7, 1000).forEach { SnapshotCodec.validateSet(cable, null, 10, 2.5, LoadUnit.PLATES, it) }
        listOf<Int?>(null, -1, 0, 1001, Int.MAX_VALUE).forEach {
            rejected { SnapshotCodec.validateSet(cable, null, 10, null, LoadUnit.PLATES, it) }
        }
        listOf(0.0, 25.5, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            rejected { SnapshotCodec.validateSet(cable, it, 10, null, LoadUnit.PLATES, 4) }
        }
        rejected { SnapshotCodec.validateSet(cable, 20.0, 10, null, LoadUnit.KG, 4) }
        rejected { SnapshotCodec.validateSet(cable, null, 0, null, LoadUnit.PLATES, 4) }
        rejected { SnapshotCodec.validateSet(cable, null, 10, Double.NaN, LoadUnit.PLATES, 4) }
        rejected { SnapshotCodec.validateSet(cable, null, 10, 10.1, LoadUnit.PLATES, 4) }
        Equipment.entries.forEach { equipment ->
            val variant = cable.copy(equipment = equipment)
            if (variant.supportsPlates) SnapshotCodec.validateSet(variant, null, 1, null, LoadUnit.PLATES, 1)
            else rejected { SnapshotCodec.validateSet(variant, null, 1, null, LoadUnit.PLATES, 1) }
        }
        listOf(cable.copy(perDumbbell = true), cable.copy(loading = LoadingType.BODYWEIGHT),
            cable.copy(loading = LoadingType.ASSISTANCE)).forEach {
            rejected { SnapshotCodec.validateSet(it, null, 10, null, LoadUnit.PLATES, 1) }
        }
        SnapshotCodec.validateSet(cable, 23.125, 10, 1.25)
        SnapshotCodec.validateSet(cable, 0.0, 1, null)
        SnapshotCodec.validateSet(cable.copy(loading = LoadingType.ASSISTANCE), 26.5, 10, null)
        SnapshotCodec.validateSet(cable.copy(loading = LoadingType.BODYWEIGHT), null, 10, null)
        rejected { SnapshotCodec.validateSet(cable.copy(loading = LoadingType.BODYWEIGHT), 0.0, 10, null) }
        rejected { SnapshotCodec.validateSet(cable.copy(loading = LoadingType.ASSISTANCE), null, 10, null) }
    }

    @Test fun entrySwitchAfterKgSetsChangesOnlyEntryAndLibraryPreferenceNotHistoryOrRest() = runTest {
        val repo = repository()
        val workout = repo.start(template = "pull")
        val first = workout.exercises.first()
        repo.log(kg = 37.5)
        val before = repo.state.value
        repo.execute(Action.SetLoadUnit(workout.id, first.id, LoadUnit.PLATES))
        val after = repo.state.value
        assertEquals(before.revision + 1, after.revision)
        assertEquals(LoadUnit.PLATES, after.activeWorkout!!.exercises.first().entryLoadUnit)
        assertEquals(LoadUnit.PLATES, after.exercises.single { it.id == cable.id }.preferredLoadUnit)
        assertEquals(before.activeWorkout!!.exercises.first().exercise, after.activeWorkout!!.exercises.first().exercise)
        assertEquals(before.activeWorkout!!.exercises.first().sets, after.activeWorkout!!.exercises.first().sets)
        assertEquals(before.restTimer, after.restTimer)
        assertEquals(before.overrides, after.overrides)
        assertEquals(before.templates, after.templates)
        repo.execute(Action.SetLoadUnit(workout.id, first.id, LoadUnit.PLATES))
        assertEquals(after, repo.state.value)
        repo.log(id = "plates", kg = null, loadUnit = LoadUnit.PLATES, plates = 6, now = 3000)
        val sets = repo.state.value.activeWorkout!!.exercises.first().sets
        assertEquals(listOf(LoadUnit.KG, LoadUnit.PLATES), sets.map { it.loadUnit })
        assertEquals(listOf(37.5, null), sets.map { it.kg })
        assertEquals(listOf(null, 6), sets.map { it.plates })
        val mixed = repo.state.value
        repo.execute(Action.SetLoadUnit(workout.id, first.id, LoadUnit.KG))
        assertEquals(mixed.restTimer, repo.state.value.restTimer)
        assertEquals(sets, repo.state.value.activeWorkout!!.exercises.first().sets)
        assertEquals(LoadUnit.KG, repo.state.value.exercises.single { it.id == cable.id }.preferredLoadUnit)
    }

    @Test fun conflictingKilogramsAndPlatesNeverReachPersistenceOrMutateExistingSets() = runTest {
        val store = MemoryPersistence()
        val repo = repository(store)
        repo.start(template = "pull")
        repo.log(id = "existing", kg = 31.25)
        val before = repo.state.value
        val disk = store.snapshot
        val writes = store.writes
        store.beforeWrite = { fail("Conflicting loads must be rejected before persistence") }
        LoadUnit.entries.forEach { unit ->
            listOf("existing", "new").forEach { setId ->
                failure {
                    repo.log(id = setId, kg = 40.5, loadUnit = unit, plates = 7, now = 3000)
                }
                assertEquals(before, repo.state.value)
                assertEquals(disk, store.snapshot)
                assertEquals(writes, store.writes)
            }
        }
    }

    @Test fun libraryPreferenceOnlyAffectsFutureStartsAndActualSubstitutions() = runTest {
        val repo = repository()
        repo.execute(Action.SetExerciseLoadUnit(cable.id, LoadUnit.PLATES))
        val workout = repo.start(template = "pull")
        val first = workout.exercises.first()
        assertEquals(LoadUnit.PLATES, first.entryLoadUnit)
        assertEquals(LoadUnit.PLATES, first.exercise.preferredLoadUnit)
        repo.execute(Action.SetExerciseLoadUnit(cable.id, LoadUnit.KG))
        assertEquals(first, repo.state.value.activeWorkout!!.exercises.first())
        repo.execute(Action.EditToday(first.id, cable.id, 3, 180))
        assertEquals(first, repo.state.value.activeWorkout!!.exercises.first())
        repo.execute(Action.EditToday(first.id, "pullup", 3, 180))
        assertEquals(LoadUnit.KG, repo.state.value.activeWorkout!!.exercises.first().entryLoadUnit)
        repo.execute(Action.SetExerciseLoadUnit(cable.id, LoadUnit.PLATES))
        repo.execute(Action.EditToday(first.id, cable.id, 3, 180))
        assertEquals(LoadUnit.PLATES, repo.state.value.activeWorkout!!.exercises.first().entryLoadUnit)
        repo.log(id = "p", kg = null, loadUnit = LoadUnit.PLATES, plates = 5)
        failure("Remove") { repo.execute(Action.EditToday(first.id, "pullup", 3, 180)) }
        repo.execute(Action.FinishWorkout(3000))
        val history = repo.state.value.workouts.single()
        repo.execute(Action.SetExerciseLoadUnit(cable.id, LoadUnit.KG))
        repo.execute(Action.StartWorkout("pull", 4000))
        assertEquals(LoadUnit.KG, repo.state.value.activeWorkout!!.exercises.first().entryLoadUnit)
        assertEquals(history, repo.state.value.workouts.first())
    }

    @Test fun loadActionsRejectHistoryUnknownIdsAndIncapableOrChangedDefinitionsAtomically() = runTest {
        val store = MemoryPersistence()
        val repo = repository(store)
        val workout = repo.start(template = "pull")
        val first = workout.exercises.first()
        val before = repo.state.value
        val writes = store.writes
        failure("active") { repo.execute(Action.SetLoadUnit("missing", first.id, LoadUnit.PLATES)) }
        failure("not part") { repo.execute(Action.SetLoadUnit(workout.id, "missing", LoadUnit.PLATES)) }
        failure("no longer") { repo.execute(Action.SetExerciseLoadUnit("missing", LoadUnit.PLATES)) }
        failure("Plate") { repo.execute(Action.SetExerciseLoadUnit("incline-db", LoadUnit.PLATES)) }
        failure("Plate") { repo.execute(Action.SetExerciseLoadUnit("pullup-assisted", LoadUnit.PLATES)) }
        failure("Plate") { repo.execute(Action.SetLoadUnit(workout.id, workout.exercises[1].id, LoadUnit.PLATES)) }
        assertEquals(before, repo.state.value)
        assertEquals(writes, store.writes)
        repo.execute(Action.AddExercise(cable.copy(equipment = Equipment.UNKNOWN)))
        val edited = repo.state.value
        failure("Plate") { repo.execute(Action.SetLoadUnit(workout.id, first.id, LoadUnit.PLATES)) }
        assertEquals(edited, repo.state.value)
        repo.execute(Action.FinishWorkout(3000))
        val finished = repo.state.value
        failure("active") { repo.execute(Action.SetLoadUnit(workout.id, first.id, LoadUnit.KG)) }
        assertEquals(finished, repo.state.value)
    }

    @Test fun failedPreferenceOrPlateWriteNeverPublishesAndIdempotentSavesNeverRestartRest() = runTest {
        val store = MemoryPersistence()
        val repo = repository(store)
        val workout = repo.start(template = "pull")
        val first = workout.exercises.first()
        val before = repo.state.value
        val disk = store.snapshot
        store.beforeWrite = { assertEquals(before, repo.state.value) }
        store.failWrites = true
        failure("unchanged") { repo.execute(Action.SetLoadUnit(workout.id, first.id, LoadUnit.PLATES)) }
        failure("unchanged") { repo.log(kg = null, loadUnit = LoadUnit.PLATES, plates = 7) }
        assertEquals(before, repo.state.value)
        assertEquals(disk, store.snapshot)
        store.failWrites = false
        store.beforeWrite = null
        List(8) { async { repo.log(kg = null, loadUnit = LoadUnit.PLATES, plates = 7) } }.awaitAll()
        val saved = repo.state.value
        assertEquals(before.revision + 1, saved.revision)
        assertEquals(1, saved.activeWorkout!!.exercises.first().sets.size)
        val writes = store.writes
        repo.log(kg = null, loadUnit = LoadUnit.PLATES, plates = 7, now = 9000)
        assertEquals(saved, repo.state.value)
        assertEquals(writes, store.writes)
        repo.log(kg = null, loadUnit = LoadUnit.PLATES, plates = 8, now = 10000)
        assertEquals(saved.restTimer, repo.state.value.restTimer)
        assertEquals(2000L, repo.state.value.activeWorkout!!.exercises.first().sets.single().completedAt)
        assertEquals(repo.state.value, repository(store).state.value)
    }

    @Test fun explicitMixedUnitHistoryCorrectionsRetainTimestampsAndDoNotChangeActiveTimerOrPreferences() = runTest {
        val repo = repository()
        val history = repo.start(template = "pull")
        val exercise = history.exercises.first()
        repo.log(id = "kg", kg = 31.75)
        repo.log(id = "plate", kg = null, loadUnit = LoadUnit.PLATES, plates = 5, now = 2500)
        repo.execute(Action.FinishWorkout(3000))
        repo.execute(Action.StartWorkout("pull", 4000))
        repo.log(id = "active", now = 5000)
        val before = repo.state.value
        repo.log(history, exercise, id = "kg", kg = null, loadUnit = LoadUnit.PLATES, plates = 6, now = 6000)
        repo.log(history, exercise, id = "plate", kg = 42.125, now = 7000)
        val after = repo.state.value
        val sets = after.workouts.first().exercises.first().sets
        assertEquals(listOf(LoadUnit.PLATES, LoadUnit.KG), sets.map { it.loadUnit })
        assertEquals(listOf(null, 42.125), sets.map { it.kg })
        assertEquals(listOf(6, null), sets.map { it.plates })
        assertEquals(listOf(2000L, 2500L), sets.map { it.completedAt })
        assertEquals(before.restTimer, after.restTimer)
        assertEquals(before.activeWorkout, after.activeWorkout)
        assertEquals(before.exercises, after.exercises)
        assertEquals(before.workouts.first().exercises.first().entryLoadUnit,
            after.workouts.first().exercises.first().entryLoadUnit)
    }

    @Test fun previousComparableSelectsLatestMatchingWorkingUnitAndReturnsOnlyThatUnit() = runTest {
        val repo = repository()
        val first = repo.start(template = "pull")
        repo.log(id = "old-kg", kg = 35.125)
        repo.execute(Action.FinishWorkout(3000))
        repo.execute(Action.StartWorkout("pull", 4000))
        val second = requireNotNull(repo.state.value.activeWorkout)
        repo.log(id = "p-warm", kg = null, loadUnit = LoadUnit.PLATES, plates = 2, warmup = true, now = 4500)
        repo.log(id = "p-work", kg = null, loadUnit = LoadUnit.PLATES, plates = 7, now = 5000)
        repo.log(id = "kg-warm", kg = 12.5, warmup = true, now = 5500)
        repo.execute(Action.FinishWorkout(6000))
        repo.execute(Action.StartWorkout("pull", 7000))
        val active = requireNotNull(repo.state.value.activeWorkout)
        repo.log(id = "active-p", kg = null, loadUnit = LoadUnit.PLATES, plates = 9, now = 8000)
        val state = repo.state.value
        val kilograms = requireNotNull(state.previousComparable(cable, active.id))
        val plates = requireNotNull(state.previousComparable(cable, active.id, LoadUnit.PLATES))
        assertEquals(first.exercises.first().id, kilograms.id)
        assertEquals(listOf("old-kg"), kilograms.sets.map { it.id })
        assertEquals(second.exercises.first().id, plates.id)
        assertEquals(listOf("p-warm", "p-work"), plates.sets.map { it.id })
        assertTrue(plates.sets.all { it.kg == null })
        assertNull(state.previousComparable(cable, second.id, LoadUnit.PLATES))
        assertNull(state.previousComparable(cable.copy(id = "row-cable"), active.id, LoadUnit.PLATES))
        assertNull(state.previousComparable(cable.copy(loading = LoadingType.ASSISTANCE), active.id, LoadUnit.PLATES))
        assertNull(state.previousComparable(cable.copy(perDumbbell = true), active.id, LoadUnit.PLATES))
        repo.log(second, second.exercises.first(), id = "newer-kg", kg = 40.75, now = 9000)
        assertEquals(listOf("kg-warm", "newer-kg"),
            repo.state.value.previousComparable(cable, active.id)!!.sets.map { it.id })
        assertEquals(listOf("p-warm", "p-work"),
            repo.state.value.previousComparable(cable, active.id, LoadUnit.PLATES)!!.sets.map { it.id })
    }
}
