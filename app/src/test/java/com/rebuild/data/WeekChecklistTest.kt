package com.rebuild.data

import com.rebuild.domain.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.TimeZone

class WeekChecklistTest {
    private val addis = ZoneId.of("Africa/Addis_Ababa")
    private val newYork = ZoneId.of("America/New_York")
    private var ids = 0

    private enum class Work { FULL, PARTIAL, WARMUP_ONLY, NONE }

    /** A workout started at [start] in its own zone, with [work] logged, finished at [finishAt] unless null. */
    private suspend fun RebuildRepository.session(
        template: String,
        start: ZonedDateTime,
        work: Work = Work.FULL,
        finishAt: ZonedDateTime? = start.plusHours(1),
        countForWeek: Boolean = false
    ): Workout {
        var now = start.toInstant().toEpochMilli()
        execute(Action.StartWorkout(template, now, start.toLocalDate().toString()))
        val workout = state.value.activeWorkout!!
        when (work) {
            Work.FULL -> workout.exercises.forEach { exercise ->
                repeat(exercise.targetSets!!) { log(workout, exercise, id = "set-${ids++}", now = ++now) }
            }
            Work.PARTIAL -> log(workout, workout.exercises.first(), id = "set-${ids++}", now = ++now)
            Work.WARMUP_ONLY -> log(workout, workout.exercises.first(), id = "set-${ids++}", warmup = true, now = ++now)
            Work.NONE -> Unit
        }
        if (finishAt != null) execute(Action.FinishWorkout(finishAt.toInstant().toEpochMilli(), countForWeek = countForWeek))
        return state.value.workouts.single { it.id == workout.id }
    }

    private suspend fun planned(phase: Phase = Phase.BASELINE) = repository().also { it.execute(Action.SelectPhase(phase)) }

    private fun at(zone: ZoneId, y: Int, m: Int, d: Int, h: Int, min: Int = 0) = ZonedDateTime.of(y, m, d, h, min, 0, 0, zone)

    private fun RebuildRepository.week(date: LocalDate, zone: ZoneId = addis) = requireNotNull(state.value.weekChecklist(date, zone))

    @Test fun lowerAOnPushDayMarksLowerADoneAndLeavesPushPending() = runTest {
        val repo = planned()
        val lowerA = repo.session("lower-a", at(addis, 2026, 9, 29, 18))
        val thursday = LocalDate.of(2026, 10, 1)
        val week = repo.week(thursday)
        assertEquals(LocalDate.of(2026, 9, 28), week.start)
        assertEquals(LocalDate.of(2026, 10, 4), week.end)
        assertEquals(listOf("push", "pull", "lower-a", "upper", "lower-b"), week.routines.map { it.template.id })
        assertEquals(RoutineStatus.DONE, week.statusOf("lower-a"))
        assertEquals(lowerA.id, week.routines.single { it.template.id == "lower-a" }.doneBy?.id)
        assertEquals(RoutineStatus.NOT_DONE, week.statusOf("push"))
        assertEquals(1, week.done)
        assertFalse(week.complete)
        assertEquals("the past date is not renamed", "2026-09-29", lowerA.startDate)

        val tuesday = repo.state.value.todayPlan(LocalDate.of(2026, 9, 29), repo.week(LocalDate.of(2026, 9, 29)))
        assertEquals(TodayPlan(TodayKind.SCHEDULED, tuesday.scheduled, tuesday.scheduled), tuesday)
        assertEquals("push", tuesday.scheduled?.id)
        val onThursday = repo.state.value.todayPlan(thursday, week)
        assertEquals(TodayKind.NEXT_UNFINISHED, onThursday.kind)
        assertEquals("lower-a", onThursday.scheduled?.id)
        assertEquals("push", onThursday.suggested?.id)
        val friday = repo.state.value.todayPlan(LocalDate.of(2026, 10, 2), week)
        assertEquals(TodayKind.REST, friday.kind)
        assertNull(friday.scheduled)
        assertEquals("rest days only offer unfinished routines as a choice", "push", friday.suggested?.id)

        // Monday starts a new week from the dated records; last week keeps its credit and nothing carries over.
        val monday = LocalDate.of(2026, 10, 5)
        val next = repo.week(monday)
        assertEquals(monday, next.start)
        assertTrue(next.routines.all { it.status == RoutineStatus.NOT_DONE })
        assertEquals(RoutineStatus.DONE, repo.week(thursday).statusOf("lower-a"))
        assertEquals(listOf(lowerA.id), repo.state.value.workouts.map { it.id })
    }

