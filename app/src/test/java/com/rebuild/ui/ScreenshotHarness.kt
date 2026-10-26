package com.rebuild.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToNodeAction
import com.rebuild.data.MemoryPersistence
import com.rebuild.data.RebuildRepository
import com.rebuild.domain.Action
import com.rebuild.domain.LoadingType
import com.rebuild.domain.Phase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Renders real screens of the app to PNG on the JVM, for design review and before/after comparison: a Galaxy A71-sized
 * phone in light, dark, 200% text and landscape. It only runs when REBUILD_SCREENS names an output folder, so the
 * normal test suite never writes files.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class ScreenshotHarness {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val out = System.getenv("REBUILD_SCREENS")?.let(::File)
    private val prefix = System.getenv("REBUILD_SCREENS_PREFIX").orEmpty()

    // Tuesday 20 October 2026, 18:30: Push is scheduled, and last Tuesday's Push gives "Last time".
    private val now = LocalDateTime.of(2026, 10, 20, 18, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val day = 24 * 60 * 60 * 1000L

    private val platform = object : PlatformActions {
        override val status = MutableStateFlow(PlatformStatus())
        override fun requestNotifications() = Unit
        override fun requestExactAlarms() = Unit
        override fun openBatterySettings() = Unit
        override fun exportBackup() = Unit
        override fun shareBackup() = Unit
        override fun chooseRestore() = Unit
    }

    @Before fun gate() = assumeTrue("Set REBUILD_SCREENS to render screens", out != null)

    @Test fun light() = tour("light")

    @Test fun dark() {
        RuntimeEnvironment.setQualifiers("+night")
        tour("dark")
    }

    @Test fun largeText() {
        RuntimeEnvironment.setQualifiers("+night")
        RuntimeEnvironment.setFontScale(2f)
        tour("large")
    }

    @Test fun landscape() {
        RuntimeEnvironment.setQualifiers("w914dp-h411dp-land-night")
        tour("land")
    }

    private fun tour(mode: String) {
        val repo = runBlocking { seeded() }
        compose.setContent { RebuildApp(repo, platform, clock = { now }) }
        shot("$mode-01-today")
        tap("History")
        shot("$mode-04-history")
        tap("Push")
        shot("$mode-05-history-detail")
        back()
        tap("Plan")
        shot("$mode-06-plan")
        tap("More")
        shot("$mode-07-more")
        tap("Today")
        runBlocking { startToday(repo) }
        compose.waitForIdle()
        shot("$mode-02-today-active")
        tap("Resume workout")
        shot("$mode-03-workout")
        compose.onNodeWithTag("exercise-picker").performScrollToNode(hasTestTag("exercise-chip-0"))
        tapTag("exercise-chip-0")
        shot("$mode-03c-workout-drop")
        tapTag("group-row")
        shot("$mode-03d-set-and-drop-editor")
        tapTag("group-done")
        tap("Finish")
        shot("$mode-08-finish-dialog")
        tapTag("confirm-button")
        compose.waitForIdle()
        shot("$mode-09-receipt")
        secondaryScreens(mode)
    }

    /** History's exercise chart, the routine library, the exercise library and the phase screen. */
    private fun secondaryScreens(mode: String) {
        if (compose.onAllNodes(hasText("Done") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()) tap("Done")
        else back()
        tap("History")
        tap("Exercises")
        tap("Incline dumbbell press")
        shot("$mode-10-exercise-history")
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).onFirst()
            .performScrollTo().performTouchInput { click(Offset(width * 0.25f, centerY)) }
        compose.waitForIdle()
        shot("$mode-10b-exercise-history-touched")
        back()
        tap("More")
        tap("Routines")
        shot("$mode-11-routines")
        back()
        tap("Exercises")
        shot("$mode-12-library")
        back()
        tap("Training phase")
        shot("$mode-13-phase")
        back()
        bodyScreens(mode)
    }

    private fun bodyScreens(mode: String) {
        tap("History")
        tapTag("history-tab-2")
        compose.waitForIdle()
        shot("$mode-14-history-body")
        tapTag("body-weight-trend")
        compose.waitForIdle()
        shot("$mode-15-bodyweight")
        touchChart("trend-chart", 0.4f)
        shot("$mode-15b-bodyweight-touched")
        back()
        tapTag("body-site-WAIST")
        shot("$mode-16-waist")
        touchChart("trend-chart", 0.55f)
        shot("$mode-16b-waist-touched")
        back()
        tapTag("body-measurements")
        compose.waitForIdle()
        shot("$mode-17-measurements")
        tapTag("measure-this-week")
        compose.waitForIdle()
        shot("$mode-18-measure-form")
        back()
        back()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /** Touches a chart [fraction] of the way across, as a finger would, so the shot shows a selected point. */
    private fun touchChart(tag: String, fraction: Float) {
        compose.onAllNodes(hasTestTag(tag)).onFirst().performScrollTo().performTouchInput { click(Offset(width * fraction, centerY)) }
        compose.waitForIdle()
    }

    private fun tapTag(tag: String) {
        val node = compose.onAllNodes(hasTestTag(tag)).onFirst()
        runCatching { node.performScrollTo() }
        node.performClick()
        compose.waitForIdle()
    }

    private fun tap(text: String) {
        val matcher = hasText(text) and hasClickAction()
        if (compose.onAllNodes(matcher).fetchSemanticsNodes().isEmpty()) {
            compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(matcher)
        }
        compose.onAllNodes(matcher).onFirst().performClick()
        compose.waitForIdle()
    }

    private suspend fun seeded(): RebuildRepository {
        val repo = RebuildRepository.fromPersistence(MemoryPersistence()) { now }
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        // Two earlier weeks of the same routine, so history and "Last time" have real numbers.
        listOf(42, 35, 28, 21, 14, 7).forEach { daysAgo ->
            val start = now - daysAgo * day
            repo.execute(Action.StartWorkout("push", start))
            val past = requireNotNull(repo.state.value.activeWorkout)
            past.exercises.take(4).forEachIndexed { e, we ->
                repeat(we.targetSets ?: 2) { s ->
                    val kg = if (we.exercise.loading == LoadingType.BODYWEIGHT) null else 40.0 - 5 * e - daysAgo / 7 * 2.5
                    repo.execute(Action.SaveSet(past.id, we.id, "p$daysAgo-$e-$s", kg, 10 - s, 2.0, false,
                        start + (e * 10 + s) * 60_000L))
                }
            }
            repo.execute(Action.FinishWorkout(start + 55 * 60_000L, countForWeek = true))
        }
        // Twelve weeks of weigh-ins three mornings a week, drifting down with daily noise, and weekly tape measures.
        val noise = listOf(0.4, -0.3, 0.1, 0.5, -0.2, 0.0, 0.3, -0.4, 0.2)
        (84 downTo 1).filter { it % 7 in setOf(1, 3, 6) }.forEachIndexed { i, daysAgo ->
            val at = now - daysAgo * day - 11 * 3_600_000L
            repo.execute(Action.LogBodyweight("bw$daysAgo", at, 85.0 - (84 - daysAgo) * 0.035 + noise[i % noise.size],
                com.rebuild.domain.MassUnit.KG))
        }
        repo.execute(Action.SetMeasurementSites(listOf(com.rebuild.domain.BodySite.WAIST, com.rebuild.domain.BodySite.CHEST,
            com.rebuild.domain.BodySite.NECK)))
        (8 downTo 1).forEach { w ->
            val at = now - (w * 7 - 5) * day - 10 * 3_600_000L
            val date = com.rebuild.domain.localDateOf(at).toString()
            repo.execute(Action.SaveMeasurements(listOf(
                com.rebuild.domain.BodyMeasurement("waist$w", at, date, com.rebuild.domain.BodySite.WAIST, 91.0 + w * 0.4,
                    com.rebuild.domain.LengthUnit.CM),
                com.rebuild.domain.BodyMeasurement("chest$w", at, date, com.rebuild.domain.BodySite.CHEST, 106.0 + (w % 2) * 0.5,
                    com.rebuild.domain.LengthUnit.CM),
                com.rebuild.domain.BodyMeasurement("neck$w", at, date, com.rebuild.domain.BodySite.NECK, 39.5 + w * 0.06,
                    com.rebuild.domain.LengthUnit.CM)
            )))
        }
        return repo
    }

    private suspend fun startToday(repo: RebuildRepository) {
        val start = now - 20 * 60_000L
        repo.execute(Action.StartWorkout("push", start))
        val today = requireNotNull(repo.state.value.activeWorkout)
        val first = today.exercises.first()
        repo.execute(Action.SaveSet(today.id, first.id, "warm", 20.0, 10, null, true, start + 60_000L))
        repo.execute(Action.SaveSet(today.id, first.id, "t1", 42.5, 10, 2.0, false, now - 30_000L))
        // Set 2 with a drop on it, the way "Add drop to set 2" logs it.
        repo.execute(Action.SaveSet(today.id, first.id, "t2", 42.5, 9, 1.0, false, now - 20_000L))
        repo.execute(Action.LogDrop(today.id, first.id, "g-drop", "t2d", 32.5, 7, 0.0, now - 10_000L))
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val root = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Every window in z-order: the activity, then dialogs, sheets and menus, each dimming what's behind it.
        windows().forEach { view ->
            val params = view.layoutParams as? WindowManager.LayoutParams
            if (params != null && params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0) {
                canvas.drawColor(android.graphics.Color.argb((params.dimAmount * 255).toInt(), 0, 0, 0))
            }
            val at = IntArray(2).also { view.getLocationOnScreen(it) }
            canvas.save()
            canvas.translate(at[0].toFloat(), at[1].toFloat())
            view.draw(canvas)
            canvas.restore()
        }
        val dir = requireNotNull(out).apply { mkdirs() }
        File(dir, "$prefix$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun windows(): List<View> {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        val views = global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<View>
        return views.filter { it.isShown && it.width > 0 }
    }
}
