package com.rebuild.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.rebuild.R
import com.rebuild.domain.RestTimer
import kotlinx.coroutines.delay

/** What the phone does when a rest ends, in or out of Rebuild. Exactly one is chosen. */
enum class RestSound(val title: String, val detail: String) {
    BUZZ("Buzz", "A short double vibration"),
    LONG_BUZZ("Long buzz", "Vibrates for about five seconds"),
    CHIME("Chime", "Your notification sound, with a buzz"),
    ALARM("Alarm", "Rings and vibrates until you stop it, for up to 5 minutes"),
    SILENT("Silent", "No sound or vibration, only the notification")
}

/** What Rebuild shows when a rest ends while it is open. Exactly one is chosen. */
enum class RestVisual(val title: String, val detail: String) {
    NONE("Nothing extra", "The rest bar simply closes"),
    FLASH("Flash", "The screen glows amber three times"),
    PULSE("Pulse", "Rings ripple out from the middle of the screen")
}

/**
 * One device's choice of rest-end alert. It stays on the phone (it's about this phone's speaker and screen, not
 * training history), so it is not part of backups. Choices that would contradict each other can't be made:
 * a silent alert always shows something on screen, or a rest could end with no sign at all while Rebuild is open.
 */
data class RestAlert(val sound: RestSound = RestSound.BUZZ, val visual: RestVisual = RestVisual.NONE) {
    /** Whether [visual] can go with this sound. */
    fun allows(visual: RestVisual): Boolean = !(sound == RestSound.SILENT && visual == RestVisual.NONE)

    /** Why [visual] is unavailable, or null when it can be chosen. */
    fun blockedReason(visual: RestVisual): String? =
        if (allows(visual)) null else "A silent alert needs something on screen."

    /** This alert with [sound], moving the on-screen choice off one the new sound can't go with. */
    fun withSound(sound: RestSound): RestAlert = copy(sound = sound).let { if (it.allows(it.visual)) it else it.copy(visual = RestVisual.PULSE) }

    /** This alert with [visual], unless the current sound rules it out. */
    fun withVisual(visual: RestVisual): RestAlert = if (allows(visual)) copy(visual = visual) else this

    /** The alarm keeps ringing, so Rebuild always offers a way to stop it on screen. */
    val needsStop: Boolean get() = sound == RestSound.ALARM
}

