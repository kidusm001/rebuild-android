package com.rebuild.data

import com.rebuild.domain.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PlateSnapshotCodecTest {
    private fun raw(state: AppState): String = buildJsonObject {
        put("format", "rebuild-backup")
        put("version", SnapshotCodec.SCHEMA_VERSION)
        put("state", Json { encodeDefaults = true }.encodeToJsonElement(state))
    }.toString()

    @Test fun oldPayloadRejectsEveryNewFieldEvenDefaultValuesAndNeverAcceptsPlateMasquerading() {
        val source = legacyJson()
        listOf(
            "\"equipment\":\"CABLE\"", "\"primaryMuscle\":\"BACK\"", "\"aliases\":[]",
            "\"preferredLoadUnit\":\"KG\"", "\"loadUnit\":\"KG\"", "\"plates\":null",
            "\"entryLoadUnit\":\"KG\"", "\"catalogRevision\":0"
        ).forEach { field ->
            rejected { SnapshotCodec.decode(source.replace("\"schemaVersion\":1", "\"schemaVersion\":1,$field")) }
        }
        var state = legacyState().copy(phase = Phase.BASELINE)
        state = StateChanges.apply(state, Action.StartWorkout("pull", 1000))
        val workout = requireNotNull(state.activeWorkout)
        state = StateChanges.apply(state, Action.SaveSet(workout.id, workout.exercises.first().id, "old-kg", 7.0, 10, null, false, 2000))
        val recorded = legacyJson(state)
        rejected { SnapshotCodec.decode(recorded.replace("\"kg\":7.0", "\"kg\":7.0,\"loadUnit\":\"PLATES\",\"plates\":7")) }
        rejected { SnapshotCodec.decode(recorded.replace("\"kg\":7.0", "\"kg\":null,\"loadUnit\":\"PLATES\",\"plates\":7")) }
        assertEquals(7.0, SnapshotCodec.decode(recorded).activeWorkout!!.exercises.first().sets.single().kg)
        assertEquals(LoadUnit.KG, SnapshotCodec.decode(recorded).activeWorkout!!.exercises.first().sets.single().loadUnit)
    }

    @Test fun versionChecksPrecedeNumbersAndRejectMissingMismatchedOrUnknownVersions() {
        val source = SnapshotCodec.encode(Seed.initialState())
        listOf(
            source.replace("\"version\":7", "\"version\":8"),
            source.replace("\"schemaVersion\":7", "\"schemaVersion\":8"),
            source.replace("\"version\":7", "\"version\":5"),
            source.replace("\"schemaVersion\":7", "\"schemaVersion\":5"),
            source.replace("\"version\":7", "\"version\":4"),
            source.replace("\"schemaVersion\":7", "\"schemaVersion\":4"),
            source.replace("\"version\":7", "\"version\":3"),
            source.replace("\"schemaVersion\":7", "\"schemaVersion\":3"),
            source.replace("\"version\":7", "\"version\":2"),
            source.replace("\"schemaVersion\":7", "\"schemaVersion\":2"),
            source.replace("\"version\":7", "\"version\":1"),
            source.replace("\"schemaVersion\":7", "\"schemaVersion\":1"),
            source.replace("\"schemaVersion\":7,", ""),
            source.replace("\"catalogRevision\":2", "\"catalogRevision\":3"),
            source.replace("\"version\":7", "\"version\":\"7\""),
            source.replace("\"version\":7", "\"version\":7,\"\\u0076ersion\":6")
        ).forEach { invalid ->
            assertNotEquals(source, invalid)
            rejected { SnapshotCodec.decode(invalid) }
        }
        try {
            SnapshotCodec.decode(source.replace("\"version\":7", "\"version\":99").replace("\"revision\":0", "\"revision\":\"not a number\""))
            fail("Expected unsupported version")
        } catch (error: RepositoryException) {
            assertTrue(error.message!!.contains("version"))
        }
    }

    @Test fun newPayloadRejectsFractionalPlateCountsWrongEnumsAndAmbiguousLoads() = runTest {
        val repo = repository()
        repo.start(template = "pull")
        repo.log(kg = null, loadUnit = LoadUnit.PLATES, plates = 7)
        val state = repo.state.value
        val source = SnapshotCodec.encode(state)
        mapOf(
            "fractional count" to source.replace("\"plates\":7", "\"plates\":7.5"),
            "whole decimal count" to source.replace("\"plates\":7", "\"plates\":7.0"),
            "quoted count" to source.replace("\"plates\":7", "\"plates\":\"7\""),
            "exponent count" to source.replace("\"plates\":7", "\"plates\":1e2"),
            "integer overflow" to source.replace("\"plates\":7", "\"plates\":2147483648"),
            "zero count" to source.replace("\"plates\":7", "\"plates\":0"),
            "count above limit" to source.replace("\"plates\":7", "\"plates\":1001"),
            "null count" to source.replace("\"plates\":7", "\"plates\":null"),
            "conflicting kilogram value" to source.replace("\"kg\":null", "\"kg\":7.0"),
            "unknown unit" to source.replace("\"loadUnit\":\"PLATES\"", "\"loadUnit\":\"LB\""),
            "kilograms with plate count" to source.replace("\"loadUnit\":\"PLATES\"", "\"loadUnit\":\"KG\""),
            "null unit" to source.replace("\"loadUnit\":\"PLATES\"", "\"loadUnit\":null"),
            "missing unit" to source.replace("\"loadUnit\":\"PLATES\",", ""),
            "unknown equipment" to source.replace("\"equipment\":\"CABLE\"", "\"equipment\":\"PULLEY\""),
            "unknown muscle" to source.replace("\"primaryMuscle\":\"BACK\"", "\"primaryMuscle\":\"LATS\""),
            "duplicate plate field" to source.replace("\"plates\":7", "\"plates\":7,\"plates\":8")
        ).forEach { (label, payload) ->
            assertNotEquals("Fixture must change the source: $label", source, payload)
            assertRejectedPayload(label, payload)
        }
        val workout = requireNotNull(state.activeWorkout)
        val first = workout.exercises.first()
        val invalid = first.copy(exercise = first.exercise.copy(equipment = Equipment.UNKNOWN))
        rejected { SnapshotCodec.decode(raw(state.copy(workouts = listOf(
            workout.copy(exercises = listOf(invalid) + workout.exercises.drop(1)))))) }
        rejected { SnapshotCodec.decode(raw(state.copy(exercises = state.exercises.map {
            if (it.id == "incline-db") it.copy(preferredLoadUnit = LoadUnit.PLATES) else it
        }))) }
    }

    @Test fun plateWireCountsRequirePlainIntegerTokensEvenWhenScientificValuesAreWhole() = runTest {
        val repo = repository()
        repo.start(template = "pull")
        repo.log(kg = null, loadUnit = LoadUnit.PLATES, plates = 7)
        val source = SnapshotCodec.encode(repo.state.value)
        listOf("7.0", "7e0", "7E0", "7e+0", "70e-1", "0.7e1", "1e3", "+7", "07", "-0").forEach { token ->
            assertRejectedPayload("noncanonical plate token $token", source.replace("\"plates\":7", "\"plates\":$token"))
        }
        listOf(1, 7, 100, 1000).forEach { count ->
            val valid = source.replace("\"plates\":7", "\"plates\":$count")
            assertEquals(count, SnapshotCodec.decode(valid).activeWorkout!!.exercises.first().sets.single().plates)
        }
    }

    private fun assertRejectedPayload(label: String, payload: String) {
        try {
            SnapshotCodec.decode(payload)
            fail("Expected rejection: $label")
        } catch (error: RepositoryException) {
            assertFalse("Rejection needs an explanation: $label", error.message.isNullOrBlank())
        }
    }

    @Test fun aliasLimitsUnicodeAndEntryModeCapabilityAreValidated() {
        val cable = Seed.exercises.single { it.id == "pulldown" }
        listOf(listOf(""), listOf("a".repeat(121)), List(21) { "Alias $it" },
            listOf("same", "same"), listOf("\uD800")).forEach {
            rejected { SnapshotCodec.validateExercise(cable.copy(aliases = it)) }
        }
        SnapshotCodec.validateExercise(cable.copy(aliases = listOf("Élévation 🙂")))
        var state = StateChanges.apply(Seed.initialState(), Action.SelectPhase(Phase.BASELINE))
        state = StateChanges.apply(state, Action.StartWorkout("push", 1000))
        val workout = requireNotNull(state.activeWorkout)
        rejected { SnapshotCodec.decode(raw(state.copy(workouts = listOf(workout.copy(
            exercises = workout.exercises.map { it.copy(entryLoadUnit = LoadUnit.PLATES) }))))) }
    }

    @Test fun newBackupRoundTripPreservesMixedHistoryAndSnapshotAndEntryAndLibraryPreferences() = runTest {
        val source = repository()
        val workout = source.start(template = "pull")
        val exercise = workout.exercises.first()
        source.log(id = "kg", kg = 32.125)
        source.execute(Action.SetLoadUnit(workout.id, exercise.id, LoadUnit.PLATES))
        source.log(id = "p", kg = null, loadUnit = LoadUnit.PLATES, plates = 7, now = 2500)
        source.execute(Action.FinishWorkout(3000))
        source.execute(Action.StartWorkout("pull", 4000))
        source.log(id = "warmup", kg = null, loadUnit = LoadUnit.PLATES, plates = 3, warmup = true, now = 4500)
        source.log(id = "working", kg = null, loadUnit = LoadUnit.PLATES, plates = 8, now = 5000)
        source.execute(Action.AddExercise(Exercise("custom-cable", "My cable station", custom = true,
            equipment = Equipment.CABLE, primaryMuscle = MuscleGroup.BACK, aliases = listOf("My row"),
            preferredLoadUnit = LoadUnit.PLATES)))
        val original = source.state.value
        val document = source.exportBackup()
        assertEquals(original, SnapshotCodec.decode(document.json))
        assertEquals(document.json, SnapshotCodec.encode(SnapshotCodec.decode(document.json)))
        val store = MemoryPersistence()
        val destination = repository(store, clock = { 9000 })
        val safety = destination.exportBackup()
        destination.execute(Action.MarkBackup(safety.revision, 8000))
        val preview = destination.previewBackup(document.json)
        assertFalse(preview.dataUpdated)
        destination.restoreBackup(preview, safety.revision)
        val restored = destination.state.value
        assertEquals(original.copy(revision = safety.revision + 1, restTimer = null,
            lastBackupAt = 8000, lastBackupRevision = safety.revision, unbackedUpSince = 9000), restored)
        assertEquals(restored, repository(store).state.value)
        val history = restored.workouts.first().exercises.first()
        assertEquals(LoadUnit.KG, history.exercise.preferredLoadUnit)
        assertEquals(LoadUnit.PLATES, history.entryLoadUnit)
        assertEquals(32.125, history.sets.first().kg)
        assertNull(history.sets.last().kg)
        assertEquals(7, history.sets.last().plates)
        assertEquals(LoadUnit.PLATES, restored.activeWorkout!!.exercises.first().exercise.preferredLoadUnit)
    }
}
