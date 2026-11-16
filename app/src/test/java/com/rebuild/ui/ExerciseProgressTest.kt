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
        fun point(date: String) = TopSetPoint(LocalDate.parse(date), LoggedSet(date, 40.0, 8, null, false, 0), emptyList())
        val segments = topSetSegments(listOf(point("2026-09-01"), point("2026-09-15"), point("2026-09-30"), point("2026-10-07")))
        assertEquals(listOf(2, 2), segments.map { it.size })
    }

    @Test fun platesNeverGetAHalfPlateTick() {
        assertEquals(true, loadTicks(4.0, 5.0, LoadUnit.PLATES).all { it == kotlin.math.floor(it) })
        assertEquals(listOf(35.0, 40.0, 45.0), loadTicks(35.0, 42.5, LoadUnit.KG))
    }
}
