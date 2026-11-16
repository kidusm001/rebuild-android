package com.rebuild.platform

import com.rebuild.domain.AppState
import com.rebuild.domain.RestTimer
import com.rebuild.domain.Measure
import com.rebuild.domain.goalReached
import com.rebuild.domain.hasGoal
import com.rebuild.domain.qualifyingCount
import com.rebuild.domain.workingCount

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
    val shortPause: Boolean = false,
    /** Cardio whose totals aren't reached yet: more of the same comes next, with no set to count. */
    val keepGoing: Boolean = false
) {
    /** The one line a notification shows under its title. */
    val next: String
        get() = when {
            shortPause -> "Next: more reps on this set"
            keepGoing -> "Next: more $exercise"
            nextSet != null -> "Next: set $nextSet" + (ofSets?.let { " of $it" } ?: "")
            nextExercise != null -> "Next: $nextExercise"
            else -> "Every planned set is done"
        }

    /** The big words of an "up next" headline: "Set 2", or the next exercise's name. */
    val headline: String
        get() = when {
            shortPause -> "Go again"
            keepGoing -> "Keep going"
            nextSet != null -> "Set $nextSet"
            nextExercise != null -> nextExercise
            else -> "All sets done"
        }

    /** The small line under the headline: "of 3 · Bench press", or where the next exercise comes from. */
    val detail: String
        get() = when {
            shortPause || keepGoing -> exercise
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
    // The workout's own goal rules decide whether this exercise is done: working sets for reps, efforts that reach
    // the minimum for timed and distance sets, the totals for cardio.
    val stillGoing = !current.hasGoal() || !current.goalReached()
    if (stillGoing) {
        if (current.exercise.measure == Measure.CARDIO) return RestContext(name, keepGoing = true)
        val done = if (current.exercise.measure == Measure.REPS) current.workingCount() else current.qualifyingCount()
        return RestContext(name, nextSet = done + 1, ofSets = current.targetSets)
    }
    val next = workout.exercises.drop(index + 1).plus(workout.exercises.take(index))
        .firstOrNull { !it.skipped && it.hasGoal() && !it.goalReached() }
    return RestContext(name, nextExercise = next?.exercise?.name)
}
