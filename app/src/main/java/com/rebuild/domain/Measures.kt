package com.rebuild.domain

import java.math.BigDecimal

/** The longest timed or cardio effort, and the largest time goal: 24 hours. */
const val MAX_EFFORT_SECONDS = 86_400
/** Timed, distance or cardio efforts on one exercise of a workout. */
const val MAX_EFFORTS = 10_000
/**
 * The largest stopwatch total taken as a measurement (about 31 years). A longer reading can't be real, so its time is
 * confirmed by the user, as after a restart.
 */
const val MAX_STOPWATCH_MS = 1_000_000_000_000L
/** The largest stopwatch step before its count starts again from 0; no real effort comes near it. */
const val MAX_STOPWATCH_STEP = 1_000_000_000

/**
 * The step after one more change: paused, resumed, stopped, or its running time found untrustworthy. Requests name
 * the step they were made for, so a late or repeated one is recognised and changes nothing.
 */
fun Stopwatch.nextStep(): Int = if (step >= MAX_STOPWATCH_STEP) 0 else step + 1

/**
 * The largest distance of one effort or goal: 1,000 km entered in metres or kilometres, or 1,000 mi. In micrometres
 * that is at most 1.609344e12, so even 10,000 efforts add up to about 1.6e16, far inside a Long.
 */
val DistanceUnit.maxThousandths: Long
    get() = when (this) {
        DistanceUnit.M -> 1_000_000_000L
        DistanceUnit.KM, DistanceUnit.MI -> 1_000_000L
    }

/** More than zero and within its unit's maximum; only such a distance is ever stored or compared. */
fun Distance.inRange(): Boolean = thousandths in 1..unit.maxThousandths

val Exercise.measured: Boolean get() = measure != Measure.REPS

/** Holds and carries can be loaded like a set (a weighted plank, a farmer's carry); cardio never records a load. */
val Measure.recordsLoad: Boolean get() = this == Measure.TIMED || this == Measure.DISTANCE

/** Timed and cardio efforts are about time; a carry may also note how long it took. */
val Measure.recordsDuration: Boolean get() = this != Measure.REPS

/** Carries are about distance; cardio may record one; a timed hold never does. */
val Measure.recordsDistance: Boolean get() = this == Measure.DISTANCE || this == Measure.CARDIO

/**
 * The measured part of a goal. Timed and distance sets need each counted effort to reach a minimum ([effortSeconds] or
 * [effortDistance]); cardio needs its efforts to add up to [totalSeconds], [totalDistance] or both.
 */
data class MeasuredGoal(
    val effortSeconds: Int? = null,
    val effortDistance: Distance? = null,
    val totalSeconds: Int? = null,
    val totalDistance: Distance? = null
) {
    val empty: Boolean get() = effortSeconds == null && effortDistance == null && totalSeconds == null && totalDistance == null
}

val WorkoutExercise.measuredGoal: MeasuredGoal get() = MeasuredGoal(effortSeconds, effortDistance, totalSeconds, totalDistance)
val PlanAddition.measuredGoal: MeasuredGoal get() = MeasuredGoal(effortSeconds, effortDistance, totalSeconds, totalDistance)
val RoutineExercise.measuredGoal: MeasuredGoal get() = MeasuredGoal(effortSeconds, effortDistance, totalSeconds, totalDistance)

fun WorkoutExercise.withGoal(goal: MeasuredGoal): WorkoutExercise = copy(effortSeconds = goal.effortSeconds,
    effortDistance = goal.effortDistance, totalSeconds = goal.totalSeconds, totalDistance = goal.totalDistance)

/** Whether [effort] reaches this entry's per-effort minimum. Distances compare exactly, never as rounded text. */
fun WorkoutExercise.qualifies(effort: Effort): Boolean = when (exercise.measure) {
    Measure.TIMED -> effortSeconds != null && effort.durationSeconds != null && effort.durationSeconds >= effortSeconds
    Measure.DISTANCE -> effortDistance != null && effort.distance != null &&
        effort.distance.micrometres >= effortDistance.micrometres
    Measure.REPS, Measure.CARDIO -> false
}

