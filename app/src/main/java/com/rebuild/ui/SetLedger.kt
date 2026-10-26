package com.rebuild.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rebuild.R
import com.rebuild.domain.Exercise
import com.rebuild.domain.LoadUnit
import com.rebuild.domain.LoggedSet
import com.rebuild.domain.WorkEntry
import com.rebuild.domain.WorkoutExercise
import com.rebuild.domain.openGroup
import com.rebuild.domain.workEntries
import com.rebuild.domain.workingCount

private enum class LedgerState { LOGGED, NEXT, PLANNED, PREVIOUS_ONLY }

/**
 * One working-set ordinal. [previous] is only ever a same-unit working entry from the last comparable
 * workout, paired in the same order the entry prefill uses; today's cell is either a saved entry or an
 * explicit not-logged placeholder, never a projected value. A drop or rest-pause set is one entry.
 */
private data class LedgerRow(
    val ordinal: Int,
    val today: WorkEntry?,
    val previous: WorkEntry?,
    val previousOrdinal: Int,
    val state: LedgerState,
    val otherUnit: Boolean = false
)

internal fun loadRepsText(set: LoggedSet, exercise: Exercise): String {
    return "${loadText(set.amount, set.loadUnit, set.plates, exercise)} × ${set.reps}"
}

internal fun rirShort(set: LoggedSet): String = set.rir?.let { "RIR ${formatKg(it)}" } ?: "RIR —"

private fun previousDescription(ordinal: Int, entry: WorkEntry, exercise: Exercise): String = when (entry) {
    is WorkEntry.Single -> "Previous set $ordinal: ${setText(entry.set, exercise).spokenUnits()}" +
        if (entry.set.rir == null) ". RIR not recorded." else ""
    is WorkEntry.Group -> groupDescription("Previous set $ordinal", entry, exercise)
}

/** Returns the visible rows and how many older comparable entries stay folded away. */
private fun ledgerRows(we: WorkoutExercise, previous: List<WorkEntry>, expanded: Boolean): Pair<List<LedgerRow>, Int> {
    val entries = we.workEntries()
    val open = we.openGroup() != null
    val rows = mutableListOf<LedgerRow>()
    // Same pairing as the entry prefill: the n-th entry logged in the entry unit meets last time's n-th entry.
    var paired = 0
    entries.forEachIndexed { index, entry ->
        val comparable = entry.unit == we.entryLoadUnit
        val previousEntry = if (comparable) previous.getOrNull(paired) else null
        rows += LedgerRow(index + 1, entry, previousEntry, if (previousEntry != null) paired + 1 else 0,
            LedgerState.LOGGED, otherUnit = !comparable)
        if (comparable) paired++
    }
    // Rows still to log toward the target or goal; without either, just the next set (none while one is open).
    val remaining = we.targetSets?.let { (it - entries.size).coerceAtLeast(0) } ?: if (open) 0 else 1
    repeat(remaining) { k ->
        val previousEntry = previous.getOrNull(paired)
        rows += LedgerRow(entries.size + k + 1, null, previousEntry, if (previousEntry != null) paired + 1 else 0,
            // While a set is open, its own row is the one in progress; the rest are only planned. A skipped
            // exercise has no next set until the skip is undone.
            if (k == 0 && !open && !we.skipped) LedgerState.NEXT else LedgerState.PLANNED)
        paired++
    }
    val folded = previous.drop(paired)
    if (expanded) folded.forEachIndexed { k, entry ->
        rows += LedgerRow(rows.size + 1, null, entry, paired + k + 1, LedgerState.PREVIOUS_ONLY)
    }
    return rows to folded.size
}

internal fun Modifier.dashedOutline(color: Color) = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(8.dp.toPx()),
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
    )
}

/**
 * The exercise's working-set ledger: the count against today's target, today's warm-ups, and each working
 * entry beside the same-unit entry from the last comparable workout. Only saved sets are editable; a drop or
 * rest-pause set opens as one set with its stages.
 */
