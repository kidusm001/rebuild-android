package com.rebuild.domain

import com.rebuild.data.SnapshotCodec
import org.junit.Assert.*
import org.junit.Test

class SeedTest {
    private val seed = Seed.initialState()

    @Test fun seedValidatesWithoutPhase() {
        SnapshotCodec.validate(seed)
        assertNull(seed.phase)
        assertTrue(seed.workouts.isEmpty())
        assertEquals(74, seed.exercises.size)
    }

    @Test fun everyEstablishedSlotMatchesSpecification() {
        val expected = mapOf(
            "push" to listOf("incline-db:2:3:8:12", "shoulder-db:2:2:8:12", "lateral-cable:3:4:12:20",
                "chest-fly-cable:2:2:10:15", "triceps-overhead:3:4:10:15"),
            "pull" to listOf("pulldown:3:3:8:12", "row-chest-supported:2:3:8:12",
                "rear-delt-cable:2:4:12:20", "curl-cable:3:4:10:15"),
            "lower-a" to listOf("leg-press:2:3:10:15", "split-squat:2:2:8:12", "curl-seated:3:4:10:15",
                "calf-seated:3:4:10:20", "crunch-cable:2:3:10:15"),
            "upper" to listOf("bench-db:2:3:8:12", "row-chest-supported:3:3:8:12", "lateral-cable:3:4:12:20",
                "pulldown:2:3:10:15", "incline-db:2:2:8:12", "curl-db:3:4:10:15", "pushdown:3:4:10:15"),
            "lower-b" to listOf("squat-hack:2:3:8:12", "rdl:3:3:8:10", "curl-seated:2:3:10:15",
                "leg-extension:2:2:10:15", "calf-standing:3:4:10:20", "crunch-cable:2:3:10:15")
        )
        expected.forEach { (id, slots) ->
            assertEquals(id, slots, seed.templates.single { it.id == id }.slots.map {
                "${it.exerciseId}:${it.baselineSets}:${it.upperSets}:${it.minReps}:${it.maxReps}"
            })
        }
    }

    @Test fun introUpperHasExactExercisesAndRepRanges() {
        val intro = seed.templates.single { it.introOnly }
        assertEquals(2, intro.weekday)
        assertEquals(listOf("chest-machine:8:12", "row-cable:8:12", "pulldown:10:15",
            "lateral-cable:12:20", "rear-delt-cable:12:20", "curl-cable:10:15", "triceps-overhead:10:15"),
            intro.slots.map { "${it.exerciseId}:${it.minReps}:${it.maxReps}" })
    }

    @Test fun phaseSchedulesAndTotalsAreExact() {
        val totals = mapOf(Phase.INTRO_ONE to 25, Phase.INTRO_TWO to 50, Phase.FIVE_TWO to 54, Phase.BASELINE to 66)
        Phase.entries.forEach { phase ->
            val available = seed.availableTemplates(phase)
            assertEquals(if (phase.fourDay) listOf(2, 4, 6, 7) else listOf(2, 3, 4, 6, 7),
                available.map { it.weekday })
            assertEquals(totals.getValue(phase), available.sumOf { t -> t.slots.sumOf { seed.targetFor(it, phase).sets } })
            val phased = seed.copy(phase = phase)
            assertNull(phased.scheduledTemplate(1))
            assertNull(phased.scheduledTemplate(5))
            assertEquals(if (phase.fourDay) "intro-upper" else "push", phased.scheduledTemplate(2)?.id)
            assertEquals(if (phase.fourDay) null else "pull", phased.scheduledTemplate(3)?.id)
        }
        assertEquals(listOf(4, 3, 3, 2), Phase.entries.map { it.targetRir })
        assertEquals(86, seed.availableTemplates(Phase.BASELINE).sumOf { t -> t.slots.sumOf { it.upperSets!! } })
    }

    @Test fun baselineAndUpperMuscleTotalsAreDirectSetsNotDoubleCounted() {
        val slots = seed.availableTemplates(Phase.BASELINE).flatMap { it.slots }
        assertEquals(mapOf("Chest" to 8, "Front delts" to 2, "Side delts" to 6, "Rear delts" to 2,
            "Triceps" to 6, "Back" to 10, "Biceps" to 6, "Quads" to 8, "Hamstrings" to 8,
            "Calves" to 6, "Abs" to 4), slots.groupBy { it.muscle }.mapValues { (_, s) -> s.sumOf { it.baselineSets } })
        assertEquals(mapOf("Chest" to 10, "Front delts" to 2, "Side delts" to 8, "Rear delts" to 4,
            "Triceps" to 8, "Back" to 12, "Biceps" to 8, "Quads" to 10, "Hamstrings" to 10,
            "Calves" to 8, "Abs" to 6), slots.groupBy { it.muscle }.mapValues { (_, s) -> s.sumOf { it.upperSets!! } })
    }

    @Test fun variantsAndLoadingConventionsAreExplicit() {
        val slots = seed.templates.flatMap { it.slots }.associateBy { it.id }
        val expectedAlternatives = mapOf(
            "push-incline" to listOf("incline-machine"),
            "push-shoulder" to listOf("shoulder-machine"),
            "pull-vertical" to emptyList(),
            "pull-rear" to listOf("reverse-fly-db"),
            "pull-curl" to listOf("curl-db"),
            "lower-a-curl" to listOf("curl-lying"),
            "upper-bench" to listOf("bench-barbell"),
            "upper-curl" to listOf("curl-cable"),
            "lower-b-squat" to listOf("squat-back")
        )
        expectedAlternatives.forEach { (id, variants) -> assertEquals(variants, slots.getValue(id).alternatives) }
        val definitions = seed.exercises.associateBy { it.id }
        assertEquals(LoadingType.BODYWEIGHT, definitions.getValue("pullup").loading)
        assertEquals(LoadingType.ASSISTANCE, definitions.getValue("pullup-assisted").loading)
        assertEquals(LoadingType.EXTERNAL, definitions.getValue("pulldown").loading)
        assertEquals(setOf("incline-db", "shoulder-db", "reverse-fly-db", "curl-db", "split-squat", "bench-db"),
            ExerciseCatalog.legacyExercises.filter { it.perDumbbell }.map { it.id }.toSet())
        assertTrue(slots.getValue("lower-a-split").perLeg)
    }

    @Test fun defaultRestMatchesCompoundAndIsolation() {
        val compound = setOf("incline-db", "shoulder-db", "pullup", "row-chest-supported", "leg-press",
            "split-squat", "bench-db", "pulldown", "squat-hack", "rdl", "chest-machine", "row-cable")
        seed.templates.flatMap { it.slots }.forEach {
            assertEquals(it.id, if (it.exerciseId in compound) 180 else 90, it.restSeconds)
        }
    }
}
