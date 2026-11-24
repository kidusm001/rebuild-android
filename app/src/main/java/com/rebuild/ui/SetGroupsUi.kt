package com.rebuild.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.IconButton
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import com.rebuild.ui.theme.SignalAmber
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
        exercise.loading == LoadingType.EXTERNAL) "${formatKg(stage.amount!!)} ×\u00A0${stage.reps}"
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
    val valueStyle = if (muted) MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum") else ledgerValueStyle()
    val accent = accentText()
    Column(modifier) {
        FitText(loadRepsText(main, exercise), style = valueStyle,
            fontWeight = if (muted) FontWeight.Normal else FontWeight.Bold,
            color = if (muted) colors.onSurfaceVariant else colors.onSurface)
        Text(rirShort(main) + note + if (group.open) " · ${followUpLabel(group.group.method).lowercase()} in progress" else "",
            style = MaterialTheme.typography.bodySmall,
            color = if (group.open && !muted) accent else colors.onSurfaceVariant)
        group.followUps.forEach { stage ->
            // Laid out like the set above it: the value, then its RIR beneath, indented after the "↳".
            Column(Modifier.padding(top = 4.dp).testTag("follow-up")) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = if (muted) colors.onSurfaceVariant else accent, fontWeight = FontWeight.SemiBold)) {
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
    val shape = MaterialTheme.shapes.small
    // The same row as a single set: card tone, rounded, pencil in a circle. An open set adds an amber ring.
    Row(
        modifier
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(colors.surfaceContainerLowest)
            .then(if (group.open) Modifier.border(2.dp, SignalAmber, shape) else Modifier) // a ring: full signal amber
            .clickable(onClickLabel = "Edit $label") { onOpen() }
            .semantics(mergeDescendants = true) {
                contentDescription = groupDescription(label, group, exercise) + if (extra) " Extra set." else ""
            }
            .testTag("group-row")
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        // A gap before the pencil: the value fits (or shrinks) beside it, never under it.
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SetWithFollowUps(group, exercise, muted = false, modifier = Modifier.weight(1f), note = if (extra) " · extra" else "")
        LoggedRowEditMark()
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
            enabled = !env.runner.looksBusy(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("finish-open-group")
        ) { Text("Finish that set") }
        ErrorText(error)
    }
}

/**
 * One drop or rest-pause set: every stage as logged, each correctable on its own, a missed stage, its type,
 * Finish set (active workout only) and deleting the whole set. Only Finish set can start a rest.
 */