@Composable
internal fun SetLedger(
    we: WorkoutExercise,
    previous: WorkoutExercise?,
    previousStartedAt: Long?,
    modifier: Modifier = Modifier,
    onEdit: (LoggedSet) -> Unit = {},
    onGroup: (String) -> Unit = {}
) {
    val exercise = we.exercise
    val done = we.workingCount()
    val inProgress = we.openGroup() != null
    val warmups = we.sets.filter { it.warmup }
    val previousEntries = previous?.workEntries()?.filter { it.unit == we.entryLoadUnit }.orEmpty()
    var expanded by rememberSaveable(exercise.identity(), previousStartedAt, we.entryLoadUnit) { mutableStateOf(false) }
    val (rows, folded) = ledgerRows(we, previousEntries, expanded)
    val hasPrevious = previousEntries.isNotEmpty()
    val stacked = LocalDensity.current.fontScale > 1.3f

    // A logged set grows the ledger instead of jumping it.
    Column(modifier.fillMaxWidth().animateContentSize(tween(200)), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Working sets: $done" + (we.targetSets?.let { " of $it" } ?: "") +
                (if (inProgress) " · 1 in progress" else "") +
                if (warmups.isNotEmpty()) " · ${countLabel(warmups.size, "warm-up")}" else "",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.testTag("working-count")
        )
        if (we.upperSets != null && done > we.upperSets) {
            Text(
                "Above the advisory upper of ${we.upperSets}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Column(
            Modifier.fillMaxWidth().then(if (hasPrevious) Modifier.testTag("previous-comparable") else Modifier),
            verticalArrangement = Arrangement.spacedBy(if (stacked) 0.dp else 4.dp)
        ) {
            LedgerHeader(hasPrevious, previousStartedAt, stacked)
            warmups.forEach { set ->
                if (stacked) StackedRow("Warm-up", null, null, false) { TodayCell(set, "Warm-up", false, exercise, true, onEdit) }
                else TableRow("W", highlighted = false, hasPrevious = hasPrevious, previous = { Spacer(it) }) {
                    TodayCell(set, "Warm-up", false, exercise, false, onEdit, it)
                }
            }
            rows.forEach { row ->
                val extra = we.targetSets?.let { row.ordinal > it } ?: false
                val current = row.state == LedgerState.NEXT ||
                    (row.today as? WorkEntry.Group)?.open == true
                if (stacked) {
                    StackedRow("Set ${row.ordinal}", row.previous?.let { row.previousOrdinal to it }, exercise,
                        row.otherUnit && hasPrevious) {
                        RowToday(row, exercise, extra, stacked = true, onEdit = onEdit, onGroup = onGroup)
                    }
                } else {
                    TableRow(row.ordinal.toString(), current, hasPrevious,
                        previous = { PreviousCell(row, exercise, it) }) {
                        RowToday(row, exercise, extra, stacked = false, onEdit = onEdit, onGroup = onGroup, modifier = it)
                    }
                }
            }
        }
        if (folded > 0 || expanded) {
            SecondaryTextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("previous-expand")) {
                Text(if (expanded) "Show fewer sets from last time" else "Show all ${previousEntries.size} sets from last time")
            }
        }
    }
}

@Composable
private fun LedgerHeader(hasPrevious: Boolean, startedAt: Long?, stacked: Boolean) {
    val label = MaterialTheme.typography.labelMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    if (stacked) {
        if (hasPrevious && startedAt != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Last time", style = label, color = color)
            Text(formatDate(startedAt), style = label, color = color, modifier = Modifier.testTag("previous-date"))
        }
        return
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Set", style = label, color = color, modifier = Modifier.width(28.dp))
        if (hasPrevious) Column(Modifier.weight(1f)) {
            Text("Last time", style = label, color = color)
            if (startedAt != null) Text(formatDate(startedAt), style = MaterialTheme.typography.labelSmall, color = color,
                modifier = Modifier.testTag("previous-date"))
        }
        Text("Today", style = label, color = color, modifier = Modifier.weight(1f).padding(start = 8.dp))
    }
}

@Composable
private fun TableRow(
    ordinal: String,
    highlighted: Boolean,
    hasPrevious: Boolean,
    previous: @Composable RowScope.(Modifier) -> Unit,
    today: @Composable RowScope.(Modifier) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(8.dp))
            .background(if (highlighted) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            ordinal,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.width(28.dp).clearAndSetSemantics {}
        )
        if (hasPrevious) previous(Modifier.weight(1f))
        today(Modifier.weight(1f))
    }
}

