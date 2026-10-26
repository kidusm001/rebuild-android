package com.rebuild.ui

import androidx.compose.foundation.layout.navigationBarsPadding
import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.rebuild.R
import com.rebuild.domain.Action
import com.rebuild.domain.EntrySuggestion
import com.rebuild.domain.PrefillSource
import com.rebuild.domain.SetMethod
import com.rebuild.domain.WorkEntry
import com.rebuild.domain.Workout
import com.rebuild.domain.WorkoutExercise
import com.rebuild.domain.dropTarget
import com.rebuild.domain.openGroup
import com.rebuild.domain.stages
import com.rebuild.domain.workEntries
import kotlin.math.roundToInt

/**
 * The unsaved next set or stage. It is hoisted above the dock/inline placement so moving the entry never loses
 * typed values, and it is keyed by the exercise's saved sets and groups so a commit (or a restore) always starts a
 * fresh draft. [method] is only the type chosen for a new entry; an open group decides its own type.
 */
@Stable
internal class EntryDraft(
    kg: String,
    reps: String,
    rir: String,
    warmup: Boolean,
    pendingSetId: String,
    error: String?,
    extra: Boolean,
    method: SetMethod?,
    pendingGroupId: String,
    drop: Boolean = false
) {
    var kg by mutableStateOf(kg)
    var reps by mutableStateOf(reps)
    var rir by mutableStateOf(rir)
    var warmup by mutableStateOf(warmup)
    /** Stable until this set is saved, so a retry after an error can't create a duplicate. */
    var pendingSetId by mutableStateOf(pendingSetId)
    var error by mutableStateOf(error)
    var extra by mutableStateOf(extra)
    var method by mutableStateOf(method)
    /** Stable until a new drop or rest-pause set is saved, like [pendingSetId]. */
    var pendingGroupId by mutableStateOf(pendingGroupId)
    /** The next entry continues the set before it as a drop instead of starting a new set. */
    var drop by mutableStateOf(drop)
    var validation by mutableStateOf<SetValidation.Invalid?>(null)
    var validationAttempt by mutableIntStateOf(0)

    /** Whether the fields still hold exactly what [suggestion] offered, i.e. nothing was typed over it. */
    fun shows(suggestion: EntrySuggestion?): Boolean =
        kg == suggestion.kgText() && reps == suggestion.repsText()

    fun fill(suggestion: EntrySuggestion?) {
        kg = suggestion.kgText()
        reps = suggestion.repsText()
    }

    companion object {
        private fun EntrySuggestion?.kgText() = this?.set?.loadValue?.let(::formatKg) ?: ""
        private fun EntrySuggestion?.repsText() = this?.takeIf { it.withReps }?.set?.reps?.toString() ?: ""

        /** Suggestions only fill the fields; nothing is saved without an explicit Log tap. */
        fun fresh(suggestion: EntrySuggestion?) = EntryDraft(
            kg = suggestion.kgText(),
            reps = suggestion.repsText(),
            rir = "",
            warmup = false,
            pendingSetId = newId(),
            error = null,
            extra = false,
            method = null,
            pendingGroupId = newId()
        )

        val Saver: Saver<EntryDraft, Any> = listSaver(
            save = {
                listOf(it.kg, it.reps, it.rir, it.warmup, it.pendingSetId, it.error ?: "", it.extra,
                    it.method?.name ?: "", it.pendingGroupId, it.drop)
            },
            restore = {
                EntryDraft(it[0] as String, it[1] as String, it[2] as String, it[3] as Boolean, it[4] as String,
                    (it[5] as String).ifEmpty { null }, it[6] as Boolean,
                    (it.getOrNull(7) as? String)?.let { name -> SetMethod.entries.find { m -> m.name == name } },
                    it.getOrNull(8) as? String ?: newId(), it.getOrNull(9) as? Boolean ?: false)
            }
        )
    }
}

/**
 * Whether the next fresh draft keeps adding drops. Every save starts a fresh draft, and a drop is usually followed by
 * another, so after a drop the entry stays on drops (and, past the target, stays open as an extra) until a set is
 * logged, another type is chosen or the drops are cancelled. Without this, a second drop was logged as a new set.
 */
internal enum class DropCarry { NONE, DROP, DROP_PAST_TARGET }

/**
 * The draft for [draftKey]. The key is folded into the saved-state key, so a draft saved before a background
 * commit is never restored over newer state after process recreation (rememberSaveable inputs alone aren't
 * checked on restore).
 */
