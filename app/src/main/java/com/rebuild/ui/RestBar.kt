package com.rebuild.ui

import com.rebuild.ui.theme.InkPress
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import com.rebuild.ui.theme.Numerals
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import com.rebuild.R
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebuild.domain.Action
import com.rebuild.domain.RestTimer
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Animatable
import com.rebuild.ui.theme.Motion

/**
 * Displays the single persisted rest countdown. Remaining time is always derived from the
 * repository's wall-clock deadline; this composable only ticks to refresh the text.
 * Alerts are scheduled by the platform from repository state, not from here.
 */
@Composable
internal fun RestBar(env: AppEnv, timer: RestTimer) {
    var now by remember { mutableLongStateOf(env.clock()) }
    LaunchedEffect(timer.id, timer.deadline) {
        while (true) {
            now = env.clock()
            val remainder = (timer.deadline - now) % 1000
            delay(if (remainder in 1..999) remainder else 1000)
        }
    }
    val remainingMs = (timer.deadline - now).coerceAtLeast(0)
    val remaining = ((remainingMs + 999) / 1000).toInt()
    val we = env.state.workouts.find { it.id == timer.workoutId }?.exercises?.find { it.id == timer.exerciseId }
    // A short pause inside a rest-pause set isn't the rest between sets, so it has no rest progress.
    val pause = timer.shortPause
    val total = we?.restSeconds?.takeIf { it > 0 && !pause }
    var editing by rememberSaveable(timer.id) { mutableStateOf(false) }
    var barHeight by remember { mutableIntStateOf(0) }
    val window = barWindow()
    val undoWindow = LocalAccessibilityManager.current?.calculateRecommendedTimeoutMillis(
        ActionRunner.UNDO_SKIP_MS, containsIcons = false, containsText = true, containsControls = true
    ) ?: ActionRunner.UNDO_SKIP_MS
    var options by rememberSaveable(timer.id) { mutableStateOf(false) }
    val compact = compactLandscapeInput() || shortLandscapeWindow() || LocalDensity.current.fontScale > 1.3f

    fun adjust(deltaMs: Long) {
        env.runner.run(restChange(timer, timer.deadline + deltaMs, env.clock()))
    }

    val colors = MaterialTheme.colorScheme
    val trackColor = colors.onPrimaryContainer.copy(alpha = 0.18f)
    val fillColor = colors.onPrimaryContainer
    val progress = if (total != null && remaining > 0) (remainingMs / (total * 1000f)).coerceIn(0f, 1f) else 0f
    // Each second the line steps in place; a jump from +30 s, −30 s or a new time moves within the bar.
    val line = remember(timer.id) { Animatable(progress) }
    var lineDeadline by remember(timer.id) { mutableLongStateOf(timer.deadline) }
    LaunchedEffect(progress) {
        if (timer.deadline != lineDeadline) {
            lineDeadline = timer.deadline
            line.animateTo(progress, Motion.moveWithinSpec())
        } else {
            line.snapTo(progress)
        }
    }
    val limitation = when {
        !env.status.notificationsAllowed -> "alerts off"
        !env.status.exactAlarmsAllowed -> "alerts may be late"
        env.status.batteryRestricted -> "battery limits alerts"
        else -> null
    }
    val showRestTip = !compact && remaining > 0 && shouldShowRestBarTip(env.state, env.tips, pause)
    // Remember when this rest showed the tip so it retires once this first rest ends or is skipped, instead of
    // repeating on every subsequent rest of the first workout.
    var tipSeenOnTimer by rememberSaveable(timer.id) { mutableStateOf(false) }
    if (showRestTip && !tipSeenOnTimer) tipSeenOnTimer = true
    LaunchedEffect(timer.id, remaining) {
        if (tipSeenOnTimer && remaining == 0) env.platform.dismissTip(Tip.REST_BAR)
    }
    val latestEnv by rememberUpdatedState(env)
    DisposableEffect(timer.id) {
        onDispose {
            if (tipSeenOnTimer && latestEnv.state.restTimer?.id != timer.id) {
                latestEnv.platform.dismissTip(Tip.REST_BAR)
            }
        }
    }
    // A floating signal-amber pill above the navigation, with a thin ink progress line along its foot. Decorative; the
    // countdown is what's spoken.
    // A full-width amber sheet docked above the navigation: rounded top corners, square bottom, edge to edge.
    Box(Modifier.fillMaxWidth().onSizeChanged { barHeight = it.height }) {
        Surface(
            color = colors.primaryContainer, // fill: softened on dark
            contentColor = colors.onPrimaryContainer,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth().testTag("rest-bar")
        ) { InkPress { // on the amber sheet, presses are ink
            // The progress line always sits along the foot of the whole amber sheet (below any tip).
            Column(
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        if (total != null && remaining > 0 && !compact) {
                            val inset = 20.dp.toPx()
                            val h = 3.dp.toPx()
                            val y = size.height - 8.dp.toPx()
                            val track = size.width - inset * 2
                            drawRoundRect(trackColor, Offset(inset, y), Size(track, h), CornerRadius(h / 2))
                            drawRoundRect(fillColor, Offset(inset, y), Size(track * line.value, h), CornerRadius(h / 2))
                        }
                    }
                    .padding(bottom = if (compact) 2.dp else 14.dp)
            ) {
            Row(
                Modifier
                    .padding(start = 20.dp, end = 8.dp, top = if (compact) 2.dp else 12.dp, bottom = if (showRestTip) 6.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // The number a resting lifter looks for is the largest thing on the bar.
                Column(
                    Modifier.weight(1f).testTag("rest-status")
                        .semantics(mergeDescendants = true) { if (remaining == 0) liveRegion = LiveRegionMode.Polite }
                ) {
                    val word = if (pause) "Pause" else "Rest"
                    val note = limitation?.let { " · $it" } ?: ""
                    val time = if (remaining > 0) formatRest(remaining) else "Done"
                    // Short windows and large text keep the bar to one line, so it never squeezes the entry. The
                    // countdown stays whole; an alert limitation follows it and is the part that gets cut short.
                    // The time keeps its text in the merged status (tests and TalkBack read it there); its description
                    // already carries the limitation, so the visible note is not read a second time.
                    if (compact) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$word $time", style = MaterialTheme.typography.titleLarge.copy(fontFamily = Numerals),
                            maxLines = 1, softWrap = false,
                            modifier = Modifier.semantics {
                                contentDescription = "$word $time" + (limitation?.let { ", $it" } ?: "")
                            })
                        if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                            softWrap = false, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).clearAndSetSemantics {})
                    }
                    else {
                        Text(word + note, style = overlineStyle())
                        Text(time, style = MaterialTheme.typography.displayMedium)
                    }
                }
                val quiet = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.White.copy(alpha = 0.38f),
                    contentColor = colors.onPrimaryContainer
                )
                // Skip ends the rest: ink on amber, the strongest control on the pill. Its label is cut from the sheet's own
        // (softened on dark) fill.
                val strong = ButtonDefaults.filledTonalButtonColors(
                    containerColor = colors.onPrimaryContainer,
                    contentColor = colors.primaryContainer
                )
                if (remaining > 0) {
                    FilledTonalButton(
                        onClick = { adjust(30_000) },
                        enabled = !env.runner.looksBusy(),
                        colors = quiet,
                        contentPadding = PaddingValues(horizontal = if (compact) 10.dp else 14.dp),
                        // Twice the usual gap before Skip, so a thumb aiming for +30 s doesn't end the rest.
                        modifier = Modifier.padding(end = 6.dp).heightIn(min = 48.dp)
                            .semantics { contentDescription = "Add 30 seconds" }
                    ) { Text(if (compact) "+30" else "+30 s", maxLines = 1, softWrap = false) }
                }
                // Skipping ends this rest for good, so it's a labelled button rather than a bare icon beside Options.
                FilledTonalButton(
                    onClick = {
                        // Read before the tip goes: the bar offering Undo keeps the height it has now.
                        val heldHeight = barHeight
                        if (tipSeenOnTimer) env.platform.dismissTip(Tip.REST_BAR)
                        // A skip with time left can be undone for a few seconds, from the bar itself; dismissing a
                        // finished (or all but finished) rest has nothing worth bringing back.
                        val skipped = timer.takeIf { it.deadline - env.clock() > UNDO_MIN_LEFT_MS }
                        env.runner.run(Action.SkipRest, onSaved = {
                            if (skipped != null) env.runner.offerUndoSkip(skipped, undoWindow, heldHeight, window)
                        })
                    },
                    enabled = !env.runner.looksBusy(),
                    colors = strong,
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                        contentDescription = if (remaining > 0) "Skip rest" else "Dismiss rest"
                    }
                ) { Text(if (remaining > 0) "Skip" else "Dismiss", maxLines = 1) }
                IconButton(onClick = { options = true }, modifier = Modifier.size(48.dp)) {
                    RebuildIcon(R.drawable.ic_ellipsis_vertical, contentDescription = "Rest options")
                }
            }
            RestBarTip(
                visible = showRestTip,
                onDismiss = { env.platform.dismissTip(Tip.REST_BAR) }
            )
            }
        } }
    }

    if (options) AdaptiveDialog(
        onDismissRequest = { options = false },
        title = { Text(if (pause) "Pause options" else "Rest options") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (remaining > 0) "${formatRest(remaining)} remaining" else if (pause) "The pause is over." else "Rest is complete.")
                if (we != null) Text("After ${we.exercise.name}")
                if (remaining > 0) {
                    TextButton(onClick = { adjust(-30_000) }, enabled = !env.runner.looksBusy(),
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("−30 s") }
                    TextButton(onClick = { options = false; editing = true },
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("Set time") }
                }
                PlatformLimitations(env.status, env.platform, showAllClear = true)
            }
        },
        confirmButton = { TextButton(onClick = { options = false }) { Text("Done") } }
    )

    if (editing) {
        // The platform's minute and second wheels; 0:00 ends the rest.
        DurationPickerDialog(
            title = "Rest time remaining",
            initialSeconds = remaining,
            maxSeconds = REST_SECONDS_RANGE.last,
            allowBlank = false,
            onDismiss = { editing = false },
            onPick = { picked ->
                val now = env.clock()
                env.runner.run(restChange(timer, now + (picked ?: 0) * 1000L, now)) { editing = false }
            }
        )
    }
}

