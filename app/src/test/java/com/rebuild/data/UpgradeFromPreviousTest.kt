package com.rebuild.data

import com.rebuild.domain.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Data saved by 0.1.10 opens in this version exactly as it was, and nothing is rewritten just by opening it. */
class UpgradeFromPreviousTest {
    @Test fun aPreviousVersionSnapshotOpensUnchanged() = runTest {
        val repo = repository()
        repo.execute(Action.EditPlan(Phase.BASELINE, "push-incline", "chest-machine", 3, 123))
        repo.execute(Action.SavePlanAddition("add-face", Phase.BASELINE, "push", "face-pull", 3, 12, 20, 75, false))
        val workout = repo.start()
        repo.log(id = "warm", kg = 10.0, warmup = true, now = 1500)
        repo.log(id = "s1", kg = 30.0, reps = 10, now = 2000)
        repo.execute(Action.LogStage(workout.id, workout.exercises[0].id, "g", SetMethod.DROP, "d1", 20.0, 8, null, 3000))
        repo.execute(Action.LogStage(workout.id, workout.exercises[0].id, "g", SetMethod.DROP, "d2", 10.0, 6, null, 3100))
        repo.execute(Action.FinishGroup(workout.id, workout.exercises[0].id, "g", 3200))
        repo.execute(Action.AddWorkoutExercise(workout.id, "x-row", "row-db"))
        repo.execute(Action.FinishWorkout(5000))
        val saved = repo.state.value
        assertTrue(saved.planOrders.isEmpty())

        // 0.1.10 wrote exactly this snapshot: version six, without plan orders, pounds or body records.
        val previous = asVersionSix(Json.parseToJsonElement(SnapshotCodec.encode(saved)).jsonObject).toString()

        val store = MemoryPersistence(previous)
        val opened = repository(store)
        assertEquals("every record opens exactly as it was", saved, opened.state.value)
        assertEquals("only the newer format is recorded, once", 1, store.writes)
        assertEquals("a format upgrade doesn't ask for a new backup", saved.revision, opened.state.value.revision)
        assertEquals(saved, SnapshotCodec.decode(requireNotNull(store.snapshot)))
        // Opening again writes nothing more.
        assertEquals(0, MemoryPersistence(store.snapshot).also { repository(it) }.writes)
    }

    private val versionSevenState = setOf("planOrders", "bodyweights", "measurements", "measurementSites", "liftingUnit")

    private fun withoutKey(element: JsonElement, key: String): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.filterKeys { it != key }.mapValues { withoutKey(it.value, key) })
        is JsonArray -> JsonArray(element.map { withoutKey(it, key) })
        else -> element
    }

    private fun asVersionSix(document: JsonObject): JsonObject {
        val state = withoutKey(JsonObject(document["state"]!!.jsonObject.filterKeys { it !in versionSevenState }), "lb")
            .jsonObject
        return JsonObject(document + ("version" to JsonPrimitive(6)) +
            ("state" to JsonObject(state + ("schemaVersion" to JsonPrimitive(6)))))
    }
}
