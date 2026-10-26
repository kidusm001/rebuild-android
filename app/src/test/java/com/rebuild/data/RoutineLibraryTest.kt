package com.rebuild.data

import com.rebuild.domain.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RoutineLibraryTest {
    private val addis = ZoneId.of("Africa/Addis_Ababa")
    /** Tuesday 29 September 2026, 18:00 in Addis Ababa. */
    private val tue = ZonedDateTime.of(2026, 9, 29, 18, 0, 0, 0, addis).toInstant().toEpochMilli()
    private val tueDate = "2026-09-29"

    private val face = RoutineExercise("face-pull", 3, 12, 20, 75, false)
    private val hammer = RoutineExercise("curl-hammer", 3, 10, 12, 60, false)
    private val lunge = RoutineExercise("lunge-db", 2, 8, 12, 120, true)

    private val RebuildRepository.active: Workout get() = state.value.activeWorkout!!

    private suspend fun library(store: MemoryPersistence = MemoryPersistence()): RebuildRepository =
        repository(store).also { it.execute(Action.SelectPhase(Phase.BASELINE)) }

    private suspend fun RebuildRepository.set(entryId: String, setId: String, now: Long, kg: Double? = 20.0,
                                              reps: Int = 10, workoutId: String = active.id) =
        execute(Action.SaveSet(workoutId, entryId, setId, kg, reps, null, false, now))

    @Test fun aSavedRoutineKeepsExactlyTheConfirmedTargetsAndAnEditReplacesItInPlace() = runTest {
        val store = MemoryPersistence()
        val repo = library(store)
        repo.execute(Action.SaveRoutine("r-arms", "Arms", listOf(face, hammer)))
        assertEquals(listOf(CustomRoutine("r-arms", "Arms", listOf(face, hammer))), repo.state.value.routines)
        val saved = repo.state.value
        val writes = store.writes
        repo.execute(Action.SaveRoutine("r-arms", "Arms", listOf(face, hammer)))
        assertEquals("Saving the same routine again changes nothing", saved, repo.state.value)
        assertEquals(writes, store.writes)
        repo.execute(Action.SaveRoutine("r-legs", "Legs", listOf(lunge)))
        repo.execute(Action.SaveRoutine("r-arms", "Arms and delts", listOf(hammer.copy(sets = 4), face)))
        assertEquals(listOf(CustomRoutine("r-arms", "Arms and delts", listOf(hammer.copy(sets = 4), face)),
            CustomRoutine("r-legs", "Legs", listOf(lunge))), repo.state.value.routines)
        assertEquals("A full-length name is kept as typed", "x".repeat(MAX_ROUTINE_NAME), repo.let {
            it.execute(Action.SaveRoutine("r-long", "x".repeat(MAX_ROUTINE_NAME), listOf(face)))
            it.state.value.routines.last().name
        })
        val state = repo.state.value
        assertTrue("A routine is unscheduled until a copy is added to a week", state.placements.isEmpty())
        assertTrue(state.planAdditions.isEmpty())
        assertTrue(state.workouts.isEmpty())
        assertEquals("Library routines never change any phase's week",
            listOf("push", "pull", "lower-a", "upper", "lower-b"), state.scheduledRoutines(Phase.BASELINE).map { it.id })
        assertEquals(listOf("intro-upper", "lower-a", "upper", "lower-b"),
            state.scheduledRoutines(Phase.INTRO_ONE).map { it.id })
    }

    @Test fun aRoutineNeedsANameAndConfirmedDistinctExercisesAndARefusedSaveChangesNothing() = runTest {
        val repo = library()
        val before = repo.state.value
        failure("Routine name") { repo.execute(Action.SaveRoutine("r1", "  ", listOf(face))) }
        failure("Routine name") { repo.execute(Action.SaveRoutine("r1", "x".repeat(MAX_ROUTINE_NAME + 1), listOf(face))) }
        failure("Identifiers") { repo.execute(Action.SaveRoutine("", "Arms", listOf(face))) }
        failure("Add at least one exercise.") { repo.execute(Action.SaveRoutine("r1", "Arms", emptyList())) }
        failure("at most $MAX_WORKOUT_EXERCISES exercises") {
            repo.execute(Action.SaveRoutine("r1", "Arms", List(MAX_WORKOUT_EXERCISES + 1) { face }))
        }
        failure("Cable face pull is listed twice. Keep one of them.") {
            repo.execute(Action.SaveRoutine("r1", "Arms", listOf(face, hammer, face.copy(sets = 1))))
        }
        failure("between 1 and") { repo.execute(Action.SaveRoutine("r1", "Arms", listOf(face.copy(sets = 0)))) }
        failure("rep range") { repo.execute(Action.SaveRoutine("r1", "Arms", listOf(face.copy(minReps = 20, maxReps = 12)))) }
        failure("Rest must") { repo.execute(Action.SaveRoutine("r1", "Arms", listOf(face.copy(restSeconds = 0)))) }
        failure("no longer exists") { repo.execute(Action.SaveRoutine("r1", "Arms", listOf(face.copy(exerciseId = "nope")))) }
        // Reps are never converted, so a routine counts them the way the plan does.
        failure("reps per leg.") {
            repo.execute(Action.SaveRoutine("r1", "Legs", listOf(RoutineExercise("split-squat", 2, 8, 12, 180, false))))
        }
        failure("without a per-leg label.") {
            repo.execute(Action.SaveRoutine("r1", "Legs", listOf(RoutineExercise("leg-press", 2, 10, 15, 180, true))))
        }
        failure("routine ID is already used") { repo.execute(Action.SaveRoutine("push", "Push", listOf(face))) }
        assertEquals(before, repo.state.value)

        repeat(MAX_ROUTINES) { repo.execute(Action.SaveRoutine("r$it", "Routine $it", listOf(face))) }
        failure("Your library can hold $MAX_ROUTINES routines. Delete one first.") {
            repo.execute(Action.SaveRoutine("r-more", "One more", listOf(face)))
        }
        repo.execute(Action.SaveRoutine("r7", "Edited", listOf(hammer)))
        assertEquals("A full library still lets a routine be edited", CustomRoutine("r7", "Edited", listOf(hammer)),
            repo.state.value.routines.single { it.id == "r7" })
        repo.execute(Action.DeleteRoutine("r0"))
        repo.execute(Action.SaveRoutine("r-more", "One more", listOf(face)))
        assertEquals(MAX_ROUTINES, repo.state.value.routines.size)
    }

    @Test fun aRoutinesOwnRowsDontSettleHowItCountsRepsButOtherRoutinesAndCopiesDo() = runTest {
        val repo = library()
        assertNull(repo.state.value.perLegConvention("lunge-db"))
        repo.execute(Action.SaveRoutine("r-legs", "Legs", listOf(lunge)))
        assertEquals(true, repo.state.value.perLegConvention("lunge-db"))
        repo.execute(Action.SaveRoutine("r-legs", "Legs", listOf(lunge.copy(perLeg = false))))
        assertFalse("Editing a routine may change how it counts its own reps",
            repo.state.value.routines.single().exercises.single().perLeg)
        failure("without a per-leg label.") { repo.execute(Action.SaveRoutine("r-more", "More legs", listOf(lunge))) }
        val draft = RoutineDraft("More legs", listOf(DraftExercise("k1", "lunge-db", 2, 8, 12, 120, true)))
        assertEquals(mapOf("k1" to setOf(DraftProblem.PER_LEG_CONFLICT)),
            repo.state.value.draftProblems(draft, ownerId = "r-more"))
        assertEquals("The routine being edited is left out", emptyMap<String, Set<DraftProblem>>(),
            repo.state.value.draftProblems(draft, ownerId = "r-legs"))
        // A scheduled copy is part of the plan, so the library routine and its copy keep counting reps the same way.
        repo.execute(Action.ScheduleRoutine("p-mon", Phase.BASELINE, 1, "r-legs"))
        failure("without a per-leg label.") { repo.execute(Action.SaveRoutine("r-legs", "Legs", listOf(lunge))) }
        failure("without a per-leg label.") { repo.execute(Action.SavePlacement("p-mon", "Legs", listOf(lunge))) }
        assertEquals(mapOf("k1" to setOf(DraftProblem.PER_LEG_CONFLICT)),
            repo.state.value.draftProblems(draft, ownerId = "r-legs"))
        // Adding it to a workout follows the same convention.
        repo.execute(Action.StartExtraWorkout(tue, tueDate))
        failure("without a per-leg label.") {
            repo.execute(Action.AddWorkoutExercise(repo.active.id, "x-lunge", "lunge-db", perLeg = true))
        }
        repo.execute(Action.AddWorkoutExercise(repo.active.id, "x-lunge", "lunge-db"))
        assertFalse(repo.active.exercises.single().perLeg)
    }

    @Test fun deletingARoutineKeepsItsScheduledCopiesAndWorkoutsAndOnlyDropsTheLink() = runTest {
        val store = MemoryPersistence()
        val repo = library(store)
        repo.execute(Action.SaveRoutine("r-arms", "Arms", listOf(face, hammer)))
        repo.execute(Action.ScheduleRoutine("p-mon", Phase.BASELINE, 1, "r-arms"))
        repo.execute(Action.StartWorkout("p-mon", tue, tueDate))
        repo.set(repo.active.exercises[0].id, "s1", tue + 1000)
        repo.execute(Action.FinishWorkout(tue + 2000))
        repo.execute(Action.StartExtraWorkout(tue + 3000, tueDate, routineId = "r-arms"))
        repo.set(repo.active.exercises[1].id, "s2", tue + 4000)
        repo.execute(Action.FinishWorkout(tue + 5000))
        val before = repo.state.value
        repo.execute(Action.DeleteRoutine("r-arms"))
        val after = repo.state.value
        assertTrue(after.routines.isEmpty())
        assertEquals(listOf(RoutinePlacement("p-mon", Phase.BASELINE, 1, "Arms", routineId = null)), after.placements)
        assertEquals("The copy keeps its exercises", before.planAdditions, after.planAdditions)
        assertEquals("Every workout stays exactly as recorded", before.workouts, after.workouts)
        assertEquals(before.scheduledRoutines(Phase.BASELINE).map { it.id }, after.scheduledRoutines(Phase.BASELINE).map { it.id })
        assertFalse(after.libraryDiffers(after.placements.single()))
        failure("no longer linked to a routine in your library") {
            repo.execute(Action.UpdatePlacementFromRoutine("p-mon"))
        }
        failure("This routine no longer exists.") {
            repo.execute(Action.StartExtraWorkout(tue + 6000, tueDate, routineId = "r-arms"))
        }
        failure("This routine no longer exists.") { repo.execute(Action.ScheduleRoutine("p-fri", Phase.BASELINE, 5, "r-arms")) }
        val writes = store.writes
        repo.execute(Action.DeleteRoutine("r-arms"))
        repo.execute(Action.DeleteRoutine("never-saved"))
        assertEquals("Deleting a missing routine changes nothing", after, repo.state.value)
        assertEquals(writes, store.writes)
        // A new routine reusing the old ID is a different routine: the copy stays unlinked.
        repo.execute(Action.SaveRoutine("r-arms", "Arms", listOf(face, hammer)))
        assertNull(repo.state.value.placements.single().routineId)
        // The copy still starts and is edited on its own.
        repo.execute(Action.SavePlacement("p-mon", "Monday arms", listOf(hammer)))
        repo.execute(Action.StartWorkout("p-mon", tue + 7000, tueDate))
        assertEquals("Monday arms", repo.active.name)
        assertEquals(listOf("curl-hammer"), repo.active.exercises.map { it.exercise.id })
        assertEquals(before.workouts, repo.state.value.workouts.dropLast(1))
    }

    @Test fun aRoutineStartedAsAnExtraListsItsGoalsAsOptionalExercisesAndNeverCounts() = runTest {
        val repo = library()
        repo.execute(Action.SaveRoutine("r-arms", "Arms", listOf(face, hammer)))
        repo.execute(Action.StartExtraWorkout(tue, tueDate, routineId = "r-arms"))
        val extra = repo.active
        assertNull(extra.templateId)
        assertTrue(extra.extra)
        assertEquals("Arms", extra.name)
        assertEquals(tueDate, extra.startDate)
        assertEquals(listOf("face-pull", "curl-hammer"), extra.exercises.map { it.exercise.id })
        extra.exercises.zip(listOf(face, hammer)).forEach { (entry, goal) ->
            assertTrue(entry.optional)
            assertFalse(entry.skipped)
            assertNull(entry.slotId)
            assertNull(entry.baselineSets)
            assertNull(entry.upperSets)
            assertNull(entry.targetRir)
            assertEquals(goal.sets, entry.targetSets)
            assertEquals(goal.minReps, entry.minReps)
            assertEquals(goal.maxReps, entry.maxReps)
            assertEquals(goal.restSeconds, entry.restSeconds)
            assertEquals(goal.perLeg, entry.perLeg)
            assertTrue(entry.sets.isEmpty())
            assertEquals(repo.state.value.exercises.single { it.id == goal.exerciseId }.preferredLoadUnit, entry.entryLoadUnit)
        }
        failure("already active") { repo.execute(Action.StartExtraWorkout(tue + 1, tueDate, routineId = "r-arms")) }
        repo.execute(Action.SaveRoutine("r-arms", "Arms v2", listOf(hammer.copy(sets = 5))))
        assertEquals("Library edits never reach a started workout", extra, repo.active)
        repeat(3) { repo.set(extra.exercises[0].id, "f$it", tue + 10 + it) }
        repeat(3) { repo.set(extra.exercises[1].id, "h$it", tue + 20 + it) }
        failure("Extra workouts never count on the weekly checklist.") {
            repo.execute(Action.FinishWorkout(tue + 100, countForWeek = true))
        }
        repo.execute(Action.FinishWorkout(tue + 100))
        val done = repo.state.value.workouts.single()
        assertFalse(done.countedForWeek)
        assertFalse("Meeting every goal still doesn't count", done.countsForWeek())
        failure("Extra workouts never count on the weekly checklist.") { repo.execute(Action.SetWeekCredit(done.id, true)) }
        val week = requireNotNull(repo.state.value.weekChecklist(LocalDate.of(2026, 9, 29), addis))
        assertEquals(listOf(done.id), week.extras.map { it.id })
        assertTrue(week.routines.all { it.status == RoutineStatus.NOT_DONE })
        assertTrue(week.other.isEmpty())
        // Scheduling the routine later never credits that earlier session.
        repo.execute(Action.ScheduleRoutine("p-tue", Phase.BASELINE, 2, "r-arms", alongside = true))
        val later = requireNotNull(repo.state.value.weekChecklist(LocalDate.of(2026, 9, 29), addis))
        assertEquals(RoutineStatus.NOT_DONE, later.statusOf("p-tue"))
        assertEquals(listOf(done.id), later.extras.map { it.id })
    }

    @Test fun aDraftFromAWorkoutListsEveryExerciseWithOnlyTheTargetsItHadAndNeverItsPerformance() = runTest {
        val repo = library()
        repo.execute(Action.StartWorkout("push", tue, tueDate))
        val push = repo.active
        val first = push.exercises.first()
        // A substitution today can leave the same exercise in the workout twice.
        repo.execute(Action.EditToday(first.id, "lateral-cable", requireNotNull(first.targetSets), first.restSeconds))
        repo.execute(Action.AddWorkoutExercise(push.id, "o-face", "face-pull"))
        repo.execute(Action.AddWorkoutExercise(push.id, "o-hammer", "curl-hammer"))
        repo.execute(Action.SetEntryGoal(push.id, "o-hammer", 3, 10, 12, 60, false))
        repo.set("o-face", "f1", tue + 1, kg = 31.0, reps = 17)
        repo.set(first.id, "l1", tue + 2, kg = 12.5, reps = 19)
        repo.execute(Action.SetSkipped(push.id, push.exercises[1].id, true))
        repo.execute(Action.FinishWorkout(tue + 100))
        val finished = repo.state.value.workouts.single()
        val draft = routineDraftFrom(finished)
        assertEquals("Push", draft.name)
        assertEquals("Every exercise, skipped and duplicate ones included, in the workout's order",
            finished.exercises.map { it.id }, draft.exercises.map { it.key })
        assertEquals(finished.exercises.map { it.exercise.id }, draft.exercises.map { it.exerciseId })
        finished.exercises.zip(draft.exercises).forEach { (entry, row) ->
            assertEquals(entry.targetSets, row.sets)
            assertEquals(entry.minReps, row.minReps)
            assertEquals(entry.maxReps, row.maxReps)
            assertEquals(entry.restSeconds, row.restSeconds)
            assertEquals(entry.perLeg, row.perLeg)
        }
        val faceRow = draft.exercises.single { it.key == "o-face" }
        assertEquals("No goal stays blank: 17 reps at 31 kg never become a target",
            listOf<Int?>(null, null, null), listOf(faceRow.sets, faceRow.minReps, faceRow.maxReps))
        val problems = repo.state.value.draftProblems(draft, ownerId = null)
        val laterals = draft.exercises.filter { it.exerciseId == "lateral-cable" }.map { it.key }
        assertEquals(listOf(first.id, push.exercises.single { it.slotId == "push-lateral" }.id), laterals)
        laterals.forEach { assertEquals(setOf(DraftProblem.DUPLICATE), problems[it]) }
        assertEquals(setOf(DraftProblem.NEEDS_TARGETS), problems["o-face"])
        assertEquals(setOf("o-face") + laterals, problems.keys)
        assertNull("Nothing is saved while a row lacks targets", repo.state.value.confirmedExercises(draft))
        failure("is listed twice") {
            repo.execute(Action.SaveRoutine("r-dup", "Push", draft.exercises.filter { it.key != "o-face" }.map {
                RoutineExercise(it.exerciseId, it.sets!!, it.minReps!!, it.maxReps!!, it.restSeconds!!, it.perLeg)
            }))
        }
        // The user keeps one lateral raise row and enters the face pull's targets.
        val reviewed = draft.copy(name = "Push copy", exercises = draft.exercises.filterNot { it.key == laterals[0] }.map {
            if (it.key == "o-face") it.copy(sets = 2, minReps = 12, maxReps = 15) else it
        })
        assertTrue(repo.state.value.draftProblems(reviewed, ownerId = null).isEmpty())
        val confirmed = requireNotNull(repo.state.value.confirmedExercises(reviewed))
        repo.execute(Action.SaveRoutine("r-push", reviewed.name, confirmed))
        assertEquals("The source workout never changes", finished, repo.state.value.workouts.single())
        assertEquals(confirmed, repo.state.value.routines.single().exercises)
        assertTrue(repo.state.value.placements.isEmpty())
        // An extra workout's default name is left for the user to choose; a routine's name is kept.
        repo.execute(Action.StartExtraWorkout(tue + 200, tueDate))
        repo.execute(Action.AddWorkoutExercise(repo.active.id, "x-face", "face-pull"))
        repo.set("x-face", "x1", tue + 201)
        repo.execute(Action.FinishWorkout(tue + 300))
        val plain = routineDraftFrom(repo.state.value.workouts.last())
        assertEquals("", plain.name)
        assertEquals("Its rest is the last-used one; nothing else is filled in",
            listOf(DraftExercise("x-face", "face-pull", null, null, null, faceRow.restSeconds, false)), plain.exercises)
        repo.execute(Action.StartExtraWorkout(tue + 400, tueDate, routineId = "r-push"))
        repo.set(repo.active.exercises[0].id, "y1", tue + 401)
        repo.execute(Action.FinishWorkout(tue + 500))
        assertEquals("Push copy", routineDraftFrom(repo.state.value.workouts.last()).name)
    }

    @Test fun restForAnAddedExerciseFallsBackToSavedRoutinesBeforeTheVisibleDefault() = runTest {
        val repo = library()
        assertEquals(RestSuggestion(DEFAULT_REST_SECONDS, RestSource.DEFAULT),
            repo.state.value.restSuggestion("face-pull", Phase.BASELINE))
        repo.execute(Action.SaveRoutine("r-arms", "Arms", listOf(face)))
        assertEquals(RestSuggestion(75, RestSource.ROUTINE), repo.state.value.restSuggestion("face-pull", Phase.BASELINE))
        assertEquals(RestSuggestion(75, RestSource.ROUTINE), repo.state.value.restSuggestion("face-pull", null))
        repo.execute(Action.ScheduleRoutine("p-mon", Phase.BASELINE, 1, "r-arms"))
        repo.execute(Action.SaveRoutine("r-arms", "Arms", listOf(face.copy(restSeconds = 45))))
        assertEquals("A scheduled copy is part of its phase's plan", RestSuggestion(75, RestSource.PLAN),
            repo.state.value.restSuggestion("face-pull", Phase.BASELINE))
        assertEquals("Another phase falls back to the library", RestSuggestion(45, RestSource.ROUTINE),
            repo.state.value.restSuggestion("face-pull", Phase.FIVE_TWO))
        assertEquals("An original slot still wins over a routine", RestSuggestion(90, RestSource.PLAN), repo.let {
            it.execute(Action.SaveRoutine("r-delts", "Delts", listOf(RoutineExercise("lateral-cable", 3, 12, 20, 40, false))))
            it.state.value.restSuggestion("lateral-cable", Phase.INTRO_ONE)
        })
        repo.execute(Action.StartExtraWorkout(tue, tueDate))
        repo.execute(Action.AddWorkoutExercise(repo.active.id, "x-face", "face-pull"))
        assertEquals(75, repo.active.exercises.single().restSeconds)
        repo.execute(Action.SetEntryGoal(repo.active.id, "x-face", null, null, null, 50, false))
        repo.set("x-face", "x1", tue + 1)
        assertEquals(RestSuggestion(50, RestSource.LAST_USED), repo.state.value.restSuggestion("face-pull", Phase.BASELINE))
    }
}
