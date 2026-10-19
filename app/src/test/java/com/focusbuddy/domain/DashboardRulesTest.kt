package com.focusbuddy.domain

import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.usecase.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class DashboardRulesTest {
    private val now = ZonedDateTime.parse("2026-10-19T10:00:00Z")
    private val date = now.toLocalDate()

    @Test fun groupsTimedTasksAndReclassifiesWhenTimePasses() {
        val next = Task(title = "Soon", scheduledDate = date, allDay = false, startAt = now.plusHours(1).toInstant())
        val later = Task(title = "Later", scheduledDate = date, allDay = false, startAt = now.plusHours(4).toInstant())
        val snapshot = AgendaSnapshot(listOf(next, later).map { TaskDetails(it, emptyList(), emptyList()) }, emptyList(), emptyList(), emptyList())
        val initial = ObserveDashboard.project(date, now, MascotSettings(), snapshot)
        assertEquals(listOf(TaskSection.Upcoming, TaskSection.LaterToday), initial.groups.map { it.section })
        val after = ObserveDashboard.project(date, now.plusHours(2), MascotSettings(), snapshot)
        assertEquals(TaskSection.Overdue, after.groups.first().section)
        assertEquals(2, after.total)
    }

    @Test fun validationRejectsInvalidDateTitleAndReversedTimes() {
        assertThrows(DraftValidationException::class.java) { SaveTask.validate(TaskDraft(date = date.toString()), ZoneOffset.UTC) }
        assertThrows(DraftValidationException::class.java) { SaveTask.validate(TaskDraft(title = "Plan", date = "2026-02-30"), ZoneOffset.UTC) }
        assertThrows(DraftValidationException::class.java) {
            SaveTask.validate(TaskDraft(title = "Plan", date = date.toString(), allDay = false, startTime = "15:00", endTime = "14:00"), ZoneOffset.UTC)
        }
    }

    @Test fun dateAndTimeValidationHandleDstGapAndOverlapExplicitly() {
        val zone = ZoneId.of("America/New_York")
        assertThrows(DraftValidationException::class.java) {
            SaveTask.validate(TaskDraft(title = "Gap", date = "2026-03-08", allDay = false, startTime = "02:30"), zone)
        }
        val overlap = SaveTask.validate(TaskDraft(title = "Overlap", date = "2026-11-01", allDay = false, startTime = "01:30"), zone)
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), overlap.startAt)
        assertEquals(LocalDate.of(2026, 11, 1), overlap.scheduledDate)
    }

    @Test fun allDayInputHasNoTimestampsAndTrimsText() {
        val content = SaveTask.validate(TaskDraft(title = "  One step  ", description = "  ", date = date.toString(), startTime = "ignored"), ZoneOffset.UTC)
        assertEquals("One step", content.title); assertNull(content.description)
        assertNull(content.startAt); assertNull(content.endAt)
    }
}
