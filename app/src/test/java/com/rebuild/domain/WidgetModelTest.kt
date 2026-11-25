package com.rebuild.domain

import com.rebuild.data.StateChanges
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetModelTest {
    private val utc = ZoneId.of("UTC")
    private val us = Locale.US

    private fun millis(date: LocalDate, hour: Int = 9, minute: Int = 0, zone: ZoneId = utc): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun baseState(): AppState =
        StateChanges.apply(Seed.initialState(), Action.SelectPhase(Phase.INTRO_TWO))

    @Test
    fun setupIncompleteRendersSetupAndOpensToday() {
        val state = Seed.initialState()
        val model = state.widgetModel(millis(LocalDate.of(2026, 9, 14)), utc, us)
        assertEquals(WidgetModel.Setup, model)
        assertEquals(WidgetDestination.TODAY, model.destination())
        assertTrue(model.spokenSummary().contains("Finish setup"))
    }

    @Test
    fun scheduledDayListsTodaysExercisesWithTargetsAndWeekCount() {
        val state = baseState()
        val tuesday = LocalDate.of(2026, 9, 15) // Tuesday (weekday 2 in INTRO_TWO: Tue/Thu/Sat/Sun)
        val model = state.widgetModel(millis(tuesday), utc, us) as WidgetModel.Idle
        val template = state.scheduledRoutines(Phase.INTRO_TWO).first { it.weekday == 2 }
        val planned = state.plannedExercises(template, Phase.INTRO_TWO)
        assertEquals(TodayKind.SCHEDULED, model.kind)
        assertEquals(template.name, model.title)
        assertEquals("Tue\u00A015\u00A0Sep", model.dateLabel)
        assertTrue(model.rowsAreExercises)
        assertEquals(planned.size, model.rows.size)
        assertEquals(WidgetMark.NUMBER, model.rows[0].mark)
        assertEquals("1", model.rows[0].markText)
        // INTRO_TWO prescribes two working sets; the row shows the plan's target, never a logged load.
        assertTrue(model.rows[0].trailing.matches(Regex("2\u00A0×\u00A0\\d+–\\d+")))
        assertEquals(0, model.weekDone)
        assertEquals(4, model.weekTotal)
        assertNull(model.note)
        assertEquals(WidgetDestination.TODAY, model.destination())
        assertTrue(model.spokenSummary().contains("Done 0 of 4 this week"))
    }

    @Test
    fun restDayIsCalmAndDoesNotEmphasizeToday() {
        val state = baseState()
        val wednesday = LocalDate.of(2026, 9, 16) // Wednesday (rest day in INTRO_TWO)
        val model = state.widgetModel(millis(wednesday), utc, us) as WidgetModel.Idle
        assertEquals(TodayKind.REST, model.kind)
        assertEquals("Rest day", model.title)
        // Rest days name the next routine still to do and list the week, never an empty card.
        assertEquals("Next: ${state.scheduledRoutines(Phase.INTRO_TWO).first { it.weekday == 4 }.name}, Thursday", model.detail)
        assertFalse(model.rowsAreExercises)
        assertEquals(4, model.rows.size)
        assertTrue(model.rows.all { it.mark == WidgetMark.DAY })
        assertEquals("Tue", model.rows[0].markText)
        assertEquals("Not done", model.rows[0].trailing)
        assertTrue(model.rows[0].past)
        // A day still to come says only its weekday, never "Not done".
        assertEquals("", model.rows[1].trailing)
        assertFalse(model.rows[1].past)
        assertNull(model.note)
        // The space under a short week previews the next workout: a named heading, then its planned exercises.
        val thursday = state.scheduledRoutines(Phase.INTRO_TWO).first { it.weekday == 4 }
        val planned = state.plannedExercises(thursday, Phase.INTRO_TWO)
        assertEquals(WidgetMark.SECTION, model.extra.first().mark)
        assertEquals("Up next\u00A0· ${thursday.name}\u00A0· ${planned.size}\u00A0exercises", model.extra.first().name)
        assertEquals(planned.size, model.extra.size - 1)
        assertTrue(model.extra.drop(1).all { it.mark == WidgetMark.NUMBER })
        assertEquals(3, model.extraMinRows)
        assertEquals("0\u00A0of\u00A04 this\u00A0week", model.weekLine)
    }

    @Test
    fun doneTodayRoutineLeadsWithDoneAndNamesNextUnfinished() {
        val tuesday = LocalDate.of(2026, 9, 15)
        val now = millis(tuesday, 10, 0)
        var state = baseState()
        val tuesdayTemplate = state.scheduledRoutines(Phase.INTRO_TWO).first { it.weekday == 2 }
        state = finishWorkoutCounting(state, tuesdayTemplate.id, now)

        val model = state.widgetModel(millis(tuesday, 14, 0), utc, us) as WidgetModel.Idle
        assertEquals(TodayKind.NEXT_UNFINISHED, model.kind)
        // Today is done: the preview takes the suggested routine's own name and weekday.
        assertTrue(model.extra.first().name.startsWith("Up next\u00A0· "))
        assertTrue(model.title.endsWith("done"))
        assertTrue(model.detail.startsWith("Next:"))
        assertEquals(1, model.weekDone)
        assertTrue(model.rows.first().muted)
        assertEquals("Done", model.rows.first().trailing)
        assertTrue(model.rows.first().spoken.endsWith("done"))
    }

    @Test
    fun weekCompleteHasZeroAmberAndCountsEveryDoneRoutine() {
        val monday = LocalDate.of(2026, 9, 14)
        var state = baseState()
        val routines = state.scheduledRoutines(Phase.INTRO_TWO)
        routines.forEachIndexed { i, routine ->
            state = finishWorkoutCounting(state, routine.id, millis(monday.plusDays(i.toLong()), 10, 0))
        }
        val model = state.widgetModel(millis(monday.plusDays(5), 11, 0), utc, us) as WidgetModel.Idle
        assertEquals(TodayKind.WEEK_COMPLETE, model.kind)
        assertEquals("Week complete", model.title)
        assertEquals(routines.size, model.weekDone)
        assertEquals(routines.size, model.weekTotal)
        assertTrue(model.rows.all { it.muted && it.trailing.endsWith("Done") })
        // Nothing comes next, so a quiet recap of the last workout: no best set, no comparison.
        assertEquals(2, model.extra.size)
        assertEquals(WidgetMark.SECTION, model.extra[0].mark)
        assertTrue(model.extra[0].name, model.extra[0].name.startsWith("Last workout\u00A0· Thu\u00A017\u00A0Sep"))
        assertEquals(WidgetMark.PLAIN, model.extra[1].mark)
        assertTrue(model.extra[1].name, model.extra[1].name.startsWith("1\u00A0working set"))
        assertTrue(model.extra[1].name.endsWith("5\u00A0min"))
        assertFalse(model.extra.joinToString { it.name }.contains("best", ignoreCase = true))
    }

    @Test
    fun extraWorkoutsNeverInflateWeekCount() {
        val monday = LocalDate.of(2026, 9, 14)
        var state = baseState()
        val startAt = millis(monday, 8, 0)
        state = StateChanges.apply(state, Action.StartExtraWorkout(startAt))
        val active = requireNotNull(state.activeWorkout)
        val exId = state.exercises.first().id
        state = StateChanges.apply(state, Action.AddWorkoutExercise(active.id, "we-extra", exId))
        val weId = state.activeWorkout!!.exercises.first().id
        state = StateChanges.apply(state, Action.SaveSet(active.id, weId, "s1", 20.0, 10, 2.0, false, startAt + 60_000))
        state = StateChanges.apply(state, Action.FinishWorkout(startAt + 600_000))

        val model = state.widgetModel(millis(monday, 12, 0), utc, us) as WidgetModel.Idle
        assertEquals(0, model.weekDone)
        assertTrue(model.rows.none { it.muted })
    }

    @Test
    fun activeWorkoutTakesPriorityOverScheduleAndShowsSlidingExerciseWindow() {
        val tuesday = LocalDate.of(2026, 9, 15)
        val startAt = millis(tuesday, 17, 58)
        var state = baseState()
        val tuesdayTemplate = state.scheduledRoutines(Phase.INTRO_TWO).first { it.weekday == 2 }
        state = StateChanges.apply(state, Action.StartWorkout(tuesdayTemplate.id, startAt))
        val workout = requireNotNull(state.activeWorkout)
        // Complete first exercise (target 2 sets in INTRO_TWO)
        val firstEx = workout.exercises[0]
        state = StateChanges.apply(state, Action.SaveSet(workout.id, firstEx.id, "s1", 40.0, 10, 3.0, false, startAt + 60_000))
        state = StateChanges.apply(state, Action.SaveSet(workout.id, firstEx.id, "s2", 40.0, 10, 3.0, false, startAt + 120_000))
        state = StateChanges.apply(state, Action.SkipRest)

        val model = state.widgetModel(startAt + 180_000, utc, us) as WidgetModel.Active
        assertEquals(WidgetDestination.WORKOUT, model.destination())
        assertEquals(workout.name, model.workoutName)
        assertTrue(model.startedLine.startsWith("started"))
        assertTrue(model.loggedSummary.contains("exercises done"))
        assertFalse(model.restOver)
        assertEquals(1, model.doneExercises)
        assertEquals(workout.exercises.size, model.totalExercises)
        assertEquals(workout.exercises[1].exercise.name, model.opensName)
        assertEquals(workout.exercises.size, model.rows.size)
        assertEquals(1, model.currentIndex)
        assertEquals(WidgetMark.DONE, model.rows[0].mark)
        assertTrue(model.rows[0].muted)
        assertEquals("2/2", model.rows[0].trailing)
        assertEquals(WidgetMark.CURRENT, model.rows[1].mark)
        assertEquals("0/2", model.rows[1].trailing)
        assertEquals(WidgetMark.NUMBER, model.rows[2].mark)
        assertEquals("3", model.rows[2].markText)
        // Spoken summary avoids middle-dot punctuation and duplicate "Opens"
        val spoken = model.spokenSummary()
        assertFalse(spoken.contains("·"))
        assertTrue(spoken.contains("Resume opens"))
    }

    @Test
    fun activeRestUsesLiveCountdownOnlyWhenExactAlarmsAllowedAndClearsAtDeadline() {
        val tuesday = LocalDate.of(2026, 9, 15)
        val startAt = millis(tuesday, 18, 0)
        var state = baseState()
        val tuesdayTemplate = state.scheduledRoutines(Phase.INTRO_TWO).first { it.weekday == 2 }
        state = StateChanges.apply(state, Action.StartWorkout(tuesdayTemplate.id, startAt))
        val workout = requireNotNull(state.activeWorkout)
        val firstEx = workout.exercises[0]
        state = StateChanges.apply(state, Action.SaveSet(workout.id, firstEx.id, "s1", 40.0, 10, 3.0, false, startAt + 10_000))
        val timer = requireNotNull(state.restTimer)

        val liveModel = state.widgetModel(startAt + 20_000, utc, us, exactAlarmsAllowed = true) as WidgetModel.Resting
        assertTrue(liveModel.liveCountdown)
        assertEquals(timer.deadline, liveModel.deadline)
        assertEquals(WidgetDestination.WORKOUT, liveModel.destination())
        assertTrue(liveModel.nextLine.startsWith("Next: set"))
        assertEquals(firstEx.exercise.name, liveModel.nextTitle)
        assertEquals("Set\u00A02\u00A0of\u00A02", liveModel.setLine)
        // No earlier finished workout yet, so there is no last time to show.
        assertNull(liveModel.lastTimeSet)
        assertEquals(0, liveModel.currentIndex)
        assertEquals(WidgetMark.CURRENT, liveModel.rows[0].mark)
        assertFalse(liveModel.spokenSummary().contains(":00")) // TalkBack uses end time, not ticking seconds
        assertTrue(liveModel.spokenSummary().contains("Resting until"))

        val staticModel = state.widgetModel(startAt + 20_000, utc, us, exactAlarmsAllowed = false) as WidgetModel.Resting
        assertFalse(staticModel.liveCountdown)
        assertNotEquals(liveModel, staticModel)

        // Once deadline has passed before alarm clears it, never show a clock; show Rest over
        val expiredModel = state.widgetModel(timer.deadline + 1_000, utc, us, exactAlarmsAllowed = true) as WidgetModel.Active
        assertTrue(expiredModel.restOver)
        assertEquals("Rest over", expiredModel.restOverLabel)
        assertNotNull(expiredModel.restOverNext)
    }

    @Test
    fun untrustedStopwatchNeverShowsLiveClock() {
        val tuesday = LocalDate.of(2026, 9, 15)
        val startAt = millis(tuesday, 18, 0)
        var state = baseState()
        val tuesdayTemplate = state.scheduledRoutines(Phase.INTRO_TWO).first { it.weekday == 2 }
        state = StateChanges.apply(state, Action.StartWorkout(tuesdayTemplate.id, startAt))
        val workout = requireNotNull(state.activeWorkout)
        val timedExercise = state.exercises.first { it.measure == Measure.TIMED }
        state = StateChanges.apply(state, Action.AddWorkoutExercise(workout.id, "we-timed", timedExercise.id))
        val addedWe = state.activeWorkout!!.exercises.last()
        val boot1 = Monotonic(boot = 1, session = "proc-a", elapsedMs = 50_000L)
        state = StateChanges.apply(state, Action.StartStopwatch("e1", workout.id, addedWe.id, boot1, startAt + 5_000))

        // Same boot: trusted live clock
        val trusted = state.widgetModel(
            startAt + 15_000, utc, us,
            monotonic = Monotonic(boot = 1, session = "proc-b", elapsedMs = 60_000L)
        ) as WidgetModel.Active
        assertTrue(trusted.stopwatch!!.liveClock)
        assertEquals(50_000L, trusted.stopwatch!!.chronometerBaseMs)

        // Different boot: untrusted -> static "time to enter", never a running clock
        val untrusted = state.widgetModel(
            startAt + 15_000, utc, us,
            monotonic = Monotonic(boot = 2, session = "proc-c", elapsedMs = 10_000L)
        ) as WidgetModel.Active
        assertFalse(untrusted.stopwatch!!.liveClock)
        assertNull(untrusted.stopwatch!!.chronometerBaseMs)
        assertTrue(untrusted.stopwatch!!.statusLine.contains("time to enter"))
    }

    @Test
    fun unrelatedStateChangesProduceEqualWidgetModelForDeduplication() {
        val monday = LocalDate.of(2026, 9, 14)
        val now = millis(monday, 9, 0)
        val before = baseState()
        val afterWeight = StateChanges.apply(
            before,
            Action.LogBodyweight("bw1", now, 80.5, MassUnit.KG, date = monday.toString())
        )
        assertEquals(before.widgetModel(now, utc, us), afterWeight.widgetModel(now, utc, us))
    }

    @Test
    fun nextMidnightHandlesNormalDaysAndDstTransitions() {
        val ny = ZoneId.of("America/New_York")
        // Spring forward: 2026-03-08 (23-hour day)
        val springDay = LocalDateTime.of(2026, 3, 8, 12, 0).atZone(ny).toInstant().toEpochMilli()
        val springNext = nextMidnightMillis(springDay, ny)
        assertEquals(LocalDate.of(2026, 3, 9), localDateOf(springNext, ny))
        assertEquals(0, java.time.Instant.ofEpochMilli(springNext).atZone(ny).hour)

        // Fall back: 2026-11-01 (25-hour day)
        val fallDay = LocalDateTime.of(2026, 11, 1, 12, 0).atZone(ny).toInstant().toEpochMilli()
        val fallNext = nextMidnightMillis(fallDay, ny)
        assertEquals(LocalDate.of(2026, 11, 2), localDateOf(fallNext, ny))
        assertEquals(0, java.time.Instant.ofEpochMilli(fallNext).atZone(ny).hour)
    }

    @Test
    fun widgetSizeClassRespectsBoundsAndFontScaleStepDown() {
        assertEquals(WidgetSize.STRIP, widgetSizeFor(280, 60, 1.0f))
        assertEquals(WidgetSize.COMPACT, widgetSizeFor(160, 130, 1.0f))
        assertEquals(WidgetSize.STANDARD, widgetSizeFor(300, 130, 1.0f))
        assertEquals(WidgetSize.STANDARD, widgetSizeFor(300, 223, 1.0f))
        assertEquals(WidgetSize.EXPANDED, widgetSizeFor(300, 260, 1.0f))
        assertEquals(WidgetSize.EXPANDED, widgetSizeFor(300, 260, 1.3f))
        // Above 130% font scale, Expanded steps down to Standard so rows don't clip
        assertEquals(WidgetSize.STANDARD, widgetSizeFor(300, 260, 1.5f))
        assertEquals(WidgetSize.STANDARD, widgetSizeFor(300, 260, 2.0f))
    }

    @Test
    fun listRowsFillTheLaunchersHeightAndShrinkWithLargeText() {
        assertEquals(2, widgetListRows(160, 1f))
        assertEquals(3, widgetListRows(190, 1f))
        assertEquals(4, widgetListRows(220, 1f))
        assertEquals(7, widgetListRows(330, 1f))
        assertEquals(7, widgetListRows(600, 1f))
        assertEquals(1, widgetListRows(190, 2f))
        assertEquals(2, widgetListRows(220, 2f))
        assertEquals(1, widgetListRows(100, 1f))
    }

    @Test
    fun widgetWindowKeepsCurrentVisibleWithOneRowOfContext() {
        assertEquals(0 until 3, widgetWindow(8, 0, 3))
        assertEquals(0 until 3, widgetWindow(8, 1, 3))
        assertEquals(3 until 6, widgetWindow(8, 4, 3))
        assertEquals(5 until 8, widgetWindow(8, 7, 3))
        assertEquals(0 until 2, widgetWindow(2, 1, 3))
        assertTrue(widgetWindow(5, 2, 0).isEmpty())
    }

    @Test
    fun restShowsLastTimesSameSetFromThePreviousComparableWorkout() {
        val tuesday = LocalDate.of(2026, 9, 15)
        var state = baseState()
        val template = state.scheduledRoutines(Phase.INTRO_TWO).first { it.weekday == 2 }
        // Last week: two working sets on the first exercise.
        val lastWeek = millis(tuesday.minusDays(7), 18, 0)
        state = StateChanges.apply(state, Action.StartWorkout(template.id, lastWeek))
        var w = requireNotNull(state.activeWorkout)
        state = StateChanges.apply(state, Action.SaveSet(w.id, w.exercises[0].id, "old-1", 40.0, 10, 2.0, false, lastWeek + 60_000))
        state = StateChanges.apply(state, Action.SaveSet(w.id, w.exercises[0].id, "old-2", 42.5, 8, 1.0, false, lastWeek + 240_000))
        state = StateChanges.apply(state, Action.FinishWorkout(lastWeek + 600_000, countForWeek = true))

        // Today: set 1 logged, resting before set 2.
        val now = millis(tuesday, 18, 0)
        state = StateChanges.apply(state, Action.StartWorkout(template.id, now))
        w = requireNotNull(state.activeWorkout)
        state = StateChanges.apply(state, Action.SaveSet(w.id, w.exercises[0].id, "new-1", 40.0, 10, 2.0, false, now + 60_000))

        val model = state.widgetModel(now + 70_000, utc, us) as WidgetModel.Resting
        assertEquals("Set\u00A02\u00A0of\u00A02", model.setLine)
        val last = requireNotNull(model.lastTimeSet)
        assertEquals(42.5, last.amount!!, 0.0)
        assertEquals(8, last.reps)
        assertEquals(w.exercises[0].exercise, model.lastTimeExercise)
    }

    @Test
    fun openDropSetSurfacesInActiveModelAndSpokenSummary() {
        val tuesday = LocalDate.of(2026, 9, 15)
        val startAt = millis(tuesday, 18, 0)
        var state = baseState()
        val template = state.scheduledRoutines(Phase.INTRO_TWO).first { it.weekday == 2 }
        state = StateChanges.apply(state, Action.StartWorkout(template.id, startAt))
        val w = requireNotNull(state.activeWorkout)
        val ex = w.exercises.first()
        state = StateChanges.apply(
            state,
            Action.LogStage(w.id, ex.id, "grp-1", SetMethod.DROP, "st-1", 40.0, 10, 2.0, startAt + 10_000)
        )

        val model = state.widgetModel(startAt + 20_000, utc, us) as WidgetModel.Active
        assertEquals("drop set in progress", model.openSetLabel)
        assertTrue(model.spokenSummary().contains("drop set in progress"))
    }

    @Test
    fun partialTodayAndMultiRoutineSameDayKeepBothFacts() {
        val tuesday = LocalDate.of(2026, 9, 15)
        val now = millis(tuesday, 10, 0)
        var state = baseState()
        val firstEx = state.exercises.first()
        state = StateChanges.apply(
            state,
            Action.SaveRoutine("r-core", "Core", listOf(RoutineExercise(firstEx.id, 2, 8, 12, 90, perLeg = false)))
        )
        state = StateChanges.apply(
            state,
            Action.ScheduleRoutine("p-core", Phase.INTRO_TWO, 2, "r-core", alongside = true)
        )
        val firstTue = state.scheduledRoutines(Phase.INTRO_TWO).first { it.weekday == 2 }
        state = StateChanges.apply(state, Action.StartWorkout(firstTue.id, now))
        val w = requireNotNull(state.activeWorkout)
        state = StateChanges.apply(state, Action.SaveSet(w.id, w.exercises.first().id, "s-part", 30.0, 10, 3.0, false, now + 5_000))
        state = StateChanges.apply(state, Action.FinishWorkout(now + 60_000, countForWeek = false))

        val model = state.widgetModel(now + 120_000, utc, us) as WidgetModel.Idle
        assertNotNull(model.note)
        assertTrue(model.note!!.contains("Partial"))
        assertTrue(model.note!!.contains("Also today: Core"))
    }

    @Test
    fun splitStartedLateOffersOptionalTodayCopy() {
        val sunday = LocalDate.of(2026, 9, 20)
        val state = StateChanges.apply(
            Seed.initialState(),
            Action.CompleteSetup(Experience.RETURNING, "full-body-3", Gear.GYM, MassUnit.KG, sunday.toString())
        )
        val model = state.widgetModel(millis(sunday, 10, 0), utc, us) as WidgetModel.Idle
        assertEquals(TodayKind.REST, model.kind)
        assertEquals(0, model.weekTotal)
        assertNotNull(model.note)
        assertTrue(model.note!!.startsWith("Optional today:"))
        assertTrue(model.rows.isEmpty())
    }

    private fun finishWorkoutCounting(state: AppState, templateId: String, startAt: Long): AppState {
        var s = StateChanges.apply(state, Action.StartWorkout(templateId, startAt))
        val w = requireNotNull(s.activeWorkout)
        val firstEx = w.exercises.first()
        s = StateChanges.apply(s, Action.SaveSet(w.id, firstEx.id, "set-$templateId", 40.0, 10, 3.0, false, startAt + 60_000))
        s = StateChanges.apply(s, Action.FinishWorkout(startAt + 300_000, countForWeek = true))
        return s
    }
}
