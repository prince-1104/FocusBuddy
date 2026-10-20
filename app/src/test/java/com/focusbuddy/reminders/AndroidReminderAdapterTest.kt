package com.focusbuddy.reminders

import android.Manifest
import android.app.*
import android.content.*
import androidx.test.core.app.ApplicationProvider
import com.focusbuddy.domain.reminders.*
import com.focusbuddy.platform.reminders.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = com.focusbuddy.TestApplication::class)
class AndroidReminderAdapterTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val id = UUID.randomUUID()
    private val trigger = Instant.parse("2026-10-20T12:00:00Z")
    @After fun cleanup() { ShadowAlarmManager.setCanScheduleExactAlarms(false) }
    @Test fun pendingIntentIdentityIsStableImmutableAndDistinctForEveryOccurrenceAndAction() {
        val first = occurrenceKey(id, trigger)
        val next = occurrenceKey(id, trigger.plusSeconds(86400))
        assertEquals(alarmPendingIntent(context, first), alarmPendingIntent(context, first))
        assertNotEquals(alarmPendingIntent(context, first), alarmPendingIntent(context, next))
        assertNotEquals(actionPendingIntent(context, first, "SNOOZE", 5), actionPendingIntent(context, first, "SNOOZE", 10))
        assertNotEquals(actionPendingIntent(context, first, "COMPLETE"), actionPendingIntent(context, first, "DISMISS"))
        assertTrue(alarmPendingIntent(context, first)!!.isImmutable)
        assertTrue(openPendingIntent(context, first).isImmutable)
        assertEquals("focusbuddy://occurrence/$first", Shadows.shadowOf(openPendingIntent(context, first)).savedIntent.data.toString())
        assertEquals("focusbuddy://occurrence/$next", Shadows.shadowOf(openPendingIntent(context, next)).savedIntent.data.toString())
    }
    @Test fun frameworkAdapterUsesFallbackWhenExactPermissionIsAbsent() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val gateway = AndroidAlarmGateway(context)
        assertFalse(gateway.access().exact)
        val row = ReminderOccurrence(occurrenceKey(id, trigger), id, trigger, trigger, "Normal", "test")
        assertEquals("Inexact", gateway.schedule(row))
        assertEquals(1, Shadows.shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
        gateway.cancel(row.key)
        assertTrue(Shadows.shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
    }
    @Test fun frameworkAdapterUsesExactWhenAccessExists() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val gateway = AndroidAlarmGateway(context)
        val row = ReminderOccurrence(occurrenceKey(id, trigger), id, trigger, trigger, "Normal", "test")
        assertEquals("Exact", gateway.schedule(row))
        assertTrue(gateway.access().exact)
    }
    @Test fun channelsRespectSoundVibrationAndExistingAndroidOverrides() {
        val notifications = AndroidNotifications(context)
        val silent = notifications.channel(false, false, null)
        val sound = notifications.channel(true, false, null)
        assertNotEquals(silent, sound)
        val manager = context.getSystemService(NotificationManager::class.java)
        assertNull(manager.getNotificationChannel(silent).sound)
        assertFalse(manager.getNotificationChannel(silent).shouldVibrate())
        assertNotNull(manager.getNotificationChannel(sound).sound)
        assertEquals(silent, notifications.channel(false, false, null))
    }
    @Test fun notificationContainsThreeRealActionsAndNoFullScreenIntent() {
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val manager = context.getSystemService(NotificationManager::class.java)
        Shadows.shadowOf(manager).setNotificationsEnabled(true)
        val key = occurrenceKey(id, trigger)
        val shown = AndroidNotifications(context).show(ReminderDelivery(ReminderOccurrence(key, id, trigger, trigger, "Normal", "test"),
            "A real reminder", "Task: Write", UUID.randomUUID(), true, true, null))
        assertTrue(shown)
        val notification = Shadows.shadowOf(manager).getNotification(key, 0)
        assertEquals(listOf("Complete", "Snooze 10 min", "Open"), notification.actions.map { it.title.toString() })
        assertNull(notification.fullScreenIntent)
        assertNotNull(notification.contentIntent)
    }
    @Test fun deniedNotificationsDoNotPost() {
        Shadows.shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        val key = occurrenceKey(id, trigger)
        assertFalse(AndroidNotifications(context).show(ReminderDelivery(ReminderOccurrence(key, id, trigger, trigger, "Normal", "test"), "Blocked", "", null, false, false, null)))
    }
    @Test fun recoveryBroadcastsEnqueueDurableForcedReconciliation() {
        val receiver = ReminderRecoveryReceiver()
        for (action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_DATE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) {
            receiver.onReceive(context, Intent(action))
            val job = context.getSystemService(android.app.job.JobScheduler::class.java).getPendingJob(AndroidReminderScheduler.IMMEDIATE_JOB)!!
            assertTrue(job.isPersisted)
            assertTrue(job.extras.getBoolean("force"))
        }
        AndroidReminderScheduler.enqueue(context)
        assertTrue(context.getSystemService(android.app.job.JobScheduler::class.java).getPendingJob(AndroidReminderScheduler.IMMEDIATE_JOB)!!.extras.getBoolean("force"))
    }
}
