package com.rebuild.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rebuild.R
import com.rebuild.domain.DistanceUnit
import com.rebuild.domain.Effort
import com.rebuild.domain.Exercise
import com.rebuild.domain.LoadUnit
import com.rebuild.domain.LoadingType
import com.rebuild.domain.Measure
import com.rebuild.domain.Workout
import com.rebuild.domain.WorkoutExercise
import com.rebuild.domain.recordsLoad

/**
 * A finished timed, distance or cardio exercise's efforts in the order logged, styled like the workout ledger: the
 * count against its target or goal, then each effort with what it meant for that target. Every row opens its
 * correction.
 */
@Composable
internal fun HistoryEffortTable(we: WorkoutExercise, onEdit: (Effort) -> Unit) {
    val measure = we.exercise.measure
    Text(effortCountText(we), style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), modifier = Modifier.testTag("effort-count"))
    if (we.efforts.isEmpty()) {
        Text("Nothing logged yet.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column {
        we.efforts.forEachIndexed { index, effort ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val label = effortLabel(measure, index + 1)
            val note = effortNote(we, index)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(onClickLabel = "Edit $label") { onEdit(effort) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = "$label: ${spokenEffort(effort, we.exercise)}" + (note?.let { ", $it" } ?: "")
                    }
                    .testTag("effort-row")
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp).clearAndSetSemantics {}
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        effortText(effort, we.exercise),
                        style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.SemiBold
                    )
                    if (note != null) Text(note, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RebuildIcon(R.drawable.ic_pencil, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** One finished session of a timed, distance or cardio exercise: its efforts in logged order, in one load unit. */
internal data class EffortSession(val workout: Workout, val efforts: List<Effort>)

/** Finished sessions of [identity], oldest first. A [unit] keeps holds and carries in kg apart from those in plates. */
internal fun effortSessions(workouts: List<Workout>, identity: Identity, unit: LoadUnit?): List<EffortSession> =
    workouts.filter { it.finishedAt != null }.sortedBy { it.startedAt }.flatMap { workout ->
        workout.exercises.filter { it.exercise.identity() == identity }
            .map { we -> EffortSession(workout, we.efforts.filter { unit == null || it.loadUnit == unit }) }
    }.filter { it.efforts.isNotEmpty() }

/**
 * One chart's dots: the values each session actually recorded, with the day each session started. Sessions without
 * any are left out, never zero.
 */
internal data class EffortSeries(val sessions: List<List<Double>>, val dates: List<Long>)

internal fun effortSeries(sessions: List<EffortSession>, value: (Effort) -> Double?): EffortSeries? {
    val kept = sessions.map { it to it.efforts.mapNotNull(value) }.filter { it.second.isNotEmpty() }
    if (kept.isEmpty()) return null
    return EffortSeries(kept.map { it.second }, kept.map { it.first.workout.startedAt })
}

/**
 * The charts for a measured exercise, each about one measurement in one unit: time per hold, distance per carry or
 * per cardio effort in each unit it was entered in, and a hold's or carry's load. Nothing is combined or derived, so
 * there is no pace, total or estimate.
 */
internal data class EffortChart(val key: String, val title: String, val series: EffortSeries, val format: (Double) -> String)

internal fun effortCharts(exercise: Exercise, unit: LoadUnit, sessions: List<EffortSession>): List<EffortChart> {
    val measure = exercise.measure
    val per = if (measure == Measure.CARDIO) "effort" else "set"
    val charts = mutableListOf<EffortChart>()
    if (measure == Measure.TIMED || measure == Measure.CARDIO) {
        effortSeries(sessions) { it.durationSeconds?.toDouble() }?.let {
            charts += EffortChart("time", "Time per $per", it) { v -> formatDuration(v.toLong()) }
        }
    }
    if (measure == Measure.DISTANCE || measure == Measure.CARDIO) {
        DistanceUnit.entries.forEach { distanceUnit ->
            effortSeries(sessions) { e -> e.distance?.takeIf { it.unit == distanceUnit }?.thousandths?.toDouble() }?.let {
                charts += EffortChart("distance-${distanceUnit.name}", "Distance per $per · ${distanceUnit.symbol}", it) { v ->
                    "${thousandthsText(v.toLong())} ${distanceUnit.symbol}"
                }
            }
        }
    }
    if (measure.recordsLoad && (exercise.loading != LoadingType.BODYWEIGHT || unit == LoadUnit.PLATES)) {
        effortSeries(sessions) { it.loadValue }?.let { charts += EffortChart("load", loadChartTitle(exercise, unit), it, ::formatKg) }
    }
    return charts
}

/** What the measured history shows, one short fact per line. */
private fun effortHistoryNote(exercise: Exercise): String = when (exercise.measure) {
    Measure.CARDIO -> "Each effort exactly as logged. Nothing unrecorded counts as zero."
    else -> "Each ${effortNoun(exercise.measure)} exactly as logged, including any under a minimum." +
        if (exercise.measure == Measure.DISTANCE) "\nDistances keep the unit they were entered in." else ""
}

/**
 * History of a timed, distance or cardio exercise: the latest session, a chart for each recorded measurement, then
 * every session's efforts as text, which is the charts' equivalent.
 */
@Composable
internal fun MeasuredExerciseHistory(env: AppEnv, identity: Identity, exercise: Exercise, onBack: () -> Unit) {
    val measure = exercise.measure
    val recordedUnits = if (!measure.recordsLoad) emptyList() else env.state.workouts.filter { it.finishedAt != null }
        .flatMap { it.exercises }.filter { it.exercise.identity() == identity }
        .flatMap { it.efforts }.map { it.loadUnit }.distinct()
    var unit by rememberSaveable(identity) { mutableStateOf(recordedUnits.singleOrNull() ?: LoadUnit.KG) }
    val sessions = effortSessions(env.state.workouts, identity, unit.takeIf { measure.recordsLoad })
    ScreenScaffold(title = exercise.name, subtitle = loadingDescription(exercise, unit), onBack = onBack) {
        // Each unit is its own series; the choice appears only when more than one was recorded.
        if (measure.recordsLoad && recordedUnits.size > 1) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                LoadUnitSelector(unit, recordedUnits.sortedBy { it.ordinal }) { unit = it }
            }
        }
        LazyBody {
            item(key = "about") {
                Text(
                    effortHistoryNote(exercise),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("measured-history-note")
                )
            }
            if (sessions.isEmpty()) {
                item(key = "empty") {
                    Text(if (measure == Measure.CARDIO) "No efforts logged yet." else "No ${effortNoun(measure)}s logged yet.")
                }
                return@LazyBody
            }
            item(key = "latest") {
                val latest = sessions.last()
                val since = "${countLabel(sessions.size, "session")} since ${formatDate(sessions.first().workout.startedAt)}"
                // Spoken whole, so a card scrolled nearly out of view still reads as more than its visible edge.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .testTag("measured-latest")
                        .clearAndSetSemantics {
                            contentDescription = "Latest, ${formatDate(latest.workout.startedAt)}: " +
                                "${latest.efforts.joinToString("; ") { spokenEffort(it, exercise) }}. $since"
                        }
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("Latest · ${formatDate(latest.workout.startedAt)}", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    latest.efforts.forEach {
                        Text(effortText(it, exercise), style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Medium)
                    }
                    Text(since, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            effortCharts(exercise, unit, sessions).forEach { chart ->
                item(key = "chart-${chart.key}") {
                    SetChart(chart.title, chart.series.sessions, chart.series.dates, chart.format,
                        modifier = Modifier.testTag("effort-chart-${chart.key}"))
                }
            }
            item(key = "sessions-header") { SectionHeader("All sessions") }
            itemsIndexed(sessions.asReversed()) { i, s ->
                Column {
                    if (i > 0) HorizontalDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .testTag("measured-session")
                            .clearAndSetSemantics {
                                contentDescription = "${formatDate(s.workout.startedAt)}: " +
                                    s.efforts.joinToString("; ") { spokenEffort(it, exercise) }
                            },
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(formatDate(s.workout.startedAt), style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.8f))
                        Column(Modifier.weight(1f)) {
                            s.efforts.forEach {
                                Text(effortText(it, exercise), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
                            }
                        }
                    }
                }
            }
        }
    }
}