fun WorkoutExercise.qualifyingCount(): Int = efforts.count(::qualifies)

/** The recorded durations added up. An effort without a duration adds nothing; it is never counted as zero time. */
fun WorkoutExercise.loggedSeconds(): Long =
    efforts.fold(0L) { sum, effort -> effort.durationSeconds?.let { Math.addExact(sum, it.toLong()) } ?: sum }

/** The recorded distances added up exactly, in micrometres. An effort without a distance adds nothing. */
fun WorkoutExercise.loggedMicrometres(): Long =
    efforts.fold(0L) { sum, effort -> effort.distance?.let { Math.addExact(sum, it.micrometres) } ?: sum }

/** Whether the entry has a target or goal to reach at all. */
fun WorkoutExercise.hasGoal(): Boolean = when (exercise.measure) {
    Measure.CARDIO -> totalSeconds != null || totalDistance != null
    else -> targetSets != null
}

/**
 * The target or goal is reached by actual work: working sets for reps; that many efforts each at least the minimum
 * for timed and distance sets; for cardio, every total it has, by the efforts' recorded time and distance added up.
 */
fun WorkoutExercise.goalReached(): Boolean = when (exercise.measure) {
    Measure.REPS -> targetSets?.let { workingCount() >= it } ?: false
    Measure.TIMED, Measure.DISTANCE -> targetSets?.let { qualifyingCount() >= it } ?: false
    Measure.CARDIO -> hasGoal() && (totalSeconds?.let { loggedSeconds() >= it } ?: true) &&
        (totalDistance?.let { loggedMicrometres() >= it.micrometres } ?: true)
}

/** Real work performed: working sets (a finished group counts once) and every timed, distance or cardio effort. */
fun WorkoutExercise.performedCount(): Int = workingCount() + efforts.size

fun Workout.performedCount(): Int = exercises.sumOf { it.performedCount() }

/** Anything other than warm-ups was logged on this entry. */
fun WorkoutExercise.hasLoggedWork(): Boolean = sets.any { !it.warmup } || efforts.isNotEmpty()

/** Sets, stages and efforts the entry holds; removing it deletes exactly these. */
fun WorkoutExercise.recordCount(): Int = sets.size + efforts.size

/**
 * The latest finished entry of [exercise] with efforts loaded the same way, keeping only efforts in [loadUnit]. It is
 * only ever offered as values to review, never saved by itself.
 */
fun AppState.previousEfforts(exercise: Exercise, excludingWorkoutId: String, loadUnit: LoadUnit): WorkoutExercise? =
    workouts.asSequence()
        .filter { it.id != excludingWorkoutId && it.finishedAt != null }
        .sortedByDescending { it.startedAt }
        .flatMap { it.exercises.asSequence() }
        .firstOrNull {
            it.exercise.id == exercise.id && it.exercise.loading == exercise.loading &&
                it.exercise.perDumbbell == exercise.perDumbbell && it.efforts.any { e -> e.loadUnit == loadUnit }
        }?.let { it.copy(efforts = it.efforts.filter { e -> e.loadUnit == loadUnit }) }

/**
 * The effort whose load and distance unit are offered for the next one in [unit]: today's last, else last time's
 * effort in the same position, else its last. Durations and distances are measured or typed, never offered.
 */
fun nextEffortSuggestion(today: WorkoutExercise, previous: WorkoutExercise?, unit: LoadUnit): Pair<Effort, PrefillSource>? {
    today.efforts.lastOrNull { it.loadUnit == unit }?.let { return it to PrefillSource.THIS_WORKOUT }
    val earlier = previous?.efforts?.filter { it.loadUnit == unit }.orEmpty()
    return (earlier.getOrNull(today.efforts.size) ?: earlier.lastOrNull())?.let { it to PrefillSource.LAST_TIME }
}

