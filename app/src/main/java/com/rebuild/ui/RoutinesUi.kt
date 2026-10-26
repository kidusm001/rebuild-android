package com.rebuild.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.rebuild.R
import com.rebuild.domain.Action
import com.rebuild.domain.AppState
import com.rebuild.domain.CustomRoutine
import com.rebuild.domain.DraftExercise
import com.rebuild.domain.DraftProblem
import com.rebuild.domain.MAX_ROUTINES
import com.rebuild.domain.MAX_ROUTINE_NAME
import com.rebuild.domain.MAX_WORKOUT_EXERCISES
import com.rebuild.domain.Phase
import com.rebuild.domain.PlannedExercise
import com.rebuild.domain.RoutineDraft
import com.rebuild.domain.RoutineExercise
import com.rebuild.domain.RoutinePlacement
import com.rebuild.domain.RoutineStatus
import com.rebuild.domain.ScheduledRoutine
import com.rebuild.domain.WorkoutTemplate
import com.rebuild.domain.allLinkedPlacements
import com.rebuild.domain.asksPerLeg
import com.rebuild.domain.complete
import com.rebuild.domain.confirmedExercises
import com.rebuild.domain.draftProblems
import com.rebuild.domain.lastLoggedPerLeg
import com.rebuild.domain.libraryDiffers
import com.rebuild.domain.linkedPlacements
import com.rebuild.domain.localDateOf
import com.rebuild.domain.Measure
import com.rebuild.domain.MeasuredGoal
import com.rebuild.domain.measureOf
import com.rebuild.domain.measuredGoal
import com.rebuild.domain.perLegConvention
import com.rebuild.domain.restSuggestion
import com.rebuild.domain.scheduledRoutines
import com.rebuild.domain.weekChecklist
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.format.TextStyle

/** Shown for a workout whose routine has since been taken off its phase's week. */
internal const val OFF_WEEK_NOTE = "This routine is no longer on its phase's week, so this workout doesn't tick the " +
    "weekly checklist. Everything logged is kept."

private fun twinsOf(routine: ScheduledRoutine, week: List<ScheduledRoutine>) =
    week.filter { it.weekday == routine.weekday && it.name == routine.name }

/** "2 of 2 on Wednesday" when one day holds the same name more than once, else null. */
internal fun occurrenceOrdinal(routine: ScheduledRoutine, week: List<ScheduledRoutine>): String? {
    val twins = twinsOf(routine, week)
    if (twins.size < 2) return null
    return "${twins.indexOfFirst { it.id == routine.id } + 1} of ${twins.size} on ${weekdayName(routine.weekday)}"
}

/** The occurrence's day for labels: "Wednesday", or "Wednesday, 2 of 2" for a same-named one. */
internal fun occurrenceDay(routine: ScheduledRoutine, week: List<ScheduledRoutine>): String {
    val twins = twinsOf(routine, week)
    return weekdayName(routine.weekday) +
        if (twins.size > 1) ", ${twins.indexOfFirst { it.id == routine.id } + 1} of ${twins.size}" else ""
}

/** The occurrence's name, told apart from a same-named one on the same day. */
internal fun occurrenceName(routine: ScheduledRoutine, week: List<ScheduledRoutine>): String =
    routine.name + (occurrenceOrdinal(routine, week)?.let { " ($it)" } ?: "")

/** "5 exercises · 12 working sets": working sets are rep-based ones; timed, distance and cardio goals aren't counted. */
internal fun plannedSummary(state: AppState, planned: List<PlannedExercise>): String {
    val workingSets = planned.filter { state.measureOf(it.exerciseId) == Measure.REPS }.sumOf { it.sets ?: 0 }
    return countLabel(planned.size, "exercise") +
        (workingSets.takeIf { it > 0 }?.let { " · ${countLabel(it, "working set")}" } ?: "")
}

internal fun routineSummary(state: AppState, exercises: List<RoutineExercise>): String {
    val workingSets = exercises.filter { state.measureOf(it.exerciseId) == Measure.REPS }.sumOf { it.sets ?: 0 }
    return countLabel(exercises.size, "exercise") +
        (workingSets.takeIf { it > 0 }?.let { " · ${countLabel(it, "working set")}" } ?: "")
}

/**
 * A target as one scannable line for Plan and routine rows: "2 × 8–12 · rest 3:00", "3 × 0:30 · rest 1:00". Falls
 * back to the worded target when there isn't a compact one.
 */
internal fun compactTargetText(measure: Measure, sets: Int?, minReps: Int?, maxReps: Int?, perLeg: Boolean,
    restSeconds: Int, goal: MeasuredGoal): String =
    shortTarget(measure, sets, minReps, maxReps, perLeg, goal)?.let { "$it · ${restText(measure, restSeconds)}" }
        ?: targetsText(measure, sets, minReps, maxReps, perLeg, restSeconds, goal)

/** A routine row's target line, in the terms its kind of exercise uses. */
internal fun routineTargetsText(state: AppState, exercise: RoutineExercise): String =
    compactTargetText(state.measureOf(exercise.exerciseId), exercise.sets, exercise.minReps, exercise.maxReps,
        exercise.perLeg, exercise.restSeconds, exercise.measuredGoal)

/** Where a library routine's copies are scheduled, by phase: "Five days: Wed, Sat". */
private fun AppState.scheduleSummary(routineId: String): String {
    val copies = allLinkedPlacements(routineId)
    if (copies.isEmpty()) return "Not on a weekly plan"
    return Phase.entries.mapNotNull { phase ->
        copies.filter { it.phase == phase }.sortedBy { it.weekday }.takeIf { it.isNotEmpty() }?.let { list ->
            "${phaseShort(phase)}: " + list.joinToString { weekdayName(it.weekday, TextStyle.SHORT) }
        }
    }.joinToString(" · ")
}

