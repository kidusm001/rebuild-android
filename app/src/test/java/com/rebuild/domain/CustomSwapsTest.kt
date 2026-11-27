package com.rebuild.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomSwapsTest {
    private fun custom(id: String, muscle: MuscleGroup) =
        Exercise(id, id, custom = true, equipment = Equipment.CABLE, primaryMuscle = muscle)

    private val state = Seed.initialState().let {
        it.copy(exercises = it.exercises + listOf(
            custom("hammer-cable", MuscleGroup.BICEPS),
            custom("pec-deck", MuscleGroup.CHEST),
            custom("skull", MuscleGroup.TRICEPS),
            custom("mystery", MuscleGroup.UNKNOWN)
        ))
    }

    @Test fun offersOnlyOwnExercisesForTheSameMuscle() {
        assertEquals(listOf("hammer-cable"), state.customSwapsFor("curl-cable"))
        assertEquals(listOf("pec-deck"), state.customSwapsFor("incline-db"))
    }

    @Test fun offersNoneForAnUnknownMuscleOrExercise() {
        assertEquals(emptyList<String>(), state.customSwapsFor("mystery"))
        assertEquals(emptyList<String>(), state.customSwapsFor("no-such-exercise"))
        assertEquals(emptyList<String>(), state.customSwapsFor(null))
    }

    @Test fun neverOffersTheExerciseItself() {
        assertEquals(emptyList<String>(), state.customSwapsFor("skull"))
    }
}

class CatalogMetadataTest {
    @Test fun everyBuiltInExerciseNamesItsMuscleAndEquipment() {
        val missing = (ExerciseCatalog.exercises + Seed.initialState().exercises).filter {
            it.primaryMuscle == MuscleGroup.UNKNOWN || it.equipment == Equipment.UNKNOWN
        }.map { it.id }.distinct()
        assertEquals(emptyList<String>(), missing)
    }
}

class MuscleChartFallbackTest {
    @Test fun anOlderWorkoutCopyWithNoMuscleCountsUnderTheLibrarysMuscle() {
        val old = Exercise("mine", "Mine", custom = true)
        val set = LoggedSet("s1", 20.0, 8, null, false, 1L)
        val entry = WorkoutExercise("e1", null, old, null, null, null, null, null, null, 90, sets = listOf(set))
        val workout = Workout("w1", null, "Extra", Phase.INTRO_ONE, 1L, finishedAt = 2L, exercises = listOf(entry),
            startDate = "2026-11-23")
        val state = Seed.initialState().let {
            it.copy(exercises = it.exercises + old.copy(primaryMuscle = MuscleGroup.BICEPS, equipment = Equipment.CABLE),
                workouts = listOf(workout))
        }
        assertEquals(listOf(MuscleGroup.BICEPS to 1),
            state.weeklySetsByMuscle(java.time.LocalDate.parse("2026-11-23")))
        // The saved record itself is untouched.
        assertEquals(MuscleGroup.UNKNOWN, state.workouts.single().exercises.single().exercise.primaryMuscle)
    }
}
