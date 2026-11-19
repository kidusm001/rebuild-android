package com.rebuild.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.res.Configuration
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.RoundedCornerShape
import com.rebuild.ui.theme.animationsOff
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import kotlin.math.roundToInt
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.core.Animatable
import com.rebuild.domain.RestTimer
import com.rebuild.domain.Stopwatch
import com.rebuild.ui.theme.FadeInOver
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import com.rebuild.R
import com.rebuild.domain.Action
import com.rebuild.domain.AppRepository
import com.rebuild.domain.AppState
import com.rebuild.domain.BodySite
import com.rebuild.domain.Exercise
import com.rebuild.domain.LoadingType
import com.rebuild.domain.Monotonic
import com.rebuild.domain.RoutineDraft
import com.rebuild.domain.draft
import com.rebuild.domain.localDateOf
import com.rebuild.domain.placementDraft
import com.rebuild.domain.routineDraftFrom
import com.rebuild.platform.PhoneClock
import com.rebuild.ui.theme.Motion
import com.rebuild.ui.theme.RebuildTheme
import java.time.ZoneId
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal enum class Tab(val route: String, val label: String, @DrawableRes val icon: Int) {
    HOME("home", "Today", R.drawable.ic_dumbbell),
    HISTORY("history", "History", R.drawable.ic_history),
    PLAN("plan", "Plan", R.drawable.ic_clipboard_list),
    MORE("more", "More", R.drawable.ic_sliders_horizontal)
}

internal object Routes {
    const val WORKOUT = "workout"
    const val PHASE = "phase"
    const val SPLIT = "split"
    const val BACKUP = "backup"
    const val HELP = "help"
    const val REST_ALERT = "rest-alert"
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

    /**
     * A workout start from Today is in flight. The workout then opens in the very composition whose state has it
     * active, before Today redraws around it: Today leaves as it was when Start was tapped.
     */
    var openOnStart by mutableStateOf(false)
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
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
        val restAlert by platform.restAlert.collectAsStateWithLifecycle()
        val ringingSince by platform.alarmRingingSince.collectAsStateWithLifecycle()
        // Setup's last step (rest alerts) comes after the plan is saved, so it's shown by this flag, not the data.
        var setupRestStep by rememberSaveable { mutableStateOf(false) }
        val restEnd = remember { RestEndState() }
        // Where the rest bar's top edge was, so a sweep at its end starts from the bar itself.
        var restBarTop by remember { mutableStateOf<Float?>(null) }
        val runner: ActionRunner = viewModel(key = "rebuild-runner-${System.identityHashCode(repository)}") {
            ActionRunner(repository, createSavedStateHandle())
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
        val env = remember(state, status, platform, runner, clock, readClock, effortDrafts, today, restAlert) {
            AppEnv(state, status, platform, runner, clock, readClock, effortDrafts, restAlert, previewRestEnd = {
                platform.previewRestAlert()
                restEnd.current = RestEnd("preview-${clock()}", shortPause = false, preview = true,
                    about = com.rebuild.platform.RestContext("", nextSet = 2, ofSets = 3))
            })
        }
        // A rest ending in front of the user gets its on-screen alert; the sound comes from its notification.
        val runningRest = state.restTimer?.takeIf { it.workoutId == state.activeWorkoutId }
        RestEndWatcher(runningRest, clock, about = { com.rebuild.platform.restContext(state, it) }) {
            restEnd.current = it
            platform.announceRestEnd(it.id, it.shortPause)
        }
        // A resting lifter glances at the phone rather than touching it: the screen stays on while a rest runs or its
        // alarm rings, so the countdown and the end of the rest can be seen.
        val view = LocalView.current
        val keepOn = runningRest != null || ringingSince != null
        DisposableEffect(view, keepOn) {
            view.keepScreenOn = keepOn
            onDispose { view.keepScreenOn = false }
        }
        val nav = rememberSaveable(saver = Navigator.Saver) { Navigator(listOf(Tab.HOME.route)) }

        // A workout that just finished here ends on its receipt; a discarded one or a restore never shows one. The
        // finish's onSaved only records which workout; the receipt shows from the composition whose state has that
        // workout finished and nothing active, the same one that drops the workout from what is drawn.
        val pending = runner.receiptPending
        val receiptFor = pending?.takeIf { id ->
            state.activeWorkoutId == null && state.workouts.any { it.id == id && it.finishedAt != null }
        }
        LaunchedEffect(pending, state.workouts) {
            if (pending != null && state.workouts.none { it.id == pending }) runner.receiptShown()
        }
        // What is drawn: the real stack, less a workout that is no longer active (the effect below cleans the stack),
        // plus a workout just started from Today, which the side effect below then pushes for real.
        val opening = nav.openOnStart && state.activeWorkoutId != null
        val renderStack = (if (opening && nav.current != Routes.WORKOUT) nav.stack + Routes.WORKOUT else nav.stack)
            .filterNot { it == Routes.WORKOUT && state.activeWorkoutId == null }
            .ifEmpty { listOf(Tab.HOME.route) }
        if (opening) SideEffect {
            nav.openOnStart = false
            nav.push(Routes.WORKOUT)
        }
        val setupShown = state.phase == null || setupRestStep
        val route = if (setupShown) SETUP_ROUTE else renderStack.last()
        // Wide windows (landscape phones, tablets) put the destinations in a rail so content keeps its height.
        val wide = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } >= 600.dp

