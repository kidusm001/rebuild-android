package com.rebuild.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Where the workout opens: a set in progress, else the stopwatch's exercise, else the first one short of its target. */
fun opensAt(state: AppState, workout: Workout): WorkoutExercise? =
    workout.openGroupExercise()
        ?: state.stopwatch?.takeIf { it.workoutId == workout.id }
            ?.let { watch -> workout.exercises.find { it.id == watch.exerciseId } }
        ?: workout.resumeExercise()

/** Layout tier chosen from the launcher's cell bounds and the user's font scale. */
enum class WidgetSize { STRIP, COMPACT, STANDARD, EXPANDED }

/**
 * Picks a layout tier for the widget's bounds. At font scale above 130%, Expanded steps down to Standard so rows
 * never clip inside fixed launcher cells.
 */
fun widgetSizeFor(widthDp: Int, heightDp: Int, fontScale: Float = 1f): WidgetSize = when {
    heightDp < 100 -> WidgetSize.STRIP
    widthDp < 220 -> WidgetSize.COMPACT
    heightDp >= 255 && fontScale <= 1.3f -> WidgetSize.EXPANDED
    else -> WidgetSize.STANDARD
}

/**
 * How many 28 dp list rows fit a [heightDp]-tall 4-wide widget under a header [headerDp] tall, with [footerLines] quiet
 * lines beneath (each 18 dp), at [fontScale]. Launchers give a 4x2 cell anywhere from about 160 to 230 dp, so the list
 * fills what it is given instead of leaving a gap; it never shows fewer than one row or more than seven.
 */
fun widgetListRows(heightDp: Int, fontScale: Float, headerDp: Int = 46, footerLines: Int = 1): Int {
    // Rows carry padding that doesn't grow with text, so they grow by about three quarters of the font scale.
    val scale = (fontScale * 0.75f).coerceAtLeast(1f)
    val usable = heightDp - 28 - (headerDp + footerLines * 18) * scale
    return (usable / (28 * scale)).toInt().coerceIn(1, 7)
}

/**
 * How many 24 dp rows fit a [heightDp]-tall 2x2 under its ~48 dp header, after [belowDp] of other content beneath
 * them (the Resume pill, or a footer line), at [fontScale]. Zero when none fit: a short 2x2 shows its header and
 * Resume alone rather than clipping rows.
 */
fun widgetCompactRows(heightDp: Int, fontScale: Float, belowDp: Int): Int {
    val scale = (fontScale * 0.75f).coerceAtLeast(1f)
    val usable = heightDp - 28 - (48 + belowDp) * scale
    return (usable / (24 * scale)).toInt().coerceIn(0, 3)
}

/**
 * The [count] consecutive indices of a [size]-long list to show so [focus] stays visible, with one row of context
 * above it when at least two rows fit: rows already done scroll away, the current one never does.
 */
fun widgetWindow(size: Int, focus: Int, count: Int): IntRange {
    if (count <= 0 || size <= 0) return IntRange.EMPTY
    if (size <= count) return 0 until size
    // One row of context above the current exercise only when there is room for both.
    val context = if (count > 1) 1 else 0
    val start = (focus - context).coerceIn(0, size - count)
    return start until start + count
}

