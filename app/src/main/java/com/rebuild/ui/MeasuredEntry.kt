package com.rebuild.ui

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.rebuild.R
import com.rebuild.domain.Action
import com.rebuild.domain.AppState
import com.rebuild.domain.Distance
import com.rebuild.domain.DistanceEntry
import com.rebuild.domain.DistanceUnit
import com.rebuild.domain.DurationEntry
import com.rebuild.domain.Effort
import com.rebuild.domain.Exercise
import com.rebuild.domain.LoadUnit
import com.rebuild.domain.LoadingType
import com.rebuild.domain.MAX_EFFORT_SECONDS
import com.rebuild.domain.Measure
import com.rebuild.domain.Monotonic
import com.rebuild.domain.PrefillSource
import com.rebuild.domain.Stopwatch
import com.rebuild.domain.StopwatchTime
import com.rebuild.domain.Workout
import com.rebuild.domain.WorkoutExercise
import com.rebuild.domain.measured
import com.rebuild.domain.nextEffortSuggestion
import com.rebuild.domain.openGroupExercise
import com.rebuild.domain.parseDistance
import com.rebuild.domain.parseDuration
import com.rebuild.domain.previousEfforts
import com.rebuild.domain.recordsDistance
import com.rebuild.domain.recordsLoad
import com.rebuild.domain.resumeExercise
import com.rebuild.domain.timeAt
import com.rebuild.domain.warmupSetCount
import com.rebuild.domain.wholeSeconds
import com.rebuild.domain.workingSetCount
import kotlinx.coroutines.delay

internal enum class EffortField { DURATION, DISTANCE, LOAD }

internal sealed interface EffortValidation {
    data class Valid(val durationSeconds: Int?, val distance: Distance?, val kg: Double?, val plates: Int?) :
        EffortValidation
    data class Invalid(val field: EffortField, val message: String) : EffortValidation
}

/**
 * Checks a timed, distance or cardio effort's fields in the order they are shown. A hold needs its time; a carry its
 * distance (its time is optional); cardio a time, a distance or both. Nothing missing is ever taken as zero, and the
 * load follows a set's rules: plates are whole plates per stack, kg is required for external weight and assistance.
 */
internal fun validateEffort(
    exercise: Exercise,
    loadUnit: LoadUnit,
    minutes: String,
    seconds: String,
    distanceText: String,
    distanceUnit: DistanceUnit,
    loadText: String
): EffortValidation {
    val measure = exercise.measure
    val time = parseDuration(minutes, seconds)
    val length = if (measure.recordsDistance) parseDistance(distanceText, distanceUnit) else DistanceEntry.Blank
    fun invalid(field: EffortField, message: String) = EffortValidation.Invalid(field, message)
    when (measure) {
        Measure.REPS -> return invalid(EffortField.DURATION, "${exercise.name} is logged in sets of reps.")
        Measure.TIMED -> {
            if (time == DurationEntry.Blank) return invalid(EffortField.DURATION, "Enter the time: minutes, seconds or both.")
            durationError(time)?.let { return invalid(EffortField.DURATION, it) }
        }
        Measure.DISTANCE -> {
            if (length == DistanceEntry.Blank) return invalid(EffortField.DISTANCE, "Enter the distance.")
            distanceError(length, distanceUnit)?.let { return invalid(EffortField.DISTANCE, it) }
            durationError(time)?.let { return invalid(EffortField.DURATION, it) }
        }
        Measure.CARDIO -> {
            durationError(time)?.let { return invalid(EffortField.DURATION, it) }
            distanceError(length, distanceUnit)?.let { return invalid(EffortField.DISTANCE, it) }
            if (time == DurationEntry.Blank && length == DistanceEntry.Blank) {
                return invalid(EffortField.DURATION, "Enter a time, a distance or both.")
            }
        }
    }
    val durationSeconds = (time as? DurationEntry.Valid)?.seconds
    val distance = (length as? DistanceEntry.Valid)?.distance
    if (!measure.recordsLoad) return EffortValidation.Valid(durationSeconds, distance, null, null)
    if (loadUnit == LoadUnit.PLATES) {
        val plates = loadText.trim().toIntOrNull()
        if (!exercise.supportsPlates || plates == null || plates !in 1..1000) {
            return invalid(EffortField.LOAD, "Enter whole plates per stack, from 1 to 1000, for a cable or stack machine.")
        }
        return EffortValidation.Valid(durationSeconds, distance, null, plates)
    }
    if (exercise.loading == LoadingType.BODYWEIGHT) return EffortValidation.Valid(durationSeconds, distance, null, null)
    val kg = parseDecimal(loadText)
    if (loadText.isNotBlank() && kg == null) return invalid(EffortField.LOAD, "Weight isn't a number.")
    val symbol = loadUnit.massSymbol()
    if (kg == null && exercise.loading == LoadingType.EXTERNAL) {
        return invalid(EffortField.LOAD, "Enter the weight in $symbol${if (exercise.perDumbbell) " per dumbbell" else ""}.")
    }
    if (kg == null) return invalid(EffortField.LOAD, "Enter the assistance in $symbol (0 if none).")
    val max = if (loadUnit == LoadUnit.LB) MAX_LB else MAX_KG
    if (!kg.isFinite() || kg < 0 || kg > max) {
        return invalid(EffortField.LOAD, "Weight must be between 0 and ${"%,d".format(max.toInt())} $symbol.")
    }
    return EffortValidation.Valid(durationSeconds, distance, kg, null)
}

