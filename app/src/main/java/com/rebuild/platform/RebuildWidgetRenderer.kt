package com.rebuild.platform

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.rebuild.R
import com.rebuild.domain.TodayKind
import com.rebuild.domain.WidgetMark
import com.rebuild.domain.WidgetModel
import com.rebuild.domain.WidgetRow
import com.rebuild.domain.WidgetSize
import com.rebuild.domain.destination
import com.rebuild.domain.spokenSummary
import com.rebuild.domain.widgetCompactRows
import com.rebuild.domain.widgetListRows
import com.rebuild.domain.widgetSizeFor
import com.rebuild.domain.widgetWindow
import com.rebuild.ui.loadText

/**
 * Renders [WidgetModel] into native XML [RemoteViews] for the exact sizes the launcher reports.
 *
 * Words and figures are set in the app's own Archivo faces by [WidgetTextPainter] and tinted by the layout's colour
 * resources, so the launcher still applies light or dark itself. The two live clocks (the rest countdown and the
 * stopwatch) are platform Chronometers ticked by the launcher, and so stay in the system's condensed face.
 */
object RebuildWidgetRenderer {
    private const val NBSP = " "
    private const val COMPACT_ROW_SLOTS = 3

    private val TITLE = WidgetTextStyle(17f, WidgetFace.BOLD)
    private val TITLE_COMPACT = WidgetTextStyle(20f, WidgetFace.BOLD)
    private val KICKER = WidgetTextStyle(12.5f)
    private val KICKER_ACCENT = WidgetTextStyle(12.5f, WidgetFace.MEDIUM)
    private val KICKER_COMPACT = WidgetTextStyle(11.5f, WidgetFace.MEDIUM)
    private val META = WidgetTextStyle(12f)
    private val PILL = WidgetTextStyle(13.5f, WidgetFace.MEDIUM)
    private val LABEL = WidgetTextStyle(11.5f, WidgetFace.MEDIUM, tracking = 0.08f, caps = true)
    private val NEXT_TITLE = WidgetTextStyle(15f, WidgetFace.BOLD)
    private val NEXT_TITLE_COMPACT = WidgetTextStyle(14f, WidgetFace.BOLD)
    private val SET_LINE = WidgetTextStyle(13f)
    private val SET_LINE_COMPACT = WidgetTextStyle(12.5f)
    private val LAST = WidgetTextStyle(12.5f)
    private val LAST_COMPACT = WidgetTextStyle(12f)
    private val STATIC_TIME = WidgetTextStyle(22f, WidgetFace.NUMERALS)
    private val ROW_NAME = WidgetTextStyle(13.5f)
    private val ROW_NAME_STRONG = WidgetTextStyle(13.5f, WidgetFace.BOLD)
    private val TRAIL = WidgetTextStyle(12.5f)
    private val TRAIL_STRONG = WidgetTextStyle(12.5f, WidgetFace.BOLD)
    /** Badge digits are marks inside a fixed 20 dp circle; the row's spoken label carries them at any text size. */
    private val BADGE = WidgetTextStyle(11f, WidgetFace.BOLD, fixed = true)
    /** Weekdays are marks in the same fixed 34 dp column as the badges; the row's spoken label names the day in full. */
    private val DAY = WidgetTextStyle(11f, WidgetFace.MEDIUM, tracking = 0.04f, caps = true, fixed = true)
    private val SMALL = WidgetTextStyle(12f)
    private val SMALL_COMPACT = WidgetTextStyle(11.5f)
    private val STOPWATCH_NAME = WidgetTextStyle(13f)
    private val STRIP_PRIMARY = WidgetTextStyle(15f, WidgetFace.BOLD)
    private val STRIP_META = WidgetTextStyle(12f, WidgetFace.MEDIUM)
    private val STRIP_DETAIL = WidgetTextStyle(13f, WidgetFace.MEDIUM)
    private val STRIP_STATIC = WidgetTextStyle(16f, WidgetFace.NUMERALS)

    private class RowIds(
        val row: Int, val mark: Int, val num: Int, val cur: Int, val done: Int, val day: Int,
        val name: Int, val nameMuted: Int, val trail: Int
    )

