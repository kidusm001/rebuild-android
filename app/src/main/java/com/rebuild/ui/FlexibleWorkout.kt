package com.rebuild.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rebuild.R
import com.rebuild.domain.Action
import com.rebuild.domain.Measure
import com.rebuild.domain.goalReached
import com.rebuild.domain.hasGoal
import com.rebuild.domain.loggedSeconds
import com.rebuild.domain.measuredGoal
import com.rebuild.domain.measureOf
import com.rebuild.domain.qualifyingCount
import com.rebuild.domain.recordCount
import com.rebuild.domain.Phase
import com.rebuild.domain.PlanAddition
import com.rebuild.domain.Workout
import com.rebuild.domain.WorkoutExercise
import com.rebuild.domain.ScheduledRoutine
import com.rebuild.domain.belowTarget
import com.rebuild.domain.inPlan
import com.rebuild.domain.lastLoggedPerLeg
import com.rebuild.domain.openGroup
import com.rebuild.domain.perLegConvention
import com.rebuild.domain.restSuggestion
import com.rebuild.domain.scheduledRoutine
import com.rebuild.domain.workingCount

/**
 * The parts of a goal the user set, in the exercise's own terms: "3 working sets · 8–12 reps", "3 timed sets of at
 * least 0:30" or "at least 20:00 in total". Null without one.
 */
internal fun goalSummary(we: WorkoutExercise): String? = when (we.exercise.measure) {
    Measure.REPS -> listOfNotNull(
        we.targetSets?.let { countLabel(it, "working set") },
        if (we.minReps != null && we.maxReps != null) "${we.minReps}–${we.maxReps} reps" else null
    ).takeIf { it.isNotEmpty() }?.joinToString(" · ")
    else -> measuredGoalText(we.exercise.measure, we.targetSets, we.measuredGoal)
}

/** A required exercise the user skipped today that is still short of its target. */
internal fun WorkoutExercise.showsSkipped(): Boolean = !optional && skipped && belowTarget()

/** Whether its target or optional goal is logged; an entry without a goal never shows as met. */
internal fun WorkoutExercise.goalMet(): Boolean = goalReached()

/**
 * "2/3" against a target or goal: working sets, or timed and distance sets that reached their minimum. Without one,
 * what was logged. Cardio shows its logged total.
 */
internal fun WorkoutExercise.progressText(): String = when (exercise.measure) {
    Measure.REPS -> targetSets?.let { "${workingCount()}/$it" } ?: "${workingCount()}"
    Measure.TIMED, Measure.DISTANCE -> targetSets?.let { "${qualifyingCount()}/$it" } ?: "${efforts.size}"
    Measure.CARDIO -> if (cardioShowsTime()) loggedTimeText(this)
        else loggedDistanceText(this) ?: totalDistance?.let { formatMicrometres(0, it.unit) } ?: "0"
}

/** Cardio progress leads with time unless the goal is only a distance or only distances were logged. */
private fun WorkoutExercise.cardioShowsTime(): Boolean = totalSeconds != null ||
    (totalDistance == null && (efforts.isEmpty() || efforts.any { it.durationSeconds != null }))

/** Spoken progress for rails and lists: never implies a target the entry doesn't have. */
internal fun WorkoutExercise.progressDescription(): String {
    val met = goalMet()
    val status = when {
        optional -> ", optional goal" + if (met) " logged" else ""
        else -> (if (met) ", target logged" else "") + if (showsSkipped()) ", skipped today" else ""
    }
    return when (exercise.measure) {
        Measure.REPS -> {
            val done = workingCount()
            when {
                optional && targetSets == null -> "${countLabel(done, "working set")}, optional"
                else -> "$done of $targetSets working sets$status"
            }
        }
        Measure.TIMED, Measure.DISTANCE -> {
            val noun = effortNoun(exercise.measure)
            val sets = targetSets
            if (sets == null) "${countLabel(efforts.size, noun)}${if (optional) ", optional" else ""}" else {
                val reached = qualifyingCount()
                val minimum = if (exercise.measure == Measure.TIMED) effortSeconds?.let { spokenDuration(it) }
                    else effortDistance?.let(::spokenDistance)
                "$reached of ${countLabel(sets, noun)}" + (minimum?.let { " reached $it" } ?: "") +
                    (if (efforts.size != reached) ", ${efforts.size} logged" else "") + status
            }
        }
        Measure.CARDIO -> {
            val logged = listOfNotNull(
                spokenDuration(loggedSeconds()).takeIf { cardioShowsTime() || efforts.any { it.durationSeconds != null } },
                spokenLoggedDistance(this)
            ).ifEmpty { listOf(spokenDuration(0)) }.joinToString(" and ") + " logged"
            if (!hasGoal()) logged + if (optional) ", optional" else ""
            else logged + " of " + measuredGoalText(Measure.CARDIO, null, measuredGoal, spoken = true) + status
        }
    }
}