/** Epoch millis of the next local midnight after [nowMillis] in [zone], DST-safe. */
fun nextMidnightMillis(nowMillis: Long, zone: ZoneId): Long =
    localDateOf(nowMillis, zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

enum class WidgetDestination { TODAY, WORKOUT }

/**
 * What leads a widget row: an exercise's order number (pending or current), a done check, or a weekday. A [SECTION]
 * row is a small overline heading ("Up next · Upper B · 7 exercises") and a [PLAIN] row a line of text; neither has a
 * mark.
 */
enum class WidgetMark { NUMBER, CURRENT, DONE, DAY, SECTION, PLAIN }

/**
 * One line of the widget's list: today's planned exercises, the workout's exercises, or the week's routines.
 * [trailing] is a short target or count ("2 × 6–8", "1/2", "Done"); [muted] rows are finished or skipped.
 */
data class WidgetRow(
    val mark: WidgetMark,
    val markText: String,
    val name: String,
    val trailing: String,
    val muted: Boolean = false,
    /** Today's routine in the week list, set in bold. */
    val today: Boolean = false,
    /** A week row for a day already gone, the first to give way when the card needs room. */
    val past: Boolean = false,
    val spoken: String = "$name, $trailing"
)

data class WidgetStopwatch(
    val exerciseName: String,
    /** True only when the running stopwatch's monotonic reading is positively verified on this boot/process. */
    val liveClock: Boolean,
    /** Elapsed-realtime base (`runningSince - elapsedMs`) for a count-up Chronometer when [liveClock] is true. */
    val chronometerBaseMs: Long?,
    /** Static description used when paused, stopped, untrusted, or for TalkBack. */
    val statusLine: String
)

/**
 * Pure, equality-comparable view model for Rebuild's home screen widget.
 * Never carries raw `now` or `revision`, so state collectors dedupe naturally and skip redundant binder updates.
 */
sealed interface WidgetModel {
    data object Setup : WidgetModel
    data object Unavailable : WidgetModel

    data class Resting(
        val workoutName: String,
        val timerId: String,
        val deadline: Long,
        val shortPause: Boolean,
        /** True only when exact alarms are allowed, so the countdown is refreshed reliably at 0:00. */
        val liveCountdown: Boolean,
        /** "Rest" or "Short pause". */
        val label: String,
        /** The exercise the next set belongs to, or a headline such as "All sets done". */
        val nextTitle: String,
        /** What comes next on that exercise: "Set 2 of 2", "Next exercise", "More reps on this set". */
        val setLine: String,
        /** One-line cue for the strip and TalkBack ("Next: set 2 of 2", "Next: Pull"). */
        val nextLine: String,
        /** The same-unit set from the last comparable workout at the next set's position; never a target. */
        val lastTimeSet: LoggedSet?,
        val lastTimeExercise: Exercise?,
        val endTimeText: String,
        val rows: List<WidgetRow>,
        val currentIndex: Int,
        val doneExercises: Int,
        val totalExercises: Int
    ) : WidgetModel

    data class Active(
        val workoutName: String,
        /** "started 6:12 PM", "started Tue 6:12 PM" or "outside the plan". */
        val startedLine: String,
        val startedSpoken: String,
        val restOver: Boolean,
        val restOverLabel: String?,
        val restOverNext: String?,
        val loggedSummary: String,
        val opensName: String?,
        val openSetLabel: String?,
        val stopwatch: WidgetStopwatch?,
        val rows: List<WidgetRow>,
        val currentIndex: Int,
        val doneExercises: Int,
        val totalExercises: Int
    ) : WidgetModel

    data class Idle(
        val date: LocalDate,
        /** "Wed 25 Nov". */
        val dateLabel: String,
        val dayFull: String,
        val kind: TodayKind,
        /** Today's routine name, "Rest day", "Push done" or "Week complete". */
        val title: String,
        /** One quiet line: today's planned size, what is next, or why nothing is listed. */
        val detail: String,
        /** Partial, also-today or optional notes; null when there is nothing more to say. */
        val note: String?,
        val stripLine: String,
        /** Today's planned exercises on a scheduled day, else this week's routines. */
        val rows: List<WidgetRow>,
        val rowsAreExercises: Boolean,
        val weekDone: Int,
        val weekTotal: Int,
        /**
         * What fills the card under a week list: the next workout's heading and planned exercises, or on a complete week
         * a quiet recap of the last one. Shown whole or not at all; see [extraMinRows].
         */
        val extra: List<WidgetRow> = emptyList(),
        /** Rows of [extra] that must fit for it to show at all: a heading is never shown on its own. */
        val extraMinRows: Int = 0
    ) : WidgetModel {
        /** "1 of 2 this week", or null without a weekly plan. */
        val weekLine: String? get() = if (weekTotal > 0) "$weekDone${NBSP}of$NBSP$weekTotal this${NBSP}week" else null
    }
}

fun WidgetModel.destination(): WidgetDestination = when (this) {
    is WidgetModel.Active, is WidgetModel.Resting -> WidgetDestination.WORKOUT
    WidgetModel.Setup, WidgetModel.Unavailable, is WidgetModel.Idle -> WidgetDestination.TODAY
}

/** Calm, complete TalkBack description with no ticking seconds or middle-dot punctuation. */
fun WidgetModel.spokenSummary(): String = when (this) {
    WidgetModel.Setup -> "Rebuild. Finish setup. Choose your return phase to see today's workout. Opens Rebuild."
    WidgetModel.Unavailable -> "Rebuild. Open Rebuild."
    is WidgetModel.Resting -> {
        val action = if (shortPause) "Short pause" else "Resting"
        val progress = if (totalExercises > 0) " $doneExercises of $totalExercises exercises done." else ""
        "Rebuild. $workoutName. $action until $endTimeText. ${nextTitle.spokenSeparators()}, " +
            "${setLine.spokenSeparators().replaceFirstChar { it.lowercase() }}.$progress Opens your workout."
    }
    is WidgetModel.Active -> {
        val parts = mutableListOf("Rebuild.", "$workoutName, $startedSpoken.")
        if (restOver && restOverNext != null) parts += "${restOverLabel ?: "Rest over"}. ${restOverNext.spokenSeparators()}."
        if (loggedSummary.isNotBlank()) parts += "${loggedSummary.spokenSeparators()}."
        if (stopwatch != null) {
            parts += "${stopwatch.statusLine.spokenSeparators()}."
        } else if (opensName != null) {
            val openSet = openSetLabel?.let { ", $it" } ?: ""
            parts += "Resume opens $opensName$openSet."
        }
        parts += "Opens your workout."
        parts.joinToString(" ")
    }
    is WidgetModel.Idle -> {
        val weekPart = if (weekTotal > 0) " Done $weekDone of $weekTotal this week." else ""
        val notePart = note?.let { " ${it.spokenSeparators()}." } ?: ""
        val extraPart = extra.firstOrNull()?.let { " ${it.spoken.spokenSeparators()}." } ?: ""
        "Rebuild. $dayFull. $title. ${detail.spokenSeparators()}.$notePart$weekPart$extraPart Opens Today."
    }
}

private fun String.spokenSeparators(): String = replace("${NBSP}· ", ", ").replace(" · ", ", ").replace(NBSP, " ")

private const val NBSP = "\u00A0"

/**
 * Pure widget projection from [AppState]. Reuses `weekChecklist`, `todayPlan`, and `opensAt` verbatim so the widget
 * never drifts from Today.
 */
fun AppState.widgetModel(
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
    exactAlarmsAllowed: Boolean = true,
    monotonic: Monotonic? = null
): WidgetModel {
    val currentPhase = phase ?: return WidgetModel.Setup
    val today = localDateOf(nowMillis, zone)

    val active = activeWorkout
    if (active != null) {
        return activeWidgetModel(
            state = this,
            workout = active,
            nowMillis = nowMillis,
            today = today,
            zone = zone,
            locale = locale,
            exactAlarmsAllowed = exactAlarmsAllowed,
            monotonic = monotonic
        )
    }

    val checklist = weekChecklist(today, zone) ?: return WidgetModel.Setup
    val plan = todayPlan(today, checklist)
    val dateFmt = DateTimeFormatter.ofPattern("d MMM", locale)
    val shortDay = DayOfWeek.of(today.dayOfWeek.value).getDisplayName(TextStyle.SHORT, locale)
    val fullDay = DayOfWeek.of(today.dayOfWeek.value).getDisplayName(TextStyle.FULL, locale)
    val dateLabel = "$shortDay$NBSP${dateFmt.format(today).replace(" ", NBSP)}"
    val fullDate = "$fullDay ${DateTimeFormatter.ofPattern("d MMMM", locale).format(today)}"
    val todays = checklist.routines.filter { it.template.weekday == today.dayOfWeek.value }
    val weekTemplates = checklist.routines.map { it.template }

    val weekRows = checklist.routines.map { row ->
        val day = DayOfWeek.of(row.template.weekday)
        // A day still to come says only its weekday; "Not done" is for days already gone.
        val status = when (row.status) {
            RoutineStatus.DONE -> "Done"
            RoutineStatus.IN_PROGRESS -> "In progress"
            RoutineStatus.PARTIAL -> "Partial"
            RoutineStatus.NOT_DONE -> when {
                row.template.weekday == today.dayOfWeek.value -> "Today"
                row.template.weekday < today.dayOfWeek.value -> "Not done"
                else -> ""
            }
        }
        val name = twinName(row.template, weekTemplates)
        WidgetRow(
            mark = WidgetMark.DAY,
            markText = day.getDisplayName(TextStyle.SHORT, locale),
            name = name,
            trailing = status,
            muted = row.status == RoutineStatus.DONE,
            today = row.template.weekday == today.dayOfWeek.value,
            past = row.template.weekday < today.dayOfWeek.value,
            spoken = listOf(day.getDisplayName(TextStyle.FULL, locale), name, status.lowercase(locale))
                .filter { it.isNotBlank() }.joinToString(", ")
        )
    }

    var rows = weekRows
    var rowsAreExercises = false
    var extra: List<WidgetRow> = emptyList()
    var extraMinRows = 0
    fun upNext(routine: ScheduledRoutine) {
        val planned = plannedExercises(routine, currentPhase)
        // Nothing planned to preview: no heading either.
        if (planned.isEmpty()) return
        val count = "${planned.size}${NBSP}exercise${if (planned.size == 1) "" else "s"}"
        val name = twinName(routine, weekTemplates)
        extra = listOf(WidgetRow(WidgetMark.SECTION, "", "Up next${NBSP}· $name${NBSP}· $count", "",
            spoken = "Up next, $name, $count")) + plannedRows(this, planned)
        // The heading and at least two exercises, or nothing.
        extraMinRows = 1 + minOf(2, planned.size)
    }
    val title: String
    val detail: String
    var note: String? = null
    val stripLine: String
    when {
        plan.kind == TodayKind.SCHEDULED && plan.scheduled != null -> {
            val scheduled = plan.scheduled
            val planned = plannedExercises(scheduled, currentPhase)
            val status = checklist.statusOf(scheduled.id)
            val alsoToday = todays.filter { it.template.id != scheduled.id }
            title = twinName(scheduled, weekTemplates)
            detail = widgetPlannedSummary(this, planned).ifBlank { currentPhase.title }
            note = listOfNotNull(
                "Partial${NBSP}· not counted".takeIf { status == RoutineStatus.PARTIAL },
                alsoToday.takeIf { it.isNotEmpty() }
                    ?.let { also -> "Also today: " + also.joinToString { "${it.template.name} (${shortStatusWord(it.status)})" } }
            ).takeIf { it.isNotEmpty() }?.joinToString("${NBSP}· ")
            stripLine = "$title${NBSP}· Today"
            if (planned.isNotEmpty()) {
                rowsAreExercises = true
                rows = plannedRows(this, planned)
            }
        }
        plan.kind == TodayKind.NEXT_UNFINISHED && plan.scheduled != null -> {
            val scheduled = plan.scheduled
            title = if (todays.size > 1) "${fullDay}'s routines done" else "${scheduled.name} done"
            val suggested = plan.suggested
            detail = if (suggested != null) {
                val nextDay = DayOfWeek.of(suggested.weekday).getDisplayName(TextStyle.FULL, locale)
                "Next: ${suggested.name}, $nextDay"
            } else "Done this week"
            stripLine = if (suggested != null) "$title${NBSP}· Next: ${suggested.name}" else title
            // The preview names the routine and takes its own weekday: the suggestion can be a day already missed.
            if (suggested != null) upNext(suggested)
        }
        plan.kind == TodayKind.WEEK_COMPLETE -> {
            val count = checklist.routines.size
            title = "Week complete"
            detail = "All $count${NBSP}workout${if (count == 1) "" else "s"} done"
            stripLine = "Week complete${NBSP}· $detail"
            lastWorkoutRecap(this, zone, locale)?.let { extra = it; extraMinRows = it.size }
        }
        else -> {
            title = "Rest day"
            val next = checklist.routines.firstOrNull {
                it.status != RoutineStatus.DONE && it.template.weekday > today.dayOfWeek.value
            }
            detail = when {
                next != null -> {
                    val day = DayOfWeek.of(next.template.weekday).getDisplayName(TextStyle.FULL, locale)
                    "Next: ${twinName(next.template, weekTemplates)}, $day"
                }
                else -> "Nothing scheduled for $fullDay"
            }
            if (next != null) upNext(next.template)
            if (checklist.routines.isEmpty() && plan.suggested != null) {
                val firstDay = DayOfWeek.of(plan.suggested.weekday).getDisplayName(TextStyle.FULL, locale)
                note = "Optional today: ${plan.suggested.name}, or wait for $firstDay"
            }
            stripLine = "Rest day"
        }
    }

    return WidgetModel.Idle(
        date = today,
        dateLabel = dateLabel,
        dayFull = fullDate,
        kind = plan.kind,
        title = title,
        detail = detail,
        note = note,
        stripLine = stripLine,
        rows = rows,
        rowsAreExercises = rowsAreExercises,
        weekDone = checklist.done,
        weekTotal = checklist.routines.size,
        extra = extra,
        extraMinRows = extraMinRows
    )
}

/** Numbered rows for planned exercises with their plan targets, never a logged load. */
private fun plannedRows(state: AppState, planned: List<PlannedExercise>): List<WidgetRow> = planned.mapIndexed { i, p ->
    val name = state.exercises.find { it.id == p.exerciseId }?.name ?: "Exercise"
    val target = plannedTarget(state.measureOf(p.exerciseId), p)
    WidgetRow(WidgetMark.NUMBER, (i + 1).toString(), name, target,
        spoken = if (target.isBlank()) name else "$name, ${target.replace("$NBSP×$NBSP", " sets of ").replace("–", " to ")}")
}

/**
 * The last finished workout as its receipt states it: when, which routine (or that it was an extra), and what was
 * logged, with minutes only when the time between start and finish is believable. No best set or comparison.
 */
private fun lastWorkoutRecap(state: AppState, zone: ZoneId, locale: Locale): List<WidgetRow>? {
    val last = state.workouts.filter { it.finishedAt != null }.maxByOrNull { it.startedAt } ?: return null
    val date = last.startLocalDate(zone)
    val day = DayOfWeek.of(date.dayOfWeek.value).getDisplayName(TextStyle.SHORT, locale)
    val dateText = "$day$NBSP${DateTimeFormatter.ofPattern("d MMM", locale).format(date).replace(" ", NBSP)}"
    val name = if (last.extra) "${last.name} (extra)" else last.name
    val minutes = ((last.finishedAt!! - last.startedAt) / 60_000L).takeIf { it in 1..300 }
    val line = widgetWorkoutLoggedText(last) + (minutes?.let { "$NBSP·$NBSP$it${NBSP}min" } ?: "")
    return listOf(
        WidgetRow(WidgetMark.SECTION, "", "Last workout${NBSP}· $dateText${NBSP}· $name", "",
            spoken = "Last workout, $dateText, $name"),
        WidgetRow(WidgetMark.PLAIN, "", line, "", spoken = line)
    )
}

/** A planned target in a few characters: "2 × 6–8", "3 sets", or blank for a cardio total. */
private fun plannedTarget(measure: Measure, p: PlannedExercise): String = when (measure) {
    Measure.REPS -> p.sets?.let { sets ->
        if (p.minReps != null && p.maxReps != null) "$sets$NBSP×$NBSP${p.minReps}–${p.maxReps}"
        else "$sets${NBSP}set${if (sets == 1) "" else "s"}"
    } ?: ""
    Measure.TIMED, Measure.DISTANCE -> p.sets?.let { "$it${NBSP}set${if (it == 1) "" else "s"}" } ?: ""
    Measure.CARDIO -> ""
}

private fun activeWidgetModel(
    state: AppState,
    workout: Workout,
    nowMillis: Long,
    today: LocalDate,
    zone: ZoneId,
    locale: Locale,
    exactAlarmsAllowed: Boolean,
    monotonic: Monotonic?
): WidgetModel {
    val resumeAt = opensAt(state, workout)
    val doneExercises = workout.exercises.count { widgetGoalMet(it) }
    val totalExercises = workout.exercises.size
    val logged = widgetWorkoutLoggedText(workout)
    val summary = if (totalExercises > 0) {
        "$logged${NBSP}· $doneExercises${NBSP}of$NBSP$totalExercises exercises done"
    } else logged

    val timer = state.restTimer?.takeIf { it.workoutId == workout.id }
    val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)

    // The rest's own exercise is the focus while resting; otherwise where Resume opens.
    val focusId = timer?.takeIf { it.deadline > nowMillis }?.exerciseId ?: resumeAt?.id
    val rows = workout.exercises.mapIndexed { i, we ->
        val done = widgetGoalMet(we)
        val current = !done && we.id == focusId
        val progress = widgetExerciseProgress(we)
        WidgetRow(
            mark = when {
                done -> WidgetMark.DONE
                current -> WidgetMark.CURRENT
                else -> WidgetMark.NUMBER
            },
            markText = (i + 1).toString(),
            name = we.exercise.name,
            trailing = progress,
            muted = done || (we.skipped && !we.hasLoggedWork()),
            spoken = "${we.exercise.name}, ${progress.replace("/", " of ")}" + if (current) ", current" else ""
        )
    }
    val currentIndex = workout.exercises.indexOfFirst { it.id == focusId }.coerceAtLeast(0)

    if (timer != null && timer.deadline > nowMillis) {
        val about = widgetRestAbout(state, workout, timer)
        return WidgetModel.Resting(
            workoutName = workout.name,
            timerId = timer.id,
            deadline = timer.deadline,
            shortPause = timer.shortPause,
            liveCountdown = exactAlarmsAllowed,
            label = if (timer.shortPause) "Short pause" else "Rest",
            nextTitle = about.title,
            setLine = about.setLine,
            nextLine = about.next,
            lastTimeSet = about.lastSet,
            lastTimeExercise = about.lastExercise,
            endTimeText = timeFmt.format(Instant.ofEpochMilli(timer.deadline).atZone(zone)),
            rows = rows,
            currentIndex = currentIndex,
            doneExercises = doneExercises,
            totalExercises = totalExercises
        )
    }

    val restOver = timer != null && timer.deadline <= nowMillis
    val restOverAbout = timer?.takeIf { restOver }?.let { widgetRestAbout(state, workout, it) }
    val restOverLabel = if (restOver) {
        if (timer?.shortPause == true) "Pause over" else "Rest over"
    } else null

    val startedTime = timeFmt.format(Instant.ofEpochMilli(workout.startedAt).atZone(zone))
    val startedDate = workout.startLocalDate(zone)
    val startedPart = if (startedDate == today) {
        "started $startedTime"
    } else {
        val dayShort = DayOfWeek.of(startedDate.dayOfWeek.value).getDisplayName(TextStyle.SHORT, locale)
        "started $dayShort $startedTime"
    }
    val startedLine = if (workout.extra) "Outside the plan" else startedPart
    val startedSpoken = if (workout.extra) "in progress outside the plan, $startedPart" else "in progress, $startedPart"

    val watch = state.stopwatch?.takeIf { it.workoutId == workout.id }
    val widgetWatch = watch?.let { w ->
        val exName = workout.exercises.find { it.id == w.exerciseId }?.exercise?.name ?: ""
        val measured = if (w.running && !w.needsConfirmation && monotonic != null) {
            w.timeAt(monotonic) as? StopwatchTime.Measured
        } else null
        val live = measured != null && w.runningSince != null
        val base = if (live) (w.runningSince!! - w.elapsedMs) else null
        val status = when {
            w.needsConfirmation || (w.running && monotonic != null && measured == null) ->
                if (exName.isNotBlank()) "$exName${NBSP}· time to enter" else "Stopwatch${NBSP}· time to enter"
            w.running ->
                if (exName.isNotBlank()) "Stopwatch timing${NBSP}· $exName" else "Stopwatch timing"
            w.stopped -> {
                val formatted = widgetDuration(wholeSeconds(w.elapsedMs))
                if (exName.isNotBlank()) "$exName${NBSP}· stopped at $formatted, not logged"
                else "Stopwatch stopped at $formatted, not logged"
            }
            else -> {
                val formatted = widgetDuration(wholeSeconds(w.elapsedMs))
                if (exName.isNotBlank()) "$exName${NBSP}· paused at $formatted"
                else "Stopwatch paused at $formatted"
            }
        }
        WidgetStopwatch(
            exerciseName = exName,
            liveClock = live,
            chronometerBaseMs = base,
            statusLine = status
        )
    }

    val openSetLabel = resumeAt?.openGroup()?.let { "${it.method.title.lowercase(locale)} in progress" }

    return WidgetModel.Active(
        workoutName = workout.name,
        startedLine = startedLine,
        startedSpoken = startedSpoken,
        restOver = restOver,
        restOverLabel = restOverLabel,
        restOverNext = restOverAbout?.next,
        loggedSummary = summary,
        opensName = resumeAt?.exercise?.name,
        openSetLabel = openSetLabel,
        stopwatch = widgetWatch,
        rows = rows,
        currentIndex = currentIndex,
        doneExercises = doneExercises,
        totalExercises = totalExercises
    )
}

