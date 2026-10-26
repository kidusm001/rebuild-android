package com.rebuild.data

import com.rebuild.domain.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RoutineSnapshotCodecTest {
    private val json = Json { encodeDefaults = true }
    private val addis = ZoneId.of("Africa/Addis_Ababa")
    /** Monday 28 September 2026. */
    private val monday = LocalDate.of(2026, 9, 28)
    private val face = RoutineExercise("face-pull", 2, 12, 20, 75, false)
    private val hammer = RoutineExercise("curl-hammer", 2, 10, 12, 60, false)
    private val row = RoutineExercise("row-stack", 3, 8, 12, 120, false)
    private val calf = RoutineExercise("calf-leg-press", 3, 12, 15, 60, false)

    private fun at(day: Long, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(monday.plusDays(day).atTime(hour, minute), addis).toInstant().toEpochMilli()

    private fun date(day: Long) = monday.plusDays(day).toString()

    /** Writes any state as a current-version document without validating it, so invalid states reach the decoder. */
    private fun raw(state: AppState): String = buildJsonObject {
        put("format", "rebuild-backup")
        put("version", SnapshotCodec.SCHEMA_VERSION)
        put("state", json.encodeToJsonElement(state))
    }.toString()

    private fun String.editState(edit: MutableMap<String, JsonElement>.() -> Unit): String {
        val root = Json.parseToJsonElement(this).jsonObject
        val state = root.getValue("state").jsonObject.toMutableMap().apply(edit)
        return JsonObject(root + ("state" to JsonObject(state))).toString()
    }

    /**
     * Library routines Arms and Back day; an edited copy of Arms on Monday, a trained copy on Friday later taken off the
     * week, an Intro-phase copy whose library routine was deleted, Pull taken off the week, a finished extra workout
     * started from Back day in plates and an active workout of the Monday copy.
     */
    private suspend fun recorded(): AppState {
        val repo = repository()
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        repo.execute(Action.SaveRoutine("r-arms", "Arms", listOf(face, hammer)))
        repo.execute(Action.SaveRoutine("r-back", "Back day", listOf(row)))
        repo.execute(Action.SaveRoutine("r-calves", "Calves", listOf(calf)))
        repo.execute(Action.ScheduleRoutine("p-mon", Phase.BASELINE, 1, "r-arms"))
        repo.execute(Action.ScheduleRoutine("p-fri", Phase.BASELINE, 5, "r-arms"))
        repo.execute(Action.ScheduleRoutine("p-intro", Phase.INTRO_ONE, 1, "r-calves"))
        repo.execute(Action.DeleteRoutine("r-calves"))
        repo.execute(Action.SavePlacement("p-mon", "Arms, heavy", listOf(face.copy(sets = 3), hammer)))
        repo.execute(Action.RemoveFromWeeklyPlan(Phase.BASELINE, "pull"))
        repo.execute(Action.StartWorkout("p-fri", at(4, 18), date(4)))
        val friday = repo.state.value.activeWorkout!!
        repo.log(friday, friday.exercises[0], id = "fri-1", kg = 15.0, reps = 15, now = at(4, 18, 5))
        repo.execute(Action.FinishWorkout(at(4, 19)))
        repo.execute(Action.RemoveFromWeeklyPlan(Phase.BASELINE, "p-fri"))
        repo.execute(Action.StartExtraWorkout(at(5, 10), date(5), routineId = "r-back"))
        val extra = repo.state.value.activeWorkout!!
        repo.execute(Action.SetLoadUnit(extra.id, extra.exercises[0].id, LoadUnit.PLATES))
        repo.execute(Action.SaveSet(extra.id, extra.exercises[0].id, "back-1", null, 10, 2.0, false, at(5, 10, 5),
            LoadUnit.PLATES, 7))
        repo.execute(Action.FinishWorkout(at(5, 11)))
        repo.execute(Action.StartWorkout("p-mon", at(7, 18), date(7)))
        val active = repo.state.value.activeWorkout!!
        repo.log(active, active.exercises[1], id = "mon-1", kg = 12.0, reps = 11, now = at(7, 18, 5))
        return repo.state.value
    }

    private fun AppState.editWorkout(index: Int, edit: (Workout) -> Workout) =
        copy(workouts = workouts.mapIndexed { i, w -> if (i == index) edit(w) else w })

    @Test fun versionFiveRoundTripKeepsRoutinesCopiesRemovalsAndTheirWorkoutsExactly() = runTest {
        val state = recorded()
        val encoded = SnapshotCodec.encode(state)
        assertTrue(encoded.contains("\"version\":7"))
        assertTrue(encoded.contains("\"schemaVersion\":7"))
        val decoded = SnapshotCodec.decode(encoded)
        assertEquals(state, decoded)
        assertEquals(encoded, SnapshotCodec.encode(decoded))
        // A 0.1.8 (v4) label can't carry saved routines or schedule changes.
        val v5 = v5Json(state)
        assertEquals(state.beforeCatalogTwo().copy(schemaVersion = 5), SnapshotCodec.decode(v5))
        failure("version-five backup") { SnapshotCodec.decode(relabelled(v5, 5, 4)) }
        // Nor a 0.1.9 (v5) label version-six measurement types, efforts or the stopwatch.
        failure("version-six backup") { SnapshotCodec.decode(relabelled(v6Json(state), 6, 5)) }
        assertEquals(listOf(CustomRoutine("r-arms", "Arms", listOf(face, hammer)),
            CustomRoutine("r-back", "Back day", listOf(row))), decoded.routines)
        assertEquals(listOf(
            RoutinePlacement("p-mon", Phase.BASELINE, 1, "Arms, heavy", "r-arms"),
            RoutinePlacement("p-fri", Phase.BASELINE, 5, "Arms", "r-arms", removedFromPlan = true),
            RoutinePlacement("p-intro", Phase.INTRO_ONE, 1, "Calves", null)
        ), decoded.placements)
        assertEquals(listOf(ScheduleRemoval(Phase.BASELINE, "pull")), decoded.scheduleRemovals)
        assertEquals("Only copies still on a week keep future exercises",
            mapOf("p-mon" to listOf("face-pull" to 3, "curl-hammer" to 2), "p-intro" to listOf("calf-leg-press" to 3)),
            decoded.planAdditions.groupBy({ it.templateId }, { it.exerciseId to it.sets }))
        assertEquals(listOf("p-mon", "push", "lower-a", "upper", "lower-b"),
            decoded.scheduledRoutines(Phase.BASELINE).map { it.id })
        assertEquals("p-intro", decoded.scheduledRoutines(Phase.INTRO_ONE).first().id)
        assertTrue(decoded.scheduledRoutines(Phase.INTRO_TWO).none { it is RoutinePlacement })
        val (friday, extra, active) = decoded.workouts
        assertEquals("p-fri", friday.templateId)
        assertEquals("Arms", friday.name)
        assertEquals(listOf("face-pull", "curl-hammer"), friday.exercises.map { it.exercise.id })
        assertTrue("The retired copy's workout keeps plan IDs its copy no longer has",
            friday.exercises.all { e -> e.slotId != null && decoded.planAdditions.none { it.id == e.slotId } })
        assertEquals(listOf("fri-1"), friday.exercises[0].sets.map { it.id })
        assertNull(extra.templateId)
        assertEquals("Back day", extra.name)
        assertFalse(extra.countedForWeek)
        val back = extra.exercises.single()
        assertTrue(back.optional)
        assertEquals(listOf(3, 8, 12, 120), listOf(back.targetSets, back.minReps, back.maxReps, back.restSeconds))
        assertEquals(7, back.sets.single().plates)
        assertEquals(LoadUnit.PLATES, back.sets.single().loadUnit)
        assertEquals("p-mon", active.templateId)
        assertEquals(decoded.activeWorkoutId, active.id)
        assertEquals(decoded.planAdditions.filter { it.templateId == "p-mon" }.map { it.id }, active.exercises.map { it.slotId })
        assertEquals(listOf(3, 2), active.exercises.map { it.targetSets })
        assertTrue(active.exercises.none { it.optional || it.baselineSets != null || it.upperSets != null })
        assertEquals(listOf("mon-1"), active.exercises[1].sets.map { it.id })
    }

    @Test fun versionFiveFieldTypesAreStrict() = runTest {
        val source = SnapshotCodec.encode(recorded())
        val routine = "{\"id\":\"r-back\",\"name\":\"Back day\",\"exercises\":[{\"exerciseId\":\"row-stack\",\"sets\":3," +
            "\"minReps\":8,\"maxReps\":12,\"restSeconds\":120,\"perLeg\":false,\"effortSeconds\":null," +
            "\"effortDistance\":null,\"totalSeconds\":null,\"totalDistance\":null}]}"
        val retired = "{\"id\":\"p-fri\",\"phase\":\"BASELINE\",\"weekday\":5,\"name\":\"Arms\",\"routineId\":\"r-arms\"," +
            "\"removedFromPlan\":true}"
        val unlinked = "{\"id\":\"p-intro\",\"phase\":\"INTRO_ONE\",\"weekday\":1,\"name\":\"Calves\",\"routineId\":null," +
            "\"removedFromPlan\":false}"
        val removals = "\"scheduleRemovals\":[{\"phase\":\"BASELINE\",\"templateId\":\"pull\"}]"
        listOf(routine, retired, unlinked, removals).forEach { assertTrue(it, source.contains(it)) }
        mapOf(
            "quoted weekday" to source.replace(retired, retired.replace("\"weekday\":5", "\"weekday\":\"5\"")),
            "decimal weekday" to source.replace(retired, retired.replace("\"weekday\":5", "\"weekday\":5.0")),
            "exponent weekday" to source.replace(retired, retired.replace("\"weekday\":5", "\"weekday\":5e0")),
            "null weekday" to source.replace(retired, retired.replace("\"weekday\":5", "\"weekday\":null")),
            "missing weekday" to source.replace(retired, retired.replace("\"weekday\":5,", "")),
            "quoted removed flag" to source.replace(retired, retired.replace("\"removedFromPlan\":true",
                "\"removedFromPlan\":\"true\"")),
            "numeric removed flag" to source.replace(unlinked, unlinked.replace("\"removedFromPlan\":false",
                "\"removedFromPlan\":0")),
            "null removed flag" to source.replace(unlinked, unlinked.replace("\"removedFromPlan\":false",
                "\"removedFromPlan\":null")),
            "numeric routine link" to source.replace(unlinked, unlinked.replace("\"routineId\":null", "\"routineId\":7")),
            "boolean routine link" to source.replace(unlinked, unlinked.replace("\"routineId\":null", "\"routineId\":false")),
            "unknown placement phase" to source.replace(retired, retired.replace("BASELINE", "LATER")),
            "unknown placement field" to source.replace(retired, retired.replace("\"removedFromPlan\":true",
                "\"removedFromPlan\":true,\"x\":1")),
            "quoted routine sets" to source.replace(routine, routine.replace("\"sets\":3", "\"sets\":\"3\"")),
            "decimal routine reps" to source.replace(routine, routine.replace("\"minReps\":8", "\"minReps\":8.0")),
            "exponent routine rest" to source.replace(routine, routine.replace("\"restSeconds\":120", "\"restSeconds\":1.2e2")),
            "quoted routine per leg" to source.replace(routine, routine.replace("\"perLeg\":false", "\"perLeg\":\"false\"")),
            "missing routine per leg" to source.replace(routine, routine.replace(",\"perLeg\":false", "")),
            "unknown routine field" to source.replace(routine, routine.replace("\"name\":\"Back day\"",
                "\"name\":\"Back day\",\"x\":1")),
            "routine exercises object" to source.replace(routine, routine.replace("\"exercises\":[{", "\"exercises\":{")
                .replace("}]}", "}}")),
            "routine exercises of numbers" to source.replace(routine, routine.replaceAfter("\"exercises\":", "[1]}")),
            "routines object" to source.editState { put("routines", getValue("routines").jsonArray.first()) },
            "placements object" to source.editState { put("placements", getValue("placements").jsonArray.first()) },
            "removals object" to source.editState {
                put("scheduleRemovals", getValue("scheduleRemovals").jsonArray.first())
            },
            "unknown removal phase" to source.replace(removals, removals.replace("BASELINE", "LATER")),
            "unknown removal field" to source.replace(removals, removals.replace("\"templateId\":\"pull\"",
                "\"templateId\":\"pull\",\"x\":1"))
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

    @Test fun versionFiveStructureRulesAreEnforcedEachByItsOwnRule() = runTest {
        val state = recorded()
        assertEquals(state, SnapshotCodec.decode(raw(state)))
        val (arms, back) = state.routines
        val (mon, fri, intro) = state.placements
        val introAddition = state.planAdditions.single { it.templateId == "p-intro" }
        val monAdditions = state.planAdditions.filter { it.templateId == "p-mon" }
        val withRoutines = { routines: List<CustomRoutine> -> state.copy(routines = routines) }
        val withPlacements = { placements: List<RoutinePlacement> -> state.copy(placements = placements) }
        val invalid = listOf(
            Triple("duplicate routine ID", withRoutines(listOf(arms, back.copy(id = "r-arms"))), "duplicate saved routine"),
            Triple("routine sharing a plan workout's ID", withRoutines(listOf(arms, back.copy(id = "push"))), "share an ID"),
            Triple("routine sharing a copy's ID", withRoutines(listOf(arms, back.copy(id = "p-intro"))), "share an ID"),
            Triple("copy sharing a plan workout's ID", withPlacements(listOf(mon, fri, intro.copy(id = "lower-a"))),
                "share an ID"),
            Triple("duplicate copy ID", withPlacements(listOf(mon, fri, intro.copy(id = "p-mon"))),
                "duplicate scheduled routine"),
            Triple("blank routine name", withRoutines(listOf(arms.copy(name = " "), back)), "Routine name"),
            Triple("overlong routine name", withRoutines(listOf(arms.copy(name = "x".repeat(121)), back)), "Routine name"),
            Triple("routine without exercises", withRoutines(listOf(arms.copy(exercises = emptyList()), back)),
                "1–100 exercises"),
            Triple("routine listing an exercise twice",
                withRoutines(listOf(arms.copy(exercises = listOf(face, face.copy(sets = 3))), back)), "exercise twice"),
            Triple("routine of a missing exercise",
                withRoutines(listOf(arms.copy(exercises = listOf(face.copy(exerciseId = "nope"))), back)), "missing exercise"),
            Triple("routine without sets", withRoutines(listOf(arms.copy(exercises = listOf(face.copy(sets = 0))), back)),
                "between 1 and"),
            Triple("routine with an inverted range",
                withRoutines(listOf(arms.copy(exercises = listOf(face.copy(minReps = 20, maxReps = 12))), back)),
                "rep range is invalid"),
            Triple("routine without rest",
                withRoutines(listOf(arms.copy(exercises = listOf(face.copy(restSeconds = 0))), back)), "Rest must be"),
            Triple("more than 100 routines",
                withRoutines(state.routines + (1..99).map { arms.copy(id = "r-extra-$it") }), "at most 100 saved routines"),
            Triple("copy on day 0", withPlacements(listOf(mon.copy(weekday = 0), fri, intro)), "Monday (1) through Sunday (7)"),
            Triple("copy on day 8", withPlacements(listOf(mon.copy(weekday = 8), fri, intro)), "Monday (1) through Sunday (7)"),
            Triple("blank copy name", withPlacements(listOf(mon.copy(name = ""), fri, intro)), "Routine name"),
            Triple("copy linked to a missing routine", withPlacements(listOf(mon, fri, intro.copy(routineId = "r-gone"))),
                "missing saved routine"),
            Triple("retired copy keeping future exercises",
                state.copy(planAdditions = state.planAdditions + introAddition.copy(id = "add-fri", phase = Phase.BASELINE,
                    templateId = "p-fri")), "cannot keep future exercises"),
            Triple("copy left without exercises",
                state.copy(planAdditions = state.planAdditions - introAddition), "Every routine must keep"),
            Triple("copy listing an exercise twice",
                state.copy(planAdditions = state.planAdditions + monAdditions[0].copy(id = "add-again")),
                "added twice"),
            Triple("copy's exercise saved in another phase",
                state.copy(planAdditions = state.planAdditions - introAddition + introAddition.copy(phase = Phase.BASELINE)),
                "unavailable in that phase"),
            Triple("routine taken off the week twice",
                state.copy(scheduleRemovals = state.scheduleRemovals + state.scheduleRemovals), "off the week twice"),
            Triple("original routine taken off a phase it isn't in",
                state.copy(scheduleRemovals = listOf(ScheduleRemoval(Phase.INTRO_ONE, "push"))), "unavailable in that phase"),
            Triple("missing routine taken off the week",
                state.copy(scheduleRemovals = listOf(ScheduleRemoval(Phase.BASELINE, "nope"))), "unavailable in that phase"),
            Triple("a phase's week left empty",
                state.copy(scheduleRemovals = state.availableTemplates(Phase.INTRO_TWO)
                    .map { ScheduleRemoval(Phase.INTRO_TWO, it.id) }), "keep at least one routine"),
            Triple("workout of a copy in another phase", state.editWorkout(0) { it.copy(templateId = "p-intro") },
                "unavailable in its recorded phase"),
            Triple("workout of a deleted copy", withPlacements(listOf(mon, intro)), "missing template"),
            Triple("copy workout using another copy's exercise", state.editWorkout(2) { w ->
                w.copy(exercises = w.exercises.mapIndexed { i, e -> if (i == 0) e.copy(slotId = introAddition.id) else e })
            }, "another routine's added exercise"),
            Triple("copy workout with a prescribed baseline", state.editWorkout(2) { w ->
                w.copy(exercises = w.exercises.mapIndexed { i, e -> if (i == 0) e.copy(baselineSets = 2) else e })
            }, "no prescribed baseline")
        )
        invalid.forEach { (label, value, message) ->
            try {
                SnapshotCodec.decode(raw(value))
                fail("Expected $label to be rejected")
            } catch (expected: RepositoryException) {
                assertTrue("$label: expected '$message' in '${expected.message}'",
                    expected.message!!.contains(message, ignoreCase = true))
            }
        }
        // The rules hold the limits exactly: 100 routines, days 1 and 7, a copy with no library link, a retired copy
        // with no future exercises and the phase's own week emptied of its originals but kept by a copy.
        listOf(
            withRoutines(state.routines + (1..98).map { arms.copy(id = "r-extra-$it") }),
            withPlacements(listOf(mon.copy(weekday = 7), fri.copy(weekday = 1), intro)),
            withPlacements(listOf(mon.copy(routineId = null), fri.copy(routineId = null), intro)),
            state.copy(scheduleRemovals = state.availableTemplates(Phase.INTRO_ONE)
                .map { ScheduleRemoval(Phase.INTRO_ONE, it.id) })
        ).forEach { valid -> assertEquals(valid, SnapshotCodec.decode(raw(valid))) }
    }

    @Test fun olderDocumentsRejectEveryVersionFiveFieldEvenWithDefaultValues() = runTest {
        val repo = repository()
        repo.start()
        repo.log(id = "old-set", kg = 20.0, now = 2000)
        val old = repo.state.value
        val placement = "\"placements\":[{\"id\":\"p-mon\",\"phase\":\"BASELINE\",\"weekday\":1,\"name\":\"Arms\"," +
            "\"routineId\":null,\"removedFromPlan\":false}]"
        val fields = listOf("\"routines\":[]", "\"placements\":[]", "\"scheduleRemovals\":[]", placement)
        mapOf(
            4 to v4Json(old),
            3 to v3Json(old),
            2 to v2Json(old.copy(workouts = old.workouts.map { it.copy(startDate = null) })),
            1 to legacyJson()
        ).forEach { (version, document) ->
            assertEquals(version, SnapshotCodec.decode(document).schemaVersion)
            val anchor = "\"schemaVersion\":$version"
            assertTrue(document.contains(anchor))
            fields.forEach { field ->
                failure("version-five backup") { SnapshotCodec.decode(document.replaceFirst(anchor, "$anchor,$field")) }
            }
        }
    }

    @Test fun versionFourDataMigratesOnceToFiveKeepingEveryRecordedValue() = runTest {
        // A 0.1.8 phone: plan edits, a finished extra workout, a credited partial routine, and an active routine with
        // an added exercise and an open rest-pause set.
        val repo = repository()
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        repo.execute(Action.SavePlanAddition("add-face", Phase.BASELINE, "push", "face-pull", 3, 12, 20, 75, false))
        repo.execute(Action.SetPlanSlotRemoved(Phase.BASELINE, "push-fly", true))
        repo.execute(Action.StartExtraWorkout(1000))
        val extra = repo.state.value.activeWorkout!!.id
        repo.execute(Action.AddWorkoutExercise(extra, "x-row", "row-stack"))
        repo.execute(Action.SaveSet(extra, "x-row", "x-row-1", 30.0, 12, 2.0, false, 2000))
        repo.execute(Action.FinishWorkout(3000))
        repo.execute(Action.StartWorkout("push", 100_000))
        val push = repo.state.value.activeWorkout!!
        repo.log(push, push.exercises[0], id = "p-1", kg = 24.0, reps = 9, now = 101_000)
        repo.execute(Action.FinishWorkout(110_000, countForWeek = true))
        repo.execute(Action.StartWorkout("pull", 200_000))
        val pull = repo.state.value.activeWorkout!!
        repo.execute(Action.AddWorkoutExercise(pull.id, "a-curl", "curl-hammer"))
        repo.execute(Action.LogStage(pull.id, pull.exercises[0].id, "a-rp", SetMethod.REST_PAUSE, "a-r1", 40.0, 10,
            null, 201_000))
        val current = repo.state.value
        val v4 = v4Json(current)
        val old = SnapshotCodec.decode(v4)
        assertEquals(4, old.schemaVersion)
        assertEquals(current.beforeCatalogTwo().copy(schemaVersion = 4), old)
        val store = MemoryPersistence(v4)
        val migrated = repository(store, clock = { 900_000 }).state.value
        assertEquals(SnapshotCodec.SCHEMA_VERSION, migrated.schemaVersion)
        assertEquals(old.revision + 1, migrated.revision)
        assertEquals(1, store.writes)
        assertEquals("Every workout, set, stage, plan edit, credit and timer is kept exactly; only the library grows",
            old.copy(schemaVersion = SnapshotCodec.SCHEMA_VERSION, revision = migrated.revision,
                unbackedUpSince = migrated.unbackedUpSince, catalogRevision = ExerciseCatalog.REVISION,
                exercises = old.exercises + ExerciseCatalog.revisionTwo), migrated)
        assertTrue(migrated.routines.isEmpty() && migrated.placements.isEmpty() && migrated.scheduleRemovals.isEmpty())
        assertEquals("The week is exactly the original routines", listOf("push", "pull", "lower-a", "upper", "lower-b"),
            migrated.scheduledRoutines(Phase.BASELINE).map { it.id })
        val reopened = repository(store, clock = { 950_000 }).state.value
        assertEquals(migrated, reopened)
        assertEquals("Opening again never migrates twice", 1, store.writes)
        val preview = repo.previewBackup(v4)
        assertTrue(preview.dataUpdated)
        assertTrue("Catalog revision two's exercises are added to the library", preview.contentUpdated)
        assertFalse("A version-four file already has its start dates", preview.startDatesAdded)
        assertFalse(preview.futurePlanUpdated)
        assertEquals(SnapshotCodec.SCHEMA_VERSION, preview.state.schemaVersion)
        assertEquals(old.workouts, preview.state.workouts)
        assertEquals(old.planAdditions, preview.state.planAdditions)
        assertEquals(old.planRemovals, preview.state.planRemovals)
    }

    @Test fun aCopyTakenOffMidWorkoutKeepsItsOpenSetFinishesOffTheChecklistAndStaysRetiredThroughARestore() = runTest {
        val repo = repository(clock = { at(3, 20) })
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        repo.execute(Action.SaveRoutine("r-arms", "Arms", listOf(face, hammer)))
        repo.execute(Action.ScheduleRoutine("p-mon", Phase.BASELINE, 1, "r-arms"))
        val additions = repo.state.value.planAdditions.filter { it.templateId == "p-mon" }
        repo.execute(Action.StartWorkout("p-mon", at(0, 18), date(0)))
        val workout = repo.state.value.activeWorkout!!
        val (pulls, curls) = workout.exercises
        assertEquals(additions.map { it.id }, workout.exercises.map { it.slotId })
        repo.log(workout, pulls, id = "w-1", kg = 15.0, reps = 15, now = at(0, 18, 5))
        repo.execute(Action.LogStage(workout.id, curls.id, "g-drop", SetMethod.DROP, "d-1", 14.0, 10, 1.0, at(0, 18, 10)))
        repo.execute(Action.LogStage(workout.id, curls.id, "g-drop", SetMethod.DROP, "d-2", 10.0, 8, null, at(0, 18, 11)))
        val logged = repo.state.value.activeWorkout!!

        // Taken off the week while its workout is open with a drop set in progress.
        repo.execute(Action.RemoveFromWeeklyPlan(Phase.BASELINE, "p-mon"))
        val retired = repo.state.value
        assertEquals(RoutinePlacement("p-mon", Phase.BASELINE, 1, "Arms", "r-arms", removedFromPlan = true),
            retired.placements.single())
        assertTrue("Its future exercises are gone", retired.planAdditions.none { it.templateId == "p-mon" })
        assertEquals("The open workout, its stages and its open drop set are untouched", logged, retired.activeWorkout)
        assertNotNull(retired.activeWorkout!!.exercises[1].openGroup())
        assertTrue(retired.scheduledRoutines(Phase.BASELINE).none { it.id == "p-mon" })

        // Its library routine goes too: only the link is dropped.
        repo.execute(Action.DeleteRoutine("r-arms"))
        assertEquals(RoutinePlacement("p-mon", Phase.BASELINE, 1, "Arms", null, removedFromPlan = true),
            repo.state.value.placements.single())
        repo.execute(Action.FinishWorkout(at(0, 19), closeOpenGroups = true))
        val finished = repo.state.value
        val done = finished.workouts.single()
        assertEquals(at(0, 19), done.finishedAt)
        assertFalse(done.countedForWeek)
        assertNull(done.exercises[1].openGroup())
        assertEquals(listOf("d-1", "d-2"), done.exercises[1].sets.map { it.id })
        assertEquals(listOf("w-1"), done.exercises[0].sets.map { it.id })
        val week = requireNotNull(finished.weekChecklist(monday, addis))
        assertEquals("Real training of a retired copy is listed outside the checklist", listOf(done.id),
            week.other.map { it.id })
        assertTrue(week.routines.none { it.template.id == "p-mon" })
        assertTrue(week.extras.isEmpty())

        // A backup of it restores into a fresh phone exactly, and nothing brings the copy back.
        val document = repo.exportBackup()
        assertEquals(finished, SnapshotCodec.decode(document.json))
        val store = MemoryPersistence()
        val destination = repository(store, clock = { at(3, 21) })
        val safety = destination.exportBackup()
        destination.execute(Action.MarkBackup(safety.revision, at(3, 21)))
        destination.restoreBackup(destination.previewBackup(document.json), safety.revision)
        val restored = destination.state.value
        assertEquals(finished.copy(revision = safety.revision + 1, restTimer = null, lastBackupAt = at(3, 21),
            lastBackupRevision = safety.revision, unbackedUpSince = at(3, 21)), restored)
        assertEquals(restored, repository(store).state.value)
        assertEquals(listOf(done.id), restored.weekChecklist(monday, addis)!!.other.map { it.id })
        val writes = store.writes
        destination.execute(Action.ScheduleRoutine("p-mon", Phase.BASELINE, 1, "r-arms"))
        assertEquals("Repeating the original schedule request changes nothing", restored, destination.state.value)
        failure("not available in your selected phase") {
            destination.execute(Action.StartWorkout("p-mon", at(3, 22), date(3)))
        }
        failure("no longer on your weekly plan") {
            destination.execute(Action.SavePlacement("p-mon", "Arms", listOf(face)))
        }
        failure("no longer on your weekly plan") { destination.execute(Action.UpdatePlacementFromRoutine("p-mon")) }
        failure("no longer on your weekly plan") { destination.execute(Action.MovePlacement("p-mon", 5)) }
        failure("not available in the selected phase") {
            destination.execute(Action.SavePlanAddition("late", Phase.BASELINE, "p-mon", "row-stack", 3, 8, 12, 90, false))
        }
        failure("isn't part of that phase") { destination.execute(Action.RestoreToWeeklyPlan(Phase.BASELINE, "p-mon")) }
        destination.execute(Action.RemoveFromWeeklyPlan(Phase.BASELINE, "p-mon"))
        assertEquals(restored, destination.state.value)
        assertEquals("Refused and repeated requests never write", writes, store.writes)
        // Its ID stays its own: a new schedule can't take it over even for another routine.
        destination.execute(Action.SaveRoutine("r-legs", "Legs", listOf(calf)))
        failure("schedule ID is already used") {
            destination.execute(Action.ScheduleRoutine("p-mon", Phase.BASELINE, 5, "r-legs"))
        }
        failure("schedule ID is already used") {
            destination.execute(Action.ScheduleRoutine("p-mon", Phase.INTRO_ONE, 1, "r-legs"))
        }
        assertTrue(destination.state.value.placements.single().removedFromPlan)
    }
}
