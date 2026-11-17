package com.rebuild.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rebuild.R
import com.rebuild.domain.LoadUnit
import com.rebuild.domain.RepRecord
import com.rebuild.domain.Workout
import com.rebuild.domain.localDateOf
import com.rebuild.domain.weekStartOf
import com.rebuild.domain.weeklySetsByMuscle
import com.rebuild.domain.workoutDays
import com.rebuild.ui.theme.SignalAmber
import androidx.compose.material3.ripple
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * A month of training at a glance: each day with a finished workout is marked and names it to screen readers. It
 * counts nothing and keeps no streak; an empty day is just an empty day.
 */
@Composable
internal fun WorkoutCalendar(env: AppEnv, onOpen: (Workout) -> Unit) {
    val today = localDateOf(env.clock())
    var shown by rememberSaveable { mutableLongStateOf(today.withDayOfMonth(1).toEpochDay()) }
    val month = LocalDate.ofEpochDay(shown)
    val days = env.state.workoutDays(month)
    val title = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()).format(month)
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
            IconButton(onClick = { shown = month.minusMonths(1).toEpochDay() }, modifier = Modifier.testTag("calendar-previous")) {
                RebuildIcon(R.drawable.ic_arrow_left, contentDescription = "Previous month")
            }
            IconButton(onClick = { shown = month.plusMonths(1).toEpochDay() }, enabled = month.plusMonths(1) <= today,
                modifier = Modifier.testTag("calendar-next")) {
                RebuildIcon(R.drawable.ic_arrow_right, contentDescription = "Next month")
            }
        }
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
            DayOfWeek.entries.forEach {
                Text(it.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        val lead = month.dayOfWeek.value - 1
        val total = month.lengthOfMonth()
        (0 until (lead + total + 6) / 7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { col ->
                    val day = week * 7 + col - lead + 1
                    Box(Modifier.weight(1f).height(52.dp), contentAlignment = Alignment.Center) {
                        if (day in 1..total) CalendarDay(month.withDayOfMonth(day), today, days[month.withDayOfMonth(day)], onOpen)
                    }
                }
            }
        }
        val count = days.values.sumOf { it.size }
        Text(
            if (count == 0) "No finished workouts this month" else "${countLabel(count, "workout")} this month",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CalendarDay(date: LocalDate, today: LocalDate, workouts: List<Workout>?, onOpen: (Workout) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val trained = !workouts.isNullOrEmpty()
    val spoken = shortDate(date) + if (trained) ": " + workouts!!.joinToString { it.name } else ""
    Column(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            // A small marker, not a large fill: full signal amber in both themes.
            .background(if (trained) SignalAmber else colors.surface.copy(alpha = 0f))
            .then(if (date == today && !trained) Modifier.border(1.5.dp, colors.outline, CircleShape) else Modifier)
            // An amber marker: the press is ink, since the app's ember press would vanish into it.
            .then(if (trained) Modifier.clickable(interactionSource = null, indication = ripple(color = colors.onPrimaryContainer),
                onClickLabel = "Open ${workouts!!.first().name}") { onOpen(workouts.first()) }
                else Modifier)
            .clearAndSetSemantics { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge,
            color = if (trained) colors.onPrimaryContainer else if (date > today) colors.outline else colors.onSurface)
        if (workouts != null && workouts.size > 1) Text("×${workouts.size}", style = MaterialTheme.typography.labelSmall,
            color = colors.onPrimaryContainer)
    }
}

/**
 * Normal working sets per primary muscle for one Monday–Sunday week, as plain bars with their numbers. No targets
 * unless the plan states one; warm-ups never count and a drop or rest-pause set counts once.
 */
@Composable
internal fun WeeklyMuscleSets(env: AppEnv) {
    val today = localDateOf(env.clock())
    // Opens on the latest week with finished workouts, so a Tuesday doesn't open on an empty week.
    var week by rememberSaveable {
        val latest = env.state.workouts.filter { it.finishedAt != null }.mapNotNull { it.startDate?.let(LocalDate::parse) }
            .filter { it <= today }.maxOrNull()
        mutableLongStateOf(weekStartOf(latest ?: today).toEpochDay())
    }
    val start = LocalDate.ofEpochDay(week)
    val rows = env.state.weeklySetsByMuscle(start)
    val most = rows.maxOfOrNull { it.second } ?: 0
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Working sets by muscle", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                Text("${shortDate(start)} – ${shortDate(start.plusDays(6))}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { week = start.minusWeeks(1).toEpochDay() }, modifier = Modifier.testTag("muscles-previous")) {
                RebuildIcon(R.drawable.ic_arrow_left, contentDescription = "Previous week")
            }
            IconButton(onClick = { week = start.plusWeeks(1).toEpochDay() }, enabled = start.plusWeeks(1) <= today,
                modifier = Modifier.testTag("muscles-next")) {
                RebuildIcon(R.drawable.ic_arrow_right, contentDescription = "Next week")
            }
        }
        if (rows.isEmpty()) Text("No working sets this week.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        rows.forEach { (muscle, sets) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp).semantics(mergeDescendants = true) {
                contentDescription = "${muscle.title}: ${countLabel(sets, "working set")}"
            }, verticalAlignment = Alignment.CenterVertically) {
                Text(muscle.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(112.dp), maxLines = 1)
                Box(Modifier.weight(1f).height(12.dp).clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    // A chart bar, not a fill behind text: full signal amber in both themes.
                    Box(Modifier.fillMaxWidth(sets / most.toFloat()).height(12.dp).clip(MaterialTheme.shapes.extraSmall)
                        .background(SignalAmber))
                }
                Spacer(Modifier.width(12.dp))
                Text(sets.toString(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.End)
            }
        }
        if (rows.isNotEmpty()) Text("Normal working sets · warm-ups excluded · primary muscle only",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Actual bests at each rep count, newest unit only, superseded ones folded away. Never estimated: what is listed was
 * lifted, on the day shown.
 */
@Composable
internal fun RecordsSection(records: List<RepRecord>, unit: LoadUnit, exercise: com.rebuild.domain.Exercise,
    was: (RepRecord) -> String? = { null }, onOpen: (Workout) -> Unit) {
    if (records.isEmpty()) return
    var all by rememberSaveable(unit) { mutableStateOf(false) }
    val shown = if (all) records else records.filterNot { it.superseded }
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        SectionHeader("Records")
        Text("Heaviest working set at each rep count, as lifted.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        shown.forEachIndexed { i, record ->
            ListRow(
                headline = "${countLabel(record.reps, "rep")} · ${loadText(record.set.amount, record.set.loadUnit,
                    record.set.plates, exercise)}",
                supporting = shortDate(LocalDate.parse(record.workout.startDate)) +
                    (if (record.superseded) " · beaten at more reps" else "") + (was(record)?.let { " · $it" } ?: ""),
                onClick = { onOpen(record.workout) },
                modifier = Modifier.groupedRow(i, shown.lastIndex).testTag("record-${record.reps}")
            )
        }
        if (records.any { it.superseded }) SecondaryButton(onClick = { all = !all }, modifier = Modifier.padding(top = 8.dp).testTag("records-all")) {
            val beaten = records.count { it.superseded }
            Text(if (all) "Hide beaten records" else "Show ${countLabel(beaten, "beaten record")}")
        }
    }
}

/**
 * "was 35 kg/DB on 18 Aug": what this record's rep count stood at on [comparison]'s day, said only when the record
 * itself was set since then and is heavier. Nothing for a rep count first lifted since (there's nothing to compare),
 * or one that hasn't moved: a plain fact, never a judgement or a colour.
 */
internal fun recordWas(record: RepRecord, comparison: com.rebuild.domain.RepComparison?, exercise: com.rebuild.domain.Exercise): String? {
    val then = comparison?.then ?: return null
    if (comparison.gain <= 0.0 || comparison.now.set.id != record.set.id) return null
    return "was ${loadText(then.set.amount, then.set.loadUnit, then.set.plates, exercise)} on ${dayMonth(then.date)}"
}

private fun dayMonth(date: LocalDate): String =
    java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault()).format(date)