private data class WidgetRestAbout(
    val title: String,
    val setLine: String,
    val next: String,
    val lastSet: LoggedSet? = null,
    val lastExercise: Exercise? = null
)

/**
 * Last time's set for [we]'s next entry, paired the way the set ledger and entry prefill pair them: the n-th entry
 * logged today in the entry unit meets last time's n-th same-unit entry, so a set logged in another unit never shifts
 * the pairing. Its main set (a drop's first stage); null when there is none, or for anything but reps.
 */
private fun AppState.lastTimeSet(workout: Workout, we: WorkoutExercise): LoggedSet? {
    if (we.exercise.measure != Measure.REPS) return null
    val previous = previousComparable(we.exercise, workout.id, we.entryLoadUnit, we.perLeg)
        ?.workEntries()?.filter { it.unit == we.entryLoadUnit } ?: return null
    val paired = we.workEntries().count { it.unit == we.entryLoadUnit }
    return previous.getOrNull(paired)?.mainSet
}

private fun widgetRestAbout(state: AppState, workout: Workout, timer: RestTimer): WidgetRestAbout {
    val index = workout.exercises.indexOfFirst { it.id == timer.exerciseId }
    val current = workout.exercises.getOrNull(index)
        ?: return WidgetRestAbout("Your workout", "Open to continue", "Open your workout")
    val name = current.exercise.name
    if (timer.shortPause) {
        return WidgetRestAbout(name, "More reps on this set", "Next: more reps on this set")
    }
    val stillGoing = !current.hasGoal() || !current.goalReached()
    if (stillGoing) {
        if (current.exercise.measure == Measure.CARDIO) {
            return WidgetRestAbout(name, "Keep going", "Next: more $name")
        }
        val done = if (current.exercise.measure == Measure.REPS) current.workingCount() else current.qualifyingCount()
        val nextSet = done + 1
        val ofSets = current.targetSets?.let { "${NBSP}of$NBSP$it" } ?: ""
        return WidgetRestAbout(
            title = name,
            setLine = "Set$NBSP$nextSet$ofSets",
            next = "Next: set$NBSP$nextSet$ofSets",
            lastSet = state.lastTimeSet(workout, current),
            lastExercise = current.exercise
        )
    }
    val nextEx = workout.exercises.drop(index + 1).plus(workout.exercises.take(index))
        .firstOrNull { !it.skipped && it.hasGoal() && !it.goalReached() }
    return if (nextEx != null) {
        val ordinal = (if (nextEx.exercise.measure == Measure.REPS) nextEx.workingCount() else nextEx.qualifyingCount()) + 1
        val ofSets = nextEx.targetSets?.let { "${NBSP}of$NBSP$it" } ?: ""
        WidgetRestAbout(
            title = nextEx.exercise.name,
            setLine = "Set$NBSP$ordinal$ofSets",
            next = "Next: ${nextEx.exercise.name}",
            lastSet = state.lastTimeSet(workout, nextEx),
            lastExercise = nextEx.exercise
        )
    } else {
        WidgetRestAbout("All sets done", "Every planned set is logged", "Every planned set is done")
    }
}

