package com.rebuild.ui

import com.rebuild.domain.repRecords
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rebuild.domain.MAX_ROUTINES
import com.rebuild.domain.Action
import com.rebuild.R
import com.rebuild.domain.Effort
import com.rebuild.domain.Exercise
import com.rebuild.domain.LoadingType
import com.rebuild.domain.LoadUnit
import com.rebuild.domain.LoggedSet
import com.rebuild.domain.Measure
import com.rebuild.domain.WorkEntry
import com.rebuild.domain.Workout
import com.rebuild.domain.WorkoutExercise
import com.rebuild.domain.asksPerLeg
import com.rebuild.domain.limitUserText
import com.rebuild.domain.followUps
import com.rebuild.domain.mainSet
import com.rebuild.domain.measured
import com.rebuild.domain.openGroup
import com.rebuild.domain.warmupSetCount
import com.rebuild.domain.workEntries
import com.rebuild.domain.workingCount
import com.rebuild.domain.workingSetCount
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal data class Identity(val id: String, val loading: LoadingType, val perDumbbell: Boolean)

internal fun Exercise.identity() = Identity(id, loading, perDumbbell)

@Composable
internal fun HistoryScreen(
    env: AppEnv,
    onNavigate: (String) -> Unit,
    onOpenToday: () -> Unit = { onNavigate(com.rebuild.ui.Tab.HOME.route) }
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val finished = env.state.workouts.filter { it.finishedAt != null }.sortedByDescending { it.startedAt }
    ScreenScaffold(title = "History") {
        val tabs = @Composable {
            val selected = MaterialTheme.colorScheme.primary
            val unselected = MaterialTheme.colorScheme.onSurfaceVariant
            listOf("Workouts", "Exercises", "Body").forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label, maxLines = 1, softWrap = false) },
                    selectedContentColor = selected, unselectedContentColor = unselected,
                    modifier = Modifier.testTag("history-tab-$i"))
            }
        }
        // A label never breaks mid-word: at large text the tabs scroll instead of squeezing.
        if (LocalDensity.current.fontScale > 1.3f) ScrollableTabRow(selectedTabIndex = tab, modifier = Modifier.readableWidth(),
            edgePadding = 0.dp) { tabs() }
        else TabRow(selectedTabIndex = tab, modifier = Modifier.readableWidth()) { tabs() }
        if (tab == 2) {
            BodyOverview(env, onWeight = { onNavigate(Routes.BODYWEIGHT) }, onMeasurements = { onNavigate(Routes.MEASUREMENTS) },
                onMeasure = { onNavigate(Routes.MEASURE) }, onSite = { onNavigate(Routes.site(it)) })
            return@ScreenScaffold
        }
        if (finished.isEmpty()) {
            ScrollBody {
                Text("No finished workouts yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Finish a workout from Today and its sets, notes and exercise charts appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onOpenToday) { Text("Go to Today") }
            }
            return@ScreenScaffold
        }
        if (tab == 0) {
            LazyBody(spacing = 0.dp) {
                item(key = "calendar") { WorkoutCalendar(env, onOpen = { onNavigate(Routes.workoutDetail(it.id)) }) }
                item(key = "muscles") { WeeklyMuscleSets(env) }
                historyMonths(finished).forEach { (month, workouts) ->
                    item(key = "month/$month") {
                        SectionHeader(month, Modifier.padding(top = 8.dp, bottom = 4.dp))
                    }
                    itemsIndexed(workouts, key = { _, w -> w.id }) { i, workout ->
                        Column {
                            if (i > 0) HorizontalDivider()
                            HistoryWorkoutRow(workout) { onNavigate(Routes.workoutDetail(workout.id)) }
                        }
                    }
                }
            }
        } else {
            val identities = finished.flatMap { w ->
                w.exercises.filter { e -> e.sets.any { !it.warmup } || e.efforts.isNotEmpty() }.map { w to it.exercise }
            }
                .groupBy { it.second.identity() }
                .map { (_, list) -> Triple(list.first().second, list.size, list.first().first.startedAt) }
                .sortedBy { it.first.name.lowercase() }
            var query by rememberSaveable { mutableStateOf("") }
            val shown = identities.filter { (exercise, _, _) -> query.isBlank() || exercise.name.contains(query.trim(), ignoreCase = true) }
            LazyBody(spacing = 0.dp) {
                item(key = "search") {
                    OutlinedTextField(
                        query, { query = it },
                        label = { Text("Search your exercises") },
                        leadingIcon = { RebuildIcon(R.drawable.ic_search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("history-exercise-search")
                    )
                }
                if (shown.isEmpty()) item(key = "none") {
                    Text("No exercise matches \"${query.trim()}\".", modifier = Modifier.padding(vertical = 12.dp))
                }
                itemsIndexed(shown, key = { _, (exercise, _, _) -> Routes.exerciseHistory(exercise) }) { i, (exercise, sessions, last) ->
                    Column {
                        if (i > 0) HorizontalDivider()
                        ListRow(
                            headline = exercise.name,
                            supporting = "${loadingShort(exercise).replaceFirstChar { it.uppercase() }} · " +
                                "${countLabel(sessions, "session")}\nLast ${formatDate(last)}",
                            onClick = { onNavigate(Routes.exerciseHistory(exercise)) },
                            trailing = { Chevron() }
                        )
                    }
                }
            }
        }
    }
}

/** Finished workouts (newest first) grouped under their calendar month, e.g. "September 2026". */
internal fun historyMonths(finished: List<Workout>): List<Pair<String, List<Workout>>> {
    val format = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())
    return finished.groupBy { YearMonth.from(Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault())) }
        .map { (month, workouts) -> format.format(month) to workouts }
}