@Composable
internal fun rememberEntryDraft(draftKey: String, suggestion: EntrySuggestion?, carry: DropCarry = DropCarry.NONE): EntryDraft =
    confinedKey(draftKey) {
        rememberSaveable(saver = EntryDraft.Saver) {
            EntryDraft.fresh(suggestion).apply {
                if (carry != DropCarry.NONE) {
                    drop = true
                    extra = carry == DropCarry.DROP_PAST_TARGET
                    // The next drop's load and reps are whatever was done, never a guess.
                    kg = ""
                    reps = ""
                }
            }
        }
    }

/**
 * key() wrapped in a constant outer group. With Compose 1.8.0, a changed key() placed directly in a composable's
 * group and followed there by a changed remember left later content lambdas un-invalidated (seen as a workout
 * showing the previous exercise), so every changing key gets a group of its own.
 */
@Composable
internal inline fun <T> confinedKey(value: Any?, content: @Composable () -> T): T =
    key("confined-key") { key(value) { content() } }

/** Where the fields' values came from; a normal set offered for a drop or rest-pause stage says so. */
internal fun prefillText(suggestion: EntrySuggestion?, method: SetMethod?): String? {
    suggestion ?: return null
    val normal = method != null && suggestion.set.groupId == null
    return when {
        suggestion.source == PrefillSource.LAST_TIME -> if (normal) "prefilled from last time's normal set" else "prefilled from last time"
        suggestion.withReps -> if (normal) "prefilled from today's last normal set" else "prefilled from this workout"
        else -> "load from the stage before"
    }
}

