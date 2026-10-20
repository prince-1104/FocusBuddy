package com.focusbuddy.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TaskEntity::class, ReminderEntity::class, CategoryEntity::class, SubtaskEntity::class, FocusSessionEntity::class, OccurrenceEntity::class, AlarmJournalEntity::class, ActiveFocusEntity::class],
    version = 5, exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class FocusBuddyDatabase : RoomDatabase() {
    abstract fun activeFocus(): ActiveFocusDao
    abstract fun personalData(): PersonalDataDao
    abstract fun occurrences(): OccurrenceDao
    abstract fun tasks(): TaskDao
    abstract fun reminders(): ReminderDao
    abstract fun subtasks(): SubtaskDao
    abstract fun categories(): CategoryDao
    abstract fun focusSessions(): FocusSessionDao

    companion object {
        /** Pre-release v1 baseline -> durable snooze recovery in v2. No destructive fallback. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN snoozedUntil INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_enabled_snoozedUntil ON reminders(enabled, snoozedUntil)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN notes TEXT")
                db.execSQL("ALTER TABLE reminders ADD COLUMN offsetMinutes INTEGER")
                db.execSQL("ALTER TABLE reminders ADD COLUMN followTaskRecurrence INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS reminder_occurrences (`key` TEXT NOT NULL, reminderId TEXT NOT NULL,
                    baseTrigger INTEGER NOT NULL, triggerAt INTEGER NOT NULL, kind TEXT NOT NULL, fingerprint TEXT NOT NULL,
                    status TEXT NOT NULL, alarmMode TEXT, sourceKey TEXT, PRIMARY KEY(`key`),
                    FOREIGN KEY(reminderId) REFERENCES reminders(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX index_reminder_occurrences_reminderId ON reminder_occurrences(reminderId)")
                db.execSQL("CREATE INDEX index_reminder_occurrences_triggerAt ON reminder_occurrences(triggerAt)")
                db.execSQL("CREATE INDEX index_reminder_occurrences_status_triggerAt ON reminder_occurrences(status,triggerAt)")
                db.execSQL("CREATE TABLE IF NOT EXISTS alarm_journal (`key` TEXT NOT NULL, triggerAt INTEGER NOT NULL, fingerprint TEXT NOT NULL, mode TEXT NOT NULL, generation TEXT NOT NULL DEFAULT '', PRIMARY KEY(`key`))")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE focus_sessions ADD COLUMN outcome TEXT NOT NULL DEFAULT 'Legacy'")
                db.execSQL("UPDATE focus_sessions SET outcome = CASE WHEN completed = 1 THEN 'Completed' WHEN endedAt IS NOT NULL THEN 'Stopped' ELSE 'Legacy' END")
                db.execSQL("""CREATE TABLE IF NOT EXISTS active_focus (slot INTEGER NOT NULL, sessionId TEXT NOT NULL,
                    targetSeconds INTEGER NOT NULL, remainingMillis INTEGER NOT NULL, segmentStartedElapsed INTEGER,
                    bootId TEXT NOT NULL, checkpointAt INTEGER NOT NULL, PRIMARY KEY(slot),
                    FOREIGN KEY(sessionId) REFERENCES focus_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE UNIQUE INDEX index_active_focus_sessionId ON active_focus(sessionId)")
            }
        }
        fun create(context: Context, name: String = "focusbuddy.db"): FocusBuddyDatabase =
            Room.databaseBuilder(context.applicationContext, FocusBuddyDatabase::class.java, name)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