    private val ROWS = listOf(
        RowIds(R.id.row_0, R.id.row_0_mark, R.id.row_0_num, R.id.row_0_cur, R.id.row_0_done, R.id.row_0_day,
            R.id.row_0_name, R.id.row_0_name_muted, R.id.row_0_trail),
        RowIds(R.id.row_1, R.id.row_1_mark, R.id.row_1_num, R.id.row_1_cur, R.id.row_1_done, R.id.row_1_day,
            R.id.row_1_name, R.id.row_1_name_muted, R.id.row_1_trail),
        RowIds(R.id.row_2, R.id.row_2_mark, R.id.row_2_num, R.id.row_2_cur, R.id.row_2_done, R.id.row_2_day,
            R.id.row_2_name, R.id.row_2_name_muted, R.id.row_2_trail),
        RowIds(R.id.row_3, R.id.row_3_mark, R.id.row_3_num, R.id.row_3_cur, R.id.row_3_done, R.id.row_3_day,
            R.id.row_3_name, R.id.row_3_name_muted, R.id.row_3_trail),
        RowIds(R.id.row_4, R.id.row_4_mark, R.id.row_4_num, R.id.row_4_cur, R.id.row_4_done, R.id.row_4_day,
            R.id.row_4_name, R.id.row_4_name_muted, R.id.row_4_trail),
        RowIds(R.id.row_5, R.id.row_5_mark, R.id.row_5_num, R.id.row_5_cur, R.id.row_5_done, R.id.row_5_day,
            R.id.row_5_name, R.id.row_5_name_muted, R.id.row_5_trail),
        RowIds(R.id.row_6, R.id.row_6_mark, R.id.row_6_num, R.id.row_6_cur, R.id.row_6_done, R.id.row_6_day,
            R.id.row_6_name, R.id.row_6_name_muted, R.id.row_6_trail)
    )

    /** A typical 4x2 cell, used before the launcher has reported the real one. */
    private val FALLBACK = SizeF(306f, 186f)

    fun buildResponsive(
        caller: Context,
        model: WidgetModel,
        options: Bundle? = null,
        nowMillis: Long = System.currentTimeMillis(),
        elapsedRealtimeMs: Long = SystemClock.elapsedRealtime()
    ): RemoteViews {
        val context = caller.applicationContext ?: caller
        // One painter per render: equal text shares one bitmap, so it is drawn and parcelled once.
        val painter = WidgetTextPainter(context)
        val sizes = reportedSizes(options)
        val size = (if (displayIsLandscape(context)) sizes.lastOrNull() else sizes.firstOrNull()) ?: FALLBACK
        // One layout, for the cell the launcher reports in the orientation we're in. No size map: given several sizes,
        // Pixel Launcher picked one for the wrong orientation, or the smallest when an update arrived while the home
        // screen was hidden. Resizes (RebuildWidgetProvider.onAppWidgetOptionsChanged) and rotations
        // (RebuildApplication.onConfigurationChanged) re-render instead.
        return renderAt(context, painter, model, size, nowMillis, elapsedRealtimeMs)
    }

    /**
     * Whether the phone's screen (and so the launcher) is landscape. Read from the display itself: while Rebuild is in
     * picture-in-picture its activity and even its application configuration report the landscape-shaped PiP window,
     * which would pick the launcher's landscape cell for a portrait home screen.
     */
    fun displayIsLandscape(context: Context): Boolean {
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
            ?: return context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        return metrics.widthPixels > metrics.heightPixels
    }

    /**
     * The sizes the launcher lays the widget out at, in dp: on API 31+ its exact list, else portrait (min width × max
     * height) then landscape (max width × min height).
     */
    @Suppress("DEPRECATION")
    fun reportedSizes(options: Bundle?): List<SizeF> {
        if (options == null) return listOf(FALLBACK)
        if (Build.VERSION.SDK_INT >= 31) {
            val exact = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                ?.filter { it.width > 0 && it.height > 0 }?.distinct()
            if (!exact.isNullOrEmpty()) return exact
        }
        val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        return listOfNotNull(
            SizeF(minW.toFloat(), maxH.toFloat()).takeIf { minW > 0 && maxH > 0 },
            SizeF(maxW.toFloat(), minH.toFloat()).takeIf { maxW > 0 && minH > 0 }
        ).distinct().ifEmpty { listOf(FALLBACK) }
    }

