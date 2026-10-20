package com.focusbuddy.data.local

import androidx.room.*
import java.time.Instant
import java.util.UUID

@Entity(tableName = "active_focus", foreignKeys = [ForeignKey(entity = FocusSessionEntity::class,
    parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["sessionId"], unique = true)])
data class ActiveFocusEntity(@PrimaryKey val slot: Int = 1, val sessionId: UUID,
    val targetSeconds: Long, val remainingMillis: Long, val segmentStartedElapsed: Long?,
    val bootId: String, val checkpointAt: Instant)

@Dao interface ActiveFocusDao {
    @Query("SELECT * FROM active_focus WHERE slot = 1") suspend fun get(): ActiveFocusEntity?
    @Insert suspend fun insert(value: ActiveFocusEntity)
    @Update suspend fun update(value: ActiveFocusEntity)
    @Query("DELETE FROM active_focus") suspend fun clear()
}

@Dao interface PersonalDataDao {
    @Query("DELETE FROM active_focus") suspend fun clearActiveFocus()
    @Query("DELETE FROM focus_sessions") suspend fun clearFocusHistory()
    @Query("DELETE FROM tasks") suspend fun clearTasks()
    @Query("DELETE FROM reminders") suspend fun clearReminders()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    /** Keep the native journal until reconciliation has canceled every OS identity. */
    @Transaction suspend fun clear() {
        clearActiveFocus(); clearFocusHistory(); clearTasks(); clearReminders(); clearCategories()
    }
}
