package com.rebuild.platform

import com.rebuild.domain.AppState
import com.rebuild.domain.RestTimer
import com.rebuild.domain.WorkoutExercise
import com.rebuild.domain.workEntries

/**
 * What a rest notification says besides the time: the exercise being rested from, and what comes next, read from the
 * saved workout exactly as logged. Nothing is guessed: with no target there is no "of N", and when every planned set
 * is done it names the next exercise still to do rather than inventing another set.
 */
data class RestContext(val exercise: String, val next: String?)

fun restContext(state: AppState, timer: RestTimer): RestContext? {
    val workout = state.workouts.find { it.id == timer.workoutId } ?: return null
    val index = workout.exercises.indexOfFirst { it.id == timer.exerciseId }
    val current = workout.exercises.getOrNull(index) ?: return null
    val done = current.workingDone()
    val target = current.targetSets
    val next = when {
        timer.shortPause -> "Next: more reps on this set"
        target == null -> "Next: set ${done + 1}"
        done < target -> "Next: set ${done + 1} of $target"
        else -> workout.exercises.drop(index + 1).plus(workout.exercises.take(index))
            .firstOrNull { !it.skipped && it.targetSets != null && it.workingDone() < it.targetSets }
            ?.let { "Next: ${it.exercise.name}" }
            ?: "Every planned set is done"
    }
    return RestContext(current.exercise.name, next)
}

private fun WorkoutExercise.workingDone(): Int =
    // A measured exercise (a hold, carry or cardio) logs efforts and never sets, so whichever it has is what counts.
    if (efforts.isNotEmpty()) efforts.size else workEntries().count { it.counted }
