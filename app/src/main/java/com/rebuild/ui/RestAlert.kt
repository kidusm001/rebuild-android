package com.rebuild.ui

import com.rebuild.ui.theme.InkPress
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
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
    CHIME("Chime", "Notification sound and a buzz, muted in silent mode"),
    ALARM("Alarm", "Rings until you stop it, up to 5 minutes. Plays in silent mode; short rests buzz instead."),
    SILENT("Silent", "Only the notification, with no sound or vibration")
}

/** What Rebuild shows when a rest ends while it is open. Exactly one is chosen. */
enum class RestVisual(val title: String, val detail: String) {
    NONE("None", "Sound and vibration only"),
    FLASH("Edge light", "The screen's edges glow three times"),
    SWEEP("Up next", "The rest bar fills the screen with your next set")
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
    fun withSound(sound: RestSound): RestAlert = copy(sound = sound).let { if (it.allows(it.visual)) it else it.copy(visual = RestVisual.FLASH) }

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
            SecondaryButton(onClick = { env.previewRestEnd() }, icon = R.drawable.ic_play,
                modifier = Modifier.testTag("rest-alert-try")) { Text("Try alert") }
            PlatformLimitations(env.status, env.platform)
        }
    }
}

@Composable
internal fun <T : Enum<T>> ChoiceGroup(
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
internal data class RestEnd(
    val id: String,
    val shortPause: Boolean,
    val preview: Boolean = false,
    /** What comes next, for the takeover's headline and TalkBack; null when the workout can't say. */
    val about: com.rebuild.platform.RestContext? = null
)

/**
 * Watches the running rest and reports its end the moment its deadline passes, but only while Rebuild is in front:
 * a rest that ended in the background was already announced by its notification, so it never flashes later.
 */
@Composable
internal fun RestEndWatcher(
    timer: RestTimer?,
    clock: () -> Long,
    about: (RestTimer) -> com.rebuild.platform.RestContext? = { null },
    onEnd: (RestEnd) -> Unit
) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val report by rememberUpdatedState(onEnd)
    val describe by rememberUpdatedState(about)
    // Android's alarm and this watcher both act at the deadline. Whichever ends the rest first, the screen alert must
    // still show: so the last rest seen is kept, and a rest that vanishes at its deadline counts as ended (a skip
    // vanishes early, so it never does). Each rest is reported once.
    var last by remember { mutableStateOf<RestTimer?>(null) }
    var reported by remember { mutableStateOf<String?>(null) }
    fun reportOnce(ended: RestTimer) {
        if (reported == ended.id) return
        reported = ended.id
        report(RestEnd(ended.id, ended.shortPause, about = describe(ended)))
    }
    LaunchedEffect(timer?.id, timer?.deadline, resumed) {
        if (timer != null) last = timer
        if (!resumed) return@LaunchedEffect
        if (timer == null) {
            val gone = last ?: return@LaunchedEffect
            val since = clock() - gone.deadline
            if (since >= -1_000 && since < 5_000) reportOnce(gone)
            return@LaunchedEffect
        }
        val wait = timer.deadline - clock()
        if (wait < -1_000) return@LaunchedEffect
        if (wait > 0) delay(wait)
        reportOnce(timer)
    }
}

/**
 * The on-screen part of a rest's end, over everything.
 *
 * - Edge light: [RestEndGlow], the screen's edges glowing in three smooth beats; the workout stays readable.
 * - Up next: [RestEndTakeover], the rest bar filling the screen with the next set, then dropping away.
 *
 * TalkBack hears the end and what's next. A ringing alarm's Stop lives in the rest bar's place, not here.
 */
