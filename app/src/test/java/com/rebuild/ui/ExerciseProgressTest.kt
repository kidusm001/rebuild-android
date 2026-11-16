package com.rebuild.ui

import com.rebuild.domain.Exercise
import com.rebuild.domain.LiftedSet
import com.rebuild.domain.LoadUnit
import com.rebuild.domain.LoadingType
import com.rebuild.domain.LoggedSet
import com.rebuild.domain.Phase
import com.rebuild.domain.RepComparison
import com.rebuild.domain.RepRecord
import com.rebuild.domain.Workout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ExerciseProgressTest {
    private val press = Exercise("press", "Press", LoadingType.EXTERNAL, false)
    private val today = LocalDate.parse("2026-11-10")
    private val workout = Workout("w", "t", "Push", Phase.BASELINE, 0, 1, exercises = emptyList())
    private fun lifted(kg: Double, reps: Int, date: String) =
        LiftedSet(LoggedSet("s$date$kg", kg, reps, null, false, 0), workout, LocalDate.parse(date))

    @Test fun aRecordSaysWhatItsRepCountWasOnlyWhenItWasSetSinceAndIsHeavier() {
        val now = lifted(46.25, 9, "2026-11-10")
        val record = RepRecord(9, 46.25, now.set, now.workout, superseded = false)
        assertEquals("was 40 kg on 12 Oct",
            recordWas(record, RepComparison(9, now, lifted(40.0, 9, "2026-10-12")), press))
        // Unchanged since: the record is older than the comparison day, so there is nothing new to say.
        val old = lifted(40.0, 10, "2026-10-01")
        assertEquals(null, recordWas(RepRecord(10, 40.0, old.set, old.workout, false), RepComparison(10, old, old), press))
        // First lifted since: nothing to compare with.
        val fresh = lifted(35.0, 12, "2026-11-03")
        assertEquals(null, recordWas(RepRecord(12, 35.0, fresh.set, fresh.workout, false), RepComparison(12, fresh, null), press))
    }

    @Test fun theTopSetLineBreaksAcrossMoreThanTwoWeeks() {
        fun point(date: String) = TopSetPoint(LocalDate.parse(date), LoggedSet(date, 40.0, 8, null, false, 0))
        val segments = topSetSegments(listOf(point("2026-09-01"), point("2026-09-15"), point("2026-09-30"), point("2026-10-07")))
        assertEquals(listOf(2, 2), segments.map { it.size })
    }

    @Test fun platesNeverGetAHalfPlateTick() {
        assertEquals(true, loadTicks(4.0, 5.0, LoadUnit.PLATES).all { it == kotlin.math.floor(it) })
        assertEquals(listOf(35.0, 40.0, 45.0), loadTicks(35.0, 42.5, LoadUnit.KG))
    }

    @Test fun threeMonthsCountsBackFromTodayAndAllKeepsEveryWorkout() {
        fun point(date: String) = TopSetPoint(LocalDate.parse(date), LoggedSet(date, 40.0, 8, null, false, 0))
        val points = listOf(point("2026-06-01"), point("2026-08-09"), point("2026-08-10"), point("2026-11-01"))
        assertEquals(listOf("2026-08-10", "2026-11-01"),
            pointsInRange(points, ChartRange.M3, today).map { it.date.toString() })
        assertEquals(4, pointsInRange(points, ChartRange.ALL, today).size)
    }

    @Test fun aDotTheLineRunsAboveHasItsRepsUnderneath() {
        fun point(date: String, kg: Double) = TopSetPoint(LocalDate.parse(date), LoggedSet(date, kg, 8, null, false, 0))
        // A peak, a valley, a rise; then a lone workout after a gap, which nothing is drawn to.
        val segments = topSetSegments(listOf(point("2026-09-01", 50.0), point("2026-09-03", 60.0), point("2026-09-05", 55.0),
            point("2026-09-07", 65.0), point("2026-10-30", 40.0)))
        assertEquals(listOf(true, false, true, false, false), labelBelow(segments))
    }

    @Test fun crowdedRepsLabelsThinOutPerSideAndOnlyTheTouchedOnesNeighboursGiveWay() {
        // Ten pixels apart with labels needing 25, all above: every third from the latest back.
        val xs = List(7) { it * 10f }
        val above = List(7) { false }
        assertEquals(setOf(6, 3, 0), labelledPoints(xs, above, 25f, null))
        // Touching the second point labels it; the labels at 0 and 3 it would run into give way, 6 stays.
        assertEquals(setOf(1, 6), labelledPoints(xs, above, 25f, 1))
        // Alternating sides, as a zig-zag is: every point keeps its reps at twenty pixels apart.
        val zigzag = List(7) { it % 2 == 0 }
        assertEquals(xs.indices.toSet(), labelledPoints(xs.map { it * 2 }, zigzag, 25f, null))
    }
}
