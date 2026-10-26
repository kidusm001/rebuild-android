package com.rebuild.data

import com.rebuild.domain.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class GroupLoggingTest {
    private fun RebuildRepository.active() = requireNotNull(state.value.activeWorkout)
    private fun RebuildRepository.ex(index: Int = 0) = active().exercises[index]

    private suspend fun RebuildRepository.stage(
        group: String,
        id: String,
        kg: Double?,
        reps: Int,
        now: Long,
        method: SetMethod = SetMethod.DROP,
        exercise: Int = 0,
        unit: LoadUnit = LoadUnit.KG,
        plates: Int? = null
    ) = execute(Action.LogStage(active().id, ex(exercise).id, group, method, id, kg, reps, null, now, unit, plates))

    @Test fun dropSetStagesAreSavedAsLoggedAndCountAsOneSetOnlyOnceFinished() = runTest {
        val store = MemoryPersistence()
        val repo = repository(store)
        val workout = repo.start()
        repo.log(id = "warm", kg = 10.0, warmup = true, now = 1500)
        repo.log(id = "normal-1", kg = 22.0, reps = 9, now = 2000)
        assertNotNull(repo.state.value.restTimer)

        repo.stage("drop", "d1", 20.0, 10, now = 3000)
        // Each stage is committed as it is logged, not held in a form until the set ends.
        val reopened = repository(MemoryPersistence(store.snapshot)).state.value.activeWorkout!!.exercises.first()
        assertEquals(listOf("d1"), reopened.stages("drop").map { it.id })
        assertEquals(SetMethod.DROP, reopened.openGroup()?.method)
        assertNull("starting the next set ends the running rest", repo.state.value.restTimer)

        repo.stage("drop", "d2", 15.0, 8, now = 3100)
        repo.stage("drop", "d3", 10.0, 6, now = 3200)
        val open = repo.ex()
        assertEquals(listOf("warm", "normal-1", "d1", "d2", "d3"), open.sets.map { it.id })
        assertEquals(listOf(20.0, 15.0, 10.0), open.stages("drop").map { it.kg })
        assertEquals(listOf(10, 8, 6), open.stages("drop").map { it.reps })
        assertEquals(listOf(3000L, 3100L, 3200L), open.stages("drop").map { it.completedAt })
        assertTrue(open.stages("drop").none { it.warmup })
        assertNull("no rest starts between drops", repo.state.value.restTimer)
        assertEquals("an open set is in progress, not complete", 1, open.workingCount())

        repo.execute(Action.FinishGroup(workout.id, open.id, "drop", 4000))
        val finished = repo.state.value
        val exercise = finished.activeWorkout!!.exercises.first()
        assertEquals(2, exercise.workingCount())
        assertEquals(4000L, exercise.setGroups.single().finishedAt)
        assertEquals(4000L + exercise.restSeconds * 1000L, finished.restTimer!!.deadline)
        assertFalse(finished.restTimer!!.shortPause)
        val entries = exercise.workEntries()
        assertEquals(2, entries.size)
        assertEquals(listOf("d1", "d2", "d3"), (entries[1] as WorkEntry.Group).stages.map { it.id })

        // Finish set again changes nothing: same rest, same finish time, no new revision.
        repo.execute(Action.FinishGroup(workout.id, exercise.id, "drop", 9000))
        assertEquals(finished, repo.state.value)
    }

    @Test fun aRepeatedStageRequestChangesNothingAndConflictingReuseIsRejected() = runTest {
        val repo = repository()
        val workout = repo.start()
        val first = workout.exercises[0]
        repo.stage("g", "s1", 20.0, 10, now = 3000)
        repo.execute(Action.FinishGroup(workout.id, first.id, "g", 4000))
        val after = repo.state.value

        // A duplicate delivery after the set finished must not append, edit, or cancel the newer rest.
        repo.stage("g", "s1", 20.0, 10, now = 4500)
        assertEquals(after, repo.state.value)
        repo.execute(Action.AddStage(workout.id, first.id, "g", "s1", 20.0, 10, null, 4600))
        assertEquals(after, repo.state.value)

        failure("different values") { repo.stage("g", "s1", 20.0, 9, now = 5000) }
        failure("already used") { repo.stage("other", "s1", 20.0, 10, now = 5000) }
        failure("already used") { repo.stage("g", "t1", 20.0, 10, now = 5000, exercise = 1) }
        failure("already finished") { repo.stage("g", "s2", 15.0, 8, now = 5000) }
        assertEquals(after, repo.state.value)

        repo.log(id = "n1", kg = 22.0, now = 6000)
        failure("already used") { repo.stage("g2", "n1", 22.0, 10, now = 6100) }
        failure("different values") { repo.execute(Action.AddStage(workout.id, first.id, "g", "n1", 22.0, 10, null, 6200)) }
        assertEquals(listOf("s1", "n1"), repo.ex().sets.map { it.id })
    }

    @Test fun anOpenSetGuardsNewSetsAndUnitSwitchesButKeepsStagesAndCorrections() = runTest {
        val repo = repository()
        val workout = repo.start(template = "pull")
        val cable = workout.exercises[0]
        repo.log(id = "n1", kg = 40.0, now = 2000)
        repo.stage("g", "s1", 35.0, 10, now = 3000)
        val before = repo.state.value

        failure("Finish the drop set on") { repo.log(exercise = workout.exercises[1], id = "x", kg = 20.0, now = 3100) }
        failure("Finish the drop set") { repo.log(id = "w", kg = 10.0, warmup = true, now = 3100) }
        failure("Finish the drop set") { repo.stage("g2", "t1", 20.0, 10, now = 3100, exercise = 1) }
        failure("Finish the drop") { repo.execute(Action.SetLoadUnit(workout.id, cable.id, LoadUnit.PLATES)) }
        failure("in progress") { repo.execute(Action.FinishWorkout(3200)) }
        assertEquals(before, repo.state.value)

        repo.execute(Action.SaveSet(workout.id, cable.id, "n1", 42.5, 10, null, false, 9999))
        assertEquals(42.5, repo.ex().sets.first().kg)
        assertEquals(2000L, repo.ex().sets.first().completedAt)
        assertNull("corrections never arm the rest", repo.state.value.restTimer)
        assertEquals(listOf("s1"), repo.ex().stages("g").map { it.id })
    }

    @Test fun finishingTheWorkoutClosesAnOpenSetOnlyWhenConfirmedAndKeepsEveryStage() = runTest {
        val repo = repository()
        repo.start()
        repo.stage("g", "s1", 20.0, 10, now = 3000)
        repo.stage("g", "s2", 15.0, 8, now = 3100)
        failure("in progress") { repo.execute(Action.FinishWorkout(5000)) }
        assertNull(repo.active().finishedAt)

        repo.execute(Action.FinishWorkout(5000, closeOpenGroups = true))
        val workout = repo.state.value.workouts.single()
        assertEquals(5000L, workout.finishedAt)
        assertEquals(5000L, workout.exercises.first().setGroups.single().finishedAt)
        assertEquals(listOf("s1", "s2"), workout.exercises.first().sets.map { it.id })
        assertEquals(1, workout.workingSetCount())
        assertNull(repo.state.value.restTimer)
        assertNull(repo.state.value.activeWorkoutId)
    }

    @Test fun aSingleStageSetIsTruthfulAndTheStageLimitIsExplicitNeverTruncating() = runTest {
        val repo = repository()
        val workout = repo.start()
        val first = workout.exercises[0]
        repo.stage("one", "a1", 20.0, 10, now = 3000)
        repo.execute(Action.FinishGroup(workout.id, first.id, "one", 3100))
        assertEquals(1, repo.ex().workingCount())
        assertEquals(1, repo.ex().stages("one").size)

        (1..MAX_GROUP_STAGES).forEach { repo.stage("long", "l$it", 30.0 - it, 5, now = 4000L + it) }
        failure("at most 20") { repo.stage("long", "l21", 5.0, 5, now = 5000) }
        failure("at most 20") { repo.execute(Action.AddStage(workout.id, first.id, "long", "l21", 5.0, 5, null, 5000)) }
        assertEquals((1..20).map { "l$it" }, repo.ex().stages("long").map { it.id })
    }

    @Test fun stageCorrectionsKeepOtherStagesAndRecountWithoutStartingRest() = runTest {
        val repo = repository()
        val workout = repo.start()
        val first = workout.exercises[0]
        repo.log(id = "n1", kg = 22.0, now = 2000)
        repo.stage("g", "s1", 20.0, 10, now = 3000)
        repo.stage("g", "s2", 15.0, 8, now = 3100)
        repo.stage("g", "s3", 10.0, 6, now = 3200)
        repo.execute(Action.FinishGroup(workout.id, first.id, "g", 3300))
        repo.execute(Action.FinishWorkout(4000))
        fun exercise() = repo.state.value.workouts.single().exercises.first()

        repo.execute(Action.SaveSet(workout.id, first.id, "s2", 16.0, 7, 1.0, false, 9999))
        val edited = exercise().stages("g")
        assertEquals(listOf("s1", "s2", "s3"), edited.map { it.id })
        assertEquals(listOf(3000L, 3100L, 3200L), edited.map { it.completedAt })
        assertEquals(listOf(20.0, 16.0, 10.0), edited.map { it.kg })
        assertEquals(listOf(10, 7, 6), edited.map { it.reps })
        assertEquals(listOf("g", "g", "g"), edited.map { it.groupId })
        failure("warm-up") { repo.execute(Action.SaveSet(workout.id, first.id, "s2", 16.0, 7, null, true, 9999)) }

        repo.execute(Action.AddStage(workout.id, first.id, "g", "s4", 5.0, 5, null, 8000))
        assertEquals(listOf("n1", "s1", "s2", "s3", "s4"), exercise().sets.map { it.id })
        assertEquals(8000L, exercise().sets.last().completedAt)
        repo.execute(Action.DeleteSet(workout.id, first.id, "s2"))
        assertEquals(listOf("s1", "s3", "s4"), exercise().stages("g").map { it.id })
        assertEquals(2, exercise().workingCount())
        repo.execute(Action.SetGroupMethod(workout.id, first.id, "g", SetMethod.REST_PAUSE))
        assertEquals(SetMethod.REST_PAUSE, exercise().setGroups.single().method)
        assertEquals(listOf("s1", "s3", "s4"), exercise().stages("g").map { it.id })

        listOf("s1", "s3").forEach { repo.execute(Action.DeleteSet(workout.id, first.id, it)) }
        assertEquals(1, exercise().setGroups.size)
        repo.execute(Action.DeleteSet(workout.id, first.id, "s4"))
        assertTrue("a set without stages no longer exists", exercise().setGroups.isEmpty())
        assertEquals(listOf("n1"), exercise().sets.map { it.id })
        assertEquals(1, exercise().workingCount())
        assertNull(repo.state.value.restTimer)

        failure("live in the active workout") {
            repo.execute(Action.LogStage(workout.id, first.id, "late", SetMethod.DROP, "late-1", 20.0, 10, null, 9000))
        }
    }

    @Test fun deletingAWholeSetKeepsTheOthersAndRecounts() = runTest {
        val repo = repository()
        val workout = repo.start()
        val first = workout.exercises[0]
        repo.log(id = "n1", kg = 22.0, now = 2000)
        repo.stage("g", "s1", 20.0, 10, now = 3000)
        repo.stage("g", "s2", 15.0, 8, now = 3100)
        repo.execute(Action.FinishGroup(workout.id, first.id, "g", 3300))
        repo.log(id = "n2", kg = 22.0, now = 4000)
        val timer = repo.state.value.restTimer
        assertEquals(3, repo.ex().workingCount())
        repo.execute(Action.DeleteGroup(workout.id, first.id, "g"))
        assertEquals(listOf("n1", "n2"), repo.ex().sets.map { it.id })
        assertEquals(listOf(2000L, 4000L), repo.ex().sets.map { it.completedAt })
        assertEquals(2, repo.ex().workingCount())
        assertEquals("deleting a set does not touch the running rest", timer, repo.state.value.restTimer)
    }

    @Test fun aShortPauseIsExplicitOnlyDuringRestPauseAndSurvivesReopen() = runTest {
        val store = MemoryPersistence()
        val repo = repository(store)
        val workout = repo.start()
        val first = workout.exercises[0]
        failure("rest-pause set is in progress") {
            repo.execute(Action.StartShortPause(workout.id, first.id, 20, 2000))
        }
        repo.stage("d", "d1", 20.0, 10, now = 2100)
        failure("rest-pause") { repo.execute(Action.StartShortPause(workout.id, first.id, 20, 2150)) }
        repo.execute(Action.FinishGroup(workout.id, first.id, "d", 2200))

        repo.stage("rp", "r1", 20.0, 10, now = 3000, method = SetMethod.REST_PAUSE)
        assertNull(repo.state.value.restTimer)
        repo.execute(Action.StartShortPause(workout.id, first.id, 20, 3100))
        val pause = repo.state.value.restTimer!!
        assertTrue(pause.shortPause)
        assertEquals(23100L, pause.deadline)
        assertEquals(pause, repository(MemoryPersistence(store.snapshot)).state.value.restTimer)

        repo.stage("rp", "r2", 20.0, 4, now = 3200, method = SetMethod.REST_PAUSE)
        assertNull(repo.state.value.restTimer)
        repo.execute(Action.StartShortPause(workout.id, first.id, 15, 3300))
        repo.execute(Action.FinishGroup(workout.id, first.id, "rp", 3400))
        val rest = repo.state.value.restTimer!!
        assertFalse(rest.shortPause)
        assertEquals(3400L + first.restSeconds * 1000L, rest.deadline)
        assertEquals("pauses are not stored in history", listOf("r1", "r2"), repo.ex().stages("rp").map { it.id })

        repo.stage("rp2", "q1", 20.0, 8, now = 5000, method = SetMethod.REST_PAUSE)
        repo.execute(Action.StartShortPause(workout.id, first.id, 20, 5100))
        repo.execute(Action.SetGroupMethod(workout.id, first.id, "rp2", SetMethod.DROP))
        assertNull(repo.state.value.restTimer)
        repo.execute(Action.SetGroupMethod(workout.id, first.id, "rp2", SetMethod.REST_PAUSE))
        repo.execute(Action.StartShortPause(workout.id, first.id, 20, 5200))
        repo.execute(Action.DeleteGroup(workout.id, first.id, "rp2"))
        assertNull(repo.state.value.restTimer)
    }

    @Test fun liveStagesKeepTheirSetsUnitPlatesStayWholeAndCorrectionsKeepPerStageTruth() = runTest {
        val repo = repository()
        val workout = repo.start(template = "pull")
        val cable = workout.exercises[0]
        repo.execute(Action.SetLoadUnit(workout.id, cable.id, LoadUnit.PLATES))
        repo.stage("p", "p1", null, 10, now = 3000, unit = LoadUnit.PLATES, plates = 10)
        repo.stage("p", "p2", null, 8, now = 3100, unit = LoadUnit.PLATES, plates = 7)
        failure("unit of the stage before") { repo.stage("p", "p3", 30.0, 6, now = 3200) }
        failure("Finish the drop") { repo.execute(Action.SetLoadUnit(workout.id, cable.id, LoadUnit.KG)) }
        failure("whole number") { repo.stage("p", "p3", null, 6, now = 3200, unit = LoadUnit.PLATES, plates = 0) }
        repo.execute(Action.FinishGroup(workout.id, cable.id, "p", 3300))
        repo.execute(Action.SetLoadUnit(workout.id, cable.id, LoadUnit.KG))
        repo.stage("k", "k1", 40.0, 10, now = 4000)

        val exercise = repo.ex()
        assertEquals(listOf(10, 7), exercise.stages("p").map { it.plates })
        assertEquals(listOf(null, null), exercise.stages("p").map { it.kg })
        assertEquals(listOf(LoadUnit.PLATES, LoadUnit.KG), exercise.workEntries().map { it.unit })

        repo.execute(Action.SaveSet(workout.id, cable.id, "p2", 25.0, 8, null, false, 0, LoadUnit.KG, null))
        val corrected = repo.ex().stages("p")
        assertEquals(LoadUnit.PLATES, corrected[0].loadUnit)
        assertEquals(10, corrected[0].plates)
        assertEquals(LoadUnit.KG, corrected[1].loadUnit)
        assertEquals(25.0, corrected[1].kg)
        assertNull(corrected[1].plates)
    }

    @Test fun normalPrefillsNeverUseStagesAndStagesPairOnlyWithTheSameMethodAndUnit() {
        val exercise = Seed.exercises.single { it.id == "pulldown" }
        fun set(id: String, kg: Double?, reps: Int, group: String? = null, unit: LoadUnit = LoadUnit.KG, plates: Int? = null) =
            LoggedSet(id, kg, reps, null, false, 1, unit, plates, group)
        fun we(sets: List<LoggedSet>, groups: List<SetGroup> = emptyList()) =
            WorkoutExercise("we", "slot", exercise, 4, 3, 4, 8, 12, 2, 120, sets = sets, setGroups = groups)
        val previous = we(listOf(
            set("p1", 40.0, 10),
            set("p2a", 35.0, 10, "pd"), set("p2b", 25.0, 8, "pd"), set("p2c", 15.0, 6, "pd"),
            set("p3", null, 12, unit = LoadUnit.PLATES, plates = 9)
        ), listOf(SetGroup("pd", SetMethod.DROP, 10)))

        val empty = we(emptyList())
        assertEquals(EntrySuggestion(previous.sets[0], PrefillSource.LAST_TIME),
            nextEntrySuggestion(empty, previous, LoadUnit.KG, null))
        val one = we(listOf(set("t1", 41.0, 9)))
        assertEquals("a previous drop set is never offered as a normal set",
            EntrySuggestion(one.sets[0], PrefillSource.THIS_WORKOUT), nextEntrySuggestion(one, previous, LoadUnit.KG, null))
        assertEquals(EntrySuggestion(previous.sets[1], PrefillSource.LAST_TIME),
            nextEntrySuggestion(one, previous, LoadUnit.KG, SetMethod.DROP))
        assertEquals("a different method never pairs with last time's stages",
            EntrySuggestion(one.sets[0], PrefillSource.THIS_WORKOUT),
            nextEntrySuggestion(one, previous, LoadUnit.KG, SetMethod.REST_PAUSE))
        assertEquals("units are never mixed", EntrySuggestion(previous.sets[4], PrefillSource.LAST_TIME),
            nextEntrySuggestion(empty, previous, LoadUnit.PLATES, null))

        val openDrop = we(listOf(set("t1", 41.0, 9), set("t2a", 36.0, 9, "td")), listOf(SetGroup("td", SetMethod.DROP)))
        assertEquals(EntrySuggestion(previous.sets[2], PrefillSource.LAST_TIME),
            nextEntrySuggestion(openDrop, previous, LoadUnit.KG, SetMethod.DROP))
        val longerDrop = we(openDrop.sets + listOf(set("t2b", 26.0, 8, "td"), set("t2c", 16.0, 6, "td")), openDrop.setGroups)
        assertNull("no stage four last time and drops are never guessed",
            nextEntrySuggestion(longerDrop, previous, LoadUnit.KG, SetMethod.DROP))

        val openRestPause = we(listOf(set("t1", 41.0, 9), set("r1", 30.0, 10, "rp")), listOf(SetGroup("rp", SetMethod.REST_PAUSE)))
        assertEquals("a mini-set offers this set's load with reps left to the user",
            EntrySuggestion(openRestPause.sets[1], PrefillSource.THIS_WORKOUT, withReps = false),
            nextEntrySuggestion(openRestPause, previous, LoadUnit.KG, SetMethod.REST_PAUSE))

        // Without groups the rule is unchanged: last time's set n, else today's last set in that unit.
        val plain = we(listOf(set("a", 40.0, 10), set("b", 41.0, 9)))
        val plainToday = we(listOf(set("x", 39.0, 10), set("y", 40.0, 10)))
        assertEquals(EntrySuggestion(plainToday.sets[1], PrefillSource.THIS_WORKOUT),
            nextEntrySuggestion(plainToday, plain, LoadUnit.KG, null))
        assertEquals(EntrySuggestion(plain.sets[1], PrefillSource.LAST_TIME),
            nextEntrySuggestion(we(plainToday.sets.take(1)), plain, LoadUnit.KG, null))
    }
}
