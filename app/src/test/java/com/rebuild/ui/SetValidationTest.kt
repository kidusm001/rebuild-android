package com.rebuild.ui

import com.rebuild.domain.Exercise
import com.rebuild.domain.LoadingType
import com.rebuild.domain.LoadUnit
import com.rebuild.domain.Equipment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetValidationTest {
    private val external = Exercise("bench", "Bench")
    private fun field(kg: String, reps: String, rir: String): SetField =
        (validateSet(external, kg, reps, rir) as SetValidation.Invalid).field

    @Test fun missingRepsIdentifiesReps() { assertEquals(SetField.REPS, field("40", "", "")) }
    @Test fun excessiveRepsIdentifiesReps() { assertEquals(SetField.REPS, field("40", "1001", "")) }
    @Test fun missingWeightIdentifiesWeight() { assertEquals(SetField.KG, field("", "8", "")) }
    @Test fun nonfiniteWeightIsRejected() { assertEquals(SetField.KG, field("NaN", "8", "")) }
    @Test fun invalidRirIdentifiesRir() { assertEquals(SetField.RIR, field("40", "8", "11")) }
    @Test fun ordinaryDecimalAndOptionalRirAreAccepted() {
        assertEquals(SetValidation.Valid(SetInput(42.5, 8, null)), validateSet(external, "42,5", "8", ""))
    }
    @Test fun bodyweightDoesNotRequireLoad() {
        assertTrue(validateSet(external.copy(loading = LoadingType.BODYWEIGHT), "", "8", "2") is SetValidation.Valid)
    }
    @Test fun assistanceAcceptsZeroButRequiresEntry() {
        val assisted = external.copy(loading = LoadingType.ASSISTANCE)
        assertTrue(validateSet(assisted, "0", "8", "") is SetValidation.Valid)
        assertEquals(SetField.KG, (validateSet(assisted, "", "8", "") as SetValidation.Invalid).field)
    }
    @Test fun platesAreWholeCountsAndNeverKilograms() {
        val cable = external.copy(equipment = Equipment.CABLE)
        assertEquals(SetValidation.Valid(SetInput(null, 12, null, 5)),
            validateSet(cable, "5", "12", "", LoadUnit.PLATES))
        listOf("", "0", "-1", "5.5", "NaN", "Infinity", "1001").forEach {
            assertTrue("$it must be rejected", validateSet(cable, it, "12", "", LoadUnit.PLATES) is SetValidation.Invalid)
        }
        assertTrue(validateSet(cable, "1000", "12", "", LoadUnit.PLATES) is SetValidation.Valid)
    }
    @Test fun platesRequireExplicitStackCapability() {
        listOf(external, external.copy(perDumbbell = true, equipment = Equipment.DUMBBELL),
            external.copy(loading = LoadingType.BODYWEIGHT),
            external.copy(loading = LoadingType.ASSISTANCE, equipment = Equipment.STACK_MACHINE)).forEach {
            assertTrue(validateSet(it, "5", "12", "", LoadUnit.PLATES) is SetValidation.Invalid)
        }
    }
}
