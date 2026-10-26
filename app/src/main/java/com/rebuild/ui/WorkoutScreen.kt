package com.rebuild.ui

import com.rebuild.domain.loadUnitChoices
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rebuild.R
import com.rebuild.domain.Action
import com.rebuild.domain.dropTarget
import com.rebuild.domain.workEntries
import com.rebuild.domain.EntrySuggestion
import com.rebuild.domain.asksPerLeg
import com.rebuild.domain.nextAfter
import com.rebuild.domain.planSourceOf
import com.rebuild.domain.LoadUnit
import com.rebuild.domain.Measure
import com.rebuild.domain.SetMethod
import com.rebuild.domain.Stopwatch
import com.rebuild.domain.Workout
import com.rebuild.domain.goalReached
import com.rebuild.domain.hasGoal
import com.rebuild.domain.measured
import com.rebuild.domain.measuredGoal
import com.rebuild.domain.qualifyingCount
import com.rebuild.domain.recordCount
import com.rebuild.domain.scheduledRoutine
import com.rebuild.domain.WorkoutExercise
import com.rebuild.domain.canSetLoadUnit
import com.rebuild.domain.limitUserText
import com.rebuild.domain.nextEntrySuggestion
import com.rebuild.domain.openGroup
import com.rebuild.domain.openGroupExercise
import com.rebuild.domain.previousComparable
import com.rebuild.domain.resumeExercise
import com.rebuild.domain.stages
import com.rebuild.domain.warmupSetCount
import com.rebuild.domain.weekStart
import com.rebuild.domain.workingCount

private const val LEAVE_BACK = "back"
private const val LEAVE_FINISH = "finish"