        val chrome = remember { Chrome(navShownAtStart = showsNav(route) && !wide) }
        chrome.density = LocalDensity.current
        chrome.navigationBars = WindowInsets.navigationBars
        chrome.ime = WindowInsets.ime
        chrome.content = ScaffoldDefaults.contentWindowInsets
        chrome.workoutContent = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        val timer = state.restTimer.takeIf { !setupShown }
        val watch = state.stopwatch.takeIf { !setupShown }
        val ringing = ringingSince?.takeIf { timer == null && !setupShown }
        // The shared bars come and go by Expand / Collapse (opaque bar): each is kept, with what it last showed, while
        // it collapses away. A rest that ends into the Up next takeover goes at once: the sheet grows from its edge.
        // One reveal value per bar is read both where the bar is drawn and where the layers pad themselves, so the
        // dock rides the bar's edge in the very same frame.
        val restFace: BarFace? = when {
            timer != null -> BarFace.Rest(timer)
            ringing != null -> BarFace.Alarm(ringing)
            else -> null
        }
        val lastRestFace = remember { arrayOfNulls<BarFace>(1) }
        if (restFace != null) lastRestFace[0] = restFace
        val restReveal = remember { Animatable(if (restFace != null) 1f else 0f) }
        LaunchedEffect(restFace != null) {
            when {
                restFace != null -> restReveal.animateTo(1f, Motion.expandSizeSpec())
                restEnd.current != null -> restReveal.snapTo(0f)
                else -> restReveal.animateTo(0f, Motion.collapseSizeSpec())
            }
        }
        val restRevealing by remember { derivedStateOf { restReveal.value > 0f } }
        val restPresent = restFace != null || restRevealing
        val lastWatch = remember { arrayOfNulls<Stopwatch>(1) }
        if (watch != null) lastWatch[0] = watch
        val watchReveal = remember { Animatable(if (watch != null) 1f else 0f) }
        LaunchedEffect(watch != null) {
            watchReveal.animateTo(if (watch != null) 1f else 0f,
                if (watch != null) Motion.expandSizeSpec() else Motion.collapseSizeSpec())
        }
        val watchRevealing by remember { derivedStateOf { watchReveal.value > 0f } }
        val watchPresent = watch != null || watchRevealing
        chrome.restReveal = { restReveal.value }
        chrome.watchReveal = { watchReveal.value }
        SideEffect {
            chrome.wide = wide
            chrome.watchShown = watchPresent
            chrome.restTargetShown = restFace != null
            chrome.watchTargetShown = watch != null
        }
        // The navigation overlay is composed while the target or the layer leaving shows it.
        var navWanted by remember { mutableStateOf(showsNav(route)) }
        val bottomNav = showsNav(route) && !wide
        val base = when {
            // The navigation bar paints its own tone; around a floating rest pill the canvas shows.
            bottomNav -> MaterialTheme.colorScheme.surface
            // On the workout the pill floats on the dock's tone, so dock and pill read as one base.
            // Only when the entry dock is pinned at the bottom; at large text or in landscape it scrolls inline.
            timer != null && route == Routes.WORKOUT && dockPinned() -> MaterialTheme.colorScheme.surfaceContainer
            timer != null -> MaterialTheme.colorScheme.surface
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        }
        // Test tags double as Android resource IDs, so an on-device driver (uiautomator, the verify-rebuild skill) can
        // find controls by the same stable handles the tests use instead of by visible text. Nothing visible changes.
        Box(modifier.fillMaxSize().semantics {
            testTagsAsResourceId = true
            this[ActionGuard] = runner.busy
        }.background(MaterialTheme.colorScheme.surface)) {
            // Beneath everything: the transitions' kinds of drawing, once, unseen, while the app is idle after launch.
            MotionWarmUp(state)
            AppFrame(
                chrome = chrome,
                targetRoute = route,
                barsBase = base,
                // Under the receipt the app is inert: no input, nothing for screen readers.
                modifier = if (receiptFor != null) INERT else Modifier,
                navBar = if (navWanted && !wide && !setupShown) ({
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
                }) else null,
                rail = if (navWanted && wide && !setupShown) ({
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
                }) else null,
                // On the workout screen the stopwatch is shown where it times; everywhere else it stays one tap away.
                watchBar = if (!watchPresent) null else ({
                    val w = watch ?: lastWatch[0]
                    if (w != null) Box(
                        Modifier.revealed(watchReveal::value) { chrome.watchFull = it }
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    ) {
                        // An editor's unsaved draft would be lost by leaving it, so the way back is through Back.
                        StopwatchBar(env, w, onOpen = if (Routes.isEditor(route) ||
                            state.activeWorkoutId != w.workoutId) null else ({ nav.push(Routes.WORKOUT) }))
                    }
                }),
                restBar = if (!restPresent) null else ({
                    val face = restFace ?: lastRestFace[0]
                    // The ringing alarm takes the rest bar's place, so Stop is where the eye already is. Two amber
                    // bars in one place: the new one fades in over the old, which never dims.
                    if (face != null) FadeInOver(
                        face,
                        modifier = Modifier.revealed(restReveal::value) { chrome.restFull = it },
                        contentKey = { it is BarFace.Alarm },
                        label = "rest-bar-face"
                    ) { shown ->
                        Box(Modifier.background(base)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                            when (shown) {
                                is BarFace.Rest -> Box(Modifier.onGloballyPositioned {
                                    restBarTop = it.boundsInRoot().top
                                }) { RestBar(env, shown.timer) }
                                is BarFace.Alarm -> RestAlarmBar(shown.since, clock, onStop = platform::stopRestAlarm)
                            }
                        }
                    }
                }),
                snackbar = { SnackbarHost(snackbar) }
            ) {
                // Setup's steps, its rest-alert step and the app are one sequence: the plan's step gives way to the
                // last one as the steps do, and the app comes in over the last step as a push (the nav bar fading in
                // with Today), never by a cut. The last step stays drawn, untouched and inert, beneath the app until
                // the app's first screen has come in, so a slow first composition never shows an empty frame.
                val stage = when {
                    state.phase == null -> SetupStage.STEPS
                    setupRestStep -> SetupStage.REST_ALERTS
                    else -> SetupStage.APP
                }
                val arriving = remember { stage != SetupStage.APP }
                var arrived by remember { mutableStateOf(!arriving) }
                // The step beneath leaves as the layer beneath a push does, from the app's first drawn frame.
                val stepOut = remember { Animatable(1f) }
                val stageScope = rememberCoroutineScope()
                val parallax = with(LocalDensity.current) { Motion.Parallax.toPx() }
                val lastStep = remember { arrayOf(SetupStage.STEPS) }
                if (stage != SetupStage.APP) lastStep[0] = stage
                val shift = with(LocalDensity.current) { Motion.Shift.roundToPx() }
                if (stage != SetupStage.APP || !arrived) AnimatedContent(if (stage == SetupStage.APP) lastStep[0] else stage,
                    transitionSpec = {
                        if (targetState > initialState) Motion.lateralEnter(shift) togetherWith Motion.lateralExit(-shift)
                        else Motion.lateralEnter(-shift) togetherWith Motion.lateralExit(shift)
                    }, label = "setup-stage") { shown ->
                    val inert = stage == SetupStage.APP || transition.targetState == EnterExitState.PostExit
                    Box(Modifier.fillMaxSize()
                        .graphicsLayer {
                            if (stage == SetupStage.APP) {
                                alpha = stepOut.value
                                translationX = -parallax * (1f - stepOut.value)
                            }
                        }
                        .layerInsets(chrome, SETUP_ROUTE, live = true)
                        .then(if (inert) INERT else Modifier)) {
                        when (shown) {
                            SetupStage.REST_ALERTS -> RestAlertSetup(env, onDone = { setupRestStep = false })
                            else -> SetupFlow(env, onPlanSaving = { setupRestStep = it })
                        }
                    }
                }
                if (stage == SetupStage.APP) AppDestination(env, nav, renderStack, chrome, arriving = arriving,
                    onArriving = { stageScope.launch { stepOut.animateTo(0f, Motion.lateralExitSpec()) } },
                    onArrived = { arrived = true }, onNavWanted = { navWanted = it })
            }
            receiptFor?.let { id ->
                state.workouts.find { it.id == id }?.let { finished ->
                    WorkoutReceipt(env, finished,
                        onView = { nav.push(Routes.workoutDetail(finished.id)) },
                        onClosed = { runner.receiptShown() },
                        onShown = { runner.receiptDrawn(finished.id) })
                }
            }
            SetLoggedTick(runner, state)
            // Topmost: a rest ending over the receipt covers it.
            restEnd.current?.let { end ->
                RestEndOverlay(end, restAlert, onDone = { restEnd.current = null },
                    restBarTop = restBarTop.takeIf { !end.preview })
            }
        }
    }
}