/**
 * Fields, warm-up toggle, set type and Log for the next set. While a drop or rest-pause set is open the same
 * fields log its next stage, and Finish set closes it. [offered] is what the fields were prefilled with;
 * [suggestionFor] gives the prefill for another set type. [viewportHeight] is the scroll viewport that hosts it.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun EntryPanel(
    env: AppEnv,
    workout: Workout,
    we: WorkoutExercise,
    draft: EntryDraft,
    offered: EntrySuggestion?,
    suggestionFor: (SetMethod?) -> EntrySuggestion?,
    viewportHeight: Int,
    onFocusChange: (Boolean) -> Unit,
    /** What the next fresh draft starts as, set just before each save (see [DropCarry]). */
    onCarry: (DropCarry) -> Unit = {}
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val large = LocalDensity.current.fontScale > 1.3f
    val open = we.openGroup()
    val stagesLogged = open?.let { we.stages(it.id).size } ?: 0
    val entries = we.workEntries()
    val dropTarget = we.dropTarget()
    val dropping = draft.drop && open == null && dropTarget != null
    val ordinal = when {
        open != null -> entries.indexOfFirst { it is WorkEntry.Group && it.group.id == open.id } + 1
        dropping -> entries.indexOf(dropTarget) + 1
        else -> entries.size + 1
    }
    val method = if (dropping) null else open?.method ?: draft.method?.takeUnless { draft.warmup }
    // Beyond a target or optional goal; without either, no set is extra. A drop continues a set already counted.
    val extra = !dropping && (we.targetSets?.let { ordinal > it } ?: false)
    var entryFocused by remember { mutableStateOf(false) }
    var confirmingFinish by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val entryBounds = remember { BringIntoViewRequester() }
    // Relocate after the hosting viewport is measured; IME insets read in composition can precede the resize.
    LaunchedEffect(entryFocused, viewportHeight) {
        if (entryFocused && viewportHeight > 0 && draft.validation == null) entryBounds.bringIntoView()
    }
    DisposableEffect(Unit) { onDispose { onFocusChange(false) } }

    fun chooseMethod(choice: SetMethod?) {
        // Offered values follow the type only while nothing was typed over them; typed values are kept.
        if (draft.shows(suggestionFor(if (draft.warmup) null else draft.method))) draft.fill(suggestionFor(choice))
        draft.method = choice
        // Any type chosen from the menu, Normal included, ends "Add drop": the next entry is a set again.
        draft.drop = false
        onCarry(DropCarry.NONE)
        if (choice != null) draft.warmup = false
    }

    fun log() {
        if (env.runner.busy) return
        val parsed = validateSet(we.exercise, draft.kg, draft.reps, draft.rir, we.entryLoadUnit)
        val input = when (parsed) {
            is SetValidation.Invalid -> {
                draft.validation = parsed
                draft.validationAttempt++
                return
            }
            is SetValidation.Valid -> parsed.input
        }
        draft.validation = null
        draft.error = null
        // A committed id must never be reused for a new set: that would overwrite the logged one.
        if (workout.exercises.any { e -> e.sets.any { it.id == draft.pendingSetId } }) draft.pendingSetId = newId()
        val action = if (dropping) {
            if (workout.exercises.any { e -> e.setGroups.any { it.id == draft.pendingGroupId } }) {
                draft.pendingGroupId = newId()
            }
            Action.LogDrop(workout.id, we.id, draft.pendingGroupId, draft.pendingSetId, input.kg, input.reps, input.rir,
                env.clock(), we.entryLoadUnit, input.plates)
        } else if (method == null) {
            Action.SaveSet(workout.id, we.id, draft.pendingSetId, input.kg, input.reps, input.rir, draft.warmup,
                env.clock(), loadUnit = we.entryLoadUnit, plates = input.plates)
        } else {
            if (open == null && workout.exercises.any { e -> e.setGroups.any { it.id == draft.pendingGroupId } }) {
                draft.pendingGroupId = newId()
            }
            Action.LogStage(workout.id, we.id, open?.id ?: draft.pendingGroupId, method, draft.pendingSetId,
                input.kg, input.reps, input.rir, env.clock(), we.entryLoadUnit, input.plates)
        }
        // Decided before the save: the save itself replaces this draft with a fresh one.
        onCarry(if (!dropping) DropCarry.NONE else if (draft.extra) DropCarry.DROP_PAST_TARGET else DropCarry.DROP)
        env.runner.run(action, onError = { draft.error = it }, cooldownMs = ActionRunner.NEW_SET_COOLDOWN_MS) {
            // A short tick confirms the set is saved without looking at the screen.
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }

    fun finishGroup() {
        val group = open ?: return
        confirmingFinish = false
        draft.error = null
        env.runner.run(Action.FinishGroup(workout.id, we.id, group.id, env.clock()), onError = { draft.error = it })
    }

    @Composable fun Fields(modifier: Modifier) {
        Column(modifier) {
            SetFields(we.exercise, draft.kg, { draft.kg = it }, draft.reps, { draft.reps = it }, draft.rir,
                { draft.rir = it }, ::log, "entry", draft.validation, draft.validationAttempt, showRirHint = false,
                unit = we.entryLoadUnit, stackOnError = false)
        }
    }

    @Composable fun LogButton(modifier: Modifier) {
        val logModifier = modifier.heightIn(min = 56.dp).testTag("log-set")
        val label = when {
            env.runner.saving -> "Saving…"
            open != null -> "Add ${open.method.stageNoun}"
            dropping -> "Log drop"
            method != null -> "Log stage 1"
            draft.warmup -> "Log warm-up"
            else -> "Log set"
        }
        // Extra sets stay available but are never styled as the expected next step; an open set's next stage is.
        if (extra && open == null) {
            OutlinedButton(onClick = ::log, enabled = !env.runner.busy, modifier = logModifier) { Text(label, maxLines = 1) }
        } else {
            Button(onClick = ::log, enabled = !env.runner.busy, modifier = logModifier) { Text(label, maxLines = 1) }
        }
    }

    @Composable fun FinishSetButton(modifier: Modifier) {
        OutlinedButton(
            onClick = {
                // Typed values that were never added as a stage are not silently dropped.
                if (draft.reps.isNotBlank() && !draft.shows(offered)) confirmingFinish = true else finishGroup()
            },
            enabled = !env.runner.busy,
            modifier = modifier.heightIn(min = 56.dp).testTag("finish-group")
        ) { Text("Finish set", maxLines = 1) }
    }

    @Composable fun ShortPause() {
        SecondaryTextButton(
            onClick = { env.runner.run(Action.StartShortPause(workout.id, we.id, SHORT_PAUSE_SECONDS, env.clock()),
                onError = { draft.error = it }) },
            enabled = !env.runner.busy,
            icon = R.drawable.ic_rest,
            modifier = Modifier.testTag("short-pause")
        ) { Text("Start ${SHORT_PAUSE_SECONDS} s pause") }
    }

    val warmupChip = @Composable { modifier: Modifier ->
        WarmupChip(draft.warmup, {
            draft.warmup = !draft.warmup
            if (draft.warmup) {
                draft.method = null
                draft.drop = false
                onCarry(DropCarry.NONE)
            }
        }, modifier)
    }
    val showWarmup = open == null && method == null
    val typeButton = @Composable {
        if (open == null) SetTypeButton(
            method, enabled = !env.runner.busy, onSelect = ::chooseMethod, named = landscape,
            dropOnto = dropTarget?.let { entries.indexOf(it) + 1 },
            dropping = dropping,
            onDrop = {
                draft.drop = true
                draft.warmup = false
                draft.method = null
                // The first drop's fields wait for what was done too.
                draft.kg = ""
                draft.reps = ""
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!landscape) EntryContext(we, ordinal, extra, draft, open?.method ?: method, open != null, stagesLogged,
            offered, dropping, onCancel = { onCarry(DropCarry.NONE) }, typeButton = typeButton)
        if (large && !landscape && showWarmup) warmupChip(Modifier)
        // Kept in view while typing: the fields and Log only. Everything after it is a scroll away on a short window.
        Column(
            Modifier
                .onFocusChanged {
                    entryFocused = it.hasFocus
                    onFocusChange(it.hasFocus)
                }
                .focusGroup()
                .bringIntoViewRequester(entryBounds),
            verticalArrangement = Arrangement.spacedBy(if (landscape) 4.dp else 8.dp)
        ) {
            if (landscape) {
                Text(
                    "${we.exercise.name} · ${loadingDescription(we.exercise, we.entryLoadUnit)} · " + when {
                        draft.warmup -> "warm-up"
                        dropping -> "drop on set $ordinal"
                        open != null -> "${open.method.title.lowercase()} set $ordinal, stage ${stagesLogged + 1}"
                        method != null -> "${method.title.lowercase()} set $ordinal, stage 1"
                        extra -> "extra set $ordinal"
                        else -> "set $ordinal"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().testTag("compact-entry-context")
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!large && showWarmup) warmupChip(Modifier)
                    Fields(Modifier.weight(3f))
                    LogButton(Modifier.weight(1f))
                }
            } else {
                Fields(Modifier.fillMaxWidth())
                when {
                    large -> LogButton(Modifier.fillMaxWidth())
                    open != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FinishSetButton(Modifier)
                        LogButton(Modifier.weight(1f))
                    }
                    method != null -> LogButton(Modifier.fillMaxWidth())
                    else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        warmupChip(Modifier)
                        LogButton(Modifier.weight(1f))
                    }
                }
            }
            ErrorText(draft.error)
        }
        if (landscape) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                typeButton()
                if (open != null) FinishSetButton(Modifier)
                if (open?.method == SetMethod.REST_PAUSE) ShortPause()
                if (large && showWarmup) warmupChip(Modifier)
                if ((extra || (dropping && draft.extra)) && open == null) {
                    TextButton(onClick = { draft.extra = false; draft.drop = false; onCarry(DropCarry.NONE) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("cancel-extra")) { Text("Cancel") }
                }
            }
        } else {
            if (large && open != null) FinishSetButton(Modifier.fillMaxWidth())
            if (open?.method == SetMethod.REST_PAUSE) ShortPause()
        }
    }

    if (confirmingFinish && open != null) {
        ConfirmDialog(
            title = "Finish without these values?",
            text = "The values in the fields aren't logged. Finish the ${open.method.title.lowercase()} with its " +
                "${countLabel(stagesLogged, "logged stage")}, or cancel and tap Add ${open.method.stageNoun} first.",
            confirmLabel = "Finish set",
            busy = env.runner.busy,
            onDismiss = { confirmingFinish = false },
            onConfirm = ::finishGroup
        )
    }
}

