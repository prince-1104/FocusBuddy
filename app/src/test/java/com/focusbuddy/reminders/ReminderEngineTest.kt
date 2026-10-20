package com.focusbuddy.reminders

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focusbuddy.data.local.*
import com.focusbuddy.data.repository.*
import com.focusbuddy.data.reminders.*
import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.reminders.*
import com.focusbuddy.domain.time.TimeZoneSource
import com.focusbuddy.domain.usecase.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = com.focusbuddy.TestApplication::class)
class ReminderEngineTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "alarms-${UUID.randomUUID()}.db"
    private var db = FocusBuddyDatabase.create(context, name)
    private val clock = MutableClock(Instant.parse("2026-10-20T10:00:00Z"))
    private var zone: ZoneId = ZoneOffset.UTC
    private val alarms = FakeAlarms()
    private val notifications = FakeNotifications()
    private val tasks get() = RoomTaskRepository(db, clock) { zone }
    private val reminders get() = RoomReminderRepository(db, clock)
    private fun engine() = ReminderEngine(db, alarms, notifications, clock) { zone }
    private val engine get() = coordinator
    private val coordinator by lazy { engine() }
    @After fun close() { db.close(); context.deleteDatabase(name) }
    private suspend fun alert(task: Task? = null, rule: String? = null): Reminder {
        val value = Reminder(taskId = task?.id, title = if (task == null) "Remember" else null,
            triggerAt = clock.instant().plusSeconds(60), recurrenceRule = rule)
        reminders.create(value); return value
    }
    private suspend fun row(id: UUID) = db.occurrences().all().first { it.reminderId == id && it.status == "Pending" }

    @Test fun reconcileIsIdempotentAndDeliveryClaimsAreDurable() = runTest {
        val alert = alert()
        engine.reconcile(); engine.reconcile()
        assertEquals(1, alarms.installs.size)
        val row = row(alert.id)
        clock.now = row.triggerAt
        engine.deliver(row.key); engine.deliver(row.key)
        assertEquals(1, notifications.shown.size)
        db.close(); db = FocusBuddyDatabase.create(context, name)
        engine().deliver(row.key)
        assertEquals(1, notifications.shown.size)
        assertEquals("Delivered", db.occurrences().get(row.key)!!.status)
    }
    @Test fun notificationOccurrenceLookupAlwaysResolvesItsOwnReminder() = runTest {
        val first = alert(); val second = alert(); engine.reconcile()
        val source = com.focusbuddy.data.reminders.RoomOccurrenceRepository(db)
        assertEquals(first.id, source.observe(row(first.id).key).first().reminder!!.id)
        assertEquals(second.id, source.observe(row(second.id).key).first().reminder!!.id)
        val key = row(first.id).key
        reminders.delete(first.id)
        assertNull(source.observe(key).first().reminder)
    }
    @Test fun editCancelsObsoleteIdentityAndOldBroadcastCannotDeliver() = runTest {
        val alert = alert(); engine.reconcile()
        val old = row(alert.id)
        reminders.update(alert.copy(triggerAt = alert.triggerAt.plusSeconds(120)))
        engine.reconcile()
        assertTrue(old.key in alarms.canceled)
        val updated = row(alert.id)
        assertNotEquals(old.key, updated.key)
        clock.now = updated.triggerAt
        engine.deliver(old.key); assertTrue(notifications.shown.isEmpty())
        engine.deliver(updated.key); assertEquals(1, notifications.shown.size)
    }
    @Test fun deletionRetainsCancellationJournalUntilObsoleteAlarmIsCanceled() = runTest {
        val alert = alert(); engine.reconcile(); val old = row(alert.id)
        reminders.delete(alert.id)
        assertNull(db.occurrences().get(old.key))
        assertTrue(db.occurrences().journal().any { it.key == old.key })
        engine.reconcile(); clock.now = old.triggerAt; engine.deliver(old.key)
        assertTrue(old.key in alarms.canceled)
        assertTrue(notifications.shown.isEmpty())
        assertTrue(db.occurrences().journal().isEmpty())
    }
    @Test fun deletedTaskAndCompletedTaskNeverDeliverPendingAlarms() = runTest {
        val parent = tasks.create(Task(title = "Parent", scheduledDate = clock.instant().atZone(zone).toLocalDate()))
        val alert = alert(parent); engine.reconcile(); val first = row(alert.id)
        tasks.setCompleted(parent.id, true)
        clock.now = first.triggerAt
        engine.deliver(first.key); engine.reconcile()
        assertTrue(notifications.shown.isEmpty())
        assertTrue(first.key in alarms.canceled)
        val other = tasks.create(Task(title = "Delete", scheduledDate = LocalDate.of(2026, 10, 20)))
        val second = alert(other); engine.reconcile(); val old = row(second.id)
        tasks.delete(other.id); engine.reconcile()
        clock.now = old.triggerAt; engine.deliver(old.key)
        assertTrue(old.key in alarms.canceled); assertTrue(notifications.shown.isEmpty())
    }
    @Test fun notificationDenialBlocksRegistrationAndNeverReplaysMissedAlerts() = runTest {
        val alert = alert(); alarms.permission = SchedulingAccess(false, true)
        engine.reconcile()
        assertTrue(alarms.installs.isEmpty())
        assertEquals("Blocked", row(alert.id).alarmMode)
        clock.now = alert.triggerAt.plusSeconds(1)
        alarms.permission = SchedulingAccess(true, true)
        engine.reconcile()
        assertTrue(alarms.installs.isEmpty())
        assertEquals("Expired", db.occurrences().all().single().status)
    }
    @Test fun grantBeforeDueTimeRegistersPreviouslyBlockedReminder() = runTest {
        alert(); alarms.permission = SchedulingAccess(false, false); engine.reconcile()
        alarms.permission = SchedulingAccess(true, false); engine.reconcile()
        assertEquals("Inexact", db.occurrences().all().single().alarmMode)
        assertEquals(1, alarms.installs.size)
    }
    @Test fun exactAccessRevocationAndGrantChangeAlarmModeSafely() = runTest {
        val alert = alert(); engine.reconcile()
        assertEquals("Exact", row(alert.id).alarmMode)
        alarms.permission = SchedulingAccess(true, false); engine.reconcile()
        assertEquals("Inexact", row(alert.id).alarmMode)
        alarms.permission = SchedulingAccess(true, true); engine.reconcile()
        assertEquals("Exact", row(alert.id).alarmMode)
        assertEquals(3, alarms.installs.size)
    }
    @Test fun revocationDuringDeliveryDoesNotReplayAfterPermissionReturns() = runTest {
        val alert = alert(); engine.reconcile(); val first = row(alert.id)
        clock.now = first.triggerAt; notifications.allowed = false; engine.deliver(first.key)
        assertEquals("Blocked", db.occurrences().get(first.key)!!.status)
        notifications.allowed = true; engine.deliver(first.key)
        assertTrue(notifications.shown.isEmpty())
    }
    @Test fun oldOrNewPastAlertsAreNotDelivered() = runTest {
        val past = Reminder(title = "Past", triggerAt = clock.instant().minusSeconds(10_800))
        reminders.create(past); engine.reconcile()
        assertTrue(alarms.installs.isEmpty())
        val old = ReminderOccurrence(occurrenceKey(past.id, past.triggerAt), past.id, past.triggerAt, past.triggerAt, "Normal", fingerprint(past, null) + "@" + zone.id)
        db.occurrences().insert(old.entity().copy(alarmMode = "Exact"))
        engine.deliver(old.key)
        assertEquals("Expired", db.occurrences().get(old.key)!!.status)
        assertTrue(notifications.shown.isEmpty())
    }
    @Test fun boundedInexactDelayIsAllowedButExcessiveLatenessExpires() = runTest {
        val first = alert(); engine.reconcile(); val row = row(first.id)
        clock.now = row.triggerAt.plusSeconds(60 * 60); engine.deliver(row.key)
        assertEquals(1, notifications.shown.size)
        val second = alert(); engine.reconcile(); val late = row(second.id)
        clock.now = late.triggerAt.plusSeconds(2 * 60 * 60 + 1); engine.deliver(late.key)
        assertEquals("Expired", db.occurrences().get(late.key)!!.status)
        assertEquals(1, notifications.shown.size)
    }
    @Test fun snoozeIsDistinctIdempotentAndPreservesOriginalRecurrence() = runTest {
        val alert = alert(rule = "FREQ=DAILY;COUNT=3"); engine.reconcile(); val original = row(alert.id)
        clock.now = original.triggerAt; engine.deliver(original.key)
        val snooze = engine.snooze(original.key, 15)!!
        assertEquals(snooze, engine.snooze(original.key, 15))
        assertNotEquals(original.key, snooze)
        assertEquals(1, db.occurrences().all().count { it.kind == "Snooze" })
        assertEquals(alert.triggerAt, reminders.get(alert.id)!!.triggerAt)
        assertNull(reminders.get(alert.id)!!.snoozedUntil)
        engine.reconcile()
        assertEquals(original.triggerAt.plusSeconds(900), db.occurrences().get(snooze)!!.triggerAt)
        assertTrue(alarms.installs.any { it.key != snooze && it.triggerAt == original.triggerAt.plusSeconds(86400) })
        clock.now = db.occurrences().get(snooze)!!.triggerAt; engine.deliver(snooze)
        assertEquals(2, notifications.shown.size)
    }
    @Test fun everySupportedSnoozeDurationIsCalculatedFromInjectedClock() = runTest {
        for (minutes in listOf(5, 10, 15, 30)) {
            val reminder = alert(); engine.reconcile(); val row = row(reminder.id)
            clock.now = row.triggerAt; engine.deliver(row.key)
            val key = engine.snooze(row.key, minutes)!!
            assertEquals(clock.instant().plusSeconds(minutes * 60L), db.occurrences().get(key)!!.triggerAt)
        }
    }
    @Test fun completeActionConsumesStandaloneOccurrenceWithoutDisablingRecurrence() = runTest {
        val alert = alert(rule = "FREQ=DAILY;COUNT=2"); engine.reconcile(); val first = row(alert.id)
        clock.now = first.triggerAt; engine.deliver(first.key)
        assertTrue(engine.complete(first.key)); assertFalse(engine.complete(first.key))
        assertTrue(reminders.get(alert.id)!!.enabled)
        engine.reconcile()
        assertTrue(alarms.installs.any { it.triggerAt == alert.triggerAt.plusSeconds(86400) })
    }
    @Test fun taskActionCompletesParentAndCancelsAllAttachedPendingAlerts() = runTest {
        val task = tasks.create(Task(title = "Complete me", scheduledDate = LocalDate.of(2026, 10, 20)))
        val first = alert(task); val second = Reminder(taskId = task.id, triggerAt = first.triggerAt.plusSeconds(100)); reminders.create(second)
        engine.reconcile(); val row = row(first.id); val other = row(second.id)
        clock.now = row.triggerAt; engine.deliver(row.key); assertTrue(engine.complete(row.key)); engine.reconcile()
        assertTrue(tasks.get(task.id)!!.task.completed)
        assertTrue(tasks.get(task.id)!!.reminders.none { it.enabled })
        assertTrue(other.key in alarms.canceled)
    }
    @Test fun staleActionCannotCompleteReopenedTaskOrCreateSnooze() = runTest {
        val task = tasks.create(Task(title = "Stale", scheduledDate = LocalDate.of(2026, 10, 20)))
        val alert = alert(task); engine.reconcile(); val row = row(alert.id)
        clock.now = row.triggerAt; engine.deliver(row.key); engine.complete(row.key)
        tasks.setCompleted(task.id, false); reminders.update(alert.copy(enabled = true))
        assertFalse(engine.complete(row.key)); assertNull(engine.snooze(row.key, 5))
        assertFalse(tasks.get(task.id)!!.task.completed)
    }
    @Test fun rebootGenerationAndForcedRecoveryRestoreDerivedAlarmsOnly() = runTest {
        alert(); engine.reconcile(); val count = alarms.installs.size
        alarms.registered.clear(); alarms.boot = "2"
        engine().reconcile()
        assertTrue(alarms.installs.size > count)
        val again = alarms.installs.size
        engine().reconcile(force = true)
        assertTrue(alarms.installs.size > again)
        assertTrue(notifications.shown.isEmpty())
    }
    @Test fun concurrentReceiversCannotPostSameOccurrenceTwice() = runTest {
        val alert = alert(); engine.reconcile(); val row = row(alert.id); clock.now = row.triggerAt
        awaitAll(async(Dispatchers.IO) { engine.deliver(row.key) }, async(Dispatchers.IO) { engine().deliver(row.key) })
        assertEquals(1, notifications.shown.size)
    }
    @Test fun snoozeSurvivesDatabaseReopenAndUnrelatedTaskNotesEdit() = runTest {
        val task = tasks.create(Task(title = "Keep snooze", scheduledDate = LocalDate.of(2026, 10, 20)))
        val alert = alert(task); engine.reconcile(); val row = row(alert.id); clock.now = row.triggerAt; engine.deliver(row.key)
        val key = engine.snooze(row.key, 10)!!
        val draft = tasks.get(task.id)!!.toDraft(zone)
        SaveTask(tasks, clock)(draft.copy(notes = "Only notes changed"), task.id, zone)
        db.close(); db = FocusBuddyDatabase.create(context, name)
        engine().reconcile()
        assertEquals("Pending", db.occurrences().get(key)!!.status)
        assertTrue(alarms.registered.containsKey(key))
    }
    @Test fun agendaCountsDropAfterDeliveryAndIncludeSnoozedTriggerDay() = runTest {
        clock.now = Instant.parse("2026-10-20T23:55:00Z")
        val alert = alert(); engine.reconcile(); val row = row(alert.id)
        val zones = object : TimeZoneSource { override val zones = flowOf(zone) }
        val calendar = RoomCalendarRepository(db, zones, clock)
        assertEquals(1, calendar.observeMonth(YearMonth.of(2026, 10)).first().first { it.date.dayOfMonth == 20 }.reminderAlertCount)
        clock.now = row.triggerAt; engine.deliver(row.key)
        assertEquals(0, calendar.observeMonth(YearMonth.of(2026, 10)).first().first { it.date.dayOfMonth == 20 }.reminderAlertCount)
        engine.snooze(row.key, 10); engine.reconcile()
        val next = calendar.observeMonth(YearMonth.of(2026, 10)).first()
        assertEquals(1, next.first { it.date.dayOfMonth == 21 }.reminderAlertCount)
    }
    @Test fun registrationFailureIsPersistedAndRetriedWithoutDuplicatingRows() = runTest {
        val alert = alert(); alarms.fail = true; engine.reconcile()
        assertEquals("Error", row(alert.id).alarmMode)
        alarms.fail = false; engine.reconcile()
        assertEquals("Exact", row(alert.id).alarmMode)
        assertEquals(1, db.occurrences().all().size)
    }
}

class MutableClock(@Volatile var now: Instant) : Clock() {
    override fun instant() = now
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
}
class FakeAlarms : AlarmGateway {
    var permission = SchedulingAccess(true, true)
    var boot = "1"
    var fail = false
    override val generation get() = boot
    val registered = ConcurrentHashMap<String, ReminderOccurrence>()
    val installs = mutableListOf<ReminderOccurrence>()
    val canceled = mutableSetOf<String>()
    override fun access() = permission
    override fun schedule(occurrence: ReminderOccurrence): String {
        if (fail) error("OS alarm unavailable")
        registered[occurrence.key] = occurrence; installs += occurrence
        return if (permission.exact) "Exact" else "Inexact"
    }
    override fun cancel(key: String) { registered.remove(key); canceled += key }
    override fun maintenance(at: Instant) = Unit
}
class FakeNotifications : NotificationGateway {
    var allowed = true
    val shown = java.util.Collections.synchronizedList(mutableListOf<ReminderDelivery>())
    override fun show(delivery: ReminderDelivery): Boolean { if (allowed) shown += delivery; return allowed }
    override fun cancel(key: String) = Unit
}
