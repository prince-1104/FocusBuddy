package com.focusbuddy.reminders

import android.Manifest
import android.app.*
import android.content.Context
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.focusbuddy.FocusBuddyApplication
import com.focusbuddy.domain.model.*
import com.focusbuddy.platform.reminders.alarmPendingIntent
import com.focusbuddy.platform.reminders.ReminderAlarmReceiver
import com.focusbuddy.platform.reminders.performReminderOperation
import com.focusbuddy.platform.reminders.AndroidReminderScheduler
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.*

/** Full Room -> Alarm PendingIntent -> receiver -> NotificationManager path on host shadows.
 * No Activity is started. This is not physical-device delivery evidence. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = com.focusbuddy.TestApplication::class)
class ReminderDeliveryIntegrationTest {
    private val app = ApplicationProvider.getApplicationContext<FocusBuddyApplication>()
    private val receiver = ReminderAlarmReceiver()
    @Before fun registerManifestReceiverInHostHarness() {
        // AGP's unit-test manifest contains the test host, not app receiver declarations.
        // Register the production receiver explicitly so PendingIntent.send dispatches it.
        app.registerReceiver(receiver, IntentFilter("com.focusbuddy.FIRE"), Context.RECEIVER_NOT_EXPORTED)
    }
    @After fun cleanup() = runBlocking {
        app.container.reminderScope.coroutineContext.job.cancelAndJoin()
        app.unregisterReceiver(receiver)
        app.container.database.close()
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
    }
    @Test fun attachedAndStandaloneAlarmsReachNotificationsThroughTheRealReceiverWithoutAnActivity() = runBlocking {
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val manager = app.getSystemService(NotificationManager::class.java)
        Shadows.shadowOf(manager).setNotificationsEnabled(true)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val container = app.container
        val task = container.tasks.create(Task(title = "Task receiver delivery", scheduledDate = LocalDate.now()))
        val trigger = Instant.now().plusSeconds(4)
        val attached = Reminder(taskId = task.id, triggerAt = trigger)
        val standalone = Reminder(title = "Standalone receiver delivery", triggerAt = trigger)
        container.reminders.create(attached); container.reminders.create(standalone)
        container.reminderScheduler.reconcile()
        val rows = container.database.occurrences().all().filter { it.reminderId in setOf(attached.id, standalone.id) }
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.status == "Pending" && it.alarmMode == "Exact" })
        val delayMillis = Duration.between(Instant.now(), trigger).toMillis() + 30
        if (delayMillis > 0) Thread.sleep(delayMillis)
        rows.forEach { checkNotNull(alarmPendingIntent(app, it.key, PendingIntent.FLAG_NO_CREATE)).send() }
        val deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos()
        while (rows.any { Shadows.shadowOf(manager).getNotification(it.key, 0) == null } && System.nanoTime() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        rows.forEach { row ->
            val posted = Shadows.shadowOf(manager).getNotification(row.key, 0)
            val diagnostic = "states=${container.database.occurrences().all()}\n" +
                org.robolectric.shadows.ShadowLog.getLogs().filter { it.tag == "FocusBuddyReminders" }
                    .joinToString("\n") { it.msg + "\n" + it.throwable?.stackTraceToString() }
            assertNotNull("Receiver failed to post ${row.reminderId}: $diagnostic", posted)
            assertEquals(if (row.reminderId == attached.id) task.title else standalone.title,
                posted.extras.getString(Notification.EXTRA_TITLE))
            assertEquals("Delivered", container.database.occurrences().get(row.key)!!.status)
        }
        // Retried callbacks must not add another notification for either occurrence.
        rows.forEach { checkNotNull(alarmPendingIntent(app, it.key)).send() }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        container.reminderScheduler.reconcile()
        assertEquals(2, Shadows.shadowOf(manager).size())
    }
    @Test fun receiverFailureRequestsAForcedRebuildOfConsumedAlarmRegistrations() {
        val failureReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                performReminderOperation(context) { error("Simulated failure before delivery claim") }
            }
        }
        val action = "com.focusbuddy.TEST_PRECLAIM_FAILURE"
        app.registerReceiver(failureReceiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        try {
            app.sendBroadcast(Intent(action).setPackage(app.packageName))
            val jobs = app.getSystemService(android.app.job.JobScheduler::class.java)
            val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
            while (jobs.getPendingJob(AndroidReminderScheduler.IMMEDIATE_JOB)?.extras?.getBoolean("force") != true && System.nanoTime() < deadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(20)
            }
            assertTrue("A failed consumed alarm must be rebuilt, not trusted as still registered",
                jobs.getPendingJob(AndroidReminderScheduler.IMMEDIATE_JOB)?.extras?.getBoolean("force") == true)
            assertEquals(0, Shadows.shadowOf(app.getSystemService(NotificationManager::class.java)).size())
        } finally { app.unregisterReceiver(failureReceiver) }
    }
}