/** The line under an exercise's name: today's target, a skip, or an optional goal. */
internal fun targetLine(workout: Workout, we: WorkoutExercise): String {
    val sets = we.targetSets
    val goal = goalSummary(we)
    return when {
        we.exercise.measure != Measure.REPS && !we.optional && goal != null ->
            if (we.showsSkipped()) "Skipped today · target $goal" else "Today's target: $goal"
        we.exercise.measure == Measure.REPS && !we.optional && sets != null && we.showsSkipped() ->
            "Skipped today · target ${countLabel(sets, "working set")} · ${we.minReps}–${we.maxReps} reps"
        we.exercise.measure == Measure.REPS && !we.optional && sets != null ->
            "Today's target: $sets working ${if (sets == 1) "set" else "sets"} · " +
                "${we.minReps}–${we.maxReps} reps · ${we.targetRir} RIR"
        else -> goal?.let { "Optional goal: $it" }
            ?: if (workout.extra) "No target · log what you do" else "Added today · optional, no target"
    }
}

/** History's compact target text for one exercise. */
internal fun historyTargetText(workout: Workout, we: WorkoutExercise): String = when {
    !we.optional && we.exercise.measure != Measure.REPS ->
        "Target ${goalSummary(we) ?: "none"}" + if (we.showsSkipped()) " · skipped" else ""
    !we.optional -> "Target ${we.targetSets} × ${we.minReps}–${we.maxReps} @ ${we.targetRir} RIR" +
        if (we.showsSkipped()) " · skipped" else ""
    else -> ((if (workout.extra) "" else "Added · ") + (goalSummary(we)?.let { "optional goal $it" } ?: "no target"))
        .replaceFirstChar { it.uppercase() }
}

/** Everything logged for an entry, in words, counting each drop or rest-pause stage and each effort it holds. */
internal fun recordsSummary(we: WorkoutExercise): String {
    val normal = we.sets.count { !it.warmup && it.groupId == null }
    val groups = we.setGroups.size
    val stages = we.sets.count { it.groupId != null }
    val warmups = we.sets.count { it.warmup }
    return listOfNotNull(
        normal.takeIf { it > 0 }?.let { countLabel(it, "set") },
        groups.takeIf { it > 0 }?.let { "${countLabel(it, "drop or rest-pause set")} (${countLabel(stages, "stage")})" },
        warmups.takeIf { it > 0 }?.let { countLabel(it, "warm-up") },
        we.efforts.size.takeIf { it > 0 }?.let { countLabel(it, effortNoun(we.exercise.measure)) }
    ).joinToString(", ")
}

/** What a rest does for this exercise, in a sentence: cardio's rest only runs when the user starts it. */
internal fun restSentence(we: WorkoutExercise): String =
    if (we.exercise.measure == Measure.CARDIO) "Rest ${formatRest(we.restSeconds)}, only if you start it after an effort."
    else "Rest ${formatRest(we.restSeconds)}."

/**
 * Removing an exercise added to a workout. Anything logged for it is deleted with it, so the dialog names every
 * set, stage and warm-up first, and the request carries that count: if more was logged meanwhile, nothing changes.
 */
