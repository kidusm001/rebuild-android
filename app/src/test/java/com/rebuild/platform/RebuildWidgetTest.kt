package com.rebuild.platform

import android.app.AlarmManager
import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.ImageView
import android.util.SizeF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.rebuild.R
import com.rebuild.WidgetRenderKey
import com.rebuild.data.StateChanges
import com.rebuild.domain.Action
import com.rebuild.domain.Measure
import com.rebuild.domain.Monotonic
import com.rebuild.domain.Phase
import com.rebuild.domain.Seed
import com.rebuild.domain.WidgetDestination
import com.rebuild.domain.WidgetModel
import com.rebuild.domain.WidgetSize
import com.rebuild.domain.scheduledRoutines
import com.rebuild.domain.widgetModel
import com.rebuild.domain.widgetSizeFor
import com.rebuild.ui.Navigator
import com.rebuild.ui.Routes
import com.rebuild.ui.Tab
import com.rebuild.ui.accentTextFor
import com.rebuild.ui.applyWidgetDestination
import com.rebuild.ui.theme.Dark
import com.rebuild.ui.theme.Light
import com.rebuild.ui.theme.SignalAmber
import androidx.compose.ui.graphics.toArgb
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RebuildWidgetTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val utc = ZoneId.of("UTC")
    private val us = Locale.US

    private fun millis(date: LocalDate, hour: Int = 10): Long =
        date.atTime(hour, 0).atZone(utc).toInstant().toEpochMilli()

    @Test
    fun allSizesAndStatesInflateWithoutErrors() {
        val tuesday = LocalDate.of(2026, 9, 15)
        val now = millis(tuesday, 10)
        val base = StateChanges.apply(Seed.initialState(), Action.SelectPhase(Phase.INTRO_TWO))
        val tuesdayTemplate = base.scheduledRoutines(Phase.INTRO_TWO).first { it.weekday == 2 }
        val activeState = StateChanges.apply(base, Action.StartWorkout(tuesdayTemplate.id, now))
        val activeWorkout = requireNotNull(activeState.activeWorkout)
        val restingState = StateChanges.apply(
            activeState,
            Action.SaveSet(activeWorkout.id, activeWorkout.exercises[0].id, "s1", 40.0, 10, 3.0, false, now + 10_000)
        )

        val models = listOf(
            WidgetModel.Setup,
            WidgetModel.Unavailable,
            base.widgetModel(now, utc, us),
            base.widgetModel(millis(LocalDate.of(2026, 9, 16), 10), utc, us), // Rest day
            activeState.widgetModel(now + 5_000, utc, us),
            restingState.widgetModel(now + 20_000, utc, us, exactAlarmsAllowed = true),
            restingState.widgetModel(now + 20_000, utc, us, exactAlarmsAllowed = false)
        )

        for (size in WidgetSize.entries) {
            for (model in models) {
                val rv = RebuildWidgetRenderer.render(context, model, size, now + 20_000, elapsedRealtimeMs = 50_000L)
                val inflated = rv.apply(context, FrameLayout(context))
                assertNotNull("Inflated view for $size / $model", inflated)
                assertNotNull("TalkBack summary set on root", inflated.findViewById<View>(R.id.widget_root).contentDescription)
            }
        }
    }

    @Test
    fun everyStateInflatesAtLargeTextAndWithALiveStopwatch() {
        val tuesday = LocalDate.of(2026, 9, 15)
        val now = millis(tuesday, 18)
        val base = StateChanges.apply(Seed.initialState(), Action.SelectPhase(Phase.INTRO_TWO))
        val template = base.scheduledRoutines(Phase.INTRO_TWO).first { it.weekday == 2 }
        var active = StateChanges.apply(base, Action.StartWorkout(template.id, now))
        val w = requireNotNull(active.activeWorkout)
        val timed = active.exercises.first { it.measure == Measure.TIMED }
        active = StateChanges.apply(active, Action.AddWorkoutExercise(w.id, "we-timed", timed.id))
        val boot = Monotonic(boot = 1, session = "p", elapsedMs = 50_000L)
        val watching = StateChanges.apply(
            active, Action.StartStopwatch("sw", w.id, active.activeWorkout!!.exercises.last().id, boot, now + 5_000)
        )
        val resting = StateChanges.apply(active, Action.SaveSet(w.id, w.exercises[0].id, "s1", 40.0, 10, 3.0, false, now))
        val models = listOf(
            base.widgetModel(now, utc, us),
            base.widgetModel(millis(LocalDate.of(2026, 9, 16)), utc, us),
            watching.widgetModel(now + 10_000, utc, us, monotonic = boot.copy(elapsedMs = 60_000L)),
            resting.widgetModel(now + 10_000, utc, us)
        )
        for (scale in listOf(1f, 1.3f, 1.5f, 2f)) {
            val scaled = context.createConfigurationContext(
                Configuration(context.resources.configuration).apply { fontScale = scale }
            )
            for (size in WidgetSize.entries) for (model in models) {
                val view = RebuildWidgetRenderer.render(scaled, model, size, now + 10_000, 100_000L)
                    .apply(scaled, FrameLayout(scaled))
                assertNotNull("$size at $scale for $model", view)
            }
        }
        val live = RebuildWidgetRenderer.render(
            context, watching.widgetModel(now + 10_000, utc, us, monotonic = boot.copy(elapsedMs = 60_000L)),
            WidgetSize.STANDARD, now + 10_000, 100_000L
        ).apply(context, FrameLayout(context))
        assertEquals(View.VISIBLE, live.findViewById<View>(R.id.stopwatch_row).visibility)
        assertEquals(View.VISIBLE, live.findViewById<View>(R.id.header_resume).visibility)
    }

    @Test
    fun anOlderReadNeverReplacesANewerPublish() {
        val gate = WidgetPublishGate()
        val shown = mutableListOf<String>()
        val resizeRead = gate.markRead() // a resize reads a running rest...
        val expiryRead = gate.markRead() // ...then the rest expires and that refresh reads the cleared state
        assertTrue(gate.admit(7, expiryRead) { shown += "expired" })
        assertFalse(gate.admit(7, resizeRead) { shown += "stale countdown" })
        assertEquals(listOf("expired"), shown)
        // Other widgets keep their own order, and a later read still publishes.
        assertTrue(gate.admit(8, resizeRead) { shown += "other widget" })
        assertTrue(gate.admit(7, gate.markRead()) { shown += "next" })
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aShort2x2NeverCutsOffItsContent() {
        val tuesday = LocalDate.of(2026, 9, 15)
        val now = millis(tuesday, 18)
        val base = StateChanges.apply(Seed.initialState(), Action.SelectPhase(Phase.INTRO_TWO))
        val template = base.scheduledRoutines(Phase.INTRO_TWO).first { it.weekday == 2 }
        val active = StateChanges.apply(base, Action.StartWorkout(template.id, now))
        val w = requireNotNull(active.activeWorkout)
        val resting = StateChanges.apply(active, Action.SaveSet(w.id, w.exercises[0].id, "s1", 40.0, 10, 3.0, false, now))
        val skipped = StateChanges.apply(resting, Action.SkipRest)
        val density = context.resources.displayMetrics.density
        for ((width, height) in listOf(160 to 130, 160 to 186, 176 to 223)) {
            for (model in listOf(skipped.widgetModel(now + 10_000, utc, us), resting.widgetModel(now + 10_000, utc, us),
                base.widgetModel(millis(LocalDate.of(2026, 9, 16)), utc, us))) {
                val view = RebuildWidgetRenderer.render(context, model, WidgetSize.COMPACT, now + 10_000, 100_000L,
                    heightDp = height, widthDp = width).apply(context, FrameLayout(context))
                val w = (width * density).toInt()
                val h = (height * density).toInt()
                view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, w, h)
                val content = view.findViewById<android.view.ViewGroup>(R.id.content)
                val limit = content.height - content.paddingBottom
                for (i in 0 until content.childCount) {
                    val child = content.getChildAt(i)
                    if (child.visibility != View.VISIBLE || child.height == 0) continue
                    assertTrue("${model::class.simpleName} ${width}x$height: child $i ends at ${child.bottom} > $limit",
                        child.bottom <= limit)
                }
            }
        }
    }

    @Test
    fun widgetTextIsSetInTheAppsArchivoFaces() {
        val painter = WidgetTextPainter(context)
        val style = WidgetTextStyle(17f, WidgetFace.BOLD)
        // Archivo Bold and the system bold set the same words to different widths; the painter must use Archivo.
        val archivo = android.graphics.Paint().apply {
            typeface = ResourcesCompat.getFont(context, R.font.archivo_bold)
            textSize = 17f * context.resources.displayMetrics.scaledDensity
            fontFeatureSettings = "tnum"
        }.measureText("Neutral-grip lat pulldown")
        val bitmap = painter.render("Neutral-grip lat pulldown", style, 400f)
        assertEquals(kotlin.math.ceil(archivo).toInt(), bitmap.width)
        assertEquals(android.graphics.Bitmap.Config.ALPHA_8, bitmap.config)
        // Shared text across size variants is one bitmap, so it travels once in the parcel.
        assertTrue(bitmap === painter.render("Neutral-grip lat pulldown", style, 400f))
        // Too long for its width: ellipsized to fit, never wider than allowed.
        val narrow = painter.render("Overhead cable triceps extension", WidgetTextStyle(13.5f), 80f)
        assertTrue(narrow.width <= painter.px(80f).toInt())
    }

    @Test
    fun restViewShowsTheNextSetAndLastTimesSetAsLifted() {
        val tuesday = LocalDate.of(2026, 9, 15)
        var state = StateChanges.apply(Seed.initialState(), Action.SelectPhase(Phase.INTRO_TWO))
        val template = state.scheduledRoutines(Phase.INTRO_TWO).first { it.weekday == 2 }
        val lastWeek = millis(tuesday.minusDays(7), 18)
        state = StateChanges.apply(state, Action.StartWorkout(template.id, lastWeek))
        var w = requireNotNull(state.activeWorkout)
        state = StateChanges.apply(state, Action.SaveSet(w.id, w.exercises[0].id, "o1", 40.0, 10, 2.0, false, lastWeek + 60_000))
        state = StateChanges.apply(state, Action.SaveSet(w.id, w.exercises[0].id, "o2", 42.5, 8, 1.0, false, lastWeek + 200_000))
        state = StateChanges.apply(state, Action.FinishWorkout(lastWeek + 600_000, countForWeek = true))
        val now = millis(tuesday, 18)
        state = StateChanges.apply(state, Action.StartWorkout(template.id, now))
        w = requireNotNull(state.activeWorkout)
        state = StateChanges.apply(state, Action.SaveSet(w.id, w.exercises[0].id, "n1", 40.0, 10, 2.0, false, now + 60_000))

        val model = state.widgetModel(now + 70_000, utc, us) as WidgetModel.Resting
        for (size in listOf(WidgetSize.COMPACT, WidgetSize.STANDARD, WidgetSize.EXPANDED)) {
            val view = RebuildWidgetRenderer.render(context, model, size, now + 70_000, 100_000L)
                .apply(context, FrameLayout(context))
            assertEquals(View.GONE, view.findViewById<View>(R.id.list_header).visibility)
            val title = view.findViewById<ImageView>(R.id.rest_next_title)
            assertEquals(w.exercises[0].exercise.name, title.contentDescription.toString())
            assertNotNull("title is drawn as an Archivo image", title.drawable)
            val last = view.findViewById<ImageView>(R.id.rest_last)
            assertEquals(View.VISIBLE, last.visibility)
            val said = last.contentDescription.toString()
            assertTrue(said, said.replace('\u00A0', ' ').matches(Regex("Last time 42\\.5 .* × 8")))
        }
    }

    @Test
    fun restingChronometerCountsDownWhenExactAllowedAndFallsBackToStaticWhenDenied() {
        val tuesday = LocalDate.of(2026, 9, 15)
        val now = millis(tuesday, 18)
        val base = StateChanges.apply(Seed.initialState(), Action.SelectPhase(Phase.INTRO_TWO))
        val template = base.scheduledRoutines(Phase.INTRO_TWO).first { it.weekday == 2 }
        val active = StateChanges.apply(base, Action.StartWorkout(template.id, now))
        val w = requireNotNull(active.activeWorkout)
        val resting = StateChanges.apply(active, Action.SaveSet(w.id, w.exercises[0].id, "s1", 40.0, 10, 3.0, false, now))
        val timer = requireNotNull(resting.restTimer)

        val liveModel = resting.widgetModel(now + 10_000, utc, us, exactAlarmsAllowed = true)
        val liveView = RebuildWidgetRenderer.render(context, liveModel, WidgetSize.STANDARD, now + 10_000, 100_000L)
            .apply(context, FrameLayout(context))
        val chrono = liveView.findViewById<Chronometer>(R.id.rest_chronometer)
        val staticView = liveView.findViewById<View>(R.id.rest_static_time)
        assertEquals(View.VISIBLE, chrono.visibility)
        assertEquals(View.GONE, staticView.visibility)
        assertTrue(chrono.isCountDown)
        assertEquals(100_000L + (timer.deadline - (now + 10_000)) + 999L, chrono.base)

        val staticModel = resting.widgetModel(now + 10_000, utc, us, exactAlarmsAllowed = false)
        val fallbackView = RebuildWidgetRenderer.render(context, staticModel, WidgetSize.STANDARD, now + 10_000, 100_000L)
            .apply(context, FrameLayout(context))
        assertEquals(View.GONE, fallbackView.findViewById<Chronometer>(R.id.rest_chronometer).visibility)
        val fallbackText = fallbackView.findViewById<ImageView>(R.id.rest_static_time)
        assertEquals(View.VISIBLE, fallbackText.visibility)
        assertTrue(fallbackText.contentDescription.toString().startsWith("Until"))
    }

    @Test
    fun widgetColorResourcesMatchInkAndEmberLightTheme() {
        assertEquals(Light.surfaceContainerLowest.toArgb(), ContextCompat.getColor(context, R.color.widget_surface))
        assertEquals(Light.surfaceContainer.toArgb(), ContextCompat.getColor(context, R.color.widget_badge_pending))
        assertEquals(Light.onSurface.toArgb(), ContextCompat.getColor(context, R.color.widget_on_surface))
        assertEquals(Light.onSurfaceVariant.toArgb(), ContextCompat.getColor(context, R.color.widget_on_surface_variant))
        assertEquals(accentTextFor(Light).toArgb(), ContextCompat.getColor(context, R.color.widget_accent_text))
        assertEquals(Light.primaryContainer.toArgb(), ContextCompat.getColor(context, R.color.widget_primary_container))
        assertEquals(Light.onPrimaryContainer.toArgb(), ContextCompat.getColor(context, R.color.widget_on_primary_container))
        assertEquals(SignalAmber.toArgb(), ContextCompat.getColor(context, R.color.widget_signal_amber))
    }

    @Test
    fun loadingAndPreviewLayoutsInflateAndOptionsMapToSizes() {
        val inflater = LayoutInflater.from(context)
        assertNotNull(inflater.inflate(R.layout.widget_loading, FrameLayout(context), false))
        assertNotNull(inflater.inflate(R.layout.widget_preview, FrameLayout(context), false))

        // API 28: portrait is min width x max height, landscape max width x min height, as the launcher reports them.
        val opts = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 306)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 560)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 120)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 222)
        }
        assertEquals(listOf(SizeF(306f, 222f), SizeF(560f, 120f)), RebuildWidgetRenderer.reportedSizes(opts))
        assertEquals(listOf(SizeF(306f, 186f)), RebuildWidgetRenderer.reportedSizes(null))
        assertEquals(WidgetSize.STRIP, widgetSizeFor(250, 60, 1.0f))
        assertNotNull(RebuildWidgetRenderer.buildResponsive(context, WidgetModel.Setup, opts).apply(context, FrameLayout(context)))
    }

    @Test
    fun fontScaleChangeInvalidatesWidgetRenderDedupeKey() {
        val k1 = WidgetRenderKey(WidgetModel.Setup, 1.0f)
        val k2 = WidgetRenderKey(WidgetModel.Setup, 2.0f)
        assertFalse(k1 == k2)
    }

    @Test
    fun parseWidgetDestinationValidatesActionExtraAndHistoryFlag() {
        assertEquals(
            WidgetDestination.WORKOUT,
            parseWidgetDestination(ACTION_WIDGET_OPEN, WidgetDestination.WORKOUT.name, 0)
        )
        assertEquals(
            WidgetDestination.TODAY,
            parseWidgetDestination(ACTION_WIDGET_OPEN, WidgetDestination.TODAY.name, 0)
        )
        // Replayed from Recents history must be ignored
        assertNull(
            parseWidgetDestination(ACTION_WIDGET_OPEN, WidgetDestination.WORKOUT.name, Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        )
        assertNull(parseWidgetDestination(Intent.ACTION_MAIN, WidgetDestination.WORKOUT.name, 0))
        assertNull(parseWidgetDestination(ACTION_WIDGET_OPEN, "UNKNOWN", 0))
    }

    @Test
    fun applyWidgetDestinationNeverPopsEditorsSetupOrReceipts() {
        // Editor open: ignored
        val editorNav = Navigator(listOf(Tab.PLAN.route, Routes.NEW_ROUTINE))
        applyWidgetDestination(editorNav, WidgetDestination.WORKOUT, "w1", setupShown = false, receiptShown = false)
        assertEquals(Routes.NEW_ROUTINE, editorNav.current)

        // Setup or receipt open: ignored
        val setupNav = Navigator(listOf(Tab.HISTORY.route))
        applyWidgetDestination(setupNav, WidgetDestination.TODAY, null, setupShown = true, receiptShown = false)
        assertEquals(Tab.HISTORY.route, setupNav.current)
        applyWidgetDestination(setupNav, WidgetDestination.TODAY, null, setupShown = false, receiptShown = true)
        assertEquals(Tab.HISTORY.route, setupNav.current)

        // Active workout: routes from History to Workout
        val activeNav = Navigator(listOf(Tab.HISTORY.route))
        applyWidgetDestination(activeNav, WidgetDestination.WORKOUT, "w1", setupShown = false, receiptShown = false)
        assertEquals(Routes.WORKOUT, activeNav.current)

        // Stale workout link when no workout active: falls back safely to Today
        val staleNav = Navigator(listOf(Tab.MORE.route))
        applyWidgetDestination(staleNav, WidgetDestination.WORKOUT, null, setupShown = false, receiptShown = false)
        assertEquals(Tab.HOME.route, staleNav.current)
    }

    @Test
    fun providerArmsAndCancelsInexactNonWakeupMidnightAlarm() {
        val manager = context.getSystemService(AlarmManager::class.java)
        val provider = RebuildWidgetProvider()
        provider.onEnabled(context)
        val scheduled = shadowOf(manager).scheduledAlarms
        assertEquals(1, scheduled.size)
        assertEquals(AlarmManager.RTC, scheduled.single().type)

        provider.onDisabled(context)
        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
    }

    @Test
    fun refreshReceiverCancelsStaleMidnightAlarmWhenNoWidgetsExist() {
        val manager = context.getSystemService(AlarmManager::class.java)
        WidgetUpdater.scheduleMidnight(context)
        assertEquals(1, shadowOf(manager).scheduledAlarms.size)

        WidgetRefreshReceiver().onReceive(context, Intent(WidgetRefreshReceiver.ACTION_MIDNIGHT))
        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
    }
}