@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutScreen(env: AppEnv, workout: Workout, onBack: () -> Unit) {
    // Resuming opens a set in progress, else the stopwatch's exercise, else the first exercise still short of its
    // target: the same one Today points to. If the workout is replaced while open (a restore), the exercise in view
    // stays when it still exists; otherwise focus returns there.
    var focusedId by rememberSaveable { mutableStateOf(opensAt(env.state, workout)?.id) }
    // An exercise just added opens as soon as it appears, even if the new state arrives a frame after the save.
    var pendingFocus by rememberSaveable(workout.id) { mutableStateOf<String?>(null) }
    val focusedIndex = workout.exercises.indexOfFirst { it.id == focusedId }.takeIf { it >= 0 }
        ?: workout.exercises.indexOfFirst { it.id == opensAt(env.state, workout)?.id }.coerceAtLeast(0)
    LaunchedEffect(workout.exercises.map { it.id }, pendingFocus) {
        val pending = pendingFocus
        if (pending != null && workout.exercises.any { it.id == pending }) {
            focusedId = pending
            pendingFocus = null
        }
    }
    LaunchedEffect(workout.id, focusedId, pendingFocus) {
        if (pendingFocus == null && workout.exercises.none { it.id == focusedId }) {
            focusedId = opensAt(env.state, workout)?.id
        }
    }
    val focused = workout.exercises.getOrNull(focusedIndex)
    var menuOpen by rememberSaveable(workout.id) { mutableStateOf(false) }
    var editingNote by rememberSaveable(workout.id) { mutableStateOf(false) }
    var finishing by rememberSaveable(workout.id) { mutableStateOf(false) }
    var discarding by rememberSaveable(workout.id) { mutableStateOf(false) }
    var adding by rememberSaveable(workout.id) { mutableStateOf(false) }
    var reordering by rememberSaveable(workout.id) { mutableStateOf(false) }
    var notice by rememberSaveable(workout.id) { mutableStateOf<String?>(null) }
    var askingPerLeg by rememberSaveable(workout.id) { mutableStateOf<String?>(null) }
    // A rep-based exercise being added, with the per-leg answer when one was asked, waiting for its working sets.
    var askingSets by rememberSaveable(workout.id) { mutableStateOf<Pair<String, Boolean?>?>(null) }
    // Typing a set hides the exercise rail so the entry and its comparison keep the space; it returns with the IME.
    var entryFocused by remember { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Exercise note drafts live here so switching exercises keeps them; leaving asks first.
    val drafts = rememberNoteDrafts(workout.id)
    val dirtyNotes = drafts.dirty(workout)
    var leaving by rememberSaveable(workout.id) { mutableStateOf<String?>(null) }
    fun guarded(target: String, go: () -> Unit) {
        if (dirtyNotes.isEmpty()) {
            go()
        } else {
            leaving = target
        }
    }
    BackHandler(enabled = dirtyNotes.isNotEmpty() && leaving == null) { leaving = LEAVE_BACK }

    fun select(id: String) {
        notice = null
        focusedId = id
    }

    fun addEntry(exerciseId: String, perLeg: Boolean?, sets: Int? = null) {
        val entryId = newId()
        notice = null
        env.runner.run(Action.AddWorkoutExercise(workout.id, entryId, exerciseId, perLeg, sets), onError = { notice = it }) {
            pendingFocus = entryId
        }
    }

    ScreenScaffold(
        title = workout.name,
        subtitle = (if (workout.extra) "Outside your plan" else phaseShort(workout.phase)) +
            " · started ${formatTime(workout.startedAt)}",
        onBack = { guarded(LEAVE_BACK, onBack) },
        compactWhenKeyboard = true,
        actions = {
            TextButton(onClick = { guarded(LEAVE_FINISH) { finishing = true } }, enabled = !env.runner.busy) { Text("Finish") }
            IconButton(onClick = { menuOpen = true }) {
                RebuildIcon(R.drawable.ic_ellipsis_vertical, contentDescription = "More workout actions")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(if (workout.notes.isBlank()) "Add workout note" else "Workout note") },
                    leadingIcon = { RebuildIcon(R.drawable.ic_notebook_pen, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        editingNote = true
                    },
                    modifier = Modifier.testTag("menu-workout-note")
                )
                DropdownMenuItem(
                    text = { Text("Add exercise") },
                    enabled = !env.runner.busy,
                    onClick = {
                        menuOpen = false
                        adding = true
                    },
                    modifier = Modifier.testTag("menu-add-exercise")
                )
                if (workout.exercises.size >= 2) DropdownMenuItem(
                    text = { Text("Reorder exercises") },
                    onClick = {
                        menuOpen = false
                        reordering = true
                    },
                    modifier = Modifier.testTag("menu-reorder")
                )
                DropdownMenuItem(
                    text = { Text("Discard workout") },
                    onClick = {
                        menuOpen = false
                        discarding = true
                    }
                )
            }
        }
    ) {
        val watch = env.state.stopwatch?.takeIf { it.workoutId == workout.id }
        val keyboardUp = WindowInsets.isImeVisible && (landscape || entryFocused)
        if (workout.exercises.isNotEmpty() && !keyboardUp) {
            ExerciseRail(workout, focusedIndex, watch, onAdd = { adding = true }, addEnabled = !env.runner.busy,
                onSelect = ::select)
        }
        // An unlogged stopwatch effort on another exercise stays in sight until it's logged or discarded.
        if (watch != null && !watch.running && watch.exerciseId != focused?.id && !WindowInsets.isImeVisible) {
            StopwatchStrip(workout, watch) { select(watch.exerciseId) }
        }
        notice?.let { NoticeLine(it) { notice = null } }
        HorizontalDivider()
        if (focused == null) {
            EmptyWorkoutBody(onAdd = { adding = true }, enabled = !env.runner.busy,
                modifier = Modifier.weight(1f).yieldsToFixedControls())
        } else {
            // Next passes over exercises skipped today; they stay one tap away in the rail.
            val next = workout.nextAfter(focusedIndex)
            val onNext: (() -> Unit)? = next?.let { n -> { select(n.id) } }
            // A fresh composition per exercise: no content, entry or dialog state can outlive a switch.
            key(focused.id) {
                ExerciseFocus(
                    env, workout, focused, focusedIndex, drafts, onNext, nextName = next?.exercise?.name,
                    onFinish = { guarded(LEAVE_FINISH) { finishing = true } },
                    onEntryFocus = { entryFocused = it },
                    onSelect = ::select,
                    modifier = Modifier.weight(1f).yieldsToFixedControls()
                )
            }
        }
    }

    if (adding) ExerciseLibraryPicker(
        env,
        onDismiss = { adding = false },
        title = "Add exercise",
        marked = workout.exercises.map { it.exercise.id }.toSet()
    ) { exercise ->
        adding = false
        // The same exercise twice would split its sets; the one already here opens instead.
        val existing = workout.exercises.find { it.exercise.id == exercise.id }
        when {
            existing != null -> {
                focusedId = existing.id
                notice = "${existing.exercise.name} is already in this workout, so it's open now."
            }
            env.state.asksPerLeg(exercise) -> askingPerLeg = exercise.id
            exercise.measured -> addEntry(exercise.id, null)
            else -> askingSets = exercise.id to null
        }
    }
    askingPerLeg?.let { id ->
        PerLegQuestionDialog(env.exercise(id)?.name ?: id, onDismiss = { askingPerLeg = null }) { perLeg ->
            askingPerLeg = null
            askingSets = id to perLeg
        }
    }
    askingSets?.let { (id, perLeg) ->
        AddSetsDialog(env, id, onDismiss = { askingSets = null }) { sets ->
            askingSets = null
            addEntry(id, perLeg, sets)
        }
    }
    if (reordering) ReorderDialog(env, workout, onDismiss = { reordering = false })

    if (editingNote) NoteDialog("Workout note", workout.notes, env, onDismiss = { editingNote = false }) {
        Action.WorkoutNote(workout.id, it)
    }
    val target = leaving
    if (target != null) {
        UnsavedNotesDialog(
            env, workout, drafts, dirtyNotes,
            onKeepEditing = { leaving = null },
            onProceed = {
                leaving = null
                if (target == LEAVE_FINISH) {
                    finishing = true
                } else {
                    onBack()
                }
            }
        )
    }
    if (finishing) FinishWorkoutDialog(env, workout, onDismiss = { finishing = false }, onFinished = { finishing = false },
        onDiscard = { finishing = false; discarding = true })
    if (discarding) DiscardWorkoutDialog(env, workout, onDismiss = { discarding = false }, onDiscarded = { discarding = false })
}

