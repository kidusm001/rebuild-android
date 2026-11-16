package com.rebuild.platform

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.rebuild.data.RebuildRepository
import com.rebuild.data.SnapshotPersistence
import com.rebuild.domain.Action
import com.rebuild.domain.Phase
import com.rebuild.domain.RestTimer
import com.rebuild.domain.availableTemplates
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RestAlarmSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val manager = context.getSystemService(AlarmManager::class.java)
    private val scheduler = RestAlarmScheduler(context)
    private val now = 1_000_000L
    private val timer = RestTimer("timer", "workout", "exercise", now + 180_000L)

    @Test fun permissionDenialUsesBestEffortWithoutBlockingTimer() {
        assertEquals(AlarmPrecision.EXACT, alarmPrecision(30, false))
        assertEquals(AlarmPrecision.BEST_EFFORT, alarmPrecision(31, false))
        assertEquals(AlarmPrecision.EXACT, alarmPrecision(35, true))
    }

    @Test fun deadlineSchedulesOneExactWakeupAndAdjustmentReplacesIt() {
        scheduler.reconcile(timer, "workout", now)
        assertEquals(1, shadowOf(manager).scheduledAlarms.size)
        assertEquals(timer.deadline, shadowOf(manager).scheduledAlarms.single().triggerAtTime)
        scheduler.reconcile(timer.copy(deadline = timer.deadline + 30_000L), "workout", now)
        assertEquals(1, shadowOf(manager).scheduledAlarms.size)
        assertEquals(timer.deadline + 30_000L, shadowOf(manager).scheduledAlarms.single().triggerAtTime)
    }

    @Test fun skipFinishAndExpiredRecoveryDoNotLeaveAlarms() {
        scheduler.reconcile(timer, "workout", now)
        scheduler.reconcile(null, "workout", now)
        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
        scheduler.reconcile(timer, "workout", now)
        scheduler.reconcile(null, null, now)
        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
        scheduler.reconcile(timer, "workout", timer.deadline + 1)
        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
        assertEquals(0, shadowOf(context.getSystemService(NotificationManager::class.java)).size())
    }

    @Test fun wrongActiveWorkoutCannotScheduleRest() {
        scheduler.reconcile(timer, "another-workout", now)
        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
    }

    @Test fun delayedLifecycleReconciliationUsesLatestCommittedDeadline() = runBlocking {
        val persistence = object : SnapshotPersistence {
            var snapshot: String? = null
            override fun read() = snapshot
            override fun write(snapshot: String) { this.snapshot = snapshot }
        }
        val repository = RebuildRepository.fromPersistence(persistence)
        val time = System.currentTimeMillis()
        repository.execute(Action.SelectPhase(Phase.INTRO_ONE))
        repository.execute(Action.StartWorkout(repository.state.value.availableTemplates().first().id, time))
        val workout = requireNotNull(repository.state.value.activeWorkout)
        val exercise = workout.exercises.first()
        repository.execute(Action.SaveSet(workout.id, exercise.id, "old", 10.0, 10, null, false, time))
        val delayedCallback = { scheduler.reconcileCurrent(repository) }
        repository.execute(Action.SaveSet(workout.id, exercise.id, "new", 10.0, 9, null, false, time + 1_000))
        val latest = requireNotNull(repository.state.value.restTimer)
        scheduler.reconcileCurrent(repository)
        delayedCallback()
        assertEquals(1, shadowOf(manager).scheduledAlarms.size)
        assertEquals(latest.deadline, shadowOf(manager).scheduledAlarms.single().triggerAtTime)
    }

    @Test fun notificationChannelIsSilentWithVibration() {
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(RestAlarmScheduler.CHANNEL_ID)
        assertNotNull(channel)
        assertNull(channel.sound)
        assertTrue(channel.shouldVibrate())
    }

    @Test fun runningRestShowsAnOngoingCountdownUntilItEnds() {
        val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))
        scheduler.reconcile(timer, "workout", now)
        val countdown = notifications.getNotification(RestAlarmScheduler.COUNTDOWN_NOTIFICATION_ID)
        assertNotNull(countdown)
        assertEquals(timer.deadline, countdown.`when`)
        assertTrue(countdown.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
        scheduler.reconcile(null, "workout", now)
        assertNull(notifications.getNotification(RestAlarmScheduler.COUNTDOWN_NOTIFICATION_ID))
    }

    @Test fun finishRemovesAnExpiredNotification() {
        scheduler.notifyExpired()
        val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))
        assertEquals(1, notifications.size())
        scheduler.reconcile(null, null, now)
        assertEquals(0, notifications.size())
    }

    @Test fun disabledNotificationsAreReportedAndDoNotNotify() {
        val manager = context.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationsEnabled(false)
        assertFalse(scheduler.notificationsAllowed())
        scheduler.notifyExpired()
        assertEquals(0, shadowOf(manager).size())
    }

    @Test fun eachSoundPostsOnItsOwnChannelAndOnlyTheAlarmKeepsRinging() {
        val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))
        com.rebuild.ui.RestSound.entries.forEach { sound ->
            val chosen = RestAlarmScheduler(context) { sound }
            chosen.notifyExpired()
            val posted = notifications.getNotification(RestAlarmScheduler.NOTIFICATION_ID)
            assertEquals(RestAlarmScheduler.channelFor(sound), posted.channelId)
            val insistent = posted.flags and android.app.Notification.FLAG_INSISTENT != 0
            assertEquals("$sound rings until stopped", sound == com.rebuild.ui.RestSound.ALARM, insistent)
            assertEquals(if (sound == com.rebuild.ui.RestSound.ALARM) 1 else 0, posted.actions?.size ?: 0)
        }
        assertEquals(com.rebuild.ui.RestSound.entries.map { RestAlarmScheduler.channelFor(it) }.toSet().size,
            com.rebuild.ui.RestSound.entries.size)
    }

    @Test fun openingRebuildStopsOnlyAnAlarm() {
        val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))
        val buzz = RestAlarmScheduler(context) { com.rebuild.ui.RestSound.BUZZ }
        buzz.notifyExpired()
        buzz.stopRinging()
        assertNotNull(notifications.getNotification(RestAlarmScheduler.NOTIFICATION_ID))
        val alarm = RestAlarmScheduler(context) { com.rebuild.ui.RestSound.ALARM }
        alarm.notifyExpired()
        alarm.stopRinging()
        assertNull(notifications.getNotification(RestAlarmScheduler.NOTIFICATION_ID))
        alarm.notifyExpired()
        buzz.stopRinging(always = true)
        assertNull(notifications.getNotification(RestAlarmScheduler.NOTIFICATION_ID))
    }

    @Test fun aShortPauseBuzzesEvenWithTheAlarmChosen() {
        val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))
        val alarm = RestAlarmScheduler(context) { com.rebuild.ui.RestSound.ALARM }
        alarm.notifyExpired(shortPause = true)
        val posted = notifications.getNotification(RestAlarmScheduler.NOTIFICATION_ID)
        assertEquals(RestAlarmScheduler.CHANNEL_ID, posted.channelId)
        assertEquals(0, posted.flags and android.app.Notification.FLAG_INSISTENT)
        assertNull(alarm.ringingSince.value)
    }

    @Test fun theAlarmReportsRingingUntilStoppedOrANewRestStarts() {
        val alarm = RestAlarmScheduler(context) { com.rebuild.ui.RestSound.ALARM }
        alarm.notifyExpired()
        assertNotNull(alarm.ringingSince.value)
        val posted = shadowOf(context.getSystemService(NotificationManager::class.java))
            .getNotification(RestAlarmScheduler.NOTIFICATION_ID)
        assertEquals("Stop shows on the lock screen", android.app.Notification.VISIBILITY_PUBLIC, posted.visibility)
        alarm.stopRinging(always = true)
        assertNull(alarm.ringingSince.value)
        alarm.notifyExpired()
        alarm.reconcile(timer, "workout", now)
        assertNull("A new rest silences the last one's alarm", alarm.ringingSince.value)
    }
}
