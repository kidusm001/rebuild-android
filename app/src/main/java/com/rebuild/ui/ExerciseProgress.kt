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
        Text(dayMonthYear(from), style = style, color = color)
        if (days >= 14) Text(dayMonthYear(from.plusDays(days / 2)), style = style, color = color)
        if (days > 0) Text(dayMonthYear(to), style = style, color = color)
    }
}

/** What the rep-count chart compares today's bests with. A choice is offered only when there was history by then. */
internal enum class RepCompareFrom(val label: String) {
    WEEKS_4("4 weeks ago"), MONTHS_3("3 months"), FIRST("First session");

    fun date(today: LocalDate, first: LocalDate): LocalDate = when (this) {
        WEEKS_4 -> today.minusWeeks(4)
        MONTHS_3 -> today.minusMonths(3)
        FIRST -> first
    }

    fun available(today: LocalDate, first: LocalDate) = this == FIRST || date(today, first) >= first
}

/** "9 reps: 46.25 kg" and "10 Nov · was 40 kg on 12 Oct (+6.25 kg)": a column read in words, never colored. */
internal fun repCompareReadout(column: RepComparison, asOf: LocalDate, unit: LoadUnit, exercise: Exercise, today: LocalDate):
    Pair<String, String> {
    val value = "${countLabel(column.reps, "rep")}: ${chartLoadText(column.now.load, unit, exercise)}"
    val day = readoutDay(column.now.date, today)
    val then = column.then
    val detail = when {
        then == null -> "$day · new since ${readoutDay(asOf, today)}"
        column.gain <= 0.0 -> "$day · none heavier since"
        else -> "$day · was ${chartLoadText(then.load, unit, exercise)} on ${readoutDay(then.date, today)} (${gainText(column.gain, unit)})"
    }
    return value to detail
}

/**
 * Same reps, then and now: one column for each rep count that has been lifted, with its heaviest set as of a
 * comparison day (a hollow dot) joined to its heaviest set now (a filled dot). A rep count first lifted since then has
 * only the filled dot and says "new". Columns are never joined: 9 reps and 10 reps are different sets.
 */
@Composable
internal fun RepCompareChart(
    sets: List<LiftedSet>,
    unit: LoadUnit,
    exercise: Exercise,
    today: LocalDate,
    modifier: Modifier = Modifier,
    titleSuffix: String = ""
) {
    if (sets.isEmpty()) return
    val first = sets.first().date
    val choices = RepCompareFrom.entries.filter { it.available(today, first) }
    var from by rememberSaveable(unit) { mutableStateOf(if (RepCompareFrom.WEEKS_4 in choices) RepCompareFrom.WEEKS_4 else RepCompareFrom.FIRST) }
    if (from !in choices) from = RepCompareFrom.FIRST
    val asOf = from.date(today, first)
    val columns = remember(sets, asOf) { repComparisons(sets, asOf) }
    val loads = columns.flatMap { listOfNotNull(it.now.load, it.then?.load) }
    val ticks = loadTicks(loads.min(), loads.max(), unit)
    val low = ticks.first()
    val span = (ticks.last() - low).takeIf { it > 0 } ?: 1.0
    val colors = MaterialTheme.colorScheme
    val accent = accentText()
    val faint = colors.onSurfaceVariant
    val card = colors.surfaceContainerLowest
    val height = chartHeight()
    val density = LocalDensity.current
    val axisStyle = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
    val inset = with(density) { axisStyle.lineHeight.toDp() } / 2
    val selection = rememberChartSelection(columns)
    // Until a column is touched, the one whose best set is the most recent.
    val latest = columns.indices.maxWith(compareBy<Int> { columns[it].now.date }.thenBy { columns[it].reps })
    val shownAt = selection.value ?: latest
    val (value, detail) = repCompareReadout(columns[shownAt], asOf, unit, exercise, today)
    val xs = remember(columns.size) { { width: Int -> List(columns.size) { (it + 0.5f) * width / columns.size } } }
    val describe = ("Same reps, then and now chart: heaviest set at each of ${countLabel(columns.size, "rep count")}, " +
        "now and as of ${dayMonthYear(asOf)}. ${columns.count { it.new }} new since then.").spokenUnits()
    ChartCard(modifier.testTag("rep-compare-card")) {
        Text("Same reps, then and now$titleSuffix", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        if (choices.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(choices) { choice ->
                TonalChip(selected = choice == from, onClick = { from = choice }, label = { Text(choice.label) },
                    modifier = Modifier.testTag("rep-compare-${choice.name}"))
            }
        }
        ChartReadout(value, detail, touched = true)
        AxisUnit(unit)
        Row {
            LoadAxis(ticks, height) { tickText(it, unit) }
            Canvas(
                Modifier.weight(1f).height(height)
                    .chartSemantics(describe, columns.size, selection.value, "$value, $detail".spokenUnits()) { selection.value = it }
                    .chartTouch(columns.size, xs) { selection.value = it }
                    .testTag("rep-compare-chart")
            ) {
                val pad = inset.toPx()
                val h = size.height - pad * 2
                fun x(i: Int) = (i + 0.5f) * size.width / columns.size
                fun y(v: Double) = pad + h * (1f - ((v - low) / span).toFloat())
                ticks.forEach { t ->
                    drawLine(colors.outlineVariant.copy(alpha = 0.7f), Offset(0f, y(t)), Offset(size.width, y(t)), strokeWidth = 1.dp.toPx())
                }
                selection.value?.let { i -> drawLine(colors.outline, Offset(x(i), 0f), Offset(x(i), size.height), strokeWidth = 1.dp.toPx()) }
                val r = 4.dp.toPx()
                columns.forEachIndexed { i, column ->
                    val now = Offset(x(i), y(column.now.load))
                    val then = column.then
                    if (then != null && column.gain > 0.0) {
                        val was = Offset(x(i), y(then.load))
                        if (was.y - now.y > r * 2) {
                            drawLine(faint, Offset(was.x, was.y - r), Offset(now.x, now.y + r), strokeWidth = 2.dp.toPx())
                        }
                        drawCircle(card, radius = r + 1.dp.toPx(), center = was)
                        drawCircle(faint, radius = r, center = was, style = Stroke(width = 1.5.dp.toPx()))
                    } else if (then != null && i != shownAt) {
                        // Unchanged: the same set then and now, so the hollow dot rings the filled one.
                        drawCircle(faint, radius = r + 2.5.dp.toPx(), center = now, style = Stroke(width = 1.5.dp.toPx()))
                    }
                    drawCircle(accent, radius = r, center = now)
                    if (i == shownAt) {
                        drawCircle(accent, radius = r + 4.dp.toPx(), center = now, style = Stroke(width = 2.dp.toPx()))
                    }
                }
            }
        }
        // Each rep count under its own column, "new" beneath the ones first lifted since the comparison day.
        Row(Modifier.fillMaxWidth().padding(start = 44.dp).clearAndSetSemantics { hideFromAccessibility() }) {
            columns.forEach { column ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(column.reps.toString(), style = axisStyle, color = colors.onSurfaceVariant)
                    Text(if (column.new) "new" else "", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 44.dp).clearAndSetSemantics { hideFromAccessibility() },
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Reps", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            ChartLegend("${readoutDay(asOf, today)}" to false, "Now" to true)
        }
    }
}