internal const val SHORT_PAUSE_SECONDS = 20

@Composable
private fun EntryContext(
    we: WorkoutExercise,
    ordinal: Int,
    extra: Boolean,
    draft: EntryDraft,
    method: SetMethod?,
    open: Boolean,
    stagesLogged: Int,
    offered: EntrySuggestion?,
    dropping: Boolean,
    onCancel: () -> Unit,
    typeButton: @Composable () -> Unit
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val setLabel = when {
        extra -> "Extra set $ordinal"
        we.targetSets == null -> "Set $ordinal"
        else -> "Set $ordinal of ${we.targetSets}"
    }
    val (title, detail) = when {
        draft.warmup -> "Warm-up" to null
        dropping -> "Drop on set $ordinal" to null
        open && method != null -> "$setLabel · ${method.title}" to "${countLabel(stagesLogged, "stage")} logged"
        method != null -> "$setLabel · ${method.title}" to prefillText(offered, method)
        extra -> setLabel to null
        else -> setLabel to prefillText(offered, null)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) {
                    append(title)
                }
                if (detail != null) withStyle(SpanStyle(color = muted)) { append(" · $detail") }
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).testTag("entry-context")
        )
        if ((extra || (dropping && draft.extra)) && !open) {
            TextButton(onClick = { draft.extra = false; draft.drop = false; onCancel() },
                modifier = Modifier.heightIn(min = 48.dp).testTag("cancel-extra")) {
                Text("Cancel")
            }
        }
        typeButton()
    }
}

