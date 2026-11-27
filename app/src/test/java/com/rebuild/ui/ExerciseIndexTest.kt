package com.rebuild.ui

import com.rebuild.domain.Exercise
import com.rebuild.domain.LoadingType
import com.rebuild.domain.LoggedSet
import com.rebuild.domain.MuscleGroup
import com.rebuild.domain.Phase
import com.rebuild.domain.Workout
import com.rebuild.domain.WorkoutExercise
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseIndexTest {
    private val pulldown = Exercise("pulldown", "Lat pulldown", primaryMuscle = MuscleGroup.BACK)
    private val row = Exercise("row", "Chest-supported row", primaryMuscle = MuscleGroup.BACK)
    private val press = Exercise("press", "Incline press", primaryMuscle = MuscleGroup.CHEST)
    private val mystery = Exercise("custom", "Band thing")

    private fun summary(exercise: Exercise, lastAt: Long) = ExerciseSummary(exercise, 1, lastAt, null, null)

    private val all = listOf(summary(row, 300), summary(press, 100), summary(mystery, 200), summary(pulldown, 300))

    private fun List<ExerciseGroup>.names() = map { g -> g.header to g.rows.map { it.exercise.name } }

    @Test fun recentPutsTheLatestFirstAndBreaksTiesByName() {
        assertEquals(listOf(null to listOf("Chest-supported row", "Lat pulldown", "Band thing", "Incline press")),
            arrangeExercises(all, ExerciseSort.RECENT).names())
    }

    @Test fun azIgnoresCase() {
        val lower = all + summary(Exercise("a", "ab wheel"), 0)
        assertEquals(listOf("ab wheel", "Band thing", "Chest-supported row", "Incline press", "Lat pulldown"),
            arrangeExercises(lower, ExerciseSort.AZ).single().rows.map { it.exercise.name })
    }

    @Test fun muscleGroupsInLibraryOrderWithUnspecifiedLastAndNamesAToZ() {
        assertEquals(
            listOf("Chest" to listOf("Incline press"), "Back" to listOf("Chest-supported row", "Lat pulldown"),
                "Muscle not set" to listOf("Band thing")),
            arrangeExercises(all, ExerciseSort.MUSCLE).names()
        )
    }

    @Test fun searchWorksWithEverySortAndDropsEmptyMuscles() {
        ExerciseSort.entries.forEach { sort ->
            assertEquals(sort.name, listOf("Chest-supported row"),
                arrangeExercises(all, sort, " ROW ").flatMap { it.rows }.map { it.exercise.name })
        }
        assertEquals(listOf("Back"), arrangeExercises(all, ExerciseSort.MUSCLE, "row").map { it.header })
        assertEquals(emptyList<ExerciseGroup>(), arrangeExercises(all, ExerciseSort.RECENT, "squat"))
    }

    private fun set(id: String, kg: Double?, reps: Int, at: Long, warmup: Boolean = false) =
        LoggedSet(id, kg, reps, 2.0, warmup, at)

    private fun entry(exercise: Exercise, vararg sets: LoggedSet) =
        WorkoutExercise("e-${exercise.id}", null, exercise, 2, null, null, 8, 12, null, 90, sets = sets.toList())

    private fun workout(id: String, at: Long, vararg entries: WorkoutExercise) =
        Workout(id, null, "Pull", Phase.BASELINE, at, at + 1, exercises = entries.toList())

    @Test fun summaryShowsTheLatestSessionsTopSetAndCountsSessions() {
        val older = workout("w1", 1_000, entry(pulldown, set("a", 50.0, 8, 1_001)))
        val latest = workout("w2", 2_000, entry(pulldown,
            set("w", 60.0, 5, 2_001, warmup = true), set("b", 46.25, 8, 2_002), set("c", 46.25, 9, 2_003),
            set("d", 41.25, 12, 2_004)))
        val summary = exerciseSummaries(listOf(older, latest)).single()
        assertEquals(2, summary.sessions)
        assertEquals(2_000L, summary.lastAt)
        assertEquals("a heavier warm-up or an older session never stands in", "46.25 kg × 9", summary.latest)
        assertEquals("46.25 kilograms times 9", summary.latestSpoken)
    }

    @Test fun assistedTopSetIsTheLeastAssistance() {
        val assisted = Exercise("pullup-assisted", "Assisted pull-up", LoadingType.ASSISTANCE)
        val top = topSet(entry(assisted, set("a", 30.0, 10, 1), set("b", 20.0, 6, 2), set("c", 20.0, 8, 3)))
        assertEquals("c", top?.id)
    }

    @Test fun anExerciseTwiceInOneWorkoutIsReadTogetherAndCountedOnce() {
        // Swapped in for another exercise, so it appears twice in the latest workout; the heavier set is in the second.
        val first = entry(pulldown, set("a", 40.0, 10, 2_001))
        val second = entry(pulldown, set("b", 45.0, 8, 2_005)).copy(id = "e-pulldown-2")
        val summary = exerciseSummaries(listOf(workout("w1", 1_000, entry(pulldown, set("x", 50.0, 8, 1_001))),
            workout("w2", 2_000, first, second))).single()
        assertEquals("45 kg × 8", summary.latest)
        assertEquals(2, summary.sessions)
    }
}
