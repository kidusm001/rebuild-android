package com.rebuild.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rebuild.R
import com.rebuild.domain.AppState
import com.rebuild.domain.Exercise
import com.rebuild.domain.LoadUnit
import com.rebuild.domain.LoadingType
import com.rebuild.ui.theme.Motion

/**
 * One-time in-place hints taught the first time each part of Rebuild is met. Each can be dismissed on its own or
 * retires once the action it teaches completes; More › Show tips turns them off or brings them all back once.
 */
enum class Tip(val tag: String) {
    SET_ENTRY("set-entry"),
    REST_BAR("rest-bar"),
    FIRST_RECEIPT("first-receipt"),
    BACKUP_AFTER_THREE("backup-after-three")
}

/**
 * This phone's in-place tip state. Kept out of the workout snapshot: tips explain the interface on this phone rather
 * than recording training. Turning [enabled] back on (or choosing Show tips again) clears [dismissed] and adds each
 * tip to [forceShow] so it shows once more even with history already logged, then retires again once dismissed or
 * completed.
 */
data class TipsState(
    val enabled: Boolean = true,
    val dismissed: Set<Tip> = emptySet(),
    val forceShow: Set<Tip> = emptySet()
) {
    fun shows(tip: Tip): Boolean = enabled && tip !in dismissed
    fun dismiss(tip: Tip): TipsState = copy(dismissed = dismissed + tip, forceShow = forceShow - tip)
    fun setEnabled(on: Boolean): TipsState =
        if (on) reset() else copy(enabled = false)
    fun reset(): TipsState = TipsState(enabled = true, dismissed = emptySet(), forceShow = Tip.entries.toSet())
}

/** First set entry: shown before any set has been logged, or once more when tips are re-enabled. */
internal fun shouldShowSetEntryTip(state: AppState, tips: TipsState): Boolean =
    tips.shows(Tip.SET_ENTRY) && (Tip.SET_ENTRY in tips.forceShow ||
        state.workouts.all { w -> w.exercises.all { it.sets.isEmpty() } })

/** First rest bar: shown on the first workout's rest (not a short pause), or once more when tips are re-enabled. */
internal fun shouldShowRestBarTip(state: AppState, tips: TipsState, shortPause: Boolean): Boolean =
    !shortPause && tips.shows(Tip.REST_BAR) &&
        (Tip.REST_BAR in tips.forceShow || state.workouts.none { it.finishedAt != null })

/** First finished workout: shown on the very first receipt, or once more when tips are re-enabled. */
internal fun shouldShowFirstReceiptTip(state: AppState, tips: TipsState): Boolean =
    tips.shows(Tip.FIRST_RECEIPT) &&
        (Tip.FIRST_RECEIPT in tips.forceShow || state.workouts.count { it.finishedAt != null } == 1)

/**
 * After the third finished workout: suggest a backup on Today once 3 workouts exist and nothing has been backed up yet
 * (or, when re-enabled, if there are unbacked-up changes). Never nags when a current backup already exists.
 */
internal fun shouldShowBackupTip(state: AppState, tips: TipsState): Boolean {
    val finished = state.workouts.count { it.finishedAt != null }
    val unsaved = state.revision > (state.lastBackupRevision ?: -1)
    val needsBackup = state.lastBackupAt == null || (Tip.BACKUP_AFTER_THREE in tips.forceShow && unsaved)
    return tips.shows(Tip.BACKUP_AFTER_THREE) && finished >= 3 && needsBackup
}

/** Explains the entry fields in plain words matching this exercise's load and unit. */
internal fun setEntryTipText(exercise: Exercise, unit: LoadUnit): String = when {
    exercise.loading == LoadingType.BODYWEIGHT ->
        "Log the reps you did and RIR — reps you could still have done. RIR is optional."
    exercise.loading == LoadingType.ASSISTANCE ->
        "Log the assistance (${unit.massSymbol()}), reps done and RIR — reps you could still have done. RIR is optional."
    unit == LoadUnit.PLATES ->
        "Log the plates on the stack, reps done and RIR — reps you could still have done. RIR is optional."
    exercise.perDumbbell ->
        "Log the weight of one dumbbell (${unit.massSymbol()}), reps done and RIR — reps you could still have done. RIR is optional."
    else ->
        "Log the weight (${unit.massSymbol()}), reps done and RIR — reps you could still have done. RIR is optional."
}

/**
 * A calm, dismissible one-time tip on the canvas, sheet or dock. Collapses smoothly when dismissed and never blocks
 * logging or navigation.
 */
@Composable
internal fun TipCard(
    tip: Tip,
    visible: Boolean,
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    AnimatedVisibility(visible = visible, enter = Motion.expand(), exit = Motion.collapse(), modifier = modifier) {
        Surface(
            color = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().testTag("tip-${tip.tag}")
        ) {
            Row(
                Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    Modifier.weight(1f).padding(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
                        Text("Tip", style = overlineStyle(), color = accentText())
                        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (actionLabel != null && onAction != null) {
                        SecondaryButton(
                            onClick = onAction,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("tip-action-${tip.tag}")
                        ) { Text(actionLabel) }
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp).testTag("dismiss-tip-${tip.tag}")
                ) {
                    RebuildIcon(
                        R.drawable.ic_x,
                        contentDescription = "Dismiss tip",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * First-rest tip rendered inside the amber rest sheet above its foot progress line, keeping the sheet's silhouette
 * and ink-on-amber contrast.
 */
@Composable
internal fun RestBarTip(visible: Boolean, onDismiss: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    AnimatedVisibility(visible = visible, enter = Motion.expand(), exit = Motion.collapse()) {
        Row(
            Modifier.fillMaxWidth().testTag("tip-${Tip.REST_BAR.tag}")
                .padding(start = 20.dp, end = 8.dp, top = 0.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                Modifier.weight(1f).semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("Tip", style = overlineStyle(), color = ink)
                Text(
                    "+30 s adds time, Skip moves on, and the options button sets a time. Change alerts in More › Rest alert.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ink
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(48.dp).testTag("dismiss-tip-${Tip.REST_BAR.tag}")
            ) {
                RebuildIcon(R.drawable.ic_x, contentDescription = "Dismiss tip", tint = ink,
                    modifier = Modifier.size(18.dp))
            }
        }
    }
}