@Composable
internal fun RemoveEntryDialog(env: AppEnv, workout: Workout, we: WorkoutExercise, onDismiss: () -> Unit) {
    var error by rememberSaveable(we.id) { mutableStateOf<String?>(null) }
    val records = we.recordCount()
    ConfirmDialog(
        title = "Remove ${we.exercise.name}?",
        text = if (records == 0) {
            "Nothing is logged for it in this workout. Your plan doesn't change."
        } else {
            "This also deletes what you logged for it in this workout: ${recordsSummary(we)}. " +
                "It can't be undone. Your plan doesn't change."
        },
        confirmLabel = if (records == 0) "Remove" else "Remove and delete ${countLabel(records, "record")}",
        destructive = records > 0,
        busy = env.runner.busy,
        error = error,
        onDismiss = onDismiss,
        onConfirm = {
            error = null
            env.runner.run(Action.RemoveWorkoutExercise(workout.id, we.id, records), onError = { error = it },
                onSuccess = onDismiss)
        }
    )
}

/** Session-only order: moving an exercise here never reorders the plan. */
@Composable
internal fun ReorderDialog(env: AppEnv, workout: Workout, onDismiss: () -> Unit) {
    var error by rememberSaveable(workout.id) { mutableStateOf<String?>(null) }
    fun move(we: WorkoutExercise, to: Int) {
        error = null
        env.runner.run(Action.MoveWorkoutExercise(workout.id, we.id, to), onError = { error = it })
    }
    AdaptiveDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reorder exercises") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(Modifier.testTag("reorder-list")) {
                    workout.exercises.forEachIndexed { index, we ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("reorder-row-$index"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OrderBadge(index + 1)
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(we.exercise.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis)
                                val status = when {
                                    we.optional && !workout.extra -> "Added today"
                                    we.showsSkipped() -> "Skipped today"
                                    else -> null
                                }
                                if (status != null) Text(status, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { move(we, index - 1) }, enabled = index > 0 && !env.runner.busy,
                                modifier = Modifier.testTag("move-up-$index")) {
                                RebuildIcon(R.drawable.ic_arrow_up, contentDescription = "Move ${we.exercise.name} up")
                            }
                            IconButton(onClick = { move(we, index + 1) },
                                enabled = index < workout.exercises.lastIndex && !env.runner.busy,
                                modifier = Modifier.testTag("move-down-$index")) {
                                RebuildIcon(R.drawable.ic_arrow_down, contentDescription = "Move ${we.exercise.name} down")
                            }
                        }
                    }
                }
                ErrorText(error)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("reorder-done")) { Text("Done") } }
    )
}

internal fun parseCount(text: String, range: IntRange): Int? = text.trim().toIntOrNull()?.takeIf { it in range }

/** Sets and a rep range typed by the user; [optional] allows leaving them blank. Returns an error or the values. */
internal fun parseGoal(setsText: String, minText: String, maxText: String, optional: Boolean):
    Pair<String?, Triple<Int?, Int?, Int?>> {
    val sets = if (optional && setsText.isBlank()) null else parseCount(setsText, TARGET_SETS_RANGE)
        ?: return "Enter working sets from 1 to ${TARGET_SETS_RANGE.last}" +
            (if (optional) ", or leave it blank." else ".") to Triple(null, null, null)
    if (optional && minText.isBlank() && maxText.isBlank()) return null to Triple(sets, null, null)
    if (minText.isBlank() || maxText.isBlank()) {
        return (if (optional) "Enter both ends of the rep range, or leave both blank." else
            "Enter the rep range, low and high.") to Triple(null, null, null)
    }
    val min = parseCount(minText, 1..MAX_REPS)
    val max = parseCount(maxText, 1..MAX_REPS)
    if (min == null || max == null || max < min) {
        return "Enter a rep range from 1 to $MAX_REPS with the low end first." to Triple(null, null, null)
    }
    return null to Triple(sets, min, max)
}

