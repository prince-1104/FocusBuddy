package com.focusbuddy.reminders

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.focusbuddy.FocusBuddyApplication
import com.focusbuddy.domain.model.Reminder
import com.focusbuddy.platform.reminders.AndroidAlarmGateway
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.Instant
import java.util.UUID

/** Run on a disposable emulator/device using scripts/verify-reminders-device.ps1. No Activity is launched. */
class ReminderDeviceTest {
    private val app get() = ApplicationProvider.getApplicationContext<FocusBuddyApplication>()
    @Test fun notificationIsPostedByRealAlarmWhileNoActivityIsOpen() = runBlocking {
        Assume.assumeTrue("Grant POST_NOTIFICATIONS before running delivery verification", AndroidAlarmGateway(app).access().notifications)
        val reminder = Reminder(title = "FocusBuddy device verification", triggerAt = Instant.now().plusSeconds(5))
        try {
            app.container.reminders.create(reminder)
            app.container.reminderScheduler.reconcile()
            val row = app.container.database.occurrences().forReminder(reminder.id).first { it.status == "Pending" }
            val deadline = System.currentTimeMillis() + 90_000
            val manager = app.getSystemService(NotificationManager::class.java)
            while (System.currentTimeMillis() < deadline && manager.activeNotifications.none { it.tag == row.key }) Thread.sleep(250)
            val notification = manager.activeNotifications.single { it.tag == row.key }.notification
            assertEquals("Delivered", app.container.database.occurrences().get(row.key)!!.status)
            assertEquals(3, notification.actions.size)
            assertNull(notification.fullScreenIntent)
        } finally { app.container.reminders.delete(reminder.id); app.container.reminderScheduler.reconcile() }
    }
    @Test fun deniedPermissionLeavesFutureOccurrenceBlocked() = runBlocking {
        Assume.assumeFalse("Run this phase with notification permission denied", AndroidAlarmGateway(app).access().notifications)
        val reminder = Reminder(title = "Permission verification", triggerAt = Instant.now().plusSeconds(120))
        try {
            app.container.reminders.create(reminder); app.container.reminderScheduler.reconcile()
            assertEquals("Blocked", app.container.database.occurrences().forReminder(reminder.id).first().alarmMode)
        } finally { app.container.reminders.delete(reminder.id); app.container.reminderScheduler.reconcile() }
    }
    @Test fun unavailableExactAccessUsesRealInexactFallback() = runBlocking {
        Assume.assumeTrue(AndroidAlarmGateway(app).access().notifications)
        Assume.assumeFalse("Run with SCHEDULE_EXACT_ALARM app-op denied", AndroidAlarmGateway(app).access().exact)
        val reminder = Reminder(title = "Fallback verification", triggerAt = Instant.now().plusSeconds(120))
        try {
            app.container.reminders.create(reminder); app.container.reminderScheduler.reconcile()
            assertEquals("Inexact", app.container.database.occurrences().forReminder(reminder.id).first().alarmMode)
        } finally { app.container.reminders.delete(reminder.id); app.container.reminderScheduler.reconcile() }
    }
}

/** Host launches this test twice and kills the APP PROCESS (not force-stop) between phases. */
class ReminderProcessDeathTest {
    @Test fun processDeathRecovery() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<FocusBuddyApplication>()
        val phase = InstrumentationRegistry.getArguments().getString("phase")
        Assume.assumeTrue("Host-driven test; use verify-reminders-device.ps1", phase in setOf("seed", "verify", "cleanup"))
        val state = app.getSharedPreferences("reminder_device_verification", Context.MODE_PRIVATE)
        if (phase == "seed") {
            check(AndroidAlarmGateway(app).access().notifications)
            val reminder = Reminder(title = "Process-death verification", triggerAt = Instant.now().plusSeconds(20))
            app.container.reminders.create(reminder); app.container.reminderScheduler.reconcile()
            val row = app.container.database.occurrences().forReminder(reminder.id).first { it.status == "Pending" }
            check(state.edit().putString("id", reminder.id.toString()).putString("key", row.key).commit())
        } else {
            val id = state.getString("id", null)?.let(UUID::fromString)
            val key = state.getString("key", null)
            try {
                if (phase == "verify") {
                    checkNotNull(key)
                    assertEquals("Delivered", app.container.database.occurrences().get(key)?.status)
                    assertEquals(1, app.getSystemService(NotificationManager::class.java).activeNotifications.count { it.tag == key })
                }
            } finally {
                id?.let { app.container.reminders.delete(it) }; app.container.reminderScheduler.reconcile(); state.edit().clear().commit()
            }
        }
    }
}
