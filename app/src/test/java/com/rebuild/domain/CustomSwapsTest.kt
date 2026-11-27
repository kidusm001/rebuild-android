package com.rebuild.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomSwapsTest {
    private fun custom(id: String, muscle: MuscleGroup) =
        Exercise(id, id, custom = true, equipment = Equipment.CABLE, primaryMuscle = muscle)

    private val state = Seed.initialState().let {
        it.copy(exercises = it.exercises + listOf(
            custom("hammer-cable", MuscleGroup.BICEPS),
            custom("pec-deck", MuscleGroup.CHEST),
            custom("skull", MuscleGroup.TRICEPS),
            custom("mystery", MuscleGroup.UNKNOWN)
        ))
    }

    @Test fun offersOnlyOwnExercisesForTheSameMuscle() {
        assertEquals(listOf("hammer-cable"), state.customSwapsFor("curl-cable"))
        assertEquals(listOf("pec-deck"), state.customSwapsFor("incline-db"))
    }

    @Test fun offersNoneForAnUnknownMuscleOrExercise() {
        assertEquals(emptyList<String>(), state.customSwapsFor("mystery"))
        assertEquals(emptyList<String>(), state.customSwapsFor("no-such-exercise"))
        assertEquals(emptyList<String>(), state.customSwapsFor(null))
    }

    @Test fun neverOffersTheExerciseItself() {
        assertEquals(emptyList<String>(), state.customSwapsFor("skull"))
    }
}

class SwapSuggestionsTest {
    private val base = Seed.initialState()

    private fun slot(id: String): PlanSlot =
        base.templates.flatMap { it.slots }.first { it.id == id }

    private fun loggedWorkout(exerciseId: String, warmupOnly: Boolean = false): Workout {
        val ex = base.exercises.first { it.id == exerciseId }
        val entry = if (ex.measured) {
            val effort = Effort("ef-$exerciseId", durationSeconds = 45, distance = null, kg = null,
                loadUnit = LoadUnit.KG, plates = null, completedAt = 1_000L)
            WorkoutExercise("e-$exerciseId", null, ex, 2, null, null, null, null, null, 90, efforts = listOf(effort))
        } else {
            val set = LoggedSet("s-$exerciseId", 20.0, 10, 2.0, warmup = warmupOnly, completedAt = 1_000L)
            WorkoutExercise("e-$exerciseId", null, ex, 2, 2, 3, 8, 12, 2, 90, sets = listOf(set))
        }
        return Workout("w-$exerciseId", null, "Extra", Phase.INTRO_TWO, 1_000L, finishedAt = 2_000L,
            exercises = listOf(entry), startDate = "2026-11-20")
    }

    @Test fun everyBuiltInExerciseHasAMovementTag() {
        val missing = ExerciseCatalog.exercises.filter { ExerciseCatalog.movementOf(it.id) == null }.map { it.id }
        assertEquals(emptyList<String>(), missing)
        val extra = ExerciseCatalog.movements.keys - ExerciseCatalog.exercises.map { it.id }.toSet()
        assertEquals(emptySet<String>(), extra)
    }

    @Test fun deltSplitKeepsLateralRaisesRearDeltsAndPressesApart() {
        val lateralGroups = base.swapSuggestions("lateral-cable", slot = slot("push-lateral"), gear = Gear.GYM)
        assertEquals(listOf(SwapGroupKind.SAME_MOVEMENT, SwapGroupKind.SAME_MUSCLE), lateralGroups.map { it.kind })
        assertEquals(listOf("lateral-db"), lateralGroups[0].suggestions.map { it.exercise.id })
        assertEquals("Lateral raise", lateralGroups[0].suggestions.single().reason)
        assertEquals("More for Shoulders", lateralGroups[1].title)
        assertTrue("shoulder-db" in lateralGroups[1].suggestions.map { it.exercise.id })
        assertTrue("rear-delt-cable" in lateralGroups[1].suggestions.map { it.exercise.id })

        val rearGroups = base.swapSuggestions("rear-delt-cable", slot = slot("pull-rear"), gear = Gear.GYM)
        assertEquals(listOf(SwapGroupKind.PLAN_ALTERNATIVES, SwapGroupKind.SAME_MOVEMENT, SwapGroupKind.SAME_MUSCLE),
            rearGroups.map { it.kind })
        assertEquals(listOf("reverse-fly-db"), rearGroups[0].suggestions.map { it.exercise.id })
        assertEquals("Plan alternative", rearGroups[0].suggestions.single().reason)
        assertEquals(setOf("face-pull", "reverse-pec-deck"), rearGroups[1].suggestions.map { it.exercise.id }.toSet())
        assertTrue("lateral-cable" in rearGroups[2].suggestions.map { it.exercise.id })
        assertTrue("shoulder-db" in rearGroups[2].suggestions.map { it.exercise.id })
    }

