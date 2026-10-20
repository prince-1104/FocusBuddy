package com.focusbuddy.domain.repository

import com.focusbuddy.domain.model.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

interface TaskRepository {
    /** Atomic aggregate creation. All supplied children must belong to the new task. */
    suspend fun create(task: Task, reminders: List<Reminder> = emptyList(), subtasks: List<Subtask> = emptyList()): Task
    suspend fun update(task: Task): Task
    /** Atomically replace edited content and children, preserving latest parent completion. */
    suspend fun updateAggregate(id: UUID, content: TaskContent, notes: String?, recurrenceRule: String?, reminders: List<Reminder>, subtasks: List<Subtask>): Task
    suspend fun setSubtaskCompleted(taskId: UUID, id: UUID, completed: Boolean)
    /** Edits content while preserving the latest completion state and recurrence metadata. */
    suspend fun updateContent(id: UUID, content: TaskContent): Task
    suspend fun get(id: UUID): TaskDetails?
    fun observe(id: UUID): Flow<TaskDetails?>
    fun observeScheduled(from: LocalDate, through: LocalDate): Flow<List<Task>>
    suspend fun setCompleted(id: UUID, completed: Boolean)
    suspend fun replaceSubtasks(taskId: UUID, subtasks: List<Subtask>)
    suspend fun delete(id: UUID)
}

interface ReminderRepository {
    suspend fun create(reminder: Reminder)
    suspend fun update(reminder: Reminder)
    suspend fun get(id: UUID): Reminder?
    fun observeAll(): Flow<List<Reminder>>
    /** Includes overdue reminders for later delivery reconciliation. No OS alarm is scheduled here. */
    fun observeEnabled(): Flow<List<Reminder>>
    suspend fun snooze(id: UUID, until: Instant)
    suspend fun disable(id: UUID)
    suspend fun delete(id: UUID)
}

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    suspend fun create(category: Category)
    suspend fun update(category: Category)
    suspend fun delete(id: UUID)
}

interface FocusSessionRepository {
    fun observeAll(): Flow<List<FocusSession>>
    suspend fun get(id: UUID): FocusSession?
    suspend fun create(session: FocusSession)
    suspend fun update(session: FocusSession)
    suspend fun delete(id: UUID)
}

interface PreferencesRepository {
    val settings: Flow<MascotSettings>
    /** Atomic single-key change; preserves concurrent mascot preference updates. */
    suspend fun setWeekStart(day: java.time.DayOfWeek?)
    /** Atomic single-key appearance change. */
    suspend fun setTheme(mode: ThemeMode)
    /** Explicit replacement of all settings. */
    suspend fun setSettings(settings: MascotSettings)
    suspend fun setMascotVisible(visible: Boolean)
    suspend fun setAnimationsEnabled(enabled: Boolean)
}

interface CalendarRepository {
    fun observeSnapshot(month: YearMonth): Flow<com.focusbuddy.domain.calendar.CalendarSnapshot>
    /** Emits a row for every date, including zeroes; responds to device timezone changes. */
    fun observeMonth(month: YearMonth): Flow<List<DaySummary>>
}

interface AgendaRepository {
    fun observe(selected: LocalDate, today: LocalDate, zone: java.time.ZoneId): Flow<AgendaSnapshot>
}
