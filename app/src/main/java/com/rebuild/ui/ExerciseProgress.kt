package com.rebuild.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rebuild.domain.Exercise
import com.rebuild.domain.LiftedSet
import com.rebuild.domain.LoadUnit
import com.rebuild.domain.LoggedSet
import com.rebuild.domain.RepComparison
import com.rebuild.domain.Workout
import com.rebuild.domain.localDateOf
import com.rebuild.domain.repComparisons
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.floor

/** The day a workout happened, as its week counts it. */
internal fun workoutDate(workout: Workout): LocalDate = workout.startDate?.let(LocalDate::parse) ?: localDateOf(workout.startedAt)

/** A session on the top set chart: its heaviest working set, and the session's other sets as faint marks. */
internal data class TopSetPoint(val date: LocalDate, val top: LoggedSet, val others: List<Double>) {
    val load: Double get() = top.loadValue!!
}

/** The heaviest set each session (more reps break a tie), in the unit the sessions were already filtered to. */
internal fun topSetPoints(sessions: List<HistorySession>): List<TopSetPoint> = sessions.mapNotNull { s ->
    val loaded = s.sets.filter { it.loadValue != null }
    val top = loaded.maxWithOrNull(compareBy<LoggedSet> { it.loadValue!! }.thenBy { it.reps }) ?: return@mapNotNull null
    TopSetPoint(workoutDate(s.workout), top, (loaded - top).map { it.loadValue!! })
}

/** Lines break where sessions are more than two weeks apart: nothing is drawn across a gap. */
internal const val TOP_SET_GAP_DAYS = 14L

internal fun topSetSegments(points: List<TopSetPoint>): List<List<TopSetPoint>> {
    val segments = mutableListOf<MutableList<TopSetPoint>>()
    points.forEach { p ->
        val last = segments.lastOrNull()?.last()
        if (last == null || ChronoUnit.DAYS.between(last.date, p.date) > TOP_SET_GAP_DAYS) segments += mutableListOf(p)
        else segments.last() += p
    }
    return segments
}

/**
 * Axis values a person could read off a scale around [min]..[max]. A single value gets room around it, and plates
 * (whole numbers only) never get a half-plate tick.
 */
internal fun loadTicks(min: Double, max: Double, unit: LoadUnit): List<Double> {
    val whole = unit == LoadUnit.PLATES
    val pad = if (whole) 1.0 else 2.5
    val (low, high) = if (max <= min) (min - pad).coerceAtLeast(0.0) to max + pad else min to max
    val ticks = niceTicks(low, high)
    if (!whole || ticks.all { it == floor(it) }) return ticks
    return niceTicks(floor(low), kotlin.math.ceil(high).coerceAtLeast(floor(low) + 2))
        .takeIf { t -> t.all { it == floor(it) } } ?: listOf(floor(low), kotlin.math.ceil(high))
}

/** "kg", "lb" or "plates" over the load axis. */
private fun axisUnit(unit: LoadUnit) = if (unit == LoadUnit.PLATES) "plates" else unit.massSymbol()

/** A load change in its unit, sign spelled out: "+6.25 kg", "+1 plate". */
internal fun gainText(gain: Double, unit: LoadUnit): String =
    if (unit == LoadUnit.PLATES) "+" + countLabel(gain.toInt(), "plate") else "+${formatKg(gain)} ${unit.massSymbol()}"

private val dayMonth: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

/** "10 Nov", with the year only when it isn't [today]'s. */
internal fun readoutDay(date: LocalDate, today: LocalDate): String =
    if (date.year == today.year) dayMonth.format(date) else dayMonthYear(date)