    private fun renderAt(
        context: Context,
        painter: WidgetTextPainter,
        model: WidgetModel,
        size: SizeF,
        nowMillis: Long,
        elapsedRealtimeMs: Long
    ): RemoteViews {
        val fontScale = context.resources.configuration.fontScale
        val tier = widgetSizeFor(size.width.toInt(), size.height.toInt(), fontScale)
        return render(context, model, tier, nowMillis, elapsedRealtimeMs, size.height.toInt(), size.width.toInt(), painter)
    }

    fun render(
        context: Context,
        model: WidgetModel,
        size: WidgetSize,
        nowMillis: Long = System.currentTimeMillis(),
        elapsedRealtimeMs: Long = SystemClock.elapsedRealtime(),
        heightDp: Int = when (size) {
            WidgetSize.STRIP -> 60
            WidgetSize.EXPANDED -> 280
            else -> 186
        },
        widthDp: Int = if (size == WidgetSize.COMPACT) 160 else 306,
        painter: WidgetTextPainter = WidgetTextPainter(context)
    ): RemoteViews {
        val layoutRes = when (size) {
            WidgetSize.STRIP -> R.layout.widget_strip
            WidgetSize.COMPACT -> R.layout.widget_compact
            WidgetSize.STANDARD, WidgetSize.EXPANDED -> R.layout.widget_standard
        }
        val views = RemoteViews(context.packageName, layoutRes)
        views.setOnClickPendingIntent(R.id.widget_root, widgetPendingIntent(context, model.destination()))
        views.setContentDescription(R.id.widget_root, model.spokenSummary())
        val pass = Pass(context, views, painter, widthDp, heightDp, nowMillis, elapsedRealtimeMs)
        when (size) {
            WidgetSize.STRIP -> pass.bindStrip(model)
            WidgetSize.COMPACT -> pass.bindCompact(model)
            WidgetSize.STANDARD, WidgetSize.EXPANDED -> pass.bindStandard(model, size == WidgetSize.EXPANDED)
        }
        return views
    }

    /** "Last time 80 kg × 10", the set as it was lifted, in its own unit; never presented as a target. */
    fun lastTimeText(model: WidgetModel.Resting): String? {
        val set = model.lastTimeSet ?: return null
        val exercise = model.lastTimeExercise ?: return null
        val load = loadText(set.amount, set.loadUnit, set.plates, exercise).replace(" ", NBSP)
        return "Last time $load${NBSP}×${NBSP}${set.reps}"
    }

    private fun workoutProgressLine(name: String, done: Int, total: Int): String =
        if (total > 0) "$name${NBSP}· $done${NBSP}of${NBSP}$total exercises done" else name

