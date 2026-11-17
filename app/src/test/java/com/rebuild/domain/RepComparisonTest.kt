package com.rebuild.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RepComparisonTest {
    private val exercise = Seed.initialState().exercises.first { it.loading == LoadingType.EXTERNAL }
    private var clock = 0L

    private fun set(kg: Double, reps: Int, unit: LoadUnit = LoadUnit.KG, warmup: Boolean = false) =
        LoggedSet.of("s${++clock}", kg, reps, null, warmup, ++clock, loadUnit = unit, plates = null)

    private fun session(date: String, vararg sets: LoggedSet, finished: Boolean = true): Workout {
        val entry = WorkoutExercise("e$date", "slot", exercise, 2, 3, null, 8, 12, 2, 90, sets = sets.toList())
        val started = LocalDate.parse(date).toEpochDay() * 86_400_000L
        return Workout("w$date", "t", "Push", Phase.BASELINE, started, if (finished) started + 1 else null,
            exercises = listOf(entry), startDate = date)
    }

    private val history = listOf(
        session("2026-09-01", set(35.0, 10), set(35.0, 9), set(60.0, 5, warmup = true)),
        session("2026-09-08", set(37.5, 9), set(35.0, 11)),
        session("2026-10-06", set(40.0, 9), set(40.0, 8), set(90.0, 10, LoadUnit.LB)),
        session("2026-10-13", set(42.5, 9)),
        session("2026-10-20", set(50.0, 12), finished = false)
    )
    private fun kg() = recordSets(history, LoadUnit.KG) { it.exercise.id == exercise.id }

    @Test fun theBestAtEachRepCountIsWhatWasLiftedByThatDay() {
        val then = bestByReps(kg(), LocalDate.parse("2026-09-08"))
        assertEquals(listOf(9, 10, 11), then.keys.toList())
        assertEquals(37.5, then.getValue(9).load, 0.0)
        assertEquals(LocalDate.parse("2026-09-08"), then.getValue(9).date)
        val now = bestByReps(kg())
        assertEquals(42.5, now.getValue(9).load, 0.0)
        assertTrue("warm-ups never count", now.values.none { it.load == 60.0 })
        assertFalse("an unfinished workout isn't history yet", now.containsKey(12))
        assertTrue("before the first session there is nothing", bestByReps(kg(), LocalDate.parse("2026-08-31")).isEmpty())
    }

    @Test fun repCountsFirstLiftedAfterTheComparisonDayAreNew() {
        val columns = repComparisons(kg(), LocalDate.parse("2026-09-08"))
        assertEquals(listOf(8, 9, 10, 11), columns.map { it.reps })
        val eight = columns.first { it.reps == 8 }
        assertTrue(eight.new)
        assertNull(eight.then)
        assertEquals(0.0, eight.gain, 0.0)
        val nine = columns.first { it.reps == 9 }
        assertFalse(nine.new)
        assertEquals(5.0, nine.gain, 0.0)
        assertEquals(LocalDate.parse("2026-10-13"), nine.now.date)
        val ten = columns.first { it.reps == 10 }
        assertEquals("the same set, then and now", ten.then, ten.now)
        assertEquals(0.0, ten.gain, 0.0)
    }

    @Test fun onlyTheChosenUnitIsCompared() {
        val lb = recordSets(history, LoadUnit.LB) { it.exercise.id == exercise.id }
        assertEquals(listOf(90.0), lb.map { it.load })
        val columns = repComparisons(lb, LocalDate.parse("2026-09-08"))
        assertEquals(listOf(10), columns.map { it.reps })
        assertTrue("first lifted in pounds after the comparison day", columns.single().new)
        assertTrue(kg().none { it.set.loadUnit == LoadUnit.LB })
    }

    @Test fun equalLoadsKeepTheFirstTimeItWasLifted() {
        val sets = recordSets(listOf(session("2026-09-01", set(40.0, 8)), session("2026-09-15", set(40.0, 8))), LoadUnit.KG) { true }
        assertEquals(LocalDate.parse("2026-09-01"), bestByReps(sets).getValue(8).date)
    }
}
