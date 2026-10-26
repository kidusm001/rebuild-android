package com.rebuild.domain

import java.time.LocalDate

/** [value] in [to], converted exactly for display only; stored entries keep the unit they were entered in. */
fun massIn(value: Double, from: MassUnit, to: MassUnit): Double = when {
    from == to -> value
    to == MassUnit.KG -> value * MassUnit.KG_PER_LB
    else -> value / MassUnit.KG_PER_LB
}

fun lengthIn(value: Double, from: LengthUnit, to: LengthUnit): Double = when {
    from == to -> value
    to == LengthUnit.CM -> value * LengthUnit.CM_PER_IN
    else -> value / LengthUnit.CM_PER_IN
}

fun BodyweightEntry.localDate(): LocalDate = LocalDate.parse(date)
fun BodyMeasurement.localDate(): LocalDate = LocalDate.parse(date)

/** The unit weights are shown in: the unit of the latest weigh-in, so the screen reads the way it was last entered. */
fun AppState.bodyweightUnit(): MassUnit = bodyweights.maxByOrNull { it.at }?.unit ?: liftingUnit

/**
 * One Monday–Sunday week of weigh-ins. [average] is the mean of each day's mean, so weighing twice on one day counts
 * that day once; it is in the display unit. [entries] keeps every reading as entered.
 */
data class WeightWeek(val start: LocalDate, val average: Double, val days: Int, val entries: List<BodyweightEntry>)

fun weightWeeks(entries: List<BodyweightEntry>, unit: MassUnit): List<WeightWeek> =
    entries.groupBy { weekStartOf(it.localDate()) }.toSortedMap().map { (start, week) ->
        val dayMeans = week.groupBy { it.date }.values.map { day -> day.map { massIn(it.value, it.unit, unit) }.average() }
        WeightWeek(start, dayMeans.average(), dayMeans.size, week.sortedBy { it.at })
    }

/**
 * The change between this week's average and the average four weeks earlier, and the weekly rate it implies. Only
 * when both weeks have weigh-ins: nothing is interpolated across a missing week.
 */
data class WeightChange(val from: WeightWeek, val to: WeightWeek, val total: Double, val weeks: Int) {
    val perWeek: Double get() = total / weeks
}

fun weightChange(weeks: List<WeightWeek>, spanWeeks: Int = 4): WeightChange? {
    val latest = weeks.lastOrNull() ?: return null
    val earlier = weeks.find { it.start == latest.start.minusWeeks(spanWeeks.toLong()) } ?: return null
    return WeightChange(earlier, latest, latest.average - earlier.average, spanWeeks)
}

/** One site's measurements, oldest first, with the differences a fat-loss check reads, in the display unit. */
data class SiteProgress(
    val site: BodySite,
    val unit: LengthUnit,
    val entries: List<BodyMeasurement>
) {
    val latest: BodyMeasurement get() = entries.last()
    private fun inUnit(entry: BodyMeasurement) = lengthIn(entry.value, entry.unit, unit)
    val latestValue: Double get() = inUnit(latest)
    /** Since the measurement before the latest one; null with a single measurement. */
    val sinceLast: Double? get() = entries.getOrNull(entries.size - 2)?.let { latestValue - inUnit(it) }
    /** Since the first measurement of this site; null with a single measurement. */
    val sinceFirst: Double? get() = if (entries.size > 1) latestValue - inUnit(entries.first()) else null
}

/** Every measured site in the weekly form's order, then any other site that has measurements, each in its own unit. */
fun AppState.siteProgress(): List<SiteProgress> {
    val bySite = measurements.groupBy { it.site }
    val order = measurementSites + bySite.keys.filterNot { it in measurementSites }.sortedBy { it.ordinal }
    return order.mapNotNull { site ->
        val entries = bySite[site]?.sortedBy { it.at } ?: return@mapNotNull null
        SiteProgress(site, entries.last().unit, entries)
    }
}

/** The weigh-ins logged on [date], newest first. */
fun AppState.weighInsOn(date: LocalDate): List<BodyweightEntry> =
    bodyweights.filter { it.date == date.toString() }.sortedByDescending { it.at }

/** True when no site of the weekly form was measured in [date]'s week yet. */
fun AppState.measurementDue(date: LocalDate): Boolean {
    val week = weekStartOf(date)
    return measurements.none { weekStartOf(it.localDate()) == week && it.site in measurementSites }
}