@Composable
private fun PreviousCell(row: LedgerRow, exercise: Exercise, modifier: Modifier) {
    val entry = row.previous
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when {
        entry != null -> Column(modifier.testTag("previous-set-${row.previousOrdinal}").clearAndSetSemantics {
            contentDescription = previousDescription(row.previousOrdinal, entry, exercise)
        }) {
            when (entry) {
                is WorkEntry.Single -> {
                    Text(loadRepsText(entry.set, exercise),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = muted)
                    Text(rirShort(entry.set), style = MaterialTheme.typography.bodySmall, color = muted)
                }
                is WorkEntry.Group -> SetWithFollowUps(entry, exercise, muted = true)
            }
        }
        row.otherUnit -> Text("Other unit · not compared", style = MaterialTheme.typography.bodySmall, color = muted,
            modifier = modifier)
        else -> Text("—", style = MaterialTheme.typography.bodyMedium, color = muted, modifier = modifier.clearAndSetSemantics {})
    }
}

@Composable
private fun RowToday(row: LedgerRow, exercise: Exercise, extra: Boolean, stacked: Boolean,
    onEdit: (LoggedSet) -> Unit, onGroup: (String) -> Unit, modifier: Modifier = Modifier) {
    when (val entry = row.today) {
        is WorkEntry.Single -> TodayCell(entry.set, "Set ${row.ordinal}", extra, exercise, stacked, onEdit, modifier)
        is WorkEntry.Group -> GroupRow(entry, "Set ${row.ordinal}", exercise, extra, onOpen = { onGroup(entry.group.id) },
            modifier = modifier.then(if (stacked) Modifier.fillMaxWidth() else Modifier))
        null -> if (row.state == LedgerState.NEXT || row.state == LedgerState.PLANNED) {
            val next = row.state == LedgerState.NEXT
            val color = if (next) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            Box(
                modifier.heightIn(min = 44.dp).then(if (stacked) Modifier.fillMaxWidth() else Modifier)
                    .dashedOutline(color)
                    .clearAndSetSemantics {
                        contentDescription = "Set ${row.ordinal}: ${if (next) "up next" else "planned"}, not logged yet"
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                // The dashed outline already says nothing is logged; the label only says which row is next.
                if (next) Text("Up next", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                else Text("Not logged", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text("—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier.clearAndSetSemantics {})
        }
    }
}

@Composable
private fun TodayCell(set: LoggedSet, label: String, extra: Boolean, exercise: Exercise, stacked: Boolean,
    onEdit: (LoggedSet) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.heightIn(min = 48.dp).then(if (stacked) Modifier.fillMaxWidth() else Modifier)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = "Edit $label") { onEdit(set) }
            .semantics(mergeDescendants = true) { contentDescription = "$label: ${setText(set, exercise).spokenUnits()}" }
            .testTag("set-row")
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (stacked) "Today: ${loadRepsText(set, exercise)}" else loadRepsText(set, exercise),
                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = if (set.warmup) FontWeight.Normal else FontWeight.SemiBold
            )
            Text(
                rirShort(set) + if (set.warmup) " · warm-up" else if (extra) " · extra" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RebuildIcon(R.drawable.ic_pencil, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp))
    }
}

/** Large text: each ordinal becomes a labelled block so values wrap instead of clipping in narrow columns. */
@Composable
private fun StackedRow(
    title: String,
    previous: Pair<Int, WorkEntry>?,
    exercise: Exercise?,
    otherUnit: Boolean,
    today: @Composable () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clearAndSetSemantics {})
        if (previous != null && exercise != null) {
            val (ordinal, entry) = previous
            Column(Modifier.fillMaxWidth().testTag("previous-set-$ordinal")
                .clearAndSetSemantics { contentDescription = previousDescription(ordinal, entry, exercise) }) {
                when (entry) {
                    is WorkEntry.Single -> Text(
                        "Last time: ${loadRepsText(entry.set, exercise)} · ${rirShort(entry.set)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    is WorkEntry.Group -> {
                        Text("Last time:", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SetWithFollowUps(entry, exercise, muted = true)
                    }
                }
            }
        } else if (otherUnit) {
            Text("Other unit · not compared", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        today()
    }
}
