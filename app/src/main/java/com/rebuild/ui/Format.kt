package com.rebuild.ui

import com.rebuild.domain.Exercise
import com.rebuild.domain.LoadingType
import com.rebuild.domain.Measure
import com.rebuild.domain.LoadUnit
import com.rebuild.domain.LoggedSet
import com.rebuild.domain.Phase
import com.rebuild.domain.WorkoutExercise
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

internal fun newId(): String = UUID.randomUUID().toString()

internal fun countLabel(count: Int, singular: String): String = "$count $singular${if (count == 1) "" else "s"}"

internal fun formatKg(kg: Double): String =
    DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.getDefault())).format(kg)

/** Accepts both "." and "," as decimal separators. Blank yields null. */
internal fun parseDecimal(text: String): Double? =
    text.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()

internal fun kgFieldLabel(exercise: Exercise): String = when (exercise.loading) {
    LoadingType.EXTERNAL -> if (exercise.perDumbbell) "kg per dumbbell" else "kg"
    LoadingType.BODYWEIGHT -> "kg (not used)"
    LoadingType.ASSISTANCE -> "Assistance kg"
}

/** How the exercise is measured and loaded: unchanged for sets of reps, prefixed for timed and distance work. */
internal fun loadingDescription(exercise: Exercise, unit: LoadUnit = LoadUnit.KG): String {
    val symbol = unit.massSymbol()
    val load = when {
        unit == LoadUnit.PLATES -> "External load · plates per stack"
        else -> when (exercise.loading) {
            LoadingType.EXTERNAL ->
                if (exercise.perDumbbell) "External load · $symbol per dumbbell" else "External load · $symbol total"
            LoadingType.BODYWEIGHT -> "Bodyweight · no weight recorded"
            LoadingType.ASSISTANCE -> "Assisted · $symbol is machine assistance (less is harder)"
        }
    }
    return when (exercise.measure) {
        Measure.REPS -> load
        Measure.TIMED -> "Timed · " + load.replaceFirstChar { it.lowercase() }
        Measure.DISTANCE -> "Distance · " + load.replaceFirstChar { it.lowercase() }
        Measure.CARDIO -> "Cardio · time, distance or both · no load"
    }
}

internal fun loadingShort(exercise: Exercise): String {
    val load = when (exercise.loading) {
        LoadingType.EXTERNAL -> if (exercise.perDumbbell) "per dumbbell" else "external"
        LoadingType.BODYWEIGHT -> "bodyweight"
        LoadingType.ASSISTANCE -> "assisted"
    }
    return when (exercise.measure) {
        Measure.REPS -> load
        Measure.TIMED -> "timed · $load"
        Measure.DISTANCE -> "distance · $load"
        Measure.CARDIO -> "cardio"
    }
}

internal fun recordedLoadingDescription(exercise: WorkoutExercise): String {
    val units = (exercise.sets.map { it.loadUnit } + exercise.efforts.map { it.loadUnit }).distinct()
    return if (units.size > 1) {
        val load = units.sortedBy { it.ordinal }.joinToString(" and ") { unitName(it) } + ", recorded separately"
        when (exercise.exercise.measure) {
            Measure.TIMED -> "Timed · external load · $load"
            Measure.DISTANCE -> "Distance · external load · $load"
            else -> "External load · $load"
        }
    } else loadingDescription(exercise.exercise, units.singleOrNull() ?: exercise.entryLoadUnit)
}

/** "kg" or "lb"; plates have no mass symbol and read as kilograms here only where a mass is expected. */
internal fun LoadUnit.massSymbol(): String = if (this == LoadUnit.LB) "lb" else "kg"

/** The unit as a short name in sentences and choices: "kg", "lb", "plates per stack". */
internal fun unitName(unit: LoadUnit): String = when (unit) {
    LoadUnit.KG -> "kg"
    LoadUnit.LB -> "lb"
    LoadUnit.PLATES -> "plates per stack"
}

