package com.focusbuddy.domain

import com.focusbuddy.domain.calendar.*
import com.focusbuddy.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ExploreCalendarTest {
    private val zone = ZoneId.of("America/New_York")
    private val month = YearMonth.of(2026, 3)
    private val date = month.atDay(8)
    private fun instant(d: LocalDate, hour: Int = 9) = d.atTime(hour, 0).atZone(zone).toInstant()
    private fun dates(anchor: String, rule: String, from: String, through: String) = Recurrence.dates(LocalDate.parse(anchor), rule, LocalDate.parse(from), LocalDate.parse(through))

    @Test fun everyMonthBoundaryAndWeekStartOverEntireLeapCycle() {
        val starts = mutableSetOf<DayOfWeek>()
        for (year in 2000..2027) for (m in 1..12) for (first in DayOfWeek.entries) {
            val month = YearMonth.of(year, m)
            starts += month.atDay(1).dayOfWeek
            val cells = monthCells(month, first)
            assertEquals(0, cells.size % 7)
            assertEquals((1..month.lengthOfMonth()).map(month::atDay), cells.filterNotNull())
            assertEquals((month.atDay(1).dayOfWeek.value - first.value + 7) % 7, cells.indexOf(month.atDay(1)))
            cells.filterNotNull().forEach { assertEquals((it.dayOfWeek.value - first.value + 7) % 7, cells.indexOf(it) % 7) }
        }
        assertEquals(7, starts.size)
        assertEquals(29, monthCells(YearMonth.of(2024, 2), DayOfWeek.MONDAY).filterNotNull().size)
        assertEquals(28, monthCells(YearMonth.of(2025, 2), DayOfWeek.SUNDAY).filterNotNull().size)
    }
    @Test fun emptyTasksOnlyRemindersOnlyAndBothRemainSeparate() {
        val tasks = listOf(Task(title = "Task", scheduledDate = date), Task(title = "Both", scheduledDate = date.plusDays(2)))
        val reminders = listOf(Reminder(title = "Alone", triggerAt = instant(date.plusDays(1))), Reminder(title = "Both", triggerAt = instant(date.plusDays(2))))
        val snapshot = calendarSnapshot(tasks, reminders, emptyList(), month, zone)
        assertEquals(DaySummary(date, 1, 0), snapshot.days[7])
        assertEquals(DaySummary(date.plusDays(1), 0, 1), snapshot.days[8])
        assertEquals(DaySummary(date.plusDays(2), 1, 1), snapshot.days[9])
        assertEquals(DaySummary(date.plusDays(3), 0, 0), snapshot.days[10])
    }
    @Test fun attachedAlertBelongsToTriggerDateAndExcludesInactiveParents() {
        val task = Task(title = "Different date", scheduledDate = date)
        val alert = Reminder(taskId = task.id, triggerAt = instant(date.plusDays(1)))
        val snapshot = calendarSnapshot(listOf(task), listOf(alert), emptyList(), month, zone)
        assertEquals(0, snapshot.days[7].reminderAlertCount)
        assertEquals(1, snapshot.days[8].reminderAlertCount)
        assertEquals(task, snapshot.alerts.single().task)
        assertTrue(calendarSnapshot(listOf(task.copy(completed = true)), listOf(alert), emptyList(), month, zone).alerts.isEmpty())
        assertTrue(calendarSnapshot(listOf(task), listOf(alert.copy(enabled = false)), emptyList(), month, zone).alerts.isEmpty())
        assertTrue(calendarSnapshot(emptyList(), listOf(alert), emptyList(), month, zone).alerts.isEmpty())
    }
    @Test fun recurringTasksAreUniquePerDateEvenWithDuplicateSelectors() {
        val task = Task(title = "Routine", scheduledDate = date, recurrenceRule = "FREQ=WEEKLY;BYDAY=SU,SU;COUNT=3")
        val snapshot = calendarSnapshot(listOf(task, task), emptyList(), emptyList(), month, zone)
        assertEquals(listOf(date, date.plusWeeks(1), date.plusWeeks(2)), snapshot.tasks.map { it.date })
        assertEquals(3, snapshot.days.sumOf { it.taskCount })
    }
    @Test fun recurrencePreservesWallClockThroughDstAndSnoozesOnlySeed() {
        val start = LocalDate.of(2026, 3, 7)
        val alert = Reminder(title = "Daily", triggerAt = instant(start), recurrenceRule = "FREQ=DAILY;COUNT=3", snoozedUntil = instant(start.plusDays(1), 10))
        val snapshot = calendarSnapshot(emptyList(), listOf(alert), emptyList(), month, zone)
        assertEquals(3, snapshot.alerts.size)
        assertEquals(0, snapshot.days[6].reminderAlertCount)
        assertEquals(2, snapshot.days[7].reminderAlertCount)
        assertEquals(1, snapshot.days[8].reminderAlertCount)
        assertEquals(instant(start.plusDays(2)), snapshot.alerts.last().trigger)
    }
    @Test fun utcUntilIsInclusiveInDeviceZoneAcrossDateBoundary() {
        val east = ZoneId.of("Asia/Tokyo")
        val alert = Reminder(title = "Until", triggerAt = Instant.parse("2026-03-01T15:30:00Z"), recurrenceRule = "FREQ=DAILY;UNTIL=20260302T153000Z")
        val snapshot = calendarSnapshot(emptyList(), listOf(alert), emptyList(), month, east)
        assertEquals(listOf(2, 3), snapshot.alerts.map { it.trigger.atZone(east).dayOfMonth })
    }
    @Test fun dailyIntervalAndCountIncludeEarlierOccurrences() {
        assertEquals(listOf(LocalDate.of(2026, 2, 2)), dates("2026-01-29", "FREQ=DAILY;INTERVAL=2;COUNT=3", "2026-02-01", "2026-02-28"))
    }
    @Test fun weeklySelectorsAndWeekStartControlInterval() {
        val monday = dates("2026-03-01", "FREQ=WEEKLY;INTERVAL=2;BYDAY=SU,MO;WKST=MO", "2026-03-01", "2026-03-16")
        val sunday = dates("2026-03-01", "FREQ=WEEKLY;INTERVAL=2;BYDAY=SU,MO;WKST=SU", "2026-03-01", "2026-03-16")
        assertFalse(monday.contains(LocalDate.of(2026, 3, 2)))
        assertTrue(sunday.contains(LocalDate.of(2026, 3, 2)))
    }
    @Test fun monthlyInvalidDaysAreSkippedRatherThanClamped() {
        assertEquals(listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 31)), dates("2026-01-31", "FREQ=MONTHLY;COUNT=2", "2026-01-01", "2026-04-30"))
    }
    @Test fun monthlyOrdinalsNegativeMonthDaysAndUntil() {
        assertEquals(listOf(LocalDate.of(2026, 3, 30)), dates("2026-01-01", "FREQ=MONTHLY;BYDAY=-1MO;UNTIL=20260331", "2026-03-01", "2026-04-30"))
        assertEquals(listOf(LocalDate.of(2026, 2, 28)), dates("2026-01-01", "FREQ=MONTHLY;BYMONTHDAY=-1", "2026-02-01", "2026-02-28"))
    }
    @Test fun yearlyLeapDayDoesNotShiftToFebruary28() {
        assertEquals(listOf(LocalDate.of(2028, 2, 29)), dates("2024-02-29", "FREQ=YEARLY", "2025-01-01", "2029-12-31"))
    }
    @Test(expected = IllegalArgumentException::class) fun unsupportedRulesAreNeverSilentlyApproximated() {
        dates("2026-01-01", "FREQ=HOURLY", "2026-01-01", "2026-01-31")
    }
    @Test fun boundaryInstantsAndExplicitTaskDateUseDifferentRules() {
        val task = Task(title = "Explicit", scheduledDate = LocalDate.of(2026, 3, 1))
        val alerts = listOf(Reminder(title = "Start", triggerAt = Instant.parse("2026-03-01T05:00:00Z")), Reminder(title = "Before", triggerAt = Instant.parse("2026-03-01T04:59:59Z")), Reminder(title = "End", triggerAt = Instant.parse("2026-04-01T04:00:00Z")))
        val snapshot = calendarSnapshot(listOf(task), alerts, emptyList(), month, zone)
        assertEquals(1, snapshot.days.first().taskCount)
        assertEquals(listOf("Start"), snapshot.alerts.map { it.reminder.title })
    }

    @Test fun yearlyWeekdaySelectorsExpandAcrossYearOrSpecifiedMonths() {
        assertEquals(listOf(LocalDate.of(2026, 1, 5)), dates("2026-01-01", "FREQ=YEARLY;BYDAY=1MO", "2026-01-01", "2026-12-31"))
        assertEquals(listOf(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 6, 1)), dates("2026-01-01", "FREQ=YEARLY;BYMONTH=3,6;BYDAY=1MO", "2026-01-01", "2026-12-31"))
    }
    @Test fun agendaInterleavesTasksAndAttachedAndStandaloneAlertsChronologically() {
        val timed = Task(title = "Ten", scheduledDate = date, allDay = false, startAt = instant(date, 10))
        val allDay = Task(title = "Anytime", scheduledDate = date)
        val before = Reminder(title = "Nine", triggerAt = instant(date, 9))
        val after = Reminder(taskId = timed.id, triggerAt = instant(date, 11))
        val snapshot = calendarSnapshot(listOf(timed, allDay), listOf(after, before), emptyList(), month, zone)
        val entries = snapshot.agenda(date)
        assertEquals(4, entries.size)
        assertEquals(allDay.id, (entries[0] as CalendarEntry.ScheduledTask).occurrence.task.id)
        assertEquals(before.id, (entries[1] as CalendarEntry.ReminderAlert).occurrence.reminder.id)
        assertEquals(timed.id, (entries[2] as CalendarEntry.ScheduledTask).occurrence.task.id)
        assertEquals(after.id, (entries[3] as CalendarEntry.ReminderAlert).occurrence.reminder.id)
        assertEquals(4, entries.map { it.key }.distinct().size)
    }
}
