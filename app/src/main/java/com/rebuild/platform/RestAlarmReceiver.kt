package com.rebuild.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rebuild.RebuildApplication
import com.rebuild.domain.Action
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == RestAlarmScheduler.ACTION_STOP) {
            (context.applicationContext as RebuildApplication).alarms.stopRinging(always = true)
            return
        }
        val action = intent.action
        if (action != RestAlarmScheduler.ACTION_EXPIRE && action != RestAlarmScheduler.ACTION_ADD_30 &&
            action != RestAlarmScheduler.ACTION_SKIP) return
        val timerId = intent.getStringExtra(RestAlarmScheduler.EXTRA_TIMER_ID) ?: return
        val pending = goAsync()
        val app = context.applicationContext as RebuildApplication
        app.applicationScope.launch {
            try {
                val repository = app.repository()
                val state = repository.state.value
                val timer = state.restTimer
                val now = System.currentTimeMillis()
                when (action) {
                    // The countdown notification's buttons act only on the rest they were shown for; a stale tap on
                    // an older notification does nothing. The saved state then redraws the notification.
                    RestAlarmScheduler.ACTION_ADD_30 -> if (timer?.id == timerId) {
                        repository.execute(Action.AdjustRest(timer.id, maxOf(timer.deadline + 30_000, now + 1_000)))
                    }
                    RestAlarmScheduler.ACTION_SKIP -> if (timer?.id == timerId) repository.execute(Action.SkipRest)
                    else -> {
                        val workoutId = timer?.workoutId
                        val about = timer?.takeIf { it.id == timerId }?.let { restContext(state, it) }
                        if (repository.consumeRestTimer(timerId, now)) {
                            app.alarms.notifyExpired(shortPause = timer?.id == timerId && timer.shortPause, about = about,
                                endedAt = timer?.deadline ?: now) {
                                repository.state.value.let { it.activeWorkoutId == workoutId && it.restTimer == null }
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.e("Rebuild", "Rest alarm could not read/commit saved state", error)
                app.alarms.reportSchedulingFailure()
            } finally {
                pending.finish()
            }
        }
    }
}

class RestRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
            )) return
        val pending = goAsync()
        val app = context.applicationContext as RebuildApplication
        app.applicationScope.launch {
            try {
                val repository = app.repository()
                recoverRestAlarm(repository, System.currentTimeMillis()) { _, _ ->
                    app.alarms.reconcileCurrent(repository)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.e("Rebuild", "Unable to recover rest alarm", error)
                app.alarms.reportSchedulingFailure()
            } finally {
                pending.finish()
            }
        }
    }
}
