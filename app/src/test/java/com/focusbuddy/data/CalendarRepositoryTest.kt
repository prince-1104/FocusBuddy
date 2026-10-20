package com.focusbuddy.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focusbuddy.data.local.FocusBuddyDatabase
import com.focusbuddy.data.repository.*
import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.time.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = com.focusbuddy.TestApplication::class)
class CalendarRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "calendar-${UUID.randomUUID()}.db"
    private val database = FocusBuddyDatabase.create(context, name)
    private val clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
    private val tasks = RoomTaskRepository(database, clock)
    private val reminders = RoomReminderRepository(database, clock)
    private val zones = MutableStateFlow(ZoneId.of("America/New_York"))
    private val source = object : TimeZoneSource { override val zones = this@CalendarRepositoryTest.zones }
    private val calendar = RoomCalendarRepository(database, source, Clock.fixed(Instant.parse("1900-01-01T00:00:00Z"), ZoneOffset.UTC))

    @After fun close() { database.close(); context.deleteDatabase(name) }

    @Test fun countsEachAlertIndependentlyAndKeepsTaskDateExplicit() = runTest {
        val date = LocalDate.of(2026, 3, 8)
        val record = Task(title = "DST day", scheduledDate = date)
        tasks.create(record)
        val first = Reminder(taskId = record.id, triggerAt = Instant.parse("2026-03-08T06:30:00Z"))
        val second = Reminder(taskId = record.id, triggerAt = Instant.parse("2026-03-08T07:30:00Z"))
        reminders.create(first); reminders.create(second)
        reminders.create(Reminder(title = "Standalone", triggerAt = Instant.parse("2026-03-08T08:30:00Z")))
        reminders.create(Reminder(title = "Disabled", triggerAt = first.triggerAt, enabled = false))
        val rows = calendar.observeMonth(YearMonth.of(2026, 3)).first()
        assertEquals(31, rows.size)
        assertEquals(DaySummary(date, 1, 3), rows.first { it.date == date })
        assertEquals(DaySummary(date.plusDays(1), 0, 0), rows.first { it.date == date.plusDays(1) })
        tasks.setCompleted(record.id, true)
        assertEquals(DaySummary(date, 1, 1), calendar.observeMonth(YearMonth.of(2026, 3)).first().first { it.date == date })
    }

    @Test fun snoozeMovesAlertToEffectiveDayWithoutMovingTaskOrDoubleCounting() = runTest {
        val date = LocalDate.of(2026, 3, 8)
        val record = tasks.create(Task(title = "Keep the date", scheduledDate = date))
        val alert = Reminder(taskId = record.id, triggerAt = Instant.parse("2026-03-08T06:00:00Z"))
        reminders.create(alert)
        reminders.snooze(alert.id, Instant.parse("2026-03-09T12:00:00Z"))
        val rows = calendar.observeMonth(YearMonth.of(2026, 3)).first()
        assertEquals(DaySummary(date, 1, 0), rows.first { it.date == date })
        assertEquals(DaySummary(date.plusDays(1), 0, 1), rows.first { it.date == date.plusDays(1) })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun activeCollectorRespondsToTimeZoneChangeWithoutDatabaseWrite() = runTest {
        zones.value = ZoneId.of("UTC")
        val date = LocalDate.of(2026, 10, 1)
        tasks.create(Task(title = "Always October first", scheduledDate = date))
        reminders.create(Reminder(title = "Near midnight", triggerAt = Instant.parse("2026-10-01T00:30:00Z")))
        val channel = calendar.observeMonth(YearMonth.of(2026, 10)).produceIn(backgroundScope)
        val before = channel.receive()
        assertEquals(DaySummary(date, 1, 1), before.first())
        zones.value = ZoneId.of("America/Los_Angeles")
        val after = channel.receive()
        assertEquals(DaySummary(date, 1, 0), after.first())
        assertEquals(0, after.sumOf { it.reminderAlertCount })
        channel.cancel()
    }

    @Test fun monthBoundariesUseLocalMidnightAndExcludeNextMonth() = runTest {
        // March starts at 05:00 UTC, April at 04:00 UTC because DST changed.
        val month = YearMonth.of(2026, 3)
        val window = MonthWindow.of(month, zones.value)
        reminders.create(Reminder(title = "Before", triggerAt = window.start.minusMillis(1)))
        reminders.create(Reminder(title = "First", triggerAt = window.start))
        reminders.create(Reminder(title = "Last", triggerAt = window.endExclusive.minusMillis(1)))
        reminders.create(Reminder(title = "After", triggerAt = window.endExclusive))
        val rows = calendar.observeMonth(month).first()
        assertEquals(2, rows.sumOf { it.reminderAlertCount })
        assertEquals(1, rows.first().reminderAlertCount)
        assertEquals(1, rows.last().reminderAlertCount)
        assertEquals(743, Duration.between(window.start, window.endExclusive).toHours())
    }

    @Test fun leapMonthAndFallDstHaveCorrectLengths() = runTest {
        assertEquals(29, calendar.observeMonth(YearMonth.of(2028, 2)).first().size)
        val fall = MonthWindow.of(YearMonth.of(2026, 11), zones.value)
        assertEquals(721, Duration.between(fall.start, fall.endExclusive).toHours())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun recurrenceCountsRefreshAfterEditCompletionDeleteAndDisable() = runTest {
        zones.value = ZoneOffset.UTC
        val date = LocalDate.of(2026, 3, 1)
        val task = tasks.create(Task(title = "Routine", scheduledDate = date, recurrenceRule = "FREQ=DAILY;COUNT=3"))
        val attached = Reminder(taskId = task.id, triggerAt = Instant.parse("2026-03-02T10:00:00Z"), recurrenceRule = "FREQ=DAILY;COUNT=2")
        val standalone = Reminder(title = "Standalone", triggerAt = Instant.parse("2026-03-02T12:00:00Z"))
        reminders.create(attached); reminders.create(standalone)
        val channel = calendar.observeSnapshot(YearMonth.from(date)).produceIn(backgroundScope)
        suspend fun matching(predicate: (com.focusbuddy.domain.calendar.CalendarSnapshot) -> Boolean): com.focusbuddy.domain.calendar.CalendarSnapshot {
            while (true) { val value = channel.receive(); if (predicate(value)) return value }
        }
        val initial = matching { it.tasks.size == 3 && it.alerts.size == 3 }
        assertEquals(2, initial.days[1].reminderAlertCount)
        tasks.update(task.copy(recurrenceRule = "FREQ=DAILY;COUNT=2"))
        matching { it.tasks.size == 2 }
        reminders.update(standalone.copy(triggerAt = Instant.parse("2026-03-04T12:00:00Z")))
        matching { it.days[3].reminderAlertCount == 1 }
        tasks.setCompleted(task.id, true)
        matching { it.alerts.size == 1 && it.tasks.all { row -> row.task.completed } }
        reminders.disable(standalone.id)
        matching { it.alerts.isEmpty() }
        tasks.delete(task.id)
        matching { it.tasks.isEmpty() }
    }

    @Test fun todayAndExploreAgreeForSelectedRecurringDay() = runTest {
        val selected = LocalDate.of(2026, 3, 8)
        val task = tasks.create(Task(title = "Weekly", scheduledDate = selected.minusWeeks(2), recurrenceRule = "FREQ=WEEKLY;COUNT=5"))
        reminders.create(Reminder(title = "Daily", triggerAt = selected.minusDays(2).atTime(10, 0).atZone(zones.value).toInstant(), recurrenceRule = "FREQ=DAILY;COUNT=4"))
        val month = calendar.observeSnapshot(YearMonth.from(selected)).first()
        val day = RoomAgendaRepository(database, clock).observe(selected, selected, zones.value).first()
        assertEquals(month.days.first { it.date == selected }, day.days.first { it.date == selected })
        assertEquals(listOf(task.id), day.tasks.map { it.task.id })
        assertEquals(selected, day.tasks.single().task.scheduledDate)
        assertEquals(1, day.standaloneReminders.size)
    }
}
