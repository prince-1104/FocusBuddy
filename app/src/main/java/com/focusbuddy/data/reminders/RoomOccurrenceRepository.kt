package com.focusbuddy.data.reminders

import androidx.room.withTransaction
import com.focusbuddy.data.local.FocusBuddyDatabase
import com.focusbuddy.data.repository.model
import com.focusbuddy.domain.reminders.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

class RoomOccurrenceRepository(private val db: FocusBuddyDatabase) : ReminderOccurrenceRepository {
    override fun observe(key: String) = db.invalidationTracker.createFlow("reminder_occurrences", "reminders", "tasks").map {
        db.withTransaction {
            val row = db.occurrences().get(key)
            val reminder = row?.let { db.reminders().get(it.reminderId)?.model() }
            val task = reminder?.taskId?.let { db.tasks().get(it)?.model() }
            ReminderContext(reminder, task, row?.triggerAt, row?.status ?: "Deleted")
        }
    }.flowOn(Dispatchers.IO)
    override val health = db.occurrences().health().flowOn(Dispatchers.IO)
}
