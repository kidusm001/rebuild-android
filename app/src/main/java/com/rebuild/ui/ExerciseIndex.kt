package com.rebuild.ui

import com.rebuild.domain.Exercise
import com.rebuild.domain.LoadingType
import com.rebuild.domain.LoggedSet
import com.rebuild.domain.MuscleGroup
import com.rebuild.domain.Workout
import com.rebuild.domain.WorkoutExercise
import com.rebuild.domain.mainSets
import com.rebuild.domain.measured
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How History's exercise list is ordered: most recently trained first, by name, or by name under each muscle. */
internal enum class ExerciseSort(val label: String, val spoken: String) {
    RECENT("Recent", "Sort by most recent"),
    AZ("A–Z", "Sort A to Z"),
    MUSCLE("Muscle", "Sort by muscle")
}

/**
 * One exercise (one identity: loading and per-dumbbell kept apart) as History's list shows it: the latest snapshot of
 * the exercise, how many finished sessions it appears in, when it was last trained, and what that session's top set
 * or latest effort was, exactly as logged.
 */
internal data class ExerciseSummary(
    val exercise: Exercise,
    val sessions: Int,
    val lastAt: Long,
    /** "46.25 kg × 9", "5 plates/stack × 12", "1:12"; null when the latest session has nothing counted to show. */
    val latest: String?,
    /** The same for screen readers: "46.25 kilograms times 9". */
    val latestSpoken: String?
)

/** A run of rows under an optional header (a muscle, for [ExerciseSort.MUSCLE]). */
internal data class ExerciseGroup(val header: String?, val rows: List<ExerciseSummary>)

/** Every exercise with a working set or effort in [finished] workouts, in no particular order. */
internal fun exerciseSummaries(finished: List<Workout>): List<ExerciseSummary> =
    finished.filter { it.finishedAt != null }.sortedByDescending { it.startedAt }
        .flatMap { w -> w.exercises.filter { e -> e.sets.any { !it.warmup } || e.efforts.isNotEmpty() }.map { w to it } }
        .groupBy { it.second.exercise.identity() }
        .map { (_, list) ->
            val workout = list.first().first
            // An exercise can be in one workout twice (swapped in for another): its sets there are read together, and
            // that workout counts once.
            val latest = list.filter { it.first.id == workout.id }.map { it.second }.reduce { a, b ->
                a.copy(sets = a.sets + b.sets, setGroups = a.setGroups + b.setGroups, efforts = a.efforts + b.efforts)
            }
            val exercise = latest.exercise
            val (text, spoken) = latestLine(latest, exercise)
            ExerciseSummary(exercise, list.map { it.first.id }.distinct().size, workout.startedAt, text, spoken)
        }

/**
 * The rows [query] matches, in [sort] order. Recent breaks ties by name; Muscle groups by the exercise's primary
 * muscle in the library's order, unspecified last, and lists each muscle A–Z.
 */
internal fun arrangeExercises(all: List<ExerciseSummary>, sort: ExerciseSort, query: String = ""): List<ExerciseGroup> {
    val q = query.trim()
    val byName = compareBy<ExerciseSummary> { it.exercise.name.lowercase() }
    val shown = all.filter { q.isEmpty() || it.exercise.name.contains(q, ignoreCase = true) }
    return when (sort) {
        ExerciseSort.RECENT -> listOf(ExerciseGroup(null, shown.sortedWith(compareByDescending<ExerciseSummary> { it.lastAt }.then(byName))))
        ExerciseSort.AZ -> listOf(ExerciseGroup(null, shown.sortedWith(byName)))
        ExerciseSort.MUSCLE -> shown.groupBy { it.exercise.primaryMuscle }
            .toSortedMap(compareBy { if (it == MuscleGroup.UNKNOWN) Int.MAX_VALUE else it.ordinal })
            .map { (muscle, rows) -> ExerciseGroup(muscleHeader(muscle), rows.sortedWith(byName)) }
    }.filter { it.rows.isNotEmpty() }
}

private fun muscleHeader(muscle: MuscleGroup) = if (muscle == MuscleGroup.UNKNOWN) "Other" else muscle.title

/**
 * The latest session's top set: the heaviest normal working set in the unit last used (then the most reps), or for
 * assisted work the least assistance. A drop counts by its own first stage; warm-ups never stand in for a set.
 */
internal fun topSet(entry: WorkoutExercise): LoggedSet? {
    val sets = entry.mainSets().ifEmpty { return null }
    val unit = sets.maxBy { it.completedAt }.loadUnit
    val same = sets.filter { it.loadUnit == unit }
    return if (entry.exercise.loading == LoadingType.ASSISTANCE) {
        same.minWith(compareBy<LoggedSet> { it.loadValue ?: 0.0 }.thenByDescending { it.reps })
    } else {
        same.maxWith(compareBy<LoggedSet> { it.loadValue ?: 0.0 }.thenBy { it.reps })
    }
}

private fun latestLine(entry: WorkoutExercise, exercise: Exercise): Pair<String?, String?> {
    if (exercise.measured) {
        val effort = entry.efforts.maxByOrNull { it.completedAt } ?: return null to null
        return effortText(effort, exercise) to spokenEffort(effort, exercise).spokenLoad()
    }
    val set = topSet(entry) ?: return null to null
    val text = "${loadText(set.amount, set.loadUnit, set.plates, exercise)} × ${set.reps}"
    return text to text.spokenLoad()
}

/** A load line as words: "46.25 kilograms times 9", "20 pounds per dumbbell", "5 plates per stack". */
internal fun String.spokenLoad(): String = spokenUnits()
    .replace(Regex("""(\d) kg/DB\b"""), "$1 kilograms per dumbbell")
    .replace(Regex("""(\d) kg\b"""), "$1 kilograms")
    .replace("plates/stack", "plates per stack")
    .replace(" assist", " assistance")
    .replace(" × ", " times ")

/** "10 Nov", or "10 Nov 2025" outside [now]'s year. */
internal fun shortDate(millis: Long, now: Long): String = datePattern(millis, now, "d MMM")

/** "10 November", or "10 November 2025" outside [now]'s year. */
internal fun spokenDate(millis: Long, now: Long): String = datePattern(millis, now, "d MMMM")

private fun datePattern(millis: Long, now: Long, pattern: String): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(millis).atZone(zone)
    val full = if (date.year == Instant.ofEpochMilli(now).atZone(zone).year) pattern else "$pattern yyyy"
    return DateTimeFormatter.ofPattern(full, Locale.getDefault()).format(date)
}
