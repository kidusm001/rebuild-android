package com.rebuild.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rebuild.R
import com.rebuild.domain.Action
import com.rebuild.domain.Measure
import com.rebuild.domain.measureOf
import com.rebuild.domain.Phase
import com.rebuild.domain.PlanSlot
import com.rebuild.domain.PlannedExercise
import com.rebuild.domain.RoutinePlacement
import com.rebuild.domain.ScheduledRoutine
import com.rebuild.domain.WorkoutTemplate
import com.rebuild.domain.availableTemplates
import com.rebuild.domain.localDateOf
import com.rebuild.domain.plannedExercises
import com.rebuild.domain.removedSlots
import com.rebuild.domain.removedTemplates
import com.rebuild.domain.scheduledRoutines
import com.rebuild.domain.targetFor
import com.rebuild.domain.weekChecklist

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlanScreen(env: AppEnv, onNavigate: (String) -> Unit) {
    val state = env.state
    val current = state.phase ?: return
    var viewPhase by rememberSaveable { mutableStateOf(current) }
    var editingSlot by rememberSaveable { mutableStateOf<String?>(null) }
    var editingAddition by rememberSaveable { mutableStateOf<String?>(null) }
    // Adding to a routine: first the routine whose picker is open, then the exercise chosen for its targets.
    var addingTo by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }
    var restoreError by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }
    // The week itself: one occurrence moved, updated or taken off, a library routine added, an original put back.
    var moving by rememberSaveable { mutableStateOf<String?>(null) }
    var updating by rememberSaveable { mutableStateOf<String?>(null) }
    var takingOff by rememberSaveable { mutableStateOf<String?>(null) }
    var reordering by rememberSaveable { mutableStateOf<String?>(null) }
    var choosingRoutine by rememberSaveable { mutableStateOf(false) }
    var scheduling by rememberSaveable { mutableStateOf<String?>(null) }
    var puttingBack by rememberSaveable { mutableStateOf<String?>(null) }
    var weekError by rememberSaveable { mutableStateOf<String?>(null) }
    val week = state.scheduledRoutines(viewPhase)
    val slots = week.filterIsInstance<WorkoutTemplate>().flatMap { it.slots }
    val takenOff = state.removedTemplates(viewPhase)

    // The chips scroll so the phase being viewed is always in sight, even at large text.
    val chips = rememberLazyListState()
    LaunchedEffect(viewPhase) { chips.animateScrollToItem(Phase.entries.indexOf(viewPhase)) }
    ScreenScaffold(title = "Plan") {
        LazyRow(
            state = chips,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(Phase.entries) { phase ->
                FilterChip(
                    selected = phase == viewPhase,
                    onClick = {
                        viewPhase = phase
                        moving = null; updating = null; takingOff = null; scheduling = null; puttingBack = null
                        weekError = null
                    },
                    label = { Text(phaseShort(phase)) },
                    leadingIcon = if (phase == current) { { CurrentPhaseMark() } } else null,
                    // The chip keeps Material's 48 dp touch target around its 32 dp body.
                    modifier = Modifier.semantics {
                        if (phase == current) contentDescription = "${phaseShort(phase)}, current phase"
                    }
                )
            }
        }
        ScrollBody {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (viewPhase == current) "Current phase" else "Not your current phase",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).testTag("plan-phase-status")
                    )
                    TextButton(
                        onClick = { onNavigate(Routes.PHASE) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("plan-change-phase")
                            .semantics { contentDescription = "Change current phase" }
                    ) { Text("Change") }
                }
                Text(viewPhase.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(phasePrescription(viewPhase), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryTextButton(onClick = { onNavigate(Routes.ROUTINES) }, icon = R.drawable.ic_library,
                    modifier = Modifier.testTag("plan-routines")) { Text("Your routines") }
                SecondaryTextButton(onClick = { onNavigate(Routes.EXERCISES) }, icon = R.drawable.ic_search) {
                    Text("Browse exercises")
                }
            }

            // Weekly totals are working sets of reps; timed, distance and cardio goals are listed with each routine.
            val targetTotal = week.sumOf { t ->
                state.plannedExercises(t, viewPhase).filter { state.measureOf(it.exerciseId) == Measure.REPS }
                    .sumOf { it.sets ?: 0 }
            }
            val establishedSlots = state.availableTemplates(Phase.BASELINE).flatMap { it.slots }
            val baselineTotal = establishedSlots.sumOf { it.baselineSets }
            val upperTotal = establishedSlots.sumOf { it.upperSets ?: it.baselineSets }
            WeeklyTotals(targetTotal, baselineTotal, upperTotal)

            // The weekly checklist is the current phase's; another phase's plan shows no this-week status.
            val weekStatus = if (viewPhase == current) state.weekChecklist(localDateOf(env.clock())) else null
            week.forEach { occurrence ->
                HorizontalDivider()
                val planned = state.plannedExercises(occurrence, viewPhase)
                OccurrenceHeader(
                    env, occurrence, week, planned, weekStatus?.statusOf(occurrence.id),
                    onEdit = { onNavigate(Routes.editPlacement(occurrence.id)) },
                    onMove = { weekError = null; moving = occurrence.id },
                    onUpdate = { weekError = null; updating = occurrence.id },
                    onTakeOff = { weekError = null; takingOff = occurrence.id },
                    onReorder = { weekError = null; reordering = occurrence.id }
                )
                if (occurrence is RoutinePlacement) Text(
                    copyNote(state, occurrence),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("plan-copy-note-${occurrence.id}")
                )
                Column {
                    planned.forEachIndexed { i, p ->
                        val name = env.exercise(p.exerciseId)?.name ?: p.exerciseId
                        val (reps, spoken) = plannedTargetLines(state.measureOf(p.exerciseId), p)
                        val slot = p.slot
                        val addition = p.addition
                        if (slot != null) {
                            val limits = "baseline ${slot.baselineSets}" + (slot.upperSets?.let { " · max $it" } ?: "")
                            ListRow(
                                headline = name,
                                modifier = Modifier.testTag("plan-slot-${slot.id}"),
                                supporting = "$reps\n${slot.muscle} · $limits",
                                label = "$name, $spoken. ${slot.muscle}, baseline ${slot.baselineSets}" +
                                    (slot.upperSets?.let { ", advisory upper $it" } ?: ""),
                                onClick = { editingSlot = slot.id },
                                leading = { OrderBadge(i + 1) },
                                trailing = { EditMark() }
                            )
                        } else if (addition != null) {
                            ListRow(
                                headline = name,
                                modifier = Modifier.testTag("plan-addition-${addition.id}"),
                                supporting = if (occurrence is WorkoutTemplate) "$reps\nYour exercise in this phase" else reps,
                                label = "$name, $spoken" + if (occurrence is WorkoutTemplate) ". Your exercise in this phase" else "",
                                onClick = { editingAddition = addition.id },
                                leading = { OrderBadge(i + 1) },
                                trailing = { EditMark() }
                            )
                        }
                    }
                    val removed = if (occurrence is WorkoutTemplate) state.removedSlots(occurrence, viewPhase) else emptyList()
                    if (removed.isNotEmpty()) {
                        Text(
                            "Removed from future ${phaseShort(viewPhase)} workouts",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        removed.forEach { slot ->
                            val name = state.targetFor(slot, viewPhase).exerciseId.let { env.exercise(it)?.name ?: it }
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("plan-removed-${slot.id}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(name, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                TextButton(
                                    onClick = {
                                        restoreError = null
                                        env.runner.run(Action.SetPlanSlotRemoved(viewPhase, slot.id, false),
                                            onError = { restoreError = occurrence.id to it })
                                    },
                                    enabled = !env.runner.busy,
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("plan-restore-${slot.id}")
                                        .semantics { contentDescription = "Restore $name" }
                                ) { Text("Restore") }
                            }
                        }
                    }
                    restoreError?.takeIf { it.first == occurrence.id }?.let { ErrorText(it.second) }
                    SecondaryTextButton(
                        onClick = { addingTo = occurrence.id },
                        enabled = !env.runner.busy,
                        icon = R.drawable.ic_plus,
                        modifier = Modifier.testTag("plan-add-${occurrence.id}")
                    ) { Text("Add exercise to ${occurrence.name}") }
                }
            }

            HorizontalDivider()
            ErrorText(weekError)
            OutlinedButton(
                onClick = { weekError = null; choosingRoutine = true },
                enabled = !env.runner.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("plan-add-routine")
            ) {
                RebuildIcon(R.drawable.ic_calendar_plus, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add a routine to the ${phaseShort(viewPhase)} week")
            }
            if (takenOff.isNotEmpty()) {
                SectionHeader("Taken off the ${phaseShort(viewPhase)} week")
                Column {
                    takenOff.forEach { template ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("plan-taken-off-${template.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                                Text(template.name, style = MaterialTheme.typography.bodyLarge)
                                Text(weekdayName(template.weekday), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(
                                onClick = {
                                    weekError = null
                                    if (week.none { it.weekday == template.weekday }) {
                                        env.runner.run(Action.RestoreToWeeklyPlan(viewPhase, template.id),
                                            onError = { weekError = it })
                                    } else puttingBack = template.id
                                },
                                enabled = !env.runner.busy,
                                modifier = Modifier.heightIn(min = 48.dp).testTag("plan-put-back-${template.id}")
                                    .semantics { contentDescription = "Put ${template.name} back on ${weekdayName(template.weekday)}" }
                            ) { Text("Put back") }
                        }
                    }
                }
            }
        }
    }

    val slot = slots.find { it.id == editingSlot }
    if (slot != null) PlanSlotDialog(env, viewPhase, slot, onDismiss = { editingSlot = null })
    val addition = state.planAdditions.find { it.id == editingAddition && it.phase == viewPhase }
    val additionOwner = addition?.let { a -> week.find { it.id == a.templateId } }
    if (addition != null && additionOwner != null) {
        PlanAdditionDialog(env, viewPhase, additionOwner, addition.exerciseId, existing = addition, source = null,
            onDismiss = { editingAddition = null })
    }
    val pickingFor = week.find { it.id == addingTo }
    if (pickingFor != null) {
        val planned = state.plannedExercises(pickingFor, viewPhase)
        ExerciseLibraryPicker(
            env,
            onDismiss = { addingTo = null },
            title = "Add to ${pickingFor.name}",
            marked = planned.map { it.exerciseId }.toSet(),
            markedLabel = "in this routine"
        ) { exercise ->
            addingTo = null
            // One already in the routine opens for editing instead of being added twice.
            val existing = planned.find { it.exerciseId == exercise.id }
            when {
                existing?.slot != null -> editingSlot = existing.slot.id
                existing?.addition != null -> editingAddition = existing.addition.id
                else -> adding = pickingFor.id to exercise.id
            }
        }
    }
    val (addTemplateId, addExerciseId) = adding ?: (null to null)
    val addTemplate = week.find { it.id == addTemplateId }
    if (addTemplate != null && addExerciseId != null) {
        PlanAdditionDialog(env, viewPhase, addTemplate, addExerciseId, existing = null, source = null,
            onDismiss = { adding = null })
    }
    (week.find { it.id == moving } as? RoutinePlacement)?.let {
        MovePlacementDialog(env, it, onDismiss = { moving = null })
    }
    (week.find { it.id == updating } as? RoutinePlacement)?.let {
        UpdateCopyDialog(env, it, onDismiss = { updating = null })
    }
    week.find { it.id == takingOff }?.let { TakeOffDialog(env, viewPhase, it, onDismiss = { takingOff = null }) }
    week.find { it.id == reordering }?.let { PlanReorderDialog(env, viewPhase, it, onDismiss = { reordering = null }) }
    if (choosingRoutine) ChooseRoutineDialog(
        env, viewPhase,
        onDismiss = { choosingRoutine = false },
        onNew = { choosingRoutine = false; onNavigate(Routes.NEW_ROUTINE) }
    ) { routine ->
        choosingRoutine = false
        scheduling = routine.id
    }
    state.routines.find { it.id == scheduling }?.let {
        ScheduleRoutineDialog(env, it, lockedPhase = viewPhase, onDismiss = { scheduling = null })
    }
    takenOff.find { it.id == puttingBack }?.let {
        RestoreTemplateDialog(env, viewPhase, it, onDismiss = { puttingBack = null })
    }
}