private fun widgetGoalMet(we: WorkoutExercise): Boolean = we.hasGoal() && we.goalReached()

private fun widgetExerciseProgress(we: WorkoutExercise): String = when {
    we.skipped && !we.hasLoggedWork() -> "Skipped"
    we.exercise.measure == Measure.REPS -> {
        val done = we.workingCount()
        we.targetSets?.let { "$done/$it" } ?: "$done${NBSP}set${if (done == 1) "" else "s"}"
    }
    we.exercise.measure == Measure.TIMED || we.exercise.measure == Measure.DISTANCE -> {
        val done = we.qualifyingCount()
        we.targetSets?.let { "$done/$it" } ?: "${we.efforts.size}${NBSP}logged"
    }
    else -> {
        val count = we.efforts.size
        "$count${NBSP}logged"
    }
}

private fun widgetWorkoutLoggedText(workout: Workout): String {
    val working = workout.workingSetCount()
    val warmups = workout.warmupSetCount()
    if (workout.exercises.none { it.exercise.measured }) {
        val base = "$working${NBSP}working set${if (working == 1) "" else "s"}"
        return if (warmups > 0) "$base, $warmups${NBSP}warm-up" else base
    }
    val parts = mutableListOf<String>()
    if (working > 0) parts += "$working${NBSP}working set${if (working == 1) "" else "s"}"
    if (warmups > 0) parts += "$warmups${NBSP}warm-up"
    listOf(
        Measure.TIMED to "timed set",
        Measure.DISTANCE to "distance set",
        Measure.CARDIO to "cardio effort"
    ).forEach { (measure, noun) ->
        val count = workout.exercises.filter { it.exercise.measure == measure }.sumOf { it.efforts.size }
        if (count > 0) parts += "$count$NBSP$noun${if (count == 1) "" else "s"}"
    }
    return parts.ifEmpty { listOf("nothing logged yet") }.joinToString(", ")
}

