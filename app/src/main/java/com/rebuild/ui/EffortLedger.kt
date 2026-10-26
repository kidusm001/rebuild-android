package com.rebuild.ui

import com.rebuild.domain.loadUnitOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.rebuild.R
import com.rebuild.domain.Action
import com.rebuild.domain.Effort
import com.rebuild.domain.Exercise
import com.rebuild.domain.LoadUnit
import com.rebuild.domain.takesLoad
import com.rebuild.domain.LoadingType
import com.rebuild.domain.Measure
import com.rebuild.domain.Workout
import com.rebuild.domain.WorkoutExercise
import com.rebuild.domain.goalReached
import com.rebuild.domain.hasGoal
import com.rebuild.domain.previousEfforts
import com.rebuild.domain.qualifies
import com.rebuild.domain.qualifyingCount
import com.rebuild.domain.recordsLoad

/** The minimum each timed or distance set must reach to count, in words for a cell: "0:30", "20 m". */
internal fun minimumText(we: WorkoutExercise): String? = when (we.exercise.measure) {
    Measure.TIMED -> we.effortSeconds?.let { formatDuration(it) }
    Measure.DISTANCE -> we.effortDistance?.let(::formatDistance)
    else -> null
}

/**
 * The count line above an exercise's efforts: how many timed or distance sets reached the minimum, and how many fell
 * short; or cardio's logged time and distance against its goal. Never a pace or a combined figure.
 */
internal fun effortCountText(we: WorkoutExercise): String {
    val measure = we.exercise.measure
    val noun = effortNoun(measure)
    return when (measure) {
        Measure.TIMED, Measure.DISTANCE -> {
            val sets = we.targetSets
            val minimum = minimumText(we)
            if (sets == null || minimum == null) {
                "${noun.replaceFirstChar { it.uppercase() }}s: ${we.efforts.size}"
            } else {
                val reached = we.qualifyingCount()
                val under = we.efforts.size - reached
                "Reached $minimum: $reached of $sets" + if (under > 0) " · $under under, not counted" else ""
            }
        }
        Measure.CARDIO -> {
            val time = if (we.totalSeconds != null || we.efforts.any { it.durationSeconds != null }) {
                loggedTimeText(we) + (we.totalSeconds?.let { " of ${formatDuration(it)}" } ?: "")
            } else null
            val distance = (loggedDistanceText(we) ?: we.totalDistance?.let { formatMicrometres(0, it.unit) })
                ?.let { it + (we.totalDistance?.let { goal -> " of ${formatDistance(goal)}" } ?: "") }
            "Logged: " + (listOfNotNull(time, distance).ifEmpty { listOf("nothing yet") }.joinToString(" · ")) +
                " · ${countLabel(we.efforts.size, "effort")}"
        }
        Measure.REPS -> ""
    }
}

/** "Timed set 2", "Distance set 1" or, for cardio, "Effort 3": one effort's name in a row or a spoken label. */
internal fun effortLabel(measure: Measure, ordinal: Int): String =
    "${if (measure == Measure.CARDIO) "Effort" else effortNoun(measure).replaceFirstChar { it.uppercase() }} $ordinal"

/** What one logged effort means for its target: whether it reached the minimum, or went beyond the goal. */
internal fun effortNote(we: WorkoutExercise, index: Int): String? {
    val effort = we.efforts[index]
    val minimum = minimumText(we)
    val sets = we.targetSets
    if (minimum == null || sets == null || we.exercise.measure == Measure.CARDIO) return null
    if (!we.qualifies(effort)) return "under $minimum, not counted"
    val reachedSoFar = we.efforts.take(index + 1).count(we::qualifies)
    return if (reachedSoFar > sets) "reaches $minimum · extra" else "reaches $minimum"
}

private enum class EffortRowState { LOGGED, NEXT, PLANNED, PREVIOUS_ONLY }

private data class EffortRow(
    val ordinal: Int,
    val today: Int?,
    val previous: Effort?,
    val previousOrdinal: Int,
    val state: EffortRowState,
    val otherUnit: Boolean = false
)

/**
 * Today's efforts beside last time's in the same load unit, paired in order as the entry offers them. Rows still to
 * do are placeholders, never projected values.
 */
