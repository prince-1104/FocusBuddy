package com.focusbuddy.data.reminders

import com.focusbuddy.data.local.FocusBuddyDatabase
import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.reminders.fingerprint

/** Called inside source-record transactions; content edits must not discard a future snooze. */
internal suspend fun FocusBuddyDatabase.preserveSnoozes(before: Reminder, after: Reminder, oldTask: Task?, newTask: Task?) {
    val timingUnchanged = oldTask?.scheduledDate == newTask?.scheduledDate && oldTask?.startAt == newTask?.startAt &&
        oldTask?.endAt == newTask?.endAt && oldTask?.allDay == newTask?.allDay && oldTask?.recurrenceRule == newTask?.recurrenceRule
    if (!after.enabled || newTask?.completed == true || before.taskId != after.taskId || !timingUnchanged ||
        before.triggerAt != after.triggerAt || before.recurrenceRule != after.recurrenceRule || before.snoozedUntil != after.snoozedUntil ||
        before.offsetMinutes != after.offsetMinutes || before.followTaskRecurrence != after.followTaskRecurrence) return
    val old = fingerprint(before, oldTask); val next = fingerprint(after, newTask)
    if (old == next) return
    occurrences().forReminder(before.id).filter { it.kind == "Snooze" && it.status == "Pending" && it.fingerprint == old }
        .forEach { occurrences().update(it.copy(fingerprint = next)) }
}
