package com.focusbuddy.data.repository

import androidx.room.withTransaction
import com.focusbuddy.data.local.FocusBuddyDatabase
import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.repository.AgendaRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

class RoomAgendaRepository(private val database: FocusBuddyDatabase) : AgendaRepository {
    override fun observe(selected: LocalDate, today: LocalDate, zone: ZoneId) =
        database.invalidationTracker.createFlow("tasks", "reminders", "subtasks", "categories").map {
            database.withTransaction {
                val from = selected.minusDays(14)
                val through = selected.plusDays(14)
                val alerts = database.reminders().enabledBetween(from.atStartOfDay(zone).toInstant(),
                    through.plusDays(1).atStartOfDay(zone).toInstant()).map { it.model() }
                val counts = database.tasks().counts(from, through).associate { it.scheduledDate to it.count }
                val alertCounts = alerts.groupingBy { it.effectiveTriggerAt.atZone(zone).toLocalDate() }.eachCount()
                AgendaSnapshot(
                    tasks = database.tasks().agenda(selected, today).map { it.model() },
                    standaloneReminders = alerts.filter { it.taskId == null && it.effectiveTriggerAt.atZone(zone).toLocalDate() == selected },
                    categories = database.categories().getAll().map { it.model() },
                    days = (0L..28L).map { offset ->
                        val date = from.plusDays(offset)
                        DaySummary(date, counts[date] ?: 0, alertCounts[date] ?: 0)
                    },
                )
            }
        }.distinctUntilChanged()
}