/** [EffortForm.typedFor] when no time was typed over a stopped stopwatch's reading. */
internal const val NOT_TYPED = -1L
/** [EffortForm.typedFor] when the time was typed for a stopwatch whose time couldn't be measured. */
internal const val CONFIRMING = -2L

/**
 * One effort's fields as typed. They stay text until logged, so nothing is rounded or converted on the way. A stopped
 * stopwatch shows its measured time in the minutes and seconds until the user types over it; [typedFor] says which
 * reading the typed time replaces, so resuming and stopping again shows the new measurement.
 */
@Stable
internal class EffortForm(
    minutes: String,
    seconds: String,
    distance: String,
    unit: DistanceUnit,
    load: String,
    effortId: String,
    typedFor: Long
) {
    var minutes by mutableStateOf(minutes)
    var seconds by mutableStateOf(seconds)
    var distance by mutableStateOf(distance)
    var unit by mutableStateOf(unit)
    var load by mutableStateOf(load)
    /** Stable until this effort is logged, so a retry after an error can't log it twice. */
    var effortId by mutableStateOf(effortId)
    var typedFor by mutableLongStateOf(typedFor)
    var error by mutableStateOf<String?>(null)
    var invalid by mutableStateOf<EffortValidation.Invalid?>(null)
    var attempt by mutableIntStateOf(0)

    /**
     * The stopwatch's own fields start with whatever this entry's distance and load hold, typed or offered; its time
     * is measured.
     */
    fun forWatch(): EffortForm = EffortForm("", "", distance, unit, load, newId(), NOT_TYPED)
}

/**
 * Unsaved effort fields of the active workout's timed, distance and cardio exercises, and of the stopwatch's effort.
 * They live above every screen, so leaving the workout, rotating or the app being recreated never loses them. Each
 * key folds in what the fields were offered from, so a draft is never restored over newer saved efforts.
 */
internal class EffortDrafts {
    private val forms = LinkedHashMap<String, EffortForm>()

    fun form(key: String, init: () -> EffortForm): EffortForm = forms.getOrPut(key, init)

    /** Drops every draft that no longer belongs to the active workout's current state. */
    fun prune(state: AppState) {
        val workout = state.activeWorkout
        val keep = mutableSetOf<String>()
        workout?.exercises?.filter { it.exercise.measured }?.forEach { we ->
            keep += effortFormKey(we, effortSuggestion(state, workout, we))
            state.stopwatch?.takeIf { it.timesEntry(workout, we) }?.let { keep += watchFormKey(it, we) }
        }
        forms.keys.retainAll(keep)
    }

    companion object {
        private const val FIELDS = 8

        val Saver = listSaver<EffortDrafts, String>(
            save = { drafts ->
                drafts.forms.flatMap { (key, form) ->
                    listOf(key, form.minutes, form.seconds, form.distance, form.unit.name, form.load, form.effortId,
                        form.typedFor.toString())
                }
            },
            restore = { saved ->
                EffortDrafts().apply {
                    saved.chunked(FIELDS).filter { it.size == FIELDS }.forEach { field ->
                        forms[field[0]] = EffortForm(field[1], field[2], field[3],
                            DistanceUnit.entries.find { it.name == field[4] } ?: DistanceUnit.M, field[5],
                            field[6].ifEmpty { newId() }, field[7].toLongOrNull() ?: NOT_TYPED)
                    }
                }
            }
        )
    }
}

/** The unit an entry's efforts are logged in: its load unit for holds and carries; cardio has no load. */
internal fun effortLoadUnit(we: WorkoutExercise): LoadUnit =
    if (we.exercise.measure.recordsLoad) we.entryLoadUnit else LoadUnit.KG

/** The effort whose load and distance unit are offered for the next one, from today or last time. */
internal fun effortSuggestion(state: AppState, workout: Workout, we: WorkoutExercise): Pair<Effort, PrefillSource>? {
    val unit = effortLoadUnit(we)
    return nextEffortSuggestion(we, state.previousEfforts(we.exercise, workout.id, unit), unit)
}

private fun Effort.signature(): String =
    listOf(id, durationSeconds, distance?.thousandths, distance?.unit, kg, plates, loadUnit).joinToString(",")

/** Changes whenever an effort is logged, removed or corrected in a way that changes what is offered next. */
internal fun effortFormKey(we: WorkoutExercise, suggestion: Pair<Effort, PrefillSource>?): String = listOf(
    "entry", we.id, we.exercise.id, we.exercise.loading, we.exercise.perDumbbell, we.exercise.measure,
    we.entryLoadUnit, we.efforts.size, we.efforts.lastOrNull()?.signature(), suggestion?.first?.signature(),
    suggestion?.second
).joinToString("|")

internal fun watchFormKey(watch: Stopwatch, we: WorkoutExercise): String = "watch|${watch.id}|${we.entryLoadUnit}"

