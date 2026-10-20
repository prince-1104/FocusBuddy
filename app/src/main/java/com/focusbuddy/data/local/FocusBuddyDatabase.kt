package com.focusbuddy.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TaskEntity::class, ReminderEntity::class, CategoryEntity::class, SubtaskEntity::class, FocusSessionEntity::class],
    version = 3, exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class FocusBuddyDatabase : RoomDatabase() {
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

        fun create(context: Context, name: String = "focusbuddy.db"): FocusBuddyDatabase =
            Room.databaseBuilder(context.applicationContext, FocusBuddyDatabase::class.java, name)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
