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
    val sets = recordSets(workouts, unit, matches)
    val best = (1..MAX_RECORD_REPS).mapNotNull { reps -> heaviest(sets.filter { it.reps == reps })?.let { Triple(reps, it.set, it.workout) } }
    return best.map { (reps, set, workout) ->
        val load = set.loadValue!!
        RepRecord(reps, load, set, workout, superseded = best.any { (r, s, _) -> r > reps && s.loadValue!! >= load })
    }
}

/** One normal working set as lifted, in its own unit, on the day its workout started. */
data class LiftedSet(val set: LoggedSet, val workout: Workout, val date: LocalDate) {
    val reps: Int get() = set.reps
    val load: Double get() = set.loadValue!!
}

/**
 * The sets records are made of: finished workouts, normal working sets (each by its own first stage, never a drop or
 * a warm-up), in [unit] only, with a load. Assisted loads are left out, as for [repRecords]. Oldest first.
 */
fun recordSets(workouts: List<Workout>, unit: LoadUnit, matches: (WorkoutExercise) -> Boolean): List<LiftedSet> =
    workouts.filter { it.finishedAt != null }.sortedBy { it.startedAt }.flatMap { w ->
        val date = w.startDate?.let(LocalDate::parse) ?: localDateOf(w.startedAt)
        w.exercises.filter(matches).filter { it.exercise.loading == LoadingType.EXTERNAL }.flatMap { e ->
            e.mainSets().filter { it.loadUnit == unit && it.loadValue != null }.map { LiftedSet(it, w, date) }
        }
    }

/** The heaviest of [sets]; among equals, the first time it was done. */
private fun heaviest(sets: List<LiftedSet>): LiftedSet? =
    sets.maxWithOrNull(compareBy<LiftedSet> { it.load }.thenByDescending { it.set.completedAt })

/**
 * The heaviest set at each rep count among [sets] lifted on or before [asOf] (every set when it is null): what the
 * records said that day. Only rep counts actually lifted by then appear.
 */
fun bestByReps(sets: List<LiftedSet>, asOf: LocalDate? = null): Map<Int, LiftedSet> =
    sets.filter { asOf == null || it.date <= asOf }.groupBy { it.reps }
        .mapValues { (_, atReps) -> heaviest(atReps)!! }.toSortedMap()

/**
 * One rep count's heaviest set [now] beside the heaviest [then], as of a comparison day. [then] is null when the rep
 * count was first lifted after that day, so there is nothing to compare it with. Both are sets actually lifted; the
 * change is never negative, because a later day's best includes every earlier set.
 */
data class RepComparison(val reps: Int, val now: LiftedSet, val then: LiftedSet?) {
    val new: Boolean get() = then == null
    /** How much heavier the best set is now; zero when it is the same set or the same load. */
    val gain: Double get() = then?.let { now.load - it.load } ?: 0.0
}

/** One column for every rep count lifted in [sets], fewest reps first, comparing its best now with its best on [asOf]. */
fun repComparisons(sets: List<LiftedSet>, asOf: LocalDate): List<RepComparison> {
    val then = bestByReps(sets, asOf)
    return bestByReps(sets).map { (reps, now) -> RepComparison(reps, now, then[reps]) }
}

/**
 * Working sets per primary muscle in the Monday–Sunday week starting [weekStart], finished workouts only. A workout
 * keeps its own copy of each exercise; where that copy has no muscle (custom exercises made before one was required)
 * the library's current muscle stands in, so setting it later fixes past weeks too. Saved records never change.
 */
fun AppState.weeklySetsByMuscle(weekStart: LocalDate): List<Pair<MuscleGroup, Int>> {
    val end = weekStart.plusDays(7)
    val libraryMuscle = exercises.associate { it.id to it.primaryMuscle }
    return workouts.filter { w ->
        w.finishedAt != null && w.startDate?.let { LocalDate.parse(it) }?.let { it >= weekStart && it < end } == true
    }.flatMap { it.exercises }
        .filter { it.exercise.measure == Measure.REPS }
        .groupBy { e ->
            e.exercise.primaryMuscle.takeIf { it != MuscleGroup.UNKNOWN } ?: libraryMuscle[e.exercise.id] ?: MuscleGroup.UNKNOWN
        }
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
