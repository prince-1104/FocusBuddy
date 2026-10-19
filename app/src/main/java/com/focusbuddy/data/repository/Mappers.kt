package com.focusbuddy.data.repository

import com.focusbuddy.data.local.*
import com.focusbuddy.domain.model.*

internal fun Task.entity() = TaskEntity(id, title, description, scheduledDate, startAt, endAt, allDay,
    priority, completed, categoryId, recurrenceRule, createdAt, updatedAt)
internal fun TaskEntity.model() = Task(id, title, description, scheduledDate, startAt, endAt, allDay,
    priority, completed, categoryId, recurrenceRule, createdAt, updatedAt)
internal fun Reminder.entity() = ReminderEntity(id, taskId, title, description, triggerAt, enabled,
    recurrenceRule, soundUri, soundEnabled, vibrationEnabled, snoozedUntil)
internal fun ReminderEntity.model() = Reminder(id, taskId, title, description, triggerAt, enabled,
    recurrenceRule, soundUri, soundEnabled, vibrationEnabled, snoozedUntil)
internal fun Category.entity() = CategoryEntity(id, name, color, icon)
internal fun CategoryEntity.model() = Category(id, name, color, icon)
internal fun Subtask.entity() = SubtaskEntity(id, taskId, title, completed, sortOrder)
internal fun SubtaskEntity.model() = Subtask(id, taskId, title, completed, sortOrder)
internal fun FocusSession.entity() = FocusSessionEntity(id, taskId, startedAt, endedAt, durationSeconds, completed)
internal fun FocusSessionEntity.model() = FocusSession(id, taskId, startedAt, endedAt, durationSeconds, completed)
internal fun TaskWithRelations.model() = TaskDetails(task.model(),
    reminders.map { it.model() }.sortedWith(compareBy({ it.effectiveTriggerAt }, { it.id })),
    subtasks.map { it.model() }.sortedWith(compareBy({ it.sortOrder }, { it.id })))
