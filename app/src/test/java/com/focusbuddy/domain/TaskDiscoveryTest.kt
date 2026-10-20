package com.focusbuddy.domain

import com.focusbuddy.domain.discovery.*
import com.focusbuddy.domain.model.*
import org.junit.Test
import org.junit.Assert.*
import java.time.*
import java.util.UUID

class TaskDiscoveryTest {
    private val now = ZonedDateTime.parse("2026-10-20T10:00:00+05:30[Asia/Kolkata]")
    private val date = now.toLocalDate()
    private val category = UUID.randomUUID()
    private val today = Task(title = "Write report", scheduledDate = date, categoryId = category, priority = TaskPriority.High, notes = "Private keyword")
    private val overdue = Task(title = "Yesterday", scheduledDate = date.minusDays(1))
    private val next = Task(title = "Tomorrow", description = "Planning", scheduledDate = date.plusDays(1))
    private val completed = Task(title = "Finished", scheduledDate = date, completed = true)
    private val all = listOf(today, overdue, next, completed)
    @Test fun eachListUsesActualCompletionAndLocalDates() {
        assertEquals(setOf(today.id, completed.id), discoverTasks(all, TaskFilters(), now).map { it.id }.toSet())
        assertEquals(listOf(next), discoverTasks(all, TaskFilters(list = TaskList.Upcoming), now))
        assertEquals(listOf(overdue), discoverTasks(all, TaskFilters(list = TaskList.Overdue), now))
        assertEquals(listOf(completed), discoverTasks(all, TaskFilters(list = TaskList.Completed), now))
    }
    @Test fun queryCategoryPriorityAndCompletionFiltersComposeWithoutDuplicates() {
        val filter = TaskFilters(query = " KEYWORD ", list = TaskList.All, categoryId = category, priority = TaskPriority.High, completion = CompletionFilter.Remaining)
        assertEquals(listOf(today), discoverTasks(all, filter, now))
        assertTrue(discoverTasks(all, filter.copy(completion = CompletionFilter.Completed), now).isEmpty())
        assertEquals(listOf(next), discoverTasks(all, TaskFilters(query = "PLAN", list = TaskList.All, uncategorized = true), now))
    }
    @Test fun recurrenceUsesCurrentOccurrencesAndExcludesExhaustedUpcomingSeries() {
        val weekly = today.copy(recurrenceRule = "FREQ=WEEKLY;BYDAY=TU", scheduledDate = date.minusWeeks(4))
        val exhausted = next.copy(recurrenceRule = "FREQ=DAILY;COUNT=2", scheduledDate = date.minusDays(4))
        val ended = next.copy(recurrenceRule = "FREQ=DAILY;UNTIL=20261019", scheduledDate = date.minusDays(4))
        assertEquals(listOf(weekly), discoverTasks(listOf(weekly, exhausted, ended), TaskFilters(), now))
        assertEquals(listOf(weekly), discoverTasks(listOf(weekly, exhausted, ended), TaskFilters(list = TaskList.Upcoming), now))
    }
    @Test fun timedOverdueAndTimeZoneBoundaryMatchTheLocalDay() {
        val timed = today.copy(allDay = false, startAt = now.minusMinutes(1).toInstant())
        assertEquals(listOf(timed), discoverTasks(listOf(timed), TaskFilters(list = TaskList.Overdue), now))
        val midnight = Instant.parse("2026-10-19T20:00:00Z")
        assertEquals(listOf(today), discoverTasks(listOf(today), TaskFilters(), midnight.atZone(ZoneId.of("Asia/Kolkata"))))
        assertTrue(discoverTasks(listOf(today), TaskFilters(), midnight.atZone(ZoneOffset.UTC)).isEmpty())
    }
    @Test fun emptyAndManyTasksOnOneDateRemainUniqueAndFilterable() {
        assertTrue(discoverTasks(emptyList(), TaskFilters(), now).isEmpty())
        val many = (1..200).map { today.copy(id = UUID.randomUUID(), title = "Task $it") }
        assertEquals(200, discoverTasks(many, TaskFilters(), now).map { it.id }.toSet().size)
    }
    @Test fun originalInstantsAcrossTimeZonesAndRulesEndingBeforeTheirSeedAreNotMisclassified() {
        val task = today.copy(allDay = false, startAt = now.plusDays(1).minusHours(1).toInstant())
        assertTrue(discoverTasks(listOf(task), TaskFilters(list = TaskList.Overdue), now).isEmpty())
        val noOccurrences = next.copy(scheduledDate = date.plusMonths(2), recurrenceRule = "FREQ=DAILY;UNTIL=20261025")
        assertTrue(discoverTasks(listOf(noOccurrences), TaskFilters(list = TaskList.Upcoming), now).isEmpty())
    }
}
