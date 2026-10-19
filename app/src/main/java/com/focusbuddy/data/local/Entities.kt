package com.focusbuddy.data.local

import androidx.room.*
import com.focusbuddy.domain.model.TaskPriority
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class DatabaseConverters {
    @TypeConverter fun uuid(value: String?): UUID? = value?.let(UUID::fromString)
    @TypeConverter fun uuid(value: UUID?): String? = value?.toString()
    @TypeConverter fun instant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
    @TypeConverter fun instant(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun date(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)
    @TypeConverter fun date(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun priority(value: String): TaskPriority = TaskPriority.valueOf(value)
    @TypeConverter fun priority(value: TaskPriority): String = value.name
}

@Entity(tableName = "categories")
data class CategoryEntity(@PrimaryKey val id: UUID, val name: String, val color: Long, val icon: String)

@Entity(tableName = "tasks", foreignKeys = [ForeignKey(
    entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"],
    onDelete = ForeignKey.SET_NULL,
)], indices = [Index(value = ["scheduledDate", "completed"]), Index("categoryId")])
data class TaskEntity(
    @PrimaryKey val id: UUID, val title: String, val description: String?, val scheduledDate: LocalDate,
    val startAt: Instant?, val endAt: Instant?, val allDay: Boolean, val priority: TaskPriority,
    val completed: Boolean, val categoryId: UUID?, val recurrenceRule: String?,
    val createdAt: Instant, val updatedAt: Instant,
)

@Entity(tableName = "reminders", foreignKeys = [ForeignKey(
    entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"],
    onDelete = ForeignKey.CASCADE,
)], indices = [Index("taskId"), Index(value = ["enabled", "triggerAt"]), Index(value = ["enabled", "snoozedUntil"])])
data class ReminderEntity(
    @PrimaryKey val id: UUID, val taskId: UUID?, val title: String?, val description: String?,
    val triggerAt: Instant, val enabled: Boolean, val recurrenceRule: String?, val soundUri: String?,
    val soundEnabled: Boolean, val vibrationEnabled: Boolean, val snoozedUntil: Instant?,
)

@Entity(tableName = "subtasks", foreignKeys = [ForeignKey(
    entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"],
    onDelete = ForeignKey.CASCADE,
)], indices = [Index(value = ["taskId", "sortOrder"])])
data class SubtaskEntity(
    @PrimaryKey val id: UUID, val taskId: UUID, val title: String, val completed: Boolean, val sortOrder: Int,
)

@Entity(tableName = "focus_sessions", foreignKeys = [ForeignKey(
    entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"],
    onDelete = ForeignKey.SET_NULL,
)], indices = [Index("taskId"), Index("startedAt")])
data class FocusSessionEntity(
    @PrimaryKey val id: UUID, val taskId: UUID?, val startedAt: Instant, val endedAt: Instant?,
    val durationSeconds: Long, val completed: Boolean,
)

data class TaskWithRelations(
    @Embedded val task: TaskEntity,
    @Relation(parentColumn = "id", entityColumn = "taskId") val reminders: List<ReminderEntity>,
    @Relation(parentColumn = "id", entityColumn = "taskId") val subtasks: List<SubtaskEntity>,
)

data class TaskDateCount(val scheduledDate: LocalDate, val count: Int)