    /** One rendering of one size: the views, the painter and the widths text may take. */
    private class Pass(
        val context: Context,
        val views: RemoteViews,
        val painter: WidgetTextPainter,
        val widthDp: Int,
        val heightDp: Int,
        val nowMillis: Long,
        val elapsedRealtimeMs: Long
    ) {
        val fontScale = context.resources.configuration.fontScale

        /** Sets [value] into the image [id] in [style], at most [maxDp] wide; returns the width it took, in dp. */
        fun text(id: Int, value: String, style: WidgetTextStyle, maxDp: Float, lines: Int = 1): Float {
            val bitmap = painter.render(value, style, maxDp.coerceAtLeast(24f), lines)
            views.setImageViewBitmap(id, bitmap)
            views.setContentDescription(id, value)
            views.setViewVisibility(id, View.VISIBLE)
            return painter.dp(bitmap.width)
        }

        fun gone(vararg ids: Int) = ids.forEach { views.setViewVisibility(it, View.GONE) }

        fun optional(id: Int, value: String?, style: WidgetTextStyle, maxDp: Float, lines: Int = 1) {
            if (value.isNullOrBlank()) gone(id) else text(id, value, style, maxDp, lines)
        }

        // ---- Strip (1 row) ----

        fun bindStrip(model: WidgetModel) {
            // 14 dp padding each side, the 18 dp icon and its 10 dp gap.
            val content = widthDp - 28f
            if (model is WidgetModel.Resting) {
                gone(R.id.strip_normal)
                views.setViewVisibility(R.id.strip_rest, View.VISIBLE)
                val label = text(R.id.strip_rest_label, model.label, LABEL, content / 3)
                val clock = restClock(R.id.strip_rest_chronometer, R.id.strip_rest_static, model, STRIP_STATIC, 22f, content / 2)
                text(R.id.strip_rest_detail, "${model.nextTitle}${NBSP}· ${model.setLine}", STRIP_DETAIL,
                    content - label - 8 - clock - 10)
                return
            }
            gone(R.id.strip_rest)
            views.setViewVisibility(R.id.strip_normal, View.VISIBLE)
            val (primary, meta) = when (model) {
                WidgetModel.Setup -> "Rebuild${NBSP}· Finish setup" to null
                WidgetModel.Unavailable -> context.getString(R.string.widget_open_rebuild) to null
                is WidgetModel.Active -> {
                    val line = if (model.restOver) "${model.restOverLabel ?: "Rest over"}${NBSP}· ${model.workoutName}"
                    else "${model.workoutName}${NBSP}· In progress"
                    line to model.totalExercises.takeIf { it > 0 }
                        ?.let { "${model.doneExercises}${NBSP}of${NBSP}$it done" }
                }
                is WidgetModel.Idle -> model.stripLine to model.weekTotal.takeIf { it > 0 }
                    ?.let { "${model.weekDone}${NBSP}of${NBSP}$it this${NBSP}week" }
                is WidgetModel.Resting -> error("handled above")
            }
            val room = content - 28
            val metaWidth = if (meta != null) text(R.id.strip_meta, meta, STRIP_META, room / 2) + 8 else 0f.also {
                gone(R.id.strip_meta)
            }
            text(R.id.strip_primary, primary, STRIP_PRIMARY, room - metaWidth)
        }

        // ---- Standard (4x2) and expanded (4x3) ----

        fun bindStandard(model: WidgetModel, expanded: Boolean) {
            val content = widthDp - 32f
            gone(R.id.stopwatch_row, R.id.rest_spacer_top)
            when (model) {
                is WidgetModel.Resting -> {
                    showRest(true)
                    val clock = restClock(R.id.rest_chronometer, R.id.rest_static_time, model, STATIC_TIME, 64f, content / 2)
                    text(R.id.rest_label, model.label, LABEL, clock.coerceAtLeast(60f))
                    val column = content - clock - 18
                    text(R.id.rest_next_eyebrow, context.getString(R.string.widget_next), LABEL, column)
                    text(R.id.rest_next_title, model.nextTitle, NEXT_TITLE, column, lines = 2)
                    text(R.id.rest_set_line, model.setLine, SET_LINE, column)
                    optional(R.id.rest_last, lastTimeText(model).takeIf { fontScale <= 1.5f }, LAST, column)
                    if (expanded) {
                        val rows = widgetListRows(heightDp, fontScale, headerDp = 96, footerLines = 2)
                        bindWindow(model.rows, model.currentIndex, rows, content, trailing = true)
                    } else {
                        // 4x2: the countdown alone, centred, with the workout underneath.
                        views.setViewVisibility(R.id.rest_spacer_top, View.VISIBLE)
                        bindRows(emptyList(), content, trailing = true)
                        gone(R.id.list_more)
                    }
                    footer(workoutProgressLine(model.workoutName, model.doneExercises, model.totalExercises), content)
                }
                is WidgetModel.Active -> {
                    showRest(false)
                    gone(R.id.header_meta)
                    val pill = text(R.id.header_resume, context.getString(R.string.widget_resume), PILL, content / 2) + 32 + 12
                    val kicker = if (model.restOver && model.restOverNext != null) {
                        "${model.restOverLabel ?: "Rest over"}${NBSP}· ${model.restOverNext}"
                    } else "In progress${NBSP}· ${model.startedLine}"
                    header(model.workoutName, kicker, accent = true, content - pill)
                    val watch = model.stopwatch
                    var footer: String? = if (expanded) model.loggedSummary else null
                    var rows = widgetListRows(heightDp, fontScale, footerLines = if (footer != null) 2 else 1)
                    if (watch != null && watch.liveClock && watch.chronometerBaseMs != null) {
                        views.setViewVisibility(R.id.stopwatch_row, View.VISIBLE)
                        views.setChronometer(R.id.stopwatch_chronometer, watch.chronometerBaseMs, null, true)
                        views.setChronometerCountDown(R.id.stopwatch_chronometer, false)
                        text(R.id.stopwatch_name, watch.exerciseName.ifBlank { "Stopwatch" }, STOPWATCH_NAME, content - 64)
                        rows = (rows - 1).coerceAtLeast(1)
                    } else if (watch != null) {
                        footer = watch.statusLine
                        if (!expanded) rows = (rows - 1).coerceAtLeast(1)
                    }
                    bindWindow(model.rows, model.currentIndex, rows, content, trailing = true)
                    footer(footer, content)
                }
                is WidgetModel.Idle -> {
                    showRest(false)
                    gone(R.id.header_resume)
                    // Lines under the list: the note (up to two), and "+N more" when the preview can't show.
                    val noteLines = if (model.note != null) 2 else 0
                    val capacity = widgetListRows(heightDp, fontScale, footerLines = noteLines)
                    // A short week leaves room under its list: preview the next workout (or recap the last), whole or
                    // not at all. Past days give way first when that makes it fit.
                    var rows = model.rows
                    if (model.extra.isNotEmpty() && rows.size + model.extraMinRows > capacity) {
                        rows = rows.filterNot { it.past }
                    }
                    val room = capacity - rows.size
                    val extra = if (model.extra.isNotEmpty() && room >= model.extraMinRows) model.extra.take(room) else {
                        rows = model.rows
                        emptyList()
                    }
                    if (extra.isNotEmpty()) {
                        // The section names what comes next, so the subtitle states the week instead of repeating it.
                        gone(R.id.header_meta)
                        val kicker = if (model.kind == TodayKind.WEEK_COMPLETE) model.detail else model.weekLine ?: model.detail
                        header(model.title, kicker, accent = false, content)
                        bindRows(rows + extra, content, trailing = true)
                        more(0, content)
                    } else {
                        // At large text the week count gives way, so the line saying what's next isn't squeezed out.
                        val meta = model.weekLine?.takeIf { fontScale <= 1.3f }
                            ?.let { text(R.id.header_meta, it, META, content / 2) + 12 }
                            ?: 0f.also { gone(R.id.header_meta) }
                        val kicker = if (model.rowsAreExercises) "Today${NBSP}· ${model.detail}" else model.detail
                        header(model.title, kicker, accent = false, content - meta)
                        bindHead(rows, widgetListRows(heightDp, fontScale, footerLines = noteLines + 1), content, trailing = true)
                    }
                    footer(model.note, content)
                }
                WidgetModel.Setup, WidgetModel.Unavailable -> bindPlain(model, content, TITLE)
            }
        }

        // ---- Compact (2x2) ----

        fun bindCompact(model: WidgetModel) {
            val content = widthDp - 28f
            val scale = (fontScale * 0.75f).coerceAtLeast(1f)
            gone(R.id.rest_next_title, R.id.rest_set_line, R.id.rest_last)
            when (model) {
                is WidgetModel.Resting -> {
                    showRest(true)
                    gone(R.id.header_resume, R.id.list_more)
                    restClock(R.id.rest_chronometer, R.id.rest_static_time, model, STATIC_TIME, 58f, content)
                    text(R.id.rest_label, model.label, LABEL, content)
                    // From the cell's real height, under the label and the countdown: the exercise, then its set, then
                    // last time, then a second line for a long name. What doesn't fit is left out, never clipped.
                    val clockDp = painter.dp(TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_SP, 58f * 1.15f, context.resources.displayMetrics).toInt())
                    var room = heightDp - 28 - 18 * scale - clockDp
                    var titleLines = 0
                    if (room >= 20 * scale) { titleLines = 1; room -= 20 * scale }
                    val showSet = room >= 18 * scale
                    if (showSet) room -= 18 * scale
                    val last = lastTimeText(model)?.takeIf { fontScale <= 1.3f && room >= 17 * scale }
                    if (last != null) room -= 17 * scale
                    if (titleLines == 1 && room >= 20 * scale) titleLines = 2
                    if (titleLines > 0) {
                        text(R.id.rest_next_title, model.nextTitle, NEXT_TITLE_COMPACT, content, lines = titleLines)
                    }
                    if (showSet) text(R.id.rest_set_line, model.setLine, SET_LINE_COMPACT, content)
                    optional(R.id.rest_last, last, LAST_COMPACT, content)
                    bindRows(emptyList(), content, trailing = false)
                    footer(null, content)
                }
                is WidgetModel.Active -> {
                    showRest(false)
                    val meta = model.totalExercises.takeIf { it > 0 }?.let { "${model.doneExercises}/$it" }
                    compactHeader(model.workoutName, if (model.restOver) (model.restOverLabel ?: "Rest over") else "In progress",
                        accent = true, meta = meta, content = content)
                    text(R.id.header_resume, context.getString(R.string.widget_resume), PILL, content - 16)
                    val rows = widgetCompactRows(heightDp, fontScale, belowDp = 40)
                    bindWindow(model.rows, model.currentIndex, rows, content, trailing = false)
                    gone(R.id.list_more)
                    footer(null, content)
                }
                is WidgetModel.Idle -> {
                    showRest(false)
                    gone(R.id.header_resume)
                    compactHeader(model.title, model.dateLabel, accent = false, meta = null, content = content)
                    val rows = widgetCompactRows(heightDp, fontScale, belowDp = 18)
                    bindHead(model.rows, rows, content, trailing = false, showMore = false)
                    footer(when {
                        model.rows.isEmpty() -> model.detail
                        model.weekTotal > 0 -> "${model.weekDone}${NBSP}of${NBSP}${model.weekTotal} done this${NBSP}week"
                        else -> null
                    }, content, compact = true)
                }
                WidgetModel.Setup, WidgetModel.Unavailable -> bindPlain(model, content, TITLE_COMPACT)
            }
        }

