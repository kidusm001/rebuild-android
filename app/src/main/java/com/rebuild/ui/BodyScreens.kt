package com.rebuild.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.unit.dp
import com.rebuild.R
import com.rebuild.domain.Action
import com.rebuild.domain.BodyMeasurement
import com.rebuild.domain.BodySite
import com.rebuild.domain.BodyweightEntry
import com.rebuild.domain.LengthUnit
import com.rebuild.domain.MassUnit
import com.rebuild.domain.SiteProgress
import com.rebuild.domain.WeightWeek
import com.rebuild.domain.bodyweightUnit
import com.rebuild.domain.lengthIn
import com.rebuild.domain.limitUserText
import com.rebuild.domain.localDate
import com.rebuild.domain.localDateOf
import com.rebuild.domain.massIn
import com.rebuild.domain.measurementDue
import com.rebuild.domain.siteProgress
import com.rebuild.domain.weekStartOf
import com.rebuild.domain.weighInsOn
import com.rebuild.domain.weightChange
import com.rebuild.domain.weightWeeks
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

/** Weigh-ins a week needs before its average leads the summary. */
private const val MIN_LEADING_DAYS = 3

// ---- Text ------------------------------------------------------------------------------------------------------

/** An average or a difference to one decimal: "82.1 kg". Entered values are always shown exactly as typed. */
internal fun oneDecimal(value: Double): String = formatKg((value * 10).roundToLong() / 10.0)

// A value and its unit, or a day and its month, never wrap apart at large text.
private const val NBSP = '\u00A0'
internal fun massText(value: Double, unit: MassUnit) = "${formatKg(value)}$NBSP${unit.symbol}"
internal fun averageText(value: Double, unit: MassUnit) = "${oneDecimal(value)}$NBSP${unit.symbol}"
internal fun lengthText(value: Double, unit: LengthUnit) = "${formatKg(value)}$NBSP${unit.symbol}"
private fun dayText(date: LocalDate) = shortDate(date).replace(' ', NBSP)

/** A neutral signed change, "−1.6 kg" or "+0.4 cm": no green or red, losing and gaining are both just facts. */
internal fun signedText(value: Double, symbol: String): String {
    val rounded = (value * 10).roundToLong() / 10.0
    return when {
        rounded == 0.0 -> "no change"
        rounded < 0 -> "−${formatKg(abs(rounded))}$NBSP$symbol"
        else -> "+${formatKg(rounded)}$NBSP$symbol"
    }
}

private fun weekRange(start: LocalDate) = "${dayText(start)} – ${dayText(start.plusDays(6))}"

/**
 * Weeks of weigh-ins as chart segments: a week with no weigh-ins breaks the line rather than being bridged. Each
 * average sits on the mean day of its weigh-ins, so this week's point is never drawn in the days still to come.
 */
internal fun weeklySegments(weeks: List<WeightWeek>): List<List<ChartPoint>> {
    val segments = mutableListOf<MutableList<ChartPoint>>()
    var previous: LocalDate? = null
    weeks.forEach { week ->
        val day = week.entries.map { it.localDate().toEpochDay() }.distinct().average().roundToLong()
        val point = ChartPoint(LocalDate.ofEpochDay(day), week.average)
        if (previous == null || previous != week.start.minusWeeks(1)) segments += mutableListOf(point)
        else segments.last() += point
        previous = week.start
    }
    return segments
}

/** A weekly point's readout: "82.2 kg average", then "Mon 12 Oct – Sun 18 Oct · 3 days". */
internal fun weekReadout(weeks: List<WeightWeek>, unit: MassUnit): (ChartPoint) -> Pair<String, String> = { point ->
    val week = weeks.firstOrNull { it.start == weekStartOf(point.date) }
    if (week == null) averageText(point.value, unit) to dayText(point.date)
    else "${averageText(week.average, unit)} average" to "${weekRange(week.start)} · ${countLabel(week.days, "day")}"
}

/** Measurements of one site as segments: a gap of more than a missed week breaks the line. */
internal fun measuredSegments(points: List<ChartPoint>): List<List<ChartPoint>> {
    val segments = mutableListOf<MutableList<ChartPoint>>()
    points.forEach { point ->
        val last = segments.lastOrNull()?.lastOrNull()
        if (last == null || ChronoUnit.DAYS.between(last.date, point.date) > 10) segments += mutableListOf(point)
        else segments.last() += point
    }
    return segments
}

