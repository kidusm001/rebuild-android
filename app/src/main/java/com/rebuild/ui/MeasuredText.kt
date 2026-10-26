package com.rebuild.ui

import com.rebuild.domain.Distance
import com.rebuild.domain.DistanceUnit
import com.rebuild.domain.Effort
import com.rebuild.domain.Exercise
import com.rebuild.domain.LoadUnit
import com.rebuild.domain.LoadingType
import com.rebuild.domain.Measure
import com.rebuild.domain.MeasuredGoal
import com.rebuild.domain.WorkoutExercise
import com.rebuild.domain.loggedMicrometres
import com.rebuild.domain.loggedSeconds
import java.text.DecimalFormatSymbols
import java.util.Locale

/** "0:45", "12:05" or "1:02:03": whole seconds, as stored and as the stopwatch shows them. */
internal fun formatDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val hours = s / 3600
    val minutes = s % 3600 / 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, s % 60) else "%d:%02d".format(minutes, s % 60)
}

internal fun formatDuration(seconds: Int): String = formatDuration(seconds.toLong())

/** "1 hour 5 minutes", "45 seconds": a duration as a screen reader should say it. */
internal fun spokenDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    if (s == 0L) return "0 seconds"
    return listOf(s / 3600 to "hour", s % 3600 / 60 to "minute", s % 60 to "second")
        .filter { it.first > 0 }
        .joinToString(" ") { (count, unit) -> "$count $unit${if (count == 1L) "" else "s"}" }
}

internal fun spokenDuration(seconds: Int): String = spokenDuration(seconds.toLong())

/** The phone's decimal separator when it is one the distance fields accept, else a point. */
private fun decimalSeparator(): Char =
    DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator.takeIf { it == '.' || it == ',' } ?: '.'

/**
 * Thousandths as a plain decimal, exactly: 2400 is "2.4", 40000 is "40", 1 is "0.001". Integer arithmetic only, so
 * nothing is rounded on the way to the screen.
 */
internal fun thousandthsText(thousandths: Long): String {
    val whole = thousandths / 1000
    val fraction = thousandths % 1000
    if (fraction == 0L) return whole.toString()
    return "$whole${decimalSeparator()}" + fraction.toString().padStart(3, '0').trimEnd('0')
}

/** "2.4 km", exactly as entered. */
internal fun formatDistance(distance: Distance): String = "${thousandthsText(distance.thousandths)} ${distance.unit.symbol}"

/** The text a distance field starts with when correcting [distance]: its number, in its own unit. */
internal fun distanceInput(distance: Distance): String = thousandthsText(distance.thousandths)

internal fun unitName(unit: DistanceUnit, plural: Boolean): String = when (unit) {
    DistanceUnit.M -> if (plural) "metres" else "metre"
    DistanceUnit.KM -> if (plural) "kilometres" else "kilometre"
    DistanceUnit.MI -> if (plural) "miles" else "mile"
}

/** "2.4 kilometres", "1 mile". */
internal fun spokenDistance(distance: Distance): String =
    "${thousandthsText(distance.thousandths)} ${unitName(distance.unit, distance.thousandths != 1000L)}"

/**
 * An exact length in micrometres shown in [unit], rounded down to the unit's thousandth so a total is never shown
 * as more than was covered: 4,999.9 m against a 5 km goal reads "4.999 km", not "5 km".
 */
internal fun formatMicrometres(micrometres: Long, unit: DistanceUnit): String =
    "${thousandthsText(micrometres.coerceAtLeast(0) / unit.micrometresPerThousandth)} ${unit.symbol}"

/** What one effort is called: "timed set", "distance set" or, for cardio, "effort". */
internal fun effortNoun(measure: Measure): String = when (measure) {
    Measure.REPS -> "set"
    Measure.TIMED -> "timed set"
    Measure.DISTANCE -> "distance set"
    Measure.CARDIO -> "effort"
}

