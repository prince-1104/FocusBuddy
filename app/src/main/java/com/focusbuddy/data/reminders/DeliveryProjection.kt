package com.focusbuddy.data.reminders

import com.focusbuddy.data.local.OccurrenceEntity
import com.focusbuddy.domain.calendar.*
import com.focusbuddy.domain.reminders.*
import java.time.*

/** Agendas show pending occurrences, never a task-date join or already-consumed alerts. */
fun CalendarSnapshot.withDeliveryState(rows: List<OccurrenceEntity>, now: Instant, sourceReminders: List<com.focusbuddy.domain.model.Reminder> = emptyList(), sourceTasks: List<com.focusbuddy.domain.model.Task> = emptyList()): CalendarSnapshot {
    val ledger = rows.associateBy { it.key }
    fun pending(row: OccurrenceEntity?) = row == null || row.status == "Pending"
    val base = alerts.map { alert ->
        val kind = if (alert.reminder.snoozedUntil == alert.trigger) "LegacySnooze" else "Normal"
        alert.copy(occurrenceId = occurrenceKey(alert.reminder.id, alert.trigger, kind))
    }.filter { alert ->
        val kind = if (alert.reminder.snoozedUntil == alert.trigger) "LegacySnooze" else "Normal"
        val row = ledger[occurrenceKey(alert.reminder.id, alert.trigger, kind)]
        pending(row) && (alert.trigger >= now || (row?.alarmMode in setOf("Exact", "Inexact") && alert.trigger >= now.minus(ReminderEngine.MAX_LATENESS)))
    }
    // Load reminders and parents from the snapshot, including consumed seed alerts, to resolve snoozes.
    val reminders = (sourceReminders + alerts.map { it.reminder }).associateBy { it.id }
    val parents = (sourceTasks + tasks.map { it.task } + alerts.mapNotNull { it.task }).associateBy { it.id }
    val snoozes = rows.filter { it.kind == "Snooze" && it.status == "Pending" &&
        (it.triggerAt >= now || (it.alarmMode in setOf("Exact", "Inexact") && it.triggerAt >= now.minus(ReminderEngine.MAX_LATENESS))) }.mapNotNull { row ->
        val reminder = reminders[row.reminderId] ?: return@mapNotNull null
        val task = parents[reminder.taskId]
        if (!reminder.enabled || (reminder.taskId != null && (task == null || task.completed)) || row.fingerprint != fingerprint(reminder, task)) null
        else AlertOccurrence(reminder, row.triggerAt, task, row.key)
    }.filter { it.trigger.atZone(zone).toLocalDate() in days.first().date..days.last().date }
    val pending = (base + snoozes).distinctBy { it.identity }.sortedBy { it.trigger }
    val counts = pending.groupingBy { it.trigger.atZone(zone).toLocalDate() }.eachCount()
    return copy(alerts = pending, days = days.map { it.copy(reminderAlertCount = counts[it.date] ?: 0) })
}
