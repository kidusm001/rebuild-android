package com.rebuild.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** A value on a day, in the unit the chart shows. */
data class ChartPoint(val date: LocalDate, val value: Double)

/** How far back a progress chart looks. All starts at the first record. */
enum class ChartRange(val label: String, val months: Long?) {
    M1("1M", 1), M3("3M", 3), M6("6M", 6), Y1("1Y", 12), ALL("All", null);

    fun start(today: LocalDate, first: LocalDate): LocalDate =
        months?.let { today.minusMonths(it) }?.takeIf { it > first } ?: first
}

@Composable
internal fun RangeChips(selected: ChartRange, onSelect: (ChartRange) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ChartRange.entries) { range ->
            FilterChip(
                selected = range == selected,
                onClick = { onSelect(range) },
                label = { Text(range.label) },
                modifier = Modifier.testTag("range-${range.name}")
            )
        }
    }
}

/** Round axis ticks around [min]..[max], so the labels are values a person could read off a scale. */
internal fun niceTicks(min: Double, max: Double, count: Int = 3): List<Double> {
    if (max <= min) return listOf(min)
    val raw = (max - min) / (count - 1)
    val magnitude = 10.0.pow(floor(log10(raw)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= raw }
    val low = floor(min / step) * step
    val high = ceil(max / step) * step
    return generateSequence(low) { it + step }.takeWhile { it <= high + step / 2 }.toList()
}

/**
 * Touching a chart: a press or a horizontal drag selects the point nearest the finger, with a light tick each time the
 * selection moves to another point. Vertical drags are left to the list the chart sits in. [xs] gives each point's x in
 * the chart's own pixels for its current size.
 */
internal fun Modifier.chartTouch(count: Int, xs: (width: Int) -> List<Float>, onSelect: (Int) -> Unit): Modifier =
    if (count == 0) this else pointerInput(count, xs) {
        fun nearest(x: Float): Int = xs(size.width).withIndex().minBy { abs(it.value - x) }.index
        coroutineScope {
            launch { detectTapGestures(onPress = { onSelect(nearest(it.x)) }) }
            launch {
                detectHorizontalDragGestures(onDragStart = { onSelect(nearest(it.x)) }) { change, _ ->
                    change.consume()
                    onSelect(nearest(change.position.x))
                }
            }
        }
    }

/**
 * The chart's selected point, and a tick when it changes. Null until the chart is touched; reset whenever [key] (the
 * range or the data) changes, so a selection never points at a point that has gone.
 */
@Composable
internal fun rememberChartSelection(key: Any): MutableState<Int?> {
    val haptics = LocalHapticFeedback.current
    val selected = remember(key) { mutableStateOf<Int?>(null) }
    return remember(selected) {
        object : MutableState<Int?> by selected {
            override var value: Int?
                get() = selected.value
                set(v) {
                    if (v != selected.value && v != null) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    selected.value = v
                }
        }
    }
}

/**
 * What the chart reads out: the touched point, or the latest one before anything is touched, with a small "Touch to
 * read" until then. It keeps two lines of height either way, so touching never moves the chart.
 */
@Composable
internal fun ChartReadout(value: String, detail: String, touched: Boolean, modifier: Modifier = Modifier) {
    // The chart itself announces this as its state, so screen readers don't hear it twice.
    Row(modifier.fillMaxWidth().semantics { hideFromAccessibility() }.testTag("chart-readout"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1)
        }
        if (!touched) Text("Touch to read", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

/** A session's values in order, equal neighbours grouped: "27.5 kg/DB × 2 · 25 kg/DB". */
internal fun sessionText(values: List<Double>, format: (Double) -> String): String {
    val runs = mutableListOf<Pair<Double, Int>>()
    values.forEach { v -> if (runs.lastOrNull()?.first == v) runs[runs.lastIndex] = v to runs.last().second + 1 else runs += v to 1 }
    return runs.joinToString(" · ") { (v, n) -> format(v) + if (n > 1) " × $n" else "" }
}

/**
 * Screen readers step through a chart like a slider: swiping up or down moves the selection one point and reads it.
 * The summary [description] is still spoken first.
 */
internal fun Modifier.chartSemantics(description: String, count: Int, selected: Int?, readout: String,
    onSelect: (Int) -> Unit): Modifier = semantics {
    contentDescription = description
    if (count > 1) {
        val at = selected ?: (count - 1)
        stateDescription = readout
        progressBarRangeInfo = ProgressBarRangeInfo(at.toFloat(), 0f..(count - 1).toFloat(), steps = (count - 2).coerceAtLeast(0))
        setProgress { target -> onSelect(target.roundToInt().coerceIn(0, count - 1)); true }
    }
}

/**
 * A progress chart over dates. [raw] readings are small hollow dots; [line] segments are a solid line with filled
 * points (a gap in the data breaks it, nothing is drawn across it). Shape, not color alone, tells them apart, and the
 * list under every chart is its text equivalent; screen readers hear [description] once.
 *
 * Touching it selects the nearest point of the line (or of the raw readings when there is no line): a guide and a ring
 * mark it, and [readout] says what it is above the chart.
 */
@Composable
internal fun TrendChart(
    raw: List<ChartPoint>,
    line: List<List<ChartPoint>>,
    from: LocalDate,
    to: LocalDate,
    format: (Double) -> String,
    description: String,
    modifier: Modifier = Modifier,
    unitLabel: String? = null,
    readout: ((ChartPoint) -> Pair<String, String>)? = null
) {
    val values = raw.map { it.value } + line.flatten().map { it.value }
    if (values.isEmpty()) return
    val ticks = niceTicks(values.min(), values.max())
    val low = ticks.first()
    val high = ticks.last()
    val span = (high - low).takeIf { it > 0 } ?: 1.0
    val days = (to.toEpochDay() - from.toEpochDay()).coerceAtLeast(1)
    val colors = MaterialTheme.colorScheme
    val axisStyle = MaterialTheme.typography.labelSmall
    val chartHeight = 180.dp
    val density = LocalDensity.current
    val inset = with(density) { axisStyle.lineHeight.toDp() } / 2
    val points = line.flatten().ifEmpty { raw }.sortedBy { it.date }
    val selection = rememberChartSelection(Triple(raw, line, from))
    val shownPoint = points.getOrNull(selection.value ?: points.lastIndex)
    val read = shownPoint?.let { readout?.invoke(it) }
    val xs = remember(points, from, days, inset) {
        { width: Int ->
            val pad = with(density) { inset.toPx() }
            points.map { pad + (width - pad * 2) * ((it.date.toEpochDay() - from.toEpochDay()).toFloat() / days) }
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // With a single point there is nothing else to read, so no "Touch to read".
        if (read != null) ChartReadout(read.first, read.second, touched = selection.value != null || points.size < 2)
        if (unitLabel != null) Text(unitLabel, style = axisStyle, color = colors.onSurfaceVariant,
            modifier = Modifier.width(40.dp).clearAndSetSemantics {}, textAlign = TextAlign.End)
        Row {
            Column(
                Modifier.height(chartHeight).width(48.dp).padding(end = 8.dp).clearAndSetSemantics {},
                verticalArrangement = if (ticks.size > 1) Arrangement.SpaceBetween else Arrangement.Center,
                horizontalAlignment = Alignment.End
            ) {
                ticks.asReversed().forEach { Text(format(it), style = axisStyle, color = colors.onSurfaceVariant, maxLines = 1) }
            }
            Crossfade(targetState = Triple(raw, line, from), animationSpec = tween(150), modifier = Modifier.weight(1f),
                label = "chart") { (shownRaw, shownLine, start) ->
                val shownDays = (to.toEpochDay() - start.toEpochDay()).coerceAtLeast(1)
                val marked = shownPoint.takeIf { selection.value != null && start == from }
                Canvas(Modifier.fillMaxWidth().height(chartHeight)
                    .chartSemantics(description, points.size, selection.value, read?.let { "${it.first}, ${it.second}" } ?: "") {
                        selection.value = it
                    }
                    .chartTouch(points.size, xs) { selection.value = it }
                    .testTag("trend-chart")) {
                    val pad = inset.toPx()
                    val h = size.height - pad * 2
                    val w = size.width - pad * 2
                    fun x(d: LocalDate) = pad + w * ((d.toEpochDay() - start.toEpochDay()).toFloat() / shownDays)
                    fun y(v: Double) = pad + h * (1f - ((v - low) / span).toFloat())
                    ticks.forEach { t ->
                        drawLine(colors.outlineVariant, Offset(0f, y(t)), Offset(size.width, y(t)), strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
                    }
                    if (marked != null) drawLine(colors.outline, Offset(x(marked.date), 0f), Offset(x(marked.date), size.height),
                        strokeWidth = 1.dp.toPx())
                    shownRaw.forEach { p ->
                        drawCircle(colors.onSurfaceVariant.copy(alpha = 0.75f), radius = 3.5.dp.toPx(), center = Offset(x(p.date), y(p.value)),
                            style = Stroke(width = 1.5.dp.toPx()))
                    }
                    shownLine.forEach { segment ->
                        if (segment.size > 1) {
                            val path = Path().apply {
                                segment.forEachIndexed { i, p ->
                                    if (i == 0) moveTo(x(p.date), y(p.value)) else lineTo(x(p.date), y(p.value))
                                }
                            }
                            drawPath(path, colors.primary, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round,
                                join = StrokeJoin.Round))
                        }
                        segment.forEach { p -> drawCircle(colors.primary, radius = 4.dp.toPx(), center = Offset(x(p.date), y(p.value))) }
                    }
                    if (marked != null) {
                        val c = Offset(x(marked.date), y(marked.value))
                        drawCircle(colors.surface, radius = 7.dp.toPx(), center = c)
                        drawCircle(colors.primary, radius = 7.dp.toPx(), center = c, style = Stroke(width = 2.5.dp.toPx()))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 48.dp).clearAndSetSemantics {}, horizontalArrangement = Arrangement.SpaceBetween) {
            // Start, middle and end, so a week in between can be read off the axis.
            Text(dayMonthYear(from), style = axisStyle, color = colors.onSurfaceVariant)
            if (days >= 14) Text(dayMonthYear(from.plusDays(days / 2)), style = axisStyle, color = colors.onSurfaceVariant)
            if (days > 0) Text(dayMonthYear(to), style = axisStyle, color = colors.onSurfaceVariant)
        }
    }
}

/** A legend in words and shape: "○ Weigh-ins  ● Weekly average". */
@Composable
internal fun ChartLegend(vararg items: Pair<String, Boolean>) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.clearAndSetSemantics {}) {
        items.forEach { (label, filled) ->
            Text((if (filled) "● " else "○ ") + label, style = MaterialTheme.typography.labelMedium,
                color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val shortDate = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")
internal fun dayMonthYear(date: LocalDate): String = shortDate.format(date)