private fun loadInput(effort: Effort): String = when (effort.loadUnit) {
    LoadUnit.PLATES -> effort.plates?.toString().orEmpty()
    LoadUnit.KG, LoadUnit.LB -> effort.amount?.let(::formatKg).orEmpty()
}

/**
 * What the fields offer from [suggestion]: a load, and the distance unit last used (today's last distance first, so a
 * time-only cardio effort in between never switches it back). A time or distance is measured or typed, never offered.
 */
private class Offer(val unit: DistanceUnit, val load: String)

private fun offer(we: WorkoutExercise, suggestion: Pair<Effort, PrefillSource>?): Offer {
    val measure = we.exercise.measure
    val offered = suggestion?.first
    val unit = we.efforts.lastOrNull { it.distance != null }?.distance?.unit ?: offered?.distance?.unit
        ?: (we.effortDistance ?: we.totalDistance)?.unit ?: defaultDistanceUnit(measure)
    return Offer(
        unit = unit,
        load = if (measure.recordsLoad && we.exercise.loading != LoadingType.BODYWEIGHT) {
            offered?.let(::loadInput).orEmpty()
        } else ""
    )
}

internal fun freshEffortForm(we: WorkoutExercise, suggestion: Pair<Effort, PrefillSource>?): EffortForm {
    val offer = offer(we, suggestion)
    return EffortForm("", "", "", offer.unit, offer.load, newId(), NOT_TYPED)
}

/** "load from last time" while the load still holds exactly what was offered; null once typed over. */
private fun offerText(form: EffortForm, we: WorkoutExercise, suggestion: Pair<Effort, PrefillSource>?): String? {
    suggestion ?: return null
    val offer = offer(we, suggestion)
    if (offer.load.isEmpty() || form.load != offer.load) return null
    return "load from " + if (suggestion.second == PrefillSource.LAST_TIME) "last time" else "this workout"
}

/** Whether [id] is taken: by a logged set or effort anywhere, or by the stopwatch's effort. */
internal fun AppState.idUsed(id: String): Boolean = stopwatch?.id == id ||
    workouts.any { w -> w.exercises.any { e -> e.sets.any { it.id == id } || e.efforts.any { it.id == id } } }

/** Where the workout opens: a set in progress, else the stopwatch's exercise, else the first one short of its target. */
internal fun opensAt(state: AppState, workout: Workout): WorkoutExercise? =
    workout.openGroupExercise()
        ?: state.stopwatch?.takeIf { it.workoutId == workout.id }
            ?.let { watch -> workout.exercises.find { it.id == watch.exerciseId } }
        ?: workout.resumeExercise()

private fun measuredNoun(measure: Measure): String = when (measure) {
    Measure.TIMED -> "timed set"
    Measure.DISTANCE -> "distance set"
    Measure.CARDIO -> "cardio effort"
    Measure.REPS -> "set"
}

/** "2 timed sets", "1 cardio effort": each kind of measured work in [workout] counted on its own, never added together. */
internal fun effortCounts(workout: Workout): List<String> =
    listOf(Measure.TIMED, Measure.DISTANCE, Measure.CARDIO).mapNotNull { measure ->
        workout.exercises.filter { it.exercise.measure == measure }.sumOf { it.efforts.size }
            .takeIf { it > 0 }?.let { countLabel(it, measuredNoun(measure)) }
    }

/**
 * What a workout holds so far: "4 working sets, 1 warm-up" as before when it has no timed, distance or cardio exercise;
 * otherwise each kind of work counted on its own, never added together.
 */
internal fun workoutLoggedText(workout: Workout): String {
    val working = workout.workingSetCount()
    val warmups = workout.warmupSetCount().takeIf { it > 0 }?.let { "$it warm-up" }
    if (workout.exercises.none { it.exercise.measured }) {
        return countLabel(working, "working set") + (warmups?.let { ", $it" } ?: "")
    }
    return (listOfNotNull(working.takeIf { it > 0 }?.let { countLabel(it, "working set") }, warmups) + effortCounts(workout))
        .ifEmpty { listOf("nothing logged yet") }.joinToString(", ")
}

/** The stopwatch's state in a few words, for lines that don't tick. */
internal fun stopwatchStatus(watch: Stopwatch): String = when {
    watch.running -> "timing"
    watch.needsConfirmation -> "time to enter"
    watch.stopped -> "stopped at ${formatDuration(wholeSeconds(watch.elapsedMs))}, not logged"
    else -> "paused at ${formatDuration(wholeSeconds(watch.elapsedMs))}"
}

/** The stopwatch's reading, refreshed on each whole second while it runs; nothing here changes what is saved. */
@Composable
internal fun rememberStopwatchReading(env: AppEnv, watch: Stopwatch): StopwatchTime {
    var reading by remember(watch) { mutableStateOf(watch.timeAt(env.monotonic())) }
    if (watch.running) LaunchedEffect(watch) {
        while (true) {
            val now = watch.timeAt(env.monotonic())
            reading = now
            val ms = (now as? StopwatchTime.Measured)?.ms
            delay(if (ms != null) 1000 - ms % 1000 else 1000)
        }
    }
    return reading
}

