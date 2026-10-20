package com.focusbuddy.data.reminders

import androidx.room.withTransaction
import com.focusbuddy.data.local.*
import com.focusbuddy.data.repository.*
import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.reminders.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.*
import java.util.UUID

fun OccurrenceEntity.model() = ReminderOccurrence(key, reminderId, baseTrigger, triggerAt, kind, fingerprint)
fun ReminderOccurrence.entity() = OccurrenceEntity(key, reminderId, baseTrigger, triggerAt, kind, fingerprint)

/** One application-scoped coordinator; every receiver revalidates Room before an external effect. */
class ReminderEngine(private val db: FocusBuddyDatabase, private val alarms: AlarmGateway,
    private val notifications: NotificationGateway, private val clock: Clock,
    private val zone: () -> ZoneId = ZoneId::systemDefault) {
    private val mutex = Mutex()
    private val dao get() = db.occurrences()
    companion object { val MAX_LATENESS: Duration = Duration.ofHours(2) }
    private suspend fun active(row: OccurrenceEntity): Pair<Reminder, Task?>? {
        val reminder = db.reminders().get(row.reminderId)?.model() ?: return null
        val task = reminder.taskId?.let { db.tasks().get(it)?.model() }
        if (!reminder.enabled || (reminder.taskId != null && (task == null || task.completed))) return null
        val expected = fingerprint(reminder, task) + if (row.kind == "Snooze") "" else "@${zone().id}"
        return (reminder to task).takeIf { row.fingerprint == expected }
    }
    suspend fun reconcile(force: Boolean = false) = withContext(Dispatchers.IO) { mutex.withLock {
        val now = clock.instant()
        val records = db.withTransaction { db.tasks().all().map { it.model() } to db.reminders().all().map { it.model() } }
        val consumed = setOf("Delivered", "Dismissed", "Completed", "Snoozed")
        val planned = planOccurrences(records.first, records.second, now, zone()).groupBy { it.reminderId }.values
            .mapNotNull { series -> series.firstOrNull { dao.get(it.key)?.status !in consumed } }.associateBy { it.key }
        db.withTransaction {
            planned.values.forEach { occurrence ->
                val old = dao.get(occurrence.key)
                if (old == null) dao.insert(occurrence.entity())
                else if (old.status in setOf("Pending", "Canceled", "Expired", "Blocked") && (old.status != "Pending" || old.fingerprint != occurrence.fingerprint)) dao.update(occurrence.entity())
            }
        }
        // Canceled and expired rows are tombstones: reconciliation must not revive a delivered occurrence.
        dao.activeRows().forEach { row ->
            val valid = active(row) != null
            val isExpired = row.triggerAt < now.minus(MAX_LATENESS)
            val obsolete = row.kind != "Snooze" && row.triggerAt > now && row.key !in planned
            if (row.status in setOf("Pending", "Delivered") && (!valid || isExpired || obsolete ||
                (row.status == "Pending" && row.triggerAt < now && row.alarmMode !in setOf("Exact", "Inexact")))) {
                dao.update(row.copy(status = if (valid && !obsolete) "Expired" else "Canceled", alarmMode = null))
            }
        }
        val rows = dao.activeRows().associateBy { it.key }
        val access = alarms.access()
        val pending = rows.values.filter { it.status == "Pending" && active(it) != null }.sortedBy { it.triggerAt }
        val register = pending.filter { it.kind == "Snooze" }.map { it.key }.toSet() +
            pending.filter { it.kind != "Snooze" }.groupBy { it.reminderId }.values.map { it.first().key }
        dao.journal().forEach { journal ->
            val row = rows[journal.key]
            val keepNotification = row?.status == "Delivered" && journal.generation == alarms.generation && active(row) != null
            val keepAlarm = row?.status == "Pending" && journal.generation == alarms.generation && row.key in register && access.notifications && active(row) != null && !force
            if (!keepAlarm && !keepNotification) {
                alarms.cancel(journal.key); notifications.cancel(journal.key); dao.forgetAlarm(journal.key)
            }
        }
        val journals = dao.journal().associateBy { it.key }
        pending.forEach { row ->
            if (row.key !in register) { if (row.alarmMode != "Queued") dao.update(row.copy(alarmMode = "Queued")); return@forEach }
            if (!access.notifications) { dao.update(row.copy(alarmMode = "Blocked")); return@forEach }
            val requestedMode = if (access.exact) "Exact" else "Inexact"
            val journal = journals[row.key]
            if (!force && journal?.mode == requestedMode && journal.fingerprint == row.fingerprint && journal.triggerAt == row.triggerAt && row.alarmMode == requestedMode) return@forEach
            try {
                dao.journal(AlarmJournalEntity(row.key, row.triggerAt, row.fingerprint, "Installing", alarms.generation))
                val actualMode = alarms.schedule(row.model())
                dao.journal(AlarmJournalEntity(row.key, row.triggerAt, row.fingerprint, actualMode, alarms.generation))
                dao.update(row.copy(alarmMode = actualMode))
            } catch (_: Exception) {
                dao.update(row.copy(alarmMode = "Error")) // durable retry on the next job/startup/maintenance.
            }
        }
        alarms.maintenance(now.plus(Duration.ofHours(12)))
    } }
    suspend fun deliver(key: String) = withContext(Dispatchers.IO) { mutex.withLock {
        val now = clock.instant()
        val claimed = db.withTransaction {
            val row = dao.get(key) ?: return@withTransaction null
            if (row.status != "Pending") return@withTransaction null
            if (now < row.triggerAt) { dao.update(row.copy(alarmMode = null)); dao.forgetAlarm(key); return@withTransaction null }
            if (now > row.triggerAt.plus(MAX_LATENESS) || active(row) == null) {
                dao.update(row.copy(status = "Expired")); return@withTransaction null
            }
            dao.update(row.copy(status = "Delivered")) // commit claim BEFORE notify: at most once after process death.
            row
        } ?: return@withLock
        db.withTransaction {
            val data = active(claimed)
            if (data == null) { dao.update(claimed.copy(status = "Canceled")); return@withTransaction }
            val (reminder, task) = data
            val shown = notifications.show(ReminderDelivery(claimed.model(), reminder.title ?: task?.title ?: "Reminder",
                listOfNotNull(task?.let { "Task: ${it.title}" }, reminder.description ?: task?.description).joinToString("\n"),
                task?.id, reminder.soundEnabled, reminder.vibrationEnabled, reminder.soundUri))
            dao.update(claimed.copy(status = if (shown) "Delivered" else "Blocked", alarmMode = null))
            dao.journal(AlarmJournalEntity(key, claimed.triggerAt, claimed.fingerprint, "Notification", alarms.generation))
        }
    } }
    suspend fun snooze(sourceKey: String, minutes: Int): String? = withContext(Dispatchers.IO) { mutex.withLock {
        require(minutes in setOf(5, 10, 15, 30))
        val key = db.withTransaction {
            val source = dao.get(sourceKey) ?: return@withTransaction null
            val childKey = UUID.nameUUIDFromBytes("$sourceKey/snooze".toByteArray(Charsets.UTF_8)).toString()
            if (source.status == "Snoozed") return@withTransaction dao.get(childKey)?.takeIf { it.status == "Pending" && active(it) != null }?.key
            if (source.status !in setOf("Delivered", "Dismissed") || active(source) == null || clock.instant() > source.triggerAt.plus(MAX_LATENESS)) return@withTransaction null
            val data = active(source)!!
            val trigger = clock.instant().plusSeconds(minutes * 60L)
            dao.insert(OccurrenceEntity(childKey, source.reminderId, source.baseTrigger, trigger, "Snooze", fingerprint(data.first, data.second), sourceKey = sourceKey))
            dao.update(source.copy(status = "Snoozed", alarmMode = null))
            childKey
        }
        if (key != null) { alarms.cancel(sourceKey); notifications.cancel(sourceKey); dao.forgetAlarm(sourceKey) }
        key
    } }
    suspend fun complete(key: String): Boolean = withContext(Dispatchers.IO) { mutex.withLock {
        val done = db.withTransaction {
            val row = dao.get(key) ?: return@withTransaction false
            if (row.status !in setOf("Delivered", "Dismissed") || clock.instant() > row.triggerAt.plus(MAX_LATENESS)) return@withTransaction false
            val data = active(row) ?: return@withTransaction false
            if (data.second != null) RoomTaskRepository(db, clock, zone).setCompleted(data.second!!.id, true)
            // Standalone completion consumes THIS occurrence, preserving the recurring series.
            dao.update(row.copy(status = "Completed", alarmMode = null))
            true
        }
        if (done) { notifications.cancel(key); alarms.cancel(key) }
        done
    } }
    suspend fun dismiss(key: String) = withContext(Dispatchers.IO) { mutex.withLock {
        dao.get(key)?.takeIf { it.status == "Delivered" }?.let { dao.update(it.copy(status = "Dismissed")) }
    } }
    suspend fun cancel(reminderId: UUID) = withContext(Dispatchers.IO) { mutex.withLock {
        db.reminders().get(reminderId)?.let { db.reminders().update(it.copy(enabled = false, snoozedUntil = null)) }
        dao.forReminder(reminderId).forEach { row ->
            alarms.cancel(row.key); notifications.cancel(row.key); dao.forgetAlarm(row.key)
            if (row.status == "Pending") dao.update(row.copy(status = "Canceled", alarmMode = null))
        }
    } }
    suspend fun install(occurrence: ReminderOccurrence) {
        withContext(Dispatchers.IO) { db.withTransaction { dao.insert(occurrence.entity()) } }
        reconcile()
    }
}