@Composable
internal fun RestEndOverlay(end: RestEnd, alert: RestAlert, onDone: () -> Unit, restBarTop: Float? = null) {
    val context = LocalContext.current
    val still = remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
            .getOrDefault(1f) == 0f
    }
    val done by rememberUpdatedState(onDone)
    Box(Modifier.fillMaxSize().testTag("rest-end-overlay")) {
        when (alert.visual) {
            RestVisual.FLASH -> RestEndGlow(end, still, onFinished = { done() })
            RestVisual.SWEEP -> {
                // Amber under the status bar: its icons turn dark for the moment, then go back.
                val view = LocalView.current
                DisposableEffect(end) {
                    val window = (view.context as? android.app.Activity)?.window
                    val controller = window?.let { WindowCompat.getInsetsController(it, view) }
                    val before = controller?.isAppearanceLightStatusBars
                    controller?.isAppearanceLightStatusBars = true
                    onDispose { if (before != null) controller.isAppearanceLightStatusBars = before }
                }
                RestEndTakeover(end, still, end.about, end.shortPause, onFinished = { done() }, startTop = restBarTop)
            }
            RestVisual.NONE -> LaunchedEffect(end) { done() }
        }
        // Announced once, without stealing focus: the end, then what to do now.
        Box(Modifier.semantics {
            contentDescription = (if (end.shortPause) "Pause over" else "Rest over") +
                (end.about?.let { ". ${it.next}" } ?: "")
            liveRegion = LiveRegionMode.Polite
        })
    }
}

internal const val FLASH_BEATS = 3
internal const val FLASH_PERIOD_MS = 1000f
internal const val FLASH_RISE_MS = 140f
internal const val FLASH_FALL_MS = 460f
internal const val FLASH_TOTAL_MS = FLASH_PERIOD_MS * (FLASH_BEATS - 1) + FLASH_RISE_MS + FLASH_FALL_MS

/**
 * How bright the flash is [t] ms in, from 0 to 1: three beats a second apart (well under three a second). Each beat
 * swells in 140 ms with an ease-out cubic and settles over 460 ms with a smootherstep, so the light leaves and
 * reaches its peak with zero speed: no corners anywhere in the curve, which is what makes it read as smooth.
 */
internal fun flashLevel(t: Float): Float {
    if (t < 0f || t >= FLASH_TOTAL_MS) return 0f
    val beat = (t / FLASH_PERIOD_MS).toInt().coerceAtMost(FLASH_BEATS - 1)
    val x = t - beat * FLASH_PERIOD_MS
    return when {
        x < FLASH_RISE_MS -> {
            val u = x / FLASH_RISE_MS
            // Smoothstep in: starts and arrives at rest.
            u * u * (3f - 2f * u)
        }
        x < FLASH_RISE_MS + FLASH_FALL_MS -> {
            val v = (x - FLASH_RISE_MS) / FLASH_FALL_MS
            // Smootherstep out: a long, soft settle.
            1f - v * v * v * (v * (v * 6f - 15f) + 10f)
        }
        else -> 0f
    }
}

/**
 * A ringing rest alarm, docked exactly where the rest bar was and in its amber: the overtime counting up in the
 * countdown's numerals, and one ink Stop pill like the bar's Skip.
 */
@Composable
internal fun RestAlarmBar(since: Long, clock: () -> Long, onStop: () -> Unit) {
    var now by remember { mutableLongStateOf(clock()) }
    LaunchedEffect(since) {
        while (true) {
            now = clock()
            delay(1000 - (now - since).mod(1000L))
        }
    }
    // Counted from the first second, so it never reads "+0:00" as if nothing had happened.
    val over = ((now - since).coerceAtLeast(0) / 1000).toInt().coerceAtLeast(1)
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.primaryContainer, // fill: softened on dark
        contentColor = colors.onPrimaryContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.fillMaxWidth().testTag("rest-end-alarm")
    ) { InkPress { // on the amber sheet, presses are ink
        Row(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).clearAndSetSemantics {
                contentDescription = "Rest over, alarm ringing"
                liveRegion = LiveRegionMode.Assertive
            }) {
                Text("Rest over", style = overlineStyle())
                Text("+${formatRest(over)}", style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.testTag("rest-end-title"))
            }
            FilledTonalButton(
                onClick = onStop,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = colors.onPrimaryContainer, contentColor = colors.primaryContainer),
                contentPadding = PaddingValues(horizontal = 24.dp),
                modifier = Modifier.heightIn(min = 48.dp).testTag("rest-end-stop")
                    .semantics { contentDescription = "Stop alarm" }
            ) { Text("Stop", maxLines = 1) }
        }
    } }
}

/** Keeps the latest [RestEnd] for [RestEndOverlay]; a preview from settings uses the same path. */
internal class RestEndState {
    var current by mutableStateOf<RestEnd?>(null)
}
