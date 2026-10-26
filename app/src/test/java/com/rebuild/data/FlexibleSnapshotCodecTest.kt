package com.rebuild.data

import com.rebuild.domain.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class FlexibleSnapshotCodecTest {
    private val json = Json { encodeDefaults = true }

    /** Writes any state as a current-version document without validating it, so invalid states reach the decoder. */
    private fun raw(state: AppState): String = buildJsonObject {
        put("format", "rebuild-backup")
        put("version", SnapshotCodec.SCHEMA_VERSION)
        put("state", json.encodeToJsonElement(state))
    }.toString()

    /**
     * Plan edits, a finished extra workout in plates, a finished partial routine with a goal, a skip, a slot removed
     * while it was running and a History correction, and an active routine with an added exercise and a skipped one.
     */
    private suspend fun recorded(): AppState {
        val repo = repository()
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        repo.execute(Action.SavePlanAddition("add-face", Phase.BASELINE, "push", "face-pull", 3, 12, 20, 75, false))
        repo.execute(Action.StartExtraWorkout(1000))
        val extra = repo.state.value.activeWorkout!!.id
        repo.execute(Action.AddWorkoutExercise(extra, "x-row", "row-stack"))
        repo.execute(Action.SetLoadUnit(extra, "x-row", LoadUnit.PLATES))
        repo.execute(Action.SaveSet(extra, "x-row", "x-row-1", null, 12, 2.0, false, 2000, LoadUnit.PLATES, 9))
        repo.execute(Action.FinishWorkout(3000))
        repo.execute(Action.StartWorkout("push", 100_000))
        val push = repo.state.value.activeWorkout!!
        repo.execute(Action.SetPlanSlotRemoved(Phase.BASELINE, "push-fly", true))
        repo.execute(Action.AddWorkoutExercise(push.id, "o-curl", "curl-hammer"))
        repo.execute(Action.SetEntryGoal(push.id, "o-curl", 3, 10, 12, 60, false))
        repo.execute(Action.SaveSet(push.id, "o-curl", "o-curl-1", 12.0, 11, null, false, 101_000))
        repo.execute(Action.SaveSet(push.id, push.exercises[0].id, "inc-1", 24.0, 9, 2.0, false, 102_000))
        repo.execute(Action.SetSkipped(push.id, push.exercises[0].id, true))
        repo.execute(Action.FinishWorkout(110_000, countForWeek = true))
        repo.execute(Action.AddWorkoutExercise(push.id, "late-calf", "calf-leg-press", perLeg = false))
        repo.execute(Action.SaveSet(push.id, "late-calf", "late-calf-1", 80.0, 15, null, false, 120_000))
        repo.execute(Action.StartWorkout("pull", 200_000))
        val pull = repo.state.value.activeWorkout!!
        repo.execute(Action.AddWorkoutExercise(pull.id, "a-face", "face-pull"))
        repo.execute(Action.MoveWorkoutExercise(pull.id, "a-face", 0))
        repo.execute(Action.SetSkipped(pull.id, pull.exercises[0].id, true))
        return repo.state.value
    }

    private fun AppState.editWorkout(index: Int, edit: (Workout) -> Workout) =
        copy(workouts = workouts.mapIndexed { i, w -> if (i == index) edit(w) else w })

    private fun AppState.editEntry(workout: Int, id: String, edit: (WorkoutExercise) -> WorkoutExercise) =
        editWorkout(workout) { w -> w.copy(exercises = w.exercises.map { if (it.id == id) edit(it) else it }) }

    @Test fun versionFourRoundTripKeepsPlanEditsExtraOptionalAndSkippedEntriesExactly() = runTest {
        val state = recorded()
        val encoded = SnapshotCodec.encode(state)
        assertTrue(encoded.contains("\"version\":7"))
        assertTrue(encoded.contains("\"schemaVersion\":7"))
        val decoded = SnapshotCodec.decode(encoded)
        assertEquals(state, decoded)
        // A 0.1.8 (v4) label can't carry the version-five fields, even with default values.
        val v5 = v5Json(state)
        assertEquals(state.beforeCatalogTwo().copy(schemaVersion = 5), SnapshotCodec.decode(v5))
        rejected { SnapshotCodec.decode(relabelled(v5, 5, 4)) }
        // Nor a 0.1.9 (v5) label the version-six fields.
        failure("version-six backup") { SnapshotCodec.decode(relabelled(v6Json(state), 6, 5)) }
        assertEquals(encoded, SnapshotCodec.encode(decoded))
        assertEquals(listOf(PlanAddition("add-face", Phase.BASELINE, "push", "face-pull", 3, 12, 20, 75, false)),
            decoded.planAdditions)
        assertEquals(listOf(PlanRemoval(Phase.BASELINE, "push-fly")), decoded.planRemovals)
        val (extra, push, pull) = decoded.workouts
        assertNull(extra.templateId)
        assertTrue(extra.exercises.single().optional)
        assertEquals(9, extra.exercises.single().sets.single().plates)
        assertEquals(LoadUnit.PLATES, extra.exercises.single().sets.single().loadUnit)
        assertTrue(push.countedForWeek)
        assertTrue(push.exercises[0].skipped)
        assertEquals(2, push.exercises[0].targetSets)
        assertEquals("History keeps the snapshot it started with, removed slot included",
            listOf("push-incline", "push-shoulder", "push-lateral", "push-fly", "push-triceps", "add-face", null, null),
            push.exercises.map { it.slotId })
        assertEquals(3, push.exercises.first { it.id == "o-curl" }.targetSets)
        assertEquals(listOf("late-calf-1"), push.exercises.first { it.id == "late-calf" }.sets.map { it.id })
        assertEquals("a-face", pull.exercises.first().id)
        assertTrue(pull.exercises[1].skipped)
    }

    @Test fun versionFourFieldTypesAreStrict() = runTest {
        val source = SnapshotCodec.encode(recorded())
        val addition = "{\"id\":\"add-face\",\"phase\":\"BASELINE\",\"templateId\":\"push\",\"exerciseId\":\"face-pull\"," +
            "\"sets\":3,\"minReps\":12,\"maxReps\":20,\"restSeconds\":75,\"perLeg\":false," +
            "\"effortSeconds\":null,\"effortDistance\":null,\"totalSeconds\":null,\"totalDistance\":null}"
        val removals = "\"planRemovals\":[{\"phase\":\"BASELINE\",\"slotId\":\"push-fly\"}]"
        listOf(addition, removals, "\"optional\":true", "\"optional\":false", "\"skipped\":true", "\"skipped\":false",
            "\"templateId\":null").forEach { assertTrue(it, source.contains(it)) }
        mapOf(
            "quoted optional" to source.replaceFirst("\"optional\":true", "\"optional\":\"true\""),
            "numeric optional" to source.replaceFirst("\"optional\":false", "\"optional\":0"),
            "null optional" to source.replaceFirst("\"optional\":false", "\"optional\":null"),
            "quoted skipped" to source.replaceFirst("\"skipped\":true", "\"skipped\":\"true\""),
            "null skipped" to source.replaceFirst("\"skipped\":false", "\"skipped\":null"),
            "quoted addition sets" to source.replace(addition, addition.replace("\"sets\":3", "\"sets\":\"3\"")),
            "decimal addition sets" to source.replace(addition, addition.replace("\"sets\":3", "\"sets\":3.0")),
            "quoted addition reps" to source.replace(addition, addition.replace("\"minReps\":12", "\"minReps\":\"12\"")),
            "exponent addition rest" to source.replace(addition, addition.replace("\"restSeconds\":75", "\"restSeconds\":7.5e1")),
            "quoted addition per leg" to source.replace(addition, addition.replace("\"perLeg\":false", "\"perLeg\":\"false\"")),
            "missing addition per leg" to source.replace(addition, addition.replace(",\"perLeg\":false", "")),
            "addition object list" to source.replace("\"planAdditions\":[$addition]", "\"planAdditions\":$addition"),
            "unknown addition field" to source.replace(addition, addition.replace("\"perLeg\":false", "\"perLeg\":false,\"x\":1")),
            "removal object list" to source.replace(removals, removals.replace(":[{", ":{").replace("}]", "}")),
            "unknown removal phase" to source.replace(removals, removals.replace("BASELINE", "LATER")),
            "numeric template" to source.replaceFirst("\"templateId\":null", "\"templateId\":7")
        ).forEach { (label, invalid) ->
            assertNotEquals(label, source, invalid)
            try {
                SnapshotCodec.decode(invalid)
                fail("Expected $label to be rejected")
            } catch (expected: RepositoryException) {
                assertFalse(label, expected.message.isNullOrBlank())
            }
        }
    }

    @Test fun versionFourStructureRulesAreEnforced() = runTest {
        val state = recorded()
        SnapshotCodec.decode(raw(state))
        val face = state.planAdditions.single()
        val invalid = listOf(
            "addition reusing a slot ID" to state.copy(planAdditions = listOf(face.copy(id = "push-incline"))),
            "duplicate addition ID" to state.copy(planAdditions = listOf(face, face.copy(templateId = "pull"))),
            "addition in an unavailable phase" to state.copy(planAdditions = listOf(face.copy(phase = Phase.INTRO_ONE))),
            "addition to a missing routine" to state.copy(planAdditions = listOf(face.copy(templateId = "nope"))),
            "addition of a missing exercise" to state.copy(planAdditions = listOf(face.copy(exerciseId = "nope"))),
            "addition already in the routine" to state.copy(planAdditions = listOf(face.copy(exerciseId = "lateral-cable"))),
            "addition listed twice" to state.copy(planAdditions = listOf(face, face.copy(id = "add-face-2"))),
            "addition without sets" to state.copy(planAdditions = listOf(face.copy(sets = 0))),
            "addition with an inverted range" to state.copy(planAdditions = listOf(face.copy(minReps = 20, maxReps = 12))),
            "addition without rest" to state.copy(planAdditions = listOf(face.copy(restSeconds = 0))),
            "duplicate removal" to state.copy(planRemovals = state.planRemovals + state.planRemovals),
            "removal in an unavailable phase" to state.copy(planRemovals = listOf(PlanRemoval(Phase.INTRO_ONE, "push-fly"))),
            "removal of a missing slot" to state.copy(planRemovals = listOf(PlanRemoval(Phase.BASELINE, "nope"))),
            "routine left empty" to state.copy(planRemovals = state.planRemovals +
                listOf("pull-vertical", "pull-row", "pull-rear", "pull-curl").map { PlanRemoval(Phase.BASELINE, it) }),
            "added entry with a slot" to state.editEntry(1, "o-curl") { it.copy(slotId = "push-fly") },
            "added entry skipped" to state.editEntry(1, "o-curl") { it.copy(skipped = true) },
            "added entry with a baseline" to state.editEntry(1, "o-curl") { it.copy(baselineSets = 2) },
            "added entry with an upper limit" to state.editEntry(1, "o-curl") { it.copy(upperSets = 4) },
            "added entry with a target RIR" to state.editEntry(1, "o-curl") { it.copy(targetRir = 2) },
            "goal with one rep end" to state.editEntry(1, "o-curl") { it.copy(maxReps = null) },
            "goal without sets in range" to state.editEntry(1, "o-curl") { it.copy(targetSets = 0) },
            "planned entry without a target" to state.editWorkout(1) { w ->
                w.copy(exercises = w.exercises.mapIndexed { i, e -> if (i == 1) e.copy(targetSets = null) else e }) },
            "planned entry without a range" to state.editWorkout(1) { w ->
                w.copy(exercises = w.exercises.mapIndexed { i, e -> if (i == 1) e.copy(minReps = null, maxReps = null) else e }) },
            "planned entry without a slot" to state.editWorkout(1) { w ->
                w.copy(exercises = w.exercises.mapIndexed { i, e -> if (i == 1) e.copy(slotId = null) else e }) },
            "planned entry without RIR" to state.editWorkout(1) { w ->
                w.copy(exercises = w.exercises.mapIndexed { i, e -> if (i == 1) e.copy(targetRir = null) else e }) },
            "original slot without baseline" to state.editWorkout(1) { w ->
                w.copy(exercises = w.exercises.mapIndexed { i, e -> if (i == 1) e.copy(baselineSets = null) else e }) },
            "added plan exercise with a baseline" to state.editWorkout(1) { w ->
                w.copy(exercises = w.exercises.map { if (it.slotId == "add-face") it.copy(baselineSets = 2) else it }) },
            "another routine's added exercise" to state.copy(planAdditions = listOf(face.copy(templateId = "pull"))),
            "duplicate plan slot" to state.editWorkout(1) { w ->
                w.copy(exercises = w.exercises.mapIndexed { i, e -> if (i == 1) e.copy(slotId = w.exercises[0].slotId) else e }) },
            "extra workout with a planned entry" to state.editWorkout(0) { w ->
                w.copy(exercises = w.exercises.map { it.copy(optional = false, slotId = "push-incline", targetSets = 2,
                    baselineSets = 2, upperSets = 3, minReps = 8, maxReps = 12, targetRir = 2) }) },
            "credited extra workout" to state.editWorkout(0) { it.copy(countedForWeek = true) },
            "finished extra workout without exercises" to state.editWorkout(0) { it.copy(exercises = emptyList()) },
            "planned workout of only added exercises" to state.editWorkout(1) { w ->
                w.copy(exercises = w.exercises.filter { it.optional }) },
            "workout of a missing routine" to state.editWorkout(1) { it.copy(templateId = "nope") }
        )
        invalid.forEach { (label, value) ->
            try {
                SnapshotCodec.decode(raw(value))
                fail("Expected $label to be rejected")
            } catch (expected: RepositoryException) {
                assertFalse(label, expected.message.isNullOrBlank())
            }
        }
        // An active extra workout may still be empty: exercises are added while logging.
        val empty = state.copy(workouts = state.workouts.dropLast(1) +
            state.workouts.last().copy(templateId = null, name = EXTRA_WORKOUT_NAME, exercises = emptyList()), restTimer = null)
        assertEquals(empty, SnapshotCodec.decode(raw(empty)))
    }

    @Test fun olderDocumentsRejectEveryVersionFourFieldAndShapeEvenWithDefaultValues() = runTest {
        val repo = repository()
        val workout = repo.start()
        repo.log(id = "old-set", kg = 20.0, now = 2000)
        val old = repo.state.value
        val v3 = v3Json(old)
        assertEquals(3, SnapshotCodec.decode(v3).schemaVersion)
        assertEquals(workout.id, SnapshotCodec.decode(v3).activeWorkout!!.id)
        val injections = listOf(
            "\"catalogRevision\":1" to "\"catalogRevision\":1,\"planAdditions\":[]",
            "\"catalogRevision\":1" to "\"catalogRevision\":1,\"planRemovals\":[]",
            "\"entryLoadUnit\":\"KG\"" to "\"entryLoadUnit\":\"KG\",\"optional\":false",
            "\"entryLoadUnit\":\"KG\"" to "\"entryLoadUnit\":\"KG\",\"skipped\":false",
            "\"templateId\":\"push\"" to "\"templateId\":null",
            "\"slotId\":\"push-incline\"" to "\"slotId\":null"
        )
        injections.forEach { (anchor, replacement) ->
            assertTrue(anchor, v3.contains(anchor))
            rejected { SnapshotCodec.decode(v3.replaceFirst(anchor, replacement)) }
        }
        val v2 = v2Json(old.copy(workouts = old.workouts.map { it.copy(startDate = null) }))
        SnapshotCodec.decode(v2)
        injections.take(4).forEach { (anchor, replacement) ->
            rejected { SnapshotCodec.decode(v2.replaceFirst(anchor, replacement)) }
        }
        val v1 = legacyJson()
        SnapshotCodec.decode(v1)
        listOf("\"planAdditions\":[]", "\"planRemovals\":[]").forEach { field ->
            rejected { SnapshotCodec.decode(v1.replace("\"schemaVersion\":1", "\"schemaVersion\":1,$field")) }
        }
    }

    @Test fun versionThreeDataMigratesOnceToFourKeepingEveryRecordedValue() = runTest {
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
        repo.execute(Action.SetLoadUnit(active.id, active.exercises[0].id, LoadUnit.PLATES))
        repo.execute(Action.LogStage(active.id, active.exercises[0].id, "a-rp", SetMethod.REST_PAUSE, "a-r1", null, 10, null,
            101_000, LoadUnit.PLATES, 9))
        repo.execute(Action.StartShortPause(active.id, active.exercises[0].id, 20, 101_500))
        val current = repo.state.value
        val v3 = v3Json(current)
        val old = SnapshotCodec.decode(v3)
        assertEquals(3, old.schemaVersion)
        assertEquals(current.beforeCatalogTwo().copy(schemaVersion = 3), old)
        val store = MemoryPersistence(v3)
        val migrated = repository(store, clock = { 900_000 }).state.value
        assertEquals(SnapshotCodec.SCHEMA_VERSION, migrated.schemaVersion)
        assertEquals(old.revision + 1, migrated.revision)
        assertEquals(1, store.writes)
        assertEquals("Every workout, set, stage, date, credit and timer is kept exactly; only the library grows",
            old.copy(schemaVersion = SnapshotCodec.SCHEMA_VERSION, revision = migrated.revision,
                unbackedUpSince = migrated.unbackedUpSince, catalogRevision = ExerciseCatalog.REVISION,
                exercises = old.exercises + ExerciseCatalog.revisionTwo), migrated)
        assertTrue(migrated.planAdditions.isEmpty())
        assertTrue(migrated.planRemovals.isEmpty())
        assertTrue(migrated.workouts.flatMap { it.exercises }.none { it.optional || it.skipped })
        val reopened = repository(store, clock = { 950_000 }).state.value
        assertEquals(migrated, reopened)
        assertEquals("Opening again never migrates twice", 1, store.writes)
        val preview = repo.previewBackup(v3)
        assertTrue(preview.dataUpdated)
        assertTrue("Catalog revision two's exercises are added to the library", preview.contentUpdated)
        assertFalse("A version-three file already has its start dates", preview.startDatesAdded)
        assertFalse(preview.futurePlanUpdated)
        assertEquals(SnapshotCodec.SCHEMA_VERSION, preview.state.schemaVersion)
        assertEquals(old.workouts, preview.state.workouts)
    }
}