private fun widgetPlannedSummary(state: AppState, planned: List<PlannedExercise>): String {
    val workingSets = planned.filter { state.measureOf(it.exerciseId) == Measure.REPS }.sumOf { it.sets ?: 0 }
    val exPart = "${planned.size}${NBSP}exercise${if (planned.size == 1) "" else "s"}"
    return if (workingSets > 0) {
        "$exPart${NBSP}· $workingSets${NBSP}working set${if (workingSets == 1) "" else "s"}"
    } else exPart
}

private fun shortStatusWord(status: RoutineStatus): String = when (status) {
    RoutineStatus.DONE -> "done"
    RoutineStatus.IN_PROGRESS -> "in progress"
    RoutineStatus.PARTIAL -> "partial"
    RoutineStatus.NOT_DONE -> "not done"
}

private fun twinName(routine: ScheduledRoutine, week: List<ScheduledRoutine>): String {
    val twins = week.filter { it.weekday == routine.weekday && it.name == routine.name }
    if (twins.size < 2) return routine.name
    val idx = twins.indexOfFirst { it.id == routine.id } + 1
    return "${routine.name} ($idx${NBSP}of$NBSP${twins.size})"
}

private fun widgetDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = s % 3600 / 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s % 60) else "%d:%02d".format(m, s % 60)
}
