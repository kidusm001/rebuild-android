package com.rebuild.data

import com.rebuild.domain.*
import java.time.ZoneId

/** Normalization is pure; the repository commits all migrations together before publishing. */
internal object StateMigrations {
    fun apply(state: AppState, zone: ZoneId = ZoneId.systemDefault()): AppState {
        val updated = startDates(PlanMigrations.apply(catalog(state)), zone)
            .copy(schemaVersion = SnapshotCodec.SCHEMA_VERSION)
        SnapshotCodec.validate(updated)
        return updated
    }

    /**
     * Older records didn't store the local date a workout started on, which decides its week. It is derived once, in
     * the phone's current zone, and stored; the recorded timestamps themselves are never changed.
     */
    private fun startDates(state: AppState, zone: ZoneId): AppState =
        if (state.workouts.all { it.startDate != null }) state else state.copy(workouts = state.workouts.map {
            if (it.startDate != null) it else it.copy(startDate = localDateOf(it.startedAt, zone).toString())
        })

    /**
     * Each catalog revision adds only its own new exercises, once, and never changes or replaces an exercise the
     * library already has (custom exercises included). The library stays within its 1,000-exercise limit.
     */
    private fun catalog(state: AppState): AppState {
        var updated = state
        if (updated.catalogRevision < 1) {
            val existingIds = updated.exercises.map { it.id }.toSet()
            updated = updated.copy(
                catalogRevision = 1,
                exercises = updated.exercises.map(::legacyMetadata) +
                    ExerciseCatalog.revisionOne.filterNot { it.id in existingIds },
                workouts = updated.workouts.map { workout ->
                    if (workout.id != updated.activeWorkoutId) workout else workout.copy(
                        exercises = workout.exercises.map { it.copy(exercise = legacyMetadata(it.exercise)) })
                }
            )
        }
        if (updated.catalogRevision < 2) {
            val existingIds = updated.exercises.map { it.id }.toSet()
            val room = (1000 - updated.exercises.size).coerceAtLeast(0)
            updated = updated.copy(catalogRevision = 2,
                exercises = updated.exercises + ExerciseCatalog.revisionTwo.filterNot { it.id in existingIds }.take(room))
        }
        return updated
    }

    private fun legacyMetadata(exercise: Exercise): Exercise {
        val canonical = ExerciseCatalog.legacyExercises.singleOrNull {
            it.id == exercise.id && it.name == exercise.name && it.loading == exercise.loading &&
                it.perDumbbell == exercise.perDumbbell && !exercise.custom
        } ?: return exercise
        return exercise.copy(
            equipment = if (exercise.equipment == Equipment.UNKNOWN) canonical.equipment else exercise.equipment,
            primaryMuscle = if (exercise.primaryMuscle == MuscleGroup.UNKNOWN) canonical.primaryMuscle else exercise.primaryMuscle,
            aliases = exercise.aliases.ifEmpty { canonical.aliases }
        )
    }
}