/**
 * Exercise switcher: each pill shows its order, name and working-set progress; the focused one is filled. The last
 * item adds an exercise.
 */
@Composable
private fun ExerciseRail(
    workout: Workout,
    focusedIndex: Int,
    watch: Stopwatch?,
    onAdd: () -> Unit,
    addEnabled: Boolean,
    onSelect: (String) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(focusedIndex) { listState.animateScrollToItem(focusedIndex) }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.readableWidth().selectableGroup().testTag("exercise-picker")
    ) {
        itemsIndexed(workout.exercises, key = { _, it -> it.id }) { index, ex ->
            ExercisePill(index, workout.exercises.size, ex, selected = index == focusedIndex,
                watch = watch?.takeIf { it.exerciseId == ex.id }) { onSelect(ex.id) }
        }
        item(key = "add-exercise") { AddExercisePill(onAdd, addEnabled) }
    }
}

@Composable
private fun ExercisePill(
    index: Int,
    count: Int,
    we: WorkoutExercise,
    selected: Boolean,
    watch: Stopwatch?,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val met = we.goalMet()
    val skipped = we.showsSkipped()
    // A set in progress or a stopwatch effort not yet logged: there is more to come here.
    val inProgress = we.openGroup() != null || watch != null
    val progress = when {
        we.openGroup() != null -> ", a set in progress"
        watch?.running == true -> ", stopwatch timing"
        watch != null -> ", stopwatch effort not logged"
        else -> ""
    }
    val shape = MaterialTheme.shapes.small
    val large = LocalDensity.current.fontScale > 1.3f
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .widthIn(max = if (large) 280.dp else 208.dp)
            .clip(shape)
            .background(if (selected) colors.secondaryContainer else Color.Transparent)
            .border(1.dp, if (selected) colors.primary else colors.outlineVariant, shape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics {
                contentDescription = "Exercise ${index + 1} of $count: ${we.exercise.name}, " +
                    we.progressDescription() + progress
            }
            .testTag("exercise-chip-$index"),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            Modifier.clearAndSetSemantics {}.padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OrderBadge(index + 1, met = met, emphasized = selected)
            Text(
                we.exercise.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) colors.onSecondaryContainer else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                (if (skipped) "Skipped" else we.progressText()) + if (inProgress) " +" else "",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant
            )
        }
    }
}

