package com.rebuild.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.rebuild.domain.DistanceEntry
import com.rebuild.domain.DistanceUnit
import com.rebuild.domain.DurationEntry
import com.rebuild.domain.Measure
import com.rebuild.domain.MeasuredGoal
import com.rebuild.domain.parseDistance
import com.rebuild.domain.parseDuration

/** The unit a new distance starts in: metres for carries, kilometres for cardio. Always visible and changeable. */
internal fun defaultDistanceUnit(measure: Measure): DistanceUnit =
    if (measure == Measure.CARDIO) DistanceUnit.KM else DistanceUnit.M

/**
 * A timed, distance or cardio goal's fields as typed. They stay text until saved, so nothing is rounded, converted or
 * lost on the way; they survive rotation and process recreation.
 */
@Stable
internal class GoalText(sets: String, minutes: String, seconds: String, distance: String, unit: DistanceUnit, rest: String) {
    var sets by mutableStateOf(sets)
    var minutes by mutableStateOf(minutes)
    var seconds by mutableStateOf(seconds)
    var distance by mutableStateOf(distance)
    var unit by mutableStateOf(unit)
    var rest by mutableStateOf(rest)

    companion object {
        val Saver = listSaver<GoalText, String>(
            save = { listOf(it.sets, it.minutes, it.seconds, it.distance, it.unit.name, it.rest) },
            restore = { GoalText(it[0], it[1], it[2], it[3], DistanceUnit.valueOf(it[4]), it[5]) }
        )

        /** The fields for an existing goal of [measure], or blank ones in the usual unit. */
        fun of(measure: Measure, sets: Int?, goal: MeasuredGoal, rest: Int?): GoalText {
            val time = if (measure == Measure.CARDIO) goal.totalSeconds else goal.effortSeconds
            val length = if (measure == Measure.CARDIO) goal.totalDistance else goal.effortDistance
            return GoalText(
                if (measure == Measure.CARDIO) "" else sets?.toString().orEmpty(),
                time?.let { (it / 60).toString() }.orEmpty(),
                time?.let { (it % 60).toString() }.orEmpty(),
                length?.let(::distanceInput).orEmpty(),
                length?.unit ?: defaultDistanceUnit(measure),
                rest?.toString().orEmpty()
            )
        }
    }
}

@Composable
internal fun rememberGoalText(vararg keys: Any?, init: () -> GoalText): GoalText =
    rememberSaveable(*keys, saver = GoalText.Saver, init = init)

/** A measured goal as entered: its count of sets (never for cardio), the goal itself and the rest. */
internal data class ParsedGoal(val sets: Int?, val goal: MeasuredGoal, val rest: Int)

internal fun durationError(entry: DurationEntry, what: String = "time"): String? = when (entry) {
    DurationEntry.Blank, is DurationEntry.Valid -> null
    DurationEntry.NotANumber -> "Enter the $what in whole minutes and seconds."
    DurationEntry.BadSeconds -> "Seconds go from 0 to 59."
    DurationEntry.OutOfRange -> "Enter a $what from 0:01 up to 24 hours."
}

internal fun distanceError(entry: DistanceEntry, unit: DistanceUnit): String? = when (entry) {
    DistanceEntry.Blank, is DistanceEntry.Valid -> null
    DistanceEntry.NotANumber -> "Distance isn't a number."
    DistanceEntry.TooPrecise -> "Use at most three decimals."
    DistanceEntry.OutOfRange -> "Enter a distance above 0, up to ${maxDistanceText(unit)}."
}

internal fun maxDistanceText(unit: DistanceUnit): String = when (unit) {
    DistanceUnit.M -> "1000000 m (1000 km)"
    DistanceUnit.KM -> "1000 km"
    DistanceUnit.MI -> "1000 mi"
}

/**
 * Checks a measured goal's fields. Timed and distance sets need a count and a per-set minimum together; cardio needs a
 * total time, a total distance or both. With [optional] every part may stay blank. Nothing is ever inferred from what
 * was performed. Returns an error, or the goal.
 */
internal fun parseMeasuredGoal(measure: Measure, text: GoalText, optional: Boolean): Pair<String?, ParsedGoal?> {
    val rest = parseRest(text.rest) ?: return "Rest must be between 1 and 3600 seconds." to null
    when (measure) {
        Measure.REPS -> return "This exercise has a rep range instead." to null
        Measure.TIMED, Measure.DISTANCE -> {
            val nouns = effortNoun(measure) + "s"
            val what = if (measure == Measure.TIMED) "time" else "distance"
            val minimumBlank = if (measure == Measure.TIMED) text.minutes.isBlank() && text.seconds.isBlank()
                else text.distance.isBlank()
            if (text.sets.isBlank() && minimumBlank) {
                return if (optional) null to ParsedGoal(null, MeasuredGoal(), rest)
                else "Enter how many $nouns and the minimum $what for each." to null
            }
            val sets = parseCount(text.sets, TARGET_SETS_RANGE)
                ?: return (if (text.sets.isBlank()) "Enter how many $nouns" else
                    "Enter $nouns from 1 to ${TARGET_SETS_RANGE.last}") +
                    (if (optional) ", or leave the goal blank." else ".") to null
            if (minimumBlank) {
                return "Enter the minimum $what each one needs" +
                    (if (optional) ", or leave the goal blank." else ".") to null
            }
            return if (measure == Measure.TIMED) {
                when (val time = parseDuration(text.minutes, text.seconds)) {
                    is DurationEntry.Valid -> null to ParsedGoal(sets, MeasuredGoal(effortSeconds = time.seconds), rest)
                    else -> durationError(time, "minimum time") to null
                }
            } else {
                when (val length = parseDistance(text.distance, text.unit)) {
                    is DistanceEntry.Valid -> null to ParsedGoal(sets, MeasuredGoal(effortDistance = length.distance), rest)
                    else -> distanceError(length, text.unit) to null
                }
            }
        }
        Measure.CARDIO -> {
            val time = parseDuration(text.minutes, text.seconds)
            val length = parseDistance(text.distance, text.unit)
            durationError(time, "total time")?.let { return it to null }
            distanceError(length, text.unit)?.let { return it to null }
            val seconds = (time as? DurationEntry.Valid)?.seconds
            val distance = (length as? DistanceEntry.Valid)?.distance
            if (!optional && seconds == null && distance == null) {
                return "Enter a total time, a total distance or both." to null
            }
            return null to ParsedGoal(null, MeasuredGoal(totalSeconds = seconds, totalDistance = distance), rest)
        }
    }
}

