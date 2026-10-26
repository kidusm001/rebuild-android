package com.rebuild.ui

import com.rebuild.domain.BodySite
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.ScaffoldDefaults
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.annotation.DrawableRes
import com.rebuild.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rebuild.domain.Monotonic
import com.rebuild.platform.PhoneClock
import kotlinx.coroutines.delay
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rebuild.domain.Action
import com.rebuild.domain.AppRepository
import com.rebuild.domain.AppState
import com.rebuild.domain.Exercise
import com.rebuild.domain.LoadingType
import com.rebuild.domain.RoutineDraft
import com.rebuild.domain.draft
import com.rebuild.domain.placementDraft
import com.rebuild.domain.routineDraftFrom
import com.rebuild.ui.theme.RebuildTheme
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import com.rebuild.domain.localDateOf
import java.time.ZoneId
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.withFrameNanos

internal enum class Tab(val route: String, val label: String, @DrawableRes val icon: Int) {
    HOME("home", "Today", R.drawable.ic_dumbbell),
    HISTORY("history", "History", R.drawable.ic_history),
    PLAN("plan", "Plan", R.drawable.ic_clipboard_list),
    MORE("more", "More", R.drawable.ic_sliders_horizontal)
}

internal object Routes {
    const val WORKOUT = "workout"
    const val PHASE = "phase"
    const val BACKUP = "backup"
    const val HELP = "help"
    const val EXERCISES = "exercises"
    const val ROUTINES = "routines"
    const val NEW_ROUTINE = "routine-new"
    const val BODYWEIGHT = "body-weight"
    const val MEASUREMENTS = "body-measurements"
    const val MEASURE = "body-measure"
    fun site(site: BodySite) = "body-site/${site.name}"
    fun workoutDetail(id: String) = "workout-detail/$id"
    fun routine(id: String) = "routine/$id"
    fun editRoutine(id: String) = "routine-edit/$id"
    fun routineFrom(workoutId: String) = "routine-from/$workoutId"
    fun editPlacement(id: String) = "placement-edit/$id"

    /** Routine editors hold an unsaved draft, so they hide the tab bar: leaving goes through Back and its prompt. */
    fun isEditor(route: String) = route == NEW_ROUTINE || route.startsWith("routine-edit/") ||
        route.startsWith("routine-from/") || route.startsWith("placement-edit/") || route == MEASURE
    fun exerciseHistory(exercise: Exercise) =
        "exercise-history/${exercise.loading.name}/${exercise.perDumbbell}/${exercise.id}"
}

/** Minimal saveable back stack; routes are plain strings so they survive process death. */
internal class Navigator(initial: List<String>) {
    val stack = mutableStateListOf<String>().apply { addAll(initial.ifEmpty { listOf(Tab.HOME.route) }) }
    val current: String get() = stack.last()
    val tab: Tab get() = Tab.entries.firstOrNull { it.route == stack.first() } ?: Tab.HOME

    fun push(route: String) {
        if (current != route) stack.add(route)
    }

