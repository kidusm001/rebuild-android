package com.rebuild.ui

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.rebuild.R
import com.rebuild.domain.measured
import com.rebuild.ui.theme.SignalAmber
import com.rebuild.ui.theme.Motion
import com.rebuild.ui.theme.FadeInOver
import com.rebuild.ui.theme.animationsOff
import com.rebuild.ui.theme.fadeOver
import kotlinx.coroutines.delay

/**
 * Draws, once per process and shortly after launch, the kinds of surfaces the app's transitions draw (an opaque panel
 * fading in on its own layer, a copy held under it with its overhang fading, a dashed slot, a back-preview card), at a
 * strength nobody can see (about 1/255), a frame each. The GPU then builds the programs for them while the app is
 * idle, instead of in the frame a lifter logs a set, reaches the target or moves to the next exercise: on a GPU without
 * a program cache (the emulator) each takes tens of milliseconds the first time. Nothing here takes input or is read
 * aloud, and it is gone after a few frames.
 */
@Composable
internal fun MotionWarmUp(state: com.rebuild.domain.AppState, modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current || animationsOff()) return
    // Once per process: a recreation (rotation) doesn't draw it again.
    var step by rememberSaveable { mutableIntStateOf(0) }
    if (step < 0) return
    LaunchedEffect(Unit) {
        if (step == 0) delay(WARM_UP_AFTER_MS)
        for (next in step + 1..WARM_UP_STEPS) {
            withFrameNanos { }
            step = next
        }
        // The last step runs the real Fade in over transitions to their end.
        delay(Motion.Long.toLong())
        withFrameNanos { }
        step = -1
    }
    if (step == 0) return
    Box(
        modifier
            .then(INERT)
            // Every draw at about 1/255 of its strength, applied per draw (no layer of its own), so what is drawn
            // reaches the window as the real transitions' draws do.
            .graphicsLayer {
                alpha = WARM_UP_ALPHA
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
    ) {
        when (step) {
            // Fade in over: an opaque panel with text, an icon and buttons, risen a little, fading in on its own layer.
            1 -> Box(Modifier.graphicsLayer { alpha = 0.5f }.background(MaterialTheme.colorScheme.surfaceContainer)) {
                Box(Modifier.graphicsLayer { translationY = 3.4f }) { Panel() }
            }
            // The copy held underneath: drawn inside the incoming copy's rect, its overhang fading on a layer.
            2 -> Box(Modifier.fadeOver(incoming = { IntSize(120, 60) }, overhang = { 0.5f }, alignment = Alignment.BottomStart)
                .background(MaterialTheme.colorScheme.surfaceContainer)) { Panel() }
            // A whole screen layer fading and travelling, with a card and the dashed slot on it.
            3 -> Box(Modifier.graphicsLayer { alpha = 0.5f; translationX = 8f }) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(120.dp, 44.dp).dashedOutline(MaterialTheme.colorScheme.outline)) {
                        Text("Up next", style = MaterialTheme.typography.labelLarge)
                    }
                    Box(Modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .padding(8.dp)) { Text("40 kg × 10", style = MaterialTheme.typography.titleMedium) }
                }
            }
            // The back preview: a scaled card with rounded corners and a shadow.
            4 -> Box(Modifier.graphicsLayer {
                scaleX = 0.97f
                scaleY = 0.97f
                shape = RoundedCornerShape(16.dp)
                clip = true
                shadowElevation = 8.dp.toPx()
            }.background(MaterialTheme.colorScheme.surface)) { Panel() }
            // The real Fade in over, as the dock (Target done) and a pill's badge use it: drawn first as they were,
            // then changed, so the next frames draw the transition itself.
            WARM_UP_STEPS - 1, WARM_UP_STEPS -> Column {
                val done = step == WARM_UP_STEPS
                val rise = with(androidx.compose.ui.platform.LocalDensity.current) { Motion.Nudge.toPx() }
                // A real ledger from the history taking its last set, as when a target is reached: the slot giving way
                // to the value, the count rolling and taking the accent, the band, the body's fading edge.
                val sample = remember(state.workouts.size) { ledgerSample(state) }
                if (sample != null) {
                    val (workout, we) = sample
                    val scroll = androidx.compose.foundation.rememberScrollState(Int.MAX_VALUE / 2)
                    Column(Modifier.width(360.dp).heightIn(max = 160.dp).fadingEdges(scroll).verticalScroll(scroll)) {
                        SetLedger(if (done) we else we.copy(sets = we.sets.dropLast(1)), we, workout.startedAt)
                        Spacer(Modifier.height(120.dp))
                    }
                }
                Row(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).padding(6.dp)) {
                    OrderBadge(1, met = done)
                    Text("Leg press", style = MaterialTheme.typography.labelLarge)
                }
                // The dock changing kind, built as the workout's own (LogDock, Fade in over from its foot, the Rise).
                LogDock(onFocusChange = {}, modifier = Modifier.width(320.dp)) {
                    FadeInOver(done, contentAlignment = Alignment.BottomStart, clip = true,
                        backing = MaterialTheme.colorScheme.surfaceContainer, label = "warm-up-dock") { shown ->
                        val y by transition.animateFloat(transitionSpec = { Motion.riseSpec() }, label = "warm-up-rise") {
                            if (it == EnterExitState.PreEnter) rise else 0f
                        }
                        val riseModifier = remember { Modifier.graphicsLayer { translationY = y } }
                        CompositionLocalProvider(LocalDockRise provides riseModifier) {
                            Column {
                                if (shown) TargetMetPanel("Bulgarian split squat", onNext = {}, onFinish = {},
                                    finishEnabled = true, dropLabel = "Add drop to set 2", onExtra = {})
                                else Panel(tall = false)
                            }
                        }
                    }
                }
            }
            // The exercise switch's body: alpha on each draw, travelling, over the canvas.
            else -> Box(Modifier.graphicsLayer {
                alpha = 0.5f
                translationX = 8f
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }) { Panel() }
        }
    }
}

/** A finished rep-based exercise with at least two working sets against a target, to draw a real ledger with. */
private fun ledgerSample(state: com.rebuild.domain.AppState): Pair<com.rebuild.domain.Workout, com.rebuild.domain.WorkoutExercise>? =
    state.workouts.asReversed().asSequence().filter { it.finishedAt != null }.flatMap { w ->
        w.exercises.asSequence().map { w to it }
    }.firstOrNull { (_, we) ->
        !we.exercise.measured && we.targetSets != null && we.setGroups.isEmpty() && we.sets.count { !it.warmup } >= 2 &&
            we.sets.none { it.warmup }
    }

/** The dock's usual contents: a status line with its icon, the amber primary button, an outlined one. */
@Composable
private fun Panel(tall: Boolean = true) {
    Column(Modifier.width(240.dp).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (tall) Text("Add drop to set 2", style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RebuildIcon(R.drawable.ic_check, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Target done", style = MaterialTheme.typography.titleSmall)
        }
        Button(onClick = {}) { Text("Next") }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = {}) { Text("Extra set") }
            Box(Modifier.size(24.dp).clip(CircleShape).background(SignalAmber), contentAlignment = Alignment.Center) {
                Text("1", style = MaterialTheme.typography.labelSmall)
            }
            Box(Modifier.size(24.dp).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
        }
    }
}

/** After the first screen and the kept tabs (built from 600 ms) have settled. */
private const val WARM_UP_AFTER_MS = 1200L
private const val WARM_UP_STEPS = 7
/** About 2.5/255: the layers inside (alpha 0.5) still reach the screen at a non-zero 1/255. */
private const val WARM_UP_ALPHA = 0.01f
