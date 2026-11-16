package com.rebuild.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rebuild.domain.Exercise
import com.rebuild.domain.LoadUnit
import com.rebuild.domain.LoggedSet
import com.rebuild.domain.Workout
import com.rebuild.domain.localDateOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.floor

/** The day a workout happened, as its week counts it. */
internal fun workoutDate(workout: Workout): LocalDate = workout.startDate?.let(LocalDate::parse) ?: localDateOf(workout.startedAt)

/** A workout on the heaviest set chart: its heaviest working set, that set's reps, and which workout it was. */
internal data class TopSetPoint(val date: LocalDate, val top: LoggedSet, val workout: String = "") {
    val load: Double get() = top.loadValue!!
}

/** The heaviest set each session (more reps break a tie), in the unit the sessions were already filtered to. */
internal fun topSetPoints(sessions: List<HistorySession>): List<TopSetPoint> = sessions.mapNotNull { s ->
    val loaded = s.sets.filter { it.loadValue != null }
    val top = loaded.maxWithOrNull(compareBy<LoggedSet> { it.loadValue!! }.thenBy { it.reps }) ?: return@mapNotNull null
    TopSetPoint(workoutDate(s.workout), top, s.workout.name)
}

/** The points a [range] shows, counting back from [today]; All shows every one. */
internal fun pointsInRange(points: List<TopSetPoint>, range: ChartRange, today: LocalDate): List<TopSetPoint> {
    if (points.isEmpty()) return points
    val start = range.start(today, points.first().date)
    return points.filter { it.date >= start }
}

/** The most workouts that each get their own line; past that, a line per workout would be a tangle, so none is drawn. */
internal const val MAX_TOP_SET_LINES = 2

/**
 * The lines on the chart: one per workout the exercise was done in, each in date order, so a heavy day and a light day
 * each climb on their own instead of one line zig-zagging between them as if every other session were a drop. Empty
 * when the exercise was done in more workouts than [MAX_TOP_SET_LINES]: then the dots stand alone. The workout with the
 * latest point comes first.
 */