    @Test fun repeatsCountOnceAndRoutinesWithTheSameNameStayDistinct() = runTest {
        val repo = planned(Phase.INTRO_TWO)
        val first = repo.session("intro-upper", at(addis, 2026, 9, 29, 18))
        repo.session("intro-upper", at(addis, 2026, 10, 1, 18))
        val week = repo.week(LocalDate.of(2026, 10, 2))
        assertEquals(listOf("intro-upper", "lower-a", "upper", "lower-b"), week.routines.map { it.template.id })
        assertEquals(listOf("Upper", "Lower A", "Upper", "Lower B"), week.routines.map { it.template.name })
        assertEquals(RoutineStatus.DONE, week.statusOf("intro-upper"))
        assertEquals(first.id, week.routines.first().doneBy?.id)
        assertEquals("Saturday's Upper is a different routine", RoutineStatus.NOT_DONE, week.statusOf("upper"))
        assertEquals(1, week.done)
        assertTrue(week.other.isEmpty())
    }

    @Test fun weeksRunMondayToMondayByLocalDateAcrossDstAndYearEnd() = runTest {
        assertEquals(LocalDate.of(2026, 12, 28), weekStartOf(LocalDate.of(2027, 1, 1)))
        assertEquals(LocalDate.of(2026, 12, 21), weekStartOf(LocalDate.of(2026, 12, 27)))
        assertEquals(LocalDate.of(2026, 12, 28), weekStartOf(LocalDate.of(2026, 12, 28)))
        assertEquals(LocalDate.of(2027, 12, 27), weekStartOf(LocalDate.of(2028, 1, 2)))

        val repo = planned()
        // The 2–9 March 2026 week in New York lasts 167 hours; 00:30 on Monday 9 March is already the next week.
        val spring = at(newYork, 2026, 3, 9, 0, 30)
        assertTrue(spring.toInstant().isBefore(at(newYork, 2026, 3, 2, 0).toInstant().plusSeconds(168 * 3600L)))
        repo.session("lower-a", spring)
        // The 26 October–2 November week lasts 169 hours; 23:30 on Sunday 1 November is still that week.
        val autumn = at(newYork, 2026, 11, 1, 23, 30)
        assertFalse(autumn.toInstant().isBefore(at(newYork, 2026, 10, 26, 0).toInstant().plusSeconds(168 * 3600L)))
        repo.session("lower-b", autumn)
        // New Year's Day 2027 is a Friday in the week that began on 28 December 2026.
        repo.session("upper", at(newYork, 2027, 1, 1, 9))

        assertEquals(RoutineStatus.DONE, repo.week(LocalDate.of(2026, 3, 9), newYork).statusOf("lower-a"))
        assertEquals(RoutineStatus.NOT_DONE, repo.week(LocalDate.of(2026, 3, 8), newYork).statusOf("lower-a"))
        assertEquals(RoutineStatus.DONE, repo.week(LocalDate.of(2026, 10, 26), newYork).statusOf("lower-b"))
        assertEquals(RoutineStatus.NOT_DONE, repo.week(LocalDate.of(2026, 11, 2), newYork).statusOf("lower-b"))
        assertEquals(RoutineStatus.DONE, repo.week(LocalDate.of(2026, 12, 28), newYork).statusOf("upper"))
        assertEquals(RoutineStatus.DONE, repo.week(LocalDate.of(2027, 1, 3), newYork).statusOf("upper"))
        assertEquals(RoutineStatus.NOT_DONE, repo.week(LocalDate.of(2027, 1, 4), newYork).statusOf("upper"))
    }

