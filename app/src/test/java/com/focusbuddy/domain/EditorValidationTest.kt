package com.focusbuddy.domain

import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.usecase.*
import org.junit.Test
import org.junit.Assert.*
import java.time.*
import java.util.UUID

class EditorValidationTest {
    private val zone = ZoneId.of("America/New_York")
    private val now = Instant.parse("2026-10-20T12:00:00Z")
    @Test fun presetsCrossDayBoundariesUsingActualInstants() {
        val start = EditorValidation.instant("2026-10-21", "00:05", zone, "trigger")
        val alert = EditorValidation.alert(AlertDraft(offsetMinutes = 10), UUID.randomUUID(), start, null, zone, now, false, "alert")
        assertEquals(start.minusSeconds(600), alert.triggerAt)
        assertEquals(LocalDate.of(2026, 10, 20), alert.triggerAt.atZone(zone).toLocalDate())
    }
    @Test fun pastAlertsRequireIntentAndDisabledAlertsCanBeStored() {
        val draft = ReminderDraft("Past", alert = AlertDraft(date = "2026-10-19", time = "09:00"))
        val error = assertThrows(DraftValidationException::class.java) { EditorValidation.standalone(draft, zone, now) }
        assertEquals("past", error.field)
        assertTrue(EditorValidation.standalone(draft.copy(allowPast = true), zone, now).enabled)
        assertFalse(EditorValidation.standalone(draft.copy(alert = draft.alert.copy(enabled = false)), zone, now).enabled)
    }
    @Test fun titleValidationAndInvalidClockTimesAreActionable() {
        assertEquals("title", assertThrows(DraftValidationException::class.java) {
            EditorValidation.standalone(ReminderDraft(alert = AlertDraft(date = "2026-10-21")), zone, now)
        }.field)
        assertThrows(DraftValidationException::class.java) { EditorValidation.instant("2026-02-30", "10:00", zone, "trigger") }
        assertThrows(DraftValidationException::class.java) { EditorValidation.instant("2026-03-08", "02:30", zone, "trigger") }
        assertThrows(DraftValidationException::class.java) { EditorValidation.instant("2026-10-21", "25:00", zone, "trigger") }
    }
    @Test fun existingExactTriggerAndSnoozeSurviveAnUnchangedEdit() {
        val original = Reminder(title = "Retained", triggerAt = Instant.parse("2026-11-01T06:30:42Z"), snoozedUntil = Instant.parse("2026-11-01T07:00:00Z"))
        val edited = EditorValidation.standalone(ReminderDraft("New title", alert = original.toDraft(zone)), zone, now)
        assertEquals(original.triggerAt, edited.triggerAt)
        assertEquals(original.snoozedUntil, edited.snoozedUntil)
        val moved = EditorValidation.standalone(ReminderDraft("Moved", alert = original.toDraft(zone).copy(time = "03:30")), zone, now)
        assertNull(moved.snoozedUntil)
    }
    @Test fun disablingClearsSnoozeAndChangingTimeRequiresNewPastConsent() {
        val old = Reminder(title = "Old", triggerAt = now.minusSeconds(86400), snoozedUntil = now.plusSeconds(3600))
        assertNotNull(EditorValidation.standalone(ReminderDraft("Old", alert = old.toDraft(zone)), zone, now))
        val disabled = EditorValidation.standalone(ReminderDraft("Old", alert = old.toDraft(zone).copy(enabled = false)), zone, now)
        assertNull(disabled.snoozedUntil)
        assertThrows(DraftValidationException::class.java) { EditorValidation.standalone(ReminderDraft("Changed", alert = old.toDraft(zone).copy(time = "01:00")), zone, now) }
    }
    @Test fun recurrenceIsValidatedOutsideComposables() {
        assertEquals("FREQ=WEEKLY;COUNT=3", EditorValidation.recurrence("freq=weekly;count=3", LocalDate.of(2026, 10, 20), "repeat"))
        assertNull(EditorValidation.recurrence("", LocalDate.now(), "repeat"))
        assertThrows(DraftValidationException::class.java) { EditorValidation.recurrence("FREQ=HOURLY", LocalDate.now(), "repeat") }
    }
    @Test fun taskValidationRejectsReverseTimesAndPreservesExactExistingTimes() {
        assertThrows(DraftValidationException::class.java) { SaveTask.validate(TaskDraft(title = "Reverse", date = "2026-10-21", allDay = false, startTime = "12:00", endTime = "11:00"), zone) }
        val task = Task(title = "Precise", scheduledDate = LocalDate.of(2026, 11, 1), allDay = false, startAt = Instant.parse("2026-11-01T06:30:42Z"))
        assertEquals(task.startAt, SaveTask.validate(TaskDetails(task, emptyList(), emptyList()).toDraft(zone), zone).startAt)
    }

    @Test fun relativeRecurringAlertUsesTaskWeekdayBeforeMidnight() {
        val taskDate = LocalDate.of(2026, 10, 20) // Tuesday
        val task = Task(title = "Early Tuesday", scheduledDate = taskDate, allDay = false,
            startAt = taskDate.atTime(0, 5).atZone(zone).toInstant(), recurrenceRule = "FREQ=WEEKLY;BYDAY=TU;COUNT=2")
        val alert = EditorValidation.alert(AlertDraft(offsetMinutes = 10, followTaskRecurrence = true), task.id,
            task.startAt, task.recurrenceRule, zone, Instant.MIN, true, "alert")
        val month = com.focusbuddy.domain.calendar.calendarSnapshot(listOf(task), listOf(alert), emptyList(), YearMonth.of(2026, 10), zone)
        assertEquals(listOf(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 26)), month.alerts.map { it.trigger.atZone(zone).toLocalDate() })
        assertEquals(0, month.days.first { it.date == taskDate }.reminderAlertCount)
    }

    @Test fun unchangedTaskEditPreservesExplicitPlacementAndInstantAfterTimezoneChange() {
        val task = Task(title = "Placed explicitly", scheduledDate = LocalDate.of(2026, 10, 21), allDay = false,
            startAt = Instant.parse("2026-10-22T00:30:00Z"))
        val newZone = ZoneId.of("Asia/Tokyo")
        val draft = TaskDetails(task, emptyList(), emptyList()).toDraft(newZone)
        assertEquals(task.startAt, SaveTask.validate(draft.copy(title = "Only title changed"), newZone).startAt)
        assertNotEquals(task.startAt, SaveTask.validate(draft.copy(date = "2026-10-23"), newZone).startAt)
    }
}
