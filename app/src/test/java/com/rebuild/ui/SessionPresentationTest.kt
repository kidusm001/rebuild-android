package com.rebuild.ui

import com.rebuild.domain.*
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SessionPresentationTest {
    private val bench = Exercise("bench", "Bench press")
    private val row = Exercise("row", "Cable row", equipment = Equipment.CABLE)
    private val curl = Exercise("curl", "Curl")
    private val locale = Locale.getDefault()
    private val zone = TimeZone.getDefault()

    @Before fun fixLocale() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreLocale() {
        Locale.setDefault(locale)
        TimeZone.setDefault(zone)
    }

    private fun exercise(id: String, what: Exercise, target: Int, vararg sets: LoggedSet) =
        WorkoutExercise(id, id, what, target, target, null, 8, 12, 2, 90, sets = sets.toList())

    private fun set(id: String, warmup: Boolean = false, unit: LoadUnit = LoadUnit.KG) =
        if (unit == LoadUnit.PLATES) LoggedSet(id, null, 10, null, warmup, 1, LoadUnit.PLATES, 5)
        else LoggedSet(id, 20.0, 10, null, warmup, 1)

    private fun workout(vararg exercises: WorkoutExercise) =
        Workout("w", "t", "Upper", Phase.INTRO_TWO, 0, exercises = exercises.toList())

    @Test fun resumeOpensTheFirstExerciseStillShortOfItsWorkingTarget() {
        val w = workout(
            exercise("a", bench, 2, set("1"), set("2"), set("3")),
            exercise("b", row, 2, set("4", unit = LoadUnit.PLATES)),
            exercise("c", curl, 2)
        )
        assertEquals("b", w.resumeExercise()?.id)
    }

    @Test fun warmupsNeverCountTowardTheResumeTarget() {
        val w = workout(
            exercise("a", bench, 1, set("w1", warmup = true), set("w2", warmup = true)),
            exercise("b", row, 1)
        )
        assertEquals("a", w.resumeExercise()?.id)
    }

    @Test fun resumeFallsBackToTheFirstExerciseWhenEveryTargetIsLoggedAndNullWhenEmpty() {
        val done = workout(exercise("a", bench, 1, set("1")), exercise("b", row, 1, set("2", unit = LoadUnit.PLATES)))
        assertEquals("a", done.resumeExercise()?.id)
        assertNull(workout().resumeExercise())
    }

    @Test fun historyGroupsNewestFirstUnderCalendarMonthsWithoutReordering() {
        val day = 86_400_000L
        val sep30 = 20_726 * day // 2026-09-30 UTC
        val sep1 = sep30 - 29 * day
        val aug31 = sep1 - day
        val workouts = listOf(sep30, sep1, aug31).mapIndexed { i, at ->
            Workout("w$i", "t", "Upper", Phase.INTRO_TWO, at, at + 1, exercises = emptyList())
        }
        val months = historyMonths(workouts)
        assertEquals(listOf("September 2026", "August 2026"), months.map { it.first })
        assertEquals(listOf("w0", "w1"), months[0].second.map { it.id })
        assertEquals(listOf("w2"), months[1].second.map { it.id })
    }
}