/**
 * One exercise: what it is and today's target, then a ledger of this session against the last comparable one.
 * On a roomy portrait screen the next-set entry is pinned to the bottom; with large text, landscape or a short
 * window it sits inline after the target so every control stays reachable by scrolling.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseFocus(
    env: AppEnv,
    workout: Workout,
    we: WorkoutExercise,
    index: Int,
    drafts: NoteDrafts,
    onNext: (() -> Unit)?,
    nextName: String?,
    onFinish: () -> Unit,
    onEntryFocus: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val exercise = we.exercise
    // Reps counted per leg are never compared with a total count, or the other way round.
    val previous = env.state.previousComparable(exercise, workout.id, we.entryLoadUnit, we.perLeg)
    val previousWorkout = previous?.let { p -> env.state.workouts.find { w -> w.exercises.any { it.id == p.id } } }
    val open = we.openGroup()
    // New sets start after an open drop or rest-pause set, so another exercise's entry points back to it.
    val openElsewhere = workout.openGroupExercise()?.takeIf { it.id != we.id }
    // Last time's matching entry, else today's last normal set; an open set offers its own next stage. Never saved
    // without a tap.
    val initial = nextEntrySuggestion(we, previous, we.entryLoadUnit, null)
    // Any change to the sets, groups, efforts, exercise or suggestion (a save, a correction or a restore) starts a fresh
    // draft.
    val draftKey = listOf(we.id, exercise.identity(), we.entryLoadUnit, we.sets, we.setGroups, we.efforts,
        initial?.set?.id, initial?.set?.loadUnit, initial?.set?.loadValue, initial?.set?.reps, initial?.withReps,
        initial?.source).joinToString("|")
    var dropCarry by rememberSaveable(we.id) { mutableStateOf(DropCarry.NONE) }
    val draft = rememberEntryDraft(draftKey, initial, dropCarry)
    val suggestionFor: (SetMethod?) -> EntrySuggestion? = { method ->
        if (open != null || method == null) initial else nextEntrySuggestion(we, previous, we.entryLoadUnit, method)
    }
    val offered = suggestionFor(draft.method?.takeUnless { draft.warmup })
    // An exercise without a target or goal always offers its next set.
    val targetMet = we.goalMet()

    var editingSet by rememberSaveable(we.id) { mutableStateOf<String?>(null) }
    var editingEffort by rememberSaveable(we.id) { mutableStateOf<String?>(null) }
    var openGroupId by rememberSaveable(we.id) { mutableStateOf<String?>(null) }
    var adjusting by rememberSaveable(we.id) { mutableStateOf(false) }
    var editingGoal by rememberSaveable(we.id) { mutableStateOf(false) }
    var savingToPlan by rememberSaveable(we.id) { mutableStateOf(false) }
    var removing by rememberSaveable(we.id) { mutableStateOf(false) }
    var details by rememberSaveable(we.id) { mutableStateOf(false) }
    var choosingUnit by rememberSaveable(we.id) { mutableStateOf(false) }
    var unitError by rememberSaveable(we.id) { mutableStateOf<String?>(null) }
    var workspaceViewport by remember { mutableIntStateOf(0) }
    var workspaceFocused by remember { mutableStateOf(false) }
    var dockFocused by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val displayDensity = LocalContext.current.resources.displayMetrics.density
    // Decided from the window, never from the IME, so focusing a field can't move the entry it is in.
    val windowHeightDp = configuration.screenHeightDp * displayDensity / density.density
    val pinned = configuration.orientation != Configuration.ORIENTATION_LANDSCAPE &&
        density.fontScale <= 1.3f && windowHeightDp >= 600f
    val imeVisible = WindowInsets.isImeVisible
    val measured = exercise.measured
    val watch = env.state.stopwatch?.takeIf { it.workoutId == workout.id }
    val watchHere = watch.timesEntry(workout, we)
    // While the stopwatch times another exercise nothing new is logged here, so its way back takes the entry's place.
    val timingElsewhere = watch?.takeIf { it.running && !watchHere }

    @Composable fun Entry(viewportHeight: Int) {
        confinedKey(draftKey) {
            when {
                openElsewhere != null -> OpenGroupElsewhere(env, workout, openElsewhere) { onSelect(openElsewhere.id) }
                timingElsewhere != null -> StopwatchElsewhere(env, workout, timingElsewhere) {
                    onSelect(timingElsewhere.exerciseId)
                }
                watchHere -> MeasuredEntryPanel(env, workout, we, draft.extra, onCancelExtra = { draft.extra = false },
                    onGoTo = onSelect, viewportHeight = viewportHeight, onFocusChange = onEntryFocus)
                we.showsSkipped() -> SkippedPanel(env, workout, we, nextName, onNext, onFinish)
                measured && (!targetMet || draft.extra) -> MeasuredEntryPanel(env, workout, we, draft.extra,
                    onCancelExtra = { draft.extra = false }, onGoTo = onSelect, viewportHeight = viewportHeight,
                    onFocusChange = onEntryFocus)
                measured -> TargetMetPanel(nextName, onNext, onFinish, finishEnabled = !env.runner.busy,
                    message = if (we.optional || exercise.measure == Measure.CARDIO) "Goal done" else "Target done",
                    extraLabel = extraEffortLabel(exercise.measure)) { draft.extra = true }
                !targetMet || draft.extra || open != null ->
                    EntryPanel(env, workout, we, draft, offered, suggestionFor, viewportHeight, onEntryFocus,
                        onCarry = { dropCarry = it })
                else -> TargetMetPanel(nextName, onNext, onFinish, finishEnabled = !env.runner.busy,
                    message = if (we.optional) "Goal done" else "Target done",
                    dropLabel = we.dropTarget()?.let { target -> "Add drop to set ${we.workEntries().indexOf(target) + 1}" },
                    onDrop = { draft.extra = true; draft.drop = true; draft.kg = ""; draft.reps = "" }) {
                    dropCarry = DropCarry.NONE
                    draft.extra = true
                }
            }
        }
    }

    DockedLayout(
        dockShare = {
            when {
                imeVisible && dockFocused -> 1f
                // Editing a note: give the whole body to the note until the keyboard closes.
                imeVisible && workspaceFocused -> 0f
                else -> 0.6f
            }
        },
        modifier = modifier.fillMaxWidth(),
        body = {
            Column(
                Modifier
                    .fillMaxHeight()
                    // A short window (a phone on its side) keeps every column it has: narrowed, the entry would wrap
                    // into rows the keyboard leaves no room for.
                    .then(if (windowHeightDp >= 480f) Modifier.readableWidth() else Modifier.fillMaxWidth())
                    .onSizeChanged { workspaceViewport = it.height }
                    .onFocusChanged { workspaceFocused = it.hasFocus }
                    .focusGroup()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    // Without the dock, the last rows scroll clear of the gesture area.
                    .then(if (pinned) Modifier else Modifier.navigationBarsPadding()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // The rail above shows the position; it is still spoken with the name.
                    Text(exercise.name, style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics {
                            heading()
                            contentDescription = "${exercise.name}, exercise ${index + 1} of ${workout.exercises.size}"
                        })
                    Text(
                        loadingDescription(exercise, we.entryLoadUnit) + if (we.perLeg) " · reps per leg" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (we.entryLoadUnit == LoadUnit.PLATES) Text(
                        "Compare plates only on the same machine.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (env.state.canSetLoadUnit(workout.id, we.id)) {
                        SecondaryTextButton(onClick = { choosingUnit = true },
                            enabled = !env.runner.busy && open == null && !watchHere,
                            icon = R.drawable.ic_sliders_horizontal, modifier = Modifier.testTag("choose-load-unit")) {
                            Text("Load unit: ${unitName(we.entryLoadUnit)}")
                        }
                        if (open != null || watchHere) Text(
                            if (open != null) "The unit stays as it is until the ${open.method.title.lowercase()} is finished."
                            else "The unit stays as it is until the stopwatch's effort is logged or discarded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    targetLine(workout, we),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("target-line")
                )

                if (!pinned) Entry(workspaceViewport)

                if (measured) {
                    EffortLedger(env, workout, we, onEdit = { editingEffort = it.id })
                } else {
                    SetLedger(we, previous, previousWorkout?.startedAt, onEdit = { editingSet = it.id },
                        onGroup = { openGroupId = it })
                }

                // Secondary actions read as one compact list with aligned icons, below the sets they relate to.
                Column {
                    if (!targetMet && !we.showsSkipped() && open == null && onNext != null && nextName != null) {
                        SecondaryTextButton(onClick = onNext, icon = R.drawable.ic_arrow_right,
                            modifier = Modifier.fillMaxWidth().testTag("next-exercise")) {
                            Text("Next: $nextName", modifier = Modifier.weight(1f))
                        }
                    }
                    SecondaryTextButton(onClick = { details = !details }, icon = R.drawable.ic_clipboard_list,
                        modifier = Modifier.fillMaxWidth().testTag("exercise-details")) {
                        Text(
                            when {
                                details -> "Hide options"
                                // Cardio never rests by itself, so its rest is only mentioned with its options.
                                exercise.measure == Measure.CARDIO -> "Goal and options"
                                we.optional -> "Goal, rest and options · rest ${formatRest(we.restSeconds)}"
                                else -> "Target and options · rest ${formatRest(we.restSeconds)}"
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    AnimatedVisibility(details, enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                        exit = fadeOut(tween(150)) + shrinkVertically(tween(150))) {
                        // AnimatedVisibility stacks its children like a Box; the options are a column.
                        Column {
                            EntryOptions(
                                env, workout, we,
                                onAdjust = { adjusting = true },
                                onEditGoal = { editingGoal = true },
                                onSaveToPlan = { savingToPlan = true },
                                onRemove = { removing = true },
                                // Skipping moves on to the next exercise still to do, as Next would.
                                onSkipped = { onNext?.invoke() }
                            )
                        }
                    }
                    ExerciseNoteField(env, workout.id, we, drafts)
                }
            }
        },
        dock = { if (pinned) LogDock(onFocusChange = { dockFocused = it }) { viewport -> Entry(viewport) } }
    )

    val set = we.sets.find { it.id == editingSet }
    if (set != null) {
        SetEditDialog(env, workout.id, we, set, newSetTime = env.clock(), onDismiss = { editingSet = null })
    }
    val effort = we.efforts.find { it.id == editingEffort }
    if (effort != null) {
        EffortEditDialog(env, workout.id, we, effort, newEffortTime = env.clock(), onDismiss = { editingEffort = null })
    }
    openGroupId?.let { id -> GroupDialog(env, workout, we, id, onDismiss = { openGroupId = null }) }
    if (adjusting && !we.optional) AdjustTodayDialog(env, workout, we, onDismiss = { adjusting = false })
    if (editingGoal && we.optional) EntryGoalDialog(env, workout, we, onDismiss = { editingGoal = false })
    if (removing && we.optional) RemoveEntryDialog(env, workout, we, onDismiss = { removing = false })
    val template = env.state.scheduledRoutine(workout.phase, workout.templateId)
    if (savingToPlan && we.optional && template != null) {
        PlanAdditionDialog(env, workout.phase, template, we.exercise.id, existing = null, source = we,
            onDismiss = { savingToPlan = false })
    }
    if (choosingUnit) AdaptiveDialog(
        onDismissRequest = { choosingUnit = false },
        title = { Text("Load unit · ${exercise.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val choices = env.state.loadUnitChoices(workout.id, we.id)
                LoadUnitSelector(we.entryLoadUnit, choices, enabled = !env.runner.busy) { unit ->
                    unitError = null
                    env.runner.run(Action.SetLoadUnit(workout.id, we.id, unit), onError = { unitError = it }) {
                        choosingUnit = false
                    }
                }
                Text("Your next sets use it, and it's remembered for this exercise. Logged sets keep their own unit.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (LoadUnit.PLATES in choices) Text(PLATES_HELP, style = MaterialTheme.typography.bodySmall)
                ErrorText(unitError)
            }
        },
        confirmButton = { TextButton(onClick = { choosingUnit = false }) { Text("Done") } },
        dismissButton = {}
    )
}

@Composable
internal fun ExerciseNoteField(env: AppEnv, workoutId: String, we: WorkoutExercise, drafts: NoteDrafts) {
    val note = drafts.text(we.id, we.notes)
    var error by rememberSaveable(we.id) { mutableStateOf<String?>(null) }
    var editing by rememberSaveable(we.id) { mutableStateOf(false) }
    val focus = remember(we.id) { FocusRequester() }
    if (!editing && note == we.notes) {
        if (note.isNotBlank()) Text(note, modifier = Modifier.testTag("exercise-note-content"))
        val action = if (note.isBlank()) "Add exercise note" else "Edit exercise note"
        SecondaryTextButton(onClick = { editing = true },
            icon = if (note.isBlank()) R.drawable.ic_notebook_pen else R.drawable.ic_pencil,
            modifier = Modifier.testTag("edit-exercise-note").semantics {
            contentDescription = "$action for ${we.exercise.name}"
        }) {
            Text(action)
        }
        return
    }
    OutlinedTextField(
        value = note,
        onValueChange = {
            editing = true
            drafts.edit(we.id, we.notes, limitUserText(it, MAX_NOTE_CHARS))
        },
        label = { Text("Exercise note") },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .testTag("exercise-note")
    )
    LaunchedEffect(editing) { if (editing) focus.requestFocus() }
    ErrorText(error)
    if (note != we.notes) {
        TextButton(
            onClick = {
                error = null
                env.runner.run(Action.ExerciseNote(workoutId, we.id, note), onError = { error = it }) {
                    drafts.clear(we.id)
                    editing = false
                }
            },
            enabled = !env.runner.busy
        ) { Text("Save note") }
    }
    TextButton(onClick = { drafts.clear(we.id); editing = false }, enabled = !env.runner.busy) { Text("Cancel note edit") }
}

@Composable
internal fun NoteDialog(
    title: String,
    initial: String,
    env: AppEnv,
    onDismiss: () -> Unit,
    action: (String) -> Action
) {
    var text by rememberSaveable(initial) { mutableStateOf(initial) }
    var error by rememberSaveable(initial) { mutableStateOf<String?>(null) }
    AdaptiveDialog(
        // Back or an outside tap only closes the dialog when nothing typed would be lost; Cancel always does.
        onDismissRequest = { if (text == initial) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = limitUserText(it, MAX_NOTE_CHARS) }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
                ErrorText(error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { env.runner.run(action(text), onError = { error = it }) { onDismiss() } },
                enabled = !env.runner.busy
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AdjustTodayDialog(env: AppEnv, workout: Workout, we: WorkoutExercise, onDismiss: () -> Unit) {
    val slot = env.state.templates.find { it.id == workout.templateId }?.slots?.find { it.id == we.slotId }
    // A removed slot or addition can't take saved changes; they stay with today's copy.
    val planSource = env.state.planSourceOf(workout, we)
    val optionIds = (listOfNotNull(slot?.exerciseId) + slot?.alternatives.orEmpty() + we.exercise.id +
        env.state.exercises.filter { it.custom }.map { it.id }).distinct()
    val measure = we.exercise.measure
    // A planned exercise is only ever swapped for one measured the same way.
    val options = optionIds.mapNotNull { id -> if (id == we.exercise.id) we.exercise else env.exercise(id) }
        .filter { it.measure == measure }
    val draftKey = listOf(we.id, we.exercise.id, we.targetSets, we.restSeconds).joinToString("|")
    var replacement by rememberSaveable(draftKey) { mutableStateOf(we.exercise.id) }
    var sets by rememberSaveable(draftKey) { mutableStateOf(we.targetSets ?: 1) }
    var rest by rememberSaveable(draftKey) { mutableStateOf(we.restSeconds.toString()) }
    var saveToPlan by rememberSaveable(draftKey) { mutableStateOf(false) }
    var allowAboveCap by rememberSaveable(draftKey) { mutableStateOf(false) }
    var error by rememberSaveable(draftKey) { mutableStateOf<String?>(null) }
    val upperSets = we.upperSets
    val aboveCap = upperSets != null && sets > upperSets
    val swapLocked = when {
        we.sets.isNotEmpty() ->
            "Swapping is off because sets are already logged for ${we.exercise.name}; they would end up in the wrong " +
                "exercise's history. Delete those sets first if you really switched exercises."
        we.efforts.isNotEmpty() ->
            "Swapping is off because ${effortNoun(measure)}s are already logged for ${we.exercise.name}; they would " +
                "end up in the wrong exercise's history. Delete them first if you really switched exercises."
        env.state.stopwatch.timesEntry(workout, we) ->
            "Swapping is off while the stopwatch holds an effort for ${we.exercise.name}. Log or discard it first."
        else -> null
    }

    fun save() {
        val restSeconds = parseRest(rest)
        if (restSeconds == null) {
            error = "Rest must be between 1 and 3600 seconds."
            return
        }
        if (aboveCap && !allowAboveCap) {
            error = "Tick the box to allow sets above the advisory upper."
            return
        }
        error = null
        env.runner.run(
            Action.EditToday(we.id, replacement, sets.takeIf { measure != Measure.CARDIO }, restSeconds,
                saveToPlan && planSource != null, allowAboveCap && aboveCap),
            onError = { error = it }
        ) { onDismiss() }
    }

    AdaptiveDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust today") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val goalLine = when (measure) {
                    Measure.TIMED, Measure.DISTANCE -> minimumText(we)?.let { "A ${effortNoun(measure)} counts when it reaches $it." }
                    Measure.CARDIO -> measuredGoalText(measure, null, we.measuredGoal)?.let { "Goal: $it." }
                    Measure.REPS -> null
                }
                if (goalLine != null) Text(
                    "$goalLine It stays as planned; change it for future workouts in Plan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("adjust-goal")
                )
                TargetEditorFields(
                    env, options, replacement, { replacement = it }, swapLocked,
                    sets, { sets = it; if (!(upperSets != null && it > upperSets)) allowAboveCap = false },
                    rest, { rest = it }, we.baselineSets, upperSets, allowAboveCap, { allowAboveCap = it },
                    measure = measure
                )
                HorizontalDivider()
                if (planSource != null) {
                    CheckRow(
                        "Also save to plan", saveToPlan, { saveToPlan = it },
                        supporting = "Use these settings for future ${phaseShort(workout.phase)} workouts",
                        modifier = Modifier.testTag("save-to-plan")
                    )
                } else {
                    Text(
                        "${we.exercise.name} is no longer in this phase's plan, so changes apply to today only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ErrorText(error)
            }
        },
        confirmButton = {
            TextButton(onClick = ::save, enabled = !env.runner.busy && (!aboveCap || allowAboveCap)) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Finishing saves only what was logged. An open drop or rest-pause set is closed with the stages it has, never
 * dropped, and a partial workout counts for its week only if the user ticks that here (or later in History).
 * Exercises added today never make a workout partial or complete it by themselves, but their working sets and efforts
 * are real effort that a partial workout can be counted with. An extra workout never counts; any logged set or
 * effort, even a warm-up, is enough to finish it. The stopwatch's unlogged effort is only dropped when ticked here.
 */