@Composable
internal fun GoalFields(
    setsText: String,
    onSets: (String) -> Unit,
    minText: String,
    onMin: (String) -> Unit,
    maxText: String,
    onMax: (String) -> Unit,
    restText: String,
    onRest: (String) -> Unit,
    optional: Boolean,
    restHint: String?,
    onDone: () -> Unit
) {
    val blank = if (optional) " (optional)" else ""
    NumberField(setsText, onSets, "Working sets$blank", Modifier.fillMaxWidth().testTag("goal-sets"), decimal = false)
    val stacked = LocalDensity.current.fontScale > 1.3f
    if (stacked) {
        NumberField(minText, onMin, "Reps, low end$blank", Modifier.fillMaxWidth().testTag("goal-min"), decimal = false)
        NumberField(maxText, onMax, "Reps, high end$blank", Modifier.fillMaxWidth().testTag("goal-max"), decimal = false)
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(minText, onMin, "Reps from", Modifier.weight(1f).testTag("goal-min"), decimal = false)
            NumberField(maxText, onMax, "Reps to", Modifier.weight(1f).testTag("goal-max"), decimal = false)
        }
    }
    NumberField(restText, onRest, "Rest between sets (seconds)", Modifier.fillMaxWidth().testTag("goal-rest"),
        decimal = false, imeAction = ImeAction.Done, supportingText = restHint, onDone = onDone)
}

/**
 * How reps are counted. A convention the plan already holds is shown, never changed; otherwise the user chooses,
 * unless [lockedBecause] says why it is already fixed (sets logged with it, or a saved plan exercise).
 */
@Composable
internal fun PerLegChoice(known: Boolean?, perLeg: Boolean, lockedBecause: String?, onChange: (Boolean) -> Unit) {
    when {
        known == true -> Text("Reps are counted per leg, as in your plan.", style = MaterialTheme.typography.bodyMedium)
        known == false -> Unit
        lockedBecause != null -> Text(
            "Reps are counted ${if (perLeg) "per leg" else "as a total"}, $lockedBecause",
            style = MaterialTheme.typography.bodyMedium
        )
        else -> CheckRow(
            "Count reps per leg", perLeg, onChange,
            supporting = "For one-leg exercises logged per side. Rebuild never converts reps.",
            modifier = Modifier.testTag("goal-per-leg")
        )
    }
}

private const val LOGGED_LOCK = "as already logged. That can't change now."

internal fun restHint(seconds: Int, label: String) = "Suggested ${formatRest(seconds)} · $label"

/**
 * An optional entry's own goal and rest, for this workout only. Every part of the goal can stay blank; it never blocks
 * finishing and never counts toward the weekly checklist.
 */