        private fun compactHeader(title: String, kicker: String, accent: Boolean, meta: String?, content: Float) {
            val metaWidth = if (meta != null) text(R.id.header_meta, meta, KICKER_COMPACT, content / 3) + 6 else 0f.also {
                gone(R.id.header_meta)
            }
            kicker(kicker, accent, KICKER_COMPACT, content - metaWidth)
            text(R.id.header_title, title, TITLE_COMPACT, content)
        }

        private fun bindPlain(model: WidgetModel, content: Float, titleStyle: WidgetTextStyle) {
            showRest(false)
            val setup = model == WidgetModel.Setup
            gone(R.id.header_meta, R.id.header_resume, R.id.list_more)
            text(R.id.header_title, if (setup) "Finish setup" else context.getString(R.string.widget_open_rebuild),
                titleStyle, content)
            kicker("Rebuild", accent = false, KICKER, content)
            bindRows(emptyList(), content, trailing = true)
            footer(if (setup) "Choose your return phase to see today's workout." else null, content)
        }

        private fun header(title: String, kicker: String, accent: Boolean, width: Float) {
            text(R.id.header_title, title, TITLE, width)
            kicker(kicker, accent, if (accent) KICKER_ACCENT else KICKER, width)
        }

        private fun kicker(value: String, accent: Boolean, style: WidgetTextStyle, width: Float) {
            text(if (accent) R.id.header_kicker_accent else R.id.header_kicker, value, style, width)
            gone(if (accent) R.id.header_kicker else R.id.header_kicker_accent)
        }

