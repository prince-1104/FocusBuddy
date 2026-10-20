package com.focusbuddy.domain

import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.usecase.*
import com.focusbuddy.domain.time.*
import org.junit.Test
import org.junit.Assert.*
import java.time.*

class EditorDefaultsTest {
    @Test fun defaultsPrefillNewRecordsWithoutChangingExistingDrafts() {
        val date = LocalDate.of(2026, 10, 21)
        val settings = MascotSettings(defaultReminderOffset = 10, defaultAlertSound = false, defaultAlertVibration = false)
        val task = newTaskDraft(date, LocalTime.of(10, 30), settings)
        assertEquals("2026-10-21", task.date); assertEquals("10:30", task.startTime)
        assertFalse(task.allDay); assertEquals(10, task.alerts.single().offsetMinutes)
        assertFalse(task.alerts.single().soundEnabled); assertFalse(task.alerts.single().vibrationEnabled)
        assertTrue(newTaskDraft(date, null, MascotSettings()).alerts.isEmpty())
        val reminder = newReminderDraft(date, LocalTime.of(11, 0), settings)
        assertEquals("11:00", reminder.alert.time); assertNull(reminder.alert.offsetMinutes)
        assertFalse(reminder.alert.soundEnabled)
    }
    @Test fun timeFormattingChangesDisplayWithoutChangingTheInstant() {
        val instant = Instant.parse("2026-10-20T13:05:00Z")
        assertEquals("13:05", timeFormatter(TimeFormat.Hour24).withZone(ZoneOffset.UTC).format(instant))
        assertTrue(timeFormatter(TimeFormat.Hour12).withZone(ZoneOffset.UTC).format(instant).startsWith("1:05"))
    }
}
