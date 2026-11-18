package com.rebuild.domain

import com.rebuild.data.SnapshotCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitsTest {
    private val library = ExerciseCatalog.exercises.associateBy { it.id }

    @Test fun theQuizPicksOneSplitForEveryAnswer() {
        val table = mapOf(
            2 to listOf("full-body-2", "full-body-2", "full-body-2", "full-body-2"),
            3 to listOf("full-body-3", "full-body-3", "full-body-3", "push-pull-legs"),
            4 to listOf("upper-lower", "upper-lower", "upper-lower", "upper-lower"),
            5 to listOf("original", "original", "upper-lower-ppl", "upper-lower-ppl"),
            6 to listOf("push-pull-legs-2", "push-pull-legs-2", "push-pull-legs-2", "push-pull-legs-2")
        )
        table.forEach { (days, ids) ->
            Experience.entries.zip(ids).forEach { (experience, id) ->
                assertEquals("$days days, $experience", id, Splits.recommend(experience, days, Gear.GYM).id)
            }
        }
    }

    @Test fun withoutAGymTheOriginalPlanGivesWayAndEveryPickMatchesTheGear() {
        assertEquals("upper-lower-ppl", Splits.recommend(Experience.NEW, 5, Gear.DUMBBELLS).id)
        assertEquals("upper-lower-ppl", Splits.recommend(Experience.RETURNING, 5, Gear.BODYWEIGHT).id)
        for (gear in Gear.entries) for (days in 2..6) for (experience in Experience.entries) {
            val split = Splits.recommend(experience, days, gear)
            assertTrue("$days $experience $gear gets ${split.id}", gear in split.gear)
            if (!split.rampsDays) assertEquals("$days $experience gets a $days-day split", days, split.days)
        }
    }

    @Test fun experienceOnlyPicksTheStartingPhaseAndABeginnerOnManyDaysHearsSo() {
        assertEquals(Phase.INTRO_ONE, Splits.startingPhase(Experience.NEW))
        assertEquals(Phase.INTRO_ONE, Splits.startingPhase(Experience.RETURNING))
        assertEquals(Phase.INTRO_TWO, Splits.startingPhase(Experience.REGULAR))
        assertEquals(Phase.FIVE_TWO, Splits.startingPhase(Experience.EXPERIENCED))
        assertTrue(Splits.manyDaysForABeginner(Experience.NEW, 5))
        assertTrue(Splits.manyDaysForABeginner(Experience.NEW, 6))
        assertFalse(Splits.manyDaysForABeginner(Experience.NEW, 4))
        assertFalse(Splits.manyDaysForABeginner(Experience.EXPERIENCED, 6))
    }

    @Test fun daysAreSpreadAcrossTheWeekWithRestBetween() {
        assertEquals(listOf(1, 4), Splits.weekdays(2))
        assertEquals(listOf(1, 3, 5), Splits.weekdays(3))
        assertEquals(listOf(1, 2, 4, 5), Splits.weekdays(4))
        assertEquals(listOf(1, 2, 3, 5, 6), Splits.weekdays(5))
        assertEquals(listOf(1, 2, 3, 4, 5, 6), Splits.weekdays(6))
    }

    @Test fun everySplitsIdsAreUniqueAcrossTheWholeLibrary() {
        val templates = Splits.all.flatMap { it.templates }
        assertEquals(templates.size, templates.map { it.id }.distinct().size)
        val slots = templates.flatMap { it.slots }
        assertEquals(slots.size, slots.map { it.id }.distinct().size)
        assertTrue("every split but the original carries its own id",
            Splits.all.all { split -> split.templates.all { it.splitId == split.id } })
    }

    @Test fun everyOptionIsARepBasedLibraryExerciseThatFitsItsGear() {
        Splits.all.filterNot { it.rampsDays }.flatMap { it.templates }.flatMap { it.slots }.forEach { slot ->
            val gym = library.getValue(slot.exerciseId)
            assertEquals(slot.id, Measure.REPS, gym.measure)
            slot.gear!!.dumbbell?.let { id ->
                val exercise = library.getValue(id)
                assertEquals(slot.id, Measure.REPS, exercise.measure)
                assertTrue("${slot.id}: $id needs only dumbbells, a bench or the floor",
                    exercise.equipment == Equipment.DUMBBELL || exercise.equipment == Equipment.BODYWEIGHT)
            }
            slot.gear!!.bodyweight?.let { id ->
                val exercise = library.getValue(id)
                assertEquals(slot.id, Measure.REPS, exercise.measure)
                assertEquals("${slot.id}: $id needs no weights", LoadingType.BODYWEIGHT, exercise.loading)
            }
        }
    }

    @Test fun noWorkoutIsEmptyOrRepeatsAnExerciseWithAnyGear() {
        for (split in Splits.all) for (gear in split.gear) for (template in split.templates) {
            val exercises = template.slots.mapNotNull { it.exerciseFor(gear) }
            assertTrue("${template.id} with $gear keeps at least 3 exercises", exercises.size >= 3)
            assertEquals("${template.id} with $gear repeats an exercise", exercises.size, exercises.distinct().size)
        }
    }

    @Test fun repsCountedPerLegAgreeWhereverAnExerciseAppears() {
        val conventions = Splits.all.flatMap { it.templates }.flatMap { it.slots }.flatMap { slot ->
            listOfNotNull(slot.exerciseId, slot.gear?.dumbbell, slot.gear?.bodyweight).map { it to slot.perLeg }
        }.groupBy({ it.first }, { it.second })
        conventions.forEach { (id, perLeg) -> assertEquals("$id is counted one way", 1, perLeg.distinct().size) }
    }

    @Test fun everyMajorMuscleGetsAUsefulWeekAtBaseline() {
        val major = listOf(MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS)
        Splits.all.filterNot { it.rampsDays }.forEach { split ->
            major.forEach { muscle ->
                val sets = split.templates.flatMap { it.slots }
                    .filter { library.getValue(it.exerciseId).primaryMuscle == muscle }.sumOf { it.baselineSets }
                assertTrue("${split.id}: $muscle gets $sets baseline sets a week", sets in 6..16)
            }
        }
    }

    @Test fun everySplitWithEveryGearInEveryPhaseIsAValidPlan() {
        for (split in Splits.all) for (gear in split.gear) for (phase in Phase.entries) {
            val base = Seed.initialState()
            val state = base.copy(
                phase = phase,
                templates = if (split.rampsDays) base.templates else base.templates + split.templates,
                split = SplitChoice(split.id, gear, Experience.REGULAR, "2026-11-16")
            )
            SnapshotCodec.validate(state)
            val week = state.availableTemplates(phase)
            assertEquals("${split.id} in $phase", if (split.rampsDays) (if (phase.fourDay) 4 else 5) else split.days, week.size)
            week.forEach { template ->
                val planned = state.plannedExercises(template, phase)
                assertTrue("${template.id} with $gear in $phase", planned.isNotEmpty())
                assertTrue(planned.all { it.exerciseId in library })
            }
        }
    }

    @Test fun bodyweightSetsGetTheirOwnRepRange() {
        val split = Splits.byId(Splits.FULL_BODY_3)
        val state = Seed.initialState().let {
            it.copy(phase = Phase.BASELINE, templates = it.templates + split.templates,
                split = SplitChoice(split.id, Gear.BODYWEIGHT, Experience.NEW, "2026-11-16"))
        }
        val pushups = state.plannedExercises(split.templates.first(), Phase.BASELINE).single { it.exerciseId == "pushup" }
        assertEquals(6, pushups.minReps)
        assertEquals(20, pushups.maxReps)
        val gym = state.copy(split = state.split!!.copy(gear = Gear.GYM))
        val bench = gym.plannedExercises(split.templates.first(), Phase.BASELINE).single { it.exerciseId == "bench-db" }
        assertEquals(6, bench.minReps)
        assertEquals(10, bench.maxReps)
    }
}