private fun effortRows(we: WorkoutExercise, previous: List<Effort>, expanded: Boolean): Pair<List<EffortRow>, Int> {
    val unit = effortLoadUnit(we)
    val rows = mutableListOf<EffortRow>()
    var paired = 0
    we.efforts.forEachIndexed { index, effort ->
        val comparable = effort.loadUnit == unit
        val previousEffort = if (comparable) previous.getOrNull(paired) else null
        rows += EffortRow(index + 1, index, previousEffort, if (previousEffort != null) paired + 1 else 0,
            EffortRowState.LOGGED, otherUnit = !comparable)
        if (comparable) paired++
    }
    val remaining = when {
        we.exercise.measure != Measure.CARDIO && we.targetSets != null && minimumText(we) != null ->
            (we.targetSets - we.qualifyingCount()).coerceAtLeast(0)
        we.exercise.measure == Measure.CARDIO && we.hasGoal() && we.goalReached() -> 0
        else -> 1
    }
    repeat(remaining) { k ->
        val previousEffort = previous.getOrNull(paired)
        rows += EffortRow(we.efforts.size + k + 1, null, previousEffort, if (previousEffort != null) paired + 1 else 0,
            if (k == 0 && !we.skipped) EffortRowState.NEXT else EffortRowState.PLANNED)
        paired++
    }
    val folded = previous.drop(paired)
    if (expanded) folded.forEachIndexed { k, effort ->
        rows += EffortRow(rows.size + 1, null, effort, paired + k + 1, EffortRowState.PREVIOUS_ONLY)
    }
    return rows to folded.size
}

/**
 * The efforts of a timed, distance or cardio exercise: the count against today's target or goal, then each effort
 * beside last time's in the same load unit. Only saved efforts are editable.
 */
@Composable
internal fun EffortLedger(env: AppEnv, workout: Workout, we: WorkoutExercise, onEdit: (Effort) -> Unit) {
    val exercise = we.exercise
    val measure = exercise.measure
    val unit = effortLoadUnit(we)
    val previous = env.state.previousEfforts(exercise, workout.id, unit)
    val previousStartedAt = previous?.let { p -> env.state.workouts.find { w -> w.exercises.any { it.id == p.id } } }?.startedAt
    val previousEfforts = previous?.efforts.orEmpty()
    var expanded by rememberSaveable(exercise.identity(), previousStartedAt, unit) { mutableStateOf(false) }
    val (rows, folded) = effortRows(we, previousEfforts, expanded)
    val hasPrevious = previousEfforts.isNotEmpty()
    val stacked = LocalDensity.current.fontScale > 1.3f
    val noun = effortNoun(measure)
    val ordinalHeader = if (measure == Measure.CARDIO) "#" else "Set"

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(effortCountText(we), style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("effort-count"))
        if (!hasPrevious) {
            Text(
                if (exercise.takesLoad) "No previous ${noun}s in ${unitName(unit)} for this exercise."
                else "No previous ${noun}s for this exercise.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(
            Modifier.fillMaxWidth().then(if (hasPrevious) Modifier.testTag("previous-comparable") else Modifier),
            verticalArrangement = Arrangement.spacedBy(if (stacked) 0.dp else 4.dp)
        ) {
            if (!stacked) EffortHeader(ordinalHeader, hasPrevious, previousStartedAt)
            else if (hasPrevious && previousStartedAt != null) Text(
                "Last time · ${formatDate(previousStartedAt)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("previous-date")
            )
            rows.forEach { row ->
                val label = effortLabel(measure, row.ordinal)
                if (stacked) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clearAndSetSemantics {})
                        PreviousEffort(row, exercise, label = "Last time: ", modifier = Modifier.fillMaxWidth(),
                            showPlaceholder = false)
                        TodayEffort(row, we, label, stacked = true, onEdit = onEdit, modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    val current = row.state == EffortRowState.NEXT
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (current) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            row.ordinal.toString(),
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.width(28.dp).clearAndSetSemantics {}
                        )
                        if (hasPrevious) PreviousEffort(row, exercise, label = "", modifier = Modifier.weight(1f),
                            showPlaceholder = true)
                        TodayEffort(row, we, label, stacked = false, onEdit = onEdit, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        if (folded > 0 || expanded) {
            SecondaryTextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("previous-expand")) {
                Text(if (expanded) "Show fewer from last time" else "Show all ${previousEfforts.size} from last time")
            }
        }
    }
}

@Composable
private fun EffortHeader(ordinalHeader: String, hasPrevious: Boolean, startedAt: Long?) {
    val label = MaterialTheme.typography.labelMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(ordinalHeader, style = label, color = color, modifier = Modifier.width(28.dp))
        if (hasPrevious) Column(Modifier.weight(1f)) {
            Text("Last time", style = label, color = color)
            if (startedAt != null) Text(formatDate(startedAt), style = MaterialTheme.typography.labelSmall, color = color,
                modifier = Modifier.testTag("previous-date"))
        }
        Text("Today", style = label, color = color, modifier = Modifier.weight(1f).padding(start = 8.dp))
    }
}

