package com.rebuild.data

import com.rebuild.domain.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class BodyAndPoundsTest {
    private fun RebuildRepository.active() = requireNotNull(state.value.activeWorkout)

    @Test fun aSetInPoundsIsKeptExactlyAndNeverConverted() = runTest {
        val repo = repository()
        val workout = repo.start()
        val first = workout.exercises.first()
        repo.execute(Action.SaveSet(workout.id, first.id, "lb1", 22.5, 10, 2.0, false, 2000, LoadUnit.LB))
        val set = repo.active().exercises.first().sets.single()
        assertEquals(LoadUnit.LB, set.loadUnit)
        assertEquals(22.5, set.lb!!, 0.0)
        assertNull("nothing is converted into kilograms", set.kg)
        assertEquals(22.5, set.amount!!, 0.0)
        assertEquals(22.5, set.loadValue!!, 0.0)

        val restored = SnapshotCodec.decode(SnapshotCodec.encode(repo.state.value))
        assertEquals(set, restored.activeWorkout!!.exercises.first().sets.single())

        failure("22,000 lb") {
            repo.execute(Action.SaveSet(workout.id, first.id, "lb2", 30000.0, 10, null, false, 3000, LoadUnit.LB))
        }
    }

    @Test fun lastTimeOnlyComparesSetsInTheSameUnit() = runTest {
        val repo = repository()
        val workout = repo.start()
        val first = workout.exercises.first()
        repo.execute(Action.SaveSet(workout.id, first.id, "kg1", 40.0, 10, 2.0, false, 2000))
        repo.execute(Action.FinishWorkout(5000))
        repo.execute(Action.StartWorkout("push", 10_000))
        val second = repo.active()
        assertNull("a kilogram session is never last time for pounds",
            repo.state.value.previousComparable(first.exercise, second.id, LoadUnit.LB))
        assertNotNull(repo.state.value.previousComparable(first.exercise, second.id, LoadUnit.KG))
    }

    @Test fun weighInsAreStoredAsEnteredCorrectedInPlaceAndDeleted() = runTest {
        val repo = repository()
        repo.execute(Action.LogBodyweight("w1", 1_000_000, 82.4, MassUnit.KG, note = "after waking"))
        repo.execute(Action.LogBodyweight("w2", 2_000_000, 181.0, MassUnit.LB))
        assertEquals(listOf("w1", "w2"), repo.state.value.bodyweights.map { it.id })
        assertEquals(MassUnit.LB, repo.state.value.bodyweights.last().unit)

        repo.execute(Action.LogBodyweight("w1", 1_000_000, 82.2, MassUnit.KG))
        assertEquals(82.2, repo.state.value.bodyweights.first { it.id == "w1" }.value, 0.0)
        assertEquals(2, repo.state.value.bodyweights.size)

        failure("between 20 and 400 kg") { repo.execute(Action.LogBodyweight("w3", 3_000_000, 4.0, MassUnit.KG)) }
        repo.execute(Action.DeleteBodyweight("w2"))
        assertEquals(listOf("w1"), repo.state.value.bodyweights.map { it.id })
        assertEquals(repo.state.value, SnapshotCodec.decode(SnapshotCodec.encode(repo.state.value)))
    }

    @Test fun aMeasuringSessionSavesOnlyItsSitesAndSitesCanBeChosen() = runTest {
        val repo = repository()
        assertEquals(listOf(BodySite.WAIST), repo.state.value.measurementSites)
        val date = localDateOf(1_000_000).toString()
        repo.execute(Action.SaveMeasurements(listOf(
            BodyMeasurement("m1", 1_000_000, date, BodySite.WAIST, 91.5, LengthUnit.CM),
            BodyMeasurement("m2", 1_000_000, date, BodySite.NECK, 15.25, LengthUnit.IN)
        )))
        assertEquals(listOf(BodySite.WAIST, BodySite.NECK), repo.state.value.measurements.map { it.site })
        assertEquals(15.25, repo.state.value.measurements.last().value, 0.0)
        failure("waist between 10 and 300 cm") {
            repo.execute(Action.SaveMeasurements(listOf(BodyMeasurement("m3", 2_000_000, date, BodySite.WAIST, 3.0,
                LengthUnit.CM))))
        }
        repo.execute(Action.SetMeasurementSites(listOf(BodySite.WAIST, BodySite.CHEST)))
        assertEquals(listOf(BodySite.WAIST, BodySite.CHEST), repo.state.value.measurementSites)
        failure("at least one site") { repo.execute(Action.SetMeasurementSites(emptyList())) }
        assertEquals("turning a site off never removes what was measured", 2, repo.state.value.measurements.size)
    }

    @Test fun switchingTheLiftingUnitChangesFutureEntriesOnlyAndKeepsPlates() = runTest {
        val repo = repository()
        val workout = repo.start()
        val first = workout.exercises.first()
        repo.execute(Action.SaveSet(workout.id, first.id, "kg1", 40.0, 10, 2.0, false, 2000))
        val cable = repo.state.value.exercises.first { it.supportsPlates }
        repo.execute(Action.SetExerciseLoadUnit(cable.id, LoadUnit.PLATES))

        repo.execute(Action.SetLiftingUnit(MassUnit.LB))
        val state = repo.state.value
        assertEquals(MassUnit.LB, state.liftingUnit)
        assertEquals(LoadUnit.PLATES, state.exercises.first { it.id == cable.id }.preferredLoadUnit)
        assertEquals(LoadUnit.LB, state.exercises.first { it.id == first.exercise.id }.preferredLoadUnit)
        assertNull(state.exercises.first { it.loading == LoadingType.BODYWEIGHT }.preferredLoadUnit.takeIf { it == LoadUnit.LB })
        assertEquals("a logged set keeps its kilograms", 40.0, state.activeWorkout!!.exercises.first().sets.single().kg!!, 0.0)
    }

    @Test fun anOlderBackupCannotCarryPoundsOrBodyRecords() = runTest {
        val repo = repository()
        repo.execute(Action.LogBodyweight("w1", 1_000_000, 82.4, MassUnit.KG))
        val document = Json.parseToJsonElement(SnapshotCodec.encode(repo.state.value)).jsonObject
        val state = document["state"]!!.jsonObject
        val claimingSix = JsonObject(document + ("version" to JsonPrimitive(6)) +
            ("state" to JsonObject(state + ("schemaVersion" to JsonPrimitive(6))))).toString()
        rejected { SnapshotCodec.decode(claimingSix) }
    }
}