/** Whole seconds to show: measured time, or the time kept before a late reading. Null when it can't be confirmed. */
private fun shownSeconds(watch: Stopwatch, reading: StopwatchTime): Long? = when (reading) {
    is StopwatchTime.Measured -> wholeSeconds(reading.ms)
    StopwatchTime.Stale -> wholeSeconds(watch.elapsedMs)
    StopwatchTime.Untrusted -> null
}

@Composable
private fun StopwatchTimeText(env: AppEnv, watch: Stopwatch, large: Boolean, modifier: Modifier = Modifier) {
    val seconds = shownSeconds(watch, rememberStopwatchReading(env, watch))
    val status = when {
        watch.running -> "timing"
        watch.stopped -> "stopped"
        else -> "paused"
    }
    val style = if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleMedium
    Text(
        seconds?.let { formatDuration(it) } ?: "Checking time…",
        style = (if (seconds == null) MaterialTheme.typography.bodyLarge else style).copy(fontFeatureSettings = "tnum"),
        fontWeight = if (large) FontWeight.Normal else FontWeight.SemiBold,
        modifier = modifier.testTag("stopwatch-time").clearAndSetSemantics {
            contentDescription = if (seconds == null) "Stopwatch time is being checked"
            else "Stopwatch ${spokenDuration(seconds)}, $status"
        }
    )
}

private enum class WatchPhase { NONE, RUNNING, PAUSED, REVIEW }