    @Test fun planDefaultAppearsFirstWhenSlotWasAlreadySwapped() {
        val pushIncline = slot("push-incline") // default incline-db, alt incline-machine
        val groups = base.swapSuggestions("chest-fly-cable", slot = pushIncline, gear = Gear.GYM)
        val plan = groups.first { it.kind == SwapGroupKind.PLAN_ALTERNATIVES }
        assertEquals(listOf("incline-db", "incline-machine"), plan.suggestions.map { it.exercise.id })
        assertEquals("Plan default", plan.suggestions[0].reason)
        assertEquals("Plan alternative", plan.suggestions[1].reason)
        // Same movement follows the slot's default exercise (chest presses).
        val movement = groups.first { it.kind == SwapGroupKind.SAME_MOVEMENT }
        assertTrue("bench-db" in movement.suggestions.map { it.exercise.id })
        assertFalse("chest-fly-cable" in groups.flatMap { it.suggestions }.map { it.exercise.id })

        // Even when an alternative sorts alphabetically before the default or has logged history,
        // Plan default stays pinned first.
        val upperBench = slot("upper-bench") // default bench-db ("Flat..."), alt bench-barbell ("Barbell...")
        val withLoggedAlt = base.copy(workouts = listOf(loggedWorkout("bench-barbell")))
        val benchPlan = withLoggedAlt.swapSuggestions("chest-fly-cable", slot = upperBench, gear = Gear.GYM)
            .first { it.kind == SwapGroupKind.PLAN_ALTERNATIVES }
        assertEquals(listOf("bench-db", "bench-barbell"), benchPlan.suggestions.map { it.exercise.id })
        assertEquals("Plan default", benchPlan.suggestions[0].reason)
        assertEquals("Plan alternative", benchPlan.suggestions[1].reason)
        assertTrue(benchPlan.suggestions[1].loggedBefore)
    }

    @Test fun equipmentFilterExcludesGearThePlanDoesNotHave() {
        val dbGroups = base.swapSuggestions("bench-db", gear = Gear.DUMBBELLS)
        val dbAll = dbGroups.flatMap { it.suggestions }.map { it.exercise }
        assertTrue(dbAll.isNotEmpty())
        assertTrue(dbAll.all { it.equipment == Equipment.DUMBBELL || it.equipment == Equipment.BODYWEIGHT })
        assertTrue(dbAll.none { it.id in setOf("chest-machine", "bench-barbell", "chest-fly-cable") })
        assertTrue(dbAll.any { it.id == "incline-db" } && dbAll.any { it.id == "pushup" })

        val bwGroups = base.swapSuggestions("pushup", gear = Gear.BODYWEIGHT)
        val bwAll = bwGroups.flatMap { it.suggestions }.map { it.exercise }
        assertEquals(listOf("pushup-incline"), bwAll.map { it.id })
        assertTrue(bwAll.all { it.equipment == Equipment.BODYWEIGHT })
    }

    @Test fun measureFilterNeverMixesRepsTimedDistanceOrCardio() {
        val plankGroups = base.swapSuggestions("plank", gear = Gear.GYM)
        val plankIds = plankGroups.flatMap { it.suggestions }.map { it.exercise.id }
        assertEquals(setOf("side-plank", "plank-weighted"), plankIds.toSet())
        assertTrue(plankIds.none { it in setOf("crunch", "crunch-cable", "knee-raise", "suitcase-carry") })

        val crunchGroups = base.swapSuggestions("crunch", gear = Gear.GYM)
        val crunchIds = crunchGroups.flatMap { it.suggestions }.map { it.exercise.id }
        assertTrue(crunchIds.none { it in setOf("plank", "side-plank", "plank-weighted", "suitcase-carry") })

        val cardioGroups = base.swapSuggestions("treadmill", gear = Gear.BODYWEIGHT)
        assertEquals(listOf("running", "walking"), cardioGroups.single().suggestions.map { it.exercise.id })

        val distanceGroups = base.swapSuggestions("farmers-carry", gear = Gear.GYM)
        assertEquals(listOf(SwapGroupKind.SAME_MOVEMENT), distanceGroups.map { it.kind })
        assertEquals(listOf("suitcase-carry"), distanceGroups.single().suggestions.map { it.exercise.id })
    }

