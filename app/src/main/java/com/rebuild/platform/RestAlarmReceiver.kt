package com.rebuild.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rebuild.RebuildApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RestAlarmScheduler.ACTION_EXPIRE) return
        val timerId = intent.getStringExtra(RestAlarmScheduler.EXTRA_TIMER_ID) ?: return
        val pending = goAsync()
        val app = context.applicationContext as RebuildApplication
        app.applicationScope.launch {
            try {
                val repository = app.repository()
                val timer = repository.state.value.restTimer
                val workoutId = timer?.workoutId
                if (repository.consumeRestTimer(timerId, System.currentTimeMillis())) {
                    app.alarms.notifyExpired(shortPause = timer?.id == timerId && timer.shortPause) {
                        repository.state.value.let { it.activeWorkoutId == workoutId && it.restTimer == null }
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
