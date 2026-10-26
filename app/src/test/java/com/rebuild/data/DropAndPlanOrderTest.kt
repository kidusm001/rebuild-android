package com.rebuild.data

import com.rebuild.domain.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DropAndPlanOrderTest {
    private fun RebuildRepository.active() = requireNotNull(state.value.activeWorkout)
    private fun RebuildRepository.ex(index: Int = 0) = active().exercises[index]

    private suspend fun RebuildRepository.drop(group: String, id: String, kg: Double, reps: Int, now: Long) =
        execute(Action.LogDrop(active().id, ex().id, group, id, kg, reps, null, now))

    @Test fun aDropJoinsTheSetBeforeItAndStaysOneWorkingSet() = runTest {
        val repo = repository()
        repo.start()
        repo.log(id = "warm", kg = 10.0, warmup = true, now = 1500)
        repo.log(id = "s1", kg = 30.0, reps = 10, now = 2000)
        repo.log(id = "s2", kg = 30.0, reps = 9, now = 3000)
        assertEquals(2, repo.ex().workingCount())

        repo.drop("g", "d1", 20.0, 8, now = 3100)
        val first = repo.ex()
        assertEquals("the drop is part of set 2, not a third set", 2, first.workingCount())
        assertEquals(listOf("warm", "s1", "s2", "d1"), first.sets.map { it.id })
        val group = first.workEntries().last() as WorkEntry.Group
        assertEquals(SetMethod.DROP, group.group.method)
        assertEquals(listOf("s2", "d1"), group.stages.map { it.id })
        assertFalse("the set is finished, not left open", group.open)
        assertEquals("rest starts again from the drop", 3100L + first.restSeconds * 1000L,
            repo.state.value.restTimer!!.deadline)

        repo.drop("g2", "d2", 10.0, 6, now = 3200)
        val second = repo.ex()
        assertEquals(2, second.workingCount())
        assertEquals("a second drop joins the same drop set", 1, second.setGroups.size)
        assertEquals(listOf("s2", "d1", "d2"), (second.workEntries().last() as WorkEntry.Group).stages.map { it.id })

        // A repeated request changes nothing; different values under the same ID are rejected.
        val after = repo.state.value
        repo.drop("g2", "d2", 10.0, 6, now = 3300)
        assertEquals(after, repo.state.value)
        failure("different values") { repo.drop("g2", "d2", 10.0, 5, now = 3300) }
    }

    @Test fun aDropNeedsAWorkingSetBeforeIt() = runTest {
        val repo = repository()
        repo.start()
        failure("working set first") { repo.drop("g", "d1", 20.0, 8, now = 2000) }
        repo.log(id = "warm", kg = 10.0, warmup = true, now = 2100)
        failure("working set first") { repo.drop("g", "d1", 20.0, 8, now = 2200) }
    }

    /** A drop set logged as a set of its own, the way the old "Drop set" type did: stages, then Finish set. */
    private suspend fun RebuildRepository.oldDropSet(group: String, vararg stages: Pair<Double, Int>, now: Long,
        method: SetMethod = SetMethod.DROP) {
        stages.forEachIndexed { i, (kg, reps) ->
            execute(Action.LogStage(active().id, ex().id, group, method, "$group-$i", kg, reps, null, now + i * 10))
        }
        execute(Action.FinishGroup(active().id, ex().id, group, now + stages.size * 10))
    }

    @Test fun aDropSetLoggedOnItsOwnCanBecomeDropsOfTheSetBeforeIt() = runTest {
        val repo = repository()
        repo.start()
        repo.log(id = "s1", kg = 30.0, reps = 10, now = 2000)
        repo.log(id = "s2", kg = 30.0, reps = 9, now = 3000)
        repo.oldDropSet("old", 20.0 to 8, 10.0 to 6, now = 3100)
        assertEquals("the old way, the drops counted as a third set", 3, repo.ex().workingCount())
        val rest = repo.state.value.restTimer

        repo.execute(Action.JoinDropsToSet(repo.active().id, repo.ex().id, "old"))
        val joined = repo.ex()
        assertEquals("now they are part of set 2", 2, joined.workingCount())
        assertEquals("every value, the order and the ids are kept", listOf("s1", "s2", "old-0", "old-1"), joined.sets.map { it.id })
        val set2 = joined.workEntries().last() as WorkEntry.Group
        assertEquals(listOf("s2", "old-0", "old-1"), set2.stages.map { it.id })
        assertEquals("set 2 is still read as 30 kg × 9", "s2", set2.mainSet.id)
        assertEquals(listOf(20.0, 10.0), set2.followUps.map { it.kg })
        assertEquals("a correction starts no timer", rest, repo.state.value.restTimer)
        SnapshotCodec.decode(SnapshotCodec.encode(repo.state.value))

        // A second old drop set joins the set that now has drops; the empty group is gone.
        repo.oldDropSet("older", 5.0 to 5, now = 4000)
        repo.execute(Action.JoinDropsToSet(repo.active().id, repo.ex().id, "older"))
        val twice = repo.ex()
        assertEquals(2, twice.workingCount())
        assertEquals(listOf("old"), twice.setGroups.map { it.id })
        assertEquals(listOf("s2", "old-0", "old-1", "older-0"), (twice.workEntries().last() as WorkEntry.Group).stages.map { it.id })
        SnapshotCodec.decode(SnapshotCodec.encode(repo.state.value))
    }

    @Test fun joiningIsRefusedWhenItWouldChangeWhatWasLifted() = runTest {
        val repo = repository()
        repo.start()
        // The first working set has nothing before it to join.
        repo.oldDropSet("first", 20.0 to 8, now = 2000)
        failure("no set before") { repo.execute(Action.JoinDropsToSet(repo.active().id, repo.ex().id, "first")) }
        // A warm-up between them keeps the stages apart.
        repo.log(id = "warm", kg = 10.0, warmup = true, now = 2500)
        repo.oldDropSet("after-warm-up", 20.0 to 8, now = 2600)
        failure("warm-up") { repo.execute(Action.JoinDropsToSet(repo.active().id, repo.ex().id, "after-warm-up")) }
        // A rest-pause set is a set of its own, and takes no drops either.
        repo.log(id = "s", kg = 30.0, reps = 8, now = 3000)
        repo.oldDropSet("rp", 30.0 to 8, 30.0 to 3, now = 3100, method = SetMethod.REST_PAUSE)
        failure("Only a drop set") { repo.execute(Action.JoinDropsToSet(repo.active().id, repo.ex().id, "rp")) }
        repo.oldDropSet("after-rp", 20.0 to 6, now = 3200)
        failure("rest-pause") { repo.execute(Action.JoinDropsToSet(repo.active().id, repo.ex().id, "after-rp")) }
        // An open drop set is finished first.
        repo.log(id = "t", kg = 30.0, reps = 8, now = 3300)
        repo.execute(Action.LogStage(repo.active().id, repo.ex().id, "open", SetMethod.DROP, "open-0", 20.0, 8, null, 3400))
        failure("Finish this set") { repo.execute(Action.JoinDropsToSet(repo.active().id, repo.ex().id, "open")) }
    }

    @Test fun aFinishedWorkoutCanBeCorrectedToo() = runTest {
        val repo = repository()
        repo.start()
        repo.log(id = "s1", kg = 30.0, reps = 10, now = 2000)
        repo.oldDropSet("old", 20.0 to 8, now = 2100)
        val workout = repo.active().id
        repo.execute(Action.FinishWorkout(5000))
        val exercise = repo.state.value.workouts.single { it.id == workout }.exercises.first().id
        repo.execute(Action.JoinDropsToSet(workout, exercise, "old"))
        assertEquals(1, repo.state.value.workouts.single { it.id == workout }.exercises.first().workingCount())
    }

    @Test fun movingAPlannedExerciseReordersFutureWorkoutsOnly() = runTest {
        val repo = repository()
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        val push = repo.state.value.scheduledRoutines(Phase.BASELINE).first { it.id == "push" }
        val before = repo.state.value.plannedExercises(push, Phase.BASELINE).map { it.slotId }
        repo.execute(Action.MovePlannedExercise(Phase.BASELINE, "push", before.last(), 0))
        val after = repo.state.value.plannedExercises(push, Phase.BASELINE).map { it.slotId }
        assertEquals(listOf(before.last()) + before.dropLast(1), after)
        assertTrue("other phases keep their order", repo.state.value.planOrders.all { it.phase == Phase.BASELINE })

        repo.execute(Action.StartWorkout("push", 1000))
        assertEquals(after, repo.active().exercises.map { it.slotId })

        // The order survives a backup round trip.
        val restored = SnapshotCodec.decode(SnapshotCodec.encode(repo.state.value))
        assertEquals(after, restored.plannedExercises(push, Phase.BASELINE).map { it.slotId })
    }

    @Test fun addingAnExerciseCanSetItsWorkingSets() = runTest {
        val repo = repository()
        repo.start()
        repo.execute(Action.AddWorkoutExercise(repo.active().id, "x-row", "row-db", sets = 4))
        val added = repo.active().exercises.first { it.id == "x-row" }
        assertEquals(4, added.targetSets)
        assertTrue(added.optional)
        failure("between 1") {
            repo.execute(Action.AddWorkoutExercise(repo.active().id, "x-face", "face-pull", sets = 0))
        }
    }
}