        private fun footer(value: String?, content: Float, compact: Boolean = false) =
            optional(R.id.footer, value, if (compact) SMALL_COMPACT else SMALL, content, lines = 2)

        private fun showRest(rest: Boolean) {
            views.setViewVisibility(R.id.list_header, if (rest) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.rest_hero, if (rest) View.VISIBLE else View.GONE)
        }

        /** The first [count] rows, then "+N more" for the rest. */
        private fun bindHead(rows: List<WidgetRow>, count: Int, content: Float, trailing: Boolean, showMore: Boolean = true) {
            bindRows(rows.take(count), content, trailing)
            more(if (showMore) rows.size - count else 0, content)
        }

        /** [count] rows around [focus] so the current exercise never scrolls away, then "+N more" for those after. */
        private fun bindWindow(rows: List<WidgetRow>, focus: Int, count: Int, content: Float, trailing: Boolean) {
            val window = widgetWindow(rows.size, focus, count)
            bindRows(rows.slice(window), content, trailing)
            more(rows.size - (window.last + 1), content)
        }

        private fun more(count: Int, content: Float) =
            optional(R.id.list_more, if (count > 0) "+$count more" else null, SMALL, content - 34)

        private fun bindRows(rows: List<WidgetRow>, content: Float, trailing: Boolean) {
            views.setViewVisibility(R.id.rows, if (rows.isEmpty()) View.GONE else View.VISIBLE)
            // The compact layout carries three row slots without trailing text; the standard one seven with it.
            val slots = if (trailing) ROWS else ROWS.take(COMPACT_ROW_SLOTS)
            slots.forEachIndexed { i, ids ->
                val row = rows.getOrNull(i)
                if (row == null) {
                    gone(ids.row)
                    return@forEachIndexed
                }
                views.setViewVisibility(ids.row, View.VISIBLE)
                views.setContentDescription(ids.row, row.spoken)
                if (row.mark == WidgetMark.SECTION || row.mark == WidgetMark.PLAIN) {
                    // Headings and recap lines span the row from the left edge, without a mark column or a value.
                    gone(ids.mark)
                    if (trailing) gone(ids.trail)
                    val section = row.mark == WidgetMark.SECTION
                    gone(if (section) ids.name else ids.nameMuted)
                    text(if (section) ids.nameMuted else ids.name, row.name, if (section) LABEL else ROW_NAME, content)
                    return@forEachIndexed
                }
                views.setViewVisibility(ids.mark, View.VISIBLE)
                val markId = when (row.mark) {
                    WidgetMark.NUMBER -> ids.num
                    WidgetMark.CURRENT -> ids.cur
                    WidgetMark.DONE -> ids.done
                    else -> ids.day
                }
                listOf(ids.num, ids.cur, ids.done, ids.day).filter { it != markId }.forEach { gone(it) }
                when (row.mark) {
                    WidgetMark.DONE -> views.setViewVisibility(markId, View.VISIBLE)
                    WidgetMark.DAY -> text(markId, row.markText, DAY, 32f)
                    else -> text(markId, row.markText, BADGE, 18f)
                }
                val strong = row.mark == WidgetMark.CURRENT || (row.today && !row.muted)
                val trail = if (trailing && row.trailing.isNotBlank()) {
                    text(ids.trail, row.trailing, if (strong) TRAIL_STRONG else TRAIL, content / 2) + 8
                } else 0f.also { if (trailing) gone(ids.trail) }
                gone(if (row.muted) ids.name else ids.nameMuted)
                text(if (row.muted) ids.nameMuted else ids.name, row.name, if (strong) ROW_NAME_STRONG else ROW_NAME,
                    content - 34 - trail)
            }
        }