@Composable
internal fun EntryGoalDialog(env: AppEnv, workout: Workout, we: WorkoutExercise, onDismiss: () -> Unit) {
    val measure = we.exercise.measure
    val key = listOf(we.id, we.targetSets, we.minReps, we.maxReps, we.restSeconds, we.perLeg, we.measuredGoal)
        .joinToString("|")
    var setsText by rememberSaveable(key) { mutableStateOf(we.targetSets?.toString().orEmpty()) }
    var minText by rememberSaveable(key) { mutableStateOf(we.minReps?.toString().orEmpty()) }
    var maxText by rememberSaveable(key) { mutableStateOf(we.maxReps?.toString().orEmpty()) }
    var restText by rememberSaveable(key) { mutableStateOf(we.restSeconds.toString()) }
    val measured = rememberGoalText(key) { GoalText.of(measure, we.targetSets, we.measuredGoal, we.restSeconds) }
    var perLeg by rememberSaveable(key) { mutableStateOf(we.perLeg) }
    var error by rememberSaveable(key) { mutableStateOf<String?>(null) }
    val known = env.state.perLegConvention(we.exercise.id)
    val suggestion = env.state.restSuggestion(we.exercise.id, workout.phase, excludingEntryId = we.id)

    fun save() {
        if (measure != Measure.REPS) {
            val (problem, parsed) = parseMeasuredGoal(measure, measured, optional = true)
            error = problem
            if (parsed == null) return
            val goal = parsed.goal
            env.runner.run(
                Action.SetEntryGoal(workout.id, we.id, parsed.sets, null, null, parsed.rest, perLeg = false,
                    effortSeconds = goal.effortSeconds, effortDistance = goal.effortDistance,
                    totalSeconds = goal.totalSeconds, totalDistance = goal.totalDistance),
                onError = { error = it }, onSuccess = onDismiss
            )
            return
        }
        val (problem, goal) = parseGoal(setsText, minText, maxText, optional = true)
        val rest = parseRest(restText)
        error = problem ?: if (rest == null) "Rest must be between 1 and 3600 seconds." else null
        if (error != null || rest == null) return
        env.runner.run(
            Action.SetEntryGoal(workout.id, we.id, goal.first, goal.second, goal.third, rest, perLeg),
            onError = { error = it }, onSuccess = onDismiss
        )
    }

    AdaptiveDialog(
        onDismissRequest = onDismiss,
        title = { Text("Goal and rest · ${we.exercise.name}") },
        text = {
            val hint = restHint(suggestion.seconds, suggestion.source.label)
            if (measure == Measure.REPS) {
                GoalFields(setsText, { setsText = it }, minText, { minText = it }, maxText, { maxText = it },
                    restText, { restText = it }, optional = true, restHint = hint, onDone = ::save)
                PerLegChoice(known, perLeg, lockedBecause = LOGGED_LOCK.takeIf { we.sets.isNotEmpty() }) { perLeg = it }
            } else {
                MeasuredGoalFields(measure, measured, optional = true, restHint = hint, onDone = ::save,
                    enabled = !env.runner.busy)
            }
            ErrorText(error)
        },
        confirmButton = {
            TextButton(onClick = ::save, enabled = !env.runner.busy, modifier = Modifier.testTag("goal-save")) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * The user's own exercise in one routine's future workouts of one phase, with targets they enter and review here.
 * From a workout, [source] is the optional entry being saved: today it stays optional.
 */
@Composable
internal fun PlanAdditionDialog(
    env: AppEnv,
    phase: Phase,
    template: ScheduledRoutine,
    exerciseId: String,
    existing: PlanAddition?,
    source: WorkoutExercise?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit = onDismiss
) {
    val name = env.exercise(exerciseId)?.name ?: source?.exercise?.name ?: exerciseId
    val measure = env.state.measureOf(exerciseId)
    val key = "${phase.name}|${template.id}|$exerciseId|${existing?.id}|${existing?.hashCode()}"
    val suggestion = env.state.restSuggestion(exerciseId, phase)
    val known = env.state.perLegConvention(exerciseId, excludingAdditionId = existing?.id)
    // A goal is never inferred from what was performed: only a goal the user set today, or the saved one, is offered.
    val measured = rememberGoalText(key) {
        GoalText.of(measure, existing?.sets ?: source?.targetSets, existing?.measuredGoal ?: source?.measuredGoal
            ?: com.rebuild.domain.MeasuredGoal(), existing?.restSeconds ?: source?.restSeconds ?: suggestion.seconds)
    }
    var setsText by rememberSaveable(key) {
        mutableStateOf((existing?.sets ?: source?.targetSets)?.toString().orEmpty())
    }
    var minText by rememberSaveable(key) {
        mutableStateOf((existing?.minReps ?: source?.minReps)?.toString().orEmpty())
    }
    var maxText by rememberSaveable(key) {
        mutableStateOf((existing?.maxReps ?: source?.maxReps)?.toString().orEmpty())
    }
    var restText by rememberSaveable(key) {
        mutableStateOf((existing?.restSeconds ?: source?.restSeconds ?: suggestion.seconds).toString())
    }
    var perLeg by rememberSaveable(key) {
        mutableStateOf(existing?.perLeg ?: known ?: source?.perLeg ?: env.state.lastLoggedPerLeg(exerciseId) ?: false)
    }
    var error by rememberSaveable(key) { mutableStateOf<String?>(null) }
    var confirmingRemove by rememberSaveable(key) { mutableStateOf(false) }
    // One ID per dialog, so a retried save edits the addition it already created instead of adding a second one.
    val additionId by rememberSaveable(key) { mutableStateOf(existing?.id ?: newId()) }

    fun save() {
        if (measure != Measure.REPS) {
            val (problem, parsed) = parseMeasuredGoal(measure, measured, optional = false)
            error = problem
            if (parsed == null) return
            val goal = parsed.goal
            env.runner.run(
                Action.SavePlanAddition(additionId, phase, template.id, exerciseId, parsed.sets, null, null, parsed.rest,
                    perLeg = false, effortSeconds = goal.effortSeconds, effortDistance = goal.effortDistance,
                    totalSeconds = goal.totalSeconds, totalDistance = goal.totalDistance),
                onError = { error = it }, onSuccess = onSaved
            )
            return
        }
        val (problem, goal) = parseGoal(setsText, minText, maxText, optional = false)
        val rest = parseRest(restText)
        error = problem ?: if (rest == null) "Rest must be between 1 and 3600 seconds." else null
        val (sets, min, max) = goal
        if (error != null || rest == null || sets == null || min == null || max == null) return
        env.runner.run(
            Action.SavePlanAddition(additionId, phase, template.id, exerciseId, sets, min, max, rest, perLeg),
            onError = { error = it }, onSuccess = onSaved
        )
    }

    AdaptiveDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "$name · ${template.name}" else "Add to ${template.name}") },
        text = {
            if (existing == null) Text(name, style = MaterialTheme.typography.titleMedium)
            val hint = if (existing == null && source == null) restHint(suggestion.seconds, suggestion.source.label) else null
            if (measure == Measure.REPS) {
                GoalFields(setsText, { setsText = it }, minText, { minText = it }, maxText, { maxText = it },
                    restText, { restText = it }, optional = false, restHint = hint, onDone = ::save)
                PerLegChoice(known, perLeg, lockedBecause = when {
                    existing != null -> "as saved in your plan."
                    source?.sets?.isNotEmpty() == true -> LOGGED_LOCK
                    else -> null
                }) { perLeg = it }
            } else {
                MeasuredGoalFields(measure, measured, optional = false, restHint = hint, onDone = ::save,
                    enabled = !env.runner.busy)
            }
            ErrorText(error)
            if (existing != null) {
                HorizontalDivider()
                TextButton(
                    onClick = { confirmingRemove = true },
                    enabled = !env.runner.busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("plan-remove-addition")
                ) { Text("Remove from future ${phaseShort(phase)} workouts") }
            }
        },
        confirmButton = {
            TextButton(onClick = ::save, enabled = !env.runner.busy, modifier = Modifier.testTag("plan-addition-save")) {
                Text(if (existing != null) "Save changes" else "Save to plan")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (confirmingRemove && existing != null) {
        var removeError by rememberSaveable(key) { mutableStateOf<String?>(null) }
        ConfirmDialog(
            title = "Remove $name?",
            text = "Future ${template.name} workouts in this phase (${phaseShort(phase)}) won't include it. " +
                "Started and finished " +
                "workouts keep it with everything logged.",
            confirmLabel = "Remove",
            busy = env.runner.busy,
            error = removeError,
            onDismiss = { confirmingRemove = false },
            onConfirm = {
                removeError = null
                env.runner.run(Action.RemovePlanAddition(existing.id), onError = { removeError = it }) {
                    confirmingRemove = false
                    onDismiss()
                }
            }
        )
    }
}

/**
 * Shown in place of the entry for a required exercise skipped today: its target stays, nothing is logged for it, and
 * the next step stays primary.
 */
@Composable
internal fun SkippedPanel(
    env: AppEnv,
    workout: Workout,
    we: WorkoutExercise,
    nextName: String?,
    onNext: (() -> Unit)?,
    onFinish: () -> Unit
) {
    var error by rememberSaveable(we.id) { mutableStateOf<String?>(null) }
    val large = LocalDensity.current.fontScale > 1.3f
    Column(Modifier.testTag("skipped-panel"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RebuildIcon(R.drawable.ic_skip_forward, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Skipped today. Its target stays" + (if (we.recordCount() > 0) " and what you logged is kept" else "") +
                    ". Undo the skip to log it.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        @Composable fun Primary(modifier: Modifier) {
            if (onNext != null && nextName != null) {
                Button(onClick = onNext, modifier = modifier.heightIn(min = 56.dp).testTag("next-exercise")) {
                    Text("Next: $nextName", maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Button(onClick = onFinish, enabled = !env.runner.busy,
                    modifier = modifier.heightIn(min = 56.dp).testTag("finish-from-entry")) { Text("Finish workout") }
            }
        }
        @Composable fun Undo(modifier: Modifier) {
            OutlinedButton(
                onClick = {
                    error = null
                    env.runner.run(Action.SetSkipped(workout.id, we.id, false), onError = { error = it })
                },
                enabled = !env.runner.busy,
                modifier = modifier.heightIn(min = 56.dp).testTag("undo-skip")
            ) { Text("Undo skip", maxLines = 1) }
        }
        if (large) {
            Primary(Modifier.fillMaxWidth())
            Undo(Modifier.fillMaxWidth())
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Undo(Modifier)
                Primary(Modifier.weight(1f))
            }
        }
        ErrorText(error)
    }
}

/**
 * Secondary options for the exercise in view. A planned exercise can be adjusted or skipped for today; an added one
 * can get an optional goal, be saved to the plan by explicit choice, or be removed.
 */
@Composable
internal fun EntryOptions(
    env: AppEnv,
    workout: Workout,
    we: WorkoutExercise,
    onAdjust: () -> Unit,
    onEditGoal: () -> Unit,
    onSaveToPlan: () -> Unit,
    onRemove: () -> Unit,
    onSkipped: () -> Unit
) {
    var error by rememberSaveable(we.id) { mutableStateOf<String?>(null) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val busy = env.runner.busy
    fun skip(skipped: Boolean) {
        error = null
        env.runner.run(Action.SetSkipped(workout.id, we.id, skipped), onError = { error = it }) {
            if (skipped) onSkipped()
        }
    }
    Column(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!we.optional) {
            Text(
                (we.baselineSets?.let { "Baseline ${countLabel(it, "set")}" } ?: "Your own exercise in this routine") +
                    (we.upperSets?.let { " · advisory upper $it (not a target)" } ?: "") +
                    if (we.exercise.measure == Measure.REPS) "" else ". ${restSentence(we)}",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Text(
                if (workout.extra) "No target unless you set a goal. ${restSentence(we)}"
                else "Added today · optional. " + restSentence(we),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
    if (!we.optional) {
        SecondaryTextButton(onClick = onAdjust, enabled = !busy, icon = R.drawable.ic_pencil) { Text("Adjust today") }
        when {
            we.skipped -> SecondaryTextButton(onClick = { skip(false) }, enabled = !busy, icon = R.drawable.ic_undo_2,
                modifier = Modifier.testTag("undo-skip-option")) { Text("Undo skip") }
            we.belowTarget() && we.openGroup() != null -> Text(
                "Finish the ${we.openGroup()!!.method.title.lowercase()} in progress before skipping this exercise.",
                style = MaterialTheme.typography.bodySmall, color = muted,
                modifier = Modifier.padding(start = 12.dp)
            )
            we.belowTarget() && env.state.stopwatch.timesEntry(workout, we) -> Text(
                "Log or discard the stopwatch's effort before skipping this exercise. Its time is kept until then.",
                style = MaterialTheme.typography.bodySmall, color = muted,
                modifier = Modifier.padding(start = 12.dp)
            )
            we.belowTarget() -> SecondaryTextButton(onClick = { skip(true) }, enabled = !busy,
                icon = R.drawable.ic_skip_forward, modifier = Modifier.testTag("skip-today")) {
                Text("Skip today · target stays")
            }
        }
    } else {
        SecondaryTextButton(onClick = onEditGoal, enabled = !busy, icon = R.drawable.ic_pencil,
            modifier = Modifier.testTag("edit-goal")) { Text("Goal and rest") }
        // Only an occurrence still on its phase's week has future workouts to save to.
        val template = env.state.scheduledRoutine(workout.phase, workout.templateId)
        if (template != null) {
            if (env.state.inPlan(template, workout.phase, we.exercise.id)) {
                Text(
                    "In your ${template.name} plan",
                    style = MaterialTheme.typography.bodySmall, color = muted,
                    modifier = Modifier.padding(start = 12.dp).testTag("addition-in-plan")
                )
            } else {
                SecondaryTextButton(onClick = onSaveToPlan, enabled = !busy, icon = R.drawable.ic_clipboard_list,
                    modifier = Modifier.testTag("save-addition")) { Text("Save to ${template.name} plan…") }
            }
        }
        SecondaryTextButton(onClick = onRemove, enabled = !busy, icon = R.drawable.ic_x,
            modifier = Modifier.testTag("remove-entry")) { Text("Remove from this workout") }
    }
    ErrorText(error)
}

/**
 * Asked once when nothing settles how an added exercise's reps are counted (not in the plan and never logged, or the
 * plan disagrees). The answer stays with the entry; Rebuild never converts reps.
 */
@Composable
internal fun PerLegQuestionDialog(name: String, onDismiss: () -> Unit, onAnswer: (Boolean) -> Unit) {
    AdaptiveDialog(
        onDismissRequest = onDismiss,
        title = { Text("How do you count $name reps?") },
        text = {
            Text(
                "Rebuild keeps reps exactly as you enter them and compares them only with sets counted the same way.",
                style = MaterialTheme.typography.bodyMedium
            )
            Column {
                ListRow("Per leg", supporting = "10 means 10 reps on each leg", onClick = { onAnswer(true) },
                    modifier = Modifier.testTag("per-leg-yes"))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ListRow("Not per leg", supporting = "One plain rep count, as for squats and presses",
                    onClick = { onAnswer(false) }, modifier = Modifier.testTag("per-leg-no"))
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** How many working sets an exercise being added to today's workout aims for. It can be changed later. */
@Composable
internal fun AddSetsDialog(env: AppEnv, exerciseId: String, onDismiss: () -> Unit, onAdd: (Int?) -> Unit) {
    val name = env.exercise(exerciseId)?.name ?: exerciseId
    // Last time's count of working sets for it, else three.
    val last = env.state.workouts.filter { it.finishedAt != null }.sortedByDescending { it.startedAt }.asSequence()
        .flatMap { it.exercises.asSequence() }
        .firstOrNull { it.exercise.id == exerciseId && it.workingCount() > 0 }?.workingCount()
    var sets by rememberSaveable(exerciseId) { mutableIntStateOf((last ?: 3).coerceIn(ADD_SETS_RANGE)) }
    AdaptiveDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add $name") },
        text = {
            Stepper("Working sets", sets, ADD_SETS_RANGE, { sets = it }, Modifier.testTag("add-sets"))
        },
        confirmButton = {
            TextButton(onClick = { onAdd(sets) }, enabled = !env.runner.busy, modifier = Modifier.testTag("add-sets-confirm")) {
                Text("Add")
            }
        },
        // Adding without a goal stays one tap: an added exercise never needs a target.
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TextButton(onClick = { onAdd(null) }, enabled = !env.runner.busy, modifier = Modifier.testTag("add-sets-none")) {
                Text("No goal")
            }
        }
    )
}

private val ADD_SETS_RANGE = 1..20

/** The rail's last item: adds an exercise to the workout in progress. */
@Composable
internal fun AddExercisePill(onClick: () -> Unit, enabled: Boolean) {
    val shape = MaterialTheme.shapes.small
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { role = Role.Button }
            .testTag("add-exercise"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RebuildIcon(R.drawable.ic_plus, contentDescription = null, modifier = Modifier.size(20.dp))
            Text("Add exercise", style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** An extra workout before its first exercise: one clear way forward, reachable by scrolling with large text. */
@Composable
internal fun EmptyWorkoutBody(onAdd: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Add your first exercise", style = MaterialTheme.typography.titleMedium)
        Button(onClick = onAdd, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("add-first-exercise")) {
            RebuildIcon(R.drawable.ic_plus, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add exercise")
        }
    }
}

/** A one-line, dismissible message such as "already in this workout"; announced politely. */
@Composable
internal fun NoticeLine(text: String, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp).testTag("workout-notice"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
        IconButton(onClick = onDismiss) { RebuildIcon(R.drawable.ic_x, contentDescription = "Dismiss message") }
    }
}