internal fun topSetLines(points: List<TopSetPoint>): List<List<TopSetPoint>> {
    val byWorkout = points.groupBy { it.workout }.values.sortedByDescending { it.last().date }
    return if (byWorkout.size > MAX_TOP_SET_LINES) emptyList() else byWorkout
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
 * Whether each of [points]' reps go under its dot rather than over it: wherever its own line doesn't run. With two
 * lines, the lower one's labels go under and the upper one's over, so they face away from each other. On a single line
 * a dot lower than both its neighbours has the line above it, so its label goes under. Lone dots are labelled above.
 */
internal fun labelBelow(points: List<TopSetPoint>, lines: List<List<TopSetPoint>>): List<Boolean> {
    val lower = lines.takeIf { it.size == 2 }?.minBy { line -> line.map { it.load }.average() }?.toSet()
    val valleys = if (lines.size == 1) topSetSegments(lines.single()).flatMap { segment ->
        segment.filterIndexed { i, p ->
            val neighbours = listOfNotNull(segment.getOrNull(i - 1), segment.getOrNull(i + 1))
            neighbours.isNotEmpty() && neighbours.all { it.load > p.load }
        }
    }.toSet() else emptySet()
    return points.map { (lower != null && it in lower) || it in valleys }
}

/**
 * Which labels get drawn, given the box each would take. Taken from the latest back, a label that would overlap one
 * already placed is skipped, so a crowded chart thins its labels instead of smearing them. The touched point always
 * shows its own, and only the labels it would overlap give way: the rest stay where they were.
 */
internal fun labelledPoints(boxes: List<Rect>, selected: Int?): Set<Int> {
    fun clash(i: Int, j: Int) = boxes[i].overlaps(boxes[j])
    val chosen = mutableListOf<Int>()
    for (i in boxes.indices.reversed()) if (chosen.none { clash(it, i) }) chosen += i
    if (selected == null || selected in chosen) return chosen.toSet()
    return (chosen.filterNot { clash(it, selected) } + selected).toSet()
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

private val dayMonth: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val weekdayDayMonth: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

/** "10 Nov", with the year only when it isn't [today]'s. */
internal fun readoutDay(date: LocalDate, today: LocalDate): String =
    if (date.year == today.year) dayMonth.format(date) else dayMonthYear(date)

/** "Thu 22 Oct", with the year only when it isn't [today]'s: the same way Records names a day. */
internal fun readoutWeekday(date: LocalDate, today: LocalDate): String =
    weekdayDayMonth.format(date) + if (date.year == today.year) "" else " ${date.year}"

/** The load axis: tick values from top to bottom, each centred on its gridline. */
@Composable
private fun LoadAxis(ticks: List<Double>, height: Dp, width: Dp, top: Dp, bottom: Dp, format: (Double) -> String) {
    Column(
        Modifier.height(height).width(width).padding(top = top, bottom = bottom, end = 8.dp)
            .clearAndSetSemantics { hideFromAccessibility() },
        verticalArrangement = if (ticks.size > 1) Arrangement.SpaceBetween else Arrangement.Center,
        horizontalAlignment = Alignment.End
    ) {
        val style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
        ticks.asReversed().forEach { Text(format(it), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
    }
}

/** The plotting area's height; the room for reps labels above and below is added on top, so big text never squeezes it. */
@Composable
private fun plotHeight(): Dp = 168.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.25f)

private fun tickText(value: Double, unit: LoadUnit) = if (unit == LoadUnit.PLATES) value.toInt().toString() else formatKg(value)

/** "3 months" and "All" on the chips, spelled out, never "3M". */
private fun rangeLabel(range: ChartRange) = if (range == ChartRange.ALL) "All" else countLabel(range.months!!.toInt(), "month")

/**
 * The heaviest set each workout, placed on its date, with its reps written by the dot: what was lifted and how many
 * times, with nothing to work out. Each workout the exercise is done in gets its own line, breaking across a gap of
 * more than two weeks. It opens on the last three months, with All a tap away once there is older history. Touching a
 * dot reads that workout's set above the chart; until then the Latest card above already says the last one.
 */
@Composable
internal fun TopSetChart(
    sessions: List<HistorySession>,
    unit: LoadUnit,
    exercise: Exercise,
    today: LocalDate,
    modifier: Modifier = Modifier
) {
    val all = remember(sessions) { topSetPoints(sessions) }
    if (all.isEmpty()) return
    // The choice only appears when it changes something: some workouts are older than three months and some aren't.
    // The chosen range is kept even while the choice is hidden, so a new workout doesn't undo it.
    val choosable = pointsInRange(all, ChartRange.M3, today).size in 1 until all.size
    var chosen by rememberSaveable { mutableStateOf(ChartRange.M3) }
    val range = if (choosable) chosen else ChartRange.ALL
    val points = remember(all, range, today) { pointsInRange(all, range, today) }
    val lines = remember(points) { topSetLines(points) }
    val below = remember(points, lines) { labelBelow(points, lines) }
    val second = remember(lines) { lines.getOrNull(1)?.toSet().orEmpty() }
    val loads = points.map { it.load }
    val ticks = loadTicks(loads.min(), loads.max(), unit)
    val low = ticks.first()
    val span = (ticks.last() - low).takeIf { it > 0 } ?: 1.0
    val from = points.first().date
    val to = points.last().date
    val days = ChronoUnit.DAYS.between(from, to)
    val colors = MaterialTheme.colorScheme
    val line = accentText()
    val card = colors.surfaceContainerLowest
    val density = LocalDensity.current
    val axisStyle = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
    val inset = with(density) { axisStyle.lineHeight.toDp() } / 2
    val repsStyle = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
    val shownStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold)
    val measurer = rememberTextMeasurer()
    // Each label laid out once, not on every frame of a drag; the touched and latest ones are bolder.
    val plain = remember(points, repsStyle, density) { points.map { measurer.measure("×${it.top.reps}", repsStyle) } }
    val bold = remember(points, shownStyle, density) { points.map { measurer.measure("×${it.top.reps}", shownStyle) } }
    // Room above the highest dot and under the lowest for their reps, so a label never runs off the chart.
    val labelRoom = with(density) { shownStyle.lineHeight.toDp() } + 4.dp
    val plot = plotHeight()
    val height = plot + labelRoom * 2 + inset * 2
    // As wide as the widest tick needs, so "112.5" at large text isn't cut off.
    val axisWidth = remember(ticks, axisStyle, density) {
        with(density) { ticks.maxOf { measurer.measure(tickText(it, unit), axisStyle).size.width }.toDp() } + 8.dp
    }.coerceAtLeast(44.dp)
    val selection = rememberChartSelection(points)
    val selected = selection.value
    val latest = points.lastIndex
    fun spokenSet(p: TopSetPoint) = "${chartLoadText(p.load, unit, exercise)} for ${countLabel(p.top.reps, "rep")}"
    fun dayAndWorkout(p: TopSetPoint) = readoutWeekday(p.date, today) + if (p.workout.isNotBlank()) " · ${p.workout}" else ""
    fun fraction(d: LocalDate) = if (days > 0) ChronoUnit.DAYS.between(from, d).toFloat() / days else 0.5f
    val xs = remember(points, inset, density) {
        { width: Int ->
            val pad = with(density) { inset.toPx() }
            points.map { pad + (width - pad * 2) * fraction(it.date) }
        }
    }
    val title = loadChartTitle(exercise, unit).substringAfter(" · ")
    val spokenRange = if (range == ChartRange.ALL) "all workouts" else "last ${rangeLabel(range)}"
    val loadSpan = if (loads.min() == loads.max()) chartLoadText(loads.min(), unit, exercise)
        else "from ${chartLoadText(loads.min(), unit, exercise)} to ${chartLoadText(loads.max(), unit, exercise)}"
    val dateSpan = if (from == to) dayMonthYear(from) else "${dayMonthYear(from)} to ${dayMonthYear(to)}"
    val describe = ("Heaviest set each workout, $title, $spokenRange: ${countLabel(points.size, "workout")}, $loadSpan, " +
        "$dateSpan." +
        (if (lines.size > 1) " One line for each of ${lines.joinToString(" and ") { it.first().workout }}." else "")).spokenUnits()
    val state = points[selected ?: latest].let { p ->
        "${if (selected == null) "Latest: " else ""}${spokenSet(p)}, ${dayMonthYear(p.date)}" +
            if (p.workout.isNotBlank()) ", ${p.workout}" else ""
    }.spokenUnits()
    ChartCard(modifier.testTag("top-set-card")) {
        Text("Heaviest set · $title", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Text("Your heaviest set each workout. Higher is more weight; ×8 is its reps.",
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        if (choosable) RangeChips(chosen, { chosen = it }, ranges = listOf(ChartRange.M3, ChartRange.ALL), label = ::rangeLabel)
        TouchReadout(points.getOrNull(selected ?: -1)?.let { p ->
            "${chartLoadText(p.load, unit, exercise)} × ${p.top.reps}" to dayAndWorkout(p)
        })
        Row {
            LoadAxis(ticks, height, axisWidth, top = labelRoom, bottom = labelRoom) { tickText(it, unit) }
            Canvas(
                Modifier.weight(1f).height(height)
                    .chartSemantics(describe, points.size, selected, state) { selection.value = it }
                    .chartTouch(points.size, xs) { selection.value = it }
                    .testTag("top-set-chart")
            ) {
                val pad = inset.toPx()
                val top = labelRoom.toPx() + pad
                val h = plot.toPx()
                val w = size.width - pad * 2
                fun x(d: LocalDate) = pad + w * fraction(d)
                fun y(v: Double) = top + h * (1f - ((v - low) / span).toFloat())
                ticks.forEach { t ->
                    drawLine(colors.outlineVariant.copy(alpha = 0.7f), Offset(0f, y(t)), Offset(size.width, y(t)), strokeWidth = 1.dp.toPx())
                }
                // Each label's box: centred on its dot, kept inside the chart, over or under the dot.
                val gap = 4.dp.toPx()
                val boxes = points.mapIndexed { i, p ->
                    val text = if (i == selected || i == latest) bold[i] else plain[i]
                    val lift = (if (i == selected) 9 else 5).dp.toPx()
                    val tw = text.size.width.toFloat()
                    val tx = (x(p.date) - tw / 2f).coerceIn(0f, (size.width - tw).coerceAtLeast(0f))
                    val ty = if (below[i]) y(p.load) + lift else y(p.load) - lift - text.size.height
                    Rect(tx - gap / 2, ty, tx + tw + gap / 2, ty + text.size.height)
                }
                val labelled = labelledPoints(boxes, selected)
                selected?.let { i ->
                    // The guide stays inside the plot and stops short of the touched dot's own label.
                    val gx = x(points[i].date)
                    val box = boxes[i]
                    val plotTop = top - pad
                    val plotBottom = top + h + pad
                    val guide = colors.outline
                    val stroke = 1.dp.toPx()
                    if (box.top > plotTop) drawLine(guide, Offset(gx, plotTop), Offset(gx, box.top), strokeWidth = stroke)
                    if (box.bottom < plotBottom) drawLine(guide, Offset(gx, box.bottom), Offset(gx, plotBottom), strokeWidth = stroke)
                }
                // Straight segments: sets are discrete, and a curve would invent loads between workouts.
                lines.forEach { workout ->
                    topSetSegments(workout).forEach { segment ->
                        val pts = segment.map { Offset(x(it.date), y(it.load)) }
                        if (pts.size > 1) {
                            val path = Path().apply { moveTo(pts.first().x, pts.first().y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
                            drawPath(path, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
                // Dots: filled on the first workout's line (and when there are no lines), hollow on the second.
                points.forEach { p ->
                    val c = Offset(x(p.date), y(p.load))
                    if (p in second) {
                        drawCircle(card, radius = 4.dp.toPx(), center = c)
                        drawCircle(line, radius = 4.dp.toPx(), center = c, style = Stroke(width = 2.dp.toPx()))
                    } else drawCircle(line, radius = if (lines.isEmpty()) 4.dp.toPx() else 3.dp.toPx(), center = c)
                }
                selected?.let { i ->
                    val c = Offset(x(points[i].date), y(points[i].load))
                    drawCircle(card, radius = 7.dp.toPx(), center = c)
                    drawCircle(line, radius = 7.dp.toPx(), center = c, style = Stroke(width = 2.5.dp.toPx()))
                }
                labelled.forEach { i ->
                    val emphasis = i == selected || i == latest
                    val text = if (emphasis) bold[i] else plain[i]
                    drawText(text, color = if (emphasis) colors.onSurface else colors.onSurfaceVariant,
                        topLeft = Offset(boxes[i].left + gap / 2, boxes[i].top))
                }
            }
        }
        DateAxis(from, to, days, today, axisWidth)
        if (lines.size > 1) WorkoutKey(lines.map { it.first().workout }, line, card)
    }
}

/**
 * Above the chart: the touched workout's set and day, or until a dot is touched, how to read one. Both take the same
 * height, so touching doesn't move the chart. The chart itself announces this to screen readers.
 */
@Composable
private fun TouchReadout(read: Pair<String, String>?) {
    val density = LocalDensity.current
    val minHeight = with(density) { 32.sp.toDp() + MaterialTheme.typography.bodySmall.lineHeight.toDp() }
    Box(Modifier.fillMaxWidth().heightIn(min = minHeight), contentAlignment = Alignment.CenterStart) {
        if (read != null) ChartReadout(read.first, read.second, touched = true)
        else Text("Touch a dot to read that workout", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics { hideFromAccessibility() })
    }
}

/** Which line is which workout, when there are two: a filled dot for the first, a hollow one for the second. */
@Composable
private fun WorkoutKey(names: List<String>, color: androidx.compose.ui.graphics.Color, card: androidx.compose.ui.graphics.Color) {
    Row(Modifier.clearAndSetSemantics { hideFromAccessibility() }, horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        names.forEachIndexed { i, name ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(Modifier.size(10.dp)) {
                    val r = 4.dp.toPx()
                    if (i == 0) drawCircle(color, radius = r)
                    else { drawCircle(card, radius = r); drawCircle(color, radius = r, style = Stroke(width = 2.dp.toPx())) }
                }
                Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** Start, middle and end dates under a date-spaced chart, so a week in between can be read off it. */
@Composable
private fun DateAxis(from: LocalDate, to: LocalDate, days: Long, today: LocalDate, start: Dp) {
    val style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Normal)
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    // The year only when the dates aren't all this year's.
    fun day(d: LocalDate) = if (from.year == today.year && to.year == today.year) readoutDay(d, today) else dayMonthYear(d)
    Row(Modifier.fillMaxWidth().padding(start = start)
        .clearAndSetSemantics { contentDescription = "From ${dayMonthYear(from)} to ${dayMonthYear(to)}" },
        horizontalArrangement = Arrangement.SpaceBetween) {
        // Large text has room for the two ends only; a middle date would run into them.
        val middle = days >= 14 && LocalDensity.current.fontScale <= 1.3f
        Text(day(from), style = style, color = color, maxLines = 1)
        if (middle) Text(day(from.plusDays(days / 2)), style = style, color = color, maxLines = 1)
        if (days > 0) Text(day(to), style = style, color = color, maxLines = 1)
    }
}
