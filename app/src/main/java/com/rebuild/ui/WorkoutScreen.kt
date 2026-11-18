package com.rebuild.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.unit.IntSize
import com.rebuild.ui.theme.FadeInOver
import com.rebuild.ui.theme.Motion
import com.rebuild.ui.theme.fadeOver
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.animateScrollBy
import kotlinx.coroutines.coroutineScope

import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.foundation.shape.CircleShape
import com.rebuild.domain.loadUnitChoices
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import com.rebuild.domain.LoadingType
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

    // Typing a set: the rail and subtitle collapse the moment a set field takes focus and expand back once the
    // keyboard has fully closed (the field may keep focus); in landscape any keyboard collapses them.
    val imeShowing = keyboardOpenOrMoving()
    var hiddenForEntry by remember { mutableStateOf(false) }
    LaunchedEffect(entryFocused) { hiddenForEntry = entryFocused }
    LaunchedEffect(imeShowing) { if (!imeShowing) hiddenForEntry = false }
    val railHidden = (entryFocused && (hiddenForEntry || imeShowing)) || (landscape && imeShowing)
    ScreenScaffold(
        title = workout.name,
        subtitle = (if (workout.extra) "Outside your plan" else phaseShort(workout.phase)) +
            " · started ${formatTime(workout.startedAt)}",
        onBack = { guarded(LEAVE_BACK, onBack) },
        compactWhenKeyboard = true,
        compactNow = railHidden,
        actions = {
            SecondaryButton(onClick = { guarded(LEAVE_FINISH) { finishing = true } }, enabled = !env.runner.showsBusy) { Text("Finish") }
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
                    enabled = !env.runner.showsBusy,
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
        // Typing a set: the rail collapses as the keyboard starts to open and expands back once it has fully closed,
        // so the ledger glides instead of jumping (the subtitle above goes the same way).
        val keyboardUp = railHidden
        AnimatedVisibility(workout.exercises.isNotEmpty() && !keyboardUp,
            enter = Motion.expand(), exit = Motion.collapse()) {
            ExerciseRail(env, workout, focusedIndex, watch, onAdd = { adding = true }, addEnabled = !env.runner.showsBusy,
                onSelect = ::select, onNotice = { notice = it })
        }
        // An unlogged stopwatch effort on another exercise stays in sight until it's logged or discarded.
        if (watch != null && !watch.running && watch.exerciseId != focused?.id && !WindowInsets.isImeVisible) {
            StopwatchStrip(workout, watch) { select(watch.exerciseId) }
        }
        notice?.let { NoticeLine(it) { notice = null } }
        if (focused == null) {
            EmptyWorkoutBody(onAdd = { adding = true }, enabled = !env.runner.showsBusy,
                modifier = Modifier.weight(1f).yieldsToFixedControls())
        } else {
            // A fresh composition per exercise: no content, entry or dialog state can outlive a switch. Switching,
            // the body travels the way the strip goes while the dock stays put: the new dock fades in over the old.
            val switch = updateTransition(focused.id, label = "exercise")
            // The exercise on screen follows the workout as it is now (a set just saved), read through states.
            val currentWorkout by rememberUpdatedState(workout)
            val currentEnv by rememberUpdatedState(env)
            val dockSizes = remember { mutableStateMapOf<String, IntSize>() }
            val shift = with(LocalDensity.current) { Motion.Shift.toPx() }
            switch.AnimatedContent(
                transitionSpec = {
                    ContentTransform(EnterTransition.None, ExitTransition.KeepUntilTransitionsFinished, sizeTransform = null)
                },
                modifier = Modifier.weight(1f).yieldsToFixedControls()
            ) { id ->
                val workout = currentWorkout
                val env = currentEnv
                val shown = workout.exercises.find { it.id == id }
                if (shown == null) {
                    Box(Modifier.fillMaxSize())
                    return@AnimatedContent
                }
                val index = workout.exercises.indexOf(shown)
                // Which way the strip went: later exercises come in from the right, earlier ones from the left.
                fun direction(): Float {
                    val from = workout.exercises.indexOfFirst { it.id == switch.currentState }
                    val to = workout.exercises.indexOfFirst { it.id == switch.targetState }
                    return if (from < 0 || to < 0 || from == to) 1f else kotlin.math.sign((to - from).toFloat())
                }
                val bodyAlpha by transition.animateFloat(transitionSpec = {
                    if (targetState == EnterExitState.Visible) Motion.fadeInSpec() else Motion.fadeOutSpec()
                }, label = "body-alpha") { if (it == EnterExitState.Visible) 1f else 0f }
                val dx by transition.animateFloat(transitionSpec = {
                    if (targetState == EnterExitState.Visible) Motion.lateralEnterTravelSpec(navigation = false)
                    else Motion.lateralExitSpec()
                }, label = "body-travel") {
                    when (it) {
                        EnterExitState.PreEnter -> direction() * shift
                        EnterExitState.Visible -> 0f
                        EnterExitState.PostExit -> -direction() * shift
                    }
                }
                val dockAlpha by transition.animateFloat(transitionSpec = { Motion.fadeInSpec() }, label = "dock-alpha") {
                    if (it == EnterExitState.PreEnter) 0f else 1f
                }
                val overhang by transition.animateFloat(transitionSpec = { Motion.fadeOutSpec() }, label = "dock-overhang") {
                    if (it == EnterExitState.PostExit) 0f else 1f
                }
                val leaving = transition.targetState == EnterExitState.PostExit
                // Next passes over exercises skipped today; they stay one tap away in the rail.
                val next = workout.nextAfter(index)
                val onNext: (() -> Unit)? = next?.let { n -> { select(n.id) } }
                ExerciseFocus(
                    env, workout, shown, index, drafts, onNext, nextName = next?.exercise?.name,
                    onFinish = { guarded(LEAVE_FINISH) { finishing = true } },
                    onEntryFocus = { if (!leaving) entryFocused = it },
                    onSelect = ::select,
                    motion = remember {
                        FocusMotion(
                            bodyAlpha = { bodyAlpha }, dx = { dx }, dockAlpha = { dockAlpha }, overhang = { overhang },
                            incomingDock = { dockSizes[switch.targetState].takeIf { switch.targetState != id } }
                        )
                    },
                    onDockSize = { dockSizes[id] = it },
                    // The copy leaving is drawn but inert: no taps, nothing for screen readers, no duplicate tags.
                    modifier = Modifier.fillMaxSize().then(if (leaving) INERT else Modifier)
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
 *
 * Hold a pill to act on it. Hold and drag: it lifts (a haptic tick), follows the finger while the others slide
 * aside (a light tick as it passes each), the strip scrolls at its edges, and dropping it saves today's order once.
 * Hold and let go without moving: a small menu on the pill moves it a place either way or removes it from today
 * (a planned exercise is skipped for today and stays in the plan; one added today is removed, after a confirmation
 * when sets are logged). Tap still just selects. Screen readers get the same three actions on each pill.
 */
@Composable
private fun ExerciseRail(
    env: AppEnv,
    workout: Workout,
    focusedIndex: Int,
    watch: Stopwatch?,
    onAdd: () -> Unit,
    addEnabled: Boolean,
    onSelect: (String) -> Unit,
    onNotice: (String) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val peek = with(density) { 48.dp.roundToPx() }
    // Bring the focused pill into view only when it isn't already, with a peek of its neighbour, so a drop or a tap
    // never makes the strip jump. Keyed by the exercise, not its place: a reorder moves no focus, so it scrolls nothing.
    val focusedKey = workout.exercises.getOrNull(focusedIndex)?.id
    LaunchedEffect(focusedKey) {
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == focusedKey }
        val inView = item != null && item.offset >= info.viewportStartOffset &&
            item.offset + item.size <= info.viewportEndOffset
        when {
            inView -> {}
            // Moves within the strip, in step with the body changing below it.
            item != null -> listState.animateScrollBy((item.offset - peek).toFloat(), Motion.moveWithinSpec())
            else -> listState.animateScrollToItem(focusedIndex, scrollOffset = -peek)
        }
    }
    // The order shown: the workout's, except while a pill is being dragged, when it follows the finger.
    val saved = workout.exercises.map { it.id }
    var order by remember { mutableStateOf(saved) }
    // The pill being dragged (or settling into its slot after the drop), and where its left edge is on screen.
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragLeft by remember { mutableFloatStateOf(0f) }
    // The finger along the strip, for edge scrolling: the strip moves when the finger nears its edge, not the pill.
    var fingerX by remember { mutableFloatStateOf(0f) }
    // The held pill's lift (scale, shadow, edge): Settles up when the hold is recognised and down after the drop.
    val lift = remember { Animatable(0f) }
    // 1 while the pill follows the finger (never eased); Settles to 0 after the drop as the pill goes into its slot.
    val follow = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var menuFor by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<String?>(null) }
    // A save that lands while a pill is held or settling waits for it to finish, then shows: the strip never keeps an
    // order older than what's saved (say a quick "Move later" from the menu while the hold is still settling).
    var savedWhileDragging by remember { mutableStateOf(false) }
    LaunchedEffect(saved) { if (dragging == null) order = saved else savedWhileDragging = true }
    val byId = workout.exercises.associateBy { it.id }
    val gap = with(density) { 8.dp.toPx() }
    // The strip's own coordinates: they never move, so the finger is measured in them rather than in the lifted
    // pill's, which slide, grow and change slot under it.
    var stripCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // Every pill width seen while dragging, so pills scrolled out of view still count at their real size.
    val widths = remember { mutableMapOf<String, Int>() }

    // A hold also reaches the pill's tap handling when the finger lifts; that tap must not switch exercises.
    var heldTap by remember { mutableStateOf(false) }
    // Edge scrolling waits for a deliberate drag: a pill held still near an edge must not scroll (and so reorder).
    var dragMoved by remember { mutableStateOf(false) }

    /** Moves an exercise for today; returns false (nothing sent) when another change is still saving. */
    fun move(id: String, to: Int): Boolean {
        if (env.runner.busy) return false
        val target = to.coerceIn(0, workout.exercises.lastIndex)
        // If the save fails the strip goes back to the saved order, so its numbers never claim a move that didn't happen.
        val before = saved
        if (before.indexOf(id) != target) env.runner.run(Action.MoveWorkoutExercise(workout.id, id, target),
            // The runner already shows the failure in the snackbar; here the strip only goes back.
            onError = { order = before })
        return true
    }
    fun removeFromToday(we: WorkoutExercise): Boolean {
        if (env.runner.busy) return false
        removeBlocked(we, watch)?.let { onNotice(it); return false }
        if (we.optional) removing = we.id
        else env.runner.run(Action.SetSkipped(workout.id, we.id, !we.skipped))
        return true
    }
    // The gesture handler outlives a recomposition, so it reads these through their latest values.
    val currentSaved by rememberUpdatedState(saved)
    val currentMove by rememberUpdatedState(::move)

    /**
     * Puts the dragged pill in the slot its centre is over. The others never change order between themselves, so
     * their positions with the dragged pill taken out come from the latest layout, whichever slot that layout drew
     * the dragged pill in. That keeps a fast drag right even when several moves arrive before the strip lays out again.
     */
    fun retarget(id: String) {
        val visible = listState.layoutInfo.visibleItemsInfo
        visible.forEach { item -> (item.key as? String)?.let { widths[it] = item.size } }
        val drawn = visible.firstOrNull { it.key == id } ?: return
        val others = order.filter { it != id }
        val anchor = visible.firstOrNull { it.key != id && it.key in others } ?: return
        val anchorIndex = others.indexOf(anchor.key)
        val known = widths.values.average().toFloat()
        fun width(other: String) = widths[other]?.toFloat() ?: known
        // The anchor's left edge with the dragged pill taken out, then everyone else's from it.
        val anchorLeft = anchor.offset - if (drawn.index < anchor.index) drawn.size + gap else 0f
        val lefts = FloatArray(others.size)
        lefts[anchorIndex] = anchorLeft
        for (i in anchorIndex + 1 until others.size) lefts[i] = lefts[i - 1] + width(others[i - 1]) + gap
        for (i in anchorIndex - 1 downTo 0) lefts[i] = lefts[i + 1] - width(others[i]) - gap
        val from = order.indexOf(id)
        val to = dragSlot(lefts.toList(), others.map(::width), gap, drawn.size.toFloat(),
            centre = dragLeft + drawn.size / 2f, from = from)
        if (to != from) {
            // A lazy row keeps its first visible item in place by key, so moving that item would scroll the whole strip
            // after it. Hold the strip where it is instead: only the pills trade places.
            val first = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            order = others.toMutableList().apply { add(to, id) }
            listState.requestScrollToItem(first, offset)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // While the finger is held near either edge the strip keeps scrolling, even if the thumb rests there: slowly at
    // the start of the zone, faster only the closer it gets to the edge, and never more than about a pill a second.
    LaunchedEffect(dragging, dragMoved) {
        val id = dragging ?: return@LaunchedEffect
        if (!dragMoved) return@LaunchedEffect
        val zone = with(density) { 64.dp.toPx() }
        val fastest = with(density) { 240.dp.toPx() }
        var last = withFrameNanos { it }
        while (lift.targetValue == 1f) {
            val now = withFrameNanos { it }
            val seconds = (now - last) / 1_000_000_000f
            last = now
            val width = stripCoordinates?.size?.width?.toFloat() ?: continue
            val depth = when {
                fingerX > width - zone && listState.canScrollForward -> ((fingerX - (width - zone)) / zone).coerceIn(0f, 1f)
                fingerX < zone && listState.canScrollBackward -> -((zone - fingerX) / zone).coerceIn(0f, 1f)
                else -> 0f
            }
            if (depth != 0f) {
                // Eased: a finger just inside the zone creeps; one at the very edge moves at the top speed.
                val speed = fastest * depth * kotlin.math.abs(depth)
                listState.scrollBy(speed * seconds)
                retarget(id)
            }
        }
    }

    val stripPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
    Box(Modifier.readableWidth()) {
    LazyRow(
        state = listState,
        contentPadding = stripPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = dragging == null,
        modifier = Modifier.fillMaxWidth().selectableGroup().testTag("exercise-picker")
            .onPlaced { stripCoordinates = it }
    ) {
        itemsIndexed(order.mapNotNull { byId[it] }, key = { _, it -> it.id }) { index, ex ->
            val lifted = dragging == ex.id
            val pillCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
            Box(
                Modifier
                    // Neighbours make way by Settle while a pill is held; pills added or removed move within.
                    .then(if (lifted) Modifier else Modifier.animateItem(fadeInSpec = Motion.fadeInSpec(),
                        placementSpec = if (dragging != null) Motion.settle() else Motion.moveWithinSpec(),
                        fadeOutSpec = Motion.fadeOutSpec()))
                    // The held pill is drawn above the strip (below); its place in the row is an empty slot.
                    .graphicsLayer { alpha = if (lifted) 0f else 1f }
                    .onPlaced { pillCoordinates[0] = it }
                    .pointerInput(ex.id, workout.id) {
                        var travelled = 0f
                        // Where the finger is along the strip, and where it and the pill were when the drag began.
                        fun stripX(local: Offset): Float? {
                            val strip = stripCoordinates ?: return null
                            val pill = pillCoordinates[0]?.takeIf { it.isAttached } ?: return null
                            return strip.localPositionOf(pill, local).x
                        }
                        var fingerStart = 0f
                        var pillStart = 0f
                        fun endDrag() {
                            dragMoved = false
                            // The pill settles from under the finger into its slot as it loses its lift, then the
                            // strip's own copy takes its place in the same frame.
                            scope.launch {
                                coroutineScope {
                                    launch { follow.animateTo(0f, Motion.settle()) }
                                    launch { lift.animateTo(0f, Motion.settle()) }
                                    // A pill dropped mostly out of sight is brought in, once, as it settles.
                                    launch {
                                        val info = listState.layoutInfo
                                        val item = info.visibleItemsInfo.firstOrNull { it.key == ex.id }
                                            ?: return@launch
                                        val start = info.viewportStartOffset + info.beforeContentPadding
                                        val end = info.viewportEndOffset - info.afterContentPadding
                                        val seen = (minOf(end, item.offset + item.size) - maxOf(start, item.offset))
                                            .coerceAtLeast(0)
                                        if (seen * 2 < item.size) listState.animateScrollBy(
                                            if (item.offset < start) (item.offset - start - peek).toFloat()
                                            else (item.offset + item.size - end + peek).toFloat(),
                                            Motion.moveWithinSpec())
                                    }
                                }
                                if (lift.targetValue == 0f) {
                                    dragging = null
                                    if (savedWhileDragging) order = currentSaved
                                    savedWhileDragging = false
                                }
                            }
                        }
                        detectDragGesturesAfterLongPress(
                            onDragStart = { at ->
                                travelled = 0f
                                dragMoved = false
                                heldTap = true
                                order = currentSaved
                                savedWhileDragging = false
                                pillStart = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == ex.id }
                                    ?.offset?.toFloat() ?: 0f
                                fingerStart = stripX(at) ?: 0f
                                fingerX = fingerStart
                                dragLeft = pillStart
                                dragging = ex.id
                                // The tick and the lift start together; the pill follows the finger from the first move.
                                scope.launch { follow.snapTo(1f) }
                                scope.launch { lift.animateTo(1f, Motion.settle()) }
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                travelled += kotlin.math.abs(amount.x) + kotlin.math.abs(amount.y)
                                if (travelled > viewConfiguration.touchSlop) dragMoved = true
                                // Measured along the strip: in the pill's own space the finger seems to move less
                                // (the pill is drawn larger) and jumps whenever the pill changes slot.
                                stripX(change.position)?.let {
                                    fingerX = it
                                    dragLeft = pillStart + it - fingerStart
                                }
                                retarget(ex.id)
                            },
                            onDragEnd = {
                                val moved = travelled > viewConfiguration.touchSlop
                                if (moved) retarget(ex.id)
                                val to = order.indexOf(ex.id)
                                heldTap = false
                                // Held still: the menu, over the saved order. Dragged: today's new order, saved once (or, if a change is
                                // still saving, back where it was, with a word why).
                                if (!moved) {
                                    order = currentSaved
                                    menuFor = ex.id
                                } else if (!currentMove(ex.id, to)) {
                                    order = currentSaved
                                    onNotice("Still saving. Try the move again in a moment.")
                                }
                                endDrag()
                            },
                            onDragCancel = {
                                // A hold released in place ends here too (the pill's tap handling takes the finger
                                // up), so a hold that never moved still opens the menu.
                                val held = travelled <= viewConfiguration.touchSlop
                                order = currentSaved
                                if (held) menuFor = ex.id
                                endDrag()
                            }
                        )
                    }
            ) {
                ExercisePill(index, workout.exercises.size, ex, selected = workout.exercises.indexOf(ex) == focusedIndex,
                    watch = watch?.takeIf { it.exerciseId == ex.id }, lifted = lifted,
                    actions = listOfNotNull(
                        CustomAccessibilityAction("Move earlier") { move(ex.id, saved.indexOf(ex.id) - 1) }
                            .takeIf { saved.indexOf(ex.id) > 0 },
                        CustomAccessibilityAction("Move later") { move(ex.id, saved.indexOf(ex.id) + 1) }
                            .takeIf { saved.indexOf(ex.id) < saved.lastIndex },
                        CustomAccessibilityAction(removeLabel(ex)) { removeFromToday(ex) }
                    )) { if (heldTap) heldTap = false else onSelect(ex.id) }
                if (menuFor == ex.id) ExerciseHoldMenu(
                    we = ex,
                    canMoveEarlier = saved.indexOf(ex.id) > 0,
                    canMoveLater = saved.indexOf(ex.id) < saved.lastIndex,
                    busy = env.runner.showsBusy,
                    blocked = removeBlocked(ex, watch),
                    onDismiss = { menuFor = null },
                    onMoveEarlier = { menuFor = null; move(ex.id, saved.indexOf(ex.id) - 1) },
                    onMoveLater = { menuFor = null; move(ex.id, saved.indexOf(ex.id) + 1) },
                    onRemove = { menuFor = null; removeFromToday(ex) }
                )
            }
        }
        item(key = "add-exercise") { AddExercisePill(onAdd, addEnabled) }
    }
    // The held pill, above every pill in the strip so no neighbour's label is ever drawn over it. It is where the
    // finger has it, whatever slot the last layout gave it; after the drop it settles into its slot.
    dragging?.let { id -> byId[id] }?.let { ex ->
        val stripTop = with(density) { stripPadding.calculateTopPadding().toPx() }
        Box(
            Modifier
                .graphicsLayer {
                    val info = listState.layoutInfo
                    val slot = info.visibleItemsInfo.firstOrNull { it.key == ex.id }?.offset?.toFloat()
                    val left = if (slot == null) dragLeft else slot + (dragLeft - slot) * follow.value
                    translationX = left - info.viewportStartOffset
                    translationY = stripTop
                    val scale = 1f + LIFT_SCALE * lift.value
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = 8.dp.toPx() * lift.value
                    shape = RoundedCornerShape(12.dp)
                    clip = false
                }
                .clearAndSetSemantics {}
        ) {
            ExercisePill(order.indexOf(ex.id).coerceAtLeast(0), workout.exercises.size, ex,
                selected = workout.exercises.indexOf(ex) == focusedIndex, watch = watch?.takeIf { it.exerciseId == ex.id },
                lifted = true, liftAmount = { lift.value }) {}
        }
    }
    }
    removing?.let { id -> byId[id] }?.let { we ->
        RemoveEntryDialog(env, workout, we, onDismiss = { removing = null })
    }
}

/**
 * Why this exercise can't come out of today right now, or null. The workout's rules refuse it with a set or
 * cluster still open, with the stopwatch holding it, or (for a planned one) once its target is done.
 */
private fun removeBlocked(we: WorkoutExercise, watch: Stopwatch?): String? = when {
    !we.optional && we.skipped -> null
    we.openGroup() != null -> "Finish the set in progress first"
    watch?.exerciseId == we.id -> "Log or discard its stopwatch effort first"
    !we.optional && we.goalReached() -> "Its target is done, so there's nothing to skip"
    else -> null
}

/** How much larger a lifted pill is drawn while it's dragged. */
private const val LIFT_SCALE = 0.06f

/**
 * The slot a dragged pill belongs in among the others, which keep their order: it passes a neighbour once its centre
 * passes the neighbour's centre, either way. [lefts] and [sizes] are the others' left edges and widths with the
 * dragged pill taken out; with it in slot k, every pill from k on sits [dragged] + [gap] further right.
 */
internal fun dragSlot(lefts: List<Float>, sizes: List<Float>, gap: Float, dragged: Float, centre: Float, from: Int): Int {
    var k = from.coerceIn(0, lefts.size)
    while (k < lefts.size && centre > lefts[k] + dragged + gap + sizes[k] / 2f) k++
    while (k > 0 && centre < lefts[k - 1] + sizes[k - 1] / 2f) k--
    return k
}

/** What removing means for this exercise today: a planned one is skipped (and can come back); an added one goes. */
private fun removeLabel(we: WorkoutExercise): String = when {
    we.optional -> "Remove from today"
    we.skipped -> "Bring back to today"
    else -> "Remove from today"
}

/** The menu a held pill opens: move it a place, or take it out of today. */
@Composable
private fun ExerciseHoldMenu(
    we: WorkoutExercise,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    busy: Boolean,
    blocked: String?,
    onDismiss: () -> Unit,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onRemove: () -> Unit
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.testTag("exercise-hold-menu")
    ) {
        Text(we.exercise.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 240.dp))
        DropdownMenuItem(
            text = { Text("Move earlier") }, enabled = canMoveEarlier && !busy, onClick = onMoveEarlier,
            leadingIcon = { RebuildIcon(R.drawable.ic_arrow_left, contentDescription = null) },
            modifier = Modifier.testTag("hold-move-earlier")
        )
        DropdownMenuItem(
            text = { Text("Move later") }, enabled = canMoveLater && !busy, onClick = onMoveLater,
            leadingIcon = { RebuildIcon(R.drawable.ic_arrow_right, contentDescription = null) },
            modifier = Modifier.testTag("hold-move-later")
        )
        val back = !we.optional && we.skipped
        DropdownMenuItem(
            text = {
                Column {
                    Text(removeLabel(we))
                    Text(
                        when {
                            blocked != null -> blocked
                            back -> "Its target is back for today"
                            we.optional -> if (we.recordCount() > 0) "Asks first: its logged sets go too" else "It was only added today"
                            else -> "Skipped today; it stays in your plan"
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            enabled = !busy && blocked == null, onClick = onRemove,
            leadingIcon = { RebuildIcon(if (back) R.drawable.ic_undo_2 else R.drawable.ic_x, contentDescription = null) },
            modifier = Modifier.testTag("hold-remove")
        )
    }
}

@Composable
private fun ExercisePill(
    index: Int,
    count: Int,
    we: WorkoutExercise,
    selected: Boolean,
    watch: Stopwatch?,
    lifted: Boolean = false,
    /** How far the lift has come (0–1): the amber edge follows it. */
    liftAmount: () -> Float = { 1f },
    actions: List<CustomAccessibilityAction> = emptyList(),
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
    // A tab, not a button: 12 dp corners rather than a full pill.
    val shape = MaterialTheme.shapes.small
    val fill by animateColorAsState(if (selected) colors.secondaryContainer else colors.surfaceContainerHigh,
        Motion.colourSpec(), label = "pill-fill")
    val large = LocalDensity.current.fontScale > 1.3f
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .widthIn(max = if (large) 280.dp else 208.dp)
            .clip(shape)
            // Filled, never outlined: the current exercise is the amber-tinted pill, the rest sit on tone. A new
            // selection recolours by the Colour rule.
            .background(fill)
            // Held: an amber edge marks the pill being moved.
            .then(if (lifted) Modifier.border(2.dp, colors.primaryContainer.copy(alpha = liftAmount().coerceIn(0f, 1f)),
                shape) else Modifier)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics {
                contentDescription = "Exercise ${index + 1} of $count: ${we.exercise.name}, " +
                    we.progressDescription() + progress
                if (actions.isNotEmpty()) customActions = actions
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
    modifier: Modifier = Modifier,
    motion: FocusMotion = FocusMotion(),
    onDockSize: (IntSize) -> Unit = {}
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

    @Composable fun DockContent(face: DockFace, viewportHeight: Int) {
        val we = face.we
        val workout = face.workout
        val draft = face.draft
        when (face.kind) {
            DockKind.OPEN_ELSEWHERE -> openElsewhere?.let { OpenGroupElsewhere(env, workout, it) { onSelect(it.id) } }
            DockKind.TIMING_ELSEWHERE -> timingElsewhere?.let { StopwatchElsewhere(env, workout, it) {
                onSelect(it.exerciseId)
            } }
            DockKind.SKIPPED -> SkippedPanel(env, workout, we, nextName, onNext, onFinish)
            DockKind.MEASURED_ENTRY -> MeasuredEntryPanel(env, workout, we, draft.extra,
                onCancelExtra = { draft.extra = false }, onGoTo = onSelect, viewportHeight = viewportHeight,
                onFocusChange = onEntryFocus)
            DockKind.MEASURED_MET -> TargetMetPanel(nextName, onNext, onFinish, finishEnabled = !env.runner.showsBusy,
                message = if (we.optional || exercise.measure == Measure.CARDIO) "Goal done" else "Target done",
                extraLabel = extraEffortLabel(exercise.measure)) { draft.extra = true }
            DockKind.ENTRY -> EntryPanel(env, workout, we, draft, face.offered, suggestionFor, viewportHeight,
                onEntryFocus, onCarry = { dropCarry = it })
            DockKind.TARGET_MET -> TargetMetPanel(nextName, onNext, onFinish, finishEnabled = !env.runner.showsBusy,
                message = if (we.optional) "Goal done" else "Target done",
                dropLabel = we.dropTarget()?.let { target -> "Add drop to set ${we.workEntries().indexOf(target) + 1}" },
                onDrop = { draft.extra = true; draft.drop = true; draft.kg = ""; draft.reps = "" }) {
                dropCarry = DropCarry.NONE
                draft.extra = true
            }
        }
    }

    val kind = when {
        openElsewhere != null -> DockKind.OPEN_ELSEWHERE
        timingElsewhere != null -> DockKind.TIMING_ELSEWHERE
        watchHere -> DockKind.MEASURED_ENTRY
        we.showsSkipped() -> DockKind.SKIPPED
        measured && (!targetMet || draft.extra) -> DockKind.MEASURED_ENTRY
        measured -> DockKind.MEASURED_MET
        !targetMet || draft.extra || open != null -> DockKind.ENTRY
        else -> DockKind.TARGET_MET
    }
    val dockColor = MaterialTheme.colorScheme.surfaceContainer
    val canvasColor = MaterialTheme.colorScheme.surface
    val nudge = with(density) { Motion.Nudge.toPx() }

    /**
     * The entry, or what takes its place. When it changes kind (Target done, Skipped, back to the entry) the new one,
     * on its own opaque backing, fades in over the old, which keeps drawing as it was until it is covered; the size
     * follows by Expand or Collapse from the foot, so Log set and Next sit where they end from the first frame, and
     * only the line above the buttons rises.
     */
    @Composable fun Entry(viewportHeight: Int, inDock: Boolean) {
        FadeInOver(
            DockFace(kind, draftKey, we, workout, draft, offered),
            contentKey = { it.kind },
            contentAlignment = if (inDock) Alignment.BottomStart else Alignment.TopStart,
            clip = true,
            backing = if (inDock) dockColor else canvasColor,
            label = "dock-content"
        ) { face ->
            val rise by transition.animateFloat(transitionSpec = { Motion.riseSpec() }, label = "dock-rise") {
                if (it == EnterExitState.PreEnter) nudge else 0f
            }
            val riseModifier = remember { Modifier.graphicsLayer { translationY = rise } }
            CompositionLocalProvider(LocalDockRise provides riseModifier) {
                Column {
                    confinedKey(face.key) {
                        DockContent(face, viewportHeight)
                    }
                }
            }
        }
    }

    val bodyScroll = rememberScrollState()
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
                    // The body slot alone moves on an exercise switch; with the entry inline it only fades.
                    .graphicsLayer {
                        alpha = motion.bodyAlpha()
                        translationX = if (pinned) motion.dx() else 0f
                    }
                    .fillMaxHeight()
                    // A short window (a phone on its side) keeps every column it has: narrowed, the entry would wrap
                    // into rows the keyboard leaves no room for.
                    .then(if (windowHeightDp >= 480f) Modifier.readableWidth() else Modifier.fillMaxWidth())
                    .onSizeChanged { workspaceViewport = it.height }
                    .onFocusChanged { workspaceFocused = it.hasFocus }
                    .focusGroup()
                    .fadingEdges(bodyScroll, if (density.fontScale > 1.3f) 48.dp else 32.dp)
                    .verticalScroll(bodyScroll)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    // Without the dock, the last rows scroll clear of the gesture area.
                    .then(if (pinned) Modifier else Modifier.navigationBarsPadding()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val unitShown = env.state.canSetLoadUnit(workout.id, we.id)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // The name, with the unit chip at its end: the chip keeps its 32 dp pill and only its touch
                    // area reaches 48 dp, so it sits on the name's line instead of pushing a line of its own apart.
                    // Baselines align, so a name that wraps keeps the chip on its first line.
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // The rail above shows the position; it is still spoken with the name.
                        Text(exercise.name, style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f).alignByBaseline().semantics {
                                heading()
                                contentDescription = "${exercise.name}, exercise ${index + 1} of ${workout.exercises.size}"
                            })
                        if (unitShown) {
                            AssistChip(
                                onClick = { choosingUnit = true },
                                enabled = !env.runner.showsBusy && open == null && !watchHere,
                                label = { Text(unitName(we.entryLoadUnit), maxLines = 1) },
                                leadingIcon = {
                                    RebuildIcon(R.drawable.ic_sliders_horizontal, contentDescription = null,
                                        modifier = Modifier.size(18.dp))
                                },
                                shape = CircleShape,
                                border = null,
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    leadingIconContentColor = MaterialTheme.colorScheme.onSurface),
                                modifier = Modifier.alignByBaseline().testTag("choose-load-unit")
                                    .semantics { contentDescription = "Load unit: ${unitName(we.entryLoadUnit)}" }
                            )
                        }
                    }
                    // One quiet line: how the load is entered, only when the unit chip doesn't already say it,
                    // then today's target.
                    Text(
                        listOfNotNull(loadingNote(exercise, we.entryLoadUnit, we.perLeg, unitShown), targetLine(workout, we))
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("target-line")
                    )
                    if (we.entryLoadUnit == LoadUnit.PLATES) Text(
                        "Compare plates only on the same machine.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (env.state.canSetLoadUnit(workout.id, we.id) && (open != null || watchHere)) Text(
                        if (open != null) "The unit stays as it is until the ${open.method.title.lowercase()} is finished."
                        else "The unit stays as it is until the stopwatch's effort is logged or discarded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!pinned) Entry(workspaceViewport, inDock = false)

                if (measured) {
                    EffortLedger(env, workout, we, onEdit = { editingEffort = it.id })
                } else {
                    SetLedger(we, previous, previousWorkout?.startedAt, onEdit = { editingSet = it.id },
                        onGroup = { openGroupId = it })
                }

                // Secondary actions read as one compact list with aligned icons, below the sets they relate to.
                Column {
                    // Before the target is met the rail is the way to move on; the Target done panel offers Next.
                    GroupedActionRow(onClick = { details = !details }, icon = R.drawable.ic_clipboard_list,
                        index = 0, lastIndex = 1,
                        trailing = if (details) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down,
                        label = if (details) "Hide options" else "Target and options",
                        modifier = Modifier.testTag("exercise-details")) {
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
                    AnimatedVisibility(details, enter = Motion.expand(), exit = Motion.collapse()) {
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
        dock = {
            if (pinned) LogDock(
                onFocusChange = { dockFocused = it },
                modifier = Modifier
                    .onSizeChanged(onDockSize)
                    // Switching exercises: the new dock fades in over the old one, which stays whole beneath it.
                    .graphicsLayer { alpha = motion.dockAlpha() }
                    .fadeOver(motion.incomingDock, motion.overhang, Alignment.BottomStart)
            ) { viewport -> Entry(viewport, inDock = true) }
        }
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
                Text((if (exercise.loading == LoadingType.EXTERNAL && !exercise.perDumbbell)
                        "Enter the total load lifted, bar included. " else "") +
                        "Your next sets use it, and it's remembered for this exercise. Logged sets keep their own unit.",
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
        GroupedActionRow(onClick = { editing = true },
            icon = if (note.isBlank()) R.drawable.ic_notebook_pen else R.drawable.ic_pencil,
            index = 1, lastIndex = 1,
            modifier = Modifier.padding(top = 2.dp).testTag("edit-exercise-note").semantics {
            contentDescription = "$action for ${we.exercise.name}"
        }) {
            Text(action, modifier = Modifier.weight(1f))
        }
        return
    }
    InputField(
        value = note,
        onValueChange = {
            editing = true
            drafts.edit(we.id, we.notes, limitUserText(it, MAX_NOTE_CHARS))
        },
        label = "Exercise note",
        singleLine = false,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .testTag("exercise-note")
    )
    LaunchedEffect(editing) { if (editing) focus.requestFocus() }
    ErrorText(error)
    if (note != we.notes) {
        SecondaryButton(
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
    SecondaryButton(onClick = { drafts.clear(we.id); editing = false }, enabled = !env.runner.busy) { Text("Cancel note edit") }
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
                InputField(value = text, onValueChange = { text = limitUserText(it, MAX_NOTE_CHARS) }, label = "Note", singleLine = false,
                    modifier = Modifier.fillMaxWidth())
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
    // The slot's exercise and swaps for the plan's equipment: a dumbbell plan never offers a hack squat.
    val gear = env.state.gear
    val optionIds = (listOfNotNull(slot?.exerciseFor(gear)) + slot?.alternativesFor(gear).orEmpty() + we.exercise.id +
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
                    SecondaryButton(
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
                    enabled = !env.runner.showsBusy,
                    supporting = "Its time isn't saved anywhere.",
                    modifier = Modifier.testTag("finish-discard-stopwatch")
                )
            }
            if (offerCredit) {
                CheckRow(
                    "Count ${workout.name} as done for its ${weekOf(workout.weekStart())}",
                    countForWeek, { countForWeek = it },
                    enabled = !env.runner.showsBusy,
                    supporting = "You can change this later in History",
                    modifier = Modifier.testTag("count-for-week")
                )
            }
            ErrorText(error)
            if (workout.extra && logged == 0 && onDiscard != null) {
                TextButton(
                    onClick = onDiscard,
                    enabled = !env.runner.showsBusy,
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
                        // The receipt follows from the state that shows this workout finished.
                        onSaved = { env.runner.receiptFor(workout.id) },
                        onSuccess = onFinished
                    )
                },
                enabled = !env.runner.showsBusy && canFinish,
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


/** What the dock shows: the entry or what takes its place. Each kind is a new surface; within one, it updates. */
private enum class DockKind { OPEN_ELSEWHERE, TIMING_ELSEWHERE, SKIPPED, MEASURED_ENTRY, MEASURED_MET, ENTRY, TARGET_MET }

/** One dock face with what it was drawn from, so a face leaving keeps drawing as it was while the next covers it. */
private class DockFace(
    val kind: DockKind,
    val key: String,
    val we: WorkoutExercise,
    val workout: Workout,
    val draft: EntryDraft,
    val offered: EntrySuggestion?
)

/** How an exercise's body and dock are drawn while the exercise changes; still outside a switch. */
@Stable
internal class FocusMotion(
    val bodyAlpha: () -> Float = { 1f },
    val dx: () -> Float = { 0f },
    val dockAlpha: () -> Float = { 1f },
    val overhang: () -> Float = { 1f },
    /** The incoming dock's size while this copy is leaving, else null. */
    val incomingDock: () -> IntSize? = { null }
)