@Composable
internal fun FinishWorkoutDialog(
    env: AppEnv,
    workout: Workout,
    onDismiss: () -> Unit,
    onFinished: () -> Unit,
    onDiscard: (() -> Unit)? = null
) {
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var countForWeek by rememberSaveable(workout.id) { mutableStateOf(false) }
    var discardWatch by rememberSaveable(workout.id) { mutableStateOf(false) }
    // An open set counts once it is closed by finishing.
    fun WorkoutExercise.countAtFinish() = workingCount() + if (openGroup() != null) 1 else 0
    // Measured goals are reached only by what was actually measured; sets of reps as before.
    fun WorkoutExercise.shortAtFinish() =
        if (exercise.measured) hasGoal() && !goalReached() else countAtFinish() < (targetSets ?: 0)
    fun WorkoutExercise.progressAtFinish(): String = when (exercise.measure) {
        Measure.REPS -> "${countAtFinish()} of $targetSets"
        Measure.TIMED, Measure.DISTANCE -> "${qualifyingCount()} of $targetSets reached ${minimumText(this)}"
        Measure.CARDIO -> listOfNotNull(
            totalSeconds?.let { "${loggedTimeText(this)} of ${formatDuration(it)}" },
            totalDistance?.let { goal ->
                "${loggedDistanceText(this) ?: formatMicrometres(0, goal.unit)} of ${formatDistance(goal)}"
            }
        ).joinToString(", ")
    }
    val short = workout.exercises.filter { !it.optional && it.shortAtFinish() }
    val openSets = workout.exercises.mapNotNull { we -> we.openGroup()?.let { we to it } }
    val total = workout.exercises.sumOf { it.countAtFinish() }
    val performed = total + workout.exercises.sumOf { it.efforts.size }
    val logged = workout.exercises.sumOf { it.recordCount() }
    val measured = workout.exercises.any { it.exercise.measured }
    val watch = env.state.stopwatch?.takeIf { it.workoutId == workout.id }
    val canFinish = (!workout.extra || logged > 0) && (watch == null || discardWatch)
    // Taken off its phase's week after it started: it is kept in History but has no checklist row to count for.
    val offWeek = !workout.extra && env.state.scheduledRoutine(workout.phase, workout.templateId) == null
    val offerCredit = !workout.extra && !offWeek && short.isNotEmpty() && performed > 0
    AdaptiveDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(when {
                workout.extra -> "Finish extra workout"
                short.isEmpty() -> "Finish workout"
                else -> "Finish partial workout"
            })
        },
        text = {
            when {
                workout.extra && performed > 0 -> Text(
                    "Save ${(listOfNotNull(total.takeIf { it > 0 }?.let { countLabel(it, "working set") }) +
                        effortCounts(workout)).joinToString(", ")} across " +
                        "${countLabel(workout.exercises.count { it.countAtFinish() + it.efforts.size > 0 }, "exercise")}?"
                )
                workout.extra && logged > 0 -> Text(
                    "Save ${countLabel(workout.warmupSetCount(), "warm-up set")} across " +
                        "${countLabel(workout.exercises.count { it.sets.isNotEmpty() }, "exercise")}? " +
                        "No working sets yet.",
                    modifier = Modifier.testTag("finish-warmups-only")
                )
                workout.extra -> Text(
                    "Log at least one ${if (measured) "set or effort" else "set"} to finish this extra workout, or discard it.",
                    modifier = Modifier.testTag("finish-needs-set")
                )
                short.isEmpty() -> Text(
                    if (workout.exercises.any { !it.optional && it.exercise.measured }) "All targets are reached. " +
                        "Finish and save this workout?" else "All target working sets are logged. Finish and save this workout?"
                )
                // What was done comes first; what's short is one tap away, never a list of failures up front.
                else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        listOfNotNull(
                            countLabel(total, "working set").takeIf { total > 0 || effortCounts(workout).isEmpty() },
                            *effortCounts(workout).toTypedArray()
                        ).joinToString(", ") + " logged. Only what you logged is saved.",
                        modifier = Modifier.testTag("finish-partial-summary")
                    )
                    var showShort by rememberSaveable(workout.id) { mutableStateOf(false) }
                    SecondaryTextButton(
                        onClick = { showShort = !showShort },
                        icon = if (showShort) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right,
                        modifier = Modifier.testTag("finish-short-toggle")
                    ) { Text("Below today's target (${short.size})") }
                    if (showShort) Text(
                        short.joinToString("\n") {
                            "${it.exercise.name}: ${it.progressAtFinish()}" + if (it.skipped) " (skipped)" else ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("finish-short-list")
                    )
                }
            }
            if (openSets.isNotEmpty()) Text(
                openSets.joinToString("\n") { (we, group) ->
                    "${we.exercise.name}: the ${group.method.title.lowercase()} in progress is finished with its " +
                        "${countLabel(we.stages(group.id).size, "logged stage")}."
                },
                modifier = Modifier.testTag("finish-open-sets")
            )
            if (offWeek) Text(OFF_WEEK_NOTE, modifier = Modifier.testTag("finish-off-week"))
            if (watch != null) {
                val name = workout.exercises.find { it.id == watch.exerciseId }?.exercise?.name ?: "an exercise"
                Text(
                    "The stopwatch on $name holds an effort that isn't logged (${stopwatchStatus(watch)}). Log it " +
                        "there first, or tick below to finish without it.",
                    modifier = Modifier.testTag("finish-stopwatch")
                )
                CheckRow(
                    "Discard the stopwatch's effort", discardWatch, { discardWatch = it },
                    enabled = !env.runner.busy,
                    supporting = "Its time isn't saved anywhere.",
                    modifier = Modifier.testTag("finish-discard-stopwatch")
                )
            }
            if (offerCredit) {
                CheckRow(
                    "Count ${workout.name} as done for its ${weekOf(workout.weekStart())}",
                    countForWeek, { countForWeek = it },
                    enabled = !env.runner.busy,
                    supporting = "You can change this later in History",
                    modifier = Modifier.testTag("count-for-week")
                )
            }
            ErrorText(error)
            if (workout.extra && logged == 0 && onDiscard != null) {
                TextButton(
                    onClick = onDiscard,
                    enabled = !env.runner.busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("finish-discard")
                ) { Text("Discard workout") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    error = null
                    env.runner.run(
                        Action.FinishWorkout(env.clock(), closeOpenGroups = openSets.isNotEmpty(),
                            countForWeek = countForWeek && offerCredit, discardStopwatch = watch != null && discardWatch),
                        onError = { error = it },
                        onSuccess = onFinished
                    )
                },
                enabled = !env.runner.busy && canFinish,
                modifier = Modifier.testTag("confirm-button")
            ) { Text(if (openSets.isNotEmpty()) "Finish set and workout" else "Finish") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("dialog-cancel")) { Text("Cancel") }
        }
    )
}

