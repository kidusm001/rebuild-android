package com.rebuild.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

fun localDateOf(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

/** The Monday that starts [date]'s week. Weeks run Monday–Sunday by calendar date, whatever the locale or DST. */
fun weekStartOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** The local date the workout started on, as recorded at start. Records without one use [zone]. */
fun Workout.startLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: localDateOf(startedAt, zone)

/** A workout belongs to the week it started in, even if it finishes after midnight on Monday. */
fun Workout.weekStart(zone: ZoneId = ZoneId.systemDefault()): LocalDate = weekStartOf(startLocalDate(zone))

/** Finished with real work: a working set (a finished drop or rest-pause set counts as one) or a measured effort. */
fun Workout.hasWorkingEffort(): Boolean = finishedAt != null && performedCount() > 0

/** Every saved target was met with real work, so the routine is done for its week without any choice. */
fun Workout.completesRoutine(): Boolean = hasWorkingEffort() && targetsMet()

/**
 * Whether this workout is its routine's one for its week. A finished workout with real working effort is always
 * required first; then either every saved target of the routine's own exercises was met, or the user explicitly
 * chose to count this partial workout (its working sets may be on exercises added that day). Added exercises never
 * complete a routine by themselves, and an extra workout belongs to no routine and never counts.
 */
fun Workout.countsForWeek(): Boolean = templateId != null && hasWorkingEffort() && (targetsMet() || countedForWeek)

enum class RoutineStatus { DONE, IN_PROGRESS, PARTIAL, NOT_DONE }

data class RoutineWeek(
    /** The occurrence: an original routine or a scheduled copy. Two copies of one routine are two rows. */
    val template: ScheduledRoutine,
    val status: RoutineStatus,
    /** The first workout that made this routine done this week. Repeats never add a second credit. */
    val doneBy: Workout?
)

data class WeekChecklist(
    /** Monday 00:00 local time; the week ends at the next Monday 00:00. */
    val start: LocalDate,
    val routines: List<RoutineWeek>,
    /** Finished workouts this week from routines that aren't in the current phase's checklist. */
    val other: List<Workout>,
    /** Finished extra workouts this week: real training outside the plan that never counts on the checklist. */
    val extras: List<Workout> = emptyList()
) {
    val end: LocalDate get() = start.plusDays(6)
    val done: Int get() = routines.count { it.status == RoutineStatus.DONE }
    val complete: Boolean get() = routines.isNotEmpty() && routines.all { it.status == RoutineStatus.DONE }
    fun statusOf(templateId: String): RoutineStatus? = routines.find { it.template.id == templateId }?.status
}

/**
 * This week's checklist for the current phase, derived only from dated workouts and explicit credit. Occurrences are
 * identified by ID, never by name, and listed in plan (weekday) order; a workout only ever counts for the occurrence
 * it was started from. Nothing is stored or reset, so schedule changes apply at once.
 */
fun AppState.weekChecklist(today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): WeekChecklist? {
    val phase = phase ?: return null
    val start = weekStartOf(today)
    val week = workouts.filter { it.weekStart(zone) == start }
    val templates = scheduledRoutines(phase)
    val routines = templates.map { template ->
        val mine = week.filter { it.templateId == template.id }
        val doneBy = mine.filter { it.countsForWeek() }.minByOrNull { it.startedAt }
        val status = when {
            doneBy != null -> RoutineStatus.DONE
            mine.any { it.id == activeWorkoutId && it.finishedAt == null } -> RoutineStatus.IN_PROGRESS
            mine.any { it.hasWorkingEffort() } -> RoutineStatus.PARTIAL
            else -> RoutineStatus.NOT_DONE
        }
        RoutineWeek(template, status, doneBy)
    }
    val listed = templates.map { it.id }.toSet()
    val other = week.filter { !it.extra && it.templateId !in listed && it.hasWorkingEffort() }.sortedBy { it.startedAt }
    val extras = week.filter { it.extra && it.hasWorkingEffort() }.sortedBy { it.startedAt }
    return WeekChecklist(start, routines, other, extras)
}

enum class TodayKind {
    /** Every routine in the checklist is done: rest, or repeat one by choice. */
    WEEK_COMPLETE,
    /** A routine scheduled today isn't done yet; with several, the first unfinished one in plan order. */
    SCHEDULED,
    /** Today's routines are all done this week; [TodayPlan.suggested] is the first unfinished one in plan order. */
    NEXT_UNFINISHED,
    /** No routine is scheduled today: an honest rest day, with unfinished routines only as an optional choice. */
    REST
}

data class TodayPlan(val kind: TodayKind, val scheduled: ScheduledRoutine?, val suggested: ScheduledRoutine?)

/** What Today offers when no workout is active. It never starts anything or counts anything by itself. */
fun AppState.todayPlan(today: LocalDate, checklist: WeekChecklist): TodayPlan {
    val todays = checklist.routines.map { it.template }.filter { it.weekday == today.dayOfWeek.value }
    val unfinished = checklist.routines.firstOrNull {
        it.status == RoutineStatus.NOT_DONE || it.status == RoutineStatus.PARTIAL
    }?.template
    val todayUnfinished = todays.firstOrNull { checklist.statusOf(it.id) != RoutineStatus.DONE }
    return when {
        checklist.complete -> TodayPlan(TodayKind.WEEK_COMPLETE, todays.firstOrNull(), null)
        todayUnfinished != null -> TodayPlan(TodayKind.SCHEDULED, todayUnfinished, todayUnfinished)
        todays.isNotEmpty() -> TodayPlan(TodayKind.NEXT_UNFINISHED, todays.first(), unfinished)
        else -> TodayPlan(TodayKind.REST, null, unfinished)
    }
}
