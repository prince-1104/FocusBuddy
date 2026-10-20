package com.focusbuddy.domain.reminders

import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.calendar.calendarSnapshot
import java.time.*
import java.security.MessageDigest
import java.util.UUID

fun occurrenceKey(id: UUID, trigger: Instant, kind: String = "Normal"): String =
    UUID.nameUUIDFromBytes("$id/$trigger/$kind".toByteArray(Charsets.UTF_8)).toString()
fun fingerprint(reminder: Reminder, task: Task?): String {
    val value = listOf(reminder, task?.id, task?.title, task?.description, task?.scheduledDate,
        task?.startAt, task?.endAt, task?.allDay, task?.recurrenceRule, task?.completed).joinToString("|")
    return MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
/** Rolling horizon replenished by delivery, persisted periodic work, and a daily maintenance alarm. */
fun planOccurrences(tasks: List<Task>, reminders: List<Reminder>, now: Instant, zone: ZoneId): List<ReminderOccurrence> {
    val end = now.plus(Duration.ofDays(35))
    val first = YearMonth.from(now.atZone(zone))
    val last = YearMonth.from(end.atZone(zone))
    val parents = tasks.associateBy { it.id }
    val results = generateSequence(first) { it.plusMonths(1) }.takeWhile { it <= last }.flatMap { month ->
        calendarSnapshot(tasks, reminders, emptyList(), month, zone).alerts.asSequence()
    }.filter { it.trigger >= now && it.trigger <= end }.map { alert ->
        val legacySnooze = alert.reminder.snoozedUntil == alert.trigger
        val kind = if (legacySnooze) "LegacySnooze" else "Normal"
        ReminderOccurrence(occurrenceKey(alert.reminder.id, alert.trigger, kind), alert.reminder.id,
            if (legacySnooze) alert.reminder.triggerAt else alert.trigger, alert.trigger, kind, fingerprint(alert.reminder, alert.task) + "@" + zone.id)
    }.toList().toMutableList()
    // A non-recurring alert outside the rolling window can be registered immediately.
    reminders.filter { it.enabled && it.recurrenceRule == null && it.effectiveTriggerAt > end &&
        (it.taskId == null || parents[it.taskId]?.completed == false) }.forEach { reminder ->
        val kind = if (reminder.snoozedUntil != null) "LegacySnooze" else "Normal"
        results += ReminderOccurrence(occurrenceKey(reminder.id, reminder.effectiveTriggerAt, kind), reminder.id,
            reminder.triggerAt, reminder.effectiveTriggerAt, kind, fingerprint(reminder, parents[reminder.taskId]) + "@" + zone.id)
    }
    return results.distinctBy { it.key }.sortedBy { it.triggerAt }
}