/** The order future workouts of [routine] in [phase] list their exercises. Started workouts keep their own. */
@Composable
private fun PlanReorderDialog(env: AppEnv, phase: Phase, routine: ScheduledRoutine, onDismiss: () -> Unit) {
    var error by rememberSaveable(routine.id) { mutableStateOf<String?>(null) }
    val planned = env.state.plannedExercises(routine, phase)
    fun move(slotId: String, to: Int) {
        error = null
        env.runner.run(Action.MovePlannedExercise(phase, routine.id, slotId, to), onError = { error = it })
    }
    AdaptiveDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reorder ${routine.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.testTag("plan-reorder-list")) {
                planned.forEachIndexed { index, p ->
                    val name = env.exercise(p.exerciseId)?.name ?: p.exerciseId
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("plan-reorder-row-$index"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OrderBadge(index + 1)
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 2,
                                overflow = TextOverflow.Ellipsis)
                            Text(plannedTargetLines(env.state.measureOf(p.exerciseId), p).first,
                                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { move(p.slotId, index - 1) }, enabled = index > 0 && !env.runner.busy,
                            modifier = Modifier.testTag("plan-move-up-$index")) {
                            RebuildIcon(R.drawable.ic_arrow_up, contentDescription = "Move $name up")
                        }
                        IconButton(onClick = { move(p.slotId, index + 1) },
                            enabled = index < planned.lastIndex && !env.runner.busy,
                            modifier = Modifier.testTag("plan-move-down-$index")) {
                            RebuildIcon(R.drawable.ic_arrow_down, contentDescription = "Move $name down")
                        }
                    }
                }
            }
            ErrorText(error)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("plan-reorder-done")) { Text("Done") } }
    )
}