@Composable
private fun WarmupChip(selected: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) =
    ToggleChip("Warm-up", selected, onToggle, modifier.testTag("entry-warmup"))

@Composable
private fun ToggleChip(label: String, selected: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onToggle,
        label = { Text(label, maxLines = 1) },
        leadingIcon = if (selected) {
            { RebuildIcon(R.drawable.ic_check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else null,
        modifier = modifier.heightIn(min = 48.dp)
    )
}

/** Shown in place of the entry once today's target is logged: the next step is primary, extra sets stay possible. */
@Composable
internal fun TargetMetPanel(
    nextName: String?,
    onNext: (() -> Unit)?,
    onFinish: () -> Unit,
    finishEnabled: Boolean,
    message: String = "Target done",
    extraLabel: String = "Extra set",
    /** "Add drop to set 2" when the last set can take a drop; null hides it. */
    dropLabel: String? = null,
    onDrop: () -> Unit = {},
    onExtra: () -> Unit
) {
    val large = LocalDensity.current.fontScale > 1.3f
    EnterOnce { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RebuildIcon(R.drawable.ic_check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                message,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        @Composable fun Primary(modifier: Modifier) {
            if (onNext != null && nextName != null) {
                Button(onClick = onNext, modifier = modifier.heightIn(min = 56.dp).testTag("next-exercise")) {
                    Text("Next: $nextName", maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Button(onClick = onFinish, enabled = finishEnabled,
                    modifier = modifier.heightIn(min = 56.dp).testTag("finish-from-entry")) {
                    Text("Finish workout")
                }
            }
        }
        @Composable fun Extra(modifier: Modifier) {
            OutlinedButton(onClick = onExtra, modifier = modifier.heightIn(min = 48.dp).testTag("extra-set")) {
                Text(extraLabel, maxLines = 1)
            }
        }
        @Composable fun Drop(modifier: Modifier) {
            if (dropLabel != null) OutlinedButton(onClick = onDrop, modifier = modifier.heightIn(min = 48.dp).testTag("drop-set")) {
                Text(dropLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // The next step is the one wide action; going further on this exercise is a quieter second row.
        Primary(Modifier.fillMaxWidth())
        if (large) {
            Drop(Modifier.fillMaxWidth())
            Extra(Modifier.fillMaxWidth())
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Drop(Modifier.weight(1f))
                Extra(Modifier.weight(1f))
            }
        }
    } }
}

/**
 * Stacks [body] over [dock]. The dock is measured first, capped at [dockShare] of the height, and the body gets
 * exactly the rest. The share is read while measuring, so focus or keyboard changes only move the split.
 */
@Composable
internal fun DockedLayout(
    dockShare: () -> Float,
    modifier: Modifier = Modifier,
    body: @Composable () -> Unit,
    dock: @Composable () -> Unit
) {
    Layout(contents = listOf(body, dock), modifier = modifier) { (bodyMeasurables, dockMeasurables), constraints ->
        val height = constraints.maxHeight
        val cap = (height * dockShare().coerceIn(0f, 1f)).roundToInt()
        val docks = dockMeasurables.map { it.measure(constraints.copy(minHeight = 0, maxHeight = cap)) }
        val dockHeight = docks.maxOfOrNull { it.height } ?: 0
        val bodyHeight = (height - dockHeight).coerceAtLeast(0)
        val bodies = bodyMeasurables.map { it.measure(constraints.copy(minHeight = bodyHeight, maxHeight = bodyHeight)) }
        layout(constraints.maxWidth, height) {
            bodies.forEach { it.place(0, 0) }
            docks.forEach { it.place(0, height - dockHeight) }
        }
    }
}

/**
 * Bottom-pinned logging surface. It scrolls on its own if its content is ever taller than the height it is
 * given, so no control becomes unreachable, and it reports the viewport it gives its content for focus relocation.
 */
@Composable
internal fun LogDock(
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(viewportHeight: Int) -> Unit
) {
    var viewport by remember { mutableIntStateOf(0) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth().testTag("log-dock")
    ) {
        // The dock paints behind the gesture area in its own color; its controls stay above it.
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                Modifier
                    .readableWidth()
                    .onSizeChanged { viewport = it.height }
                    .onFocusChanged { onFocusChange(it.hasFocus) }
                    .focusGroup()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) { content(viewport) }
        }
    }
}