/**
 * The goal fields of a timed, distance or cardio exercise: how many sets and the minimum each needs, or cardio's totals;
 * then the rest. The rep-based fields stay in [GoalFields].
 */
@Composable
internal fun MeasuredGoalFields(
    measure: Measure,
    text: GoalText,
    optional: Boolean,
    restHint: String?,
    onDone: () -> Unit,
    enabled: Boolean = true
) {
    val blank = if (optional) " (optional)" else ""
    when (measure) {
        Measure.TIMED, Measure.DISTANCE -> {
            NumberField(text.sets, { text.sets = it }, effortNoun(measure).replaceFirstChar { it.uppercase() } + "s$blank",
                Modifier.fillMaxWidth().testTag("goal-sets"), decimal = false)
            if (measure == Measure.TIMED) {
                DurationFields("Minimum time for each", text.minutes, { text.minutes = it }, text.seconds,
                    { text.seconds = it }, "goal")
            } else {
                DistanceField("Minimum distance for each", text.distance, { text.distance = it }, text.unit,
                    { text.unit = it }, "goal-distance", enabled)
            }
        }
        Measure.CARDIO -> {
            Text(
                if (optional) "A total time, a total distance, both, or leave both blank."
                else "A total time, a total distance or both. Your efforts add up toward it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            DurationFields("Total time$blank", text.minutes, { text.minutes = it }, text.seconds, { text.seconds = it },
                "goal")
            DistanceField("Total distance$blank", text.distance, { text.distance = it }, text.unit, { text.unit = it },
                "goal-distance", enabled)
        }
        Measure.REPS -> Unit
    }
    NumberField(text.rest, { text.rest = it },
        if (measure == Measure.CARDIO) "Rest when you start it (seconds)" else "Rest between sets (seconds)",
        Modifier.fillMaxWidth().testTag("goal-rest"), decimal = false, imeAction = ImeAction.Done,
        supportingText = restHint, onDone = onDone)
}

/** Minutes and seconds, side by side, or stacked with large text. */
@Composable
internal fun DurationFields(
    label: String,
    minutes: String,
    onMinutes: (String) -> Unit,
    seconds: String,
    onSeconds: (String) -> Unit,
    tagPrefix: String,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {}
) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    val stacked = LocalDensity.current.fontScale > 1.3f
    @Composable fun Minutes(modifier: Modifier) = NumberField(minutes, onMinutes, "Minutes",
        modifier.testTag("$tagPrefix-minutes"), decimal = false, isError = isError)
    @Composable fun Seconds(modifier: Modifier) = NumberField(seconds, onSeconds, "Seconds",
        modifier.testTag("$tagPrefix-seconds"), decimal = false, isError = isError, imeAction = imeAction, onDone = onDone)
    if (stacked) {
        Minutes(Modifier.fillMaxWidth())
        Seconds(Modifier.fillMaxWidth())
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Minutes(Modifier.weight(1f))
            Seconds(Modifier.weight(1f))
        }
    }
}

/**
 * A distance over its unit, chosen explicitly. Changing the unit keeps the number; nothing is converted. Always
 * stacked, like History's effort dialog: beside the three-way choice, a goal dialog left the field a sliver.
 */
@Composable
internal fun DistanceField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    unit: DistanceUnit,
    onUnit: (DistanceUnit) -> Unit,
    tag: String,
    enabled: Boolean = true,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(value, onValue, "$label (${unit.symbol})", Modifier.fillMaxWidth().testTag(tag), decimal = true,
            isError = isError, imeAction = imeAction, onDone = onDone)
        DistanceUnitChoice(unit, onUnit, enabled)
    }
}

/** Metres, kilometres or miles, as a three-way choice; each says its full name to a screen reader. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DistanceUnitChoice(unit: DistanceUnit, onUnit: (DistanceUnit) -> Unit, enabled: Boolean = true) {
    SingleChoiceSegmentedButtonRow {
        DistanceUnit.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = unit == option,
                onClick = { onUnit(option) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, DistanceUnit.entries.size),
                icon = {},
                label = { Text(option.symbol, maxLines = 1) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .widthIn(min = 52.dp)
                    .semantics { contentDescription = unitName(option, plural = true) }
                    .testTag("distance-unit-${option.name}")
            )
        }
    }
}