/**
 * A planned exercise's target as one scannable line, "2 × 8–12 · rest 3:00", and the same in words for its row label.
 */
private fun plannedTargetLines(measure: Measure, p: PlannedExercise): Pair<String, String> {
    val line = compactTargetText(measure, p.sets, p.minReps, p.maxReps, p.perLeg, p.restSeconds, p.goal)
    val spoken = spokenTarget(measure, p.sets, p.minReps, p.maxReps, p.perLeg, p.goal)
        ?.let { "$it, ${restText(measure, p.restSeconds)}" } ?: line
    return line to spoken
}

/** Marks the current phase on its chip; the chip's label says so in words. */
@Composable
internal fun CurrentPhaseMark() {
    Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
}

@Composable
private fun EditMark() {
    RebuildIcon(R.drawable.ic_pencil, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp))
}

/**
 * The week's working sets as a small ledger. Only this phase's target is something to do; the other two lines
 * are reference points, and the advisory upper is a ceiling rather than a goal.
 */
@Composable
private fun WeeklyTotals(target: Int, baseline: Int, upper: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Weekly working sets")
        Column(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            TotalLine("This phase", target, strong = true)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TotalLine("Established baseline", baseline)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TotalLine("Advisory upper", upper)
        }
    }
}

@Composable
private fun TotalLine(label: String, sets: Int, strong: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
            color = if (strong) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "$sets",
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
            color = if (strong) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlanSlotDialog(env: AppEnv, phase: Phase, slot: PlanSlot, onDismiss: () -> Unit) {
    val target = env.state.targetFor(slot, phase)
    val key = "${phase.name}|${slot.id}|$target"
    var exerciseId by rememberSaveable(key) { mutableStateOf(target.exerciseId) }
    var sets by rememberSaveable(key) { mutableStateOf(target.sets) }
    var rest by rememberSaveable(key) { mutableStateOf(target.restSeconds.toString()) }
    var allowAboveCap by rememberSaveable(key) { mutableStateOf(false) }
    var error by rememberSaveable(key) { mutableStateOf<String?>(null) }
    var confirmingRemove by rememberSaveable(key) { mutableStateOf(false) }
    val aboveCap = slot.upperSets != null && sets > slot.upperSets
    val options = (listOf(slot.exerciseId) + slot.alternatives + target.exerciseId +
        env.state.exercises.filter { it.custom }.map { it.id }).distinct().mapNotNull(env::exercise)
    val defaults = PlanDefaults(slot, phase)

    fun save() {
        val restSeconds = parseRest(rest)
        if (restSeconds == null) {
            error = "Rest must be between 1 and 3600 seconds."
            return
        }
        error = null
        env.runner.run(
            Action.EditPlan(phase, slot.id, exerciseId, sets, restSeconds, allowAboveCap && aboveCap),
            onError = { error = it }
        ) { onDismiss() }
    }

    AdaptiveDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit plan · ${phaseShort(phase)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TargetEditorFields(
                    env, options, exerciseId, { exerciseId = it }, null,
                    sets, { sets = it; if (!(slot.upperSets != null && it > slot.upperSets)) allowAboveCap = false },
                    rest, { rest = it }, slot.baselineSets, slot.upperSets, allowAboveCap, { allowAboveCap = it }
                )
                TextButton(onClick = {
                    exerciseId = slot.exerciseId
                    sets = defaults.sets
                    rest = slot.restSeconds.toString()
                    allowAboveCap = false
                }) { Text("Use phase defaults") }
                ErrorText(error)
                HorizontalDivider()
                TextButton(
                    onClick = { confirmingRemove = true },
                    enabled = !env.runner.busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("plan-remove-slot")
                ) { Text("Remove from future ${phaseShort(phase)} workouts") }
            }
        },
        confirmButton = {
            TextButton(onClick = ::save, enabled = !env.runner.busy && (!aboveCap || allowAboveCap)) { Text("Save to plan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (confirmingRemove) {
        val name = env.exercise(target.exerciseId)?.name ?: target.exerciseId
        var removeError by rememberSaveable(key) { mutableStateOf<String?>(null) }
        ConfirmDialog(
            title = "Remove $name?",
            text = "Future ${phaseShort(phase)} workouts won't include it. Started and finished workouts keep it with " +
                "everything logged, and you can restore it here.",
            confirmLabel = "Remove",
            busy = env.runner.busy,
            error = removeError,
            onDismiss = { confirmingRemove = false },
            onConfirm = {
                removeError = null
                env.runner.run(Action.SetPlanSlotRemoved(phase, slot.id, true), onError = { removeError = it }) {
                    confirmingRemove = false
                    onDismiss()
                }
            }
        )
    }
}

private class PlanDefaults(slot: PlanSlot, phase: Phase) {
    val sets: Int = when (phase) {
        Phase.INTRO_ONE -> 1
        Phase.INTRO_TWO, Phase.FIVE_TWO -> 2
        Phase.BASELINE -> slot.baselineSets
    }
}