    @Test fun excludesCurrentAndAlreadyPresentExercisesAndNeverDuplicatesAcrossGroups() {
        val excluding = setOf("incline-machine", "bench-db", "chest-fly-cable")
        val groups = base.swapSuggestions("incline-db", slot = slot("push-incline"), gear = Gear.GYM,
            excluding = excluding)
        val allIds = groups.flatMap { it.suggestions }.map { it.exercise.id }
        assertEquals(allIds.distinct(), allIds)
        assertFalse("incline-db" in allIds)
        assertTrue(excluding.none { it in allIds })
        assertTrue(groups.none { it.kind == SwapGroupKind.PLAN_ALTERNATIVES })
    }

    @Test fun loggedBeforeRanksFirstWithinAGroupThenAlphabetical() {
        val withHistory = base.copy(workouts = listOf(
            loggedWorkout("pushup"),
            loggedWorkout("bench-db"),
            loggedWorkout("bench-barbell", warmupOnly = true)
        ))
        val groups = withHistory.swapSuggestions("incline-db", gear = Gear.GYM)
        val sameMovement = groups.first { it.kind == SwapGroupKind.SAME_MOVEMENT }.suggestions
        // Logged working-set exercises come first in alphabetical order; warm-up-only barbell bench stays unlogged.
        assertEquals(listOf("bench-db", "pushup"), sameMovement.take(2).map { it.exercise.id })
        assertTrue(sameMovement.take(2).all { it.loggedBefore })
        val rest = sameMovement.drop(2)
        assertTrue(rest.none { it.loggedBefore })
        assertEquals(rest.map { it.exercise.name.lowercase() }.sorted(), rest.map { it.exercise.name.lowercase() })

        // Measured effort history also promotes an exercise within its group.
        val withTimed = base.copy(workouts = listOf(loggedWorkout("plank-weighted")))
        val plankSuggestions = withTimed.swapSuggestions("plank", gear = Gear.GYM).first().suggestions
        assertEquals("plank-weighted", plankSuggestions.first().exercise.id)
        assertTrue(plankSuggestions.first().loggedBefore)
    }

    @Test fun customExercisesMatchOnMuscleAndOldUnknownOnesReturnEmptyGracefully() {
        val customBiceps = Exercise("custom-curl", "Zottman curl", custom = true,
            equipment = Equipment.DUMBBELL, primaryMuscle = MuscleGroup.BICEPS)
        val mystery = Exercise("mystery", "Mystery lift", custom = true,
            equipment = Equipment.UNKNOWN, primaryMuscle = MuscleGroup.UNKNOWN)
        val state = base.copy(exercises = base.exercises + listOf(customBiceps, mystery))

        val bicepsGroups = state.swapSuggestions("curl-cable", slot = slot("pull-curl"), gear = Gear.GYM)
        val muscleGroup = bicepsGroups.first { it.kind == SwapGroupKind.SAME_MUSCLE }
        assertEquals(listOf("custom-curl"), muscleGroup.suggestions.map { it.exercise.id })
        assertFalse("mystery" in bicepsGroups.flatMap { it.suggestions }.map { it.exercise.id })

        // Custom exercise with no muscle returns empty instead of crashing or suggesting unrelated lifts.
        assertEquals(emptyList<SwapGroup>(), state.swapSuggestions("mystery", gear = Gear.GYM))
        assertEquals(emptyList<SwapGroup>(), state.swapSuggestions("does-not-exist", gear = Gear.GYM))

        // Custom exercise with a muscle still gets same-muscle suggestions.
        val fromCustom = state.swapSuggestions("custom-curl", gear = Gear.DUMBBELLS)
        assertEquals(listOf(SwapGroupKind.SAME_MUSCLE), fromCustom.map { it.kind })
        assertEquals(setOf("curl-db", "curl-hammer", "curl-incline-db"),
            fromCustom.single().suggestions.map { it.exercise.id }.toSet())
    }