/**
 * Entry for a timed, distance or cardio exercise: type the measurements and Log, or time the effort with the
 * stopwatch. Stopping only fills the fields for review; nothing is logged, and no rest starts, until Log. Holds and
 * carries rest after Log; cardio never rests by itself, so a rest for intervals is started here when wanted.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun MeasuredEntryPanel(
    env: AppEnv,
    workout: Workout,
    we: WorkoutExercise,
    extra: Boolean,
    onCancelExtra: () -> Unit,
    onGoTo: (String) -> Unit,
    viewportHeight: Int,
    onFocusChange: (Boolean) -> Unit
) {
    val exercise = we.exercise
    val measure = exercise.measure
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val large = LocalDensity.current.fontScale > 1.3f
    val busy = env.runner.busy
    val watch = env.state.stopwatch
    val ownWatch = watch?.takeIf { it.timesEntry(workout, we) }
    val otherWatch = watch?.takeIf { ownWatch == null }
    val loadUnit = effortLoadUnit(we)
    val suggestion = effortSuggestion(env.state, workout, we)
    val entryForm = env.effortDrafts.form(effortFormKey(we, suggestion)) { freshEffortForm(we, suggestion) }
    val form = ownWatch?.let { env.effortDrafts.form(watchFormKey(it, we)) { entryForm.forWatch() } } ?: entryForm
    val phase = when {
        ownWatch == null -> WatchPhase.NONE
        ownWatch.stopped -> WatchPhase.REVIEW
        ownWatch.running -> WatchPhase.RUNNING
        else -> WatchPhase.PAUSED
    }
    val showFields = phase == WatchPhase.NONE || phase == WatchPhase.REVIEW
    // A stopped stopwatch's time fills the fields until the user types over it; a time that couldn't be measured, or
    // ran past what one effort can record, is left blank for the user to enter.
    val reviewKey = ownWatch?.takeIf { phase == WatchPhase.REVIEW }?.let { if (reviewSeconds(it) == null) CONFIRMING else it.elapsedMs }
    val typed = reviewKey == null || form.typedFor == reviewKey
    val measuredSeconds = ownWatch?.takeIf { phase == WatchPhase.REVIEW }?.let(::reviewSeconds)
    val minutes = if (typed) form.minutes else measuredSeconds?.let { (it / 60).toString() }.orEmpty()
    val seconds = if (typed) form.seconds else measuredSeconds?.let { (it % 60).toString() }.orEmpty()
    fun typeTime(newMinutes: String? = null, newSeconds: String? = null) {
        if (!typed && reviewKey != null) {
            form.minutes = minutes
            form.seconds = seconds
            form.typedFor = reviewKey
        }
        newMinutes?.let { form.minutes = it }
        newSeconds?.let { form.seconds = it }
    }
    val typedOver = reviewKey != null && typed && measuredSeconds != null &&
        (parseDuration(form.minutes, form.seconds) as? DurationEntry.Valid)?.seconds?.toLong() != measuredSeconds

    var entryFocused by remember { mutableStateOf(false) }
    // The step the discard confirmation was opened for: confirming discards that state, never a newer one.
    var discardingStep by rememberSaveable(ownWatch?.id) { mutableStateOf<Int?>(null) }
    var discardError by rememberSaveable(ownWatch?.id) { mutableStateOf<String?>(null) }
    val entryBounds = remember { BringIntoViewRequester() }
    LaunchedEffect(entryFocused, viewportHeight) {
        if (entryFocused && viewportHeight > 0 && form.invalid == null) entryBounds.bringIntoView()
    }
    DisposableEffect(Unit) { onDispose { onFocusChange(false) } }

    fun log() {
        if (env.runner.busy) return
        val valid = when (val parsed = validateEffort(exercise, loadUnit, minutes, seconds, form.distance, form.unit,
            form.load)) {
            is EffortValidation.Invalid -> {
                form.invalid = parsed
                form.attempt++
                return
            }
            is EffortValidation.Valid -> parsed
        }
        form.invalid = null
        form.error = null
        // The stopwatch's effort keeps its own id; a typed one never reuses an id that is taken, the stopwatch's included.
        val effortId = ownWatch?.id ?: run {
            if (env.state.idUsed(form.effortId)) form.effortId = newId()
            form.effortId
        }
        env.runner.run(
            Action.SaveEffort(workout.id, we.id, effortId, valid.durationSeconds, valid.distance, valid.kg, env.clock(),
                loadUnit, valid.plates, watchStep = ownWatch?.step),
            onError = { form.error = it },
            cooldownMs = ActionRunner.NEW_SET_COOLDOWN_MS
        )
    }

    fun start() {
        if (env.runner.busy) return
        entryForm.error = null
        if (env.state.idUsed(entryForm.effortId)) entryForm.effortId = newId()
        val id = entryForm.effortId
        env.runner.run(Action.StartStopwatch(id, workout.id, we.id, env.monotonic(), env.clock()),
            onError = { entryForm.error = it }) {
            // This id now belongs to the stopwatch's effort; a typed effort gets its own.
            if (entryForm.effortId == id) entryForm.effortId = newId()
        }
    }

    fun control(action: (Monotonic) -> Action) {
        form.error = null
        env.runner.run(action(env.monotonic()), onError = { form.error = it })
    }

    val noun = effortNoun(measure)
    val title = when {
        phase == WatchPhase.NONE && extra && measure == Measure.CARDIO -> "Another effort"
        phase == WatchPhase.NONE && extra -> "Extra $noun"
        else -> "${noun.replaceFirstChar { it.uppercase() }} ${we.efforts.size + 1}"
    }
    val detail = when (phase) {
        WatchPhase.RUNNING -> "stopwatch timing"
        WatchPhase.PAUSED -> "stopwatch paused"
        WatchPhase.REVIEW -> if (ownWatch?.let(::reviewSeconds) == null) "enter its time" else "stopped"
        WatchPhase.NONE -> listOfNotNull(
            when (measure) {
                Measure.TIMED -> we.effortSeconds?.let { "counts at ${formatDuration(it)} or more" }
                Measure.DISTANCE -> we.effortDistance?.let { "counts at ${formatDistance(it)} or more" }
                Measure.CARDIO -> "time, distance or both"
                Measure.REPS -> null
            },
            if (extra) (if (we.optional) "beyond your optional goal" else "beyond today's target") else null,
            offerText(form, we, suggestion)
        ).joinToString(" · ")
    }

    @Composable fun Context() {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) {
                        append(if (landscape) "${exercise.name} · $title" else title)
                    }
                    if (detail.isNotEmpty()) withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        append(" · $detail")
                    }
                },
                style = if (landscape) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).testTag("entry-context")
            )
            if (extra && phase == WatchPhase.NONE) {
                TextButton(onClick = onCancelExtra, modifier = Modifier.heightIn(min = 48.dp).testTag("cancel-extra")) {
                    Text("Cancel")
                }
            }
        }
    }

    @Composable fun TimeFields(modifier: Modifier, last: Boolean) {
        EffortNumberField(minutes, { typeTime(newMinutes = it) }, "Minutes", EffortField.DURATION, form.invalid,
            form.attempt, form, modifier.testTag("effort-minutes"), decimal = false)
        EffortNumberField(seconds, { typeTime(newSeconds = it) }, "Seconds", EffortField.DURATION, form.invalid,
            form.attempt, form, modifier.testTag("effort-seconds"), decimal = false, focusOnError = false,
            imeAction = if (last) ImeAction.Done else ImeAction.Next, onDone = ::log)
    }

    @Composable fun DistanceInput(modifier: Modifier, last: Boolean) {
        EffortNumberField(form.distance, { form.distance = it }, "Distance (${form.unit.symbol})", EffortField.DISTANCE,
            form.invalid, form.attempt, form, modifier.testTag("effort-distance"), decimal = true,
            imeAction = if (last) ImeAction.Done else ImeAction.Next, onDone = ::log)
    }

    val loadShown = measure.recordsLoad && exercise.loading != LoadingType.BODYWEIGHT
    @Composable fun LoadInput(modifier: Modifier) {
        val plates = loadUnit == LoadUnit.PLATES
        EffortNumberField(form.load, { form.load = it }, effortLoadLabel(exercise, loadUnit), EffortField.LOAD,
            form.invalid, form.attempt, form, modifier.testTag(if (plates) "effort-plates" else "effort-kg"),
            decimal = !plates, filterNumericInput = !plates, imeAction = ImeAction.Done, onDone = ::log)
    }

    @Composable fun UnitChoice() {
        DistanceUnitChoice(form.unit, { form.unit = it }, enabled = !busy)
    }

    @Composable fun Fields() {
        // Shown in the order they are checked: what the effort is measured by first.
        when {
            large -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (measure == Measure.DISTANCE) {
                    DistanceInput(Modifier.fillMaxWidth(), last = false)
                    UnitChoice()
                }
                TimeFields(Modifier.fillMaxWidth(), last = measure == Measure.TIMED && !loadShown)
                if (measure == Measure.CARDIO) {
                    DistanceInput(Modifier.fillMaxWidth(), last = true)
                    UnitChoice()
                }
                if (loadShown) LoadInput(Modifier.fillMaxWidth())
            }
            landscape -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // The unit sits right after its distance, as it does upright: a phone on its side can still choose it.
                if (measure == Measure.DISTANCE) {
                    DistanceInput(Modifier.weight(1.2f), last = false)
                    UnitChoice()
                }
                TimeFields(Modifier.weight(1f), last = measure == Measure.TIMED && !loadShown)
                if (measure == Measure.CARDIO) {
                    DistanceInput(Modifier.weight(1.2f), last = true)
                    UnitChoice()
                }
                if (loadShown) LoadInput(Modifier.weight(1f))
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (measure == Measure.DISTANCE) Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    DistanceInput(Modifier.weight(1f), last = false)
                    UnitChoice()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeFields(Modifier.weight(1f), last = measure == Measure.TIMED && !loadShown)
                    if (loadShown) LoadInput(Modifier.weight(1f))
                }
                if (measure == Measure.CARDIO) Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    DistanceInput(Modifier.weight(1f), last = true)
                    UnitChoice()
                }
            }
        }
        // The failing field is focused and carries the error itself, so this visible copy is not a live region.
        form.invalid?.let {
            Text(it.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }

    @Composable fun IconLabel(icon: Int, label: String) {
        RebuildIcon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }

    @Composable fun Primary(modifier: Modifier) {
        val buttonModifier = modifier.heightIn(min = 56.dp)
        when (phase) {
            WatchPhase.RUNNING, WatchPhase.PAUSED -> Button(
                onClick = { ownWatch?.let { w -> control { Action.StopStopwatch(w.id, w.step, it) } } },
                enabled = !busy,
                modifier = buttonModifier.testTag("stop-stopwatch")
            ) { IconLabel(R.drawable.ic_square, "Stop") }
            else -> {
                val label = if (env.runner.saving) "Saving…" else "Log $noun"
                // An extra effort stays available but is never styled as the expected next step.
                if (extra && phase == WatchPhase.NONE) {
                    OutlinedButton(onClick = ::log, enabled = !busy, modifier = buttonModifier.testTag("log-effort")) {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Button(onClick = ::log, enabled = !busy, modifier = buttonModifier.testTag("log-effort")) {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    // The stopwatch control beside the primary action, if any.
    val secondary: (@Composable (Modifier) -> Unit)? = when (phase) {
        WatchPhase.NONE -> if (otherWatch == null) { modifier ->
            OutlinedButton(onClick = ::start, enabled = !busy,
                modifier = modifier.heightIn(min = 56.dp).testTag("start-stopwatch")) {
                IconLabel(R.drawable.ic_rest, if (large || landscape) "Start stopwatch" else "Stopwatch")
            }
        } else null
        WatchPhase.RUNNING -> { modifier ->
            OutlinedButton(onClick = { ownWatch?.let { w -> control { Action.PauseStopwatch(w.id, w.step, it) } } },
                enabled = !busy, modifier = modifier.heightIn(min = 56.dp).testTag("pause-stopwatch")) {
                IconLabel(R.drawable.ic_pause, "Pause")
            }
        }
        WatchPhase.PAUSED -> { modifier ->
            OutlinedButton(onClick = { ownWatch?.let { w -> control { Action.ResumeStopwatch(w.id, w.step, it) } } },
                enabled = !busy, modifier = modifier.heightIn(min = 56.dp).testTag("resume-stopwatch")) {
                IconLabel(R.drawable.ic_play, "Resume")
            }
        }
        // Resuming only makes sense for a time that can still be logged.
        WatchPhase.REVIEW -> {
            val reviewed = ownWatch?.takeIf { reviewSeconds(it) != null }
            if (reviewed != null) { modifier ->
                OutlinedButton(onClick = { control { Action.ResumeStopwatch(reviewed.id, reviewed.step, it) } },
                    enabled = !busy, modifier = modifier.heightIn(min = 56.dp).testTag("resume-stopwatch")) {
                    IconLabel(R.drawable.ic_play, "Resume")
                }
            } else null
        }
    }

    val canRest = measure == Measure.CARDIO && phase == WatchPhase.NONE && we.efforts.isNotEmpty() &&
        env.state.restTimer == null && watch?.running != true

    @Composable fun Discard() {
        SecondaryTextButton(onClick = { discardError = null; discardingStep = ownWatch?.step }, enabled = !busy,
            icon = R.drawable.ic_x, modifier = Modifier.testTag("discard-stopwatch")) {
            Text("Discard $noun")
        }
    }

    @Composable fun CardioRest() {
        SecondaryTextButton(
            onClick = {
                entryForm.error = null
                env.runner.run(Action.StartRest(newId(), workout.id, we.id, env.clock()),
                    onError = { entryForm.error = it })
            },
            enabled = !busy,
            icon = R.drawable.ic_rest,
            modifier = Modifier.testTag("cardio-rest")
        ) { Text("Start rest ${formatRest(we.restSeconds)}") }
    }

    @Composable fun UseMeasured() {
        SecondaryTextButton(onClick = { form.typedFor = NOT_TYPED; form.invalid = null }, enabled = !busy,
            icon = R.drawable.ic_undo_2, modifier = Modifier.testTag("use-measured-time")) {
            Text("Use the measured ${formatDuration(measuredSeconds ?: 0)}")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!landscape) Context()
        when (phase) {
            WatchPhase.RUNNING, WatchPhase.PAUSED -> ownWatch?.let {
                StopwatchTimeText(env, it, large = !landscape, modifier = Modifier.fillMaxWidth())
            }
            WatchPhase.REVIEW -> ownWatch?.let { ReviewNotice(it, measuredSeconds, typedOver) }
            WatchPhase.NONE -> otherWatch?.let { other ->
                val name = workout.exercises.find { it.id == other.exerciseId }?.exercise?.name
                Text(
                    "The stopwatch has an effort on ${name ?: "another exercise"} that isn't logged " +
                        "(${stopwatchStatus(other)}). Log or discard it there to time this one; you can still type " +
                        "this one's measurements.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (name != null && other.workoutId == workout.id) {
                    SecondaryTextButton(onClick = { onGoTo(other.exerciseId) }, icon = R.drawable.ic_arrow_right,
                        modifier = Modifier.testTag("go-to-stopwatch-entry")) { Text("Go to $name") }
                }
            }
        }
        // Kept in view while typing: the fields and the actions. Everything after it is a scroll away.
        Column(
            Modifier
                .onFocusChanged {
                    entryFocused = it.hasFocus
                    onFocusChange(it.hasFocus)
                }
                .focusGroup()
                .bringIntoViewRequester(entryBounds),
            verticalArrangement = Arrangement.spacedBy(if (landscape) 4.dp else 8.dp)
        ) {
            if (landscape) Context()
            if (showFields) Fields()
            when {
                large -> {
                    Primary(Modifier.fillMaxWidth())
                    secondary?.invoke(Modifier.fillMaxWidth())
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    secondary?.invoke(Modifier)
                    Primary(Modifier.weight(1f))
                }
            }
            ErrorText(form.error)
        }
        if (ownWatch != null || canRest || typedOver) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                if (typedOver) UseMeasured()
                if (canRest) CardioRest()
                if (ownWatch != null) Discard()
            }
        }
    }

    val discardStep = discardingStep
    if (discardStep != null && ownWatch != null) {
        ConfirmDialog(
            title = "Discard this $noun?",
            text = "Its time" + (if (measure != Measure.TIMED) " and anything typed for it" else "") +
                " won't be logged. Nothing else changes. This can't be undone.",
            confirmLabel = "Discard",
            destructive = true,
            busy = busy,
            error = discardError,
            onDismiss = { discardingStep = null },
            onConfirm = {
                env.runner.run(Action.DiscardStopwatch(ownWatch.id, discardStep), onError = { discardError = it }) {
                    discardingStep = null
                }
            }
        )
    }
}

/** What a stopped stopwatch measured, or why its time must be entered; nothing is ever estimated. */
@Composable
private fun ReviewNotice(watch: Stopwatch, measuredSeconds: Long?, typedOver: Boolean) {
    Text(reviewNoticeText(watch, measuredSeconds, typedOver), style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag("stopwatch-review"))
}

