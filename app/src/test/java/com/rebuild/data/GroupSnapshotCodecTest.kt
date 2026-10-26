package com.rebuild.data

import com.rebuild.domain.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class GroupSnapshotCodecTest {
    private val json = Json { encodeDefaults = true }

    /** Writes any state as a current-version document without validating it, so invalid states reach the decoder. */
    private fun raw(state: AppState): String = buildJsonObject {
        put("format", "rebuild-backup")
        put("version", SnapshotCodec.SCHEMA_VERSION)
        put("state", json.encodeToJsonElement(state))
    }.toString()

    /** History with a normal set, a finished drop set and week credit, plus an open rest-pause set with a pause. */
    private suspend fun recorded(): AppState {
        val repo = repository()
        val first = repo.start(template = "pull")
        val cable = first.exercises[0]
        repo.log(first, cable, id = "h-normal", kg = 40.0, now = 2000)
        repo.execute(Action.LogStage(first.id, cable.id, "h-drop", SetMethod.DROP, "h-d1", 35.0, 10, 1.0, 3000))
        repo.execute(Action.LogStage(first.id, cable.id, "h-drop", SetMethod.DROP, "h-d2", 25.0, 8, null, 3100))
        repo.execute(Action.FinishGroup(first.id, cable.id, "h-drop", 3200))
        repo.execute(Action.FinishWorkout(4000, countForWeek = true))
        repo.execute(Action.StartWorkout("pull", 100_000))
        val active = repo.state.value.activeWorkout!!
        val activeCable = active.exercises[0]
        repo.execute(Action.SetLoadUnit(active.id, activeCable.id, LoadUnit.PLATES))
        repo.execute(Action.LogStage(active.id, activeCable.id, "a-rp", SetMethod.REST_PAUSE, "a-r1", null, 10, null,
            101_000, LoadUnit.PLATES, 9))
        repo.execute(Action.StartShortPause(active.id, activeCable.id, 20, 101_500))
        return repo.state.value
    }

    private fun AppState.editExercise(workout: Int, exercise: Int, edit: (WorkoutExercise) -> WorkoutExercise) =
        copy(workouts = workouts.mapIndexed { wi, w ->
            if (wi != workout) w else w.copy(exercises = w.exercises.mapIndexed { ei, e -> if (ei == exercise) edit(e) else e })
        })

    @Test fun versionThreeRoundTripKeepsStagesOpenSetPauseCreditAndDatesExactly() = runTest {
        val state = recorded()
        val encoded = SnapshotCodec.encode(state)
        assertTrue(encoded.contains("\"version\":7"))
        val decoded = SnapshotCodec.decode(encoded)
        assertEquals(state, decoded)
        assertEquals(encoded, SnapshotCodec.encode(decoded))
        val history = decoded.workouts.first()
        assertTrue(history.countedForWeek)
        assertNotNull(history.startDate)
        assertEquals(listOf("h-normal", "h-d1", "h-d2"), history.exercises[0].sets.map { it.id })
        assertEquals(listOf(null, "h-drop", "h-drop"), history.exercises[0].sets.map { it.groupId })
        assertEquals(SetGroup("h-drop", SetMethod.DROP, 3200), history.exercises[0].setGroups.single())
        val open = decoded.activeWorkout!!.exercises[0]
        assertEquals(SetGroup("a-rp", SetMethod.REST_PAUSE, null), open.setGroups.single())
        assertEquals(9, open.sets.single().plates)
        assertTrue(decoded.restTimer!!.shortPause)
        // The same data as 0.1.9 (v5) stored it is valid, but no older label can carry it.
        val v5 = v5Json(state)
        assertEquals(state.beforeCatalogTwo().copy(schemaVersion = 5), SnapshotCodec.decode(v5))
        // A v2 reader can't take this document even if relabelled: its stages would otherwise flatten into sets.
        rejected { SnapshotCodec.decode(relabelled(v5, 5, 2)) }
        // Likewise a 0.1.7 (v3) label can't carry version-four plan edits or optional/skipped markers.
        rejected { SnapshotCodec.decode(relabelled(v5, 5, 3)) }
        // And a 0.1.8 (v4) label can't carry version-five routines or weekly schedule changes, even empty ones.
        rejected { SnapshotCodec.decode(relabelled(v5, 5, 4)) }
        // Nor a 0.1.9 (v5) label version-six measurement types, efforts or the stopwatch.
        rejected { SnapshotCodec.decode(relabelled(v6Json(state), 6, 5)) }
    }

    @Test fun olderDocumentsRejectEveryVersionThreeFieldEvenWithDefaultValues() = runTest {
        val repo = repository()
        val workout = repo.start()
        repo.log(id = "old-set", kg = 20.0, now = 2000)
        val old = repo.state.value.copy(workouts = repo.state.value.workouts.map { it.copy(startDate = null) })
        val v2 = v2Json(old)
        assertEquals(workout.id, SnapshotCodec.decode(v2).activeWorkout!!.id)
        val injections = listOf(
            "\"plates\":null" to "\"plates\":null,\"groupId\":null",
            "\"entryLoadUnit\":\"KG\"" to "\"entryLoadUnit\":\"KG\",\"setGroups\":[]",
            "\"startedAt\":1000" to "\"startedAt\":1000,\"startDate\":\"1970-01-01\"",
            "\"startedAt\":1000" to "\"startedAt\":1000,\"countedForWeek\":false",
            "\"deadline\":" to "\"shortPause\":false,\"deadline\":"
        )
        injections.forEach { (anchor, replacement) ->
            assertTrue(anchor, v2.contains(anchor))
            rejected { SnapshotCodec.decode(v2.replaceFirst(anchor, replacement)) }
        }
        val v1 = legacyJson(legacyState().copy(phase = Phase.BASELINE).let {
            StateChanges.apply(it, Action.StartWorkout("pull", 1000, "1970-01-01"))
        }.let { it.copy(workouts = it.workouts.map { w -> w.copy(startDate = null) }) })
        SnapshotCodec.decode(v1)
        listOf("\"startedAt\":1000" to "\"startedAt\":1000,\"startDate\":\"1970-01-01\"",
            "\"startedAt\":1000" to "\"startedAt\":1000,\"countedForWeek\":false").forEach { (anchor, replacement) ->
            rejected { SnapshotCodec.decode(v1.replaceFirst(anchor, replacement)) }
        }
    }

    @Test fun versionThreeStructureRulesAreEnforced() = runTest {
        val state = recorded()
        SnapshotCodec.decode(raw(state))
        val stage = state.workouts[0].exercises[0].sets[1]
        val invalid = listOf(
            "missing group" to state.editExercise(0, 0) { e -> e.copy(sets = e.sets.map { if (it.id == "h-d1") it.copy(groupId = "nope") else it }) },
            "group in another exercise" to state.editExercise(0, 1) { e ->
                e.copy(sets = listOf(stage.copy(id = "moved")), setGroups = emptyList()) },
            "empty group" to state.editExercise(0, 1) { e -> e.copy(setGroups = listOf(SetGroup("empty", SetMethod.DROP, 3000))) },
            "too many stages" to state.editExercise(0, 0) { e ->
                e.copy(sets = e.sets + (3..21).map { stage.copy(id = "extra-$it") }) },
            "split stages" to state.editExercise(0, 0) { e -> e.copy(sets = listOf(e.sets[1], e.sets[0], e.sets[2])) },
            "warm-up stage" to state.editExercise(0, 0) { e -> e.copy(sets = e.sets.map { if (it.id == "h-d2") it.copy(warmup = true) else it }) },
            "open set in finished workout" to state.editExercise(0, 0) { e -> e.copy(setGroups = e.setGroups.map { it.copy(finishedAt = null) }) },
            "two open sets" to state.editExercise(1, 1) { e ->
                e.copy(sets = listOf(stage.copy(id = "second-open", groupId = "second")), setGroups = listOf(SetGroup("second", SetMethod.DROP))) },
            "duplicate group id" to state.editExercise(1, 1) { e ->
                e.copy(sets = listOf(stage.copy(id = "dup-stage")), setGroups = listOf(SetGroup("h-drop", SetMethod.DROP, 200_000))) },
            "blank group id" to state.editExercise(0, 0) { e ->
                e.copy(sets = e.sets.map { if (it.groupId != null) it.copy(groupId = " ") else it }, setGroups = e.setGroups.map { it.copy(id = " ") }) },
            "control group id" to state.editExercise(0, 0) { e ->
                e.copy(sets = e.sets.map { if (it.groupId != null) it.copy(groupId = "a\u0007") else it }, setGroups = e.setGroups.map { it.copy(id = "a\u0007") }) },
            "credit on active" to state.copy(workouts = state.workouts.map { if (it.finishedAt == null) it.copy(countedForWeek = true) else it }),
            "pause without rest-pause" to state.editExercise(1, 0) { e -> e.copy(setGroups = e.setGroups.map { it.copy(method = SetMethod.DROP) }) },
            "pause on finished set" to state.editExercise(1, 0) { e -> e.copy(setGroups = e.setGroups.map { it.copy(finishedAt = 101_200) }) }
        ) + listOf(null, "", "1970-1-1", "1970-02-30", "+1970-01-01", "1970-01-04", "1969-12-29", "01/01/1970").map { date ->
            "start date $date" to state.copy(workouts = state.workouts.mapIndexed { i, w -> if (i == 0) w.copy(startDate = date) else w })
        }
        invalid.forEach { (label, value) ->
            try {
                SnapshotCodec.decode(raw(value))
                fail("Expected $label to be rejected")
            } catch (expected: RepositoryException) {
                assertFalse(label, expected.message.isNullOrBlank())
            }
        }
    }

    @Test fun versionThreeFieldTypesAreStrict() = runTest {
        val source = raw(recorded())
        listOf(
            "\"countedForWeek\":true" to "\"countedForWeek\":\"true\"",
            "\"countedForWeek\":false" to "\"countedForWeek\":0",
            "\"shortPause\":true" to "\"shortPause\":\"true\"",
            "\"method\":\"DROP\"" to "\"method\":\"SUPERSET\"",
            "\"setGroups\":[]" to "\"setGroups\":null",
            "\"groupId\":null" to "\"groupId\":5",
            "\"groupId\":\"h-drop\"" to "\"groupId\":[\"h-drop\"]",
            "\"finishedAt\":3200}" to "\"finishedAt\":3200,\"note\":\"x\"}",
            "\"finishedAt\":3200}" to "\"finishedAt\":\"3200\"}"
        ).forEach { (anchor, replacement) ->
            assertTrue(anchor, source.contains(anchor))
            try {
                SnapshotCodec.decode(source.replaceFirst(anchor, replacement))
                fail("Expected $replacement to be rejected")
            } catch (expected: RepositoryException) {
                assertFalse(expected.message.isNullOrBlank())
            }
        }
        val date = Regex("\"startDate\":\"[0-9-]+\"")
        assertTrue(date.containsMatchIn(source))
        rejected { SnapshotCodec.decode(date.replaceFirst(source, "\"startDate\":19700101")) }
        rejected { SnapshotCodec.decode(date.replaceFirst(source, "\"startDate\":null")) }
    }

    @Test fun olderDataGetsStartDatesOnceWithoutChangingAnyRecordedValue() = runTest {
        val repo = repository()
        val finished = repo.start(template = "pull")
        repo.log(finished, finished.exercises[0], id = "kg-set", kg = 37.5, now = 2000)
        repo.execute(Action.FinishWorkout(3000))
        // 21:30 UTC is already the next day in Addis Ababa (UTC+3) but not in New York.
        val late = 1_790_000_000_000L - 1_790_000_000_000L % 86_400_000L + 77_400_000L
        repo.execute(Action.StartWorkout("pull", late, localDateOf(late, ZoneId.of("UTC")).toString()))
        val active = repo.state.value.activeWorkout!!
        repo.execute(Action.SetLoadUnit(active.id, active.exercises[0].id, LoadUnit.PLATES))
        repo.log(active, active.exercises[0], id = "plate-set", kg = null, loadUnit = LoadUnit.PLATES, plates = 8, now = late + 1000)
        val current = repo.state.value
        val old = SnapshotCodec.decode(v2Json(current.copy(workouts = current.workouts.map { it.copy(startDate = null) })))
        assertEquals(2, old.schemaVersion)
        assertTrue(old.workouts.all { it.startDate == null })

        val addis = StateMigrations.apply(old, ZoneId.of("Africa/Addis_Ababa"))
        val newYork = StateMigrations.apply(old, ZoneId.of("America/New_York"))
        assertEquals(SnapshotCodec.SCHEMA_VERSION, addis.schemaVersion)
        assertEquals(localDateOf(late, ZoneId.of("Africa/Addis_Ababa")).toString(), addis.workouts[1].startDate)
        assertEquals(localDateOf(late, ZoneId.of("America/New_York")).toString(), newYork.workouts[1].startDate)
        assertNotEquals(addis.workouts[1].startDate, newYork.workouts[1].startDate)
        assertEquals("migration is idempotent", addis, StateMigrations.apply(addis, ZoneId.of("America/New_York")))
        assertEquals(old.workouts.map { it.copy(startDate = null) }, addis.workouts.map { it.copy(startDate = null) })
        assertEquals(old.restTimer, addis.restTimer)
        assertEquals(old.activeWorkoutId, addis.activeWorkoutId)
        assertEquals(LoadUnit.PLATES, addis.activeWorkout!!.exercises[0].sets.single().loadUnit)
        assertEquals(8, addis.activeWorkout!!.exercises[0].sets.single().plates)

        val store = MemoryPersistence(v2Json(current.copy(workouts = current.workouts.map { it.copy(startDate = null) })))
        val opened = repository(store, clock = { 9_000 })
        assertEquals(1, store.writes)
        assertEquals(current.revision + 1, opened.state.value.revision)
        assertTrue(opened.state.value.workouts.all { it.startDate != null })
        val reopened = repository(store)
        assertEquals(1, store.writes)
        assertEquals(opened.state.value, reopened.state.value)
    }
}
