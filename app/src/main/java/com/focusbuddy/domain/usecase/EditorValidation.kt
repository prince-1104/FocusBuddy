package com.focusbuddy.domain.usecase

import com.focusbuddy.domain.calendar.Recurrence
import com.focusbuddy.domain.model.*
import java.time.*
import java.time.format.*
import java.util.UUID

object EditorValidation {
    fun instant(date: String, time: String, zone: ZoneId, field: String): Instant {
        val local = try { LocalDate.parse(date.trim()).atTime(LocalTime.parse(time.trim(), DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT))) }
        catch (_: DateTimeException) { throw DraftValidationException(field, "Choose a valid date and 24-hour time (HH:mm).") }
        val offsets = zone.rules.getValidOffsets(local)
        if (offsets.isEmpty()) throw DraftValidationException(field, "This time doesn't exist because the clocks change.")
        return local.atOffset(offsets.first()).toInstant()
    }
    fun recurrence(text: String, anchor: LocalDate, field: String): String? {
        val rule = text.trim().uppercase().removePrefix("RRULE:").ifBlank { return null }
        try { Recurrence.dates(anchor, rule, anchor, anchor.plusYears(1)) }
        catch (_: Exception) { throw DraftValidationException(field, "Use a supported daily, weekly, monthly, or yearly recurrence rule.") }
        return rule
    }
    fun alert(draft: AlertDraft, taskId: UUID?, start: Instant?, taskRule: String?, zone: ZoneId,
        now: Instant, allowPast: Boolean, field: String): Reminder {
        val trigger = draft.offsetMinutes?.let { offset ->
            if (offset !in setOf(0, 5, 10, 30)) throw DraftValidationException(field, "Choose a supported alert preset.")
            (start ?: throw DraftValidationException(field, "Set a task date and time before choosing an alert preset.")).minusSeconds(offset * 60L)
        } ?: draft.originalTrigger?.takeIf { original ->
            val local = original.atZone(zone)
            local.toLocalDate().toString() == draft.date.trim() && local.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) == draft.time.trim()
        } ?: instant(draft.date, draft.time, zone, field)
        if (draft.followTaskRecurrence && draft.offsetMinutes == null) throw DraftValidationException(field, "Custom alerts use their own repeat rule.")
        val rule = if (draft.followTaskRecurrence) taskRule else recurrence(draft.recurrenceRule, trigger.atZone(zone).toLocalDate(), field)
        val changed = trigger != draft.originalTrigger || !draft.originalEnabled
        if (draft.enabled && trigger < now && changed && !allowPast)
            throw DraftValidationException("past", "An enabled alert is in the past. Change its time, disable it, or explicitly keep past alerts.")
        return Reminder(id = draft.id, taskId = taskId, title = if (taskId == null) "Reminder" else null,
            triggerAt = trigger, enabled = draft.enabled, recurrenceRule = rule, soundUri = draft.soundUri,
            soundEnabled = draft.soundEnabled, vibrationEnabled = draft.vibrationEnabled,
            snoozedUntil = draft.snoozedUntil?.takeIf { draft.enabled && trigger == draft.originalTrigger },
            offsetMinutes = draft.offsetMinutes, followTaskRecurrence = taskId != null && draft.followTaskRecurrence)
    }
    fun standalone(draft: ReminderDraft, zone: ZoneId, now: Instant): Reminder {
        if (draft.title.isBlank()) throw DraftValidationException("title", "Give your reminder a title.")
        return alert(draft.alert.copy(offsetMinutes = null, followTaskRecurrence = false), null, null, null, zone, now, draft.allowPast, "trigger")
            .copy(title = draft.title.trim(), description = draft.description.trim().ifBlank { null })
    }
}

fun Reminder.toDraft(zone: ZoneId): AlertDraft {
    val local = triggerAt.atZone(zone)
    return AlertDraft(id, local.toLocalDate().toString(), local.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
        offsetMinutes = offsetMinutes, followTaskRecurrence = followTaskRecurrence, enabled = enabled, soundEnabled = soundEnabled, vibrationEnabled = vibrationEnabled, recurrenceRule = recurrenceRule.orEmpty(),
        soundUri = soundUri, snoozedUntil = snoozedUntil, originalTrigger = triggerAt, originalEnabled = enabled)
}

fun TaskDetails.toDraft(zone: ZoneId): TaskDraft {
    val time = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
    return TaskDraft(task.title, task.description.orEmpty(), task.scheduledDate.toString(), task.allDay,
        task.startAt?.let(time::format).orEmpty(), task.endAt?.let(time::format).orEmpty(), task.priority, task.categoryId,
        task.notes.orEmpty(), task.recurrenceRule.orEmpty(), subtasks.map { SubtaskDraft(it.id, it.title, it.completed) }, reminders.map { it.toDraft(zone) }, originalStart = task.startAt, originalEnd = task.endAt, originalDate = task.scheduledDate.toString())
}