@Composable
internal fun DiscardWorkoutDialog(env: AppEnv, workout: Workout, onDismiss: () -> Unit, onDiscarded: () -> Unit) {
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val count = workout.exercises.sumOf { it.sets.size }
    val grouped = workout.exercises.sumOf { we -> we.sets.count { it.groupId != null } }
    val efforts = effortCounts(workout)
    val watch = env.state.stopwatch?.takeIf { it.workoutId == workout.id }
    val sets = "$count logged ${if (count == 1) "set" else "sets"}" +
        if (grouped > 0) " (counting each drop or rest-pause stage)" else ""
    ConfirmDialog(
        title = "Discard workout?",
        text = if (efforts.isEmpty() && watch == null) {
            "${workout.name} and its $sets will be deleted. This can't be undone."
        } else {
            val parts = listOfNotNull(sets.takeIf { count > 0 }) + efforts
            val name = watch?.let { w -> workout.exercises.find { it.id == w.exerciseId }?.exercise?.name }
            workout.name + (if (parts.isEmpty()) "" else " and its ${parts.joinToString(", ")}") + " will be deleted." +
                (if (watch != null) " The stopwatch's unlogged effort${name?.let { " on $it" } ?: ""} is discarded too." else "") +
                " This can't be undone."
        },
        confirmLabel = "Discard",
        destructive = true,
        busy = env.runner.busy,
        error = error,
        onDismiss = onDismiss,
        onConfirm = {
            error = null
            env.runner.run(Action.DiscardWorkout, onError = { error = it }, onSuccess = onDiscarded)
        }
    )
}

