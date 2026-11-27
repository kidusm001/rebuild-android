package com.rebuild.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.rebuild.ui.theme.Motion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A flick down faster than this closes the sheet wherever it is. */
private val DISMISS_VELOCITY = 1000.dp
/** Dragged further than this share of its height, the sheet closes when let go. */
private const val DISMISS_FRACTION = 0.5f

/**
 * A modal bottom sheet drawn inside the app's own window: by Sheet in from its full height (the only thing that
 * travels), over a scrim. It closes by Sheet out on [close] (its content's own buttons), Back, a scrim tap, or a drag
 * past half its height or faster than [DISMISS_VELOCITY]; [onClosed] runs once it has gone.
 *
 * The caller makes the app behind it inert. The sheet takes focus and announces [paneTitle]; it never reaches above
 * the status bar, so long content (large text) scrolls inside it. A confirming tick is felt as it lands (98 % of its
 * travel). After recreation it is simply there, with no replayed entrance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppSheet(
    key: Any,
    paneTitle: String,
    onClosed: () -> Unit,
    modifier: Modifier = Modifier,
    /** Runs once the sheet has been drawn (below the screen, or at rest after recreation), as its travel starts. */
    onShown: () -> Unit = {},
    /** The dim already over the app as the sheet arrives (a window it replaces), where its scrim starts. */
    scrimFrom: Float = 0f,
    content: @Composable (close: () -> Unit) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val closed by rememberUpdatedState(onClosed)
    var entered by rememberSaveable(key) { mutableStateOf(false) }
    // The share of the sheet's height still below the screen: 1 hidden, 0 at rest.
    val hidden = remember(key) { Animatable(if (entered) 0f else 1f) }
    val scrim = remember(key) { Animatable(if (entered) Motion.ScrimAlpha else 0f) }
    var drag by remember(key) { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(0) }
    var closing by remember(key) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val velocityThreshold = with(LocalDensity.current) { DISMISS_VELOCITY.toPx() }

    val shown by rememberUpdatedState(onShown)
    LaunchedEffect(key) {
        runCatching { focus.requestFocus() }
        // The travel's clock starts from the sheet's first drawn frame: a slow first composition then delays the
        // sheet, it never eats its travel.
        withFrameNanos { }
        if (entered) {
            shown()
            return@LaunchedEffect
        }
        // Drawn once, below the screen: whatever covered the wait (the finish dialog) now goes, at once, and its dim
        // carries over into this scrim, which settles to its own strength as the sheet comes in.
        scrim.snapTo(scrimFrom)
        shown()
        launch { scrim.animateTo(Motion.ScrimAlpha, Motion.scrimInSpec()) }
        launch {
            snapshotFlow { hidden.value }.first { it <= 0.02f }
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
        hidden.animateTo(0f, Motion.sheetInSpec())
        entered = true
    }

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            val h = height.coerceAtLeast(1)
            hidden.snapTo((hidden.value + drag / h).coerceIn(0f, 1f))
            drag = 0f
            launch { scrim.animateTo(0f, Motion.scrimOutSpec()) }
            hidden.animateTo(1f, Motion.sheetOutSpec())
            closed()
        }
    }

    fun letGo(velocity: Float) {
        if (closing) return
        if (drag > height * DISMISS_FRACTION || velocity > velocityThreshold) close()
        else if (drag > 0f) scope.launch { animate(drag, 0f, animationSpec = Motion.settle()) { v, _ -> drag = v } }
    }

    BackHandler(enabled = !closing) { close() }

    // Scrolled content hands a downward drag at its top to the sheet, and an upward one back once the sheet is up.
    val nested = remember(key) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f && drag > 0f) {
                    val used = maxOf(available.y, -drag)
                    drag += used
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f && !closing) {
                    drag += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drag > 0f) {
                    letGo(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val scrimColor = MaterialTheme.colorScheme.scrim
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(scrimColor, alpha = scrim.value) }
                .pointerInput(key) { detectTapGestures { close() } }
                .clearAndSetSemantics { }
        )
        Box(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = modifier
                    .then(if (closing) INERT else Modifier)
                    .widthIn(max = MAX_CONTENT_WIDTH)
                    .fillMaxWidth()
                    .onSizeChanged { height = it.height }
                    .graphicsLayer { translationY = hidden.value * height + drag }
                    .semantics { this.paneTitle = paneTitle }
                    .focusRequester(focus)
                    .focusGroup()
                    .nestedScroll(nested)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta -> if (!closing) drag = (drag + delta).coerceAtLeast(0f) },
                        onDragStopped = { velocity -> letGo(velocity) }
                    ),
                shape = MaterialTheme.shapes.large.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
                color = BottomSheetDefaults.ContainerColor,
                tonalElevation = BottomSheetDefaults.Elevation
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    BottomSheetDefaults.DragHandle()
                    content(::close)
                }
            }
        }
    }
}
