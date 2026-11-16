package com.rebuild.platform

import com.rebuild.domain.AppState
import com.rebuild.domain.RestTimer
import com.rebuild.domain.WorkoutExercise
import com.rebuild.domain.workEntries

/**
 * What comes after a rest, read from the saved workout exactly as logged, for every surface that announces it (the
 * on-screen takeover, the countdown and the rest-over notification). Nothing is guessed: with no target there is no
 * "of N", and when every planned set of this exercise is done it names the next exercise still to do.
 */
data class RestContext(
    /** The exercise just rested from. */
    val exercise: String,
    /** The working set coming up on [exercise], counting from 1; null when this exercise is done. */
    val nextSet: Int? = null,
    /** How many working sets [exercise] is planned for; null without a target. */
    val ofSets: Int? = null,
    /** The next exercise still to do, once [exercise] is done. */
    val nextExercise: String? = null,
    /** A rest-pause's short pause: more reps on the same set come next. */
    val shortPause: Boolean = false
) {
    /** The one line a notification shows under its title. */
    val next: String
        get() = when {
            shortPause -> "Next: more reps on this set"
            nextSet != null -> "Next: set $nextSet" + (ofSets?.let { " of $it" } ?: "")
            nextExercise != null -> "Next: $nextExercise"
            else -> "Every planned set is done"
        }

    /** The big words of an "up next" headline: "Set 2", or the next exercise's name. */
    val headline: String
        get() = when {
            shortPause -> "Go again"
            nextSet != null -> "Set $nextSet"
            nextExercise != null -> nextExercise
            else -> "All sets done"
        }

    /** The small line under the headline: "of 3 · Bench press", or where the next exercise comes from. */
    val detail: String
        get() = when {
            shortPause -> exercise
            nextSet != null -> listOfNotNull(ofSets?.let { "of $it" }, exercise.takeIf { it.isNotBlank() }).joinToString(" · ")
            nextExercise != null -> "after $exercise"
            else -> exercise
        }
}

fun restContext(state: AppState, timer: RestTimer): RestContext? {
    val workout = state.workouts.find { it.id == timer.workoutId } ?: return null
    val index = workout.exercises.indexOfFirst { it.id == timer.exerciseId }
    val current = workout.exercises.getOrNull(index) ?: return null
    val name = current.exercise.name
    if (timer.shortPause) return RestContext(name, shortPause = true)
    val done = current.workingDone()
    val target = current.targetSets
    return when {
        target == null -> RestContext(name, nextSet = done + 1)
        done < target -> RestContext(name, nextSet = done + 1, ofSets = target)
        else -> RestContext(name, nextExercise = workout.exercises.drop(index + 1).plus(workout.exercises.take(index))
            .firstOrNull { !it.skipped && it.targetSets != null && it.workingDone() < it.targetSets }?.exercise?.name)
    }
}

private fun WorkoutExercise.workingDone(): Int =
    // A measured exercise (a hold, carry or cardio) logs efforts and never sets, so whichever it has is what counts.
    if (efforts.isNotEmpty()) efforts.size else workEntries().count { it.counted }