/** What a monotonic clock reading says about a stopwatch's performed time. */
sealed interface StopwatchTime {
    /** Exactly [ms] performed: the recorded time plus any running time from a trusted clock. */
    data class Measured(val ms: Long) : StopwatchTime
    /** The reading was taken in this app process before the stopwatch last started: a late request. */
    data object Stale : StopwatchTime
    /**
     * The running time can't be trusted (the phone restarted, or the reading can't be tied to the boot it started in),
     * or its time already needs confirming. Nothing is estimated; the user enters the duration.
     */
    data object Untrusted : StopwatchTime
}

/**
 * Trust needs a positively known matching boot or the same app process; two unknown boots never match. Within one boot
 * the clock counts deep sleep and ignores wall-clock and time-zone changes. Paused time is never added.
 */
fun Stopwatch.timeAt(now: Monotonic): StopwatchTime {
    if (needsConfirmation) return StopwatchTime.Untrusted
    val since = runningSince ?: return StopwatchTime.Measured(elapsedMs)
    val sameProcess = !session.isNullOrEmpty() && session == now.session
    val sameBoot = boot != null && now.boot != null && boot == now.boot
    if (sameProcess && now.elapsedMs < since) return StopwatchTime.Stale
    if (!(sameProcess || sameBoot) || now.elapsedMs < since) return StopwatchTime.Untrusted
    val running = now.elapsedMs - since
    if (running > MAX_STOPWATCH_MS - elapsedMs) return StopwatchTime.Untrusted
    return StopwatchTime.Measured(elapsedMs + running)
}

/** Whole seconds of a measured time, rounded down: 29.999 s is 29 s, and under a second is 0, never a second. */
fun wholeSeconds(ms: Long): Long = ms.coerceAtLeast(0) / 1000

/** A typed distance, checked exactly; nothing is rounded or truncated. */
sealed interface DistanceEntry {
    data object Blank : DistanceEntry
    data class Valid(val distance: Distance) : DistanceEntry
    data object NotANumber : DistanceEntry
    /** More than three decimals; refused rather than cut off. */
    data object TooPrecise : DistanceEntry
    /** Zero, or above the unit's maximum. */
    data object OutOfRange : DistanceEntry
}

private val distanceText = Regex("[0-9]{1,10}([.,][0-9]{1,12})?")

/** Reads [text] (a "." or "," decimal) as a distance in [unit], with at most three decimals. */
fun parseDistance(text: String, unit: DistanceUnit): DistanceEntry {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return DistanceEntry.Blank
    if (!trimmed.matches(distanceText)) return DistanceEntry.NotANumber
    val value = BigDecimal(trimmed.replace(',', '.'))
    if (value.stripTrailingZeros().scale() > 3) return DistanceEntry.TooPrecise
    val thousandths = value.movePointRight(3).toBigIntegerExact()
    if (thousandths.signum() <= 0 || thousandths > BigDecimal.valueOf(unit.maxThousandths).toBigInteger()) {
        return DistanceEntry.OutOfRange
    }
    return DistanceEntry.Valid(Distance(thousandths.toLong(), unit))
}

/** A typed duration from minutes and seconds fields. */
sealed interface DurationEntry {
    data object Blank : DurationEntry
    data class Valid(val seconds: Int) : DurationEntry
    data object NotANumber : DurationEntry
    /** Seconds above 59. */
    data object BadSeconds : DurationEntry
    /** Zero, or more than 24 hours. */
    data object OutOfRange : DurationEntry
}

private val wholeText = Regex("[0-9]{1,7}")

/** Reads minutes and seconds fields; one of them may be blank. 0:00 and anything over 24 hours are refused. */
fun parseDuration(minutes: String, seconds: String): DurationEntry {
    val m = minutes.trim()
    val s = seconds.trim()
    if (m.isEmpty() && s.isEmpty()) return DurationEntry.Blank
    if ((m.isNotEmpty() && !m.matches(wholeText)) || (s.isNotEmpty() && !s.matches(wholeText))) {
        return DurationEntry.NotANumber
    }
    val secs = s.ifEmpty { "0" }.toLong()
    if (secs > 59) return DurationEntry.BadSeconds
    val total = m.ifEmpty { "0" }.toLong() * 60 + secs
    return if (total in 1..MAX_EFFORT_SECONDS) DurationEntry.Valid(total.toInt()) else DurationEntry.OutOfRange
}
