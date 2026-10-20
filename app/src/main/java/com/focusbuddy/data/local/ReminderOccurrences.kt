package com.focusbuddy.data.local

import androidx.room.*
import java.time.Instant
import java.util.UUID

@Entity(tableName = "reminder_occurrences", foreignKeys = [ForeignKey(entity = ReminderEntity::class,
    parentColumns = ["id"], childColumns = ["reminderId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("reminderId"), Index("triggerAt"), Index(value = ["status", "triggerAt"])])
data class OccurrenceEntity(@PrimaryKey val key: String, val reminderId: UUID,
    val baseTrigger: Instant, val triggerAt: Instant, val kind: String, val fingerprint: String,
    val status: String = "Pending", val alarmMode: String? = null, val sourceKey: String? = null)

/** Separate OS identity journal survives record cascades, so obsolete PendingIntents can be canceled. */
@Entity(tableName = "alarm_journal")
data class AlarmJournalEntity(@PrimaryKey val key: String, val triggerAt: Instant, val fingerprint: String, val mode: String, @ColumnInfo(defaultValue = "''") val generation: String = "")

@Dao
interface OccurrenceDao {
    @Query("""SELECT COUNT(*) AS pending,
        COALESCE(SUM(CASE WHEN alarmMode = 'Error' THEN 1 ELSE 0 END),0) AS failed,
        COALESCE(SUM(CASE WHEN alarmMode = 'Blocked' THEN 1 ELSE 0 END),0) AS blocked
        FROM reminder_occurrences WHERE status = 'Pending'""")
    fun health(): kotlinx.coroutines.flow.Flow<com.focusbuddy.domain.reminders.SchedulingHealth>
    @Query("SELECT * FROM reminder_occurrences WHERE status IN ('Pending','Delivered')") suspend fun activeRows(): List<OccurrenceEntity>
    @Query("SELECT * FROM reminder_occurrences WHERE reminderId = :id") suspend fun forReminder(id: UUID): List<OccurrenceEntity>
    @Query("SELECT * FROM reminder_occurrences WHERE triggerAt >= :start AND triggerAt < :end") suspend fun between(start: Instant, end: Instant): List<OccurrenceEntity>
    @Query("SELECT * FROM reminder_occurrences") suspend fun all(): List<OccurrenceEntity>
    @Query("SELECT * FROM reminder_occurrences WHERE key = :key") suspend fun get(key: String): OccurrenceEntity?
    @Query("SELECT * FROM reminder_occurrences WHERE key = :key") fun observe(key: String): kotlinx.coroutines.flow.Flow<OccurrenceEntity?>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(row: OccurrenceEntity): Long
    @Update suspend fun update(row: OccurrenceEntity)
    @Query("SELECT * FROM alarm_journal") suspend fun journal(): List<AlarmJournalEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun journal(row: AlarmJournalEntity)
    @Query("DELETE FROM alarm_journal WHERE key = :key") suspend fun forgetAlarm(key: String)
}
