package com.rebuild.domain

import com.rebuild.data.SnapshotCodec
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class ExerciseCatalogTest {
    private val state = Seed.initialState()

    @Test fun catalogIsBoundedUniqueDescribedAndDoesNotPrescribePullups() {
        assertEquals(74, state.exercises.size)
        assertEquals(31, ExerciseCatalog.legacyExercises.size)
        assertEquals(74, state.exercises.map { it.id }.distinct().size)
        assertEquals("Revision two only adds timed, distance and cardio exercises",
            mapOf(Measure.REPS to 60, Measure.TIMED to 5, Measure.DISTANCE to 2, Measure.CARDIO to 7),
            state.exercises.groupingBy { it.measure }.eachCount())
        assertEquals(ExerciseCatalog.revisionOne + ExerciseCatalog.revisionTwo, state.exercises)
        assertEquals(2, state.catalogRevision)
        assertEquals(7, state.schemaVersion)
        assertTrue(state.exercises.all {
            it.equipment != Equipment.UNKNOWN && it.primaryMuscle != MuscleGroup.UNKNOWN &&
                it.aliases.isNotEmpty() && it.preferredLoadUnit == LoadUnit.KG
        })
        assertTrue(state.templates.flatMap { it.slots }.none {
            it.exerciseId in setOf("pullup", "pullup-assisted") ||
                it.alternatives.any { id -> id in setOf("pullup", "pullup-assisted") }
        })
        SnapshotCodec.validate(state)
    }

    @Test fun searchFindsNamesAliasesPunctuationAndTokenOrder() {
        listOf("LAT PULLDOWN", "lat—pull.down", "lat pulldown", "Lat pull down").forEach {
            assertTrue(it, state.searchExercises(it).any { e -> e.id == "pulldown" })
        }
        assertEquals(listOf("rdl"), state.searchExercises("r.d.l.").map { it.id })
        assertEquals(listOf("split-squat"), state.searchExercises("RFESS").map { it.id })
        assertTrue(state.searchExercises("pullup").any { it.id == "pullup" })
        assertTrue(state.searchExercises("press dumbbell incline").any { it.id == "incline-db" })
        assertEquals(state.exercises, state.searchExercises("  "))
        assertTrue(state.searchExercises("not an exercise name").isEmpty())
    }

    @Test fun equipmentMuscleAndCustomFiltersComposeWithAliasSearch() {
        val custom = Exercise("my-pulldown", "My comfortable vertical pull", custom = true,
            equipment = Equipment.CABLE, primaryMuscle = MuscleGroup.BACK, aliases = listOf("Lat pulldown"))
        val unknown = Exercise("mystery", "Old custom cable pull", custom = true)
        val library = state.copy(exercises = state.exercises + custom + unknown)
        assertEquals(listOf(custom), library.searchExercises("lat pull down", Equipment.CABLE, MuscleGroup.BACK, true))
        assertEquals(listOf(unknown), library.searchExercises(equipment = Equipment.UNKNOWN, customOnly = true))
        assertEquals(listOf(custom, unknown), library.searchExercises(customOnly = true))
        assertTrue(library.searchExercises("lat", Equipment.DUMBBELL, MuscleGroup.BACK, true).isEmpty())
        assertEquals(library.exercises.searchExercises("lat"), library.searchExercises("lat"))
        assertFalse(unknown.supportsPlates)
        assertTrue(custom.supportsPlates)
    }

    @Test fun browserHelperHasNamedFiltersAndSortsNameIndependentlyOfCatalogOrder() {
        val zebra = Exercise("z", "Zebra station", custom = true, equipment = Equipment.CABLE,
            primaryMuscle = MuscleGroup.BACK, aliases = listOf("Lat pull-down"))
        val alpha = zebra.copy(id = "a", name = "alpha station")
        val wrongMuscle = zebra.copy(id = "chest", name = "Chest station", primaryMuscle = MuscleGroup.CHEST)
        val wrongEquipment = zebra.copy(id = "machine", name = "Machine station", equipment = Equipment.MACHINE)
        val exercises = listOf(zebra, wrongMuscle, wrongEquipment, alpha)
        assertEquals(listOf(alpha, zebra), searchExercises(exercises = exercises, query = "LAT.PULL—DOWN",
            muscle = MuscleGroup.BACK, equipment = Equipment.CABLE))
        assertEquals(listOf(alpha, wrongMuscle, wrongEquipment, zebra), searchExercises(exercises, ""))
        assertEquals(listOf(zebra, wrongMuscle, wrongEquipment, alpha), exercises)
    }

    @Test fun compactAliasSearchCannotBridgePartialWordSuffixesAndPrefixes() {
        val rdl = state.exercises.single { it.id == "rdl" }
        val backwardLunge = state.exercises.single { it.id == "lunge-db" }
        val forwardLunge = Exercise("forward-lunge", "Forward lunge", custom = true)
        val library = listOf(backwardLunge, forwardLunge, rdl)
        listOf("R.D.L.", "rdl", "r d l", "R-D-L").forEach { query ->
            assertEquals(query, listOf(rdl), searchExercises(library, query))
        }
        val pullup = Exercise("spaced-pullup", "Strict pull up", custom = true)
        val pulldown = Exercise("spaced-pulldown", "Lat pull down", custom = true)
        assertEquals(listOf(pullup), searchExercises(listOf(pullup, pulldown), "pullup"))
        assertEquals(listOf(pulldown), searchExercises(listOf(pullup, pulldown), "latpulldown"))
        assertEquals(listOf(pulldown), searchExercises(listOf(pullup, pulldown), "LAT.PULL—DOWN"))
    }

    @Test fun searchIsLocaleIndependentAndNormalizesAccentsWithoutMutatingNames() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertTrue(state.searchExercises("INCLINE").any { it.id == "incline-db" })
            val exercise = Exercise("accent", "Élévation latérale", custom = true)
            assertEquals(listOf(exercise), listOf(exercise).searchExercises("elevation laterale"))
            assertEquals("Élévation latérale", exercise.name)
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test fun capabilityDependsOnExplicitMetadataAndNeverNameOrPreference() {
        Equipment.entries.forEach { equipment ->
            LoadingType.entries.forEach { loading ->
                listOf(false, true).forEach { perDumbbell ->
                    val exercise = Exercise("variant", "Cable stack machine", loading, perDumbbell,
                        equipment = equipment)
                    assertEquals(loading == LoadingType.EXTERNAL && !perDumbbell &&
                        equipment in setOf(Equipment.CABLE, Equipment.STACK_MACHINE), exercise.supportsPlates)
                }
            }
        }
        assertFalse(Exercise("custom", "Cable curl", custom = true).supportsPlates)
    }

    @Test fun loadValuesNeverConvertOrRelabelActualKilograms() {
        val kg = LoggedSet("kg", 23.75, 10, null, false, 1000)
        val plates = LoggedSet("plates", null, 10, null, false, 1000, LoadUnit.PLATES, 7)
        assertEquals(23.75, kg.loadValue)
        assertEquals(LoadUnit.KG, kg.loadUnit)
        assertNull(kg.plates)
        assertEquals(7.0, plates.loadValue)
        assertNull(plates.kg)
        assertNull(kg.copy(kg = null).loadValue)
    }
}
