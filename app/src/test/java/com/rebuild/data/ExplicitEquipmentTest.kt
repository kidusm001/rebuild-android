package com.rebuild.data

import com.rebuild.domain.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ExplicitEquipmentTest {
    private val custom = Exercise("custom-cable", "My own cable station", custom = true)

    private suspend fun RebuildRepository.startCustom(definition: Exercise = custom): Workout {
        execute(Action.AddExercise(definition))
        execute(Action.EditPlan(Phase.BASELINE, "pull-vertical", definition.id, 3, 180))
        return start(template = "pull")
    }

    @Test fun explicitEquipmentConfirmationEnablesActiveCustomAfterKgSetsWithoutRelabelingAnything() = runTest {
        val store = MemoryPersistence()
        val repo = repository(store)
        val workout = repo.startCustom()
        val exercise = workout.exercises.first()
        repo.log(id = "warmup", kg = 12.5, warmup = true)
        repo.log(id = "working", kg = 26.125, now = 2500)
        repo.execute(Action.ExerciseNote(workout.id, exercise.id, "Original machine notes"))
        assertFalse(LoadUnit.PLATES in repo.state.value.loadUnitChoices(workout.id, exercise.id))
        val captured = repo.state.value.activeWorkout!!.exercises.first()
        val explicit = custom.copy(equipment = Equipment.CABLE, primaryMuscle = MuscleGroup.BACK,
            aliases = listOf("My lat station"))
        repo.execute(Action.AddExercise(explicit))
        assertEquals(captured, repo.state.value.activeWorkout!!.exercises.first())
        val before = repo.state.value
        assertTrue(LoadUnit.PLATES in before.loadUnitChoices(workout.id, exercise.id))
        assertFalse(before.canSetLoadUnit("different-workout", exercise.id))
        assertFalse(before.canSetLoadUnit(workout.id, "different-exercise"))
        repo.execute(Action.SetLoadUnit(workout.id, exercise.id, LoadUnit.PLATES))
        val after = repo.state.value
        assertEquals(captured.copy(exercise = custom.copy(equipment = Equipment.CABLE),
            entryLoadUnit = LoadUnit.PLATES), after.activeWorkout!!.exercises.first())
        assertEquals(explicit.copy(preferredLoadUnit = LoadUnit.PLATES),
            after.exercises.single { it.id == custom.id })
        assertEquals(before.restTimer, after.restTimer)
        assertEquals(before.templates, after.templates)
        assertEquals(before.overrides, after.overrides)
        assertEquals(before.revision + 1, after.revision)
        val writes = store.writes
        repo.execute(Action.SetLoadUnit(workout.id, exercise.id, LoadUnit.PLATES))
        assertEquals(after, repo.state.value)
        assertEquals(writes, store.writes)
        repo.log(id = "new-plates", kg = null, loadUnit = LoadUnit.PLATES, plates = 8, now = 3000)
        assertEquals(captured.sets, repo.state.value.activeWorkout!!.exercises.first().sets.take(2))
        assertEquals(repo.state.value, repository(store).state.value)
    }

    @Test fun mismatchingIdentityAndUnsupportedEquipmentCannotEnrichAnUnknownSnapshot() = runTest {
        listOf(
            custom.copy(equipment = Equipment.CABLE, name = "Renamed station"),
            custom.copy(equipment = Equipment.CABLE, loading = LoadingType.ASSISTANCE),
            custom.copy(equipment = Equipment.CABLE, perDumbbell = true),
            custom.copy(equipment = Equipment.CABLE, custom = false),
            custom.copy(equipment = Equipment.MACHINE),
            custom.copy(equipment = Equipment.BARBELL),
            custom
        ).forEach { definition ->
            val store = MemoryPersistence()
            val repo = repository(store)
            val workout = repo.startCustom()
            val exercise = workout.exercises.first()
            repo.log(kg = 23.75)
            repo.execute(Action.AddExercise(definition))
            val before = repo.state.value
            val disk = store.snapshot
            val writes = store.writes
            assertFalse(LoadUnit.PLATES in before.loadUnitChoices(workout.id, exercise.id))
            failure("Plate") { repo.execute(Action.SetLoadUnit(workout.id, exercise.id, LoadUnit.PLATES)) }
            assertEquals(before, repo.state.value)
            assertEquals(disk, store.snapshot)
            assertEquals(writes, store.writes)
        }
    }

    @Test fun knownEquipmentAndFinishedSnapshotsAreNeverReclassified() = runTest {
        val repo = repository()
        val workout = repo.startCustom(custom.copy(equipment = Equipment.MACHINE))
        repo.log()
        repo.execute(Action.AddExercise(custom.copy(equipment = Equipment.CABLE)))
        val captured = repo.state.value
        assertFalse(LoadUnit.PLATES in captured.loadUnitChoices(workout.id, workout.exercises.first().id))
        failure("Plate") { repo.execute(Action.SetLoadUnit(workout.id, workout.exercises.first().id, LoadUnit.PLATES)) }
        assertEquals(captured, repo.state.value)
        repo.execute(Action.FinishWorkout(3000))
        val history = repo.state.value.workouts.single()
        assertFalse(repo.state.value.canSetLoadUnit(workout.id, workout.exercises.first().id))
        failure("active") { repo.execute(Action.SetLoadUnit(workout.id, workout.exercises.first().id, LoadUnit.PLATES)) }
        repo.execute(Action.StartWorkout("pull", 4000))
        assertEquals(Equipment.CABLE, repo.state.value.activeWorkout!!.exercises.first().exercise.equipment)
        assertEquals(history, repo.state.value.workouts.first())
    }

    @Test fun failedEquipmentAdoptionIsAtomicAndNeverChangesSnapshotOrPreferenceOrRest() = runTest {
        val store = MemoryPersistence()
        val repo = repository(store)
        val workout = repo.startCustom()
        repo.log(kg = 37.125)
        repo.execute(Action.AddExercise(custom.copy(equipment = Equipment.STACK_MACHINE)))
        val before = repo.state.value
        val disk = store.snapshot
        val writes = store.writes
        assertTrue(LoadUnit.PLATES in before.loadUnitChoices(workout.id, workout.exercises.first().id))
        store.beforeWrite = { assertEquals(before, repo.state.value) }
        store.failWrites = true
        failure("unchanged") {
            repo.execute(Action.SetLoadUnit(workout.id, workout.exercises.first().id, LoadUnit.PLATES))
        }
        assertEquals(before, repo.state.value)
        assertEquals(disk, store.snapshot)
        assertEquals(writes, store.writes)
        store.beforeWrite = null
        store.failWrites = false
        repo.execute(Action.SetLoadUnit(workout.id, workout.exercises.first().id, LoadUnit.PLATES))
        assertEquals(Equipment.STACK_MACHINE, repo.state.value.activeWorkout!!.exercises.first().exercise.equipment)
        assertEquals(before.activeWorkout!!.exercises.first().sets, repo.state.value.activeWorkout!!.exercises.first().sets)
        assertEquals(before.restTimer, repo.state.value.restTimer)
    }
}
