package com.focusbuddy.data.repository

import androidx.room.withTransaction
import com.focusbuddy.data.local.FocusBuddyDatabase
import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.repository.TaskRepository
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

class RoomTaskRepository(private val database: FocusBuddyDatabase, private val clock: Clock) : TaskRepository {
    private val tasks = database.tasks()
    private val reminders = database.reminders()
    private val subtasks = database.subtasks()

    override suspend fun create(task: Task, reminders: List<Reminder>, subtasks: List<Subtask>): Task =
        database.withTransaction {
            require(reminders.all { it.taskId == task.id }) { "Reminder belongs to another task" }
            require(subtasks.all { it.taskId == task.id }) { "Subtask belongs to another task" }
            val now = clock.instant().truncatedTo(ChronoUnit.MILLIS)
            val record = task.copy(createdAt = now, updatedAt = now)
            tasks.insert(record.entity()) // ABORT, never REPLACE (which could cascade-delete children).
            this.reminders.insertAll(reminders.map {
                (if (record.completed) it.copy(enabled = false, snoozedUntil = null) else it).entity()
            })
            this.subtasks.insertAll(subtasks.map { it.entity() })
            record
        }

    override suspend fun update(task: Task): Task = database.withTransaction {
        val previous = requireNotNull(tasks.get(task.id)) { "Task does not exist" }
        val now = maxOf(previous.updatedAt, clock.instant().truncatedTo(ChronoUnit.MILLIS))
        val record = task.copy(createdAt = previous.createdAt, updatedAt = now)
        check(tasks.update(record.entity()) == 1)
        if (record.completed) reminders.disableForTask(record.id)
        record
    }

    override suspend fun get(id: UUID): TaskDetails? = tasks.details(id)?.model()
    override suspend fun updateContent(id: UUID, content: TaskContent): Task = database.withTransaction {
        val current = requireNotNull(tasks.get(id)) { "Task no longer exists" }.model()
        update(current.copy(title = content.title, description = content.description,
            scheduledDate = content.scheduledDate, startAt = content.startAt, endAt = content.endAt,
            allDay = content.allDay, priority = content.priority, categoryId = content.categoryId))
    }
    override fun observe(id: UUID) = tasks.observe(id).map { it?.model() }
    override fun observeScheduled(from: LocalDate, through: LocalDate) = run {
        require(through >= from)
        tasks.observeScheduled(from, through).map { records -> records.map { it.model() } }
    }

    override suspend fun setCompleted(id: UUID, completed: Boolean) = database.withTransaction {
        val previous = requireNotNull(tasks.get(id)) { "Task does not exist" }
        val now = maxOf(previous.updatedAt, clock.instant().truncatedTo(ChronoUnit.MILLIS))
        check(tasks.update(previous.copy(completed = completed, updatedAt = now)) == 1)
        if (completed) reminders.disableForTask(id)
        // Undoing completion does not silently restore old alerts.
    }

    override suspend fun replaceSubtasks(taskId: UUID, subtasks: List<Subtask>) = database.withTransaction {
        requireNotNull(tasks.get(taskId)) { "Task does not exist" }
        require(subtasks.all { it.taskId == taskId })
        this.subtasks.deleteForTask(taskId)
        this.subtasks.insertAll(subtasks.map { it.entity() })
    }

    override suspend fun delete(id: UUID) = database.withTransaction {
        tasks.delete(id) // FK cascades reminders/subtasks; focus-session task IDs become null.
    }
}
