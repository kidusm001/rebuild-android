package com.rebuild.ui

import com.rebuild.domain.*
import org.junit.Assert.*
import org.junit.Test

class LoadPresentationTest {
    private val cable = Exercise("cable", "Cable row", equipment = Equipment.CABLE)
    private val kg = LoggedSet("kg", 5.0, 12, null, false, 2)
    private val plates = LoggedSet("plates", null, 10, 2.0, false, 3, LoadUnit.PLATES, 5)

    @Test fun setDescriptionsRetainUnitsIndependentOfPreference() {
        assertEquals("5 kg × 12", setText(kg, cable.copy(preferredLoadUnit = LoadUnit.PLATES)))
        assertEquals("5 plates/stack × 10 @ 2 RIR", setText(plates, cable))
    }

    @Test fun historyChartsAndRepsNeverMixUnitsOrWarmups() {
        val exercise = WorkoutExercise("e", "s", cable, 2, 2, 3, 8, 12, 2, 180,
            sets = listOf(kg, plates, plates.copy(id = "warm", warmup = true, reps = 99)))
        val old = Workout("w", "t", "Test", Phase.BASELINE, 1, 4, exercises = listOf(exercise))
        val active = old.copy(id = "active", finishedAt = null)
        assertEquals("External load · kg and plates per stack, recorded separately", recordedLoadingDescription(exercise))
        assertEquals("External load · plates per stack", recordedLoadingDescription(exercise.copy(sets = listOf(plates))))
        val kgSeries = historySessions(listOf(old, active), cable.identity(), LoadUnit.KG)
        val plateSeries = historySessions(listOf(old, active), cable.identity(), LoadUnit.PLATES)
        assertEquals(listOf(kg), kgSeries.single().sets)
        assertEquals(listOf(plates), plateSeries.single().sets)
        assertEquals(5.0, plateSeries.single().sets.single().loadValue)
        assertTrue(historySessions(listOf(old), cable.copy(id = "other-machine").identity(), LoadUnit.PLATES).isEmpty())
    }

    @Test fun aSetWithDropsReadsAsThatSetWithItsDropsAndChartsItsOwnLoad() {
        val stage = { id: String, kgValue: Double, reps: Int -> LoggedSet(id, kgValue, reps, null, false, 10, groupId = "g") }
        val stages = listOf(stage("s1", 20.0, 10), stage("s2", 15.0, 8), stage("s3", 10.0, 6))
        val plateStage = LoggedSet("p1", null, 9, 1.0, false, 20, LoadUnit.PLATES, 4, groupId = "pg")
        val exercise = WorkoutExercise("e", "s", cable, 3, 3, 4, 8, 12, 2, 180,
            sets = listOf(kg) + stages + plateStage,
            setGroups = listOf(SetGroup("g", SetMethod.DROP, 30), SetGroup("pg", SetMethod.REST_PAUSE, 40)))
        val workout = Workout("w", "t", "Test", Phase.BASELINE, 1, 50, exercises = listOf(exercise))

        val group = exercise.workEntries()[1] as WorkEntry.Group
        // The set is the set; the drops are part of it, never a "drop set" that replaces it.
        assertEquals("20 kg × 10, drop 15 kg × 8, drop 10 kg × 6", groupText(group, cable))
        assertEquals("Set 2: 20 kg × 10, with 2 drops: 15 kg × 8; 10 kg × 6.", groupDescription("Set 2", group, cable))
        assertEquals("15 × 8", followUpText(stages[1], stages[0], cable))
        assertEquals("4 plates/stack × 9 @ 1 RIR", groupText(exercise.workEntries()[2] as WorkEntry.Group, cable))
        assertEquals(3, exercise.workingCount())

        val kgSession = historySessions(listOf(workout), cable.identity(), LoadUnit.KG).single()
        // Charts plot each set by its own load, so set 2 is 20 kg × 10; its drops are never chart points.
        assertEquals(listOf(kg, stages[0]), kgSession.sets)
        assertEquals(listOf(group), kgSession.groups)
        val plateSession = historySessions(listOf(workout), cable.identity(), LoadUnit.PLATES).single()
        assertEquals(listOf("p1"), plateSession.sets.map { it.id })
        assertEquals(listOf("p1"), plateSession.groups.single().stages.map { it.id })

        // A session with only stages is still listed, so history never claims nothing was logged.
        val onlyGroups = workout.copy(exercises = listOf(exercise.copy(sets = stages)))
        assertEquals(1, historySessions(listOf(onlyGroups), cable.identity(), LoadUnit.KG).single().groups.size)
    }

    @Test fun aNormalSetOfferedForAStageIsLabeledAsNormal() {
        val stage = kg.copy(id = "stage", groupId = "group")
        assertEquals("prefilled from last time's normal set",
            prefillText(EntrySuggestion(kg, PrefillSource.LAST_TIME), SetMethod.DROP))
        assertEquals("prefilled from today's last normal set",
            prefillText(EntrySuggestion(kg, PrefillSource.THIS_WORKOUT), SetMethod.REST_PAUSE))
        assertEquals("prefilled from last time", prefillText(EntrySuggestion(stage, PrefillSource.LAST_TIME), SetMethod.DROP))
        assertEquals("load from the stage before",
            prefillText(EntrySuggestion(stage, PrefillSource.THIS_WORKOUT, withReps = false), SetMethod.REST_PAUSE))
        // Normal sets read exactly as before.
        assertEquals("prefilled from last time", prefillText(EntrySuggestion(kg, PrefillSource.LAST_TIME), null))
        assertEquals("prefilled from this workout", prefillText(EntrySuggestion(kg, PrefillSource.THIS_WORKOUT), null))
        assertNull(prefillText(null, SetMethod.DROP))
    }
}
