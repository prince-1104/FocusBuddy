package com.focusbuddy.domain.calendar

import com.focusbuddy.domain.model.*
import java.time.*

data class TaskOccurrence(val task: Task, val date: LocalDate, val start: Instant?, val subtasksCompleted: Int = 0, val subtasksTotal: Int = 0)
data class AlertOccurrence(val reminder: Reminder, val trigger: Instant, val task: Task?, val occurrenceId: String? = null) {
    val identity: String get() = occurrenceId ?: "${reminder.id}:$trigger"
}
data class CalendarSnapshot(val days: List<DaySummary>, val tasks: List<TaskOccurrence>, val alerts: List<AlertOccurrence>, val categories: List<Category>, val zone: ZoneId)

fun calendarSnapshot(tasks: List<Task>, reminders: List<Reminder>, categories: List<Category>, month: YearMonth, zone: ZoneId, subtasks: List<Subtask> = emptyList()): CalendarSnapshot {
    val from = month.atDay(1)
    val through = month.atEndOfMonth()
    val children = subtasks.groupBy { it.taskId }
    val occurrences = tasks.flatMap { task -> Recurrence.dates(task.scheduledDate, task.recurrenceRule, from, through).map { date ->
        TaskOccurrence(task, date, if (date == task.scheduledDate) task.startAt else task.startAt?.atZone(zone)?.let { date.atTime(it.toLocalTime()).atZone(zone).toInstant() }, children[task.id].orEmpty().count { it.completed }, children[task.id].orEmpty().size)
    } }.distinctBy { it.task.id to it.date }
    val parents = tasks.associateBy { it.id }
    val alerts = reminders.filter { it.enabled && (it.taskId == null || parents[it.taskId]?.completed == false) }.flatMap { reminder ->
        val anchor = reminder.triggerAt.atZone(zone)
        val rawUntil = reminder.recurrenceRule?.removePrefix("RRULE:")?.split(';')?.firstOrNull { it.startsWith("UNTIL=") }?.substringAfter('=')
        val instantLimit = rawUntil?.takeIf { it.endsWith('Z') }?.let { LocalDateTime.parse(it.dropLast(1), java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")).toInstant(ZoneOffset.UTC) }
        val localRule = if (instantLimit == null) reminder.recurrenceRule else reminder.recurrenceRule?.replace("UNTIL=$rawUntil", "UNTIL=" + instantLimit.atZone(zone).toLocalDate().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE))
        val parent = parents[reminder.taskId]
        val triggers = if (reminder.followTaskRecurrence && reminder.offsetMinutes != null && parent != null) {
            Recurrence.dates(parent.scheduledDate, parent.recurrenceRule, from.minusDays(1), through.plusDays(1)).map { date ->
                val start = if (date == parent.scheduledDate && parent.startAt != null) parent.startAt else
                    date.atTime(parent.startAt?.atZone(zone)?.toLocalTime() ?: LocalTime.of(9, 0)).atZone(zone).toInstant()
                start.minusSeconds(reminder.offsetMinutes * 60L)
            }.filter { it.atZone(zone).toLocalDate() in from..through }.toMutableList()
        } else {
            val dates = Recurrence.dates(anchor.toLocalDate(), localRule, from, through)
            dates.map { if (it == anchor.toLocalDate()) reminder.triggerAt else it.atTime(anchor.toLocalTime()).atZone(zone).toInstant() }.toMutableList()
        }
        // Snoozing moves the original alert only, never every future occurrence in its series.
        if (reminder.snoozedUntil != null) {
            triggers.remove(reminder.triggerAt)
            if (reminder.snoozedUntil.atZone(zone).toLocalDate() in from..through) triggers += reminder.snoozedUntil
        }
        triggers.distinct().filter { instantLimit == null || it <= instantLimit || it == reminder.snoozedUntil }.map { AlertOccurrence(reminder, it, parents[reminder.taskId]) }
    }.distinctBy { it.reminder.id to it.trigger }
    val taskCounts = occurrences.groupingBy { it.date }.eachCount()
    val alertCounts = alerts.groupingBy { it.trigger.atZone(zone).toLocalDate() }.eachCount()
    return CalendarSnapshot((1..month.lengthOfMonth()).map { day -> val date = month.atDay(day); DaySummary(date, taskCounts[date] ?: 0, alertCounts[date] ?: 0) }, occurrences, alerts.sortedBy { it.trigger }, categories, zone)
}

sealed interface CalendarEntry {
    val key: String
    val time: Instant?
    data class ScheduledTask(val occurrence: TaskOccurrence) : CalendarEntry {
        override val key = "task:${occurrence.task.id}:${occurrence.date}"
        override val time = occurrence.start
    }
    data class ReminderAlert(val occurrence: AlertOccurrence) : CalendarEntry {
        override val key = "alert:${occurrence.identity}"
        override val time = occurrence.trigger
    }
}
fun CalendarSnapshot.agenda(date: LocalDate): List<CalendarEntry> =
    (tasks.filter { it.date == date }.map { CalendarEntry.ScheduledTask(it) } +
        alerts.filter { it.trigger.atZone(zone).toLocalDate() == date }.map { CalendarEntry.ReminderAlert(it) })
        .sortedWith(compareBy<CalendarEntry> { it.time ?: Instant.MIN }.thenBy { it.key })