/**
 * The whole seconds a stopped stopwatch offers for logging, or null when the user must enter the time: its running
 * time couldn't be trusted, or it ran past the 24 hours one effort can record.
 */
internal fun reviewSeconds(watch: Stopwatch): Long? =
    if (watch.needsConfirmation) null else wholeSeconds(watch.elapsedMs).takeIf { it <= MAX_EFFORT_SECONDS }

internal fun reviewNoticeText(watch: Stopwatch, measuredSeconds: Long?, typedOver: Boolean): String = when {
    watch.needsConfirmation -> "The stopwatch couldn't confirm its running time after a restart, so it stopped " +
        "without one. It started at ${formatTime(watch.startedAt)}." +
        (if (watch.elapsedMs >= 1000) " ${formatDuration(wholeSeconds(watch.elapsedMs))} was timed before it " +
            "last resumed." else "") + " Enter the time, or discard it."
    measuredSeconds == null -> "The stopwatch ran ${formatDuration(wholeSeconds(watch.elapsedMs))}, longer than " +
        "the 24 hours one effort can record. Enter the time to log, or discard it."
    typedOver -> "The stopwatch measured ${formatDuration(measuredSeconds)}. Your typed time is logged instead."
    else -> "Stopped at ${formatDuration(measuredSeconds)}. Not saved until you log it."
}

