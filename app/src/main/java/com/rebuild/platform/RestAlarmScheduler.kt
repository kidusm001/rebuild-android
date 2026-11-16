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
import com.rebuild.ui.RestSound
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.SystemClock
import android.widget.RemoteViews
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AlarmPrecision { EXACT, BEST_EFFORT }

fun alarmPrecision(sdk: Int, exactAllowed: Boolean): AlarmPrecision =
    if (sdk < 31 || exactAllowed) AlarmPrecision.EXACT else AlarmPrecision.BEST_EFFORT

class RestAlarmScheduler(
    private val context: Context,
    /** The sound the user chose for a rest's end; read at the moment it ends. */
    private val sound: () -> RestSound = { RestSound.BUZZ }
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val _failure = MutableStateFlow<String?>(null)
    val failure = _failure.asStateFlow()
    private val _ringingSince = MutableStateFlow<Long?>(null)
    /** When the rest alarm started ringing, or null while it is quiet; the app shows its Stop bar from this. */
    val ringingSince = _ringingSince.asStateFlow()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val ringOut = Runnable { _ringingSince.value = null }

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
        // Android fixes a channel's sound and vibration once it exists, so each choice has a channel of its own.
        // "rest-complete" stays the short buzz it always was, keeping any setting already made to it.
        notificationManager.createNotificationChannel(
            NotificationChannel(LONG_CHANNEL_ID, context.getString(R.string.rest_long_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.rest_long_channel_description)
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = LONG_BUZZ
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(CHIME_CHANNEL_ID, context.getString(R.string.rest_chime_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.rest_chime_channel_description)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 120, 200)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(ALARM_CHANNEL_ID, context.getString(R.string.rest_alarm_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.rest_alarm_channel_description)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 300, 500, 300, 500)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(SILENT_CHANNEL_ID, context.getString(R.string.rest_silent_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.rest_silent_channel_description)
                setSound(null, null)
                enableVibration(false)
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
            notificationManager.getNotificationChannel(channelFor(sound()))?.importance != NotificationManager.IMPORTANCE_NONE

    @Synchronized
    fun reconcileCurrent(repository: AppRepository) {
        // A queued lifecycle callback must not replace a newer alarm with its captured snapshot.
        val current = repository.state.value
        reconcile(current.restTimer, current.activeWorkoutId, about = current.restTimer?.let { restContext(current, it) })
    }

    @Synchronized
    fun reconcile(
        timer: RestTimer?,
        activeWorkoutId: String?,
        now: Long = System.currentTimeMillis(),
        about: RestContext? = null
    ) {
        val pending = pendingIntent(timer?.id)
        alarmManager.cancel(pending)
        if (activeWorkoutId == null || timer != null) {
            notificationManager.cancel(NOTIFICATION_ID)
            quiet()
        }
        if (timer == null || activeWorkoutId != timer.workoutId || timer.deadline <= now) {
            notificationManager.cancel(COUNTDOWN_NOTIFICATION_ID)
            return
        }
        _failure.value = null
        showCountdown(timer, now, about)
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
    fun notifyExpired(
        shortPause: Boolean = false,
        about: RestContext? = null,
        endedAt: Long = System.currentTimeMillis(),
        stillRelevant: () -> Boolean = { true }
    ) {
        if (!stillRelevant() || !notificationsAllowed()) return
        // A rest-pause's short pause comes round every few seconds; ringing until stopped each time would need a free
        // hand mid-set, so a short pause buzzes instead of ringing.
        val chosen = sound().let { if (shortPause && it == RestSound.ALARM) RestSound.BUZZ else it }
        val open = PendingIntent.getActivity(
            context, 2, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (chosen == RestSound.ALARM && !notificationsAllowedOn(ALARM_CHANNEL_ID)) return
        // "Rest over" leads, what to do now follows ("Next: set 2 of 3"), and the exercise sits in the header beside
        // the time it ended, so the shade and lock screen answer "what now?" without opening the app.
        val title = context.getString(if (shortPause) R.string.pause_complete else R.string.rest_complete)
        // The amber card: "REST OVER" small, the next set big ("Set 2"), and what it belongs to when expanded. The
        // plain title and text stay set for the lock screen's redacted form, watches and screen readers.
        fun card(layout: Int) = RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.rest_over_label, title.uppercase())
            setTextViewText(R.id.rest_over_headline, about?.headline ?: title)
            if (layout == R.layout.notification_rest_over_big) setTextViewText(R.id.rest_over_detail, about?.detail ?: "")
        }
        val builder = NotificationCompat.Builder(context, channelFor(chosen))
            .setSmallIcon(R.drawable.ic_rest)
            .setColor(SIGNAL_AMBER)
            .setContentTitle(title)
            .setContentText(about?.next)
            .setSubText(about?.exercise)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(card(R.layout.notification_rest_over))
            .setCustomBigContentView(card(R.layout.notification_rest_over_big))
            .setCustomHeadsUpContentView(card(R.layout.notification_rest_over))
            .setWhen(endedAt)
            .setShowWhen(true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(if (chosen == RestSound.ALARM) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        if (chosen == RestSound.ALARM) {
            // Rings until stopped: from the notification, by opening Rebuild, or on its own after a few minutes so a
            // phone left in a locker doesn't ring for the rest of the day.
            // Public, so its Stop action shows on the lock screen too.
            builder.setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setTimeoutAfter(ALARM_LIMIT_MS)
                .addAction(0, context.getString(R.string.rest_alarm_stop), stopIntent())
        } else {
            builder.setOnlyAlertOnce(true)
        }
        try {
            val notification = builder.build()
            if (chosen == RestSound.ALARM) notification.flags = notification.flags or android.app.Notification.FLAG_INSISTENT
            notificationManager.notify(NOTIFICATION_ID, notification)
            if (chosen == RestSound.ALARM) {
                _ringingSince.value = System.currentTimeMillis()
                main.removeCallbacks(ringOut)
                main.postDelayed(ringOut, ALARM_LIMIT_MS)
            } else quiet()
        } catch (error: SecurityException) {
            Log.w("Rebuild", "Notification permission changed at expiry", error)
            _failure.value = "Android blocked the rest notification. Check notification permission."
        }
    }

    /**
     * Silences a ringing rest alarm by removing its notification. Opening Rebuild calls this with the alarm chosen;
     * its Stop button and the in-app Stop pass [always], since the choice may have changed while it rang.
     */
    fun stopRinging(always: Boolean = false) {
        if (always || sound() == RestSound.ALARM || _ringingSince.value != null) {
            notificationManager.cancel(NOTIFICATION_ID)
            quiet()
        }
    }

    private fun quiet() {
        main.removeCallbacks(ringOut)
        _ringingSince.value = null
    }

    private fun notificationsAllowedOn(channel: String): Boolean =
        notificationManager.getNotificationChannel(channel)?.importance != NotificationManager.IMPORTANCE_NONE

    private fun stopIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, 4, Intent(context, RestAlarmReceiver::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /**
     * The running rest as an ongoing, silent countdown, like a clock app's timer, so it stays in view from the
     * status bar and lock screen while Rebuild is in the background. It removes itself at the deadline.
     */
    private fun showCountdown(timer: RestTimer, now: Long, about: RestContext?) {
        if (!notificationsAllowed()) return
        val open = PendingIntent.getActivity(
            context, 3, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val label = context.getString(if (timer.shortPause) R.string.pause_running else R.string.rest_running)
        fun countdown(layout: Int) = RemoteViews(context.packageName, layout).apply {
            setChronometer(R.id.rest_countdown, SystemClock.elapsedRealtime() + (timer.deadline - now), null, true)
            setChronometerCountDown(R.id.rest_countdown, true)
            setTextViewText(R.id.rest_title, about?.exercise ?: label)
            setTextViewText(R.id.rest_next, about?.next ?: "")
        }
        try {
            notificationManager.notify(
                COUNTDOWN_NOTIFICATION_ID,
                NotificationCompat.Builder(context, COUNTDOWN_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_rest)
                    .setColor(SIGNAL_AMBER)
                    .setContentTitle(about?.exercise ?: label)
                    .setContentText(about?.next)
                    .setSubText(label)
                    // The time large and live in the accent, the exercise and what's next beside it; it counts down on
                    // its own, so nothing has to update it every second.
                    .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                    .setCustomContentView(countdown(R.layout.notification_rest_countdown))
                    .setCustomBigContentView(countdown(R.layout.notification_rest_countdown_big))
                    // The rest bar's two controls, so a rest can be stretched or ended from the lock screen.
                    .addAction(0, context.getString(R.string.rest_add_30), restIntent(ACTION_ADD_30, timer.id, 5))
                    .addAction(0, context.getString(R.string.rest_skip), restIntent(ACTION_SKIP, timer.id, 6))
                    .setWhen(timer.deadline)
                    .setShowWhen(false)
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

    private fun restIntent(action: String, timerId: String, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
        context, requestCode,
        Intent(context, RestAlarmReceiver::class.java).setAction(action).putExtra(EXTRA_TIMER_ID, timerId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun pendingIntent(timerId: String?): PendingIntent =
        PendingIntent.getBroadcast(
            context, 1,
            Intent(context, RestAlarmReceiver::class.java).setAction(ACTION_EXPIRE).putExtra(EXTRA_TIMER_ID, timerId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        const val ACTION_EXPIRE = "com.rebuild.REST_EXPIRED"
        const val ACTION_STOP = "com.rebuild.REST_ALARM_STOP"
        const val ACTION_ADD_30 = "com.rebuild.REST_ADD_30"
        const val ACTION_SKIP = "com.rebuild.REST_SKIP"
        /** The rest bar's signal amber, as the notifications' accent. */
        const val SIGNAL_AMBER = 0xFFFFB224.toInt()
        const val LONG_CHANNEL_ID = "rest-complete-long"
        const val CHIME_CHANNEL_ID = "rest-complete-chime"
        const val ALARM_CHANNEL_ID = "rest-complete-alarm"
        const val SILENT_CHANNEL_ID = "rest-complete-silent"
        const val ALARM_LIMIT_MS = 5 * 60_000L
        private val LONG_BUZZ = longArrayOf(0, 700, 250, 700, 250, 700, 250, 700, 250, 700)

        fun channelFor(sound: RestSound): String = when (sound) {
            RestSound.BUZZ -> CHANNEL_ID
            RestSound.LONG_BUZZ -> LONG_CHANNEL_ID
            RestSound.CHIME -> CHIME_CHANNEL_ID
            RestSound.ALARM -> ALARM_CHANNEL_ID
            RestSound.SILENT -> SILENT_CHANNEL_ID
        }
        const val EXTRA_TIMER_ID = "timer_id"
        const val CHANNEL_ID = "rest-complete"
        const val NOTIFICATION_ID = 21
        const val COUNTDOWN_CHANNEL_ID = "rest-countdown"
        const val COUNTDOWN_NOTIFICATION_ID = 22
    }
}
