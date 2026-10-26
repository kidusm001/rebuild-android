package com.rebuild.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.rebuild.R
import com.rebuild.domain.Action
import com.rebuild.domain.Exercise
import com.rebuild.domain.LoadUnit
import com.rebuild.domain.LoadingType
import com.rebuild.domain.LoggedSet
import com.rebuild.domain.MAX_GROUP_STAGES
import com.rebuild.domain.RoutinePlacement
import com.rebuild.domain.SetMethod
import com.rebuild.domain.WorkEntry
import com.rebuild.domain.Workout
import com.rebuild.domain.WorkoutExercise
import com.rebuild.domain.completesRoutine
import com.rebuild.domain.followUps
import com.rebuild.domain.hasWorkingEffort
import com.rebuild.domain.mainSet
import com.rebuild.domain.occurrenceOf
import com.rebuild.domain.scheduledRoutine
import com.rebuild.domain.stages
import com.rebuild.domain.weekStart
import com.rebuild.domain.workEntries
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What a stage after the set itself is called: "Drop", or "Pause" for a rest-pause. */
internal fun followUpLabel(method: SetMethod): String = when (method) {
    SetMethod.DROP -> "Drop"
    SetMethod.REST_PAUSE -> "Pause"
}

/**
 * A drop in its set's row: "32.5 × 7", the unit implied by the set right above it, since a drop uses its set's unit.
 * A stage a correction moved to another unit, or a plate stack, keeps its unit spelled out.
 */
internal fun followUpText(stage: LoggedSet, main: LoggedSet, exercise: Exercise): String =
    if (stage.loadUnit == main.loadUnit && stage.loadUnit != LoadUnit.PLATES && stage.amount != null &&
        exercise.loading == LoadingType.EXTERNAL) "${formatKg(stage.amount!!)} × ${stage.reps}"
    else loadRepsText(stage, exercise)

/**
 * "20 kg × 10 @ 1 RIR, drop 15 kg × 8, drop 10 kg × 6": the set as any set reads, then what was added to it, every
 * stage in the unit it was logged in.
 */
internal fun groupText(group: WorkEntry.Group, exercise: Exercise): String =
    setText(group.mainSet, exercise) +
        group.followUps.joinToString("") { ", ${followUpLabel(group.group.method).lowercase()} ${setText(it, exercise)}" } +
        if (group.open) " (in progress)" else ""

/** Spoken form: one set, then its drops as part of it, never a run of separate sets. */
internal fun groupDescription(label: String, group: WorkEntry.Group, exercise: Exercise): String {
    val noun = followUpLabel(group.group.method).lowercase()
    val after = group.followUps
    return "$label: ${setText(group.mainSet, exercise).spokenUnits()}" +
        (if (after.isEmpty()) "" else ", with ${countLabel(after.size, noun)}: " +
            after.joinToString("; ") { setText(it, exercise).spokenUnits() }) +
        (if (group.open) ". In progress" else "") + "."
}

internal fun methodHelp(method: SetMethod?): String = when (method) {
    null -> "One set, logged once"
    SetMethod.DROP -> "Lower the load and keep going"
    SetMethod.REST_PAUSE -> "Pause briefly, then more reps"
}

private val weekDayFormat: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

/** "Mon 21 Sep". */
internal fun shortDate(date: LocalDate): String = weekDayFormat.format(date)

/** "week of Mon 21 Sep". */
internal fun weekOf(start: LocalDate): String = "week of ${shortDate(start)}"

/**
 * A set with drops (or rest-pause stages): the set itself first, exactly as a normal set reads (load × reps, then RIR),
 * then each drop beneath it after a "↳", so it reads as part of that set and never as a set of its own. [muted] is the
 * previous-workout style. The caller provides the spoken description.
 */
