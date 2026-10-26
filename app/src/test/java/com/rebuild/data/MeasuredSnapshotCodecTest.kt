package com.rebuild.data

import com.rebuild.domain.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class MeasuredSnapshotCodecTest {
    private val json = Json { encodeDefaults = true }
    private val addis = ZoneId.of("Africa/Addis_Ababa")
    /** Monday 28 September 2026. */
    private val monday = LocalDate.of(2026, 9, 28)

    private fun at(day: Long, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(monday.plusDays(day).atTime(hour, minute), addis).toInstant().toEpochMilli()

    private fun date(day: Long) = monday.plusDays(day).toString()
    private fun m(thousandths: Long) = Distance(thousandths, DistanceUnit.M)
    private fun mi(thousandths: Long) = Distance(thousandths, DistanceUnit.MI)

    /** Writes any state as a current-version document without validating it, so invalid states reach the decoder. */
    private fun raw(state: AppState): String = buildJsonObject {
        put("format", "rebuild-backup")
        put("version", SnapshotCodec.SCHEMA_VERSION)
        put("state", json.encodeToJsonElement(state))
    }.toString()

    private val core = listOf(
        RoutineExercise("plank", 2, null, null, 60, false, effortSeconds = 30),
        RoutineExercise("rowing-machine", null, null, null, 90, false, totalDistance = m(2_000_000)))

    /**
     * A Core routine on Monday, finished short of its plank goal but credited, with an added run and weighted plank; a
     * farmer's carry added to Push's plan; and Tuesday's Push in progress with a logged carry and a paused stopwatch.
     */
    private suspend fun recorded(): AppState {
        val repo = repository()
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        repo.execute(Action.SaveRoutine("r-core", "Core", core))
        repo.execute(Action.ScheduleRoutine("p-core", Phase.BASELINE, 1, "r-core"))
        repo.execute(Action.StartWorkout("p-core", at(0, 18), date(0)))
        val monday = repo.state.value.activeWorkout!!
        val (plank, row) = monday.exercises
        repo.execute(Action.SaveEffort(monday.id, plank.id, "e-plank", 45, null, null, at(0, 18, 1)))
        repo.execute(Action.SaveEffort(monday.id, row.id, "e-row", 480, m(2_000_000), null, at(0, 18, 10)))
        repo.execute(Action.AddWorkoutExercise(monday.id, "o-run", "running"))
        repo.execute(Action.SetEntryGoal(monday.id, "o-run", null, null, null, 90, false, totalDistance = mi(1500)))
        repo.execute(Action.SaveEffort(monday.id, "o-run", "e-run", null, mi(1243), null, at(0, 18, 20)))
        repo.execute(Action.AddWorkoutExercise(monday.id, "o-weighted", "plank-weighted"))
        repo.execute(Action.SaveEffort(monday.id, "o-weighted", "e-weighted", 40, null, 10.0, at(0, 18, 30)))
        repo.execute(Action.FinishWorkout(at(0, 19)))
        repo.execute(Action.SetWeekCredit(monday.id, true))
        repo.execute(Action.SavePlanAddition("a-carry", Phase.BASELINE, "push", "farmers-carry", 3, null, null, 120,
            false, effortDistance = m(40_000)))
        repo.execute(Action.StartWorkout("push", at(1, 18), date(1)))
        val push = repo.state.value.activeWorkout!!
        val carry = push.exercises.single { it.exercise.id == "farmers-carry" }
        repo.execute(Action.SaveEffort(push.id, carry.id, "e-carry", null, m(40_000), 24.0, at(1, 18, 5)))
        repo.execute(Action.StartStopwatch("sw", push.id, carry.id, Monotonic(7, "process-1", 1_000), at(1, 18, 8)))
        repo.execute(Action.PauseStopwatch("sw", 0, Monotonic(7, "process-1", 31_000)))
        return repo.state.value
    }

    private fun AppState.editEntry(workout: Int, exerciseId: String, edit: (WorkoutExercise) -> WorkoutExercise) =
        copy(workouts = workouts.mapIndexed { i, w ->
            if (i != workout) w else w.copy(exercises = w.exercises.map { if (it.exercise.id == exerciseId) edit(it) else it })
        })

    private fun AppState.editEffort(workout: Int, exerciseId: String, edit: (Effort) -> Effort) =
        editEntry(workout, exerciseId) { it.copy(efforts = it.efforts.map(edit)) }

    private fun AppState.editWatch(edit: (Stopwatch) -> Stopwatch) = copy(stopwatch = edit(stopwatch!!))

    @Test fun versionSixRoundTripKeepsGoalsEffortsUnitsAndTheStopwatchExactly() = runTest {
        val state = recorded()
        val encoded = SnapshotCodec.encode(state)
        assertTrue(encoded.contains("\"version\":7"))
        val decoded = SnapshotCodec.decode(encoded)
        assertEquals(state, decoded)
        assertEquals(encoded, SnapshotCodec.encode(decoded))
        assertTrue("A distance keeps the unit and digits it was entered in",
            encoded.contains("\"distance\":{\"thousandths\":1243,\"unit\":\"MI\"}"))
        assertEquals(core, decoded.routines.single().exercises)
        assertEquals(MeasuredGoal(effortDistance = m(40_000)), decoded.planAdditions.single { it.id == "a-carry" }.measuredGoal)
        val (finished, active) = decoded.workouts
        assertTrue(finished.countedForWeek)
        assertFalse(finished.targetsMet())
        assertEquals(listOf("plank", "rowing-machine", "running", "plank-weighted"), finished.exercises.map { it.exercise.id })
        assertEquals(listOf(Measure.TIMED, Measure.CARDIO, Measure.CARDIO, Measure.TIMED),
            finished.exercises.map { it.exercise.measure })
        assertEquals(listOf(MeasuredGoal(effortSeconds = 30), MeasuredGoal(totalDistance = m(2_000_000)),
            MeasuredGoal(totalDistance = mi(1500)), MeasuredGoal()), finished.exercises.map { it.measuredGoal })
        assertEquals(listOf(
            Effort("e-plank", 45, null, null, LoadUnit.KG, null, at(0, 18, 1)),
            Effort("e-row", 480, m(2_000_000), null, LoadUnit.KG, null, at(0, 18, 10)),
            Effort("e-run", null, mi(1243), null, LoadUnit.KG, null, at(0, 18, 20)),
            Effort("e-weighted", 40, null, 10.0, LoadUnit.KG, null, at(0, 18, 30))
        ), finished.exercises.flatMap { it.efforts })
        val carry = active.exercises.single { it.exercise.id == "farmers-carry" }
        assertEquals("a-carry", carry.slotId)
        assertFalse(carry.optional)
        assertEquals(listOf(3, null, null, null, 120), listOf(carry.targetSets, carry.minReps, carry.maxReps,
            carry.targetRir, carry.restSeconds))
        assertEquals(Stopwatch("sw", active.id, carry.id, 30_000, null, null, null, false, false, at(1, 18, 8), 1),
            decoded.stopwatch)
        // Every state a stopwatch can be saved in comes back exactly.
        val watch = state.stopwatch!!
        listOf(
            watch.copy(runningSince = 5_000, boot = 7, session = "process-1"),
            watch.copy(runningSince = 5_000, boot = null, session = "process-1"),
            watch.copy(stopped = true),
            watch.copy(stopped = true, needsConfirmation = true)
        ).forEach { variant ->
            val saved = state.copy(stopwatch = variant)
            assertEquals(saved, SnapshotCodec.decode(SnapshotCodec.encode(saved)))
        }
        // A 0.1.9 (v5) label can't carry any of it.
        failure("version-six backup") { SnapshotCodec.decode(relabelled(v6Json(state), 6, 5)) }
    }

    @Test fun aRestoredRunningStopwatchComesBackStoppedForItsTimeToBeEntered() = runTest {
        val state = recorded()
        val running = state.editWatch { it.copy(runningSince = 31_000, boot = 7, session = "process-1") }
        val store = MemoryPersistence()
        val destination = repository(store)
        val preview = destination.previewBackup(SnapshotCodec.encode(running))
        val unconfirmed = running.stopwatch!!.copy(runningSince = null, boot = null, session = null, stopped = true,
            needsConfirmation = true, step = running.stopwatch!!.step + 1)
        assertEquals("Another clock's running time is never trusted", unconfirmed, preview.state.stopwatch)
        assertEquals("Nothing else changes", running.copy(stopwatch = unconfirmed), preview.state)
        assertFalse(preview.dataUpdated)
        assertFalse(preview.contentUpdated)
        assertFalse(preview.futurePlanUpdated)
        assertFalse(preview.startDatesAdded)
        val safety = destination.exportBackup()
        destination.execute(Action.MarkBackup(safety.revision, 10_000))
        destination.restoreBackup(preview, destination.state.value.revision)
        val restored = destination.state.value
        assertEquals(unconfirmed, restored.stopwatch)
        assertEquals(running.workouts, restored.workouts)
        assertEquals(running.routines, restored.routines)
        assertEquals(running.placements, restored.placements)
        assertEquals(running.planAdditions, restored.planAdditions)
        assertEquals(running.activeWorkoutId, restored.activeWorkoutId)
        assertNull(restored.restTimer)
        assertEquals("It stays unconfirmed after the app restarts", restored, repository(store).state.value)
        // A paused stopwatch has no running time to doubt, so it comes back exactly as saved.
        val paused = repository().previewBackup(SnapshotCodec.encode(state))
        assertEquals(state, paused.state)
        assertFalse(paused.dataUpdated)
    }

    @Test fun versionSixStructureRulesAreEnforced() = runTest {
        val state = recorded()
        SnapshotCodec.decode(raw(state))
        val (finished, active) = state.workouts
        val plank = finished.exercises.single { it.exercise.id == "plank" }
        val carry = active.exercises.single { it.exercise.id == "farmers-carry" }
        val repsEntry = active.exercises.first { it.exercise.measure == Measure.REPS }
        val invalid: List<Triple<String, AppState, String>> = listOf(
            Triple("effort on a rep exercise", state.editEntry(1, repsEntry.exercise.id) {
                it.copy(efforts = listOf(Effort("x", 30, null, null, LoadUnit.KG, null, at(1, 18)))) },
                "rep-based exercise is logged in sets"),
            Triple("timed effort with a distance", state.editEffort(0, "plank") { it.copy(distance = m(1000)) },
                "records a time, not a distance"),
            Triple("timed effort without a time", state.editEffort(0, "plank") { it.copy(durationSeconds = null) },
                "how long Plank lasted"),
            Triple("a day and a second", state.editEffort(0, "plank") { it.copy(durationSeconds = 86_401) },
                "1 second to 24 hours"),
            Triple("cardio with a load", state.editEffort(0, "rowing-machine") { it.copy(kg = 5.0) },
                "Cardio doesn't record a load"),
            Triple("cardio with nothing measured", state.editEffort(0, "running") { it.copy(distance = null) },
                "a duration, a distance or both"),
            Triple("no distance", state.editEffort(0, "running") { it.copy(distance = mi(0)) }, "more than 0"),
            Triple("past 1,000 mi", state.editEffort(0, "running") { it.copy(distance = mi(1_000_001)) },
                "at most 1,000 mi"),
            Triple("carry without its load", state.editEffort(1, "farmers-carry") { it.copy(kg = null) },
                "Weight or assistance"),
            Triple("carry in plates", state.editEffort(1, "farmers-carry") {
                it.copy(kg = null, loadUnit = LoadUnit.PLATES, plates = 4) }, "Plate counts are only available"),
            Triple("bodyweight hold with a load", state.editEffort(0, "plank") { it.copy(kg = 5.0) }, "Bodyweight"),
            Triple("measured differently from the library", state.editEntry(0, "plank-weighted") {
                it.copy(exercise = it.exercise.copy(measure = Measure.DISTANCE)) }, "measurement type differs"),
            Triple("required hold without its minimum", state.editEntry(0, "plank") { it.copy(effortSeconds = null) },
                "minimum for each"),
            Triple("timed goal with a distance", state.editEntry(0, "plank") { it.copy(effortDistance = m(1000)) },
                "number of sets and the minimum"),
            Triple("cardio with sets", state.editEntry(0, "rowing-machine") { it.copy(targetSets = 3) },
                "not sets or reps"),
            Triple("required cardio without a goal", state.editEntry(0, "rowing-machine") { it.copy(totalDistance = null) },
                "total time, a total distance or both"),
            Triple("measured entry per leg", state.editEntry(0, "plank") { it.copy(perLeg = true) }, "per leg"),
            Triple("set on a measured entry", state.editEntry(0, "plank") {
                it.copy(sets = listOf(LoggedSet("s", null, 10, null, false, at(0, 18, 2)))) }, "logged as efforts"),
            Triple("measured entry with a prescription", state.editEntry(0, "plank") { it.copy(targetRir = 2) },
                "with no prescription"),
            Triple("measured entry in an original slot", state.editEntry(0, "plank") {
                it.copy(slotId = state.templates.first().slots.first().id) }, "with no prescription"),
            Triple("rep entry with a measured goal", state.editEntry(1, repsEntry.exercise.id) {
                it.copy(effortSeconds = 30) }, "no timed, distance or cardio goal"),
            Triple("duplicate effort ID", state.editEffort(0, "running") { it.copy(id = "e-plank") }, "duplicate"),
            Triple("unconfirmed time still open", state.editWatch { it.copy(needsConfirmation = true) },
                "needs confirming is stopped"),
            Triple("running beside a rest", state.editWatch { it.copy(runningSince = 5_000, boot = 7, session = "p") }
                .copy(restTimer = RestTimer("r", active.id, carry.id, at(1, 19))), "can't run while the stopwatch"),
            Triple("running without its process", state.editWatch { it.copy(runningSince = 5_000) },
                "record the clock it started on"),
            Triple("paused holding a boot", state.editWatch { it.copy(boot = 7) }, "holds no clock"),
            Triple("timing a rep exercise", state.editWatch { it.copy(exerciseId = repsEntry.id) },
                "must time a timed, distance or cardio exercise"),
            Triple("timing a finished workout", state.editWatch { it.copy(workoutId = finished.id, exerciseId = plank.id) },
                "must belong to the active workout"),
            Triple("timing a logged effort", state.editWatch { it.copy(id = "e-carry") }, "already logged"),
            Triple("timing a skipped exercise", state.editEntry(1, "farmers-carry") { it.copy(skipped = true) },
                "must time a timed, distance or cardio exercise"),
            Triple("time past the limit", state.editWatch { it.copy(elapsedMs = MAX_STOPWATCH_MS + 1) },
                "outside the supported range"),
            Triple("negative time", state.editWatch { it.copy(elapsedMs = -1) }, ""),
            Triple("negative start", state.editWatch { it.copy(runningSince = -1, session = "p") }, ""),
            Triple("negative step", state.editWatch { it.copy(step = -1) }, "stopwatch's step"),
            Triple("step past the limit", state.editWatch { it.copy(step = MAX_STOPWATCH_STEP + 1) }, "step is outside")
        )
        invalid.forEach { (label, value, messagePart) ->
            try {
                SnapshotCodec.decode(raw(value))
                fail("Expected $label to be rejected")
            } catch (expected: RepositoryException) {
                val message = expected.message.orEmpty()
                assertTrue("$label: $message", message.isNotBlank() && message.contains(messagePart, ignoreCase = true))
            }
        }
    }

    @Test fun versionSixFieldTypesAreStrict() = runTest {
        val source = raw(recorded())
        listOf(
            "\"unit\":\"MI\"" to "\"unit\":\"YD\"",
            "\"unit\":\"MI\"" to "\"unit\":2",
            "\"thousandths\":1243" to "\"thousandths\":1243.5",
            "\"thousandths\":1243" to "\"thousandths\":\"1243\"",
            "\"thousandths\":1243" to "\"thousandths\":1e3",
            "\"distance\":{\"thousandths\":1243,\"unit\":\"MI\"}" to "\"distance\":\"1.243 mi\"",
            "\"durationSeconds\":45" to "\"durationSeconds\":\"45\"",
            "\"durationSeconds\":45" to "\"durationSeconds\":45.0",
            "\"effortSeconds\":30" to "\"effortSeconds\":\"30\"",
            "\"measure\":\"TIMED\"" to "\"measure\":\"timed\"",
            "\"measure\":\"TIMED\"" to "\"measure\":\"STRETCH\"",
            "\"kg\":24.0" to "\"kg\":\"24\"",
            "\"efforts\":[]" to "\"efforts\":null",
            "\"elapsedMs\":30000" to "\"elapsedMs\":\"30000\"",
            "\"elapsedMs\":30000" to "\"elapsedMs\":30000.5",
            "\"boot\":null" to "\"boot\":\"7\"",
            "\"session\":null" to "\"session\":7",
            "\"stopped\":false" to "\"stopped\":0",
            "\"needsConfirmation\":false" to "\"needsConfirmation\":\"false\"",
            "\"step\":1" to "\"step\":\"1\"",
            "\"step\":1" to "\"step\":1.5",
            "\"step\":1" to "\"step\":1e0",
            "\"step\":1" to "\"step\":null",
            ",\"step\":1" to ""
        ).forEach { (anchor, replacement) ->
            assertTrue(anchor, source.contains(anchor))
            try {
                SnapshotCodec.decode(source.replaceFirst(anchor, replacement))
                fail("Expected $replacement to be rejected")
            } catch (expected: RepositoryException) {
                assertFalse(expected.message.isNullOrBlank())
            }
        }
    }

    @Test fun richVersionFiveDataGainsTheNewExercisesOnceAndKeepsEverythingElseExactly() = runTest {
        val repo = repository()
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        repo.execute(Action.SaveRoutine("r-arms", "Arms", listOf(RoutineExercise("face-pull", 2, 12, 20, 75, false),
            RoutineExercise("curl-hammer", 2, 10, 12, 60, false))))
        repo.execute(Action.ScheduleRoutine("p-mon", Phase.BASELINE, 1, "r-arms"))
        repo.execute(Action.RemoveFromWeeklyPlan(Phase.BASELINE, "lower-b"))
        repo.execute(Action.SavePlanAddition("a-face", Phase.BASELINE, "push", "face-pull", 2, 12, 15, 60, false))
        val pushSlot = repo.state.value.templates.single { it.id == "push" }.slots.last().id
        repo.execute(Action.SetPlanSlotRemoved(Phase.BASELINE, pushSlot, true))
        repo.execute(Action.StartWorkout("pull", at(2, 18), date(2)))
        val pull = repo.state.value.activeWorkout!!
        val cable = pull.exercises[0]
        repo.execute(Action.SetLoadUnit(pull.id, cable.id, LoadUnit.PLATES))
        repo.execute(Action.LogStage(pull.id, cable.id, "g-drop", SetMethod.DROP, "d1", null, 10, null, at(2, 18, 5),
            LoadUnit.PLATES, 9))
        repo.execute(Action.LogStage(pull.id, cable.id, "g-drop", SetMethod.DROP, "d2", null, 8, null, at(2, 18, 6),
            LoadUnit.PLATES, 7))
        repo.execute(Action.FinishGroup(pull.id, cable.id, "g-drop", at(2, 18, 7)))
        repo.execute(Action.FinishWorkout(at(2, 19), countForWeek = true))
        repo.execute(Action.StartExtraWorkout(at(3, 10), date(3)))
        val extra = repo.state.value.activeWorkout!!
        repo.execute(Action.AddWorkoutExercise(extra.id, "x-row", "row-db"))
        repo.execute(Action.SaveSet(extra.id, "x-row", "x1", 30.0, 10, 2.0, false, at(3, 10, 5)))
        repo.execute(Action.FinishWorkout(at(3, 11)))
        repo.execute(Action.StartWorkout("p-mon", at(7, 18), date(7)))
        val active = repo.state.value.activeWorkout!!
        repo.log(active, active.exercises[0], id = "a1", kg = 15.0, reps = 15, now = at(7, 18, 5))
        val current = repo.state.value
        assertNotNull(current.restTimer)
        val document = v5Json(current)
        val old = SnapshotCodec.decode(document)
        assertEquals(5, old.schemaVersion)
        assertEquals(1, old.catalogRevision)
        assertTrue(old.exercises.none { it.measured })

        val preview = repository().previewBackup(document)
        assertEquals("The phone gets exactly what 0.1.9 had, plus the new exercises", current, preview.state)
        assertTrue(preview.dataUpdated)
        assertTrue(preview.contentUpdated)
        assertFalse(preview.futurePlanUpdated)
        assertFalse(preview.startDatesAdded)

        val store = MemoryPersistence(document)
        val opened = repository(store, clock = { 9_000 })
        assertEquals("Upgraded once, in one write", 1, store.writes)
        val upgraded = opened.state.value
        assertEquals(SnapshotCodec.SCHEMA_VERSION, upgraded.schemaVersion)
        assertEquals(ExerciseCatalog.REVISION, upgraded.catalogRevision)
        assertEquals(old.exercises + ExerciseCatalog.revisionTwo, upgraded.exercises)
        assertEquals(current.copy(revision = old.revision + 1), upgraded)
        assertEquals(old.restTimer, upgraded.restTimer)
        assertNull(upgraded.stopwatch)
        assertTrue("Nothing is measured that wasn't", upgraded.workouts.all { w ->
            w.exercises.all { it.efforts.isEmpty() && it.measuredGoal.empty && it.exercise.measure == Measure.REPS }
        })
        assertTrue(upgraded.planAdditions.all { it.measuredGoal.empty })
        assertTrue(upgraded.routines.all { r -> r.exercises.all { it.measuredGoal.empty } })
        val reopened = repository(store)
        assertEquals("Reopening never rewrites", 1, store.writes)
        assertEquals(upgraded, reopened.state.value)
    }
}
