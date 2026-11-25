package com.rebuild.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong
import android.util.Log
import com.rebuild.RebuildApplication
import com.rebuild.domain.AppState
import com.rebuild.domain.WidgetModel
import com.rebuild.domain.nextMidnightMillis
import com.rebuild.domain.widgetModel
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Rebuild's adaptive home screen widget ("Today"). Read-only: every tap opens Rebuild cleanly without mutating
 * workout state, and all layout/state rules come from [com.rebuild.domain.widgetModel].
 */
class RebuildWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        // Arm midnight first so a transient timeout or error during render never drops the next rollover.
        WidgetUpdater.scheduleMidnight(context)
        runAsync(context) {
            WidgetUpdater.refreshFromRepository(context, appWidgetIds)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        // Text is set by the app at the cell's exact width, so every resize re-renders (on all API levels).
        runAsync(context) {
            WidgetUpdater.refreshOne(context, appWidgetId, newOptions)
        }
    }

    override fun onEnabled(context: Context) {
        WidgetUpdater.scheduleMidnight(context)
    }

    override fun onDisabled(context: Context) {
        WidgetUpdater.cancelMidnight(context)
    }

    private fun runAsync(context: Context, block: suspend () -> Unit) {
        val pending = goAsync()
        val app = context.applicationContext as? RebuildApplication
        if (app == null) {
            pending?.finish()
            return
        }
        app.applicationScope.launch {
            try {
                withTimeout(BROADCAST_TIMEOUT_MS) { block() }
            } catch (error: TimeoutCancellationException) {
                Log.w("Rebuild", "Widget update timed out; keeping existing views", error)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.e("Rebuild", "Widget update failed; keeping existing views", error)
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        private const val BROADCAST_TIMEOUT_MS = 8_000L
    }
}

/**
 * Dedicated receiver for date/time/boot/locale/midnight widget refreshes, kept separate from [RestRecoveryReceiver]
 * so widget updates can never delay or disrupt rest-alarm recovery.
 */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return
        if (!WidgetUpdater.hasWidgets(context)) {
            WidgetUpdater.cancelMidnight(context)
            return
        }
        WidgetUpdater.scheduleMidnight(context)
        val pending = goAsync()
        val app = context.applicationContext as? RebuildApplication
        if (app == null) {
            pending?.finish()
            return
        }
        app.applicationScope.launch {
            try {
                withTimeout(8_000L) {
                    WidgetUpdater.refreshFromRepository(context)
                }
            } catch (error: TimeoutCancellationException) {
                Log.w("Rebuild", "Widget refresh timed out", error)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.e("Rebuild", "Widget refresh failed; keeping existing views", error)
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        const val ACTION_MIDNIGHT = "com.rebuild.WIDGET_MIDNIGHT"
        private val HANDLED_ACTIONS = setOf(
            ACTION_MIDNIGHT,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        )
    }
}

/**
 * Orders widget publishes by when their state was read. Resizes, rest expiry, resume, broadcasts and state changes all
 * render on their own; a slow render of older state must never replace a newer one (a resize that read a running rest
 * landing after the expiry refresh would leave a countdown running past zero).
 */
class WidgetPublishGate {
    private val reads = AtomicLong()
    private val published = HashMap<Int, Long>()

    /** Call right when saved state is read; the stamp goes with whatever is rendered from it. */
    fun markRead(): Long = reads.incrementAndGet()

    /** Publishes through [publish] unless widget [id] already shows something read later than [read]. */
    fun admit(id: Int, read: Long, publish: () -> Unit): Boolean = synchronized(published) {
        if (read < (published[id] ?: 0L)) return false
        published[id] = read
        publish()
        true
    }
}

object WidgetUpdater {
    private const val MIDNIGHT_REQUEST_CODE = 110
    val gate = WidgetPublishGate()

    fun componentName(context: Context): ComponentName =
        ComponentName(context, RebuildWidgetProvider::class.java)

    fun widgetIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context)?.getAppWidgetIds(componentName(context)) ?: IntArray(0)

    fun hasWidgets(context: Context): Boolean = widgetIds(context).isNotEmpty()

    fun modelFor(
        context: Context,
        state: AppState,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): WidgetModel {
        val app = context.applicationContext as? RebuildApplication
        val exactAllowed = app?.alarms?.canUseExactAlarms() ?: true
        val monotonic = if (state.stopwatch?.running == true) PhoneClock.read(context) else null
        return state.widgetModel(
            nowMillis = nowMillis,
            zone = zone,
            locale = locale,
            exactAlarmsAllowed = exactAllowed,
            monotonic = monotonic
        )
    }

    /** Renders [model] into every widget; [read] stamps when the state behind it was read (see [WidgetPublishGate]). */
    fun renderAll(
        context: Context,
        model: WidgetModel,
        ids: IntArray = widgetIds(context),
        nowMillis: Long = System.currentTimeMillis(),
        elapsedRealtimeMs: Long = SystemClock.elapsedRealtime(),
        read: Long = gate.markRead()
    ) {
        if (ids.isEmpty()) return
        val manager = AppWidgetManager.getInstance(context) ?: return
        for (id in ids) {
            val options = manager.getAppWidgetOptions(id)
            val views = RebuildWidgetRenderer.buildResponsive(context, model, options, nowMillis, elapsedRealtimeMs)
            gate.admit(id, read) { manager.updateAppWidget(id, views) }
        }
    }

    /** Pass [read] from right where [state] was read, when that wasn't just now. */
    fun refreshFromState(
        context: Context,
        state: AppState,
        ids: IntArray = widgetIds(context),
        nowMillis: Long = System.currentTimeMillis(),
        read: Long = gate.markRead()
    ) {
        if (ids.isEmpty()) return
        renderAll(context, modelFor(context, state, nowMillis), ids, nowMillis, read = read)
    }

    suspend fun refreshFromRepository(context: Context, ids: IntArray = widgetIds(context)) {
        if (ids.isEmpty()) return
        val app = context.applicationContext as? RebuildApplication ?: return
        val repository = try {
            app.repository()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.e("Rebuild", "Widget could not open saved workouts", error)
            renderAll(context, WidgetModel.Unavailable, ids)
            return
        }
        val state = repository.state.value
        refreshFromState(context, state, ids, read = gate.markRead())
    }

    suspend fun refreshOne(context: Context, appWidgetId: Int, options: Bundle) {
        val app = context.applicationContext as? RebuildApplication ?: return
        val manager = AppWidgetManager.getInstance(context) ?: return
        val now = System.currentTimeMillis()
        var read = gate.markRead()
        val model = try {
            val state = app.repository().state.value
            read = gate.markRead()
            modelFor(context, state, now)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.e("Rebuild", "Widget resize could not open saved workouts", error)
            WidgetModel.Unavailable
        }
        val views = RebuildWidgetRenderer.buildResponsive(context, model, options, now)
        gate.admit(appWidgetId, read) { manager.updateAppWidget(appWidgetId, views) }
    }

    /**
     * Schedules an inexact, non-wakeup alarm just after local midnight so day rollovers refresh cleanly on the next
     * wake without burning battery or spending the Doze idle quota reserved for rest alerts.
     */
    fun scheduleMidnight(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = nextMidnightMillis(nowMillis, zone) + 1_000L
        manager.set(AlarmManager.RTC, triggerAt, midnightPendingIntent(context))
    }

    fun cancelMidnight(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(midnightPendingIntent(context))
    }

    private fun midnightPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            MIDNIGHT_REQUEST_CODE,
            Intent(context, WidgetRefreshReceiver::class.java).setAction(WidgetRefreshReceiver.ACTION_MIDNIGHT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
