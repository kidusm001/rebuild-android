package com.rebuild.data

import com.rebuild.domain.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** First-run setup and changing split later: one change each, history and plan edits always kept. */
class SetupAndSplitTest {
    private val zone = ZoneId.of("Africa/Addis_Ababa")
    private var ids = 0

    private fun at(y: Int, m: Int, d: Int, h: Int = 18) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, zone)

    private suspend fun RebuildRepository.finished(template: String, start: ZonedDateTime): Workout {
        var now = start.toInstant().toEpochMilli()
        execute(Action.StartWorkout(template, now, start.toLocalDate().toString()))
        val workout = state.value.activeWorkout!!
        workout.exercises.forEach { exercise ->
            repeat(exercise.targetSets!!) { log(workout, exercise, id = "set-${ids++}", now = ++now) }
        }
        execute(Action.FinishWorkout(now + 60_000))
        return state.value.workouts.single { it.id == workout.id }
    }

    private suspend fun setUp(
        experience: Experience = Experience.NEW,
        days: Int = 3,
        gear: Gear = Gear.GYM,
        unit: MassUnit = MassUnit.KG,
        today: String = "2026-11-16"
    ): RebuildRepository = repository().also {
        it.execute(Action.CompleteSetup(experience, Splits.recommend(experience, days, gear).id, gear, unit, today))
    }

    @Test fun setupSetsTheSplitPhaseAndUnitInOneChange() = runTest {
        val repo = setUp(Experience.NEW, days = 3, gear = Gear.DUMBBELLS, unit = MassUnit.LB)
        val state = repo.state.value
        assertEquals(Phase.INTRO_ONE, state.phase)
        assertEquals(SplitChoice("full-body-3", Gear.DUMBBELLS, Experience.NEW, "2026-11-16"), state.split)
        assertEquals(MassUnit.LB, state.liftingUnit)
        assertEquals(listOf("full-body-3-a", "full-body-3-b", "full-body-3-c"), state.scheduledRoutines().map { it.id })
        assertEquals(listOf(1, 3, 5), state.scheduledRoutines().map { it.weekday })
        // One working set per exercise to start, with dumbbells in place of the gym's machines and cables.
        val first = state.plannedExercises(state.scheduledRoutines().first(), Phase.INTRO_ONE)
        assertEquals(listOf("squat-goblet", "bench-db", "row-db", "leg-curl-slider", "lateral-db", "crunch"), first.map { it.exerciseId })
        assertTrue(first.all { it.sets == 1 })
        assertEquals("The original plan stays in the plan, unlisted", Seed.templates.size,
            state.templates.count { it.splitId == ORIGINAL_SPLIT })
        assertEquals(1, state.revision)
    }

    @Test fun setupRunsOnceAndRefusesAGymOnlySplitWithoutAGym() = runTest {
        val repo = setUp()
        failure("already done") {
            repo.execute(Action.CompleteSetup(Experience.NEW, "full-body-2", Gear.GYM, MassUnit.KG, "2026-11-16"))
        }
        failure("needs a gym") {
            repository().execute(Action.CompleteSetup(Experience.NEW, ORIGINAL_SPLIT, Gear.BODYWEIGHT, MassUnit.KG, "2026-11-16"))
        }
        failure("isn't available") {
            repository().execute(Action.CompleteSetup(Experience.NEW, "arnold", Gear.GYM, MassUnit.KG, "2026-11-16"))
        }
    }

    @Test fun theOriginalPlanKeepsItsOwnDaysAndRamp() = runTest {
        val repo = setUp(Experience.RETURNING, days = 5)
        val state = repo.state.value
        assertEquals(ORIGINAL_SPLIT, state.activeSplitId)
        assertEquals("4 days while returning gently", 4, state.scheduledRoutines().size)
        assertEquals(5, state.scheduledRoutines(Phase.FIVE_TWO).size)
        assertEquals(Seed.templates.size, state.templates.size)
    }

    @Test fun changingSplitIsBlockedMidWorkoutAndKeepsTheOldSplitsEditsForAReturn() = runTest {
        val repo = setUp(Experience.REGULAR, days = 3)
        val split = repo.state.value.split!!
        val slot = "full-body-3-a-press"
        repo.execute(Action.EditPlan(Phase.INTRO_TWO, slot, "chest-machine", 3, 120))
        repo.execute(Action.StartWorkout("full-body-3-a", at(2026, 11, 16).toInstant().toEpochMilli(), "2026-11-16"))
        failure("active workout") {
            repo.execute(Action.ChangeSplit("upper-lower", Gear.GYM, Experience.REGULAR, "2026-11-17"))
        }
        repo.execute(Action.DiscardWorkout)

        repo.execute(Action.ChangeSplit("upper-lower", Gear.GYM, Experience.REGULAR, "2026-11-17"))
        var state = repo.state.value
        assertEquals(Phase.INTRO_TWO, state.phase)
        assertEquals(listOf("upper-lower-upper-a", "upper-lower-lower-a", "upper-lower-upper-b", "upper-lower-lower-b"),
            state.scheduledRoutines().map { it.id })
        assertTrue("the old split's edit is kept", state.overrides.any { it.slotId == slot })
        SnapshotCodec.validate(state)

        repo.execute(Action.ChangeSplit("full-body-3", Gear.GYM, Experience.REGULAR, "2026-11-18"))
        state = repo.state.value
        assertEquals(split.copy(since = "2026-11-18"), state.split)
        assertEquals("the edit comes back with its split", "chest-machine",
            state.plannedExercises(state.scheduledRoutines().first(), Phase.INTRO_TWO).first { it.slotId == slot }.exerciseId)
        assertEquals("a split's workouts are added once", Seed.templates.size + 3 + 4, state.templates.size)
    }

    @Test fun daysBeforeSetupInItsFirstWeekAreNeverMissed() = runTest {
        // Set up on a Thursday with Monday, Wednesday and Friday workouts: only Friday is left this week.
        val repo = setUp(Experience.NEW, days = 3, today = "2026-11-19")
        val week = repo.state.value.weekChecklist(LocalDate.of(2026, 11, 19), zone)!!
        assertEquals(listOf("full-body-3-c"), week.routines.map { it.template.id })
        // The next week lists all three.
        assertEquals(3, repo.state.value.weekChecklist(LocalDate.of(2026, 11, 23), zone)!!.routines.size)
    }

    @Test fun aSplitStartedTooLateInTheWeekOffersItsFirstWorkoutAsAnOptionalStart() = runTest {
        val repo = setUp(Experience.NEW, days = 3, today = "2026-11-21")
        val saturday = LocalDate.of(2026, 11, 21)
        val state = repo.state.value
        val week = state.weekChecklist(saturday, zone)!!
        assertTrue(week.routines.isEmpty())
        val today = state.todayPlan(saturday, week)
        assertEquals(TodayKind.REST, today.kind)
        assertEquals("full-body-3-a", today.suggested?.id)
        // Starting it is allowed, and it then shows as done this week.
        repo.finished("full-body-3-a", at(2026, 11, 21))
        val after = repo.state.value.weekChecklist(saturday, zone)!!
        assertEquals(listOf("full-body-3-a"), after.routines.map { it.template.id })
        assertEquals(RoutineStatus.DONE, after.statusOf("full-body-3-a"))
    }

    @Test fun changingSplitMidWeekKeepsWhatWasDoneAndOnlyAsksForTheRest() = runTest {
        val repo = setUp(Experience.NEW, days = 3, today = "2026-11-09")
        val monday = repo.finished("full-body-3-a", at(2026, 11, 16))
        repo.execute(Action.ChangeSplit("upper-lower", Gear.GYM, Experience.NEW, "2026-11-18"))
        val week = repo.state.value.weekChecklist(LocalDate.of(2026, 11, 18), zone)!!
        // Monday's full-body workout keeps its credit; Upper/Lower asks only for Thursday and Friday.
        assertEquals(listOf("full-body-3-a", "upper-lower-upper-b", "upper-lower-lower-b"), week.routines.map { it.template.id })
        assertEquals(monday.id, week.routines.first().doneBy?.id)
        assertTrue(week.other.isEmpty())
        // The week after is the new split alone.
        assertEquals(4, repo.state.value.weekChecklist(LocalDate.of(2026, 11, 23), zone)!!.routines.size)
    }

    @Test fun aBackupWithASplitHistoryOfAnotherAndDormantEditsRoundTrips() = runTest {
        val repo = setUp(Experience.REGULAR, days = 3, gear = Gear.BODYWEIGHT)
        repo.execute(Action.EditPlan(Phase.INTRO_TWO, "full-body-3-a-press", "dip", 2, 90))
        repo.finished("full-body-3-a", at(2026, 11, 16))
        repo.execute(Action.ChangeSplit("upper-lower", Gear.BODYWEIGHT, Experience.REGULAR, "2026-11-17"))
        val state = repo.state.value
        val encoded = SnapshotCodec.encode(state)
        assertEquals(state, SnapshotCodec.decode(encoded))
        assertEquals("a backup opens on another phone without change", state, repository(MemoryPersistence(encoded)).state.value)
    }

    @Test fun anOlderBackupCannotCarrySplitFieldsButOpensAsTheOriginalPlan() = runTest {
        val old = Seed.initialState().copy(phase = Phase.BASELINE)
        val v7 = v7Json(old)
        val decoded = SnapshotCodec.decode(v7)
        assertNull(decoded.split)
        assertEquals(ORIGINAL_SPLIT, decoded.activeSplitId)
        assertEquals(listOf("push", "pull", "lower-a", "upper", "lower-b"), decoded.scheduledRoutines().map { it.id })
        val withSplit = Json.parseToJsonElement(v7).jsonObject.let { document ->
            val state = document["state"]!!.jsonObject
            JsonObject(document + ("state" to JsonObject(state + ("split" to JsonNull))))
        }.toString()
        failure("version-eight") { SnapshotCodec.decode(withSplit) }
    }

    @Test fun aSwapMadeWithOneGearNeverFollowsThePlanToAnother() = runTest {
        val repo = setUp(Experience.REGULAR, days = 3, gear = Gear.GYM)
        val slot = "full-body-3-c-squat"
        repo.execute(Action.EditPlan(Phase.INTRO_TWO, slot, "squat-back", 2, 180))
        fun planned() = repo.state.value.let { s ->
            s.plannedExercises(s.scheduledRoutines().single { it.id == "full-body-3-c" }, Phase.INTRO_TWO)
                .single { it.slotId == slot }.exerciseId
        }
        assertEquals("squat-back", planned())
        repo.execute(Action.ChangeSplit("full-body-3", Gear.BODYWEIGHT, Experience.REGULAR, "2026-11-17"))
        assertEquals("the bodyweight plan asks for its own squat", "squat-bodyweight", planned())
        repo.execute(Action.ChangeSplit("full-body-3", Gear.GYM, Experience.REGULAR, "2026-11-18"))
        assertEquals("and the gym swap comes back with the gym", "squat-back", planned())
        SnapshotCodec.validate(repo.state.value)
    }

    @Test fun anAddedExerciseASlotNowAsksForIsSetAsideNotListedTwice() = runTest {
        val repo = setUp(Experience.REGULAR, days = 3, gear = Gear.GYM)
        repo.execute(Action.SavePlanAddition("add-pushup", Phase.INTRO_TWO, "full-body-3-a", "pushup", 2, 6, 20, 90, false))
        fun dayA() = repo.state.value.let { s ->
            s.plannedExercises(s.scheduledRoutines().single { it.id == "full-body-3-a" }, Phase.INTRO_TWO).map { it.exerciseId }
        }
        assertTrue("pushup" in dayA())
        // Bodyweight turns the bench slot into push-ups: the switch succeeds and lists push-ups once.
        repo.execute(Action.ChangeSplit("full-body-3", Gear.BODYWEIGHT, Experience.REGULAR, "2026-11-17"))
        assertEquals(1, dayA().count { it == "pushup" })
        assertTrue("the addition stays saved", repo.state.value.planAdditions.any { it.id == "add-pushup" })
        repo.execute(Action.ChangeSplit("full-body-3", Gear.GYM, Experience.REGULAR, "2026-11-18"))
        assertTrue("and is listed again with the gym", "pushup" in dayA())
    }

    @Test fun aFullLibraryStillGetsTheExercisesEverySplitNeeds() = runTest {
        val full = Seed.initialState().let { seed ->
            val own = (1..(1000 - (seed.exercises.size - ExerciseCatalog.revisionThree.size))).map {
                Exercise("own-$it", "My exercise $it", custom = true)
            }
            seed.copy(catalogRevision = 2, exercises = seed.exercises.filterNot { e ->
                ExerciseCatalog.revisionThree.any { it.id == e.id } } + own)
        }
        assertEquals(1000, full.exercises.size)
        val repo = repository(MemoryPersistence(SnapshotCodec.encode(full)))
        assertTrue(ExerciseCatalog.revisionThree.all { r -> repo.state.value.exercises.any { it.id == r.id } })
        repo.execute(Action.CompleteSetup(Experience.NEW, "full-body-3", Gear.BODYWEIGHT, MassUnit.KG, "2026-11-16"))
        assertEquals("full-body-3", repo.state.value.activeSplitId)
        failure("library is full") { repo.execute(Action.AddExercise(Exercise("one-more", "One more", custom = true))) }
    }

    @Test fun aLibraryMarkedRevisionThreeWithoutItsExercisesIsRepaired() = runTest {
        val seed = Seed.initialState()
        val own = (1..(1000 - (seed.exercises.size - ExerciseCatalog.revisionThree.size))).map {
            Exercise("own-$it", "My exercise $it", custom = true)
        }
        // What an earlier build left behind: revision 3 recorded, its exercises never added for lack of room.
        val stranded = seed.copy(exercises = seed.exercises.filterNot { e -> ExerciseCatalog.revisionThree.any { it.id == e.id } } + own)
        assertEquals(3, stranded.catalogRevision)
        val repo = repository(MemoryPersistence(SnapshotCodec.encode(stranded)))
        assertTrue(ExerciseCatalog.revisionThree.all { r -> repo.state.value.exercises.any { it.id == r.id } })
        repo.execute(Action.CompleteSetup(Experience.NEW, "full-body-3", Gear.BODYWEIGHT, MassUnit.KG, "2026-11-16"))
        assertEquals("full-body-3", repo.state.value.activeSplitId)
    }

    @Test fun aSwappedSlotWithItsOldExerciseAddedBackStillOpens() = runTest {
        val repo = setUp(Experience.REGULAR, days = 3, gear = Gear.GYM)
        repo.execute(Action.EditPlan(Phase.INTRO_TWO, "full-body-3-a-press", "chest-machine", 2, 180))
        repo.execute(Action.SavePlanAddition("add-bench", Phase.INTRO_TWO, "full-body-3-a", "bench-db", 2, 6, 10, 180, false))
        val state = repo.state.value
        assertEquals(state, SnapshotCodec.decode(SnapshotCodec.encode(state)))
    }

    @Test fun aSetAsideAdditionNeverBlocksEditingTheSlotThatAsksForIt() = runTest {
        val repo = setUp(Experience.REGULAR, days = 3, gear = Gear.GYM)
        repo.execute(Action.SavePlanAddition("add-pushup", Phase.INTRO_TWO, "full-body-3-a", "pushup", 2, 6, 20, 90, false))
        repo.execute(Action.ChangeSplit("full-body-3", Gear.BODYWEIGHT, Experience.REGULAR, "2026-11-17"))
        // The bench slot now asks for push-ups; changing its sets keeps that exercise and must save.
        repo.execute(Action.EditPlan(Phase.INTRO_TWO, "full-body-3-a-press", "pushup", 3, 120))
        val state = repo.state.value
        assertEquals(3, state.plannedExercises(state.scheduledRoutines().first(), Phase.INTRO_TWO)
            .single { it.slotId == "full-body-3-a-press" }.sets)
    }

    @Test fun anExerciseAddedWithDumbbellsThatTheGymSlotUsesStillSwitchesToTheGym() = runTest {
        val repo = setUp(Experience.REGULAR, days = 3, gear = Gear.DUMBBELLS)
        // With dumbbells, Full body A's legs slot is a goblet squat, so adding the leg press is allowed.
        repo.execute(Action.SavePlanAddition("add-press", Phase.INTRO_TWO, "full-body-3-a", "leg-press", 2, 10, 15, 180, false))
        repo.execute(Action.ChangeSplit("full-body-3", Gear.GYM, Experience.REGULAR, "2026-11-17"))
        val state = repo.state.value
        val dayA = state.plannedExercises(state.scheduledRoutines().first(), Phase.INTRO_TWO).map { it.exerciseId }
        assertEquals("the gym slot asks for it, listed once", 1, dayA.count { it == "leg-press" })
        assertEquals(state, SnapshotCodec.decode(SnapshotCodec.encode(state)))
    }
}