/** A recorded load of a timed or distance effort; nothing for bodyweight or a load that wasn't entered. */
internal fun effortLoadText(effort: Effort, exercise: Exercise): String? = when {
    effort.loadUnit == LoadUnit.PLATES -> effort.plates?.let { "$it plates/stack" }
    exercise.loading == LoadingType.BODYWEIGHT -> null
    exercise.loading == LoadingType.ASSISTANCE -> massText(effort.amount, effort.loadUnit, exercise)
    else -> effort.amount?.let { massText(it, effort.loadUnit, exercise) }
}

/** "0:45 · 10 kg", "40 m · 0:52 · 24 kg/DB", "25:00 · 5 km": the measurements taken, nothing else. */
internal fun effortText(effort: Effort, exercise: Exercise): String {
    val time = effort.durationSeconds?.let { formatDuration(it) }
    val distance = effort.distance?.let(::formatDistance)
    val parts = if (exercise.measure == Measure.DISTANCE) listOf(distance, time) else listOf(time, distance)
    return (parts + effortLoadText(effort, exercise)).filterNotNull().joinToString(" · ")
}

internal fun spokenEffort(effort: Effort, exercise: Exercise): String {
    val time = effort.durationSeconds?.let { spokenDuration(it) }
    val distance = effort.distance?.let(::spokenDistance)
    val parts = if (exercise.measure == Measure.DISTANCE) listOf(distance, time) else listOf(time, distance)
    return (parts + effortLoadText(effort, exercise)).filterNotNull().joinToString(", ")
}

/**
 * A measured goal in words: "3 timed sets of at least 0:30", "2 distance sets of at least 40 m" or "at least 20:00
 * and 5 km in total". Null for a rep-based exercise or without a goal.
 */
internal fun measuredGoalText(measure: Measure, sets: Int?, goal: MeasuredGoal, spoken: Boolean = false): String? {
    val time: (Int) -> String = if (spoken) { s -> spokenDuration(s) } else { s -> formatDuration(s) }
    val length: (Distance) -> String = if (spoken) ::spokenDistance else ::formatDistance
    return when (measure) {
        Measure.REPS -> null
        Measure.TIMED, Measure.DISTANCE -> sets?.let { count ->
            val minimum = if (measure == Measure.TIMED) goal.effortSeconds?.let(time) else goal.effortDistance?.let(length)
            countLabel(count, effortNoun(measure)) + (minimum?.let { " of at least $it" } ?: "")
        }
        Measure.CARDIO -> listOfNotNull(goal.totalSeconds?.let(time), goal.totalDistance?.let(length))
            .takeIf { it.isNotEmpty() }?.let { "at least ${it.joinToString(" and ")} in total" }
    }
}

/** The rest part of a target line. Cardio never rests by itself; its rest only runs when the user starts it. */
internal fun restText(measure: Measure, restSeconds: Int): String =
    if (measure == Measure.CARDIO) "rest ${formatRest(restSeconds)} if you start it" else "rest ${formatRest(restSeconds)}"

/**
 * A plan or routine target in one line: "3 sets × 8–12 per leg · rest 1:30" for reps, "3 timed sets of at least 0:30 ·
 * rest 1:00", or "At least 20:00 in total · rest 1:30 if you start it" for cardio.
 */
internal fun targetsText(
    measure: Measure,
    sets: Int?,
    minReps: Int?,
    maxReps: Int?,
    perLeg: Boolean,
    restSeconds: Int,
    goal: MeasuredGoal = MeasuredGoal()
): String {
    val target = when (measure) {
        Measure.REPS -> listOfNotNull(
            sets?.let { countLabel(it, "set") },
            if (minReps != null && maxReps != null) "$minReps–$maxReps" + (if (perLeg) " per leg" else "") else null
        ).joinToString(" × ").ifEmpty { "No target" }
        else -> measuredGoalText(measure, sets, goal)?.replaceFirstChar { it.uppercase() } ?: "No goal"
    }
    return "$target · ${restText(measure, restSeconds)}"
}

internal fun targetsText(sets: Int, minReps: Int, maxReps: Int, perLeg: Boolean, restSeconds: Int): String =
    targetsText(Measure.REPS, sets, minReps, maxReps, perLeg, restSeconds)

