package com.rebuild.platform

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rebuild.MainActivity
import com.rebuild.R
import com.rebuild.domain.AppRepository
import com.rebuild.domain.RestTimer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AlarmPrecision { EXACT, BEST_EFFORT }

fun alarmPrecision(sdk: Int, exactAllowed: Boolean): AlarmPrecision =
    if (sdk < 31 || exactAllowed) AlarmPrecision.EXACT else AlarmPrecision.BEST_EFFORT

class RestAlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val _failure = MutableStateFlow<String?>(null)
    val failure = _failure.asStateFlow()

    init {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.rest_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.rest_channel_description)
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 120, 200)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(COUNTDOWN_CHANNEL_ID, context.getString(R.string.rest_countdown_channel_name),
                NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.rest_countdown_channel_description)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
    }

    fun canUseExactAlarms(): Boolean = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()

    fun notificationsAllowed(): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            notificationManager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE

    @Synchronized
    fun reconcileCurrent(repository: AppRepository) {
        // A queued lifecycle callback must not replace a newer alarm with its captured snapshot.
        val current = repository.state.value
        reconcile(current.restTimer, current.activeWorkoutId)
    }

    @Synchronized
    fun reconcile(timer: RestTimer?, activeWorkoutId: String?, now: Long = System.currentTimeMillis()) {
        val pending = pendingIntent(timer?.id)
        alarmManager.cancel(pending)
        if (activeWorkoutId == null || timer != null) notificationManager.cancel(NOTIFICATION_ID)
        if (timer == null || activeWorkoutId != timer.workoutId || timer.deadline <= now) {
            notificationManager.cancel(COUNTDOWN_NOTIFICATION_ID)
            return
        }
        _failure.value = null
        showCountdown(timer, now)
        when (alarmPrecision(Build.VERSION.SDK_INT, canUseExactAlarms())) {
            AlarmPrecision.EXACT -> {
                try {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer.deadline, pending)
                } catch (error: SecurityException) {
                    Log.w("Rebuild", "Exact alarm access changed; using best effort", error)
                    _failure.value = "Exact alarm access changed. Rest alerts are now best effort; on-screen timing still works."
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer.deadline, pending)
                }
            }
            AlarmPrecision.BEST_EFFORT ->
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer.deadline, pending)
        }
    }

    fun reportSchedulingFailure() {
        _failure.value = "Android could not schedule a background rest alert. Keep Rebuild visible and check battery and alarm access."
    }

    @Synchronized
    fun notifyExpired(shortPause: Boolean = false, stillRelevant: () -> Boolean = { true }) {
        if (!stillRelevant() || !notificationsAllowed()) return
        val open = PendingIntent.getActivity(
            context, 2, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            notificationManager.notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_rest)
                    .setContentTitle(context.getString(if (shortPause) R.string.pause_complete else R.string.rest_complete))
                    .setContentText(context.getString(
                        if (shortPause) R.string.pause_complete_body else R.string.rest_complete_body))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .build()
            )
        } catch (error: SecurityException) {
            Log.w("Rebuild", "Notification permission changed at expiry", error)
            _failure.value = "Android blocked the rest notification. Check notification permission."
        }
    }

    /**
     * The running rest as an ongoing, silent countdown, like a clock app's timer, so it stays in view from the
     * status bar and lock screen while Rebuild is in the background. It removes itself at the deadline.
     */
    private fun showCountdown(timer: RestTimer, now: Long) {
        if (!notificationsAllowed()) return
        val open = PendingIntent.getActivity(
            context, 3, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            notificationManager.notify(
                COUNTDOWN_NOTIFICATION_ID,
                NotificationCompat.Builder(context, COUNTDOWN_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_rest)
                    .setContentTitle(context.getString(if (timer.shortPause) R.string.pause_running else R.string.rest_running))
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setWhen(timer.deadline)
                    .setShowWhen(true)
                    .setTimeoutAfter(timer.deadline - now)
                    .setContentIntent(open)
                    .setOngoing(true)
                    .setSilent(true)
                    .setOnlyAlertOnce(true)
                    .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .build()
            )
        } catch (error: SecurityException) {
            Log.w("Rebuild", "Notification permission changed before the rest countdown", error)
        }
    }

    private fun pendingIntent(timerId: String?): PendingIntent =
        PendingIntent.getBroadcast(
            context, 1,
            Intent(context, RestAlarmReceiver::class.java).setAction(ACTION_EXPIRE).putExtra(EXTRA_TIMER_ID, timerId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        const val ACTION_EXPIRE = "com.rebuild.REST_EXPIRED"
        const val EXTRA_TIMER_ID = "timer_id"
        const val CHANNEL_ID = "rest-complete"
        const val NOTIFICATION_ID = 21
        const val COUNTDOWN_CHANNEL_ID = "rest-countdown"
        const val COUNTDOWN_NOTIFICATION_ID = 22
    }
}
