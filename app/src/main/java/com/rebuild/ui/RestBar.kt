package com.rebuild.ui

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebuild.domain.Action
import com.rebuild.domain.RestTimer
import kotlinx.coroutines.delay

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
    var options by rememberSaveable(timer.id) { mutableStateOf(false) }
    val compactLandscape = compactLandscapeInput()
    val compact = compactLandscape || LocalDensity.current.fontScale > 1.3f

    fun adjust(deltaMs: Long) {
        env.runner.run(restChange(timer, timer.deadline + deltaMs, env.clock()))
    }

    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.testTag("rest-bar")) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = if (compact) 0.dp else 8.dp,
            bottom = if (compact) 0.dp else 8.dp)) {
            val limitation = when {
                !env.status.notificationsAllowed -> "alerts off"
                !env.status.exactAlarmsAllowed -> "alerts may be late"
                env.status.batteryRestricted -> "battery limits alerts"
                else -> null
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // The number a resting lifter looks for is the largest thing on the bar.
                Column(
                    Modifier.weight(1f).testTag("rest-status")
                        .semantics(mergeDescendants = true) { if (remaining == 0) liveRegion = LiveRegionMode.Polite }
                ) {
                    // An alert limitation is part of the status line; Options explains it and opens settings.
                    val word = if (pause) "Pause" else "Rest"
                    val note = limitation?.let { " · $it" } ?: ""
                    val time = if (remaining > 0) formatRest(remaining) else "Done"
                    // Short windows and large text keep the bar to one line, so it never squeezes the entry.
                    if (compact) Text("$word $time$note", style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    else {
                        Text(word + note, style = MaterialTheme.typography.labelMedium)
                        Text(time, style = MaterialTheme.typography.headlineMedium)
                    }
                }
                if (remaining > 0) {
                    TextButton(
                        onClick = { adjust(30_000) },
                        enabled = !env.runner.busy,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Add 30 seconds" }
                    ) { Text("+30 s") }
                }
                IconButton(onClick = { env.runner.run(Action.SkipRest) }, enabled = !env.runner.busy, modifier = Modifier.size(48.dp)) {
                    RebuildIcon(if (remaining > 0) R.drawable.ic_skip_forward else R.drawable.ic_x,
                        contentDescription = if (remaining > 0) "Skip rest" else "Dismiss rest")
                }
                IconButton(onClick = { options = true }, modifier = Modifier.size(48.dp)) {
                    RebuildIcon(R.drawable.ic_ellipsis_vertical, contentDescription = "Rest options")
                }
            }
            if (total != null && remaining > 0 && !compactLandscape) {
                LinearProgressIndicator(
                    progress = { (remainingMs / (total * 1000f)).coerceIn(0f, 1f) },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.16f),
                    drawStopIndicator = {},
                    // The countdown is the accessible equivalent; expanded progress semantics overlap the controls.
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 8.dp).clearAndSetSemantics {}
                )
            }

        }
    }

    if (options) AdaptiveDialog(
        onDismissRequest = { options = false },
        title = { Text(if (pause) "Pause options" else "Rest options") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (remaining > 0) "${formatRest(remaining)} remaining" else if (pause) "The pause is over." else "Rest is complete.")
                if (we != null) Text("After ${we.exercise.name}")
                if (remaining > 0) {
                    TextButton(onClick = { adjust(-30_000) }, enabled = !env.runner.busy,
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
        var seconds by rememberSaveable(timer.id) { mutableStateOf(remaining.toString()) }
        var error by rememberSaveable(timer.id) { mutableStateOf<String?>(null) }
        AdaptiveDialog(
            onDismissRequest = { editing = false },
            title = { Text("Rest time remaining") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(seconds, { seconds = it }, "Seconds from now", decimal = false, imeAction = ImeAction.Done)
                    ErrorText(error)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = seconds.toIntOrNull()
                    if (value == null || value !in 0..REST_SECONDS_RANGE.last) {
                        error = "Enter 0 to 3600 seconds. 0 ends the rest."
                    } else {
                        val now = env.clock()
                        env.runner.run(restChange(timer, now + value * 1000L, now), onError = { error = it }) {
                            editing = false
                        }
                    }
                }, enabled = !env.runner.busy) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } }
        )
    }
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
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier.fillMaxSize().testTag("rest-pip")) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (timer?.shortPause == true) "Pause" else "Rest",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    if (remaining > 0) formatRest(remaining) else "Done",
                    style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum", fontSize = 44.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
