package com.rebuild.ui

import com.rebuild.domain.Exercise
import com.rebuild.domain.LiftedSet
import com.rebuild.domain.LoadUnit
import com.rebuild.domain.LoadingType
import com.rebuild.domain.LoggedSet
import com.rebuild.domain.Phase
import com.rebuild.domain.RepComparison
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

    @Test fun aColumnReadsNowThenAndTheChangeInWords() {
        val asOf = LocalDate.parse("2026-10-13")
        val gained = RepComparison(9, lifted(46.25, 9, "2026-11-10"), lifted(40.0, 9, "2026-10-12"))
        assertEquals("9 reps: 46.25 kg" to "10 Nov · was 40 kg on 12 Oct (+6.25 kg)",
            repCompareReadout(gained, asOf, LoadUnit.KG, press, today))
        val same = lifted(40.0, 10, "2026-10-01")
        assertEquals("10 reps: 40 kg" to "1 Oct · none heavier since",
            repCompareReadout(RepComparison(10, same, same), asOf, LoadUnit.KG, press, today))
        assertEquals("12 reps: 35 kg" to "3 Nov · new since 13 Oct",
            repCompareReadout(RepComparison(12, lifted(35.0, 12, "2026-11-03"), null), asOf, LoadUnit.KG, press, today))
    }

    @Test fun theTopSetLineBreaksAcrossMoreThanTwoWeeks() {
        fun point(date: String) = TopSetPoint(LocalDate.parse(date), LoggedSet(date, 40.0, 8, null, false, 0), emptyList())
        val segments = topSetSegments(listOf(point("2026-09-01"), point("2026-09-15"), point("2026-09-30"), point("2026-10-07")))
        assertEquals(listOf(2, 2), segments.map { it.size })
    }

    @Test fun platesNeverGetAHalfPlateTick() {
        assertEquals(true, loadTicks(4.0, 5.0, LoadUnit.PLATES).all { it == kotlin.math.floor(it) })
        assertEquals(listOf(35.0, 40.0, 45.0), loadTicks(35.0, 42.5, LoadUnit.KG))
    }
}
