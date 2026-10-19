package com.focusbuddy.data.repository

import androidx.room.withTransaction
import com.focusbuddy.data.local.FocusBuddyDatabase
import com.focusbuddy.domain.model.DaySummary
import com.focusbuddy.domain.repository.CalendarRepository
import com.focusbuddy.domain.time.MonthWindow
import com.focusbuddy.domain.time.TimeZoneSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.YearMonth

class RoomCalendarRepository(private val database: FocusBuddyDatabase, private val timeZones: TimeZoneSource) : CalendarRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeMonth(month: YearMonth) = timeZones.zones.distinctUntilChanged().flatMapLatest { zone ->
        val window = MonthWindow.of(month, zone)
        database.invalidationTracker.createFlow("tasks", "reminders").map {
            database.withTransaction {
                val tasks = database.tasks().counts(month.atDay(1), month.atEndOfMonth())
                    .associate { row -> row.scheduledDate to row.count }
                val alerts = database.reminders().enabledBetween(window.start, window.endExclusive)
                    .groupingBy { row -> (row.snoozedUntil ?: row.triggerAt).atZone(zone).toLocalDate() }
                    .eachCount()
                (1..month.lengthOfMonth()).map { day ->
                    val date = month.atDay(day)
                    DaySummary(date, tasks[date] ?: 0, alerts[date] ?: 0)
                }
            }
        }
    }.distinctUntilChanged()
}