/**
 * The set-logged tick: once the state being composed holds the set just saved, the tick fires at the start of the next
 * frame, i.e. right after the frame that first draws the set was handed off, however long that frame took. Its own
 * scope, so the pending id never recomposes the app.
 */
@Composable
private fun SetLoggedTick(runner: ActionRunner, state: AppState) {
    val id = runner.tickFor ?: return
    val shown = state.activeWorkout?.exercises?.any { e -> e.sets.any { it.id == id } } == true
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    if (shown) LaunchedEffect(id) {
        withFrameNanos { }
        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
        runner.ticked(id)
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
    val effortDrafts: EffortDrafts = EffortDrafts(),
    val restAlert: RestAlert = RestAlert(),
    /** Shows and sounds the chosen rest-end alert now, from the settings screen. */
    val previewRestEnd: () -> Unit = {}
) {
    fun exercise(id: String): Exercise? = state.exercises.find { it.id == id }
}

/** The steps of first-run setup and the app after it, in order. */
private enum class SetupStage { STEPS, REST_ALERTS, APP }

/** A back gesture in progress: the layer shown beneath the screen it would close. */
private class Peek(val key: String, val route: String, val showsNav: Boolean)

/** How the target layer changed: deeper (push), back (pop), between peers or out of a finished workout (fade). */
private enum class Move { NONE, PUSH, POP, FADE }

/** One change of the target layer; [leaving] is the layer it replaced, drawn until the transition ends. */
private class Plan(
    val id: Int,
    val target: String,
    val targetRoute: String,
    val leaving: String?,
    val leavingRoute: String?,
    val move: Move
)

/**
 * What the layers remember between frames: the last plan and each layer's motion and the env it was last shown with.
 * Not snapshot state: it is only read where a plan changes, which recomposes the layers anyway.
 */
private class LayerBook {
    private var last: Plan? = null
    private var lastStack: List<String> = emptyList()
    private var ids = 0
    val motions = mutableMapOf<String, LayerMotion>()
    val envs = mutableMapOf<String, AppEnv>()

    /** The layer a predictive back gesture has just committed to: it goes on from where the preview left it. */
    var previewed: String? = null

    fun next(
        key: String,
        route: String,
        stack: List<String>,
        workoutEnded: Boolean,
        settled: Int,
        density: Density,
        arriving: Boolean
    ): Plan {
        val prev = last
        val plan = if (prev == null && arriving) {
            // The app's first screen, straight after setup: it comes in as a push, over the last setup step.
            motions[key] = LayerMotion(0f, with(density) { Motion.Shift.toPx() })
            Plan(ids++, key, route, null, null, Move.PUSH)
        } else if (prev == null) {
            motions[key] = LayerMotion(1f)
            Plan(ids++, key, route, null, null, Move.NONE)
        } else {
            val move = when {
                // Under the receipt (or after a discard) the workout gives way in place: the sheet is the only travel.
                prev.targetRoute == Routes.WORKOUT && workoutEnded -> Move.FADE
                lastStack.size == 1 && stack.size == 1 -> Move.FADE
                route in lastStack && prev.targetRoute !in stack -> Move.POP
                else -> Move.PUSH
            }
            // Going back to the layer that is still leaving reverses it from where it is.
            val stillShown = (prev.leaving == key && settled != prev.id) || (previewed == key && key in motions)
            previewed = null
            if (!stillShown) {
                val dx = with(density) {
                    when (move) {
                        Move.PUSH -> Motion.Shift.toPx()
                        Move.POP -> -Motion.Parallax.toPx()
                        else -> 0f
                    }
                }
                motions[key] = LayerMotion(0f, dx)
            }
            Plan(ids++, key, route, prev.target, prev.targetRoute, move)
        }
        last = plan
        lastStack = stack
        return plan
    }

    /** The target of the last plan: its motion is never put away. */
    val lastTarget: String? get() = last?.target

    /** After a transition only the target keeps a motion; anything shown again starts from its own first frame. */
    fun settle(plan: Plan) {
        motions.keys.retainAll(setOf(plan.target))
        envs.keys.retainAll(setOf(plan.target))
    }
}

@Composable
private fun AppDestination(
    env: AppEnv,
    nav: Navigator,
    stack: List<String>,
    chrome: Chrome,
    /** Straight after setup: the first screen comes in as a push. */
    arriving: Boolean = false,
    /** Called on the first screen's first drawn frame, when its push starts, and once it is fully in. */
    onArriving: () -> Unit = {},
    onArrived: () -> Unit = {},
    backEnabled: Boolean = true,
    onNavWanted: (Boolean) -> Unit
) {
    val route = stack.last()
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
    val atTabRoot = stack.size == 1 && route in TAB_ROUTES
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

    // The stack transition. Every layer is full size and laid out with its own final insets from its first frame; the
    // layer leaving stays drawn, untouched, until the incoming one has drawn once, so a slow first composition shows
    // the old screen, never the canvas or a half-built one.
    val key = if (atTabRoot) route else stackKey(route)
    val book = remember { LayerBook() }
    var settled by remember { mutableIntStateOf(-1) }
    val density = LocalDensity.current
    val plan = remember(key) {
        book.next(key, route, stack, workoutEnded = state.activeWorkoutId == null, settled = settled, density = density,
            arriving = arriving)
    }
    val leaving = plan.leaving?.takeIf { settled != plan.id }
    val leavingRoute = plan.leavingRoute?.takeIf { leaving != null }
    LaunchedEffect(plan) {
        val incoming = book.motions.getValue(plan.target)
        val outgoing = plan.leaving?.let { book.motions[it] }
        val navIn = showsNav(plan.targetRoute)
        val navOut = plan.leavingRoute?.let(::showsNav)
        if (plan.move == Move.PUSH && outgoing == null) {
            // The first screen after setup: a push over the setup step leaving beneath it (drawn by the caller).
            chrome.navShown.snapTo(if (navIn) 1f else 0f)
            withFrameNanos { }
            onArriving()
            coroutineScope {
                launch { incoming.alpha.animateTo(1f, Motion.fadeInSpec()) }
                launch { incoming.dx.animateTo(0f, Motion.lateralEnterTravelSpec(navigation = true)) }
            }
            onArrived()
        } else if (plan.move == Move.NONE || outgoing == null) {
            incoming.alpha.snapTo(1f)
            incoming.dx.snapTo(0f)
            chrome.navShown.snapTo(if (navIn) 1f else 0f)
        } else {
            // t = 0 is the first frame the incoming layer has been drawn.
            withFrameNanos { }
            coroutineScope {
                launch { incoming.alpha.animateTo(1f, Motion.fadeInSpec()) }
                launch { incoming.dx.animateTo(0f, Motion.lateralEnterTravelSpec(navigation = true)) }
                launch { outgoing.alpha.animateTo(0f, Motion.lateralExitSpec()) }
                val away = with(density) {
                    when (plan.move) {
                        Move.PUSH -> -Motion.Parallax.toPx()
                        Move.POP -> Motion.Shift.toPx()
                        else -> 0f
                    }
                }
                if (away != 0f) launch { outgoing.dx.animateTo(away, Motion.lateralExitSpec()) }
                // Chrome shift: the shared bars leave the navigation bar's top at once on a push, and reach it only
                // once the layer leaving is half gone on a pop, so they never cover a layer drawn above 0.5.
                if (navOut != null && navIn != navOut) launch {
                    if (navIn) snapshotFlow { outgoing.alpha.value }.first { it <= 0.5f }
                    chrome.navShown.animateTo(if (navIn) 1f else 0f, Motion.chromeShiftSpec())
                } else {
                    chrome.navShown.snapTo(if (navIn) 1f else 0f)
                }
            }
        }
        settled = plan.id
        book.settle(plan)
    }
    // Predictive back: while a back gesture is held on a pushed screen, the screen beneath is shown under it, and the
    // screen follows the finger as a preview; letting go finishes the pop from there, cancelling settles it back.
    var peek by remember { mutableStateOf<Peek?>(null) }
    val scope = rememberCoroutineScope()
    val beneathRoute = stack.getOrNull(stack.size - 2)
    val beneathKey = beneathRoute?.let { if (stack.size == 2 && it in TAB_ROUTES) it else stackKey(it) }
    // Screens whose Back asks first (editors, the measure flow) and Remove animations keep a plain Back.
    val predictive = backEnabled && beneathKey != null && !Routes.isEditor(route) && !animationsOff() &&
        settled == plan.id
    BackHandler(enabled = backEnabled && (stack.size > 1 || nav.current != Tab.HOME.route)) { nav.pop() }
    PredictiveBackHandler(enabled = predictive) { events ->
        val top = book.motions[plan.target] ?: run { events.collect { }; nav.pop(); return@PredictiveBackHandler }
        val underKey = beneathKey!!
        val parallax = with(density) { Motion.Parallax.toPx() }
        val under = book.motions[underKey]?.takeIf { underKey != plan.target } ?: LayerMotion(0.6f, -parallax)
        book.motions[underKey] = under
        peek = Peek(underKey, beneathRoute!!, showsNav(beneathRoute))
        top.previewing = true
        try {
            events.collect { e ->
                val p = e.progress.coerceIn(0f, 1f)
                top.edge = if (e.swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                top.peek.snapTo(p)
                under.alpha.snapTo(0.6f + 0.4f * p)
                under.dx.snapTo(-parallax * (1f - p))
            }
            // Committed: the pop goes on from where the preview is, the screen leaving fading as any pop's does.
            top.previewing = false
            book.previewed = underKey
            peek = null
            nav.pop()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Cancelled: back where it was by Settle, then the layer beneath is put away again.
            scope.launch {
                coroutineScope {
                    launch { top.peek.animateTo(0f, Motion.settle()) }
                    launch { under.alpha.animateTo(0f, Motion.settle()) }
                    launch { under.dx.animateTo(-parallax, Motion.settle()) }
                }
                if (peek?.key == underKey) peek = null
                top.previewing = false
                if (underKey != book.lastTarget) book.motions.remove(underKey)
            }
            throw e
        }
    }

    val navAlpha = remember(plan, leaving, peek) {
        val target = book.motions[plan.target]
        val out = leaving?.let { book.motions[it] }
        val tShows = showsNav(plan.targetRoute)
        val lShows = leavingRoute?.let(::showsNav)
        val under = peek?.let { book.motions[it.key] }
        when {
            // A back preview over a screen without the bar, onto one with it: the bar comes with the screen beneath.
            peek != null && under != null && !tShows && peek!!.showsNav -> ({ under.alpha.value })
            // The first screen after setup comes in with its bar.
            plan.leaving == null && plan.move == Move.PUSH && tShows -> ({ target?.alpha?.value ?: 1f })
            // Peers that both show it (a tab switch, Plan › Change split): the bar stays opaque.
            lShows == null || (tShows && lShows) -> if (tShows) ({ 1f }) else ({ 0f })
            tShows -> ({ target?.alpha?.value ?: 1f })
            else -> ({ out?.alpha?.value ?: 0f })
        }
    }
    SideEffect {
        val tShows = showsNav(plan.targetRoute)
        val lShows = leavingRoute?.let(::showsNav)
        chrome.navAlpha = navAlpha
        chrome.navInteractive = tShows && (lShows == null || lShows) && peek == null
        onNavWanted(tShows || lShows == true || peek?.showsNav == true)
    }

    @Composable
    fun Layer(layerKey: String, layerRoute: String, shownWith: AppEnv, content: @Composable (AppEnv) -> Unit) =
        LayerBox(layerKey, layerRoute, isTarget = layerKey == plan.target,
            leaving = layerKey == leaving || layerKey == peek?.key,
            motion = book.motions[layerKey], chrome = chrome, env = shownWith, content = content)

    Layout(content = {
        tabs.forEach { tab ->
            key(tab) {
                // A hidden tab keeps the env it was last shown with, so it never recomposes while hidden.
                val shownWith = remember { arrayOfNulls<AppEnv>(1) }
                if (tab == plan.target || shownWith[0] == null) shownWith[0] = env
                Layer(tab, tab, shownWith[0]!!) { e -> saved.SaveableStateProvider(tab) { RouteContent(e, nav, tab) } }
            }
        }
        listOfNotNull(
            if (!atTabRoot) plan.target to route else null,
            leaving?.takeIf { it !in TAB_ROUTES }?.let { it to leavingRoute!! },
            // The screen beneath a back preview, when it is a pushed one (a kept tab is already composed).
            peek?.takeIf { it.key !in TAB_ROUTES }?.let { it.key to it.route }
        ).forEach { (layerKey, layerRoute) ->
            key(layerKey) {
                pushed += layerRoute
                // A pushed screen leaving keeps the env it was last shown with: a finished workout still draws itself.
                if (layerKey == plan.target || layerKey !in book.envs) book.envs[layerKey] = env
                Layer(layerKey, layerRoute, book.envs.getValue(layerKey)) { e ->
                    saved.SaveableStateProvider(stackKey(layerRoute)) { key(layerRoute) { RouteContent(e, nav, layerRoute) } }
                }
            }
        }
    }) { measurables, constraints ->
        // Every kept tab is measured (cheap: its inputs don't change), but only the target and the layer leaving are
        // placed: the one on top depends on the direction.
        val placeables = measurables.map { it.layoutId to it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            val order = when {
                peek != null -> listOf(peek?.key, plan.target)
                plan.move == Move.POP -> listOf(plan.target, leaving)
                else -> listOf(leaving, plan.target)
            }
            order.filterNotNull().forEach { k -> placeables.firstOrNull { it.first == k }?.second?.place(0, 0) }
        }
    }
}

/**
 * One layer of the stack transition. Its own composable with plain inputs, so a hidden kept tab (not the target, not
 * leaving, no motion, the env it was last shown with) skips entirely when the app recomposes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayerBox(
    layerKey: String,
    layerRoute: String,
    isTarget: Boolean,
    leaving: Boolean,
    motion: LayerMotion?,
    chrome: Chrome,
    env: AppEnv,
    content: @Composable (AppEnv) -> Unit
) {
    // A hidden layer has no press ripple: a ripple caught mid-press when the layer was covered would otherwise replay,
    // frozen, the next time the layer is shown. Back on screen it starts still.
    val ripple = LocalRippleConfiguration.current
    val canvas = MaterialTheme.colorScheme.surface
    Box(
        Modifier
            .layoutId(layerKey)
            .graphicsLayer {
                alpha = motion?.alpha?.value ?: 0f
                translationX = motion?.dx?.value ?: 0f
                // A back preview: the screen shrinks toward the finger's side with rounded corners and a shadow.
                val p = motion?.peek?.value ?: 0f
                if (p > 0f) {
                    val scale = 1f - (1f - Motion.BackPeekScale) * p
                    scaleX = scale
                    scaleY = scale
                    translationX += motion!!.edge * Motion.BackPeekOffset.toPx() * p
                    shape = RoundedCornerShape(Motion.BackPeekCorner * p)
                    clip = true
                    shadowElevation = Motion.LiftShadow.toPx() * p
                }
            }
            // A previewed screen is a card over the one beneath: opaque, not see-through where it draws nothing.
            .drawBehind { if (motion?.previewing == true) drawRect(canvas) }
            // Input and screen readers reach only the target, from the transition's first frame.
            .then(if (isTarget) Modifier else INERT)
            .layerInsets(chrome, layerRoute, live = isTarget || leaving)
    ) {
        CompositionLocalProvider(
            LocalLayerActive provides isTarget,
            LocalRippleConfiguration provides ripple.takeIf { isTarget || leaving }
        ) {
            InactiveBack(disabled = !isTarget) { content(env) }
        }
    }
}

private val TAB_ROUTES = Tab.entries.map { it.route }.toSet()

private const val PREWARM_DELAY_MS = 600L

/** Saved-state key of a pushed screen, apart from a tab root's even if the routes match. */
private fun stackKey(route: String) = "stack/$route"

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
        route == Tab.HOME.route -> HomeScreen(env, onOpenWorkout = { nav.push(Routes.WORKOUT) }, onNavigate = nav::push,
            onStarting = { nav.openOnStart = it })
        route == Tab.HISTORY.route -> HistoryScreen(env, onNavigate = nav::push, onOpenToday = { nav.select(Tab.HOME) })
        route == Tab.PLAN.route -> PlanScreen(env, onNavigate = nav::push)
        route == Tab.MORE.route -> MoreScreen(env, onNavigate = nav::push)
        route == Routes.WORKOUT -> {
            val workout = state.activeWorkout
            if (workout != null) WorkoutScreen(env, workout, onBack = { nav.pop() })
        }
        route == Routes.PHASE -> PhaseSettingsScreen(env, onBack = { nav.pop() })
        route == Routes.SPLIT -> ChangeSplitScreen(env, onBack = { nav.pop() })
        route == Routes.BACKUP -> BackupScreen(env, onBack = { nav.pop() })
        route == Routes.HELP -> HelpScreen(onBack = { nav.pop() })
        route == Routes.REST_ALERT -> RestAlertScreen(env, onBack = { nav.pop() })
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

/** The same rule the workout uses to pin its entry dock: portrait, text at most 130%, a window at least 600 dp tall. */
@Composable
private fun dockPinned(): Boolean {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val displayDensity = LocalContext.current.resources.displayMetrics.density
    val windowHeightDp = configuration.screenHeightDp * displayDensity / density.density
    return configuration.orientation != Configuration.ORIENTATION_LANDSCAPE &&
        density.fontScale <= 1.3f && windowHeightDp >= 600f
}

/** What the rest bar's place shows: the countdown, or the ringing alarm that takes its place. */
private sealed interface BarFace {
    data class Rest(val timer: RestTimer) : BarFace
    data class Alarm(val since: Long) : BarFace
}

/**
 * A bar revealed from its top edge: it reports [reveal] of its full height (written to [full]) and shows its top part,
 * so a bottom-anchored bar rises with its edge. Read while measuring, in step with the layers' insets.
 */
private fun Modifier.revealed(reveal: () -> Float, full: (Int) -> Unit): Modifier = clipToBounds().layout { m, c ->
    val p = m.measure(c.copy(minHeight = 0))
    full(p.height)
    layout(p.width, (p.height * reveal()).roundToInt()) { p.place(0, 0) }
}
