package com.focusbuddy.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY scheduledDate, startAt, createdAt, id") fun observeAll(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks") suspend fun all(): List<TaskEntity>
    @Query("SELECT * FROM tasks WHERE scheduledDate <= :through AND (scheduledDate >= :from OR recurrenceRule IS NOT NULL)")
    suspend fun calendarCandidates(from: LocalDate, through: LocalDate): List<TaskEntity>
    @Query("SELECT * FROM tasks WHERE id IN (:ids)") suspend fun getByIds(ids: List<UUID>): List<TaskEntity>
    @Transaction @Query("SELECT * FROM tasks WHERE id IN (:ids)") suspend fun detailsByIds(ids: List<UUID>): List<TaskWithRelations>
    @Insert suspend fun insert(task: TaskEntity)
    @Update suspend fun update(task: TaskEntity): Int
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun get(id: UUID): TaskEntity?
    @Transaction @Query("SELECT * FROM tasks WHERE id = :id") suspend fun details(id: UUID): TaskWithRelations?
    @Transaction @Query("SELECT * FROM tasks WHERE id = :id") fun observe(id: UUID): Flow<TaskWithRelations?>
    @Query("SELECT * FROM tasks WHERE scheduledDate BETWEEN :from AND :through ORDER BY scheduledDate, startAt, createdAt, id")
    fun observeScheduled(from: LocalDate, through: LocalDate): Flow<List<TaskEntity>>
    @Query("SELECT scheduledDate, COUNT(*) AS count FROM tasks WHERE scheduledDate BETWEEN :from AND :through GROUP BY scheduledDate")
    suspend fun counts(from: LocalDate, through: LocalDate): List<TaskDateCount>
    @Query("DELETE FROM tasks WHERE id = :id") suspend fun delete(id: UUID)
    @Transaction
    @Query("""SELECT * FROM tasks WHERE scheduledDate = :selected
        OR (:selected = :today AND completed = 0 AND scheduledDate < :today)
        ORDER BY scheduledDate, startAt, createdAt, id""")
    suspend fun agenda(selected: LocalDate, today: LocalDate): List<TaskWithRelations>
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE id IN (:ids)") suspend fun getByIds(ids: List<UUID>): List<ReminderEntity>
    @Query("SELECT * FROM reminders") suspend fun all(): List<ReminderEntity>
    @Query("SELECT * FROM reminders WHERE taskId = :taskId") suspend fun forTask(taskId: UUID): List<ReminderEntity>
    @Query("""SELECT r.* FROM reminders r LEFT JOIN tasks t ON t.id = r.taskId
        WHERE r.enabled = 1 AND (r.taskId IS NULL OR t.completed = 0)
        AND (r.recurrenceRule IS NOT NULL OR (r.triggerAt >= :start AND r.triggerAt < :end)
            OR (r.snoozedUntil >= :start AND r.snoozedUntil < :end))""")
    suspend fun calendarCandidates(start: Instant, end: Instant): List<ReminderEntity>
    @Insert suspend fun insert(reminder: ReminderEntity)
    @Insert suspend fun insertAll(reminders: List<ReminderEntity>)
    @Update suspend fun update(reminder: ReminderEntity): Int
    @Query("SELECT * FROM reminders WHERE id = :id") suspend fun get(id: UUID): ReminderEntity?
    @Query("SELECT * FROM reminders ORDER BY COALESCE(snoozedUntil, triggerAt), id")
    fun observeAll(): Flow<List<ReminderEntity>>
    @Query("""SELECT r.* FROM reminders r LEFT JOIN tasks t ON t.id = r.taskId
        WHERE r.enabled = 1 AND (r.taskId IS NULL OR t.completed = 0)
        ORDER BY COALESCE(r.snoozedUntil, r.triggerAt), r.id""")
    fun observeEnabled(): Flow<List<ReminderEntity>>
    @Query("""SELECT r.* FROM reminders r LEFT JOIN tasks t ON t.id = r.taskId
        WHERE r.enabled = 1 AND (r.taskId IS NULL OR t.completed = 0)
        AND ((r.snoozedUntil IS NULL AND r.triggerAt >= :start AND r.triggerAt < :end)
            OR (r.snoozedUntil >= :start AND r.snoozedUntil < :end))
        ORDER BY COALESCE(r.snoozedUntil, r.triggerAt), r.id""")
    suspend fun enabledBetween(start: Instant, end: Instant): List<ReminderEntity>
    @Query("DELETE FROM reminders WHERE taskId = :taskId") suspend fun deleteForTask(taskId: UUID)
    @Query("UPDATE reminders SET enabled = 0, snoozedUntil = NULL WHERE taskId = :taskId")
    suspend fun disableForTask(taskId: UUID)
    @Query("DELETE FROM reminders WHERE id = :id") suspend fun delete(id: UUID)
}

@Dao
interface SubtaskDao {
    @Query("UPDATE subtasks SET completed = :completed WHERE id = :id AND taskId = :taskId")
    suspend fun setCompleted(taskId: UUID, id: UUID, completed: Boolean): Int
    @Insert suspend fun insertAll(subtasks: List<SubtaskEntity>)
    @Query("DELETE FROM subtasks WHERE taskId = :taskId") suspend fun deleteForTask(taskId: UUID)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE, id") suspend fun getAll(): List<CategoryEntity>
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE, id") fun observeAll(): Flow<List<CategoryEntity>>
    @Insert suspend fun insert(category: CategoryEntity)
    @Update suspend fun update(category: CategoryEntity): Int
    @Query("DELETE FROM categories WHERE id = :id") suspend fun delete(id: UUID)
}

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC, id") fun observeAll(): Flow<List<FocusSessionEntity>>
    @Query("SELECT * FROM focus_sessions WHERE id = :id") suspend fun get(id: UUID): FocusSessionEntity?
    @Insert suspend fun insert(session: FocusSessionEntity)
    @Update suspend fun update(session: FocusSessionEntity): Int
    @Query("DELETE FROM focus_sessions WHERE id = :id") suspend fun delete(id: UUID)
}