@Composable
internal fun RestAlertScreen(env: AppEnv, onBack: () -> Unit) {
    val alert = env.restAlert
    ScreenScaffold(title = "Rest alert", onBack = onBack) {
        ScrollBody {
            Text("How you hear that a rest is over, and what Rebuild shows if it's open. It applies on this phone only.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionHeader("Sound and vibration")
            ChoiceGroup(RestSound.entries, alert.sound, "rest-sound", { it.title }, { it.detail }) {
                env.platform.setRestAlert(alert.withSound(it))
            }
            SectionHeader("On screen, while Rebuild is open")
            ChoiceGroup(RestVisual.entries, alert.visual, "rest-visual", { it.title },
                { alert.blockedReason(it) ?: it.detail }, enabled = { alert.allows(it) }) {
                env.platform.setRestAlert(alert.withVisual(it))
            }
            if (alert.needsStop) {
                Text("The alarm also shows a Stop button here, and stops when you open Rebuild or tap its notification.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SecondaryButton(onClick = { env.previewRestEnd() }, icon = R.drawable.ic_play,
                modifier = Modifier.testTag("rest-alert-try")) { Text("Try it") }
            PlatformLimitations(env.status, env.platform)
        }
    }
}

@Composable
private fun <T : Enum<T>> ChoiceGroup(
    options: List<T>,
    selected: T,
    tagPrefix: String,
    title: (T) -> String,
    detail: (T) -> String,
    enabled: (T) -> Boolean = { true },
    onSelect: (T) -> Unit
) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, option ->
            val on = enabled(option)
            Row(
                Modifier
                    .groupedRow(i, options.lastIndex)
                    .selectable(selected = option == selected, enabled = on, role = Role.RadioButton) { onSelect(option) }
                    .heightIn(min = 56.dp)
                    .padding(vertical = 8.dp)
                    .alpha(if (on) 1f else 0.5f)
                    .testTag("$tagPrefix-${option.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RadioButton(selected = option == selected, onClick = null, enabled = on)
                Column(Modifier.weight(1f)) {
                    Text(title(option), style = MaterialTheme.typography.bodyLarge)
                    Text(detail(option), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** A rest that ended in front of the user, waiting for its on-screen alert. */
internal data class RestEnd(val id: String, val shortPause: Boolean, val preview: Boolean = false)

/**
 * Watches the running rest and reports its end the moment its deadline passes, but only while Rebuild is in front:
 * a rest that ended in the background was already announced by its notification, so it never flashes later.
 */
@Composable
internal fun RestEndWatcher(timer: RestTimer?, clock: () -> Long, onEnd: (RestEnd) -> Unit) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val report by rememberUpdatedState(onEnd)
    LaunchedEffect(timer?.id, timer?.deadline, resumed) {
        if (timer == null || !resumed) return@LaunchedEffect
        val wait = timer.deadline - clock()
        if (wait < -1_000) return@LaunchedEffect
        if (wait > 0) delay(wait)
        report(RestEnd(timer.id, timer.shortPause))
    }
}

/**
 * The on-screen part of a rest-end alert, over everything: an amber glow three times (at one flash a second,
 * well under the three-a-second photosensitivity limit), or rings rippling out behind "Rest's up". With the
 * system's animations turned off both hold still. Tapping anywhere closes it; an alarm keeps it up with a Stop button.
 */
@Composable
internal fun RestEndOverlay(end: RestEnd, alert: RestAlert, onStopAlarm: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val still = remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
            .getOrDefault(1f) == 0f
    }
    val done by rememberUpdatedState(onDone)
    val ringing = alert.needsStop
    val title = if (end.shortPause) "Pause's up" else "Rest's up"
    if (alert.visual == RestVisual.NONE && !ringing) {
        LaunchedEffect(end) { done() }
        return
    }
    LaunchedEffect(end, ringing) {
        // A flash or pulse lasts a few seconds; an alarm's screen stays until it is stopped.
        if (!ringing) {
            delay(if (alert.visual == RestVisual.FLASH) 3_200 else 4_000)
            done()
        }
    }
    val amber = MaterialTheme.colorScheme.primaryContainer
    Box(
        Modifier
            .fillMaxSize()
            .testTag("rest-end-overlay")
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (ringing) onStopAlarm()
                done()
            }
            .semantics { paneTitle = title },
        contentAlignment = Alignment.Center
    ) {
        when (alert.visual) {
            RestVisual.FLASH -> {
                val glow = remember(end) { Animatable(0f) }
                LaunchedEffect(end, still) {
                    if (still) { glow.snapTo(0.55f); return@LaunchedEffect }
                    repeat(3) {
                        glow.animateTo(0.75f, tween(350))
                        glow.animateTo(0f, tween(650))
                    }
                }
                Box(Modifier.fillMaxSize().background(amber.copy(alpha = glow.value)))
            }
            RestVisual.PULSE -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.85f)))
            RestVisual.NONE -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.85f)))
        }
        Column(
            Modifier.safeDrawingPadding().padding(24.dp).widthIn(max = 360.dp)
                .semantics { liveRegion = LiveRegionMode.Assertive },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (alert.visual == RestVisual.PULSE) {
                val waves = rememberInfiniteTransition(label = "rest-end-rings")
                val phase by waves.animateFloat(0f, 1f, infiniteRepeatable(tween(1_600, easing = LinearEasing), RepeatMode.Restart),
                    label = "rest-end-ring-phase")
                // Rings ripple outward past the badge's own bounds; the badge stays put in the middle.
                Box(
                    Modifier.padding(vertical = 48.dp).size(112.dp).drawBehind {
                        val center = Offset(size.width / 2, size.height / 2)
                        val reach = 120.dp.toPx()
                        repeat(3) { ring ->
                            val p = if (still) (ring + 1) / 3f else (phase + ring / 3f) % 1f
                            drawCircle(amber.copy(alpha = (1f - p) * 0.8f), radius = size.minDimension / 2 + reach * p,
                                center = center, style = Stroke(width = 6.dp.toPx() * (1f - p) + 1.dp.toPx()))
                        }
                        drawCircle(amber, radius = size.minDimension / 2, center = center)
                    },
                    contentAlignment = Alignment.Center
                ) {
                    RebuildIcon(R.drawable.ic_rest, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(48.dp))
                }
            }
            // Over a flash or a still screen the words sit on a card, so they read against whatever is behind.
            val card = alert.visual != RestVisual.PULSE
            Column(
                if (card) Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(24.dp) else Modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (card) RebuildIcon(R.drawable.ic_rest, contentDescription = null, tint = accentText(),
                    modifier = Modifier.size(32.dp))
                Text(title, style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = if (card) MaterialTheme.colorScheme.onSurface else androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.testTag("rest-end-title"))
                if (ringing) {
                    Button(onClick = { onStopAlarm(); done() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .testTag("rest-end-stop")) { Text("Stop alarm") }
                }
            }
        }
    }
}

/** Keeps the latest [RestEnd] for [RestEndOverlay]; a preview from settings uses the same path. */
internal class RestEndState {
    var current by mutableStateOf<RestEnd?>(null)
}
