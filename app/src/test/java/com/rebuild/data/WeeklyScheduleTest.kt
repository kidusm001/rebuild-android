package com.rebuild.data

import com.rebuild.domain.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class WeeklyScheduleTest {
    private val addis = ZoneId.of("Africa/Addis_Ababa")
    /** Monday 28 September 2026; its week runs to Sunday 4 October. */
    private val monday = LocalDate.of(2026, 9, 28)
    private val face = RoutineExercise("face-pull", 2, 12, 20, 75, false)
    private val hammer = RoutineExercise("curl-hammer", 2, 10, 12, 60, false)
    private val established = listOf("push", "pull", "lower-a", "upper", "lower-b")
    private var ids = 0

    private fun at(date: LocalDate, hour: Int) = ZonedDateTime.of(date.atTime(hour, 0), addis).toInstant().toEpochMilli()

    /** Baseline phase with library routine Arms (face pulls and hammer curls). */
    private suspend fun planned(store: MemoryPersistence = MemoryPersistence()): RebuildRepository {
        val repo = repository(store)
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        repo.execute(Action.SaveRoutine("r-arms", "Arms", listOf(face, hammer)))
        return repo
    }

    private fun RebuildRepository.week(date: LocalDate = monday) = requireNotNull(state.value.weekChecklist(date, addis))
    private fun RebuildRepository.weekIds(phase: Phase = Phase.BASELINE) = state.value.scheduledRoutines(phase).map { it.id }
    private fun RebuildRepository.today(date: LocalDate) = state.value.todayPlan(date, week(date))

    /** Starts [occurrence] on [date], logs every target (or a single set), and finishes it. */
    private suspend fun RebuildRepository.train(
        occurrence: String,
        date: LocalDate,
        full: Boolean = true,
        countForWeek: Boolean = false
    ): Workout {
        var now = at(date, 18)
        execute(Action.StartWorkout(occurrence, now, date.toString()))
        val workout = state.value.activeWorkout!!
        if (full) workout.exercises.forEach { e -> repeat(e.targetSets!!) { log(workout, e, id = "set-${ids++}", now = ++now) } }
        else log(workout, workout.exercises.first(), id = "set-${ids++}", now = ++now)
        execute(Action.FinishWorkout(now + 1, countForWeek = countForWeek))
        return state.value.workouts.single { it.id == workout.id }
    }

    @Test fun schedulingOnAFreeDayAddsAnIndependentCopyAndRepeatingTheRequestChangesNothing() = runTest {
        val store = MemoryPersistence()
        val repo = planned(store)
        repo.execute(Action.ScheduleRoutine("p-mon", Phase.BASELINE, 1, "r-arms"))
        val scheduled = repo.state.value
        val copy = scheduled.placements.single()
        assertEquals(RoutinePlacement("p-mon", Phase.BASELINE, 1, "Arms", "r-arms"), copy)
        assertEquals(listOf(face, hammer), scheduled.placementExercises(copy))
        val additions = scheduled.planAdditions.filter { it.templateId == "p-mon" }
        assertEquals(scheduled.planAdditions, additions)
        assertTrue(additions.all { it.phase == Phase.BASELINE })
        assertEquals(2, additions.map { it.id }.distinct().size)
        assertEquals(listOf("p-mon") + established, repo.weekIds())
        assertEquals("Other phases keep their own week", established, repo.weekIds(Phase.FIVE_TWO))
        assertEquals(listOf("r-arms"), scheduled.routines.map { it.id })
        val writes = store.writes
        repo.execute(Action.ScheduleRoutine("p-mon", Phase.BASELINE, 1, "r-arms"))
        repo.execute(Action.ScheduleRoutine("p-mon", Phase.BASELINE, 1, "r-arms", alongside = true))
        repo.execute(Action.ScheduleRoutine("p-mon", Phase.BASELINE, 1, "r-arms", replacingId = "push"))
        assertEquals("A repeated request never adds, replaces or settles anything again", scheduled, repo.state.value)
        assertEquals(writes, store.writes)
        // The same ID for anything else is refused, never a new schedule.
        repo.execute(Action.SaveRoutine("r-legs", "Legs", listOf(RoutineExercise("lunge-db", 2, 8, 12, 120, true))))
        failure("schedule ID is already used") { repo.execute(Action.ScheduleRoutine("p-mon", Phase.FIVE_TWO, 1, "r-arms")) }
        failure("schedule ID is already used") { repo.execute(Action.ScheduleRoutine("p-mon", Phase.BASELINE, 5, "r-arms")) }
        failure("schedule ID is already used") { repo.execute(Action.ScheduleRoutine("p-mon", Phase.BASELINE, 1, "r-legs")) }
        failure("schedule ID is already used") { repo.execute(Action.ScheduleRoutine("push", Phase.BASELINE, 1, "r-arms")) }
        failure("schedule ID is already used") { repo.execute(Action.ScheduleRoutine("r-legs", Phase.BASELINE, 1, "r-arms")) }
        failure("routine ID is already used") { repo.execute(Action.SaveRoutine("p-mon", "Copy", listOf(face))) }
        failure("Monday to Sunday") { repo.execute(Action.ScheduleRoutine("p-x", Phase.BASELINE, 0, "r-arms")) }
        failure("Monday to Sunday") { repo.execute(Action.ScheduleRoutine("p-x", Phase.BASELINE, 8, "r-arms")) }
        failure("This routine no longer exists.") { repo.execute(Action.ScheduleRoutine("p-x", Phase.BASELINE, 1, "nope")) }
        assertEquals(listOf("p-mon"), repo.state.value.placements.map { it.id })
        // A copy edited afterwards is never overwritten by a late retry, even after its routine is deleted.
        repo.execute(Action.SavePlacement("p-mon", "Monday arms", listOf(hammer.copy(sets = 4))))
        val edited = repo.state.value
        repo.execute(Action.ScheduleRoutine("p-mon", Phase.BASELINE, 1, "r-arms"))
        assertEquals(edited, repo.state.value)
        repo.execute(Action.DeleteRoutine("r-arms"))
        val deleted = repo.state.value
        repo.execute(Action.ScheduleRoutine("p-mon", Phase.BASELINE, 1, "r-arms"))
        assertEquals(deleted, repo.state.value)
        assertEquals(listOf(hammer.copy(sets = 4)), deleted.placementExercises(deleted.placements.single()))
    }

    @Test fun aTakenDayNeedsAnExplicitChoiceAndReplaceTakesOffExactlyThatOccurrence() = runTest {
        val repo = planned()
        val before = repo.state.value
        failure("That day already has Push. Choose Add alongside or Replace.") {
            repo.execute(Action.ScheduleRoutine("p-tue", Phase.BASELINE, 2, "r-arms"))
        }
        failure("Choose either Add alongside or Replace, not both.") {
            repo.execute(Action.ScheduleRoutine("p-tue", Phase.BASELINE, 2, "r-arms", alongside = true, replacingId = "push"))
        }
        failure("no longer on that day") {
            repo.execute(Action.ScheduleRoutine("p-tue", Phase.BASELINE, 2, "r-arms", replacingId = "pull"))
        }
        failure("no longer on that day") {
            repo.execute(Action.ScheduleRoutine("p-tue", Phase.BASELINE, 2, "r-arms", replacingId = "intro-upper"))
        }
        assertEquals("A refused request changes nothing", before, repo.state.value)
        repo.execute(Action.ScheduleRoutine("p-tue", Phase.BASELINE, 2, "r-arms", alongside = true))
        assertEquals(listOf("push", "p-tue", "pull", "lower-a", "upper", "lower-b"), repo.weekIds())
        failure("That day already has Push, Arms. Choose Add alongside or Replace.") {
            repo.execute(Action.ScheduleRoutine("p-tue2", Phase.BASELINE, 2, "r-arms"))
        }
        repo.execute(Action.ScheduleRoutine("p-wed", Phase.BASELINE, 3, "r-arms", replacingId = "pull"))
        val replaced = repo.state.value
        assertEquals(listOf(ScheduleRemoval(Phase.BASELINE, "pull")), replaced.scheduleRemovals)
        assertEquals(listOf("push", "p-tue", "p-wed", "lower-a", "upper", "lower-b"), repo.weekIds())
        val pull = replaced.templates.single { it.id == "pull" }
        assertEquals("Pull keeps its plan, to be put back later", before.plannedExercises(pull, Phase.BASELINE),
            replaced.plannedExercises(pull, Phase.BASELINE))
        assertEquals(listOf("pull"), replaced.removedTemplates(Phase.BASELINE).map { it.id })
        assertEquals("Five-two keeps Pull", established, repo.weekIds(Phase.FIVE_TWO))
        // Replacing a copy no workout used deletes it with its exercises; only that one occurrence goes.
        repo.execute(Action.ScheduleRoutine("p-tue3", Phase.BASELINE, 2, "r-arms", replacingId = "p-tue"))
        val after = repo.state.value
        assertNull(after.placements.find { it.id == "p-tue" })
        assertTrue(after.planAdditions.none { it.templateId == "p-tue" })
        assertEquals(listOf("push", "p-tue3", "p-wed", "lower-a", "upper", "lower-b"), repo.weekIds())
        assertEquals(replaced.planAdditions.filter { it.templateId == "p-wed" }, after.planAdditions.filter { it.templateId == "p-wed" })
        assertTrue(after.workouts.isEmpty())
    }

    @Test fun theSameRoutineOnTwoDaysIsTwoOccurrencesWithTheirOwnCredit() = runTest {
        val repo = planned()
        repo.execute(Action.ScheduleRoutine("p-mon", Phase.BASELINE, 1, "r-arms"))
        repo.execute(Action.ScheduleRoutine("p-fri", Phase.BASELINE, 5, "r-arms"))
        val week = repo.week()
        assertEquals(listOf("p-mon", "push", "pull", "lower-a", "p-fri", "upper", "lower-b"), week.routines.map { it.template.id })
        assertEquals(listOf("Arms", "Arms"), week.routines.filter { it.template is RoutinePlacement }.map { it.template.name })
        val first = repo.train("p-mon", monday)
        repo.week().let { after ->
            assertEquals(RoutineStatus.DONE, after.statusOf("p-mon"))
            assertEquals(first.id, after.routines.single { it.template.id == "p-mon" }.doneBy?.id)
            assertEquals("The same name on Friday is still to do", RoutineStatus.NOT_DONE, after.statusOf("p-fri"))
            assertEquals(1, after.done)
        }
        // Repeating Monday's copy on Friday never ticks Friday's copy or adds a second credit.
        val friday = monday.plusDays(4)
        repo.train("p-mon", friday)
        repo.week().let { after ->
            assertEquals(first.id, after.routines.single { it.template.id == "p-mon" }.doneBy?.id)
            assertEquals(RoutineStatus.NOT_DONE, after.statusOf("p-fri"))
            assertEquals(1, after.done)
            val today = repo.today(friday)
            assertEquals(TodayKind.SCHEDULED, today.kind)
            assertEquals("p-fri", today.scheduled?.id)
        }
        repo.train("p-fri", friday, full = false, countForWeek = true)
        assertEquals(RoutineStatus.DONE, repo.week().statusOf("p-fri"))
        assertEquals(2, repo.week().done)
    }

    @Test fun twoCopiesOnOneDayAreTwoOccurrencesAndTodayOffersTheRemainingOneFirst() = runTest {
        val repo = planned()
        repo.execute(Action.ScheduleRoutine("p-mon", Phase.BASELINE, 1, "r-arms"))
        repo.execute(Action.ScheduleRoutine("p-mon2", Phase.BASELINE, 1, "r-arms", alongside = true))
        val week = repo.week()
        assertEquals(listOf("p-mon", "p-mon2") + established, week.routines.map { it.template.id })
        val first = week.routines[0].template
        assertEquals(TodayPlan(TodayKind.SCHEDULED, first, first), repo.today(monday))
        repo.train("p-mon", monday)
        repo.today(monday).let { today ->
            assertEquals("The remaining copy today comes before any other day", TodayKind.SCHEDULED, today.kind)
            assertEquals("p-mon2", today.scheduled?.id)
            assertEquals("p-mon2", today.suggested?.id)
        }
        assertEquals(RoutineStatus.NOT_DONE, repo.week().statusOf("p-mon2"))
        val partial = repo.train("p-mon2", monday, full = false)
        assertEquals(RoutineStatus.PARTIAL, repo.week().statusOf("p-mon2"))
        assertEquals("p-mon2", repo.today(monday).scheduled?.id)
        repo.execute(Action.SetWeekCredit(partial.id, true))
        repo.today(monday).let { today ->
            assertEquals(TodayKind.NEXT_UNFINISHED, today.kind)
            assertEquals("p-mon", today.scheduled?.id)
            assertEquals("push", today.suggested?.id)
        }
        repo.execute(Action.SetWeekCredit(partial.id, false))
        assertEquals("Removing explicit credit re-derives the status", RoutineStatus.PARTIAL, repo.week().statusOf("p-mon2"))
        assertEquals(RoutineStatus.DONE, repo.week().statusOf("p-mon"))
    }

    @Test fun aDayWithoutALiveOccurrenceIsAnHonestRestDayAndScheduleChangesApplyAtOnce() = runTest {
        val repo = planned()
        repo.today(monday).let { today ->
            assertEquals(TodayKind.REST, today.kind)
            assertNull(today.scheduled)
            assertEquals("Unfinished routines are only an optional choice", "push", today.suggested?.id)
        }
        repo.execute(Action.ScheduleRoutine("p-mon", Phase.BASELINE, 1, "r-arms"))
        val first = repo.week().routines.first().template
        assertEquals(TodayPlan(TodayKind.SCHEDULED, first, first), repo.today(monday))
        repo.execute(Action.MovePlacement("p-mon", 3, alongside = true))
        assertEquals(TodayKind.REST, repo.today(monday).kind)
        repo.today(monday.plusDays(2)).let { wednesday ->
            assertEquals(TodayKind.SCHEDULED, wednesday.kind)
            assertEquals("pull", wednesday.scheduled?.id)
        }
        repo.execute(Action.RemoveFromWeeklyPlan(Phase.BASELINE, "push"))
        repo.today(monday.plusDays(1)).let { tuesday ->
            assertEquals("Tuesday is a rest day once Push is off the week", TodayKind.REST, tuesday.kind)
            assertEquals("pull", tuesday.suggested?.id)
        }
        // A copy taken off with its workout leaves the day without a live occurrence too.
        repo.execute(Action.ScheduleRoutine("p-thu", Phase.BASELINE, 4, "r-arms", replacingId = "lower-a"))
        repo.train("p-thu", monday.plusDays(3), full = false)
        repo.execute(Action.RemoveFromWeeklyPlan(Phase.BASELINE, "p-thu"))
        repo.today(monday.plusDays(3)).let { thursday ->
            assertEquals(TodayKind.REST, thursday.kind)
            assertNull(thursday.scheduled)
        }
        assertEquals("Its workout is listed as another routine's", listOf("p-thu"),
            repo.week().other.map { it.templateId })
    }

    @Test fun movingTakingOffAndPuttingBackKeepIdentityCreditAndEveryWorkout() = runTest {
        val store = MemoryPersistence()
        val repo = planned(store)
        repo.execute(Action.ScheduleRoutine("p-mon", Phase.BASELINE, 1, "r-arms"))
        val done = repo.train("p-mon", monday)
        assertEquals(RoutineStatus.DONE, repo.week().statusOf("p-mon"))
        repo.execute(Action.MovePlacement("p-mon", 5))
        assertEquals(RoutinePlacement("p-mon", Phase.BASELINE, 5, "Arms", "r-arms"), repo.state.value.placements.single())
        assertEquals("Credit belongs to the occurrence, not its day", RoutineStatus.DONE, repo.week().statusOf("p-mon"))
        val writes = store.writes
        repo.execute(Action.MovePlacement("p-mon", 5))
        repo.execute(Action.MovePlacement("p-mon", 5, replacingId = "nope"))
        assertEquals("Moving to the day it is on changes nothing", writes, store.writes)
        failure("That day already has Push. Choose Add alongside or Replace.") { repo.execute(Action.MovePlacement("p-mon", 2)) }
        failure("not both") { repo.execute(Action.MovePlacement("p-mon", 2, alongside = true, replacingId = "push")) }
        failure("no longer on that day") { repo.execute(Action.MovePlacement("p-mon", 2, replacingId = "pull")) }
        failure("Monday to Sunday") { repo.execute(Action.MovePlacement("p-mon", 0)) }
        failure("no longer on your weekly plan") { repo.execute(Action.MovePlacement("push", 1)) }
        repo.execute(Action.MovePlacement("p-mon", 2, alongside = true))
        assertEquals(listOf("push", "p-mon", "pull", "lower-a", "upper", "lower-b"), repo.weekIds())
        repo.execute(Action.MovePlacement("p-mon", 3, replacingId = "pull"))
        assertEquals(listOf("push", "p-mon", "lower-a", "upper", "lower-b"), repo.weekIds())
        assertEquals(listOf(ScheduleRemoval(Phase.BASELINE, "pull")), repo.state.value.scheduleRemovals)
        assertEquals(listOf(done), repo.state.value.workouts)

        // Taken off, a used copy stays as a record of its workouts that nothing brings back.
        repo.execute(Action.RemoveFromWeeklyPlan(Phase.BASELINE, "p-mon"))
        val removed = repo.state.value
        assertEquals(RoutinePlacement("p-mon", Phase.BASELINE, 3, "Arms", "r-arms", removedFromPlan = true),
            removed.placements.single())
        assertTrue(removed.planAdditions.none { it.templateId == "p-mon" })
        assertEquals(listOf(done), removed.workouts)
        assertNull(repo.week().statusOf("p-mon"))
        assertEquals(listOf(done.id), repo.week().other.map { it.id })
        assertEquals("History still names the copy it came from", "p-mon", removed.occurrenceOf(done)?.id)
        assertTrue(removed.linkedPlacements("r-arms", Phase.BASELINE).isEmpty())
        assertTrue(removed.allLinkedPlacements("r-arms").isEmpty())
        repo.execute(Action.RemoveFromWeeklyPlan(Phase.BASELINE, "p-mon"))
        repo.execute(Action.ScheduleRoutine("p-mon", Phase.BASELINE, 3, "r-arms"))
        assertEquals("Taking off again or a late retry never brings it back", removed, repo.state.value)
        failure("no longer on your weekly plan") { repo.execute(Action.MovePlacement("p-mon", 1)) }
        failure("no longer on your weekly plan") { repo.execute(Action.SavePlacement("p-mon", "Arms", listOf(face))) }
        failure("no longer on your weekly plan") { repo.execute(Action.UpdatePlacementFromRoutine("p-mon")) }
        failure("not available") { repo.execute(Action.StartWorkout("p-mon", at(monday, 20), monday.toString())) }
        failure("not available") {
            repo.execute(Action.SavePlanAddition("a-1", Phase.BASELINE, "p-mon", "row-stack", 2, 8, 12, 90, false))
        }
        failure("isn't part of that phase") { repo.execute(Action.RestoreToWeeklyPlan(Phase.BASELINE, "p-mon")) }
        assertEquals(removed, repo.state.value)

        // An original routine is only marked, so it can be put back on its own day with the same explicit choice.
        repo.execute(Action.ScheduleRoutine("p-wed", Phase.BASELINE, 3, "r-arms"))
        failure("That day already has Arms. Choose Add alongside or Replace.") {
            repo.execute(Action.RestoreToWeeklyPlan(Phase.BASELINE, "pull"))
        }
        repo.execute(Action.RestoreToWeeklyPlan(Phase.BASELINE, "pull", replacingId = "p-wed"))
        assertEquals(established, repo.weekIds())
        assertTrue(repo.state.value.scheduleRemovals.isEmpty())
        assertEquals("The unused copy it replaced leaves no record", listOf("p-mon"), repo.state.value.placements.map { it.id })
        val restored = repo.state.value
        repo.execute(Action.RestoreToWeeklyPlan(Phase.BASELINE, "pull"))
        repo.execute(Action.RemoveFromWeeklyPlan(Phase.BASELINE, "never-scheduled"))
        repo.execute(Action.RemoveFromWeeklyPlan(Phase.BASELINE, "intro-upper"))
        assertEquals(restored, repo.state.value)
        failure("isn't part of that phase") { repo.execute(Action.RestoreToWeeklyPlan(Phase.BASELINE, "intro-upper")) }
        repo.execute(Action.RemoveFromWeeklyPlan(Phase.BASELINE, "lower-b"))
        repo.execute(Action.RestoreToWeeklyPlan(Phase.BASELINE, "lower-b"))
        assertEquals(established, repo.weekIds())
        assertEquals(listOf(done), repo.state.value.workouts)
    }

    @Test fun theLastOccurrenceOfAWeekCanBeReplacedButNeverTakenOff() = runTest {
        val repo = planned()
        listOf("push", "pull", "lower-a", "upper").forEach { repo.execute(Action.RemoveFromWeeklyPlan(Phase.BASELINE, it)) }
        assertEquals(listOf("lower-b"), repo.weekIds())
        failure("Lower B is the only routine on this phase's week") {
            repo.execute(Action.RemoveFromWeeklyPlan(Phase.BASELINE, "lower-b"))
        }
        repo.execute(Action.ScheduleRoutine("p-sun", Phase.BASELINE, 7, "r-arms", replacingId = "lower-b"))
        assertEquals("Replacing is one change, so the only routine can be replaced", listOf("p-sun"), repo.weekIds())
        failure("Arms is the only routine") { repo.execute(Action.RemoveFromWeeklyPlan(Phase.BASELINE, "p-sun")) }
        repo.execute(Action.MovePlacement("p-sun", 1))
        assertEquals(listOf("p-sun"), repo.weekIds())
        repo.execute(Action.RestoreToWeeklyPlan(Phase.BASELINE, "push"))
        assertEquals(listOf("p-sun", "push"), repo.weekIds())
        repo.execute(Action.RemoveFromWeeklyPlan(Phase.BASELINE, "p-sun"))
        assertEquals(listOf("push"), repo.weekIds())
        assertTrue("An unused copy leaves no record", repo.state.value.placements.isEmpty())
        assertTrue(repo.state.value.planAdditions.isEmpty())
        assertEquals("Other phases are untouched", established, repo.weekIds(Phase.FIVE_TWO))
        val week = repo.week()
        assertEquals(listOf("push"), week.routines.map { it.template.id })
        assertFalse(week.complete)
    }

    @Test fun eachPhaseOwnsItsWeekAndACopyNeverChangesPhase() = runTest {
        val repo = planned()
        repo.execute(Action.ScheduleRoutine("p-intro", Phase.INTRO_ONE, 1, "r-arms"))
        repo.execute(Action.RemoveFromWeeklyPlan(Phase.INTRO_ONE, "lower-a"))
        assertEquals(listOf("p-intro", "intro-upper", "upper", "lower-b"), repo.weekIds(Phase.INTRO_ONE))
        assertEquals("Intro two keeps its own week", listOf("intro-upper", "lower-a", "upper", "lower-b"),
            repo.weekIds(Phase.INTRO_TWO))
        assertEquals("The selected phase is untouched", established, repo.weekIds())
        assertNull(repo.week().statusOf("p-intro"))
        failure("not available") { repo.execute(Action.StartWorkout("p-intro", at(monday, 18), monday.toString())) }
        // Moving keeps the phase; a copy for another phase is a new schedule with its own ID.
        repo.execute(Action.MovePlacement("p-intro", 3))
        assertEquals(RoutinePlacement("p-intro", Phase.INTRO_ONE, 3, "Arms", "r-arms"), repo.state.value.placements.single())
        repo.execute(Action.ScheduleRoutine("p-base", Phase.BASELINE, 1, "r-arms"))
        assertEquals(listOf(Phase.INTRO_ONE, Phase.BASELINE), repo.state.value.placements.map { it.phase })
        assertEquals(2, repo.state.value.allLinkedPlacements("r-arms").size)
        assertEquals(listOf("p-base"), repo.state.value.linkedPlacements("r-arms", Phase.BASELINE).map { it.id })
        repo.execute(Action.SelectPhase(Phase.INTRO_ONE))
        repo.train("p-intro", monday.plusDays(2))
        assertEquals(RoutineStatus.DONE, repo.week().statusOf("p-intro"))
        assertNull(repo.week().statusOf("lower-a"))
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        assertEquals("Another phase's workout is listed apart", listOf("p-intro"), repo.week().other.map { it.templateId })
        assertEquals(RoutineStatus.NOT_DONE, repo.week().statusOf("p-base"))
    }

    @Test fun libraryEditsReachACopyOnlyThroughAnExplicitUpdateAndCopiesEditIndependently() = runTest {
        val repo = planned()
        repo.execute(Action.ScheduleRoutine("p-mon", Phase.BASELINE, 1, "r-arms"))
        repo.execute(Action.ScheduleRoutine("p-fri", Phase.BASELINE, 5, "r-arms"))
        repo.execute(Action.StartWorkout("p-mon", at(monday, 18), monday.toString()))
        val started = repo.state.value.activeWorkout!!
        val original = repo.state.value
        val row = RoutineExercise("row-stack", 3, 8, 12, 120, false)
        repo.execute(Action.SaveRoutine("r-arms", "Arms day", listOf(face.copy(sets = 4), row)))
        val edited = repo.state.value
        assertEquals("Copies keep their own name and exercises", original.placements, edited.placements)
        assertEquals(original.planAdditions, edited.planAdditions)
        assertEquals(started, edited.activeWorkout)
        assertTrue(edited.placements.all { edited.libraryDiffers(it) })
        val faceId = edited.planAdditions.single { it.templateId == "p-mon" && it.exerciseId == "face-pull" }.id
        repo.execute(Action.UpdatePlacementFromRoutine("p-mon"))
        val updated = repo.state.value
        val mon = updated.placements.single { it.id == "p-mon" }
        assertEquals(RoutinePlacement("p-mon", Phase.BASELINE, 1, "Arms day", "r-arms"), mon)
        assertEquals(listOf(face.copy(sets = 4), row), updated.placementExercises(mon))
        assertEquals("A kept exercise keeps its plan ID", faceId,
            updated.planAdditions.single { it.templateId == "p-mon" && it.exerciseId == "face-pull" }.id)
        assertFalse(updated.libraryDiffers(mon))
        assertEquals("The other copy is untouched", original.placements.single { it.id == "p-fri" },
            updated.placements.single { it.id == "p-fri" })
        assertEquals(original.planAdditions.filter { it.templateId == "p-fri" },
            updated.planAdditions.filter { it.templateId == "p-fri" })
        assertTrue(updated.libraryDiffers(updated.placements.single { it.id == "p-fri" }))
        assertEquals("The running workout keeps what it started with", started, updated.activeWorkout)
        repo.execute(Action.UpdatePlacementFromRoutine("p-mon"))
        assertEquals("Updating again changes nothing", updated, repo.state.value)

        repo.execute(Action.SavePlacement("p-fri", "Friday arms", listOf(hammer.copy(sets = 3))))
        val saved = repo.state.value
        val fri = saved.placements.single { it.id == "p-fri" }
        assertEquals(RoutinePlacement("p-fri", Phase.BASELINE, 5, "Friday arms", "r-arms"), fri)
        assertEquals(listOf(hammer.copy(sets = 3)), saved.placementExercises(fri))
        assertEquals("The library is untouched", updated.routines, saved.routines)
        assertEquals(updated.planAdditions.filter { it.templateId == "p-mon" }, saved.planAdditions.filter { it.templateId == "p-mon" })
        failure("is listed twice") { repo.execute(Action.SavePlacement("p-fri", "Friday arms", listOf(hammer, hammer))) }
        failure("Add at least one exercise.") { repo.execute(Action.SavePlacement("p-fri", "Friday arms", emptyList())) }
        failure("Routine name") { repo.execute(Action.SavePlacement("p-fri", " ", listOf(hammer))) }
        failure("no longer on your weekly plan") { repo.execute(Action.UpdatePlacementFromRoutine("missing")) }
        assertEquals(saved, repo.state.value)

        // Workouts after an update start from the copy's new exercises; the earlier one keeps its own.
        repo.execute(Action.DiscardWorkout)
        repo.execute(Action.StartWorkout("p-mon", at(monday, 19), monday.toString()))
        val next = repo.state.value.activeWorkout!!
        assertEquals("Arms day", next.name)
        assertEquals("p-mon", next.templateId)
        assertEquals(saved.planAdditions.filter { it.templateId == "p-mon" }.map { it.id }, next.exercises.map { it.slotId })
        assertEquals(listOf(4, 3), next.exercises.map { it.targetSets })
        next.exercises.forEach { entry ->
            assertFalse(entry.optional)
            assertNull(entry.baselineSets)
            assertNull(entry.upperSets)
            assertEquals(Phase.BASELINE.targetRir, entry.targetRir)
        }
    }

    @Test fun aCopysFutureWorkoutsCanGainAndLoseExercisesButNeverBecomeEmpty() = runTest {
        val repo = planned()
        repo.execute(Action.ScheduleRoutine("p-mon", Phase.BASELINE, 1, "r-arms"))
        repo.execute(Action.SavePlanAddition("add-row", Phase.BASELINE, "p-mon", "row-stack", 3, 8, 12, 120, false))
        val state = repo.state.value
        val mon = state.placements.single()
        assertEquals(listOf("face-pull", "curl-hammer", "row-stack"), state.placementExercises(mon).map { it.exerciseId })
        assertTrue(state.libraryDiffers(mon))
        assertEquals("The library routine is unchanged", listOf(face, hammer), state.routines.single().exercises)
        failure("is already in Arms") {
            repo.execute(Action.SavePlanAddition("add-face", Phase.BASELINE, "p-mon", "face-pull", 3, 8, 12, 120, false))
        }
        repo.execute(Action.StartWorkout("p-mon", at(monday, 18), monday.toString()))
        val workout = repo.state.value.activeWorkout!!
        val ids = state.planAdditions.filter { it.templateId == "p-mon" }.map { it.id }
        assertEquals(ids, workout.exercises.map { it.slotId })
        repo.execute(Action.RemovePlanAddition(ids[0]))
        repo.execute(Action.RemovePlanAddition(ids[1]))
        failure("Arms needs at least one exercise in this phase") { repo.execute(Action.RemovePlanAddition(ids[2])) }
        assertEquals(listOf("row-stack"), repo.state.value.placementExercises(repo.state.value.placements.single()).map { it.exerciseId })
        assertEquals("The running workout keeps all three", workout, repo.state.value.activeWorkout)
    }

    @Test fun schedulingIntoACompleteWeekMakesItIncompleteAndKeepsEveryFinishedWorkout() = runTest {
        val repo = planned()
        val sunday = monday.plusDays(6)
        val trained = listOf("push" to 1L, "pull" to 2L, "lower-a" to 3L, "upper" to 5L, "lower-b" to 6L)
            .map { (id, day) -> repo.train(id, monday.plusDays(day)) }
        val workouts = repo.state.value.workouts
        assertEquals(trained, workouts)
        assertTrue(repo.week().complete)
        assertEquals(5, repo.week().done)
        assertEquals(TodayKind.WEEK_COMPLETE, repo.today(sunday).kind)

        repo.execute(Action.ScheduleRoutine("p-sun", Phase.BASELINE, 7, "r-arms", alongside = true))
        val reopened = repo.week()
        assertFalse("A routine added to a complete week makes it incomplete", reopened.complete)
        assertEquals(established + "p-sun", reopened.routines.map { it.template.id })
        assertEquals(5, reopened.done)
        assertEquals(RoutineStatus.NOT_DONE, reopened.statusOf("p-sun"))
        assertEquals("Every earlier credit stays with the workout that earned it", trained.map { it.id },
            reopened.routines.filter { it.status == RoutineStatus.DONE }.map { it.doneBy?.id })
        repo.today(sunday).let {
            assertEquals(TodayKind.SCHEDULED, it.kind)
            assertEquals("p-sun", it.scheduled?.id)
        }
        assertEquals("Scheduling never touches a workout", workouts, repo.state.value.workouts)

        repo.execute(Action.RemoveFromWeeklyPlan(Phase.BASELINE, "p-sun"))
        assertTrue("Taking it off again leaves the week complete", repo.week().complete)
        assertEquals(established, repo.week().routines.map { it.template.id })
        assertEquals(TodayKind.WEEK_COMPLETE, repo.today(sunday).kind)
        assertEquals(workouts, repo.state.value.workouts)
    }
}