    @Test fun hipHingeConnectsRdlsAndConventionalDeadlift() {
        val groups = base.swapSuggestions("rdl", gear = Gear.GYM)
        val sameMovement = groups.first { it.kind == SwapGroupKind.SAME_MOVEMENT }
        assertEquals(listOf("deadlift", "rdl-db"), sameMovement.suggestions.map { it.exercise.id })
        val sameMuscle = groups.first { it.kind == SwapGroupKind.SAME_MUSCLE }
        assertEquals(setOf("curl-seated", "curl-lying", "curl-standing", "leg-curl-slider"),
            sameMuscle.suggestions.map { it.exercise.id }.toSet())
    }

    @Test fun unknownEquipmentFitsGymOnlyAndSlotGearFollowsSelectedGear() {
        val legacyBiceps = Exercise("legacy-curl", "Old curl", custom = true,
            equipment = Equipment.UNKNOWN, primaryMuscle = MuscleGroup.BICEPS)
        val state = base.copy(exercises = base.exercises + legacyBiceps)
        assertTrue("legacy-curl" in state.swapSuggestions("curl-db", gear = Gear.GYM)
            .flatMap { it.suggestions }.map { it.exercise.id })
        assertFalse("legacy-curl" in state.swapSuggestions("curl-db", gear = Gear.DUMBBELLS)
            .flatMap { it.suggestions }.map { it.exercise.id })
        assertFalse("legacy-curl" in state.swapSuggestions("curl-db", gear = Gear.BODYWEIGHT)
            .flatMap { it.suggestions }.map { it.exercise.id })
        assertTrue("legacy-curl" in state.swapSuggestions("curl-db", gear = null)
            .flatMap { it.suggestions }.map { it.exercise.id })

        // Split slot with Gear.BODYWEIGHT uses bodyweight default & alternatives, pinning default first.
        val upperTemplates = Splits.byId(Splits.UPPER_LOWER).templates
        val pulldownSlot = upperTemplates.flatMap { it.slots }.first { it.exerciseFor(Gear.BODYWEIGHT) == "pullup" }
        val bwPlan = base.swapSuggestions("chinup", slot = pulldownSlot, gear = Gear.BODYWEIGHT)
            .first { it.kind == SwapGroupKind.PLAN_ALTERNATIVES }
        assertEquals("pullup", bwPlan.suggestions.first().exercise.id)
        assertEquals("Plan default", bwPlan.suggestions.first().reason)
        assertTrue(bwPlan.suggestions.all { it.exercise.equipment == Equipment.BODYWEIGHT })
    }
}

class CatalogMetadataTest {
    @Test fun everyBuiltInExerciseNamesItsMuscleAndEquipment() {
        val missing = (ExerciseCatalog.exercises + Seed.initialState().exercises).filter {
            it.primaryMuscle == MuscleGroup.UNKNOWN || it.equipment == Equipment.UNKNOWN
        }.map { it.id }.distinct()
        assertEquals(emptyList<String>(), missing)
    }
}

class MuscleChartFallbackTest {
    @Test fun anOlderWorkoutCopyWithNoMuscleCountsUnderTheLibrarysMuscle() {
        val old = Exercise("mine", "Mine", custom = true)
        val set = LoggedSet("s1", 20.0, 8, null, false, 1L)
        val entry = WorkoutExercise("e1", null, old, null, null, null, null, null, null, 90, sets = listOf(set))
        val workout = Workout("w1", null, "Extra", Phase.INTRO_ONE, 1L, finishedAt = 2L, exercises = listOf(entry),
            startDate = "2026-11-23")
        val state = Seed.initialState().let {
            it.copy(exercises = it.exercises + old.copy(primaryMuscle = MuscleGroup.BICEPS, equipment = Equipment.CABLE),
                workouts = listOf(workout))
        }
        assertEquals(listOf(MuscleGroup.BICEPS to 1),
            state.weeklySetsByMuscle(java.time.LocalDate.parse("2026-11-23")))
        // The saved record itself is untouched.
        assertEquals(MuscleGroup.UNKNOWN, state.workouts.single().exercises.single().exercise.primaryMuscle)
    }
}
