package com.rebuild.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BodyProgressTest {
    private fun weigh(id: String, date: String, value: Double, unit: MassUnit = MassUnit.KG, hour: Int = 7) =
        BodyweightEntry(id, LocalDate.parse(date).toEpochDay() * 86_400_000L + hour * 3_600_000L, date, value, unit)

    @Test fun aWeekAveragesItsDaysSoWeighingTwiceCountsTheDayOnce() {
        val weeks = weightWeeks(listOf(
            weigh("a", "2026-10-19", 82.0), weigh("b", "2026-10-19", 84.0, hour = 20),
            weigh("c", "2026-10-21", 81.0)
        ), MassUnit.KG)
        val week = weeks.single()
        assertEquals(LocalDate.parse("2026-10-19"), week.start)
        assertEquals(2, week.days)
        assertEquals("(83 + 81) / 2, not (82 + 84 + 81) / 3", 82.0, week.average, 1e-9)
    }

    @Test fun mixedUnitsAverageInTheDisplayUnitWithoutChangingEntries() {
        val entries = listOf(weigh("a", "2026-10-19", 180.0, MassUnit.LB), weigh("b", "2026-10-20", 82.0))
        val week = weightWeeks(entries, MassUnit.KG).single()
        assertEquals((180.0 * MassUnit.KG_PER_LB + 82.0) / 2, week.average, 1e-9)
        assertEquals(MassUnit.LB, week.entries.first().unit)
        assertEquals(180.0, week.entries.first().value, 0.0)
    }

    @Test fun theChangeNeedsBothWeeksAndNeverInterpolates() {
        val weeks = weightWeeks(listOf(
            weigh("a", "2026-09-21", 84.0), weigh("b", "2026-10-19", 82.4)
        ), MassUnit.KG)
        val change = weightChange(weeks)!!
        assertEquals(-1.6, change.total, 1e-9)
        assertEquals(-0.4, change.perWeek, 1e-9)
        assertNull("no week four weeks back, no change shown",
            weightChange(weightWeeks(listOf(weigh("c", "2026-09-28", 84.0), weigh("d", "2026-10-19", 82.0)), MassUnit.KG)))
    }

    @Test fun siteProgressReadsSinceLastAndSinceFirstInTheLatestUnit() {
        val state = Seed.initialState().copy(measurements = listOf(
            BodyMeasurement("m1", 1, "1970-01-01", BodySite.WAIST, 36.0, LengthUnit.IN),
            BodyMeasurement("m2", 2, "1970-01-01", BodySite.WAIST, 90.0, LengthUnit.CM),
            BodyMeasurement("m3", 3, "1970-01-01", BodySite.WAIST, 89.0, LengthUnit.CM)
        ))
        val waist = state.siteProgress().single()
        assertEquals(LengthUnit.CM, waist.unit)
        assertEquals(-1.0, waist.sinceLast!!, 1e-9)
        assertEquals(89.0 - 36.0 * 2.54, waist.sinceFirst!!, 1e-9)
    }

    @Test fun recordsAreActualBestsAndSupersededOnesFoldAway() {
        val exercise = Seed.initialState().exercises.first { it.loading == LoadingType.EXTERNAL }
        fun set(id: String, kg: Double, reps: Int, at: Long) = LoggedSet(id, kg, reps, null, false, at)
        val entry = WorkoutExercise("e", "s", exercise, 2, 2, null, 8, 12, 2, 90,
            sets = listOf(set("a", 40.0, 10, 1), set("b", 45.0, 8, 2), set("c", 40.0, 8, 3),
                LoggedSet("w", 60.0, 5, null, true, 0)))
        val workout = Workout("w1", "t", "Push", Phase.BASELINE, 0, 100, exercises = listOf(entry), startDate = "1970-01-01")
        val records = repRecords(listOf(workout), LoadUnit.KG) { it.exercise.id == exercise.id }
        assertEquals(listOf(8, 10), records.map { it.reps })
        assertEquals(45.0, records.first { it.reps == 8 }.load, 0.0)
        assertFalse("8 reps at 45 kg is not beaten by 10 reps at 40 kg", records.first { it.reps == 8 }.superseded)
        assertTrue("warm-ups never set a record", records.none { it.load == 60.0 })
        assertTrue(repRecords(listOf(workout), LoadUnit.LB) { true }.isEmpty())
    }
}