    fun pop(): Boolean {
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            return true
        }
        if (current != Tab.HOME.route) {
            select(Tab.HOME)
            return true
        }
        return false
    }

    fun select(tab: Tab) {
        stack.clear()
        stack.add(tab.route)
    }

    /** Swaps [from] for [to] only while [from] is still on top, e.g. a saved new routine opening its own page. */
    fun replace(from: String, to: String) {
        if (current == from) stack[stack.lastIndex] = to
    }

    /** Pops only while [route] is on top, so a late save callback never closes a different screen. */
    fun popIf(route: String) {
        if (current == route) pop()
    }

    /** Removes routes that no longer resolve (e.g. the workout after it was finished). */
    fun removeAll(predicate: (String) -> Boolean) {
        val kept = stack.filterNot(predicate)
        if (kept.size == stack.size) return
        stack.clear()
        stack.addAll(kept.ifEmpty { listOf(Tab.HOME.route) })
    }

    companion object {
        val Saver = listSaver<Navigator, String>(save = { it.stack.toList() }, restore = { Navigator(it) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RebuildApp(
    repository: AppRepository,
    platform: PlatformActions,
    modifier: Modifier = Modifier,
    clock: () -> Long = System::currentTimeMillis,
    /** The monotonic clock the stopwatch measures with; tests pass their own. */
    monotonic: (() -> Monotonic)? = null
) {
    RebuildTheme {
        val state by repository.state.collectAsStateWithLifecycle()
        val status by platform.status.collectAsStateWithLifecycle()
        val runner: ActionRunner = viewModel(key = "rebuild-runner-${System.identityHashCode(repository)}") {
            ActionRunner(repository)
        }
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(runner) { runner.errors.collect { snackbar.showSnackbar(it) } }
        val context = LocalContext.current.applicationContext
        val readClock = monotonic ?: remember(context) { { PhoneClock.read(context) } }

        // Back in the app, a running stopwatch is checked against the clock: after a restart its time can't be
        // trusted, so it stops and asks for the duration instead of guessing one.
        val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
        val resumed = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
        val running = state.stopwatch?.takeIf { it.running }
        val runningId = running?.id
        val runningStep = running?.step
        LaunchedEffect(runningId, runningStep, resumed) {
            if (runningId != null && runningStep != null && resumed) {
                while (!runner.run(Action.RecoverStopwatch(runningId, runningStep, readClock()))) delay(250)
            }
        }

        // Typed effort fields outlive the workout screen, rotation and recreation; only drafts that no longer match the
        // active workout are dropped.
        val effortDrafts = rememberSaveable(saver = EffortDrafts.Saver) { EffortDrafts() }
        LaunchedEffect(state.workouts, state.activeWorkoutId, state.stopwatch) { effortDrafts.prune(state) }

        // The calendar day is an input of every screen ("this week", a rest day, today's weigh-in). It is read again on
        // each recomposition here (a tab tap, a back press, resuming the app) and at the next midnight while the app
        // stays open, so a kept screen never shows yesterday.
        var midnight by remember { mutableIntStateOf(0) }
        val today = midnight.let { localDateOf(clock()) }
        LaunchedEffect(today) {
            val next = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            delay((next - clock()).coerceIn(60_000L, 86_400_000L))
            midnight++
        }

        // One instance per real change of its inputs. Every screen takes it, so a fresh instance on each recomposition
        // here (a tab tap, a back press, the lifecycle) would recompose every screen on the stack for nothing.
        val env = remember(state, status, platform, runner, clock, readClock, effortDrafts, today) {
            AppEnv(state, status, platform, runner, clock, readClock, effortDrafts)
        }
        val nav = rememberSaveable(saver = Navigator.Saver) { Navigator(listOf(Tab.HOME.route)) }

        // A workout that just finished here ends on its receipt; a discarded one or a restore never shows one.
        var lastActive by rememberSaveable { mutableStateOf(state.activeWorkoutId) }
        var receiptFor by rememberSaveable { mutableStateOf<String?>(null) }
        LaunchedEffect(state.activeWorkoutId) {
            val ended = lastActive
            if (ended != null && state.activeWorkoutId == null &&
                state.workouts.any { it.id == ended && it.finishedAt != null }) receiptFor = ended
            lastActive = state.activeWorkoutId
        }

        val route = nav.current
        // Navigation stays everywhere except the workout itself, a past workout's detail (its note drafts are guarded
        // by Back) and editors with an unsaved draft.
        val showNav = state.phase != null && route != Routes.WORKOUT && !route.startsWith("workout-detail/") &&
            !Routes.isEditor(route)
        // Wide windows (landscape phones, tablets) put the destinations in a rail so content keeps its height.
        val wide = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } >= 600.dp
        Row(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            if (showNav && wide) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Spacer(Modifier.weight(1f))
                    Tab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = nav.tab == tab,
                            onClick = { nav.select(tab) },
                            icon = { RebuildIcon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
            Scaffold(
                // The rail already sits clear of a side navigation bar or cutout.
                modifier = Modifier.weight(1f).fillMaxSize().then(
                    if (showNav && wide) Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                    else Modifier
                ),
                snackbarHost = { SnackbarHost(snackbar) },
                // The workout's dock and body pad the gesture area themselves, so its color reaches the screen edge.
                contentWindowInsets = if (route == Routes.WORKOUT)
                    WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                else ScaffoldDefaults.contentWindowInsets,
                bottomBar = {
                    val timer = state.restTimer
                    // On the workout screen the stopwatch is shown where it times; everywhere else it stays one tap away.
                    val watch = state.stopwatch?.takeIf { route != Routes.WORKOUT }
                    val bottomNav = showNav && !wide
                    if (state.phase != null && (bottomNav || timer != null || watch != null)) {
                        // The lowest bar paints behind the gesture area in its own color, and every bar rides above
                        // the keyboard, so nothing light or empty ever shows under a dark screen.
                        val base = when {
                            bottomNav -> MaterialTheme.colorScheme.surfaceContainer
                            timer != null -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                        Column(
                            Modifier
                                .background(base)
                                .windowInsetsPadding(
                                    if (bottomNav) WindowInsets.ime.exclude(WindowInsets.navigationBars)
                                    else WindowInsets.ime.union(WindowInsets.navigationBars)
                                        .only(WindowInsetsSides.Bottom)
                                )
                        ) {
                            if (watch != null) {
                                Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                                    // An editor's unsaved draft would be lost by leaving it, so the way back is through Back.
                                    StopwatchBar(env, watch, onOpen = if (Routes.isEditor(route) ||
                                        state.activeWorkoutId != watch.workoutId) null else ({ nav.push(Routes.WORKOUT) }))
                                }
                            }
                            if (timer != null) {
                                Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                                    RestBar(env, timer)
                                }
                            }
                            if (bottomNav) {
                                NavigationBar {
                                    Tab.entries.forEach { tab ->
                                        NavigationBarItem(
                                            selected = nav.tab == tab,
                                            onClick = { nav.select(tab) },
                                            icon = { RebuildIcon(tab.icon, contentDescription = null) },
                                            label = { Text(tab.label) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            ) { inner ->
                Box(
                    Modifier
                        .padding(inner)
                        .consumeWindowInsets(inner)
                        .imePadding()
                ) {
                    if (state.phase == null) {
                        FirstRunScreen(env)
                    } else {
                        BackHandler(enabled = nav.stack.size > 1 || nav.current != Tab.HOME.route) { nav.pop() }
                        AppDestination(env, nav)
                    }
                }
            }
        }
        state.workouts.find { it.id == receiptFor }?.let { finished ->
            WorkoutReceipt(env, finished,
                onView = { receiptFor = null; nav.push(Routes.workoutDetail(finished.id)) },
                onDismiss = { receiptFor = null })
        }
    }
}

/** Everything a screen needs; a new instance only when the state or one of its services changes. */
internal class AppEnv(
    val state: AppState,
    val status: PlatformStatus,
    val platform: PlatformActions,
    val runner: ActionRunner,
    val clock: () -> Long,
    val monotonic: () -> Monotonic,
    val effortDrafts: EffortDrafts = EffortDrafts()
) {
    fun exercise(id: String): Exercise? = state.exercises.find { it.id == id }
}

@Composable
private fun AppDestination(env: AppEnv, nav: Navigator) {
    val route = nav.current
    val state = env.state
    // Keyed on the value itself so a push that races ahead of the state update isn't undone.
    LaunchedEffect(state.activeWorkoutId) {
        if (state.activeWorkoutId == null) nav.removeAll { it == Routes.WORKOUT }
    }
    LaunchedEffect(state.workouts) {
        nav.removeAll { r ->
            (r.startsWith("workout-detail/") || r.startsWith("routine-from/")) &&
                state.workouts.none { it.id == r.substringAfter('/') }
        }
    }
    LaunchedEffect(state.routines) {
        nav.removeAll { r ->
            (r.startsWith("routine/") || r.startsWith("routine-edit/")) && state.routines.none { it.id == r.substringAfter('/') }
        }
    }
    // A copy taken off its week (or replaced) can no longer be edited.
    LaunchedEffect(state.placements) {
        nav.removeAll { r ->
            r.startsWith("placement-edit/") &&
                state.placements.none { it.id == r.substringAfter('/') && !it.removedFromPlan }
        }
    }
    // Tab roots (Today, History, Plan, More) stay composed once visited, so switching tabs only shows another
    // already-built screen instead of composing, measuring and laying it out from scratch. A hidden tab keeps the
    // state it was last shown with, so it doesn't recompose while the user logs sets elsewhere, and it gets a Back
    // dispatcher nothing presses. Pushed screens (a workout, a detail page, an editor) get one composition group per
    // route, so drafts never carry over to a different workout or exercise; a pushed screen that leaves the stack
    // starts fresh next time, so an editor never reopens an abandoned draft.
    val saved = rememberSaveableStateHolder()
    val atTabRoot = nav.stack.size == 1 && route in TAB_ROUTES
    val visited = remember { mutableStateListOf(nav.tab.route) }
    // Once the first screen has settled, the other tabs are built in the background, one per frame, so even the first
    // switch to each is instant. Tests and screenshots see the same screens either way.
    LaunchedEffect(Unit) {
        delay(PREWARM_DELAY_MS)
        TAB_ROUTES.forEach { route ->
            withFrameNanos { }
            if (route !in visited) visited += route
        }
    }
    // A tab shown for the first time joins the kept ones in this same frame, so it never flashes empty.
    val tabs = if (atTabRoot && route !in visited) visited + route else visited.toList()
    SideEffect { tabs.forEach { if (it !in visited) visited += it } }
    val pushed = remember { mutableSetOf<String>() }
    LaunchedEffect(nav.stack.toList()) {
        (pushed - nav.stack.toSet()).forEach { saved.removeState(stackKey(it)) }
        pushed.retainAll(nav.stack.toSet())
    }
    Box(Modifier.fillMaxSize()) {
        KeepAliveTabs(tabs, active = route.takeIf { atTabRoot }) { tab, active ->
            val shownWith = remember { arrayOfNulls<AppEnv>(1) }
            if (active || shownWith[0] == null) shownWith[0] = env
            InactiveBack(disabled = !active) {
                saved.SaveableStateProvider(tab) { RouteContent(shownWith[0]!!, nav, tab) }
            }
        }
        AnimatedContent(
            targetState = route.takeUnless { atTabRoot },
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(90)) },
            label = "route"
        ) { shown ->
            if (shown != null) {
                pushed += shown
                saved.SaveableStateProvider(stackKey(shown)) { key(shown) { RouteContent(env, nav, shown) } }
            }
        }
    }
}

private val TAB_ROUTES = Tab.entries.map { it.route }.toSet()

/**
 * Built once, outside composition. Written inline as `if (active) Modifier else Modifier.clearAndSetSemantics { }`, the
 * compiler remembers the lambda inside the branch, and flipping the branch shifts the slot table so the whole tab
 * after it is discarded and rebuilt, which is the cost keeping tabs alive exists to avoid.
 */
private val HIDDEN_TAB = Modifier.clearAndSetSemantics { }
private const val PREWARM_DELAY_MS = 600L

/** Saved-state key of a pushed screen, apart from a tab root's even if the routes match. */
private fun stackKey(route: String) = "stack/$route"

/**
 * Keeps every route in [routes] composed and measured but places only [active]: the others aren't drawn, and their
 * semantics are cleared, so screen readers and tests see one screen. A hidden tab's inputs don't change, so measuring
 * it again is a cache hit, and showing it only places it and fades its layer in, with no recomposition or layout.
 */
@Composable
private fun KeepAliveTabs(routes: List<String>, active: String?, content: @Composable (String, Boolean) -> Unit) {
    Layout(content = {
        routes.forEach { route ->
            key(route) {
                val isActive = route == active
                val alpha = remember { Animatable(if (isActive) 1f else 0f) }
                LaunchedEffect(isActive) {
                    if (isActive) alpha.animateTo(1f, tween(150)) else alpha.snapTo(0f)
                }
                // Unplaced nodes still count for semantics, so a hidden tab is cleared from the tree explicitly: screen
                // readers, focus and tests reach only the tab on screen.
                Box(
                    Modifier
                        .layoutId(route)
                        .graphicsLayer { this.alpha = alpha.value }
                        .then(if (isActive) Modifier else HIDDEN_TAB)
                ) { content(route, isActive) }
            }
        }
    }) { measurables, constraints ->
        val placeables = measurables.map { it.layoutId to it.measure(constraints) }
        val shown = placeables.firstOrNull { it.first == active }?.second
        layout(shown?.width ?: constraints.minWidth, shown?.height ?: constraints.minHeight) {
            shown?.place(0, 0)
        }
    }
}

/** While [disabled], [content]'s Back handlers register with a dispatcher no key or gesture reaches. */
@Composable
private fun InactiveBack(disabled: Boolean, content: @Composable () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val inert = remember(lifecycleOwner) {
        object : OnBackPressedDispatcherOwner {
            override val onBackPressedDispatcher = OnBackPressedDispatcher()
            override val lifecycle get() = lifecycleOwner.lifecycle
        }
    }
    // One call site whatever [disabled] is: switching between two would discard and rebuild the whole tab.
    val owner = if (disabled) inert else LocalOnBackPressedDispatcherOwner.current ?: inert
    CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides owner, content = content)
}

@Composable
private fun RouteContent(env: AppEnv, nav: Navigator, route: String) {
    val state = env.state
    when {
        route == Tab.HOME.route -> HomeScreen(env, onOpenWorkout = { nav.push(Routes.WORKOUT) }, onNavigate = nav::push)
        route == Tab.HISTORY.route -> HistoryScreen(env, onNavigate = nav::push, onOpenToday = { nav.select(Tab.HOME) })
        route == Tab.PLAN.route -> PlanScreen(env, onNavigate = nav::push)
        route == Tab.MORE.route -> MoreScreen(env, onNavigate = nav::push)
        route == Routes.WORKOUT -> {
            val workout = state.activeWorkout
            if (workout != null) WorkoutScreen(env, workout, onBack = { nav.pop() })
        }
        route == Routes.PHASE -> PhaseSettingsScreen(env, onBack = { nav.pop() })
        route == Routes.BACKUP -> BackupScreen(env, onBack = { nav.pop() })
        route == Routes.HELP -> HelpScreen(onBack = { nav.pop() })
        route == Routes.EXERCISES -> ExerciseLibraryScreen(env, onBack = { nav.pop() })
        route == Routes.BODYWEIGHT -> BodyweightScreen(env, onBack = { nav.pop() })
        route == Routes.MEASUREMENTS -> MeasurementsScreen(env, onBack = { nav.pop() }, onMeasure = { nav.push(Routes.MEASURE) },
            onSite = { nav.push(Routes.site(it)) })
        route == Routes.MEASURE -> MeasureScreen(env, onBack = { nav.popIf(route) })
        route.startsWith("body-site/") -> BodySite.entries.find { it.name == route.substringAfter('/') }
            ?.let { SiteScreen(env, it, onBack = { nav.pop() }) }
        route == Routes.ROUTINES -> RoutinesScreen(env, onBack = { nav.pop() }, onNavigate = nav::push)
        route == Routes.NEW_ROUTINE -> {
            val id by rememberSaveable { mutableStateOf(newId()) }
            RoutineEditorScreen(
                env, title = "New routine", subtitle = null,
                intro = "Saved to your library, ready to start or add to a week.",
                ownerId = id, initial = { RoutineDraft("", emptyList()) }, saveLabel = "Save routine",
                onBack = { nav.popIf(route) },
                toAction = { name, exercises -> Action.SaveRoutine(id, name, exercises) },
                onSaved = { nav.replace(route, Routes.routine(id)) }
            )
        }
        route.startsWith("routine/") -> {
            val routine = state.routines.find { it.id == route.removePrefix("routine/") }
            if (routine != null) RoutineDetailScreen(
                env, routine, onBack = { nav.pop() }, onNavigate = nav::push,
                onStarted = {
                    nav.select(Tab.HOME)
                    nav.push(Routes.WORKOUT)
                },
                onDeleted = { nav.removeAll { it == route || it == Routes.editRoutine(routine.id) } }
            )
        }
        route.startsWith("routine-edit/") -> {
            val routine = state.routines.find { it.id == route.removePrefix("routine-edit/") }
            if (routine != null) RoutineEditorScreen(
                env, title = "Edit routine", subtitle = routine.name,
                intro = "Copies on weekly plans keep their settings until you update them.",
                ownerId = routine.id, initial = { routine.draft() }, saveLabel = "Save changes",
                onBack = { nav.popIf(route) },
                toAction = { name, exercises -> Action.SaveRoutine(routine.id, name, exercises) },
                onSaved = { nav.popIf(route) }
            )
        }
        route.startsWith("routine-from/") -> {
            val workout = state.workouts.find { it.id == route.removePrefix("routine-from/") }
            if (workout != null) {
                val id by rememberSaveable { mutableStateOf(newId()) }
                RoutineEditorScreen(
                    env, title = "Save as routine", subtitle = "From ${workout.name}, ${formatDate(workout.startedAt)}",
                    intro = "Set targets for each exercise; logged loads never become targets.",
                    ownerId = id, initial = { routineDraftFrom(workout) }, saveLabel = "Save routine",
                    onBack = { nav.popIf(route) },
                    toAction = { name, exercises -> Action.SaveRoutine(id, name, exercises) },
                    onSaved = { nav.replace(route, Routes.routine(id)) }
                )
            }
        }
        route.startsWith("placement-edit/") -> {
            val placement = state.placements.find { it.id == route.removePrefix("placement-edit/") && !it.removedFromPlan }
            if (placement != null) RoutineEditorScreen(
                env, title = "Edit ${placement.name}",
                subtitle = "${weekdayName(placement.weekday)} · ${phaseShort(placement.phase)} week",
                intro = "Changes this day's copy from its next workout only.",
                ownerId = placement.id, initial = { state.placementDraft(placement) }, saveLabel = "Save this day",
                onBack = { nav.popIf(route) },
                toAction = { name, exercises -> Action.SavePlacement(placement.id, name, exercises) },
                onSaved = { nav.popIf(route) }
            )
        }
        route.startsWith("workout-detail/") -> {
            val workout = state.workouts.find { it.id == route.removePrefix("workout-detail/") }
            if (workout != null) WorkoutDetailScreen(env, workout, onBack = { nav.pop() }, onNavigate = nav::push)
        }
        route.startsWith("exercise-history/") -> {
            val parts = route.removePrefix("exercise-history/").split("/", limit = 3)
            val loading = runCatching { LoadingType.valueOf(parts[0]) }.getOrNull()
            if (parts.size == 3 && loading != null) {
                ExerciseHistoryScreen(env, parts[2], loading, parts[1].toBoolean(), onBack = { nav.pop() },
                    onOpenWorkout = { nav.push(Routes.workoutDetail(it)) })
            } else {
                LaunchedEffect(route) { nav.pop() }
            }
        }
        else -> LaunchedEffect(route) { nav.select(Tab.HOME) }
    }
}