@Composable
internal fun SetWithFollowUps(group: WorkEntry.Group, exercise: Exercise, muted: Boolean, modifier: Modifier = Modifier,
    note: String = "") {
    val colors = MaterialTheme.colorScheme
    val main = group.mainSet
    val valueStyle = if (muted) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
    Column(modifier) {
        Text(loadRepsText(main, exercise), style = valueStyle.copy(fontFeatureSettings = "tnum"),
            fontWeight = if (muted) FontWeight.Normal else FontWeight.SemiBold,
            color = if (muted) colors.onSurfaceVariant else colors.onSurface)
        Text(rirShort(main) + note + if (group.open) " · ${followUpLabel(group.group.method).lowercase()} in progress" else "",
            style = MaterialTheme.typography.bodySmall,
            color = if (group.open && !muted) colors.primary else colors.onSurfaceVariant)
        group.followUps.forEach { stage ->
            // Laid out like the set above it: the value, then its RIR beneath, indented after the "↳".
            Column(Modifier.padding(top = 4.dp).testTag("follow-up")) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = if (muted) colors.onSurfaceVariant else colors.primary, fontWeight = FontWeight.Medium)) {
                            append("↳ ${followUpLabel(group.group.method)} ")
                        }
                        append(followUpText(stage, main, exercise))
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = if (muted) FontWeight.Normal else FontWeight.Medium,
                    color = if (muted) colors.onSurfaceVariant else colors.onSurface
                )
                if (stage.rir != null) Text(rirShort(stage), style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp))
            }
        }
    }
}

/**
 * Today's set with drops, in the ledger or a finished workout: one row like any other set (pencil and all) that opens
 * the set and its drops for editing.
 */
@Composable
internal fun GroupRow(
    group: WorkEntry.Group,
    label: String,
    exercise: Exercise,
    extra: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .then(if (group.open) Modifier.border(1.dp, colors.primary, shape) else Modifier)
            .clickable(onClickLabel = "Edit $label") { onOpen() }
            .semantics(mergeDescendants = true) {
                contentDescription = groupDescription(label, group, exercise) + if (extra) " Extra set." else ""
            }
            .testTag("group-row")
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SetWithFollowUps(group, exercise, muted = false, modifier = Modifier.weight(1f), note = if (extra) " · extra" else "")
        RebuildIcon(R.drawable.ic_pencil, contentDescription = null, tint = colors.onSurfaceVariant,
            modifier = Modifier.size(18.dp))
    }
}

/**
 * Normal or rest-pause for the next entry, plus adding a drop to the set just logged. Shows the current type, [named] where it doesn't sit beside the
 * set it describes; the menu explains each in a line.
 */