/** A mass exactly as entered, labeled in its own unit: "40 kg/DB", "90 lb", "20 lb assist". */
internal fun massText(amount: Double?, unit: LoadUnit, exercise: Exercise): String {
    val symbol = unit.massSymbol()
    return when (exercise.loading) {
        LoadingType.EXTERNAL -> when {
            amount == null -> "$symbol not recorded"
            exercise.perDumbbell -> "${formatKg(amount)} $symbol/DB"
            else -> "${formatKg(amount)} $symbol"
        }
        LoadingType.BODYWEIGHT -> "Bodyweight"
        LoadingType.ASSISTANCE -> if (amount == null) "unassisted" else "${formatKg(amount)} $symbol assist"
    }
}

/** Any load as entered: a mass in kg or lb, or a whole plate count per stack. */
internal fun loadText(amount: Double?, unit: LoadUnit, plates: Int?, exercise: Exercise): String =
    if (unit == LoadUnit.PLATES) "$plates plates/stack" else massText(amount, unit, exercise)

internal fun kgText(kg: Double?, exercise: Exercise): String = massText(kg, LoadUnit.KG, exercise)

/** Spoken form of a load line: screen readers say "pounds" in full rather than guessing at "lb". */
internal fun String.spokenUnits(): String = replace(Regex("""(\d) lb(/DB)?\b""")) {
    it.groupValues[1] + " pounds" + if (it.groupValues[2].isNotEmpty()) " per dumbbell" else ""
}

internal fun setText(set: LoggedSet, exercise: Exercise): String {
    val rir = set.rir?.let { " @ ${formatKg(it)} RIR" } ?: ""
    return "${loadText(set.amount, set.loadUnit, set.plates, exercise)} × ${set.reps}$rir"
}

internal fun formatRest(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** A full date in the app's one date style, day before month ("20 Oct 2026"), like "Tue 20 Oct" elsewhere. */
internal fun formatDate(millis: Long): String =
    DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.getDefault()).format(
        java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()))

internal fun formatTime(millis: Long): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

internal fun weekdayOf(millis: Long): Int =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).dayOfWeek.value

internal fun weekdayName(weekday: Int, style: TextStyle = TextStyle.FULL): String =
    if (weekday in 1..7) DayOfWeek.of(weekday).getDisplayName(style, Locale.getDefault()) else "Unscheduled"

internal fun phasePrescription(phase: Phase): String = when (phase) {
    Phase.INTRO_ONE -> "4 days a week · 1 working set per exercise · about 4 reps in reserve"
    Phase.INTRO_TWO -> "4 days a week · 2 working sets per exercise · about 3 reps in reserve"
    Phase.FIVE_TWO -> "5 days a week · 2 working sets per exercise · about 3 reps in reserve"
    Phase.BASELINE -> "5 days a week · baseline sets (66 weekly) · about 2 reps in reserve"
}

internal fun phaseGuidance(phase: Phase): String = when (phase) {
    Phase.INTRO_ONE -> "Choose this after a long break or if you are unsure. Every set should feel comfortably submaximal."
    Phase.INTRO_TWO -> "Choose this once single sets feel easy and you recover well between sessions."
    Phase.FIVE_TWO -> "Choose this when four days are routine and you are ready for the fifth day."
    Phase.BASELINE -> "Choose this only after gradually adding 2–4 weekly sets at a time toward the baseline."
}

internal fun phaseShort(phase: Phase): String = when (phase) {
    Phase.INTRO_ONE -> "Intro 1"
    Phase.INTRO_TWO -> "Intro 2"
    Phase.FIVE_TWO -> "Five days"
    Phase.BASELINE -> "Baseline"
}
// Mirrors the repository/backup codec limits so every valid stored value can be shown and edited.
internal const val MAX_NOTE_CHARS = 4096
internal const val MAX_KG = 10_000.0
internal const val MAX_LB = 22_000.0
internal const val MAX_REPS = 1000
internal val REST_SECONDS_RANGE = 1..3600
internal val TARGET_SETS_RANGE = 1..1000