@OptIn(ExperimentalLayoutApi::class)
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
            busy = env.runner.looksBusy(),
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

    val noun = followUpLabel(group.method)
    val before = entries.getOrNull(index - 1)
    val joinable = group.method == SetMethod.DROP && group.finishedAt != null && before != null &&
        before.unit == entry.unit && (before is WorkEntry.Single ||
            (before is WorkEntry.Group && before.group.method == SetMethod.DROP && !before.open))
    val finishable = live && group.finishedAt == null
    var menu by remember { mutableStateOf(false) }

    // The dialog's job is the set and its drops: reading them, fixing one, adding a forgotten one. The rare repairs
    // (joining onto the set before, deleting) live in the title's menu, away from the thumb and from the confirm.
    AdaptiveDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.Top) {
                Text("Set ${index + 1} and its ${noun.lowercase()}s", modifier = Modifier.weight(1f).padding(top = 6.dp))
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp).testTag("group-more")) {
                        RebuildIcon(R.drawable.ic_ellipsis_vertical, contentDescription = "More set actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (joinable) DropdownMenuItem(
                            text = {
                                Column(Modifier.padding(vertical = 4.dp)) {
                                    Text("Make them drops of set $index", style = MaterialTheme.typography.bodyLarge)
                                    Text("Set ${index + 1} stops counting as a working set; every value is kept",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            onClick = {
                                menu = false
                                error = null
                                env.runner.run(Action.JoinDropsToSet(workout.id, we.id, groupId), onError = { error = it }) {
                                    onDismiss()
                                }
                            },
                            enabled = !env.runner.looksBusy(),
                            modifier = Modifier.heightIn(min = 56.dp).testTag("join-drops")
                        )
                        DropdownMenuItem(
                            text = { Text("Delete this set", color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; confirmDelete = true },
                            enabled = !env.runner.busy,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("group-delete")
                        )
                    }
                }
            }
        },
        text = {
            // Said here as in the ledger, so an open set never reads as a finished one.
            if (group.finishedAt == null) Text("In progress", style = MaterialTheme.typography.labelLarge,
                color = accentText(), modifier = Modifier.testTag("group-in-progress"))
            // One grouped list, separated by tone: the set, then its drops (or pauses) numbered from 1.
            Column(Modifier.testTag("group-stages"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                entry.stages.forEachIndexed { i, set ->
                    val name = if (i == 0) "Set" else "$noun $i"
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clip(groupShape(i, entry.stages.lastIndex))
                            .background(tonalFill())
                            .clickable(onClickLabel = "Edit ${name.lowercase()}") { editingStage = set.id }
                            .semantics(mergeDescendants = true) {
                                contentDescription = "$name: ${setText(set, exercise).spokenUnits()}"
                            }
                            .testTag("stage-row")
                            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(if (i == 0) "Set" else "↳ $name", style = MaterialTheme.typography.labelLarge,
                                color = if (i == 0) MaterialTheme.colorScheme.onSurfaceVariant else accentText())
                            // The value never breaks inside itself: if load × reps and the RIR don't fit on one line,
                            // the RIR moves down whole.
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                itemVerticalAlignment = Alignment.CenterVertically) {
                                // A non-breaking space keeps "× 9" whole, so large text breaks before it, not inside it.
                                Text(loadRepsText(set, exercise).replace(" × ", " ×\u00A0"), style = ledgerValueStyle(),
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(rirShort(set), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                            }
                        }
                        LoggedRowEditMark()
                    }
                }
            }
            if (entry.stages.size < MAX_GROUP_STAGES) {
                SecondaryButton(onClick = { addingStageId = newId() }, enabled = !env.runner.busy,
                    icon = R.drawable.ic_plus, modifier = Modifier.fillMaxWidth().testTag("add-missed-stage")) {
                    Text("Add a ${noun.lowercase()}")
                }
            } else {
                Text("This set has the most it can hold: itself and ${MAX_GROUP_STAGES - 1} more.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Drop set or rest-pause: a pill choice like History's tabs, applied at once. The line under it
            // describes the chosen type and is spoken when it changes, so the switch is never silent.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Type", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() })
                PillChoice(
                    options = SetMethod.entries,
                    selected = group.method,
                    onSelect = { method ->
                        error = null
                        env.runner.run(Action.SetGroupMethod(workout.id, we.id, groupId, method), onError = { error = it })
                    },
                    // A zero-width space after the hyphen is the only place a large label may break.
                    label = { it.title.replace("-", "-\u200B") },
                    enabled = !env.runner.busy,
                    tag = { "group-method-${it.name.lowercase()}" }
                )
                Text(methodHelp(group.method), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            ErrorText(error)
        },
        // One amber action in thumb reach: Finish set while the set is still going, otherwise Done.
        confirmButton = {
            if (finishable) TextButton(
                onClick = {
                    error = null
                    env.runner.run(Action.FinishGroup(workout.id, we.id, groupId, env.clock()), onError = { error = it }) {
                        onDismiss()
                    }
                },
                enabled = !env.runner.looksBusy(),
                modifier = Modifier.testTag("group-finish")
            ) { Text("Finish set") }
            else TextButton(onClick = onDismiss, modifier = Modifier.testTag("group-done")) { Text("Done") }
        },
        dismissButton = {
            if (finishable) TextButton(onClick = onDismiss, modifier = Modifier.testTag("group-done")) { Text("Close") }
        }
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
                    enabled = !env.runner.looksBusy(),
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
