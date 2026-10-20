package com.focusbuddy.data.repository

import androidx.room.withTransaction
import com.focusbuddy.data.local.FocusBuddyDatabase
import com.focusbuddy.domain.calendar.*
import com.focusbuddy.domain.repository.CalendarRepository
import com.focusbuddy.domain.time.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.YearMonth

class RoomCalendarRepository(private val database: FocusBuddyDatabase, private val timeZones: TimeZoneSource) : CalendarRepository {
    private data class Records(val tasks: List<com.focusbuddy.domain.model.Task>, val reminders: List<com.focusbuddy.domain.model.Reminder>, val categories: List<com.focusbuddy.domain.model.Category>, val subtasks: List<com.focusbuddy.domain.model.Subtask>)
    override fun observeMonth(month: YearMonth) = observeSnapshot(month).map { it.days }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSnapshot(month: YearMonth): Flow<CalendarSnapshot> = timeZones.zones.distinctUntilChanged().flatMapLatest { zone ->
        val window = MonthWindow.of(month, zone)
        database.invalidationTracker.createFlow("tasks", "reminders", "categories", "subtasks").map {
            val records = database.withTransaction {
                val reminders = database.reminders().calendarCandidates(window.start, window.endExclusive).map { it.model() }
                val tasks = database.tasks().calendarCandidates(month.atDay(1), month.atEndOfMonth()).map { it.model() }
                val missing = reminders.mapNotNull { it.taskId }.distinct() - tasks.map { it.id }.toSet()
                val allTasks = tasks + database.tasks().getByIds(missing).map { it.model() }
                val children = database.tasks().detailsByIds(allTasks.map { it.id }).flatMap { it.subtasks }.map { it.model() }
                Records(allTasks, reminders, database.categories().getAll().map { it.model() }, children)
            }
            calendarSnapshot(records.tasks, records.reminders, records.categories, month, zone, records.subtasks)
        }
    }.distinctUntilChanged().flowOn(kotlinx.coroutines.Dispatchers.Default)
}