    @Test fun newWorkoutsRecordThePhonesLocalStartDateAndKeepItAcrossZones() = runTest {
        val original = TimeZone.getDefault()
        val repo = planned()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(newYork))
            val mondayEarly = at(newYork, 2026, 3, 9, 0, 30).toInstant().toEpochMilli()
            repo.execute(Action.StartWorkout("lower-a", mondayEarly))
            assertEquals("2026-03-09", repo.state.value.activeWorkout!!.startDate)
            repo.execute(Action.FinishWorkout(mondayEarly + 60_000))
        } finally {
            TimeZone.setDefault(original)
        }
        // Read later in another zone, the recorded date still decides the week deterministically.
        val workout = repo.state.value.workouts.single()
        assertEquals(LocalDate.of(2026, 3, 9), workout.weekStart(addis))
        assertEquals(LocalDate.of(2026, 3, 9), workout.weekStart(ZoneId.of("Pacific/Kiritimati")))
        // Older records without a date are read in the phone's current zone until migration stores one.
        val legacy = workout.copy(startDate = null)
        assertEquals(LocalDate.of(2026, 3, 9), legacy.weekStart(newYork))
        assertEquals(LocalDate.of(2026, 3, 9), legacy.weekStart(addis))
        assertEquals(LocalDate.of(2026, 3, 2), legacy.weekStart(ZoneId.of("Pacific/Honolulu")))
    }

    @Test fun aWorkoutStartedLateSundayCreditsOnlyTheWeekItStartedIn() = runTest {
        val repo = planned()
        repo.session("lower-b", at(addis, 2026, 10, 4, 23, 50), finishAt = at(addis, 2026, 10, 5, 0, 20))
        assertEquals(RoutineStatus.DONE, repo.week(LocalDate.of(2026, 10, 4)).statusOf("lower-b"))
        val next = repo.week(LocalDate.of(2026, 10, 5))
        assertEquals(RoutineStatus.NOT_DONE, next.statusOf("lower-b"))
        assertEquals(0, next.done)
        assertTrue(next.other.isEmpty())
    }

    @Test fun theLocaleFirstDayOfWeekNeverMovesTheWeek() {
        val original = Locale.getDefault()
        try {
            listOf(Locale.US, Locale.FRANCE, Locale.forLanguageTag("ar-SA"), Locale.forLanguageTag("am-ET")).forEach { locale ->
                Locale.setDefault(locale)
                assertEquals(LocalDate.of(2026, 9, 28), weekStartOf(LocalDate.of(2026, 10, 4)))
                assertEquals(LocalDate.of(2026, 10, 5), weekStartOf(LocalDate.of(2026, 10, 5)))
            }
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test fun emptyWarmupOnlyAndDiscardedWorkoutsNeverCount() = runTest {
        val repo = planned()
        repo.session("push", at(addis, 2026, 9, 29, 7), Work.NONE)
        repo.session("pull", at(addis, 2026, 9, 30, 7), Work.WARMUP_ONLY)
        repo.session("lower-a", at(addis, 2026, 10, 1, 7), Work.FULL, finishAt = null)
        repo.execute(Action.DiscardWorkout)
        val week = repo.week(LocalDate.of(2026, 10, 2))
        assertEquals(listOf(RoutineStatus.NOT_DONE), week.routines.map { it.status }.distinct())
        assertEquals(0, week.done)
        val empty = repo.state.value.workouts.first()
        failure("at least one working set") { repo.execute(Action.SetWeekCredit(empty.id, true)) }
        val warmups = repo.state.value.workouts[1]
        failure("at least one working set") { repo.execute(Action.SetWeekCredit(warmups.id, true)) }
        repo.execute(Action.StartWorkout("upper", at(addis, 2026, 10, 3, 7).toInstant().toEpochMilli(), "2026-10-03"))
        failure("at least one working set") { repo.execute(Action.FinishWorkout(at(addis, 2026, 10, 3, 8).toInstant().toEpochMilli(), countForWeek = true)) }
        assertEquals(RoutineStatus.IN_PROGRESS, repo.week(LocalDate.of(2026, 10, 3)).statusOf("upper"))

        // Even a credit flag written into stored data can't make an empty workout count.
        val forged = repo.state.value.copy(workouts = repo.state.value.workouts.map {
            if (it.id == empty.id) it.copy(countedForWeek = true) else it
        })
        assertFalse(forged.workouts.first().countsForWeek())
        assertEquals(RoutineStatus.NOT_DONE, forged.weekChecklist(LocalDate.of(2026, 10, 2), addis)!!.statusOf("push"))
    }

    @Test fun aPartialWorkoutCountsOnlyByExplicitChoiceAndUncountingNeverUndoesAFullOne() = runTest {
        val repo = planned()
        val partial = repo.session("push", at(addis, 2026, 9, 29, 7), Work.PARTIAL)
        val day = LocalDate.of(2026, 10, 2)
        assertEquals(RoutineStatus.PARTIAL, repo.week(day).statusOf("push"))
        assertFalse(partial.countedForWeek)
        assertEquals("a partial routine is still offered", "push", repo.state.value.todayPlan(day, repo.week(day)).suggested?.id)

        repo.execute(Action.SetWeekCredit(partial.id, true))
        assertEquals(RoutineStatus.DONE, repo.week(day).statusOf("push"))
        assertEquals("pull", repo.state.value.todayPlan(day, repo.week(day)).suggested?.id)
        assertEquals("credit is not logged work", 1, repo.state.value.workouts.single().workingSetCount())
        repo.execute(Action.SetWeekCredit(partial.id, false))
        assertEquals(RoutineStatus.PARTIAL, repo.week(day).statusOf("push"))

        val chosen = repo.session("pull", at(addis, 2026, 9, 30, 7), Work.PARTIAL, countForWeek = true)
        assertTrue(chosen.countedForWeek)
        assertEquals(RoutineStatus.DONE, repo.week(day).statusOf("pull"))

        val full = repo.session("lower-a", at(addis, 2026, 10, 1, 7))
        assertFalse(full.countedForWeek)
        repo.execute(Action.SetWeekCredit(full.id, false))
        assertEquals("unchecking can't undo a routine whose targets were all met",
            RoutineStatus.DONE, repo.week(day).statusOf("lower-a"))

        // Deleting a logged set re-derives the status; explicit credit stays a separate choice.
        val exercise = full.exercises.first()
        repo.execute(Action.DeleteSet(full.id, exercise.id, exercise.sets.first().id))
        assertEquals(RoutineStatus.PARTIAL, repo.week(day).statusOf("lower-a"))
        repo.execute(Action.SetWeekCredit(full.id, true))
        assertEquals(RoutineStatus.DONE, repo.week(day).statusOf("lower-a"))
        val keptCredit = repo.state.value.workouts.single { it.id == chosen.id }
        keptCredit.exercises.flatMap { e -> e.sets.map { e to it } }.forEach { (e, set) ->
            repo.execute(Action.DeleteSet(chosen.id, e.id, set.id))
        }
        assertEquals("credit without any working set left doesn't count",
            RoutineStatus.NOT_DONE, repo.week(day).statusOf("pull"))
    }

    @Test fun aFinishedDropSetCountsAsOneSetTowardTheWeeksTargets() = runTest {
        val repo = planned(Phase.INTRO_ONE)
        val start = at(addis, 2026, 10, 1, 7)
        var now = start.toInstant().toEpochMilli()
        repo.execute(Action.StartWorkout("lower-a", now, "2026-10-01"))
        val workout = repo.state.value.activeWorkout!!
        workout.exercises.forEachIndexed { index, exercise ->
            if (index == 0) {
                listOf(100.0, 80.0, 60.0).forEachIndexed { stage, kg ->
                    repo.execute(Action.LogStage(workout.id, exercise.id, "g", SetMethod.DROP, "g-$stage", kg, 10, null, ++now))
                }
                assertEquals(RoutineStatus.IN_PROGRESS, repo.week(start.toLocalDate()).statusOf("lower-a"))
                repo.execute(Action.FinishGroup(workout.id, exercise.id, "g", ++now))
            } else repo.log(workout, exercise, id = "n-$index", now = ++now)
        }
        repo.execute(Action.FinishWorkout(++now))
        val finished = repo.state.value.workouts.single()
        assertEquals(workout.exercises.size, finished.workingSetCount())
        assertTrue(finished.completesRoutine())
        assertEquals(RoutineStatus.DONE, repo.week(start.toLocalDate()).statusOf("lower-a"))

        // Three stages of one set are not three sets: with a target of two, one drop set leaves it partial.
        val two = planned(Phase.INTRO_TWO)
        two.execute(Action.StartWorkout("lower-a", now, "2026-10-01"))
        val second = two.state.value.activeWorkout!!
        second.exercises.forEachIndexed { index, exercise ->
            if (index == 0) {
                listOf(100.0, 80.0, 60.0).forEachIndexed { stage, kg ->
                    two.execute(Action.LogStage(second.id, exercise.id, "h", SetMethod.DROP, "h-$stage", kg, 10, null, ++now))
                }
                two.execute(Action.FinishGroup(second.id, exercise.id, "h", ++now))
            } else repeat(exercise.targetSets!!) { two.log(second, exercise, id = "m-$index-$it", now = ++now) }
        }
        two.execute(Action.FinishWorkout(++now))
        assertEquals(RoutineStatus.PARTIAL, two.week(start.toLocalDate()).statusOf("lower-a"))
    }

    @Test fun changingPhaseChangesTheChecklistButKeepsCreditJudgedOnSavedTargets() = runTest {
        val repo = planned(Phase.INTRO_TWO)
        val intro = repo.session("intro-upper", at(addis, 2026, 9, 29, 18))
        val lowerA = repo.session("lower-a", at(addis, 2026, 10, 1, 18))
        assertTrue(lowerA.exercises.all { it.targetSets == 2 })
        repo.execute(Action.SelectPhase(Phase.BASELINE))

        val week = repo.week(LocalDate.of(2026, 10, 2))
        assertEquals(listOf("push", "pull", "lower-a", "upper", "lower-b"), week.routines.map { it.template.id })
        assertEquals("judged on the targets saved with the workout", RoutineStatus.DONE, week.statusOf("lower-a"))
        assertEquals("the four-day Upper doesn't satisfy Push", RoutineStatus.NOT_DONE, week.statusOf("push"))
        assertEquals(listOf(intro.id), week.other.map { it.id })
        assertEquals(1, week.done)
        assertEquals(listOf(Phase.INTRO_TWO, Phase.INTRO_TWO), repo.state.value.workouts.map { it.phase })
    }

    @Test fun everyRoutineDoneOffersRestOrARepeatThatCountsOnce() = runTest {
        val repo = planned()
        listOf("push" to 29, "pull" to 30).forEach { (id, day) -> repo.session(id, at(addis, 2026, 9, day, 7)) }
        listOf("lower-a" to 1, "upper" to 3, "lower-b" to 4).forEach { (id, day) -> repo.session(id, at(addis, 2026, 10, day, 7)) }
        val sunday = LocalDate.of(2026, 10, 4)
        val week = repo.week(sunday)
        assertTrue(week.complete)
        assertEquals(5, week.done)
        val plan = repo.state.value.todayPlan(sunday, week)
        assertEquals(TodayKind.WEEK_COMPLETE, plan.kind)
        assertEquals("lower-b", plan.scheduled?.id)
        assertNull("nothing is pushed once the week is done", plan.suggested)

        repo.session("lower-b", at(addis, 2026, 10, 4, 18))
        assertEquals(5, repo.week(sunday).done)
        val restored = SnapshotCodec.decode(SnapshotCodec.encode(repo.state.value))
        assertEquals(repo.week(sunday), requireNotNull(restored.weekChecklist(sunday, addis)))
        assertEquals("a new week is not started early", 0, repo.week(LocalDate.of(2026, 10, 5)).done)
    }
}
