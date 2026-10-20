package com.focusbuddy.data.repository

import androidx.room.withTransaction
import com.focusbuddy.data.local.FocusBuddyDatabase
import com.focusbuddy.domain.calendar.calendarSnapshot
import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.repository.AgendaRepository
import kotlinx.coroutines.flow.*
import java.time.*

class RoomAgendaRepository(private val database: FocusBuddyDatabase) : AgendaRepository {
    override fun observe(selected: LocalDate, today: LocalDate, zone: ZoneId) =
        database.invalidationTracker.createFlow("tasks", "reminders", "subtasks", "categories").map {
            val from = selected.minusDays(14)
            val through = selected.plusDays(14)
            val records = database.withTransaction {
                val reminders = database.reminders().calendarCandidates(from.atStartOfDay(zone).toInstant(),
                    through.plusDays(1).atStartOfDay(zone).toInstant()).map { it.model() }
                val tasks = database.tasks().calendarCandidates(from, through).map { it.model() }
                val missing = reminders.mapNotNull { it.taskId }.distinct() - tasks.map { it.id }.toSet()
                val allTasks = tasks + database.tasks().getByIds(missing).map { it.model() }
                val details = database.tasks().detailsByIds(allTasks.map { it.id }).map { it.model() }
                val overdue = if (selected == today) database.tasks().agenda(selected, today).map { it.model() }
                    .filter { it.task.scheduledDate < today && it.task.recurrenceRule == null } else emptyList()
                Records(allTasks, reminders, database.categories().getAll().map { it.model() }, details, overdue)
            }
            val snapshots = generateSequence(YearMonth.from(from)) { it.plusMonths(1) }
                .takeWhile { it <= YearMonth.from(through) }
                .map { calendarSnapshot(records.tasks, records.reminders, records.categories, it, zone, records.details.flatMap { details -> details.subtasks }) }.toList()
            val snapshot = snapshots.first { YearMonth.from(it.days.first().date) == YearMonth.from(selected) }
            val selectedTasks = snapshot.tasks.filter { it.date == selected }.map { occurrence ->
                val details = records.details.first { it.task.id == occurrence.task.id }
                val shift = occurrence.start?.let { start -> details.task.startAt?.let { Duration.between(it, start) } }
                details.copy(task = details.task.copy(scheduledDate = selected, startAt = occurrence.start,
                    endAt = details.task.endAt?.let { if (shift == null) it else it.plus(shift) }))
            }
            AgendaSnapshot((selectedTasks + records.overdue).distinctBy { it.task.id },
                snapshot.alerts.filter { it.reminder.taskId == null && it.trigger.atZone(zone).toLocalDate() == selected }
                    .map { it.reminder.copy(triggerAt = it.trigger, snoozedUntil = null) },
                records.categories, snapshots.flatMap { it.days }.filter { it.date in from..through })
        }.distinctUntilChanged().flowOn(kotlinx.coroutines.Dispatchers.Default)

    private data class Records(val tasks: List<Task>, val reminders: List<Reminder>, val categories: List<Category>,
        val details: List<TaskDetails>, val overdue: List<TaskDetails>)
}
