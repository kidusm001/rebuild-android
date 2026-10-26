package com.rebuild.domain

import java.time.LocalDate

/**
 * The heaviest normal working set logged at [reps] reps, in one unit. A record is [superseded] when another set at
 * more reps moved at least as much load: it is still true, just no longer the best thing to aim at.
 */
data class RepRecord(val reps: Int, val load: Double, val set: LoggedSet, val workout: Workout, val superseded: Boolean)

const val MAX_RECORD_REPS = 15

/**
 * Actual bests at each rep count from 1 to [MAX_RECORD_REPS] for the exercise [matches] selects, in [unit] only.
 * Finished workouts, normal working sets, no estimates. Assisted loads are left out: less assistance is harder, so
 * "heaviest" means nothing there.
 */
fun repRecords(workouts: List<Workout>, unit: LoadUnit, matches: (WorkoutExercise) -> Boolean): List<RepRecord> {
    val sets = workouts.filter { it.finishedAt != null }.flatMap { w ->
        w.exercises.filter(matches).filter { it.exercise.loading == LoadingType.EXTERNAL }.flatMap { e ->
            // A set with drops counts by its own first stage, as lifted; the drops after it never do.
            e.mainSets().filter { it.loadUnit == unit && it.loadValue != null }
                .map { it to w }
        }
    }
    val best = (1..MAX_RECORD_REPS).mapNotNull { reps ->
        sets.filter { it.first.reps == reps }
            // The heaviest; among equals, the first time it was done.
            .maxWithOrNull(compareBy<Pair<LoggedSet, Workout>> { it.first.loadValue!! }.thenByDescending { it.first.completedAt })
            ?.let { (set, workout) -> Triple(reps, set, workout) }
    }
    return best.map { (reps, set, workout) ->
        val load = set.loadValue!!
        RepRecord(reps, load, set, workout, superseded = best.any { (r, s, _) -> r > reps && s.loadValue!! >= load })
    }
}

/** Working sets per primary muscle in the Monday–Sunday week starting [weekStart], finished workouts only. */
fun AppState.weeklySetsByMuscle(weekStart: LocalDate): List<Pair<MuscleGroup, Int>> {
    val end = weekStart.plusDays(7)
    return workouts.filter { w ->
        w.finishedAt != null && w.startDate?.let { LocalDate.parse(it) }?.let { it >= weekStart && it < end } == true
    }.flatMap { it.exercises }
        .filter { it.exercise.measure == Measure.REPS }
        .groupBy { it.exercise.primaryMuscle }
        .mapValues { (_, entries) -> entries.sumOf { it.workingCount() } }
        .filterValues { it > 0 }
        .toList()
        .sortedWith(compareByDescending<Pair<MuscleGroup, Int>> { it.second }.thenBy { it.first.ordinal })
}

/** Finished workouts on each day of [month] (any day in it), for a calendar. */
fun AppState.workoutDays(month: LocalDate): Map<LocalDate, List<Workout>> {
    val first = month.withDayOfMonth(1)
    val next = first.plusMonths(1)
    return workouts.filter { it.finishedAt != null }.mapNotNull { w ->
        w.startDate?.let { LocalDate.parse(it) }?.takeIf { it >= first && it < next }?.let { it to w }
    }.groupBy({ it.first }, { it.second })
}