// ---- Weigh-in dialog --------------------------------------------------------------------------------------------

/**
 * Logs a new weigh-in, or corrects or deletes [existing]. The field starts empty, with the last reading shown beneath
 * it: a prefill would let a weigh-in that never happened pass as real.
 */
@Composable
internal fun BodyweightDialog(env: AppEnv, existing: BodyweightEntry?, onDismiss: () -> Unit) {
    val state = env.state
    val key = existing?.id ?: "new"
    val id by rememberSaveable(key) { mutableStateOf(existing?.id ?: newId()) }
    var text by rememberSaveable(key) { mutableStateOf(existing?.value?.let(::formatKg).orEmpty()) }
    var unit by rememberSaveable(key) { mutableStateOf(existing?.unit ?: state.bodyweightUnit()) }
    var note by rememberSaveable(key) { mutableStateOf(existing?.note.orEmpty()) }
    var error by rememberSaveable(key) { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable(key) { mutableStateOf(false) }
    val last = state.bodyweights.filter { it.id != existing?.id }.maxByOrNull { it.at }

    fun save() {
        val value = parseDecimal(text)
        val range = if (unit == MassUnit.KG) 20.0..400.0 else 44.0..880.0
        if (value == null || value !in range) {
            error = "Enter your weight from ${range.start.toInt()} to ${range.endInclusive.toInt()} ${unit.symbol}."
            return
        }
        error = null
        val at = existing?.at ?: env.clock()
        env.runner.run(Action.LogBodyweight(id, at, value, unit, note.trim(), existing?.date ?: localDateOf(at).toString()),
            onError = { error = it }, onSuccess = onDismiss)
    }

    if (deleting && existing != null) {
        ConfirmDialog(
            title = "Delete this weigh-in?",
            text = "${massText(existing.value, existing.unit)} on ${dayText(existing.localDate())} at " +
                "${formatTime(existing.at)} is removed. This can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            busy = env.runner.busy,
            onDismiss = { deleting = false },
            onConfirm = { env.runner.run(Action.DeleteBodyweight(existing.id), onSuccess = onDismiss) }
        )
        return
    }
    AdaptiveDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Log weight" else "Edit weigh-in") },
        text = {
            NumberField(text, { text = it; error = null }, "Bodyweight (${unit.symbol})",
                Modifier.fillMaxWidth().testTag("bodyweight-value"), isError = error != null,
                supportingText = error ?: last?.let { "Last: ${massText(it.value, it.unit)} · ${dayText(it.localDate())}" },
                imeAction = ImeAction.Done, onDone = ::save)
            MassUnitChips(unit) { if (it != unit) { unit = it; error = null } }
            OutlinedTextField(note, { note = limitUserText(it, MAX_NOTE_CHARS) }, label = { Text("Note (optional)") },
                singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().testTag("bodyweight-note"))
            Text(
                if (existing != null) "Weighed ${dayText(existing.localDate())} at ${formatTime(existing.at)}"
                else "Same conditions each time: on waking, before food.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (existing != null) TextButton(
                onClick = { deleting = true },
                enabled = !env.runner.busy,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.heightIn(min = 48.dp).testTag("bodyweight-delete")
            ) { Text("Delete weigh-in") }
        },
        confirmButton = {
            TextButton(onClick = ::save, enabled = !env.runner.busy, modifier = Modifier.testTag("bodyweight-save")) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MassUnitChips(unit: MassUnit, onSelect: (MassUnit) -> Unit) {
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MassUnit.entries.forEach { option ->
            FilterChip(selected = option == unit, onClick = { onSelect(option) }, label = { Text(option.symbol) },
                modifier = Modifier.testTag("mass-${option.name}")
                    .semantics { contentDescription = if (option == MassUnit.KG) "Kilograms" else "Pounds" })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LengthUnitChips(unit: LengthUnit, onSelect: (LengthUnit) -> Unit) {
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LengthUnit.entries.forEach { option ->
            FilterChip(selected = option == unit, onClick = { onSelect(option) }, label = { Text(option.symbol) },
                modifier = Modifier.testTag("length-${option.name}")
                    .semantics { contentDescription = if (option == LengthUnit.CM) "Centimetres" else "Inches" })
        }
    }
}

// ---- Bodyweight screen ------------------------------------------------------------------------------------------

/**
 * Weigh-ins as weekly averages: the average is the headline, never today's reading, so a day's water swing isn't
 * the story. Raw readings stay visible as muted dots and in the list, exactly as entered.
 */
@Composable
internal fun BodyweightScreen(env: AppEnv, onBack: () -> Unit) {
    val state = env.state
    val unit = state.bodyweightUnit()
    val weeks = weightWeeks(state.bodyweights, unit)
    val today = localDateOf(env.clock())
    var logging by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var range by rememberSaveable { mutableStateOf(ChartRange.M3) }

    ScreenScaffold(title = "Bodyweight", onBack = onBack, actions = {
        TextButton(onClick = { logging = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("weight-log")) {
            Text("Log")
        }
    }) {
        LazyBody {
            if (weeks.isEmpty()) {
                item(key = "empty") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("No weigh-ins yet", style = MaterialTheme.typography.titleMedium)
                        Text("Log one from Today or after a workout. Weekly averages and the trend build from there.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { logging = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Log weight") }
                    }
                }
                return@LazyBody
            }
            item(key = "summary") { WeightSummary(weeks, unit, today) }
            if (weeks.size < 2) item(key = "chart-soon") {
                Text("The trend line starts with a second week of weigh-ins.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else item(key = "chart") {
                val first = state.bodyweights.minOf { it.localDate() }
                val start = range.start(today, first)
                val raw = state.bodyweights.filter { it.localDate() >= start }
                    .map { ChartPoint(it.localDate(), massIn(it.value, it.unit, unit)) }
                val segments = weeklySegments(weeks.filter { it.start.plusDays(6) >= start })
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RangeChips(range, { range = it })
                    val shown = segments.flatten()
                    if (raw.isEmpty()) Text("No weigh-ins in this range.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TrendChart(
                        raw, segments, start, today, { oneDecimal(it) }, unitLabel = unit.symbol,
                        readout = weekReadout(weeks, unit),
                        description = "Weekly average bodyweight, ${range.label}: " + if (shown.isEmpty()) "no weeks."
                            else "${averageText(shown.first().value, unit)} to ${averageText(shown.last().value, unit)} " +
                                "over ${countLabel(shown.size, "week")}."
                    )
                    ChartLegend("Weigh-ins" to false, "Weekly average" to true)
                }
            }
            item(key = "weeks-header") { SectionHeader("Weeks") }
            weeks.asReversed().forEach { week ->
                item(key = "week-${week.start}") {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp).semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(weekRange(week.start), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("${averageText(week.average, unit)} avg · ${countLabel(week.days, "day")}",
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                itemsIndexed(week.entries.asReversed(), key = { _, e -> e.id }) { _, entry ->
                    ListRow(
                        headline = massText(entry.value, entry.unit),
                        supporting = "${dayText(entry.localDate())} · ${formatTime(entry.at)}" +
                            (if (entry.note.isNotBlank()) " · ${entry.note}" else ""),
                        onClick = { editing = entry.id },
                        modifier = Modifier.testTag("weigh-in-${entry.id}"),
                        trailing = { RebuildIcon(R.drawable.ic_pencil, contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    )
                }
            }
        }
    }
    if (logging) BodyweightDialog(env, null, onDismiss = { logging = false })
    state.bodyweights.find { it.id == editing }?.let { BodyweightDialog(env, it, onDismiss = { editing = null }) }
}

@Composable
private fun WeightSummary(weeks: List<WeightWeek>, unit: MassUnit, today: LocalDate) {
    // A week of one or two weigh-ins is mostly the scale's day-to-day swing: the last full week leads until this one
    // has three, and every change is worked out from the week that leads.
    val current = weeks.last().takeIf { it.start == weekStartOf(today) }
    val leading = weeks.filter { it.start < weekStartOf(today) }
    val shown = if (current != null && (current.days >= MIN_LEADING_DAYS || leading.isEmpty())) weeks else leading
    val latest = shown.last()
    val thisWeek = latest.start == weekStartOf(today)
    val previous = shown.getOrNull(shown.size - 2)
    val change = weightChange(shown)
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer)
            .semantics(mergeDescendants = true) {}.padding(16.dp).testTag("weight-summary"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(if (thisWeek) "This week" else if (latest.start == weekStartOf(today).minusWeeks(1)) "Last week"
            else "Week of ${dayText(latest.start)}", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(averageText(latest.average, unit), style = MaterialTheme.typography.headlineMedium)
        Text("Average of ${countLabel(latest.days, "day")} · ${weekRange(latest.start)}",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (current != null && latest !== current) Text(
            "So far this week: ${averageText(current.average, unit)} · ${countLabel(current.days, "day")}",
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("weight-so-far")
        )
        if (previous != null) Text(
            (if (previous.start == latest.start.minusWeeks(1)) "Week before" else "Week of ${dayText(previous.start)}") +
                ": ${averageText(previous.average, unit)} (${signedText(latest.average - previous.average, unit.symbol)})",
            style = MaterialTheme.typography.bodyMedium
        )
        if (change != null) Text(
            "Last 4 weeks: ${signedText(change.total, unit.symbol)} · about ${signedText(change.perWeek, unit.symbol)} a week",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("weight-change")
        )
    }
}

// ---- Measurements ------------------------------------------------------------------------------------------------

@Composable
internal fun MeasurementsScreen(env: AppEnv, onBack: () -> Unit, onMeasure: () -> Unit, onSite: (BodySite) -> Unit) {
    val state = env.state
    val progress = state.siteProgress()
    val today = localDateOf(env.clock())
    var editingSites by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(title = "Measurements", onBack = onBack, actions = {
        TextButton(onClick = { editingSites = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("edit-sites")) {
            Text("Sites")
        }
    }) {
        LazyBody(spacing = 0.dp) {
            item(key = "measure") {
                Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val due = state.measurementDue(today)
                    if (due) Button(onClick = onMeasure, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .testTag("measure-this-week")) { Text("Measure this week") }
                    else OutlinedButton(onClick = onMeasure, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("measure-this-week")) { Text("Measured this week · add or correct") }
                    Text("Once a week, same morning conditions, snug tape, relaxed after a normal breath out.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (progress.isEmpty()) {
                item(key = "empty") {
                    Text("${state.measurementSites.first().title} is ready to measure. Turn on other sites in Sites.",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
            itemsIndexed(progress, key = { _, p -> p.site.name }) { i, site ->
                Column {
                    if (i > 0) HorizontalDivider()
                    ListRow(
                        headline = site.site.title,
                        supporting = siteSupporting(site),
                        onClick = { onSite(site.site) },
                        modifier = Modifier.testTag("site-${site.site.name}"),
                        trailing = { RebuildIcon(R.drawable.ic_chevron_right, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    )
                }
            }
        }
    }
    if (editingSites) SitesDialog(env, onDismiss = { editingSites = false })
}

/** Which sites the weekly form asks for. Turning one off never deletes what was measured there. */
@Composable
private fun SitesDialog(env: AppEnv, onDismiss: () -> Unit) {
    var chosen by rememberSaveable { mutableStateOf(env.state.measurementSites.map { it.name }) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AdaptiveDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sites to measure") },
        text = {
            Column {
                BodySite.entries.forEach { site ->
                    CheckRow(site.title, site.name in chosen, { on ->
                        chosen = if (on) chosen + site.name else chosen - site.name
                    }, supporting = site.landmark, modifier = Modifier.testTag("site-toggle-${site.name}"))
                }
            }
            ErrorText(error)
        },
        confirmButton = {
            TextButton(onClick = {
                val sites = BodySite.entries.filter { it.name in chosen }
                env.runner.run(Action.SetMeasurementSites(sites), onError = { error = it }, onSuccess = onDismiss)
            }, enabled = !env.runner.busy) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * This week's measuring form: every chosen site, each starting empty with its last value and landmark beneath. Only
 * filled sites are saved, in one go; a site measured earlier this week is corrected in place rather than doubled.
 */
@Composable
internal fun MeasureScreen(env: AppEnv, onBack: () -> Unit) {
    val state = env.state
    val today = localDateOf(env.clock())
    val week = weekStartOf(today)
    val sites = state.measurementSites
    val thisWeek = state.measurements.filter { weekStartOf(it.localDate()) == week }.associateBy { it.site }
    val values: SnapshotStateMap<String, String> = rememberSaveable(saver = stringMapSaver) {
        mutableStateMapOf<String, String>().apply {
            sites.forEach { site -> put(site.name, thisWeek[site]?.value?.let(::formatKg) ?: "") }
        }
    }
    var unit by rememberSaveable {
        mutableStateOf(state.measurements.maxByOrNull { it.at }?.unit ?: LengthUnit.CM)
    }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var discarding by rememberSaveable { mutableStateOf(false) }
    val changed = sites.any { (values[it.name] ?: "") != (thisWeek[it]?.value?.let(::formatKg) ?: "") }
    BackHandler(enabled = changed && !discarding) { discarding = true }
    val last = state.siteProgress().associateBy { it.site }

    fun save() {
        val at = env.clock()
        val entries = sites.mapNotNull { site ->
            val text = values[site.name].orEmpty()
            if (text.isBlank()) return@mapNotNull null
            val value = parseDecimal(text) ?: run { error = "${site.title} isn't a number."; return }
            val earlier = thisWeek[site]
            BodyMeasurement(earlier?.id ?: newId(), earlier?.at ?: at, earlier?.date ?: today.toString(), site, value, unit)
        }
        if (entries.isEmpty()) { error = "Enter at least one measurement."; return }
        error = null
        env.runner.run(Action.SaveMeasurements(entries), onError = { error = it }, onSuccess = onBack)
    }

    ScreenScaffold(title = "Measure this week", subtitle = weekRange(week),
        onBack = { if (changed) discarding = true else onBack() }, compactWhenKeyboard = true) {
        ScrollBody {
            LengthUnitChips(unit) { unit = it }
            sites.forEach { site ->
                val previous = last[site]?.let { p ->
                    p.entries.lastOrNull { weekStartOf(it.localDate()) != week }
                }
                NumberField(
                    values[site.name].orEmpty(), { values[site.name] = it; error = null },
                    "${site.title} (${unit.symbol})",
                    Modifier.fillMaxWidth().testTag("measure-${site.name}"),
                    supportingText = listOfNotNull(
                        previous?.let { "Last: ${lengthText(it.value, it.unit)} · ${dayText(it.localDate())}" },
                        site.landmark
                    ).joinToString("\n")
                )
            }
            ErrorText(error)
            Button(onClick = ::save, enabled = !env.runner.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("measure-save")) { Text("Save measurements") }
        }
    }
    if (discarding) ConfirmDialog(
        title = "Discard these measurements?",
        text = "What you typed here isn't saved.",
        confirmLabel = "Discard",
        destructive = true,
        onDismiss = { discarding = false },
        onConfirm = { discarding = false; onBack() }
    )
}

private val stringMapSaver = androidx.compose.runtime.saveable.Saver<SnapshotStateMap<String, String>, List<String>>(
    save = { map -> map.flatMap { listOf(it.key, it.value) } },
    restore = { list -> mutableStateMapOf<String, String>().apply { list.chunked(2).forEach { put(it[0], it[1]) } } }
)

/** One site over time: a plain line through every measurement, no smoothing, then the dated list. */
@Composable
internal fun SiteScreen(env: AppEnv, site: BodySite, onBack: () -> Unit) {
    val progress = env.state.siteProgress().find { it.site == site }
    val today = localDateOf(env.clock())
    var range by rememberSaveable { mutableStateOf(ChartRange.ALL) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    ScreenScaffold(title = site.title, subtitle = site.landmark, onBack = onBack) {
        LazyBody {
            if (progress == null) {
                item(key = "empty") { Text("Not measured yet.") }
                return@LazyBody
            }
            item(key = "latest") { SiteSummary(progress, today) }
            if (progress.entries.size > 1) item(key = "chart") {
                val start = range.start(today, progress.entries.first().localDate())
                val points = progress.entries.filter { it.localDate() >= start }
                    .map { ChartPoint(it.localDate(), lengthIn(it.value, it.unit, progress.unit)) }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RangeChips(range, { range = it })
                    TrendChart(emptyList(), measuredSegments(points), start, today, { oneDecimal(it) }, unitLabel = progress.unit.symbol,
                        readout = { p ->
                            val before = points.lastOrNull { it.date < p.date }
                            lengthText(p.value, progress.unit) to dayText(p.date) +
                                (before?.let { " · ${signedText(p.value - it.value, progress.unit.symbol)} since ${dayText(it.date)}" } ?: "")
                        },
                        description = "${site.title}, ${range.label}: " + if (points.isEmpty()) "no measurements." else
                            "${lengthText(points.first().value, progress.unit)} to ${lengthText(points.last().value, progress.unit)} " +
                                "over ${countLabel(points.size, "measurement")}.")
                }
            }
            item(key = "all") { SectionHeader("All measurements") }
            val newestFirst = progress.entries.asReversed()
            itemsIndexed(newestFirst, key = { _, e -> e.id }) { i, entry ->
                val before = newestFirst.getOrNull(i + 1)
                Column {
                    if (i > 0) HorizontalDivider()
                    ListRow(
                        headline = lengthText(entry.value, entry.unit),
                        supporting = dayText(entry.localDate()) + (before?.let {
                            " · ${signedText(lengthIn(entry.value, entry.unit, progress.unit) -
                                lengthIn(it.value, it.unit, progress.unit), progress.unit.symbol)}"
                        } ?: ""),
                        onClick = { editing = entry.id },
                        trailing = { RebuildIcon(R.drawable.ic_pencil, contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    )
                }
            }
        }
    }
    env.state.measurements.find { it.id == editing }?.let { MeasurementDialog(env, it, onDismiss = { editing = null }) }
}

@Composable
private fun SiteSummary(progress: SiteProgress, today: LocalDate) {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer)
            .semantics(mergeDescendants = true) {}.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Latest · ${dayText(progress.latest.localDate())}", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(lengthText(progress.latest.value, progress.latest.unit), style = MaterialTheme.typography.headlineMedium)
        progress.sinceLast?.let { Text("${signedText(it, progress.unit.symbol)} since last", style = MaterialTheme.typography.bodyMedium) }
        progress.sinceFirst?.let {
            Text("${signedText(it, progress.unit.symbol)} since ${dayText(progress.entries.first().localDate())} · " +
                countLabel(progress.entries.size, "measurement"), style = MaterialTheme.typography.bodyMedium)
        }
        if (progress.entries.size == 1) Text("One measurement so far. The chart grows each week.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MeasurementDialog(env: AppEnv, entry: BodyMeasurement, onDismiss: () -> Unit) {
    var text by rememberSaveable(entry.id) { mutableStateOf(formatKg(entry.value)) }
    var unit by rememberSaveable(entry.id) { mutableStateOf(entry.unit) }
    var error by rememberSaveable(entry.id) { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable(entry.id) { mutableStateOf(false) }
    if (deleting) {
        ConfirmDialog(
            title = "Delete this measurement?",
            text = "${entry.site.title} ${lengthText(entry.value, entry.unit)} on ${dayText(entry.localDate())} is " +
                "removed. This can't be undone.",
            confirmLabel = "Delete", destructive = true, busy = env.runner.busy,
            onDismiss = { deleting = false },
            onConfirm = { env.runner.run(Action.DeleteMeasurement(entry.id), onSuccess = onDismiss) }
        )
        return
    }
    AdaptiveDialog(
        onDismissRequest = onDismiss,
        title = { Text("${entry.site.title} · ${dayText(entry.localDate())}") },
        text = {
            NumberField(text, { text = it; error = null }, "${entry.site.title} (${unit.symbol})", Modifier.fillMaxWidth(),
                isError = error != null, supportingText = error, imeAction = ImeAction.Done)
            LengthUnitChips(unit) { unit = it }
            TextButton(onClick = { deleting = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.heightIn(min = 48.dp)) { Text("Delete measurement") }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = parseDecimal(text) ?: run { error = "Enter a number."; return@TextButton }
                env.runner.run(Action.SaveMeasurements(listOf(entry.copy(value = value, unit = unit))),
                    onError = { error = it }, onSuccess = onDismiss)
            }, enabled = !env.runner.busy) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** "91.4 cm · Sun 18 Oct", then "−0.4 cm since last · −2.8 cm since 30 Aug" once there are two. */
private fun siteSupporting(site: SiteProgress): String {
    val changes = listOfNotNull(
        site.sinceLast?.let { "${signedText(it, site.unit.symbol)} since last" },
        site.sinceFirst?.let { "${signedText(it, site.unit.symbol)} since ${dayText(site.entries.first().localDate())}" }
    )
    return "${lengthText(site.latest.value, site.latest.unit)} · ${dayText(site.latest.localDate())}" +
        if (changes.isEmpty()) "" else "\n" + changes.joinToString(" · ")
}

// ---- Overview (History › Body) and Today's quiet rows -----------------------------------------------------------

/** History's Body tab: the week's weight average and every site's latest, each opening its own trend. */
@Composable
internal fun ColumnScope.BodyOverview(env: AppEnv, onWeight: () -> Unit, onMeasurements: () -> Unit, onMeasure: () -> Unit,
    onSite: (BodySite) -> Unit) {
    val state = env.state
    val unit = state.bodyweightUnit()
    val weeks = weightWeeks(state.bodyweights, unit)
    val today = localDateOf(env.clock())
    var logging by rememberSaveable { mutableStateOf(false) }
    ScrollBody {
        SectionHeader("Bodyweight")
        if (weeks.isEmpty()) {
            Text("No weigh-ins yet. Log one from Today or after a workout.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)) { WeightSummary(weeks, unit, today) }
            val shown = weeks.takeLast(12)
            if (shown.size < 2) Text("The trend line starts with a second week of weigh-ins.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else TrendChart(emptyList(), weeklySegments(shown), shown.first().start, today, { oneDecimal(it) },
                unitLabel = unit.symbol, readout = weekReadout(shown, unit),
                description = "Weekly average bodyweight, last ${countLabel(shown.size, "week")}: " +
                    "${averageText(shown.first().average, unit)} to ${averageText(shown.last().average, unit)}.")
            if (shown.size >= 2) Text("Weekly average · last ${countLabel(shown.size, "week")}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { logging = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                .testTag("body-log-weight")) { Text("Log weight") }
            if (weeks.isNotEmpty()) TextButton(onClick = onWeight, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                .testTag("body-weight-trend")) { Text("Trend and weigh-ins") }
        }
        SectionHeader("Measurements")
        val progress = state.siteProgress()
        if (progress.isEmpty()) Text("Measure your waist once a week to see it change.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            progress.forEachIndexed { i, site ->
                if (i > 0) HorizontalDivider()
                ListRow(
                    headline = site.site.title,
                    supporting = siteSupporting(site),
                    onClick = { onSite(site.site) },
                    modifier = Modifier.testTag("body-site-${site.site.name}"),
                    trailing = { RebuildIcon(R.drawable.ic_chevron_right, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.measurementDue(today)) OutlinedButton(onClick = onMeasure,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("body-measure")) { Text("Measure this week") }
            TextButton(onClick = onMeasurements, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                .testTag("body-measurements")) { Text("All measurements") }
        }
    }
    if (logging) BodyweightDialog(env, null, onDismiss = { logging = false })
}

/**
 * Today's body rows: the day's weigh-in (or a quiet way to log one) and, once a week, a reminder to measure. No
 * streaks or missed counts; skipping a day or a week is just an empty row.
 */
@Composable
internal fun ColumnScope.TodayBodyRows(env: AppEnv, onWeight: () -> Unit, onMeasure: () -> Unit) {
    val state = env.state
    val today = localDateOf(env.clock())
    val todays = state.weighInsOn(today).firstOrNull()
    var logging by rememberSaveable { mutableStateOf(false) }
    Column {
        ListRow(
            headline = "Bodyweight",
            supporting = todays?.let { "${massText(it.value, it.unit)} at ${formatTime(it.at)}" } ?: "Not logged today",
            onClick = onWeight,
            modifier = Modifier.testTag("today-bodyweight"),
            trailing = {
                TextButton(onClick = { logging = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("today-log-weight")) {
                    Text(if (todays == null) "Log" else "Add")
                }
            }
        )
        if (state.measurementDue(today)) {
            HorizontalDivider()
            ListRow(
                headline = "Measure this week",
                supporting = state.measurementSites.joinToString { it.title },
                onClick = onMeasure,
                modifier = Modifier.testTag("today-measure"),
                trailing = { RebuildIcon(R.drawable.ic_chevron_right, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            )
        }
    }
    if (logging) BodyweightDialog(env, null, onDismiss = { logging = false })
}
