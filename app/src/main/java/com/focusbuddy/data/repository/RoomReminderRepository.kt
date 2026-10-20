package com.focusbuddy.data.repository

import androidx.room.withTransaction
import com.focusbuddy.data.reminders.preserveSnoozes
import com.focusbuddy.data.local.FocusBuddyDatabase
import com.focusbuddy.domain.model.Reminder
import com.focusbuddy.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.util.UUID

class RoomReminderRepository(private val database: FocusBuddyDatabase, private val clock: Clock) : ReminderRepository {
    private val reminders = database.reminders()

    private suspend fun validateAttachment(reminder: Reminder) {
        reminder.taskId?.let { id ->
            val task = requireNotNull(database.tasks().get(id)) { "Task does not exist" }
            require(!reminder.enabled || !task.completed) { "Completed tasks cannot have active reminders" }
        }
    }

    override suspend fun create(reminder: Reminder) = database.withTransaction {
        validateAttachment(reminder)
        reminders.insert(reminder.entity())
    }

    override suspend fun update(reminder: Reminder) = database.withTransaction {
        val previous = requireNotNull(reminders.get(reminder.id)) { "Reminder does not exist" }.model()
        validateAttachment(reminder)
        require(reminders.update(reminder.entity()) == 1) { "Reminder does not exist" }
        database.preserveSnoozes(previous, reminder, previous.taskId?.let { database.tasks().get(it)?.model() },
            reminder.taskId?.let { database.tasks().get(it)?.model() })
    }

    override suspend fun get(id: UUID) = reminders.get(id)?.model()
    override fun observeAll() = reminders.observeAll().map { rows -> rows.map { it.model() } }
    override fun observeEnabled() = reminders.observeEnabled().map { rows -> rows.map { it.model() } }

    override suspend fun snooze(id: UUID, until: Instant) = database.withTransaction {
        require(until > clock.instant()) { "Snooze must end in the future" }
        val reminder = requireNotNull(get(id)) { "Reminder does not exist" }
        require(reminder.enabled) { "Disabled reminders cannot be snoozed" }
        validateAttachment(reminder)
        check(reminders.update(reminder.copy(snoozedUntil = until).entity()) == 1)
    }

    override suspend fun disable(id: UUID) = database.withTransaction {
        val reminder = requireNotNull(get(id)) { "Reminder does not exist" }
        check(reminders.update(reminder.copy(enabled = false, snoozedUntil = null).entity()) == 1)
    }

    override suspend fun delete(id: UUID) { reminders.delete(id) }
}