/** The load axis: tick values from top to bottom, each centred on its gridline. */
@Composable
private fun LoadAxis(ticks: List<Double>, height: Dp, format: (Double) -> String) {
    Column(
        Modifier.height(height).width(44.dp).padding(end = 8.dp).clearAndSetSemantics { hideFromAccessibility() },
        verticalArrangement = if (ticks.size > 1) Arrangement.SpaceBetween else Arrangement.Center,
        horizontalAlignment = Alignment.End
    ) {
        val style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
        ticks.asReversed().forEach { Text(format(it), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
    }
}

@Composable
private fun AxisUnit(unit: LoadUnit) {
    Text(axisUnit(unit), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End, modifier = Modifier.width(36.dp).clearAndSetSemantics { hideFromAccessibility() })
}

@Composable
private fun chartHeight(): Dp = 168.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.25f)

private fun tickText(value: Double, unit: LoadUnit) = if (unit == LoadUnit.PLATES) value.toInt().toString() else formatKg(value)

/**
 * Each session's top set: the heaviest working set that day, placed on its date, with a straight line between
 * sessions that breaks across a gap of more than two weeks. The session's other sets are faint dots. The readout
 * says the set and its reps ("42.5 kg/DB × 10 · 20 Oct 2026"): the latest until a point is touched.
 */
@Composable
internal fun TopSetChart(sessions: List<HistorySession>, unit: LoadUnit, exercise: Exercise, modifier: Modifier = Modifier) {
    val points = remember(sessions) { topSetPoints(sessions) }
    if (points.isEmpty()) return
    val segments = remember(points) { topSetSegments(points) }
    val values = points.flatMap { it.others + it.load }
    val ticks = loadTicks(values.min(), values.max(), unit)
    val low = ticks.first()
    val span = (ticks.last() - low).takeIf { it > 0 } ?: 1.0
    val from = points.first().date
    val to = points.last().date
    val days = ChronoUnit.DAYS.between(from, to)
    val colors = MaterialTheme.colorScheme
    val line = accentText()
    val card = colors.surfaceContainerLowest
    val height = chartHeight()
    val density = LocalDensity.current
    val axisStyle = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
    val inset = with(density) { axisStyle.lineHeight.toDp() } / 2
    val selection = rememberChartSelection(points)
    val shown = points[selection.value ?: points.lastIndex]
    val value = "${chartLoadText(shown.load, unit, exercise)} × ${shown.top.reps}"
    val detail = "${if (selection.value == null) "Latest" else "Selected"} · ${dayMonthYear(shown.date)}"
    fun fraction(d: LocalDate) = if (days > 0) ChronoUnit.DAYS.between(from, d).toFloat() / days else 0.5f
    val xs = remember(points, inset) {
        { width: Int ->
            val pad = with(density) { inset.toPx() }
            points.map { pad + (width - pad * 2) * fraction(it.date) }
        }
    }
    val title = loadChartTitle(exercise, unit).substringAfter(" · ")
    val describe = ("Top set chart, $title: ${countLabel(points.size, "session")}, from " +
        "${chartLoadText(values.min(), unit, exercise)} to ${chartLoadText(values.max(), unit, exercise)}, " +
        "${dayMonthYear(from)} to ${dayMonthYear(to)}. Each point is that session's heaviest working set." +
        if (segments.size > 1) " The line breaks at ${countLabel(segments.size - 1, "gap")} of more than two weeks." else "").spokenUnits()
    ChartCard(modifier.testTag("top-set-card")) {
        Text("Top set · $title", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        ChartReadout(value, detail, touched = true)
        AxisUnit(unit)
        Row {
            LoadAxis(ticks, height) { tickText(it, unit) }
            Canvas(
                Modifier.weight(1f).height(height)
                    .chartSemantics(describe, points.size, selection.value, "$value, $detail".spokenUnits()) { selection.value = it }
                    .chartTouch(points.size, xs) { selection.value = it }
                    .testTag("top-set-chart")
            ) {
                val pad = inset.toPx()
                val h = size.height - pad * 2
                val w = size.width - pad * 2
                fun x(d: LocalDate) = pad + w * fraction(d)
                fun y(v: Double) = pad + h * (1f - ((v - low) / span).toFloat())
                ticks.forEach { t ->
                    drawLine(colors.outlineVariant.copy(alpha = 0.7f), Offset(0f, y(t)), Offset(size.width, y(t)), strokeWidth = 1.dp.toPx())
                }
                selection.value?.let { i ->
                    val gx = x(points[i].date)
                    drawLine(colors.outline, Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1.dp.toPx())
                }
                points.forEach { p ->
                    p.others.forEach { v -> drawCircle(colors.onSurfaceVariant.copy(alpha = 0.4f), radius = 3.dp.toPx(), center = Offset(x(p.date), y(v))) }
                }
                // Straight segments: sets are discrete, and a curve would invent loads between sessions.
                segments.forEach { segment ->
                    val pts = segment.map { Offset(x(it.date), y(it.load)) }
                    if (pts.size > 1) {
                        val path = Path().apply { moveTo(pts.first().x, pts.first().y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
                        drawPath(path, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    pts.forEach { drawCircle(line, radius = 3.dp.toPx(), center = it) }
                }
                val c = Offset(x(shown.date), y(shown.load))
                drawCircle(card, radius = 7.dp.toPx(), center = c)
                drawCircle(line, radius = 7.dp.toPx(), center = c, style = Stroke(width = 2.5.dp.toPx()))
            }
        }
        DateAxis(from, to, days)
    }
}

/** Start, middle and end dates under a date-spaced chart, so a week in between can be read off it. */
@Composable
private fun DateAxis(from: LocalDate, to: LocalDate, days: Long) {
    val style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(start = 44.dp)
        .clearAndSetSemantics { contentDescription = "From ${dayMonthYear(from)} to ${dayMonthYear(to)}" },
        horizontalArrangement = Arrangement.SpaceBetween) {
        // Large text has room for the two ends only; a middle date would run into them.
        val middle = days >= 14 && LocalDensity.current.fontScale <= 1.3f
        Text(dayMonthYear(from), style = style, color = color, maxLines = 1)
        if (middle) Text(dayMonthYear(from.plusDays(days / 2)), style = style, color = color, maxLines = 1)
        if (days > 0) Text(dayMonthYear(to), style = style, color = color, maxLines = 1)
    }
}