/** What the rest bar's height depends on: the window's size and orientation and the text scale. */
@Composable
internal fun barWindow(): String = LocalConfiguration.current.let {
    "${it.orientation}:${it.screenWidthDp}x${it.screenHeightDp}@${it.fontScale}"
}

/** Below this much time left, a skip just ends the rest: Undo would bring back a rest that's already over. */
private const val UNDO_MIN_LEFT_MS = 5_000L

/**
 * The rest bar a moment after Skip: the same amber sheet, the same height (so the dock doesn't move), with "Skipped"
 * where the countdown was and Undo where Skip was. It collapses when the undo window ends.
 */
@Composable
internal fun SkippedRestBar(env: AppEnv, timer: RestTimer, minHeight: Dp = 0.dp) {
    val colors = MaterialTheme.colorScheme
    val compact = compactLandscapeInput() || shortLandscapeWindow() || LocalDensity.current.fontScale > 1.3f
    val word = if (timer.shortPause) "Pause" else "Rest"
    Surface(
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        // At least as tall as the bar it replaced (which may have carried a tip), so nothing above it moves.
        modifier = Modifier.fillMaxWidth().heightIn(min = minHeight).testTag("rest-skipped")
    ) { InkPress {
        Row(
            Modifier.padding(start = 20.dp, end = 8.dp, top = if (compact) 2.dp else 12.dp,
                bottom = if (compact) 2.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                if (compact) Text("$word skipped", style = MaterialTheme.typography.titleLarge, maxLines = 1,
                    softWrap = false, overflow = TextOverflow.Ellipsis)
                else {
                    Text(word, style = overlineStyle())
                    Text("Skipped", style = MaterialTheme.typography.displayMedium, maxLines = 1, softWrap = false,
                        overflow = TextOverflow.Ellipsis)
                }
            }
            FilledTonalButton(
                onClick = {
                    env.runner.run(Action.RestoreRest(timer, env.clock()), onSaved = { env.runner.endUndoSkip() })
                },
                enabled = !env.runner.looksBusy(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = colors.onPrimaryContainer,
                    contentColor = colors.primaryContainer
                ),
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.heightIn(min = 48.dp).semantics {
                    contentDescription = if (timer.shortPause) "Undo skip, bring the pause back"
                        else "Undo skip, bring the rest back"
                }
            ) { Text("Undo", maxLines = 1) }
            // Where Options sat, so Undo lands under the thumb that just pressed Skip.
            Box(Modifier.size(48.dp))
        }
    } }
}