@Composable
private fun HistoryWorkoutRow(workout: Workout, onClick: () -> Unit) {
    val date = Instant.ofEpochMilli(workout.startedAt).atZone(ZoneId.systemDefault())
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DayBlock(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), "${date.dayOfMonth}",
            formatDate(workout.startedAt))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(workout.name, style = MaterialTheme.typography.titleMedium)
            Text(
                (if (workout.extra) "Outside the plan" else phaseShort(workout.phase)) +
                    " · ${setsSummary(workout)}",
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (workout.notes.isNotBlank()) Text(
                workout.notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Chevron()
    }
}

/** Trailing mark of a row that opens another screen; decorative, since the row carries the words. */
@Composable
private fun Chevron() {
    RebuildIcon(R.drawable.ic_chevron_right, contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
}

@Composable
internal fun WorkoutDetailScreen(env: AppEnv, workout: Workout, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    var editingExercise by rememberSaveable(workout.id) { mutableStateOf<String?>(null) }
    var editingSet by rememberSaveable(workout.id) { mutableStateOf<String?>(null) }
    var addingSetId by rememberSaveable(workout.id) { mutableStateOf<String?>(null) }
    var editingEffort by rememberSaveable(workout.id) { mutableStateOf<String?>(null) }
    var addingEffortId by rememberSaveable(workout.id) { mutableStateOf<String?>(null) }
    var groupExercise by rememberSaveable(workout.id) { mutableStateOf<String?>(null) }
    var groupId by rememberSaveable(workout.id) { mutableStateOf<String?>(null) }
    var addingExercise by rememberSaveable(workout.id) { mutableStateOf(false) }
    var askingPerLeg by rememberSaveable(workout.id) { mutableStateOf<String?>(null) }
    var removingEntry by rememberSaveable(workout.id) { mutableStateOf<String?>(null) }
    var notice by rememberSaveable(workout.id) { mutableStateOf<String?>(null) }
    // An exercise to bring into view: one picked again, or one just added once it appears.
    var revealEntry by rememberSaveable(workout.id) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(revealEntry, workout.exercises.map { it.id }) {
        val index = workout.exercises.indexOfFirst { it.id == revealEntry }
        if (index >= 0) {
            // The summary and the note come first.
            listState.animateScrollToItem(index + 2)
            revealEntry = null
        }
    }
    fun addForgotten(exerciseId: String, perLeg: Boolean?) {
        val entryId = newId()
        notice = null
        env.runner.run(Action.AddWorkoutExercise(workout.id, entryId, exerciseId, perLeg), onError = { notice = it }) {
            revealEntry = entryId
            // The point of adding it here is to log what was done, so its first set or effort opens right away.
            if (env.exercise(exerciseId)?.measured == true) addingEffortId = newId() else addingSetId = newId()
            editingExercise = entryId
            editingSet = null
            editingEffort = null
        }
    }
    val drafts = rememberNoteDrafts(workout.id)
    val dirtyNotes = drafts.dirty(workout)
    // Route to open after the unsaved-notes question, or LEAVE_BACK_ROUTE to go back.
    var leaving by rememberSaveable(workout.id) { mutableStateOf<String?>(null) }
    fun leave(target: String) {
        when {
            dirtyNotes.isNotEmpty() -> leaving = target
            target == LEAVE_BACK_ROUTE -> onBack()
            else -> onNavigate(target)
        }
    }
    BackHandler(enabled = dirtyNotes.isNotEmpty() && leaving == null) { leaving = LEAVE_BACK_ROUTE }
    ScreenScaffold(title = workout.name, subtitle = formatDate(workout.startedAt), onBack = { leave(LEAVE_BACK_ROUTE) }) {
        LazyBody(state = listState) {
            item(key = "summary") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Column(Modifier.semantics(mergeDescendants = true) {}) {
                        // An extra workout says it is outside the plan in its week line below.
                        if (!workout.extra) Text(workout.phase.title, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            formatTime(workout.startedAt) + (workout.finishedAt?.let { "–${formatTime(it)}" } ?: "") +
                                " · ${setsSummary(workout)}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    WeekCredit(env, workout)
                    // A finished workout can seed a library routine; the workout itself never changes.
                    if (workout.finishedAt != null && workout.exercises.isNotEmpty()) {
                        val full = env.state.routines.size >= MAX_ROUTINES
                        SecondaryTextButton(onClick = { leave(Routes.routineFrom(workout.id)) },
                            enabled = !full && !env.runner.busy, icon = R.drawable.ic_library,
                            modifier = Modifier.testTag("save-as-routine")) { Text("Save as routine") }
                        if (full) Text(
                            "Library full at $MAX_ROUTINES routines. Delete one to save another.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item(key = "workout-note") { WorkoutNote(env, workout, drafts) }
            items(workout.exercises, key = { "exercise/${it.id}" }) { we ->
                val measured = we.exercise.measured
                var noteOpen by rememberSaveable(we.id) { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider()
                    HistoryExerciseHeader(
                        workout, we,
                        busy = env.runner.busy,
                        onOpenHistory = { leave(Routes.exerciseHistory(we.exercise)) },
                        onAddMissed = {
                            if (measured) {
                                addingEffortId = newId()
                                editingEffort = null
                            } else {
                                addingSetId = newId()
                                editingSet = null
                            }
                            editingExercise = we.id
                        },
                        onAddNote = { noteOpen = true },
                        // Only exercises added to this workout can be taken out; planned ones keep their target.
                        onRemove = if (we.optional && workout.exercises.size > 1) {
                            { removingEntry = we.id }
                        } else null
                    )
                    if (measured) {
                        HistoryEffortTable(we, onEdit = {
                            addingEffortId = null
                            editingExercise = we.id
                            editingEffort = it.id
                        })
                    } else {
                        HistorySetTable(we, onEdit = {
                            addingSetId = null
                            editingExercise = we.id
                            editingSet = it.id
                        }, onGroup = {
                            groupExercise = we.id
                            groupId = it
                        })
                    }
                    HistoryExerciseNote(env, workout.id, we, drafts, noteOpen) { noteOpen = it }
                }
            }
            item(key = "add-exercise") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HorizontalDivider()
                    notice?.let { NoticeLine(it) { notice = null } }
                    SecondaryTextButton(
                        onClick = { addingExercise = true },
                        enabled = !env.runner.busy,
                        icon = R.drawable.ic_plus,
                        modifier = Modifier.fillMaxWidth().testTag("history-add-exercise")
                    ) { Text("Add forgotten exercise", modifier = Modifier.weight(1f)) }
                    if (!workout.extra) Text(
                        "Never changes the plan or this workout's week.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    if (addingExercise) ExerciseLibraryPicker(
        env,
        onDismiss = { addingExercise = false },
        title = "Add forgotten exercise",
        marked = workout.exercises.map { it.exercise.id }.toSet()
    ) { exercise ->
        addingExercise = false
        val existing = workout.exercises.find { it.exercise.id == exercise.id }
        when {
            existing != null -> {
                notice = "${existing.exercise.name} is already in this workout. " +
                    "Add its missed ${effortNoun(existing.exercise.measure)}s there."
                revealEntry = existing.id
            }
            env.state.asksPerLeg(exercise) -> askingPerLeg = exercise.id
            else -> addForgotten(exercise.id, null)
        }
    }
    askingPerLeg?.let { id ->
        PerLegQuestionDialog(env.exercise(id)?.name ?: id, onDismiss = { askingPerLeg = null }) { perLeg ->
            askingPerLeg = null
            addForgotten(id, perLeg)
        }
    }
    workout.exercises.find { it.id == removingEntry }?.let { entry ->
        RemoveEntryDialog(env, workout, entry, onDismiss = { removingEntry = null })
    }
    val target = leaving
    if (target != null) {
        UnsavedNotesDialog(
            env, workout, drafts, dirtyNotes,
            onKeepEditing = { leaving = null },
            onProceed = {
                leaving = null
                if (target == LEAVE_BACK_ROUTE) onBack() else onNavigate(target)
            }
        )
    }
    val groupOwner = workout.exercises.find { it.id == groupExercise }
    val openedGroup = groupId
    if (groupOwner != null && openedGroup != null) {
        GroupDialog(env, workout, groupOwner, openedGroup, onDismiss = { groupExercise = null; groupId = null })
    }
    val we = workout.exercises.find { it.id == editingExercise }
    if (we != null && !we.exercise.measured && (editingSet != null || addingSetId != null)) {
        val set = we.sets.find { it.id == editingSet }
        if (set != null || addingSetId != null) {
            SetEditDialog(
                env, workout.id, we, set,
                newSetTime = workout.finishedAt ?: env.clock(),
                newSetId = addingSetId,
                onDismiss = { editingSet = null; editingExercise = null; addingSetId = null }
            )
        }
    }
    if (we != null && we.exercise.measured && (editingEffort != null || addingEffortId != null)) {
        val effort = we.efforts.find { it.id == editingEffort }
        if (effort != null || addingEffortId != null) {
            // A correction: it never starts a rest or the stopwatch, and a missed effort is dated with the workout.
            EffortEditDialog(
                env, workout.id, we, effort,
                newEffortTime = workout.finishedAt ?: env.clock(),
                newEffortId = addingEffortId,
                onDismiss = { editingEffort = null; editingExercise = null; addingEffortId = null }
            )
        }
    }
}

/**
 * An exercise's header in a finished workout: tapping it opens the exercise's history. Corrections that add to it
 * (a missed set or effort, a note) and taking an added exercise out sit in its options menu.
 */
@Composable
private fun HistoryExerciseHeader(
    workout: Workout,
    we: WorkoutExercise,
    busy: Boolean,
    onOpenHistory: () -> Unit,
    onAddMissed: () -> Unit,
    onAddNote: () -> Unit,
    onRemove: (() -> Unit)?
) {
    val name = we.exercise.name
    val loading = recordedLoadingDescription(we)
    val target = historyTargetText(workout, we)
    var menu by rememberSaveable(we.id) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .clickable(onClickLabel = "Open exercise history", onClick = onOpenHistory)
                .clearAndSetSemantics {
                    heading()
                    contentDescription = "$name, $loading, $target"
                }
                .testTag("history-exercise-${we.id}")
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OrderBadge(workout.exercises.indexOf(we) + 1, met = we.goalMet())
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(loading, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(target, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // At large text the name needs the room; the whole header still opens the history.
            if (LocalDensity.current.fontScale <= 1.3f) Chevron()
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.testTag("history-options-${we.id}")) {
                RebuildIcon(R.drawable.ic_ellipsis_vertical, contentDescription = "Options for $name",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                val missed = "Add missed ${effortNoun(we.exercise.measure)}"
                DropdownMenuItem(
                    text = { Text(missed) },
                    onClick = { menu = false; onAddMissed() },
                    enabled = !busy,
                    leadingIcon = { RebuildIcon(R.drawable.ic_plus, contentDescription = null) },
                    modifier = Modifier.semantics { contentDescription = "$missed for $name" }
                )
                if (we.notes.isBlank()) DropdownMenuItem(
                    text = { Text("Add exercise note") },
                    onClick = { menu = false; onAddNote() },
                    leadingIcon = { RebuildIcon(R.drawable.ic_notebook_pen, contentDescription = null) },
                    modifier = Modifier.testTag("edit-exercise-note")
                        .semantics { contentDescription = "Add exercise note for $name" }
                )
                if (onRemove != null) DropdownMenuItem(
                    text = { Text("Remove from this workout") },
                    onClick = { menu = false; onRemove() },
                    enabled = !busy,
                    leadingIcon = { RebuildIcon(R.drawable.ic_x, contentDescription = null) },
                    modifier = Modifier.testTag("history-remove-${we.id}")
                        .semantics { contentDescription = "Remove $name from this workout" }
                )
            }
        }
    }
}

/**
 * A finished exercise's note: shown only when there is one, with its own Edit. The field opens from the options
 * menu's Add exercise note, from Edit, or while a draft is unsaved; it saves and cancels like the workout's note.
 */
@Composable
private fun HistoryExerciseNote(
    env: AppEnv,
    workoutId: String,
    we: WorkoutExercise,
    drafts: NoteDrafts,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit
) {
    val note = drafts.text(we.id, we.notes)
    var error by rememberSaveable(we.id) { mutableStateOf<String?>(null) }
    val focus = remember(we.id) { FocusRequester() }
    if (!open && note == we.notes) {
        if (note.isBlank()) return
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 4.dp).semantics(mergeDescendants = true) {}) {
                Text("Note", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("exercise-note-content"))
            }
            IconButton(onClick = { onOpenChange(true) }, modifier = Modifier.testTag("edit-exercise-note")) {
                RebuildIcon(R.drawable.ic_pencil, contentDescription = "Edit exercise note for ${we.exercise.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = note,
            onValueChange = {
                onOpenChange(true)
                drafts.edit(we.id, we.notes, limitUserText(it, MAX_NOTE_CHARS))
            },
            label = { Text("Exercise note") },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .testTag("exercise-note")
        )
        LaunchedEffect(open) { if (open) focus.requestFocus() }
        ErrorText(error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    error = null
                    env.runner.run(Action.ExerciseNote(workoutId, we.id, note), onError = { error = it }) {
                        drafts.clear(we.id)
                        onOpenChange(false)
                    }
                },
                enabled = note != we.notes && !env.runner.busy
            ) { Text("Save note") }
            TextButton(
                onClick = { drafts.clear(we.id); error = null; onOpenChange(false) },
                enabled = !env.runner.busy
            ) { Text("Cancel note edit") }
        }
    }
}

/** Read-first workout note: saved text with Add/Edit; the field opens on request or while a draft is unsaved. */
@Composable
private fun WorkoutNote(env: AppEnv, workout: Workout, drafts: NoteDrafts) {
    val note = drafts.text(WORKOUT_NOTE_KEY, workout.notes)
    var error by rememberSaveable(workout.id) { mutableStateOf<String?>(null) }
    var open by rememberSaveable(workout.id) { mutableStateOf(false) }
    var focusPending by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    fun startEditing() {
        open = true
        focusPending = true
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (open || note != workout.notes) {
            OutlinedTextField(
                value = note,
                onValueChange = { drafts.edit(WORKOUT_NOTE_KEY, workout.notes, limitUserText(it, MAX_NOTE_CHARS)) },
                label = { Text("Workout note") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .testTag("workout-note")
            )
            ErrorText(error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        error = null
                        env.runner.run(Action.WorkoutNote(workout.id, note), onError = { error = it }) {
                            drafts.clear(WORKOUT_NOTE_KEY)
                            open = false
                        }
                    },
                    enabled = note != workout.notes && !env.runner.busy
                ) { Text("Save note") }
                TextButton(
                    onClick = {
                        drafts.clear(WORKOUT_NOTE_KEY)
                        error = null
                        open = false
                    },
                    enabled = !env.runner.busy
                ) { Text("Cancel") }
            }
            LaunchedEffect(focusPending) {
                if (focusPending) {
                    focus.requestFocus()
                    focusPending = false
                }
            }
        } else if (workout.notes.isNotBlank()) {
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Text("Workout note", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(workout.notes, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("workout-note-text"))
            }
            SecondaryTextButton(onClick = { startEditing() }, icon = R.drawable.ic_pencil) { Text("Edit workout note") }
        } else {
            SecondaryTextButton(onClick = { startEditing() }, icon = R.drawable.ic_notebook_pen) { Text("Add workout note") }
        }
    }
}

private fun workingSetsLabel(count: Int) = "$count working ${if (count == 1) "set" else "sets"}"

/**
 * "3 working sets". A workout with only warm-ups also says how many, so its saved work never looks empty. Timed,
 * distance and cardio work is counted on its own ("3 working sets, 2 timed sets, 1 cardio effort"), never as sets.
 */
private fun setsSummary(workout: Workout): String {
    val working = workout.workingSetCount()
    val warmups = workout.warmupSetCount()
    val efforts = effortCounts(workout)
    if (efforts.isNotEmpty()) {
        return (listOfNotNull(working.takeIf { it > 0 }?.let(::workingSetsLabel)) + efforts).joinToString(", ")
    }
    return workingSetsLabel(working) + if (working == 0 && warmups > 0) ", ${countLabel(warmups, "warm-up set")}" else ""
}

/**
 * A finished exercise's sets in the order logged, styled like the workout ledger: W or the working-set number,
 * the load in the unit that set was recorded in, then RIR. A drop or rest-pause set is one numbered row with its
 * stages. Every row opens its correction.
 */
@Composable
private fun HistorySetTable(we: WorkoutExercise, onEdit: (LoggedSet) -> Unit, onGroup: (String) -> Unit) {
    val workingDone = we.workingCount()
    val warmups = we.sets.count { it.warmup }
    val inProgress = we.openGroup() != null
    Text(
        "Working sets: $workingDone" + (we.targetSets?.let { " of $it" } ?: "") +
            (if (inProgress) " · 1 in progress" else "") +
            if (warmups > 0) " · $warmups warm-up (not counted)" else "",
        style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
        modifier = Modifier.testTag("working-count")
    )
    if (we.upperSets != null && workingDone > we.upperSets) {
        Text(
            "Above the advisory upper of ${we.upperSets}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    if (we.sets.isEmpty()) {
        Text("No sets logged yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val entries = we.workEntries()
    val groupEntries = entries.filterIsInstance<WorkEntry.Group>().associateBy { it.group.id }
    Column {
        var workingIndex = 0
        var first = true
        we.sets.forEach { set ->
            val group = set.groupId?.let(groupEntries::get)
            // A group is one row, at the position of its first stage.
            if (group != null && group.stages.first().id != set.id) return@forEach
            if (!first) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            first = false
            if (!set.warmup) workingIndex++
            val label = if (set.warmup) "Warm-up" else "Set $workingIndex"
            val extra = !set.warmup && we.targetSets?.let { workingIndex > it } == true
            if (group != null) {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("$workingIndex", style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp).clearAndSetSemantics {})
                    GroupRow(group, label, we.exercise, extra, onOpen = { onGroup(group.group.id) }, modifier = Modifier.weight(1f))
                }
                return@forEach
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(onClickLabel = "Edit $label") { onEdit(set) }
                    .semantics(mergeDescendants = true) { contentDescription = "$label: ${setText(set, we.exercise)}" }
                    .testTag("set-row")
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    if (set.warmup) "W" else "$workingIndex",
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp).clearAndSetSemantics {}
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        loadRepsText(set, we.exercise),
                        style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                        fontWeight = if (set.warmup) FontWeight.Normal else FontWeight.SemiBold,
                        color = if (set.warmup) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        rirShort(set) + if (set.warmup) " · warm-up" else if (extra) " · extra" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                RebuildIcon(R.drawable.ic_pencil, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Lazy screen body with the same padding as [ScrollBody], for long lists without nested scrolling. */
@Composable
internal fun ColumnScope.LazyBody(
    spacing: Dp = 12.dp,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit
) {
    LazyColumn(
        Modifier
            .weight(1f)
            .readableWidth()
            .yieldsToFixedControls(),
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content
    )
}

private const val LEAVE_BACK_ROUTE = "__back__"

/** One finished session of an exercise in one unit: its working entries in logged order, warm-ups excluded. */
internal data class HistorySession(val workout: Workout, val entries: List<WorkEntry>, val perLeg: Boolean = false) {
    /** Each working set's main set, the values the charts plot; a drop is part of its set and never plotted itself. */
    val sets: List<LoggedSet> get() = entries.filter { it.counted }.map { it.mainSet }
    /** Sets with drops or rest-pause stages, listed with every stage below the charts. */
    val groups: List<WorkEntry.Group> get() = entries.filterIsInstance<WorkEntry.Group>()
}

internal fun historySessions(workouts: List<Workout>, identity: Identity, unit: LoadUnit): List<HistorySession> =
    workouts.filter { it.finishedAt != null }.sortedBy { it.startedAt }.flatMap { workout ->
        workout.exercises.filter { it.exercise.identity() == identity }
            .map { HistorySession(workout, it.workEntries().filter { entry -> entry.unit == unit }, it.perLeg) }
    }.filter { it.entries.isNotEmpty() }

private fun entryText(entry: WorkEntry, exercise: Exercise): String = when (entry) {
    is WorkEntry.Single -> setText(entry.set, exercise)
    is WorkEntry.Group -> groupText(entry, exercise)
}

@Composable
internal fun ExerciseHistoryScreen(
    env: AppEnv,
    exerciseId: String,
    loading: LoadingType,
    perDumbbell: Boolean,
    onBack: () -> Unit,
    onOpenWorkout: (String) -> Unit = {}
) {
    val identity = Identity(exerciseId, loading, perDumbbell)
    val exercise = env.state.workouts.asSequence().flatMap { it.exercises.asSequence() }
        .map { it.exercise }.lastOrNull { it.identity() == identity }
        ?: env.exercise(exerciseId)?.takeIf { it.identity() == identity }
        ?: Exercise(exerciseId, exerciseId, loading, perDumbbell)
    // An exercise's measure never changes, so its history is either sets of reps or efforts, never both.
    if (exercise.measured) {
        MeasuredExerciseHistory(env, identity, exercise, onBack)
        return
    }
    val recordedUnits = env.state.workouts.filter { it.finishedAt != null }
        .flatMap { it.exercises }.filter { it.exercise.identity() == identity }
        .flatMap { it.sets }.filterNot { it.warmup }.map { it.loadUnit }.distinct()
    var unit by rememberSaveable(identity) { mutableStateOf(recordedUnits.singleOrNull() ?: LoadUnit.KG) }
    val sessions = historySessions(env.state.workouts, identity, unit)

    ScreenScaffold(title = exercise.name, subtitle = loadingDescription(exercise, unit), onBack = onBack) {
        // Each unit is its own series; the choice appears only when more than one was recorded.
        if (recordedUnits.size > 1) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                LoadUnitSelector(unit, recordedUnits.sortedBy { it.ordinal }) { unit = it }
            }
        }
        LazyBody {
            item(key = "about") {
                Text(
                    "Working sets exactly as logged. Warm-ups excluded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (sessions.isEmpty()) {
                item(key = "empty") { Text("No working sets logged yet.") }
                return@LazyBody
            }
            item(key = "latest") {
                val latest = sessions.last()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .semantics(mergeDescendants = true) {}
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("Latest · ${formatDate(latest.workout.startedAt)}", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    latest.entries.forEach {
                        Text(entryText(it, exercise), style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Medium)
                    }
                    Text("${countLabel(sessions.size, "session")} since ${formatDate(sessions.first().workout.startedAt)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Stages aren't separate sets, so charts plot normal working sets only; groups are listed below.
            val charted = sessions.filter { it.sets.isNotEmpty() }
            if (sessions.any { it.groups.isNotEmpty() }) {
                item(key = "groups-note") {
                    Text(
                        "Charts plot each set as lifted; its drops are listed with it below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("charts-normal-only")
                    )
                }
            }
            val kgSeries = charted.map { s -> s.sets.mapNotNull { it.loadValue } }
            if (kgSeries.any { it.isNotEmpty() }) {
                item(key = "kg-chart") {
                    SetChart(loadChartTitle(exercise, unit), kgSeries, charted.map { it.workout.startedAt },
                        readFormat = { chartLoadText(it, unit, exercise) })
                }
            }
            if (charted.isNotEmpty()) item(key = "reps-chart") {
                val perLeg = charted.map { it.perLeg }
                SetChart(
                    "Reps" + when {
                        perLeg.all { it } -> " (per leg)"
                        perLeg.any { it } -> " (per leg in ${perLeg.count { it }} of ${perLeg.size} sessions)"
                        else -> ""
                    },
                    charted.map { s -> s.sets.map { it.reps.toDouble() } },
                    charted.map { it.workout.startedAt },
                    readFormat = { countLabel(it.toInt(), "rep") }
                )
            }
            val records = repRecords(env.state.workouts, unit) { it.exercise.identity() == identity }
            if (records.isNotEmpty()) item(key = "records") {
                RecordsSection(records, unit, exercise) { onOpenWorkout(it.id) }
            }
            item(key = "sessions-header") { SectionHeader("All sessions") }
            // When only some sessions counted reps per leg, each of those says so; reps are never converted.
            val mixedPerLeg = sessions.any { it.perLeg } && !sessions.all { it.perLeg }
            itemsIndexed(sessions.asReversed()) { i, s ->
                Column {
                    if (i > 0) HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {},
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(formatDate(s.workout.startedAt), style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.8f))
                        Column(Modifier.weight(1f)) {
                            s.entries.forEach {
                                Text(entryText(it, exercise), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
                            }
                            if (mixedPerLeg && s.perLeg) {
                                Text("Reps per leg", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One plotted load in its unit: "5 plates", "42.5 kg/DB", "95 lb". */
internal fun chartLoadText(value: Double, unit: LoadUnit, exercise: Exercise): String = when {
    unit == LoadUnit.PLATES -> countLabel(value.toInt(), "plate")
    exercise.perDumbbell -> "${formatKg(value)} ${unit.massSymbol()}/DB"
    else -> "${formatKg(value)} ${unit.massSymbol()}"
}

/** "Weight · kg per dumbbell", "Load · plates per stack": a load chart's title in the unit it plots. */
internal fun loadChartTitle(exercise: Exercise, unit: LoadUnit): String {
    val symbol = unit.massSymbol()
    val kgUnit = if (unit == LoadUnit.PLATES) "plates per stack" else when (exercise.loading) {
        LoadingType.EXTERNAL -> if (exercise.perDumbbell) "$symbol per dumbbell" else symbol
        LoadingType.BODYWEIGHT -> symbol
        LoadingType.ASSISTANCE -> "assistance $symbol (lower is harder)"
    }
    return "${if (unit == LoadUnit.PLATES) "Load" else "Weight"} · $kgUnit"
}

/**
 * Dots for every logged value, one column per session, labelled with [format]. Text list below is the equivalent.
 * Touching it selects a session: a guide marks its column and the readout above lists what was done that day.
 */
@Composable
internal fun SetChart(
    title: String,
    sessions: List<List<Double>>,
    dates: List<Long>,
    format: (Double) -> String = ::formatKg,
    modifier: Modifier = Modifier,
    /** The readout's form of a value, with its unit ("42.5 kg/DB", "12 reps"); the axis keeps [format]. */
    readFormat: (Double) -> String = format
) {
    val values = sessions.flatten()
    if (values.isEmpty()) return
    val firstDate = dates.first()
    val lastDate = dates.last()
    val min = values.min()
    val max = values.max()
    val flat = max <= min
    val span = if (flat) 1.0 else max - min
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val guide = MaterialTheme.colorScheme.outline
    val describe = "$title chart: ${sessions.size} sessions, values from ${format(min)} to " +
        "${format(max)}, ${formatDate(firstDate)} to ${formatDate(lastDate)}. Latest session: ${sessions.last().joinToString { format(it) }}."
    // Taller with larger text so scaled axis labels keep their room beside the plot.
    val chartHeight = 160.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.25f)
    val axisStyle = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    // The plot is inset by half a label's line height, so each axis label is centred on its gridline.
    val density = LocalDensity.current
    val inset = with(density) { axisStyle.lineHeight.toDp() } / 2
    val selection = rememberChartSelection(sessions to dates)
    val shown = selection.value ?: sessions.lastIndex
    val readValue = sessionText(sessions[shown], readFormat)
    val readDetail = "${formatDate(dates[shown])} · ${countLabel(sessions[shown].size, "set")}"
    val xs = remember(sessions.size, inset) {
        { width: Int ->
            val pad = with(density) { inset.toPx() }
            val w = width - pad * 2
            List(sessions.size) { i -> pad + if (sessions.size > 1) w / (sessions.size - 1) * i else w / 2 }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
        ChartReadout(readValue, readDetail, touched = selection.value != null || sessions.size < 2)
        Row {
            Column(
                Modifier
                    .height(chartHeight)
                    .padding(end = 8.dp)
                    .clearAndSetSemantics {},
                verticalArrangement = if (flat) Arrangement.Center else Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(format(max), style = axisStyle, color = axisColor)
                // A midline label only when it is a value someone could log, never "9.5 reps".
                val mid = (min + max) / 2.0
                if (!flat && mid == kotlin.math.round(mid)) Text(format(mid), style = axisStyle, color = axisColor)
                else if (!flat) Text("", style = axisStyle)
                if (!flat) Text(format(min), style = axisStyle, color = axisColor)
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .height(chartHeight)
                    .chartSemantics(describe, sessions.size, selection.value, "$readValue, $readDetail") { selection.value = it }
                    .chartTouch(sessions.size, xs) { selection.value = it }
            ) {
                val pad = inset.toPx()
                val h = size.height - pad * 2
                val w = size.width - pad * 2
                listOf(0f, 0.5f, 1f).forEach { f ->
                    val y = pad + h * f
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
                        pathEffect = if (f == 0.5f) PathEffect.dashPathEffect(floatArrayOf(8f, 8f)) else null)
                }
                val step = if (sessions.size > 1) w / (sessions.size - 1) else 0f
                fun xOf(i: Int) = pad + if (sessions.size > 1) step * i else w / 2
                fun yOf(v: Double) = pad + if (flat) h / 2 else h * (1f - ((v - min) / span).toFloat())
                selection.value?.let { i -> drawLine(guide, Offset(xOf(i), 0f), Offset(xOf(i), size.height), strokeWidth = 1.dp.toPx()) }
                // A faint line through each session's top value, to read the trend; the dots remain the data.
                if (sessions.size > 1) {
                    val tops = sessions.mapIndexedNotNull { i, set -> set.maxOrNull()?.let { Offset(xOf(i), yOf(it)) } }
                    tops.zipWithNext { a, b -> drawLine(color.copy(alpha = 0.3f), a, b, strokeWidth = 1.5.dp.toPx()) }
                }
                sessions.forEachIndexed { i, set ->
                    val x = xOf(i)
                    val emphasized = i == (selection.value ?: sessions.lastIndex)
                    set.forEach { v ->
                        val y = yOf(v)
                        drawCircle(if (emphasized) color else color.copy(alpha = 0.7f),
                            radius = (if (emphasized) 5 else 4).dp.toPx(), center = Offset(x, y))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDate(firstDate), style = axisStyle, color = axisColor)
            if (sessions.size > 1) Text(formatDate(lastDate), style = axisStyle, color = axisColor)
        }
    }
}