/**
 * A field of an effort's entry. After a Log or Save attempt the first failing field takes focus and carries the error;
 * an earlier attempt, from before this field was composed, never moves focus. [attemptKey] is what [attempt] counts for.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EffortNumberField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    field: EffortField,
    invalid: EffortValidation.Invalid?,
    attempt: Int,
    attemptKey: Any,
    modifier: Modifier,
    decimal: Boolean,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
    filterNumericInput: Boolean = true,
    focusOnError: Boolean = true
) {
    val focus = remember { FocusRequester() }
    val bring = remember { BringIntoViewRequester() }
    val message = invalid?.takeIf { it.field == field }?.message
    val seen = remember(attemptKey) { attempt }
    NumberField(value, onValue, label, modifier.focusRequester(focus).bringIntoViewRequester(bring),
        decimal = decimal, isError = message != null, imeAction = imeAction, onDone = onDone,
        filterNumericInput = filterNumericInput, errorSemantics = message)
    LaunchedEffect(attemptKey, attempt) {
        if (focusOnError && message != null && attempt != seen) {
            focus.requestFocus()
            bring.bringIntoView()
        }
    }
}

/**
 * In place of another exercise's entry while the stopwatch is timing: nothing new is logged in the workout until it
 * is paused or stopped, so the way back to it and Pause are one tap each. Its time is kept either way.
 */
