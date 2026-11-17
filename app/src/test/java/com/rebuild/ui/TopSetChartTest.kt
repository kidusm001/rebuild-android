package com.rebuild.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.rebuild.domain.Exercise
import com.rebuild.domain.LoadUnit
import com.rebuild.domain.LoggedSet
import com.rebuild.domain.Phase
import com.rebuild.domain.Workout
import com.rebuild.domain.WorkoutExercise
import com.rebuild.ui.theme.RebuildTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TopSetChartTest {
    @get:Rule val compose = createComposeRule()

    private val press = Exercise("press", "Press")
    private val today = LocalDate.parse("2026-11-10")

    private fun session(date: String, load: Double, unit: LoadUnit, name: String = "Push"): HistorySession {
        val set = if (unit == LoadUnit.LB) LoggedSet("s$date", null, 8, null, false, 0, LoadUnit.LB, lb = load)
            else LoggedSet("s$date", load, 8, null, false, 0)
        val entry = WorkoutExercise("e$date", null, press, 2, null, null, 8, 12, null, 90, sets = listOf(set))
        val workout = Workout("w$date", null, name, Phase.BASELINE, 0, 1, exercises = listOf(entry), startDate = date)
        return historySessions(listOf(workout), press.identity(), unit).single()
    }

    @Test fun switchingToAUnitWithOnlyOldWorkoutsFadesAcrossWithoutCrashing() {
        // kg has old and recent workouts, so the chart opens on 3 months; lb has only old ones, so it shows All.
        val kg = listOf(session("2026-05-01", 40.0, LoadUnit.KG), session("2026-11-01", 50.0, LoadUnit.KG))
        val lb = listOf(session("2026-04-01", 90.0, LoadUnit.LB), session("2026-05-01", 95.0, LoadUnit.LB))
        val shown = mutableStateOf(kg to LoadUnit.KG)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            RebuildTheme { TopSetChart(shown.value.first, shown.value.second, press, today) }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("range-M3").assertExists()
        shown.value = lb to LoadUnit.LB
        // Mid-fade the outgoing view is still drawn: it must keep its own kg points, not refilter lb by 3 months.
        repeat(10) { compose.mainClock.advanceTimeBy(32) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("top-set-chart").assertExists()
    }

    @Test fun splittingAnimatesToALinePerWorkoutAndBack() {
        val two = listOf(session("2026-10-01", 60.0, LoadUnit.KG, "Lower A"), session("2026-10-03", 50.0, LoadUnit.KG, "Lower B"),
            session("2026-10-08", 62.5, LoadUnit.KG, "Lower A"), session("2026-10-10", 52.5, LoadUnit.KG, "Lower B"))
        compose.setContent { RebuildTheme { TopSetChart(two, LoadUnit.KG, press, today) } }
        compose.onNodeWithTag("split-by-workout").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("top-set-chart").assertExists()
        compose.onNodeWithTag("split-by-workout").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("top-set-chart").assertExists()
    }
}