        /**
         * The rest's live countdown, or its static end time when exact alarms are off; returns the width it takes. The
         * countdown is a platform Chronometer the launcher ticks, so it alone stays in the system condensed face, and
         * its width is measured in that face.
         */
        fun restClock(
            chronometerId: Int,
            staticId: Int,
            model: WidgetModel.Resting,
            staticStyle: WidgetTextStyle,
            clockSp: Float,
            maxDp: Float
        ): Float {
            if (model.liveCountdown) {
                // +999ms matches RestBar's ceiling rounding so the widget agrees with the app to the second and never
                // sits on 00:00 during the final second.
                val base = elapsedRealtimeMs + (model.deadline - nowMillis) + 999L
                views.setViewVisibility(chronometerId, View.VISIBLE)
                gone(staticId)
                views.setChronometer(chronometerId, base, null, true)
                views.setChronometerCountDown(chronometerId, true)
                val paint = Paint().apply {
                    typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                    textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, clockSp, context.resources.displayMetrics)
                    fontFeatureSettings = "tnum"
                }
                return painter.dp(paint.measureText("00:00").toInt() + 1)
            }
            gone(chronometerId)
            return text(staticId, "Until${NBSP}${model.endTimeText}", staticStyle, maxDp, lines = 2)
        }
    }
}
