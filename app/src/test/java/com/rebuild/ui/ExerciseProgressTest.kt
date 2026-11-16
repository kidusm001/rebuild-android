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
import androidx.compose.ui.geometry.Rect

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

    private fun point(date: String, kg: Double, workout: String = "Push") =
        TopSetPoint(LocalDate.parse(date), LoggedSet("$date$workout", kg, 8, null, false, 0), workout)

    @Test fun oneLineByDefaultAndALinePerWorkoutWhenSplit() {
        val points = listOf(point("2026-10-01", 60.0, "Lower A"), point("2026-10-03", 50.0, "Lower B"),
            point("2026-10-08", 62.5, "Lower A"), point("2026-10-10", 52.5, "Lower B"))
        // By default one line through every workout, in date order.
        assertEquals(listOf(points), topSetLines(points, split = false))
        // Split: the latest workout's line first, each in date order.
        val lines = topSetLines(points, split = true)
        assertEquals(listOf(listOf(50.0, 52.5), listOf(60.0, 62.5)), lines.map { l -> l.map { it.load } })
        // The lower line's reps go under it, the upper's over.
        assertEquals(listOf(false, true, false, true), labelBelow(points, lines))
        // A third workout would be a tangle: no split on offer, and asking for one keeps the single line.
        val three = points + point("2026-10-12", 55.0, "Extra")
        assertEquals(false, canSplitByWorkout(three))
        assertEquals(listOf(three), topSetLines(three, split = true))
        // Nor with only one workout.
        assertEquals(false, canSplitByWorkout(points.filter { it.workout == "Lower A" }))
    }

    @Test fun onOneLineADotTheLineRunsAboveHasItsRepsUnderneath() {
        // A peak, a valley, a rise; then a lone workout after a gap, which nothing is drawn to.
        val points = listOf(point("2026-09-01", 50.0), point("2026-09-03", 60.0), point("2026-09-05", 55.0),
            point("2026-09-07", 65.0), point("2026-10-30", 40.0))
        assertEquals(listOf(true, false, true, false, false), labelBelow(points, topSetLines(points, split = false)))
    }

    @Test fun crowdedLabelsThinOutAndOnlyTheTouchedOnesNeighboursGiveWay() {
        // Boxes 25 wide, 10 apart, on one row: every third from the latest back.
        val row = List(7) { Rect(it * 10f, 0f, it * 10f + 25f, 10f) }
        assertEquals(setOf(6, 3, 0), labelledPoints(row, null))
        // Touching the second labels it; the boxes it overlaps (0 and 3) give way, 6 stays.
        assertEquals(setOf(1, 6), labelledPoints(row, 1))
        // Boxes on different rows never clash, however close across: a zig-zag keeps every label.
        val zigzag = List(7) { Rect(it * 10f, if (it % 2 == 0) 0f else 20f, it * 10f + 15f, if (it % 2 == 0) 10f else 30f) }
        assertEquals(zigzag.indices.toSet(), labelledPoints(zigzag, null))
        // But a box under one dot can still run into a box over another: they are checked as boxes, not sides.
        assertEquals(setOf(1), labelledPoints(listOf(Rect(0f, 0f, 20f, 10f), Rect(5f, 5f, 25f, 15f)), null))
    }

    @Test fun theChartsWeekdaysMatchRecordsAndOnlyAnOlderYearIsSpelledOut() {
        assertEquals("Thu 22 Oct", readoutWeekday(LocalDate.parse("2026-10-22"), today))
        assertEquals("Wed 22 Oct 2025", readoutWeekday(LocalDate.parse("2025-10-22"), today))
    }
}