private fun weekStatusText(status: RoutineStatus): String = when (status) {
    RoutineStatus.DONE -> "Done this week · a repeat doesn't count twice"
    RoutineStatus.IN_PROGRESS -> "In progress"
    RoutineStatus.PARTIAL -> "Partial this week · not counted"
    RoutineStatus.NOT_DONE -> "Not done this week"
}

private fun sortedRoutines(state: AppState) = state.routines.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

private fun inLibrary(state: AppState, placement: RoutinePlacement) = state.routines.any { it.id == placement.routineId }

private val draftJson = Json { encodeDefaults = true }

private val DraftSaver = Saver<RoutineDraft, String>(
    save = { draftJson.encodeToString(RoutineDraft.serializer(), it) },
    restore = { draftJson.decodeFromString(RoutineDraft.serializer(), it) }
)

@Serializable
private data class RemovedRow(val index: Int, val row: DraftExercise)

private val RemovedSaver = Saver<RemovedRow?, String>(
    save = { removed -> removed?.let { draftJson.encodeToString(RemovedRow.serializer(), it) } },
    restore = { draftJson.decodeFromString(RemovedRow.serializer(), it) }
)

/** The library: every routine the user saved, shared by all phases. */
@Composable
internal fun RoutinesScreen(env: AppEnv, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val state = env.state
    val routines = sortedRoutines(state)
    val full = state.routines.size >= MAX_ROUTINES
    ScreenScaffold(title = "Your routines", onBack = onBack) {
        ScrollBody {
            Text(
                "Start one any time, or add it to a phase's week.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = { onNavigate(Routes.NEW_ROUTINE) },
                enabled = !full,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("routine-new")
            ) {
                RebuildIcon(R.drawable.ic_plus, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("New routine")
            }
            if (full) Text(
                "Library full at $MAX_ROUTINES routines. Delete one to add another.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (routines.isEmpty()) {
                Column(Modifier.testTag("routines-empty").semantics(mergeDescendants = true) {}) {
                    Text("No routines yet", style = MaterialTheme.typography.titleMedium)
                    Text("Build one here, or save a finished workout from History.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else Column(Modifier.testTag("routine-list")) {
                routines.forEachIndexed { i, routine ->
                    if (i > 0) HorizontalDivider()
                    ListRow(
                        routine.name,
                        supporting = routineSummary(state, routine.exercises) + "\n" + state.scheduleSummary(routine.id),
                        onClick = { onNavigate(Routes.routine(routine.id)) },
                        trailing = {
                            RebuildIcon(R.drawable.ic_chevron_right, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        },
                        modifier = Modifier.testTag("routine-row-${routine.id}")
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RoutineDetailScreen(
    env: AppEnv,
    routine: CustomRoutine,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onStarted: () -> Unit,
    onDeleted: () -> Unit
) {
    val state = env.state
    val phase = state.phase ?: return
    var choosingStart by rememberSaveable(routine.id) { mutableStateOf(false) }
    var scheduling by rememberSaveable(routine.id) { mutableStateOf(false) }
    var deleting by rememberSaveable(routine.id) { mutableStateOf(false) }
    var updating by rememberSaveable(routine.id) { mutableStateOf<String?>(null) }
    var startError by rememberSaveable(routine.id) { mutableStateOf<String?>(null) }
    var notice by rememberSaveable(routine.id) { mutableStateOf<String?>(null) }
    val linked = state.linkedPlacements(routine.id, phase)
    val active = state.activeWorkout

    fun startExtra() {
        startError = null
        env.runner.run(Action.StartExtraWorkout(env.clock(), routineId = routine.id), onError = { startError = it }) {
            onStarted()
        }
    }

    ScreenScaffold(title = routine.name, subtitle = routineSummary(state, routine.exercises), onBack = onBack) {
        ScrollBody {
            Button(
                onClick = { if (linked.isEmpty()) startExtra() else choosingStart = true },
                enabled = active == null && !env.runner.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("routine-start")
            ) { PlayLabel(if (linked.isEmpty()) "Start as extra session" else "Start ${routine.name}") }
            Text(
                when {
                    active != null -> "Finish or discard your active workout before starting another."
                    linked.isEmpty() -> "Not on your ${phaseShort(phase)} week, so it runs as an extra session."
                    else -> "On your ${phaseShort(phase)} week on ${linked.joinToString { weekdayName(it.weekday) }}. " +
                        "You'll pick which one."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("routine-start-note")
            )
            ErrorText(startError)
            OutlinedButton(
                onClick = { notice = null; scheduling = true },
                enabled = !env.runner.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("routine-schedule")
            ) {
                RebuildIcon(R.drawable.ic_calendar_plus, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add to a weekly plan")
            }
            notice?.let { NoticeLine(it) { notice = null } }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryTextButton(onClick = { onNavigate(Routes.editRoutine(routine.id)) }, enabled = !env.runner.busy,
                    icon = R.drawable.ic_pencil, modifier = Modifier.testTag("routine-edit")) { Text("Edit routine") }
                SecondaryTextButton(onClick = { deleting = true }, enabled = !env.runner.busy, icon = R.drawable.ic_x,
                    modifier = Modifier.testTag("routine-delete")) { Text("Delete") }
            }

            HorizontalDivider()
            SectionHeader("Exercises")
            Column(Modifier.testTag("routine-exercises")) {
                routine.exercises.forEachIndexed { i, exercise -> RoutineExerciseRow(env, i, exercise) }
            }

            HorizontalDivider()
            SectionHeader("On weekly plans")
            val copies = state.allLinkedPlacements(routine.id).sortedWith(compareBy({ it.phase.ordinal }, { it.weekday }))
            if (copies.isEmpty()) {
                Text("Not on any phase's week yet.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else Column {
                copies.forEachIndexed { i, copy ->
                    if (i > 0) HorizontalDivider()
                    val differs = state.libraryDiffers(copy)
                    val where = "${weekdayName(copy.weekday)} · ${phaseShort(copy.phase)} week"
                    ListRow(
                        where + if (copy.phase == phase) " · current" else "",
                        supporting = listOfNotNull(
                            copy.name.takeIf { it != routine.name }?.let { "Named $it there" },
                            if (differs) "Differs from this routine" else "Same as this routine"
                        ).joinToString(" · "),
                        trailing = if (!differs) null else {
                            {
                                TextButton(
                                    onClick = { updating = copy.id },
                                    enabled = !env.runner.busy,
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("routine-update-${copy.id}")
                                        .semantics { contentDescription = "Update the copy on $where" }
                                ) { Text("Update") }
                            }
                        },
                        modifier = Modifier.testTag("routine-copy-${copy.id}")
                    )
                }
            }
            Text(
                "Copies keep their own settings until you choose Update.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (choosingStart) StartRoutineDialog(env, routine, phase, linked, onDismiss = { choosingStart = false }) {
        choosingStart = false
        onStarted()
    }
    if (scheduling) ScheduleRoutineDialog(env, routine, lockedPhase = null, onDismiss = { scheduling = false }) { p, day ->
        notice = "Added to the ${phaseShort(p)} week on ${weekdayName(day)}."
    }
    if (deleting) {
        var error by rememberSaveable(routine.id) { mutableStateOf<String?>(null) }
        ConfirmDialog(
            title = "Delete ${routine.name}?",
            text = "It leaves your library. Copies on weekly plans stay with their own settings, and workouts keep " +
                "everything logged.",
            confirmLabel = "Delete",
            destructive = true,
            busy = env.runner.busy,
            error = error,
            onDismiss = { deleting = false },
            onConfirm = {
                error = null
                env.runner.run(Action.DeleteRoutine(routine.id), onError = { error = it }) {
                    deleting = false
                    onDeleted()
                }
            }
        )
    }
    state.placements.find { it.id == updating && !it.removedFromPlan }?.takeIf { inLibrary(state, it) }?.let {
        UpdateCopyDialog(env, it, onDismiss = { updating = null })
    }
}

@Composable
private fun RoutineExerciseRow(env: AppEnv, index: Int, exercise: RoutineExercise) {
    val name = env.exercise(exercise.exerciseId)?.name ?: exercise.exerciseId
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OrderBadge(index + 1)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(
                routineTargetsText(env.state, exercise),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val EXTRA_CHOICE = "__extra__"
private const val ALONGSIDE = "__alongside__"

/**
 * Starting a library routine that has copies on this phase's week: the user says which day's workout it is, or an
 * extra session. Nothing is preselected, so it is never guessed.
 */
@Composable
private fun StartRoutineDialog(
    env: AppEnv,
    routine: CustomRoutine,
    phase: Phase,
    linked: List<RoutinePlacement>,
    onDismiss: () -> Unit,
    onStarted: () -> Unit
) {
    val state = env.state
    var choice by rememberSaveable(routine.id) { mutableStateOf<String?>(null) }
    var error by rememberSaveable(routine.id) { mutableStateOf<String?>(null) }
    val checklist = state.weekChecklist(localDateOf(env.clock()))
    val week = state.scheduledRoutines(phase)
    val selected = choice?.takeIf { it == EXTRA_CHOICE || linked.any { copy -> copy.id == it } }

    fun start() {
        val chosen = selected ?: return
        error = null
        val action = if (chosen == EXTRA_CHOICE) Action.StartExtraWorkout(env.clock(), routineId = routine.id)
        else Action.StartWorkout(chosen, env.clock())
        env.runner.run(action, onError = { error = it }, onSuccess = onStarted)
    }

    AdaptiveDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start ${routine.name}") },
        text = {
            Text("Which workout is this?", style = MaterialTheme.typography.bodyMedium)
            Column(Modifier.selectableGroup()) {
                linked.forEach { copy ->
                    RadioRow(
                        "${occurrenceDay(copy, week)} · ${copy.name}",
                        selected = selected == copy.id,
                        onClick = { choice = copy.id },
                        supporting = listOfNotNull(
                            checklist?.statusOf(copy.id)?.let(::weekStatusText),
                            "Uses that day's own settings".takeIf { state.libraryDiffers(copy) }
                        ).joinToString(" · ").ifEmpty { null },
                        modifier = Modifier.testTag("start-choice-${copy.id}")
                    )
                }
                RadioRow(
                    "Extra session",
                    selected = selected == EXTRA_CHOICE,
                    onClick = { choice = EXTRA_CHOICE },
                    supporting = "Outside the plan · never counts on the weekly checklist",
                    modifier = Modifier.testTag("start-choice-extra")
                )
            }
            ErrorText(error)
        },
        confirmButton = {
            TextButton(onClick = ::start, enabled = selected != null && !env.runner.busy,
                modifier = Modifier.testTag("start-choice-confirm")) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Seven days of one phase's week, each saying what is already on it. */
@Composable
private fun DayChoices(week: List<ScheduledRoutine>, selected: Int?, current: Int? = null, onSelect: (Int) -> Unit) {
    Column(Modifier.selectableGroup()) {
        (1..7).forEach { day ->
            val there = week.filter { it.weekday == day }.joinToString { it.name }
            RadioRow(
                weekdayName(day),
                selected = selected == day,
                onClick = { onSelect(day) },
                supporting = when {
                    day == current -> "Its day now" + if (there.isEmpty()) "" else ", with $there"
                    there.isEmpty() -> "Rest day"
                    else -> there
                },
                modifier = Modifier.testTag("schedule-day-$day")
            )
        }
    }
}

private fun replaceNote(state: AppState, other: ScheduledRoutine): String =
    "It comes off this week. Workouts already done keep everything. " + when (other) {
        is WorkoutTemplate -> "You can put it back from Plan."
        is RoutinePlacement -> if (inLibrary(state, other)) "This copy's settings are removed; the routine stays in your library."
        else "Its exercises aren't saved anywhere else."
    }

/** A taken day never loses a routine silently: the user keeps it alongside or names the one to replace. */
@Composable
private fun ConflictChoices(
    state: AppState,
    onDay: List<ScheduledRoutine>,
    week: List<ScheduledRoutine>,
    choice: String?,
    onChoice: (String) -> Unit
) {
    if (onDay.isEmpty()) return
    val names = onDay.joinToString { it.name }
    val bounds = remember { BringIntoViewRequester() }
    // The confirm button waits for this choice, so picking a taken day low in the list scrolls the question into view.
    LaunchedEffect(onDay.first().weekday, names) {
        withFrameNanos { }
        bounds.bringIntoView()
    }
    Column(Modifier.bringIntoViewRequester(bounds), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "${weekdayName(onDay.first().weekday)} already has $names. Add this as another workout that day, or replace one.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("schedule-conflict").semantics { liveRegion = LiveRegionMode.Polite }
        )
        Column(Modifier.selectableGroup()) {
            RadioRow(
                "Add alongside",
                selected = choice == ALONGSIDE,
                onClick = { onChoice(ALONGSIDE) },
                supporting = "Keeps $names. Each counts on its own on the checklist.",
                modifier = Modifier.testTag("schedule-alongside")
            )
            onDay.forEach { other ->
                RadioRow(
                    "Replace ${occurrenceName(other, week)}",
                    selected = choice == other.id,
                    onClick = { onChoice(other.id) },
                    supporting = replaceNote(state, other),
                    modifier = Modifier.testTag("schedule-replace-${other.id}")
                )
            }
        }
    }
}

/**
 * Puts a copy of [routine] on one day of a phase's week. [lockedPhase] is the phase being edited in Plan; from the
 * library the user picks it, starting at the current phase.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScheduleRoutineDialog(
    env: AppEnv,
    routine: CustomRoutine,
    lockedPhase: Phase?,
    onDismiss: () -> Unit,
    onScheduled: (Phase, Int) -> Unit = { _, _ -> }
) {
    val state = env.state
    val key = routine.id
    // One ID per dialog, so a retried request is recognized instead of adding a second copy.
    val placementId by rememberSaveable(key) { mutableStateOf(newId()) }
    var phase by rememberSaveable(key) { mutableStateOf(lockedPhase ?: state.phase ?: Phase.entries.first()) }
    var weekday by rememberSaveable(key) { mutableStateOf<Int?>(null) }
    var conflict by rememberSaveable(key) { mutableStateOf<String?>(null) }
    var error by rememberSaveable(key) { mutableStateOf<String?>(null) }
    val week = state.scheduledRoutines(phase)
    val onDay = weekday?.let { day -> week.filter { it.weekday == day } }.orEmpty()
    // A choice about a routine that has since left that day is dropped, never guessed.
    val choice = conflict?.takeIf { it == ALONGSIDE || onDay.any { other -> other.id == it } }

    fun schedule() {
        val day = weekday ?: return
        error = null
        env.runner.run(
            Action.ScheduleRoutine(placementId, phase, day, routine.id, alongside = choice == ALONGSIDE,
                replacingId = choice?.takeIf { it != ALONGSIDE }),
            onError = { error = it }
        ) {
            onScheduled(phase, day)
            onDismiss()
        }
    }

    AdaptiveDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (lockedPhase != null) "Add ${routine.name} to the ${phaseShort(lockedPhase)} week"
                else "Add ${routine.name} to a week")
        },
        text = {
            Text(
                "A copy goes on that day with its own settings.",
                style = MaterialTheme.typography.bodyMedium
            )
            if (lockedPhase == null) {
                SectionHeader("Phase")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Phase.entries.forEach { option ->
                        FilterChip(
                            selected = option == phase,
                            onClick = { phase = option; conflict = null },
                            label = { Text(phaseShort(option)) },
                            leadingIcon = if (option == state.phase) { { CurrentPhaseMark() } } else null,
                            modifier = Modifier.testTag("schedule-phase-${option.name}").semantics {
                                if (option == state.phase) contentDescription = "${phaseShort(option)}, current phase"
                            }
                        )
                    }
                }
            }
            SectionHeader("Day")
            DayChoices(week, weekday) { weekday = it; conflict = null }
            ConflictChoices(state, onDay, week, choice) { conflict = it }
            ErrorText(error)
        },
        confirmButton = {
            TextButton(
                onClick = ::schedule,
                enabled = weekday != null && (onDay.isEmpty() || choice != null) && !env.runner.busy,
                modifier = Modifier.testTag("schedule-confirm")
            ) { Text("Add to ${phaseShort(phase)} week") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun MovePlacementDialog(env: AppEnv, placement: RoutinePlacement, onDismiss: () -> Unit) {
    val state = env.state
    val others = state.scheduledRoutines(placement.phase).filter { it.id != placement.id }
    var weekday by rememberSaveable(placement.id) { mutableStateOf<Int?>(null) }
    var conflict by rememberSaveable(placement.id) { mutableStateOf<String?>(null) }
    var error by rememberSaveable(placement.id) { mutableStateOf<String?>(null) }
    val target = weekday?.takeIf { it != placement.weekday }
    val onDay = target?.let { day -> others.filter { it.weekday == day } }.orEmpty()
    val choice = conflict?.takeIf { it == ALONGSIDE || onDay.any { other -> other.id == it } }

    fun move() {
        val day = target ?: return
        error = null
        env.runner.run(
            Action.MovePlacement(placement.id, day, alongside = choice == ALONGSIDE,
                replacingId = choice?.takeIf { it != ALONGSIDE }),
            onError = { error = it }, onSuccess = onDismiss
        )
    }

    AdaptiveDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change day · ${placement.name}") },
        text = {
            Text(
                "Moves it on the ${phaseShort(placement.phase)} week from now on. Workouts already done this week " +
                    "still count for it.",
                style = MaterialTheme.typography.bodyMedium
            )
            DayChoices(others, weekday ?: placement.weekday, current = placement.weekday) { weekday = it; conflict = null }
            ConflictChoices(state, onDay, others, choice) { conflict = it }
            ErrorText(error)
        },
        confirmButton = {
            TextButton(
                onClick = ::move,
                enabled = target != null && (onDay.isEmpty() || choice != null) && !env.runner.busy,
                modifier = Modifier.testTag("move-confirm")
            ) { Text("Move") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Putting an original routine back on a day that now holds another routine asks the same explicit question. */
@Composable
internal fun RestoreTemplateDialog(env: AppEnv, phase: Phase, template: WorkoutTemplate, onDismiss: () -> Unit) {
    val state = env.state
    val week = state.scheduledRoutines(phase)
    val onDay = week.filter { it.weekday == template.weekday }
    var conflict by rememberSaveable(template.id) { mutableStateOf<String?>(null) }
    var error by rememberSaveable(template.id) { mutableStateOf<String?>(null) }
    val choice = conflict?.takeIf { it == ALONGSIDE || onDay.any { other -> other.id == it } }

    fun restore() {
        error = null
        env.runner.run(
            Action.RestoreToWeeklyPlan(phase, template.id, alongside = choice == ALONGSIDE,
                replacingId = choice?.takeIf { it != ALONGSIDE }),
            onError = { error = it }, onSuccess = onDismiss
        )
    }

    AdaptiveDialog(
        onDismissRequest = onDismiss,
        title = { Text("Put ${template.name} back") },
        text = {
            Text("It goes back on ${weekdayName(template.weekday)} with its plan as you left it.",
                style = MaterialTheme.typography.bodyMedium)
            ConflictChoices(state, onDay, week, choice) { conflict = it }
            ErrorText(error)
        },
        confirmButton = {
            TextButton(
                onClick = ::restore,
                enabled = (onDay.isEmpty() || choice != null) && !env.runner.busy,
                modifier = Modifier.testTag("restore-confirm-week")
            ) { Text("Put back") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun UpdateCopyDialog(env: AppEnv, placement: RoutinePlacement, onDismiss: () -> Unit) {
    val routine = env.state.routines.find { it.id == placement.routineId } ?: return
    var error by rememberSaveable(placement.id) { mutableStateOf<String?>(null) }
    ConfirmDialog(
        title = "Update from ${routine.name}?",
        text = "The ${weekdayName(placement.weekday)} copy on the ${phaseShort(placement.phase)} week takes this " +
            "routine's name and exercises.\nChanges made only to that copy are replaced.\nWorkouts keep what they had.",
        confirmLabel = "Update",
        busy = env.runner.busy,
        error = error,
        onDismiss = onDismiss,
        onConfirm = {
            error = null
            env.runner.run(Action.UpdatePlacementFromRoutine(placement.id), onError = { error = it }, onSuccess = onDismiss)
        }
    )
}

@Composable
internal fun TakeOffDialog(env: AppEnv, phase: Phase, occurrence: ScheduledRoutine, onDismiss: () -> Unit) {
    var error by rememberSaveable(occurrence.id) { mutableStateOf<String?>(null) }
    ConfirmDialog(
        title = "Take ${occurrence.name} off the ${phaseShort(phase)} week?",
        text = "It leaves this week's checklist and the weeks after. Workouts already done keep everything. " +
            when (occurrence) {
                is WorkoutTemplate -> "Its plan is kept, and you can put it back from Plan."
                is RoutinePlacement -> if (inLibrary(env.state, occurrence))
                    "This copy's settings are removed; the routine stays in your library."
                else "This copy's exercises aren't saved anywhere else."
            },
        confirmLabel = "Take off",
        destructive = true,
        busy = env.runner.busy,
        error = error,
        onDismiss = onDismiss,
        onConfirm = {
            error = null
            env.runner.run(Action.RemoveFromWeeklyPlan(phase, occurrence.id), onError = { error = it }, onSuccess = onDismiss)
        }
    )
}

/** Plan's first step to schedule a library routine on the week being viewed. */
@Composable
internal fun ChooseRoutineDialog(
    env: AppEnv,
    phase: Phase,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onChoose: (CustomRoutine) -> Unit
) {
    val routines = sortedRoutines(env.state)
    AdaptiveDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to the ${phaseShort(phase)} week") },
        text = {
            if (routines.isEmpty()) {
                Text("You have no saved routines yet. Create one, or save a finished workout as a routine from History.",
                    style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("A copy goes on the day you pick.", style = MaterialTheme.typography.bodyMedium)
                Column {
                    routines.forEachIndexed { i, routine ->
                        if (i > 0) HorizontalDivider()
                        ListRow(routine.name, supporting = routineSummary(env.state, routine.exercises),
                            onClick = { onChoose(routine) }, modifier = Modifier.testTag("choose-routine-${routine.id}"))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onNew, enabled = env.state.routines.size < MAX_ROUTINES,
                modifier = Modifier.testTag("choose-routine-new")) { Text("New routine") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * One routine of a phase's week in Plan: its day, name and this week's status, with the actions for that occurrence.
 * Original routines can only be taken off; the user's copies can also be edited, moved or updated from the library.
 */
@Composable
internal fun OccurrenceHeader(
    env: AppEnv,
    occurrence: ScheduledRoutine,
    week: List<ScheduledRoutine>,
    planned: List<PlannedExercise>,
    status: RoutineStatus?,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onUpdate: () -> Unit,
    onTakeOff: () -> Unit,
    onReorder: () -> Unit = {}
) {
    var open by rememberSaveable(occurrence.id) { mutableStateOf(false) }
    val statusText = when (status) {
        RoutineStatus.DONE -> " · done this week"
        RoutineStatus.IN_PROGRESS -> " · in progress"
        RoutineStatus.PARTIAL -> " · partial this week"
        RoutineStatus.NOT_DONE, null -> ""
    }
    Row(Modifier.fillMaxWidth().testTag("plan-occurrence-${occurrence.id}"), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DayBlock(weekdayName(occurrence.weekday, TextStyle.SHORT), null, weekdayName(occurrence.weekday))
            Column(Modifier.weight(1f)) {
                Text(occurrence.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    plannedSummary(env.state, planned) +
                        statusText + (occurrenceOrdinal(occurrence, week)?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Box {
            IconButton(
                onClick = { open = true },
                enabled = !env.runner.busy,
                modifier = Modifier.testTag("plan-options-${occurrence.id}")
            ) {
                RebuildIcon(R.drawable.ic_ellipsis_vertical,
                    contentDescription = "Options for ${occurrence.name}, ${occurrenceDay(occurrence, week)}")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                if (occurrence is RoutinePlacement) {
                    MenuItem("Edit routine", R.drawable.ic_pencil, "plan-menu-edit") { open = false; onEdit() }
                    MenuItem("Change day", R.drawable.ic_calendar, "plan-menu-move") { open = false; onMove() }
                    if (env.state.libraryDiffers(occurrence)) {
                        MenuItem("Update from library", R.drawable.ic_refresh_cw, "plan-menu-update") { open = false; onUpdate() }
                    }
                }
                if (planned.size >= 2) {
                    MenuItem("Reorder exercises", R.drawable.ic_arrow_down, "plan-menu-reorder") { open = false; onReorder() }
                }
                MenuItem("Take off this week", R.drawable.ic_x, "plan-menu-take-off") { open = false; onTakeOff() }
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, @DrawableRes icon: Int, tag: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        leadingIcon = { RebuildIcon(icon, contentDescription = null) },
        modifier = Modifier.testTag(tag)
    )
}

/** Where a scheduled copy came from, for its Plan section. */
internal fun copyNote(state: AppState, placement: RoutinePlacement): String {
    val routine = state.routines.find { it.id == placement.routineId }
    return when {
        routine == null -> "Your routine · no longer in your library, so this copy holds its only settings"
        state.libraryDiffers(placement) -> "Your routine · differs from ${routine.name} in your library"
        else -> "Your routine · copy of ${routine.name} in your library"
    }
}

private const val EDITOR_HEADER_ITEMS = 3

private fun joinWithAnd(parts: List<String>): String = when (parts.size) {
    0 -> ""
    1 -> parts[0]
    else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
}

private fun AppState.problemText(problem: DraftProblem, row: DraftExercise, ownerId: String): String = when (problem) {
    DraftProblem.NEEDS_TARGETS -> "Needs " + joinWithAnd(when (measureOf(row.exerciseId)) {
        Measure.REPS -> listOfNotNull(
            "working sets".takeIf { row.sets == null },
            "a rep range".takeIf { row.minReps == null || row.maxReps == null })
        Measure.TIMED -> listOfNotNull(
            "timed sets".takeIf { row.sets == null },
            "a minimum time".takeIf { row.effortSeconds == null })
        Measure.DISTANCE -> listOfNotNull(
            "distance sets".takeIf { row.sets == null },
            "a minimum distance".takeIf { row.effortDistance == null })
        Measure.CARDIO -> listOfNotNull(
            "a total time or distance".takeIf { row.totalSeconds == null && row.totalDistance == null })
    } + listOfNotNull("rest".takeIf { row.restSeconds == null })) + "."
    DraftProblem.DUPLICATE -> "Listed more than once. Remove the extra one."
    DraftProblem.PER_LEG_CONFLICT -> "Your plan counts its reps " +
        (if (perLegConvention(row.exerciseId, excludingOwnerId = ownerId) == true) "per leg" else "as a total") +
        ". Open it to match."
}

/**
 * The shared editor for a new routine, a library routine, a draft from a finished workout and a scheduled copy.
 * Nothing is saved until the user reviews every exercise's targets and taps save; [ownerId] is what gets saved.
 */
@Composable
internal fun RoutineEditorScreen(
    env: AppEnv,
    title: String,
    subtitle: String?,
    intro: String,
    ownerId: String,
    initial: () -> RoutineDraft,
    saveLabel: String,
    onBack: () -> Unit,
    toAction: (String, List<RoutineExercise>) -> Action,
    onSaved: () -> Unit
) {
    val state = env.state
    val original by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(initial()) }
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(original) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var discarding by rememberSaveable { mutableStateOf(false) }
    var removed by rememberSaveable(stateSaver = RemovedSaver) { mutableStateOf<RemovedRow?>(null) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var reveal by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current
    val problems = state.draftProblems(draft, ownerId)
    val changed = draft != original
    fun nameOf(exerciseId: String) = env.exercise(exerciseId)?.name ?: exerciseId

    LaunchedEffect(reveal, draft.exercises.map { it.key }) {
        val index = draft.exercises.indexOfFirst { it.key == reveal }
        if (index >= 0) {
            listState.animateScrollToItem(index + EDITOR_HEADER_ITEMS)
            reveal = null
        }
    }
    fun leave() {
        if (changed) discarding = true else onBack()
    }
    BackHandler(enabled = changed) { discarding = true }

    fun upsert(row: DraftExercise) {
        val rows = draft.exercises
        draft = draft.copy(exercises = if (rows.any { it.key == row.key }) rows.map { if (it.key == row.key) row else it }
            else rows + row)
        reveal = row.key
        error = null
    }
    fun move(index: Int, to: Int) {
        if (index !in draft.exercises.indices || to !in draft.exercises.indices) return
        draft = draft.copy(exercises = draft.exercises.toMutableList().apply { add(to, removeAt(index)) })
    }
    fun remove(index: Int) {
        val row = draft.exercises.getOrNull(index) ?: return
        draft = draft.copy(exercises = draft.exercises.filterIndexed { i, _ -> i != index })
        removed = RemovedRow(index, row)
        notice = null
    }
    fun undoRemove() {
        val last = removed ?: return
        val rows = draft.exercises.toMutableList()
        rows.add(last.index.coerceIn(0, rows.size), last.row)
        draft = draft.copy(exercises = rows)
        removed = null
        reveal = last.row.key
    }
    fun save() {
        focus.clearFocus()
        val name = draft.name.trim()
        val first = draft.exercises.firstOrNull { problems.containsKey(it.key) }
        error = when {
            name.isEmpty() -> "Name the routine."
            draft.exercises.isEmpty() -> "Add at least one exercise."
            first != null -> "${nameOf(first.exerciseId)}: " +
                state.problemText(problems.getValue(first.key).first(), first, ownerId)
            else -> null
        }
        if (first != null) reveal = first.key
        if (error != null) return
        val exercises = state.confirmedExercises(draft) ?: return
        env.runner.run(toAction(name, exercises), onError = { error = it }, onSuccess = onSaved)
    }

    // Keys are unique by construction; index keys are only a guard against a corrupted saved draft.
    val distinctKeys = draft.exercises.map { it.key }.toSet().size == draft.exercises.size
    ScreenScaffold(title = title, subtitle = subtitle, onBack = ::leave, compactWhenKeyboard = true) {
        LazyColumn(
            Modifier.weight(1f).readableWidth().yieldsToFixedControls().testTag("routine-editor"),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "intro") {
                Text(intro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item(key = "name") {
                OutlinedTextField(
                    draft.name,
                    { draft = draft.copy(name = it.take(MAX_ROUTINE_NAME)) },
                    label = { Text("Routine name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.secondary,
                        focusedLabelColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("routine-name")
                )
            }
            item(key = "header") {
                SectionHeader(if (draft.exercises.isEmpty()) "Exercises" else "Exercises · ${draft.exercises.size}")
            }
            if (draft.exercises.isEmpty()) item(key = "empty") {
                Text("No exercises yet.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            itemsIndexed(draft.exercises, key = { index, row -> if (distinctKeys) "row/${row.key}" else "row-index/$index" }) { index, row ->
                val name = nameOf(row.exerciseId)
                val measure = state.measureOf(row.exerciseId)
                DraftRow(
                    name, index, draft.exercises.size,
                    targets = row.restSeconds?.takeIf { row.complete(measure) }?.let { rest ->
                        compactTargetText(measure, row.sets, row.minReps, row.maxReps, row.perLeg, rest,
                            MeasuredGoal(row.effortSeconds, row.effortDistance, row.totalSeconds, row.totalDistance))
                    },
                    problems[row.key].orEmpty().map { state.problemText(it, row, ownerId) },
                    enabled = !env.runner.busy,
                    onOpen = { editing = row.key },
                    onUp = { move(index, index - 1) },
                    onDown = { move(index, index + 1) },
                    onRemove = { remove(index) }
                )
            }
            item(key = "add") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    removed?.let { last -> UndoLine("Removed ${nameOf(last.row.exerciseId)}.", ::undoRemove) { removed = null } }
                    notice?.let { NoticeLine(it) { notice = null } }
                    OutlinedButton(
                        onClick = { picking = true },
                        enabled = draft.exercises.size < MAX_WORKOUT_EXERCISES && !env.runner.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("routine-add-exercise")
                    ) {
                        RebuildIcon(R.drawable.ic_plus, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add exercise")
                    }
                }
            }
        }
        if (!compactLandscapeInput()) {
            HorizontalDivider()
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ErrorText(error)
                Button(
                    onClick = ::save,
                    enabled = !env.runner.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("routine-save")
                ) { Text(saveLabel) }
            }
        }
    }

    if (picking) ExerciseLibraryPicker(
        env,
        onDismiss = { picking = false },
        title = "Add to ${draft.name.trim().ifEmpty { "routine" }}",
        marked = draft.exercises.map { it.exerciseId }.toSet(),
        markedLabel = "in this routine"
    ) { exercise ->
        picking = false
        // One already listed opens instead of being added twice.
        val existing = draft.exercises.firstOrNull { it.exerciseId == exercise.id }
        if (existing != null) {
            notice = "${exercise.name} is already in this routine."
            reveal = existing.key
            editing = existing.key
        } else {
            notice = null
            pending = exercise.id
        }
    }
    editing?.let { key ->
        draft.exercises.find { it.key == key }?.let { row ->
            DraftTargetsDialog(env, row.exerciseId, row, ownerId, onDismiss = { editing = null }) {
                upsert(it)
                editing = null
            }
        }
    }
    pending?.let { exerciseId ->
        DraftTargetsDialog(env, exerciseId, null, ownerId, onDismiss = { pending = null }) {
            upsert(it)
            pending = null
        }
    }
    if (discarding) ConfirmDialog(
        title = "Discard changes?",
        text = "Your changes to this routine aren't saved.",
        confirmLabel = "Discard",
        destructive = true,
        onDismiss = { discarding = false },
        onConfirm = {
            discarding = false
            onBack()
        }
    )
}

@Composable
private fun DraftRow(
    name: String,
    index: Int,
    count: Int,
    targets: String?,
    problems: List<String>,
    enabled: Boolean,
    onOpen: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit
) {
    val stacked = LocalDensity.current.fontScale > 1.3f
    Column(Modifier.testTag("draft-row-$index")) {
        if (index > 0) HorizontalDivider()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clickable(enabled = enabled, onClickLabel = "Set targets", onClick = onOpen)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OrderBadge(index + 1)
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                    if (targets != null) Text(
                        targets,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    problems.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (!stacked) DraftRowControls(name, index, count, enabled, onUp, onDown, onRemove)
        }
        if (stacked) Row(Modifier.align(Alignment.End)) { DraftRowControls(name, index, count, enabled, onUp, onDown, onRemove) }
    }
}

@Composable
private fun DraftRowControls(
    name: String,
    index: Int,
    count: Int,
    enabled: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit
) {
    IconButton(onClick = onUp, enabled = enabled && index > 0, modifier = Modifier.testTag("draft-up-$index")) {
        RebuildIcon(R.drawable.ic_arrow_up, contentDescription = "Move $name up")
    }
    IconButton(onClick = onDown, enabled = enabled && index < count - 1, modifier = Modifier.testTag("draft-down-$index")) {
        RebuildIcon(R.drawable.ic_arrow_down, contentDescription = "Move $name down")
    }
    IconButton(onClick = onRemove, enabled = enabled, modifier = Modifier.testTag("draft-remove-$index")) {
        RebuildIcon(R.drawable.ic_x, contentDescription = "Remove $name")
    }
}

@Composable
private fun UndoLine(text: String, onUndo: () -> Unit, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("routine-undo"), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
        TextButton(onClick = onUndo, modifier = Modifier.heightIn(min = 48.dp).testTag("routine-undo-button")) { Text("Undo") }
        IconButton(onClick = onDismiss) { RebuildIcon(R.drawable.ic_x, contentDescription = "Dismiss message") }
    }
}

/**
 * One routine exercise's targets, typed by the user. Nothing is taken from what was lifted; rest starts at the
 * exercise's last-used or planned rest, else a visible 90 seconds.
 */
@Composable
private fun DraftTargetsDialog(
    env: AppEnv,
    exerciseId: String,
    row: DraftExercise?,
    ownerId: String,
    onDismiss: () -> Unit,
    onSave: (DraftExercise) -> Unit
) {
    val state = env.state
    val exercise = env.exercise(exerciseId)
    val name = exercise?.name ?: exerciseId
    val measure = state.measureOf(exerciseId)
    val key = row?.key ?: "new|$exerciseId"
    val suggestion = state.restSuggestion(exerciseId, state.phase)
    val known = state.perLegConvention(exerciseId, excludingOwnerId = ownerId)
    val rowKey by rememberSaveable(key) { mutableStateOf(row?.key ?: newId()) }
    var setsText by rememberSaveable(key) { mutableStateOf(row?.sets?.toString().orEmpty()) }
    var minText by rememberSaveable(key) { mutableStateOf(row?.minReps?.toString().orEmpty()) }
    var maxText by rememberSaveable(key) { mutableStateOf(row?.maxReps?.toString().orEmpty()) }
    var restText by rememberSaveable(key) { mutableStateOf((row?.restSeconds ?: suggestion.seconds).toString()) }
    val measured = rememberGoalText(key) {
        GoalText.of(measure, row?.sets, MeasuredGoal(row?.effortSeconds, row?.effortDistance, row?.totalSeconds,
            row?.totalDistance), row?.restSeconds ?: suggestion.seconds)
    }
    val initialPerLeg = known ?: row?.perLeg ?: state.lastLoggedPerLeg(exerciseId) ?: false
    var perLeg by rememberSaveable(key) { mutableStateOf(initialPerLeg) }
    val asks by rememberSaveable(key) {
        mutableStateOf(measure == Measure.REPS &&
            (known != null || initialPerLeg || (exercise != null && state.asksPerLeg(exercise))))
    }
    var error by rememberSaveable(key) { mutableStateOf<String?>(null) }

    fun save() {
        if (measure != Measure.REPS) {
            val (problem, parsed) = parseMeasuredGoal(measure, measured, optional = false)
            error = problem
            if (parsed == null) return
            val goal = parsed.goal
            onSave(DraftExercise(rowKey, exerciseId, parsed.sets, null, null, parsed.rest, perLeg = false,
                effortSeconds = goal.effortSeconds, effortDistance = goal.effortDistance,
                totalSeconds = goal.totalSeconds, totalDistance = goal.totalDistance))
            return
        }
        val (problem, goal) = parseGoal(setsText, minText, maxText, optional = false)
        val rest = parseRest(restText)
        error = problem ?: if (rest == null) "Rest must be between 1 and 3600 seconds." else null
        val (sets, min, max) = goal
        if (error != null || rest == null || sets == null || min == null || max == null) return
        onSave(DraftExercise(rowKey, exerciseId, sets, min, max, rest, known ?: perLeg))
    }

    AdaptiveDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (row == null) "Add $name" else name) },
        text = {
            Text(
                if (measure == Measure.REPS) "Logged loads and reps never become targets."
                else "Logged times and distances never become goals.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val hint = if (row?.restSeconds == null) restHint(suggestion.seconds, suggestion.source.label) else null
            if (measure == Measure.REPS) {
                GoalFields(setsText, { setsText = it }, minText, { minText = it }, maxText, { maxText = it },
                    restText, { restText = it }, optional = false, restHint = hint, onDone = ::save)
                if (asks) PerLegChoice(known, perLeg, lockedBecause = null) { perLeg = it }
            } else {
                MeasuredGoalFields(measure, measured, optional = false, restHint = hint, onDone = ::save)
            }
            ErrorText(error)
        },
        confirmButton = {
            TextButton(onClick = ::save, modifier = Modifier.testTag("draft-targets-save")) {
                Text(if (row == null) "Add to routine" else "Done")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