/**
 * A new deadline that is already due skips the rest; otherwise it stays at least a second out,
 * so the platform never has to schedule an alert in the past.
 */
internal fun restChange(timer: RestTimer, deadline: Long, now: Long): Action =
    if (deadline <= now) Action.SkipRest else Action.AdjustRest(timer.id, maxOf(deadline, now + 1_000))

/**
 * The rest countdown alone, for the small picture-in-picture window shown while Rebuild is in the background.
 * Like [RestBar] it only derives the remaining time from the saved deadline.
 */
@Composable
internal fun RestPip(timer: RestTimer?, clock: () -> Long, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(clock()) }
    LaunchedEffect(timer?.id, timer?.deadline) {
        while (timer != null) {
            now = clock()
            val remainder = (timer.deadline - now) % 1000
            delay(if (remainder in 1..999) remainder else 1000)
        }
    }
    val remaining = timer?.let { (((it.deadline - now).coerceAtLeast(0) + 999) / 1000).toInt() } ?: 0
    // The rest sheet's own amber and ink, label and numerals, so the window reads as the same countdown.
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.fillMaxSize().testTag("rest-pip")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (timer?.shortPause == true) "Pause" else "Rest", style = overlineStyle(), maxLines = 1)
                Text(
                    if (remaining > 0) formatRest(remaining) else "Done",
                    style = MaterialTheme.typography.displayMedium,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