@Composable
private fun PreviousEffort(row: EffortRow, exercise: Exercise, label: String, modifier: Modifier, showPlaceholder: Boolean) {
    val effort = row.previous
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when {
        effort != null -> Text(
            label + effortText(effort, exercise),
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = muted,
            modifier = modifier.testTag("previous-effort-${row.previousOrdinal}").clearAndSetSemantics {
                contentDescription = "Previous ${row.previousOrdinal}: ${spokenEffort(effort, exercise)}"
            }
        )
        row.otherUnit -> Text("Other unit · not compared", style = MaterialTheme.typography.bodySmall, color = muted,
            modifier = modifier)
        showPlaceholder -> Text("—", style = MaterialTheme.typography.bodyMedium, color = muted,
            modifier = modifier.clearAndSetSemantics {})
    }
}

@Composable
private fun TodayEffort(row: EffortRow, we: WorkoutExercise, label: String, stacked: Boolean,
    onEdit: (Effort) -> Unit, modifier: Modifier) {
    val exercise = we.exercise
    val index = row.today
    if (index != null) {
        val effort = we.efforts[index]
        val note = effortNote(we, index)
        Row(
            modifier.heightIn(min = 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = "Edit $label") { onEdit(effort) }
                .semantics(mergeDescendants = true) {
                    contentDescription = "$label: ${spokenEffort(effort, exercise)}" + (note?.let { ", $it" } ?: "")
                }
                .testTag("effort-row")
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    (if (stacked) "Today: " else "") + effortText(effort, exercise),
                    style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold
                )
                if (note != null) Text(note, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RebuildIcon(R.drawable.ic_pencil, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp))
        }
        return
    }
    if (row.state == EffortRowState.PREVIOUS_ONLY) {
        if (!stacked) Text("—", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.clearAndSetSemantics {})
        return
    }
    val next = row.state == EffortRowState.NEXT
    val color = if (next) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(
        modifier.heightIn(min = 44.dp)
            .dashedOutline(color)
            .clearAndSetSemantics {
                contentDescription = "$label: ${if (next) "up next" else "planned"}, not logged yet"
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (next) Column {
            Text("Up next", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("not logged", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else Text("Not logged", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Edit a logged effort, or add a missed one ([effort] == null) to a finished workout. A correction keeps the effort's
 * own time and load unit unless changed here, and never starts a rest or the stopwatch.
 */
@Composable
internal fun EffortEditDialog(
    env: AppEnv,
    workoutId: String,
    we: WorkoutExercise,
    effort: Effort?,
    newEffortTime: Long,
    newEffortId: String? = null,
    onDismiss: () -> Unit
) {
    // Specific to the effort's current content, so a draft saved before a background change never overwrites it.
    val draftKey = effort?.let {
        listOf(it.id, it.durationSeconds, it.distance?.thousandths, it.distance?.unit, it.kg, it.lb, it.plates, it.loadUnit)
            .joinToString("|")
    } ?: "${requireNotNull(newEffortId)}|${we.entryLoadUnit}"
    key(draftKey) { EffortEditContent(env, workoutId, we, effort, newEffortTime, newEffortId, onDismiss) }
}

@Composable
private fun EffortEditContent(
    env: AppEnv,
    workoutId: String,
    we: WorkoutExercise,
    effort: Effort?,
    newEffortTime: Long,
    newEffortId: String?,
    onDismiss: () -> Unit
) {
    val exercise = we.exercise
    val measure = exercise.measure
    val noun = effortNoun(measure)
    var unit by rememberSaveable { mutableStateOf(effort?.loadUnit ?: effortLoadUnit(we)) }
    val seconds0 = effort?.durationSeconds
    var minutes by rememberSaveable { mutableStateOf(seconds0?.let { (it / 60).toString() }.orEmpty()) }
    var seconds by rememberSaveable { mutableStateOf(seconds0?.let { (it % 60).toString() }.orEmpty()) }
    var distance by rememberSaveable { mutableStateOf(effort?.distance?.let(::distanceInput).orEmpty()) }
    var distanceUnit by rememberSaveable {
        mutableStateOf(effort?.distance?.unit ?: we.efforts.lastOrNull { it.distance != null }?.distance?.unit
            ?: (we.effortDistance ?: we.totalDistance)?.unit ?: defaultDistanceUnit(measure))
    }
    var load by rememberSaveable {
        mutableStateOf(when {
            effort == null -> ""
            effort.loadUnit == LoadUnit.PLATES -> effort.plates?.toString().orEmpty()
            else -> effort.amount?.let(::formatKg).orEmpty()
        })
    }
    val effortId = effort?.id ?: requireNotNull(newEffortId)
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var invalid by remember { mutableStateOf<EffortValidation.Invalid?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    // A restored "add missed effort" dialog whose effort already committed is finished; saving again would edit it.
    val alreadyAdded = effort == null && we.efforts.any { it.id == effortId }
    LaunchedEffect(alreadyAdded) { if (alreadyAdded) onDismiss() }
    if (alreadyAdded) return

    if (confirmDelete && effort != null) {
        ConfirmDialog(
            title = "Delete this $noun?",
            text = "${effortText(effort, exercise)} will be removed from ${exercise.name}. This can't be undone.",
            confirmLabel = "Delete $noun",
            destructive = true,
            busy = env.runner.busy,
            error = error,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                env.runner.run(Action.DeleteEffort(workoutId, we.id, effort.id), onError = { error = it }) { onDismiss() }
            }
        )
        return
    }

    fun save() {
        val parsed = when (val result = validateEffort(exercise, unit, minutes, seconds, distance, distanceUnit, load)) {
            is EffortValidation.Invalid -> {
                invalid = result
                attempt++
                return
            }
            is EffortValidation.Valid -> result
        }
        invalid = null
        error = null
        env.runner.run(
            Action.SaveEffort(workoutId, we.id, effortId, parsed.durationSeconds, parsed.distance, parsed.kg,
                effort?.completedAt ?: newEffortTime, unit, parsed.plates),
            onError = { error = it }
        ) { onDismiss() }
    }

    val loadShown = measure.recordsLoad && (exercise.loading != LoadingType.BODYWEIGHT || unit == LoadUnit.PLATES)
    AdaptiveDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (effort == null) "Add a missed $noun" else "Edit $noun") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${exercise.name} · ${loadingDescription(exercise, unit)}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    when {
                        effort == null -> "It is added after the others and starts no rest or stopwatch."
                        measure == Measure.DISTANCE -> "The time is optional."
                        measure == Measure.CARDIO -> "A time, a distance or both."
                        else -> "When it was logged stays as it is."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (exercise.takesLoad) {
                    LoadUnitSelector(unit, exercise.loadUnitOptions(), enabled = !env.runner.busy) {
                        if (it != unit) { unit = it; load = ""; invalid = null }
                    }
                    if (unit == LoadUnit.PLATES) Text(PLATES_HELP, style = MaterialTheme.typography.bodySmall)
                }
                @Composable fun Distance() {
                    // Cardio's distance is the last field, so the keyboard's action saves.
                    val last = measure == Measure.CARDIO
                    EffortNumberField(distance, { distance = it }, "Distance (${distanceUnit.symbol})", EffortField.DISTANCE,
                        invalid, attempt, Unit, Modifier.fillMaxWidth().testTag("edit-effort-distance"), decimal = true,
                        imeAction = if (last) ImeAction.Done else ImeAction.Next, onDone = if (last) ::save else ({}))
                    DistanceUnitChoice(distanceUnit, { distanceUnit = it }, enabled = !env.runner.busy)
                }
                @Composable fun Time() {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EffortNumberField(minutes, { minutes = it }, "Minutes", EffortField.DURATION, invalid, attempt, Unit,
                            Modifier.weight(1f).testTag("edit-effort-minutes"), decimal = false)
                        EffortNumberField(seconds, { seconds = it }, "Seconds", EffortField.DURATION, invalid, attempt, Unit,
                            Modifier.weight(1f).testTag("edit-effort-seconds"), decimal = false, focusOnError = false,
                            imeAction = if (loadShown || measure == Measure.CARDIO) ImeAction.Next else ImeAction.Done,
                            onDone = ::save)
                    }
                }
                if (measure == Measure.DISTANCE) Distance()
                Time()
                if (measure == Measure.CARDIO) Distance()
                if (loadShown) EffortNumberField(load, { load = it }, effortLoadLabel(exercise, unit), EffortField.LOAD,
                    invalid, attempt, Unit,
                    Modifier.fillMaxWidth().testTag(if (unit == LoadUnit.PLATES) "edit-effort-plates" else "edit-effort-kg"),
                    decimal = unit != LoadUnit.PLATES, filterNumericInput = unit != LoadUnit.PLATES,
                    imeAction = ImeAction.Done, onDone = ::save)
                invalid?.let {
                    Text(it.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                ErrorText(error)
                if (effort != null) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        enabled = !env.runner.busy,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("delete-effort")
                    ) { Text("Delete $noun") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::save, enabled = !env.runner.busy, modifier = Modifier.testTag("save-effort")) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
