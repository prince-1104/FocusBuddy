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
    override fun observeMonth(month: YearMonth) = observeSnapshot(month).map { it.days }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSnapshot(month: YearMonth): Flow<CalendarSnapshot> = timeZones.zones.distinctUntilChanged().flatMapLatest { zone ->
        val window = MonthWindow.of(month, zone)
        database.invalidationTracker.createFlow("tasks", "reminders", "categories").map {
            val records = database.withTransaction {
                val reminders = database.reminders().calendarCandidates(window.start, window.endExclusive).map { it.model() }
                val tasks = database.tasks().calendarCandidates(month.atDay(1), month.atEndOfMonth()).map { it.model() }
                val missing = reminders.mapNotNull { it.taskId }.distinct() - tasks.map { it.id }.toSet()
                Triple(tasks + database.tasks().getByIds(missing).map { it.model() }, reminders, database.categories().getAll().map { it.model() })
            }
            calendarSnapshot(records.first, records.second, records.third, month, zone)
        }
    }.distinctUntilChanged().flowOn(kotlinx.coroutines.Dispatchers.Default)
}