/** A target in a few characters for a list: "3 × 8–12", "3 × 0:30", "2 × 40 m" or "20:00 · 5 km". Null without one. */
internal fun shortTarget(measure: Measure, sets: Int?, minReps: Int?, maxReps: Int?, perLeg: Boolean,
    goal: MeasuredGoal): String? = when (measure) {
    Measure.REPS -> sets?.let { count ->
        if (minReps != null && maxReps != null) "$count × $minReps–$maxReps" + (if (perLeg) " per leg" else "")
        else countLabel(count, "set")
    }
    Measure.TIMED -> if (sets != null && goal.effortSeconds != null) "$sets × ${formatDuration(goal.effortSeconds)}" else null
    Measure.DISTANCE -> if (sets != null && goal.effortDistance != null) "$sets × ${formatDistance(goal.effortDistance)}" else null
    Measure.CARDIO -> listOfNotNull(goal.totalSeconds?.let { formatDuration(it) }, goal.totalDistance?.let(::formatDistance))
        .takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** [shortTarget] in words: "3 working sets of 8 to 12 reps", "3 timed sets of at least 30 seconds". */
internal fun spokenTarget(measure: Measure, sets: Int?, minReps: Int?, maxReps: Int?, perLeg: Boolean,
    goal: MeasuredGoal): String? = when (measure) {
    Measure.REPS -> sets?.let { count ->
        countLabel(count, "working set") + if (minReps != null && maxReps != null) {
            " of $minReps to $maxReps reps" + if (perLeg) " per leg" else ""
        } else ""
    }
    else -> measuredGoalText(measure, sets, goal, spoken = true)
}

/**
 * The cardio distance logged on [we]. Against a distance goal it is the exact total shown in the goal's unit (rounded
 * down, never up); otherwise each unit keeps its own exact total ("800 m + 2 km"). Null when no effort recorded one.
 */
internal fun loggedDistanceText(we: WorkoutExercise, inUnit: DistanceUnit? = we.totalDistance?.unit): String? {
    val distances = we.efforts.mapNotNull { it.distance }
    if (distances.isEmpty()) return null
    if (inUnit != null) return formatMicrometres(we.loggedMicrometres(), inUnit)
    val byUnit = distances.groupBy { it.unit }
    return DistanceUnit.entries.mapNotNull { unit ->
        byUnit[unit]?.let { list -> formatDistance(Distance(list.fold(0L) { s, d -> Math.addExact(s, d.thousandths) }, unit)) }
    }.joinToString(" + ")
}

/** [formatMicrometres] as a screen reader should say it: "4.999 kilometres". */
internal fun spokenMicrometres(micrometres: Long, unit: DistanceUnit): String {
    val thousandths = micrometres.coerceAtLeast(0) / unit.micrometresPerThousandth
    return "${thousandthsText(thousandths)} ${unitName(unit, thousandths != 1000L)}"
}

/** [loggedDistanceText] in words. */
internal fun spokenLoggedDistance(we: WorkoutExercise, inUnit: DistanceUnit? = we.totalDistance?.unit): String? {
    val distances = we.efforts.mapNotNull { it.distance }
    if (distances.isEmpty()) return inUnit?.let { spokenMicrometres(0, it) }
    if (inUnit != null) return spokenMicrometres(we.loggedMicrometres(), inUnit)
    val byUnit = distances.groupBy { it.unit }
    return DistanceUnit.entries.mapNotNull { unit ->
        byUnit[unit]?.let { list -> spokenDistance(Distance(list.fold(0L) { s, d -> Math.addExact(s, d.thousandths) }, unit)) }
    }.joinToString(" and ")
}

/** Whether [this] stopwatch belongs to [we] in [workout]. */
internal fun com.rebuild.domain.Stopwatch?.timesEntry(workout: com.rebuild.domain.Workout, we: WorkoutExercise): Boolean =
    this != null && workoutId == workout.id && exerciseId == we.id

/** The cardio time logged on [we]; efforts without a duration add nothing. */
internal fun loggedTimeText(we: WorkoutExercise): String = formatDuration(we.loggedSeconds())
