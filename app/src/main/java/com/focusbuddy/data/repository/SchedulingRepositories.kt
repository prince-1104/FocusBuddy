package com.focusbuddy.data.repository

import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.repository.*
import com.focusbuddy.domain.reminders.ReminderScheduler
import java.time.Instant
import java.util.UUID

/** Database commits first; a reconciliation request must never turn a successful save into a retry. */
class SchedulingTaskRepository(private val source: TaskRepository, private val scheduler: ReminderScheduler) : TaskRepository by source {
    override suspend fun create(task: Task, reminders: List<Reminder>, subtasks: List<Subtask>) = source.create(task, reminders, subtasks).also { scheduler.notifyAfterCommit() }
    override suspend fun update(task: Task) = source.update(task).also { scheduler.notifyAfterCommit() }
    override suspend fun updateContent(id: UUID, content: TaskContent) = source.updateContent(id, content).also { scheduler.notifyAfterCommit() }
    override suspend fun updateAggregate(id: UUID, content: TaskContent, notes: String?, recurrenceRule: String?, reminders: List<Reminder>, subtasks: List<Subtask>) =
        source.updateAggregate(id, content, notes, recurrenceRule, reminders, subtasks).also { scheduler.notifyAfterCommit() }
    override suspend fun setCompleted(id: UUID, completed: Boolean) { source.setCompleted(id, completed); scheduler.notifyAfterCommit() }
    override suspend fun delete(id: UUID) { source.delete(id); scheduler.notifyAfterCommit() }
}
class SchedulingReminderRepository(private val source: ReminderRepository, private val scheduler: ReminderScheduler) : ReminderRepository by source {
    override suspend fun create(reminder: Reminder) { source.create(reminder); scheduler.notifyAfterCommit() }
    override suspend fun update(reminder: Reminder) { source.update(reminder); scheduler.notifyAfterCommit() }
    override suspend fun snooze(id: UUID, until: Instant) { source.snooze(id, until); scheduler.notifyAfterCommit() }
    override suspend fun disable(id: UUID) { source.disable(id); scheduler.notifyAfterCommit() }
    override suspend fun delete(id: UUID) { source.delete(id); scheduler.notifyAfterCommit() }
}

private fun ReminderScheduler.notifyAfterCommit() {
    try { requestReconciliation() }
    catch (_: Exception) {
        // A platform adapter owns its retry/reconciliation policy. The committed record is
        // still the source of truth; reporting a failed save here could create a duplicate.
    }
}