@Composable
internal fun StopwatchElsewhere(env: AppEnv, workout: Workout, watch: Stopwatch, onGo: () -> Unit) {
    val name = workout.exercises.find { it.id == watch.exerciseId }?.exercise?.name ?: "another exercise"
    var error by rememberSaveable(watch.id) { mutableStateOf<String?>(null) }
    Column(Modifier.testTag("stopwatch-elsewhere"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "The stopwatch is timing $name. Pause or stop it to log here; its time is kept.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            StopwatchTimeText(env, watch, large = false)
        }
        Button(onClick = onGo, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("go-to-stopwatch")) {
            Text("Go to $name", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(
            onClick = {
                error = null
                env.runner.run(Action.PauseStopwatch(watch.id, watch.step, env.monotonic()), onError = { error = it })
            },
            enabled = !env.runner.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("pause-stopwatch-elsewhere")
        ) {
            RebuildIcon(R.drawable.ic_pause, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Pause stopwatch")
        }
        ErrorText(error)
    }
}

/**
 * Under the exercise rail while the stopwatch holds an unlogged effort on another exercise (paused or stopped), so it
 * is never forgotten; the whole strip goes to it.
 */
@Composable
internal fun StopwatchStrip(workout: Workout, watch: Stopwatch, onGo: () -> Unit) {
    val name = workout.exercises.find { it.id == watch.exerciseId }?.exercise?.name ?: return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClickLabel = "Go to $name", role = Role.Button, onClick = onGo)
                .semantics(mergeDescendants = true) {}
                .testTag("stopwatch-strip")
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RebuildIcon(R.drawable.ic_rest, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp))
            Text(
                "$name · stopwatch ${stopwatchStatus(watch)}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text("Go to", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * The stopwatch away from the workout screen: what it times, its time, Pause or Resume, and the way back. It is
 * stacked with the rest bar, above any navigation.
 */
@Composable
internal fun StopwatchBar(env: AppEnv, watch: Stopwatch, onOpen: (() -> Unit)?) {
    val name = env.state.workouts.find { it.id == watch.workoutId }?.exercises
        ?.find { it.id == watch.exerciseId }?.exercise?.name ?: "Stopwatch"
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.testTag("stopwatch-bar")) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (watch.running) {
                    StopwatchTimeText(env, watch, large = false)
                } else {
                    Text("Stopwatch ${stopwatchStatus(watch)}", style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("stopwatch-time"))
                }
            }
            when {
                watch.running -> IconButton(
                    onClick = { env.runner.run(Action.PauseStopwatch(watch.id, watch.step, env.monotonic())) },
                    enabled = !env.runner.busy,
                    modifier = Modifier.testTag("bar-pause-stopwatch")
                ) { RebuildIcon(R.drawable.ic_pause, contentDescription = "Pause stopwatch") }
                !watch.stopped -> IconButton(
                    onClick = { env.runner.run(Action.ResumeStopwatch(watch.id, watch.step, env.monotonic())) },
                    enabled = !env.runner.busy,
                    modifier = Modifier.testTag("bar-resume-stopwatch")
                ) { RebuildIcon(R.drawable.ic_play, contentDescription = "Resume stopwatch") }
            }
            if (onOpen != null) {
                TextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp).testTag("bar-open-workout")) {
                    Text("Open")
                }
            }
        }
    }
}

/** The load field's label: whole plates per stack, assistance, or a weight in kg or lb (per dumbbell where it is). */
internal fun effortLoadLabel(exercise: Exercise, unit: LoadUnit): String = when {
    unit == LoadUnit.PLATES -> "Plates"
    exercise.loading == LoadingType.ASSISTANCE -> "Assist ${unit.massSymbol()}"
    exercise.perDumbbell -> "${unit.massSymbol()}/DB"
    else -> unit.massSymbol()
}

/** The label of the button that adds an effort beyond the goal. */
internal fun extraEffortLabel(measure: Measure): String = when (measure) {
    Measure.TIMED -> "Extra timed set"
    Measure.DISTANCE -> "Extra distance set"
    else -> "Another effort"
}
