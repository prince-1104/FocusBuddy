package com.focusbuddy.domain.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class TaskPriority { Low, Medium, High }

data class Task(
    val id: UUID = UUID.randomUUID(),
    val title: String,
    val description: String? = null,
    val scheduledDate: LocalDate,
    val startAt: Instant? = null,
    val endAt: Instant? = null,
    val allDay: Boolean = true,
    val priority: TaskPriority = TaskPriority.Medium,
    val completed: Boolean = false,
    val categoryId: UUID? = null,
    val recurrenceRule: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
    val notes: String? = null,
) {
    init {
        require(title.isNotBlank()) { "Task title is required" }
        require(!allDay || (startAt == null && endAt == null)) { "All-day tasks cannot have times" }
        require(endAt == null || startAt != null) { "An end time requires a start time" }
        require(startAt == null || endAt == null || endAt >= startAt) { "End precedes start" }
        require(updatedAt >= createdAt) { "Update precedes creation" }
        require(recurrenceRule == null || recurrenceRule.isNotBlank())
    }
}

data class Reminder(
    val id: UUID = UUID.randomUUID(),
    val taskId: UUID? = null,
    val title: String? = null,
    val description: String? = null,
    val triggerAt: Instant,
    val enabled: Boolean = true,
    val recurrenceRule: String? = null,
    /** Null uses the system default notification sound; never stores sound file contents. */
    val soundUri: String? = null,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val snoozedUntil: Instant? = null,
    val offsetMinutes: Int? = null, val followTaskRecurrence: Boolean = false,
) {
    val effectiveTriggerAt: Instant get() = snoozedUntil ?: triggerAt
    init {
        require(taskId != null || !title.isNullOrBlank()) { "Standalone reminders need a title" }
        require(title == null || title.isNotBlank())
        require(offsetMinutes == null || (taskId != null && offsetMinutes in setOf(0, 5, 10, 30)))
        require(!followTaskRecurrence || (taskId != null && offsetMinutes != null))
        require(enabled || snoozedUntil == null) { "Disabled reminders cannot remain snoozed" }
        require(recurrenceRule == null || recurrenceRule.isNotBlank())
    }
}

data class Category(val id: UUID = UUID.randomUUID(), val name: String, val color: Long, val icon: String) {
    init {
        require(name.isNotBlank() && icon.isNotBlank())
        require(color in 0..0xFFFFFFFFL) { "Color must be an ARGB value" }
    }
}

data class Subtask(
    val id: UUID = UUID.randomUUID(), val taskId: UUID, val title: String,
    val completed: Boolean = false, val sortOrder: Int = 0,
) {
    init { require(title.isNotBlank()); require(sortOrder >= 0) }
}

data class TaskDetails(val task: Task, val reminders: List<Reminder>, val subtasks: List<Subtask>)

data class FocusSession(
    val id: UUID = UUID.randomUUID(), val taskId: UUID? = null,
    val startedAt: Instant, val endedAt: Instant? = null,
    /** Actual accumulated focus time, excluding pauses; not inferred from wall-clock time. */
    val durationSeconds: Long = 0, val completed: Boolean = false,
) {
    init {
        require(endedAt == null || endedAt >= startedAt)
        require(durationSeconds >= 0)
        require(!completed || endedAt != null) { "Completed sessions need an end time" }
    }
}

enum class MascotPersonality { Calm, Encouraging, Quiet }

data class MascotSettings(
    val selectedMascot: String = "bunny",
    val personality: MascotPersonality = MascotPersonality.Calm,
    val supportedColors: Set<Long> = setOf(0xFFD9C8FAL),
    val selectedColor: Long = 0xFFD9C8FAL,
    val theme: ThemeMode = ThemeMode.System,
    val animationsEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val showMascot: Boolean = true,
    val weekStart: java.time.DayOfWeek? = null,
) {
    init {
        require(selectedMascot.isNotBlank())
        require(supportedColors.isNotEmpty() && supportedColors.all { it in 0..0xFFFFFFFFL })
        require(selectedColor in supportedColors)
    }
}

data class DaySummary(val date: LocalDate, val taskCount: Int, val reminderAlertCount: Int)