@Composable
internal fun SetTypeButton(
    method: SetMethod?,
    enabled: Boolean,
    onSelect: (SetMethod?) -> Unit,
    named: Boolean = false,
    /** The set a drop would join right now ("set 2"), or null when there is none; offered first in the menu. */
    dropOnto: Int? = null,
    dropping: Boolean = false,
    onDrop: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val current = when {
        dropping && dropOnto != null -> "Drop on set $dropOnto"
        else -> method?.title ?: "Normal"
    }
    Box {
        TextButton(
            onClick = { expanded = true },
            enabled = enabled,
            // The default type is quiet; amber marks a chosen method or a drop.
            colors = if (method == null && !dropping) ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant) else ButtonDefaults.textButtonColors(),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("set-type")
                .semantics {
                    contentDescription = "Set type: $current"
                    role = Role.DropdownList
                }
        ) {
            Text((if (named) "Set type: " else "") + current, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            RebuildIcon(R.drawable.ic_chevron_down, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // The common case after a heavy set: keep going lighter as part of that same set.
            if (dropOnto != null) {
                DropdownMenuItem(
                    text = {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text("Add drop to set $dropOnto", style = MaterialTheme.typography.bodyLarge)
                            Text("Part of set $dropOnto, not a new set", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    leadingIcon = {
                        if (dropping) RebuildIcon(R.drawable.ic_check, contentDescription = "Selected")
                        else Spacer(Modifier.size(24.dp))
                    },
                    onClick = {
                        expanded = false
                        onDrop()
                    },
                    modifier = Modifier.heightIn(min = 56.dp).testTag("set-type-add-drop")
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            // No "Drop set" here: a drop is never a set of its own, only something added to a set ("Add drop" above).
            // Rest-pause stays, because a rest-pause set really is one set.
            listOf<SetMethod?>(null, SetMethod.REST_PAUSE).forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(option?.let { "New ${it.title.lowercase()}" } ?: "Normal set",
                                style = MaterialTheme.typography.bodyLarge)
                            Text(methodHelp(option), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    leadingIcon = {
                        if (option == method && !dropping) RebuildIcon(R.drawable.ic_check, contentDescription = "Selected")
                        else Spacer(Modifier.size(24.dp))
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    modifier = Modifier.heightIn(min = 56.dp).testTag("set-type-${option?.name?.lowercase() ?: "normal"}")
                )
            }
        }
    }
}

/**
 * Shown in place of another exercise's entry while a drop or rest-pause set is open: new sets start after it,
 * so the way back to it, or closing it with the stages already logged, is one tap.
 */
@Composable
internal fun OpenGroupElsewhere(env: AppEnv, workout: Workout, other: WorkoutExercise, onGo: () -> Unit) {
    val group = other.setGroups.first { it.finishedAt == null }
    val stages = other.stages(group.id).size
    var error by rememberSaveable(group.id) { mutableStateOf<String?>(null) }
    Column(Modifier.testTag("open-group-elsewhere"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "A ${group.method.title.lowercase()} on ${other.exercise.name} is in progress with " +
                "${countLabel(stages, "stage")} logged. Finish it before logging here.",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = onGo, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("go-to-open-group")) {
            Text("Go to ${other.exercise.name}", maxLines = 2)
        }
        OutlinedButton(
            onClick = {
                error = null
                env.runner.run(Action.FinishGroup(workout.id, other.id, group.id, env.clock()), onError = { error = it })
            },
            enabled = !env.runner.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("finish-open-group")
        ) { Text("Finish that set") }
        ErrorText(error)
    }
}

/**
 * One drop or rest-pause set: every stage as logged, each correctable on its own, a missed stage, its type,
 * Finish set (active workout only) and deleting the whole set. Only Finish set can start a rest.
 */
@Composable
internal fun GroupDialog(env: AppEnv, workout: Workout, we: WorkoutExercise, groupId: String, onDismiss: () -> Unit) {
    val entries = we.workEntries()
    val index = entries.indexOfFirst { it is WorkEntry.Group && it.group.id == groupId }
    val entry = entries.getOrNull(index) as? WorkEntry.Group
    // Deleted or replaced underneath (last stage deleted, a restore): nothing is left to show.
    LaunchedEffect(entry == null) { if (entry == null) onDismiss() }
    if (entry == null) return
    val group = entry.group
    val exercise = we.exercise
    val live = env.state.activeWorkoutId == workout.id && workout.finishedAt == null
    var editingStage by rememberSaveable(groupId) { mutableStateOf<String?>(null) }
    var addingStageId by rememberSaveable(groupId) { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable(groupId) { mutableStateOf(false) }
    var error by rememberSaveable(groupId) { mutableStateOf<String?>(null) }

    val stage = entry.stages.find { it.id == editingStage }
    if (stage != null || addingStageId != null) {
        SetEditDialog(
            env, workout.id, we, stage,
            newSetTime = if (live) env.clock() else workout.finishedAt ?: env.clock(),
            newSetId = addingStageId,
            groupId = groupId,
            onDismiss = { editingStage = null; addingStageId = null }
        )
        return
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this ${group.method.title.lowercase()}?",
            text = "${groupText(entry, exercise)} will be removed from ${exercise.name}, all " +
                "${countLabel(entry.stages.size, "stage")}. Other sets keep their values and times. This can't be undone.",
            confirmLabel = "Delete set",
            destructive = true,
            busy = env.runner.busy,
            error = error,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                error = null
                env.runner.run(Action.DeleteGroup(workout.id, we.id, groupId), onError = { error = it }) {
                    confirmDelete = false
                    onDismiss()
                }
            }
        )
        return
    }

    AdaptiveDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set ${index + 1} and its ${followUpLabel(group.method).lowercase()}s") },
        text = {
            Column(Modifier.testTag("group-stages")) {
                val noun = followUpLabel(group.method)
                entry.stages.forEachIndexed { i, set ->
                    // The set itself, then its drops (or pauses), numbered from 1.
                    val name = if (i == 0) "Set" else "$noun $i"
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(onClickLabel = "Edit ${name.lowercase()}") { editingStage = set.id }
                            .semantics(mergeDescendants = true) {
                                contentDescription = "$name: ${setText(set, exercise).spokenUnits()}"
                            }
                            .testTag("stage-row")
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(if (i == 0) "Set" else "↳ $noun $i", style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            color = if (i == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(72.dp))
                        Text(setText(set, exercise), style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                            modifier = Modifier.weight(1f))
                        RebuildIcon(R.drawable.ic_pencil, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (entry.stages.size < MAX_GROUP_STAGES) {
                SecondaryTextButton(onClick = { addingStageId = newId() }, enabled = !env.runner.busy,
                    icon = R.drawable.ic_plus, modifier = Modifier.testTag("add-missed-stage")) {
                    Text("Add a missed ${followUpLabel(group.method).lowercase()}")
                }
            } else {
                Text("This set has the most it can hold: itself and ${MAX_GROUP_STAGES - 1} more.", style = MaterialTheme.typography.bodySmall)
            }
            val before = entries.getOrNull(index - 1)
            val joinable = group.method == SetMethod.DROP && group.finishedAt != null && before != null &&
                before.unit == entry.unit && (before is WorkEntry.Single ||
                    (before is WorkEntry.Group && before.group.method == SetMethod.DROP && !before.open))
            if (joinable) {
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Were these drops off set $index?", style = MaterialTheme.typography.titleSmall)
                    Text("Make them part of set $index. Set ${index + 1} stops counting as a working set; every value is kept.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(
                        onClick = {
                            error = null
                            env.runner.run(Action.JoinDropsToSet(workout.id, we.id, groupId), onError = { error = it }) {
                                onDismiss()
                            }
                        },
                        enabled = !env.runner.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("join-drops")
                    ) { Text("Make them drops of set $index") }
                }
            }
            HorizontalDivider()
            SectionHeader("Type")
            Column(Modifier.selectableGroup()) {
                SetMethod.entries.forEach { method ->
                    RadioRow(
                        method.title, group.method == method,
                        onClick = {
                            if (group.method != method) {
                                error = null
                                env.runner.run(Action.SetGroupMethod(workout.id, we.id, groupId, method), onError = { error = it })
                            }
                        },
                        enabled = !env.runner.busy,
                        supporting = methodHelp(method)
                    )
                }
            }
            ErrorText(error)
            if (live && group.finishedAt == null) {
                Button(
                    onClick = {
                        error = null
                        env.runner.run(Action.FinishGroup(workout.id, we.id, groupId, env.clock()), onError = { error = it }) {
                            onDismiss()
                        }
                    },
                    enabled = !env.runner.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("group-finish")
                ) { Text("Finish set") }
            }
            TextButton(
                onClick = { confirmDelete = true },
                enabled = !env.runner.busy,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text("Delete this set") }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("group-done")) { Text("Done") } }
    )
}

/**
 * Whether a finished workout counts for its week. Meeting every saved target counts by itself; a partial workout
 * with real working sets, on any of its exercises, counts only by explicit choice, and an empty one can't count.
 * An extra workout is outside the plan and never counts.
 */
@Composable
internal fun WeekCredit(env: AppEnv, workout: Workout) {
    if (workout.finishedAt == null) return
    var error by rememberSaveable(workout.id) { mutableStateOf<String?>(null) }
    val week = weekOf(workout.weekStart())
    val offWeek = !workout.extra && env.state.scheduledRoutine(workout.phase, workout.templateId) == null
    // A scheduled copy taken off the week never comes back, so new credit for it could never show.
    val retired = (env.state.occurrenceOf(workout) as? RoutinePlacement)?.removedFromPlan == true
    Column(Modifier.testTag("week-credit"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (offWeek) Text(
            "${workout.name} is no longer on the ${phaseShort(workout.phase)} week",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("week-credit-off-week")
        )
        when {
            workout.extra -> Text(
                "Outside the plan",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            workout.completesRoutine() -> Text(
                when {
                    !offWeek -> "Every target met · counts for its $week"
                    retired -> "Every target met"
                    else -> "Every target met · counts again if ${workout.name} goes back on the week"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            retired && !workout.countedForWeek -> Unit
            workout.hasWorkingEffort() -> {
                CheckRow(
                    "Count as ${workout.name} done for its $week",
                    workout.countedForWeek,
                    { counted ->
                        error = null
                        env.runner.run(Action.SetWeekCredit(workout.id, counted), onError = { error = it })
                    },
                    enabled = !env.runner.busy,
                    supporting = "Partial: not every target was met",
                    modifier = Modifier.testTag("week-credit-toggle")
                )
                ErrorText(error)
            }
            else -> Text(
                "No working sets logged",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
