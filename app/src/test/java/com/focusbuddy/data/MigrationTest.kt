package com.focusbuddy.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.focusbuddy.data.local.FocusBuddyDatabase
import com.focusbuddy.data.repository.RoomTaskRepository
import com.focusbuddy.data.repository.RoomCategoryRepository
import com.focusbuddy.data.repository.RoomFocusSessionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-${UUID.randomUUID()}.db"
    private var database: FocusBuddyDatabase? = null

    @After fun close() { database?.close(); context.deleteDatabase(name) }

    private fun legacy(version: Int = 1): SQLiteOpenHelper {
        val sql = checkNotNull(javaClass.classLoader!!.getResourceAsStream("v1.sql"))
            .bufferedReader().use { reader -> reader.readLines().filterNot { it.trimStart().startsWith("--") }.joinToString("\n") }
        return object : SQLiteOpenHelper(context, name, null, version) {
                override fun onCreate(db: SQLiteDatabase) {
                    sql.split(';').filter { it.isNotBlank() }.forEach { db.execSQL(it.trim()) }
                    if (version == 2) {
                        db.execSQL("ALTER TABLE reminders ADD COLUMN snoozedUntil INTEGER")
                        db.execSQL("CREATE INDEX index_reminders_enabled_snoozedUntil ON reminders(enabled,snoozedUntil)")
                    }
                }
                override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Not used")
            }
    }

    @Test fun migrationAddsNullableSnoozeAndIndexWhilePreservingRecords() = runTest {
        val taskId = UUID.randomUUID()
        val reminderId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val date = LocalDate.of(2026, 10, 19)
        legacy().use { helper ->
            val db = helper.writableDatabase
            db.execSQL("INSERT INTO categories(id,name,color,icon) VALUES(?,'Retained category',123,'leaf')", arrayOf(categoryId.toString()))
            db.execSQL("INSERT INTO tasks(id,title,scheduledDate,categoryId,allDay,priority,completed,createdAt,updatedAt) VALUES(?,?,?,?,1,'High',0,1000,1000)",
                arrayOf<Any>(taskId.toString(), "Survive upgrade", date.toEpochDay(), categoryId.toString()))
            db.execSQL("INSERT INTO reminders(id,taskId,triggerAt,enabled,soundEnabled,vibrationEnabled) VALUES(?,?,2000,1,1,0)",
                arrayOf(reminderId.toString(), taskId.toString()))
            db.execSQL("INSERT INTO subtasks(id,taskId,title,completed,sortOrder) VALUES(?,?,?,0,0)",
                arrayOf(UUID.randomUUID().toString(), taskId.toString(), "Retained child"))
            db.execSQL("INSERT INTO focus_sessions(id,taskId,startedAt,durationSeconds,completed) VALUES(?,?,1000,0,0)",
                arrayOf(sessionId.toString(), taskId.toString()))
        }
        val reopened = FocusBuddyDatabase.create(context, name).also { database = it }
        // Room validates the entire migrated table/index/FK schema before serving this query.
        val details = RoomTaskRepository(reopened, Clock.systemUTC()).get(taskId)!!
        assertEquals("Survive upgrade", details.task.title)
        assertNull(details.task.notes)
        assertEquals(date, details.task.scheduledDate)
        assertEquals("Retained child", details.subtasks.single().title)
        assertEquals(reminderId, details.reminders.single().id)
        assertTrue(details.reminders.single().enabled)
        assertNull(details.reminders.single().snoozedUntil)
        assertEquals(categoryId, RoomCategoryRepository(reopened).observeAll().first().single().id)
        assertEquals(taskId, RoomFocusSessionRepository(reopened).get(sessionId)!!.taskId)
        reopened.openHelper.readableDatabase.query("PRAGMA index_list(reminders)").use { cursor ->
            val names = mutableListOf<String>()
            while (cursor.moveToNext()) names += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            assertTrue("index_reminders_enabled_snoozedUntil" in names)
        }
    }

    @Test fun omittedMigrationChangeFailsRoomSchemaValidation() {
        legacy().use { it.writableDatabase }
        val broken = Room.databaseBuilder(context, FocusBuddyDatabase::class.java, name)
            .addMigrations(object : Migration(1, 2) { override fun migrate(db: SupportSQLiteDatabase) = Unit }, FocusBuddyDatabase.MIGRATION_2_3)
            .build().also { database = it }
        assertThrows(IllegalStateException::class.java) { broken.openHelper.writableDatabase }
    }

    @Test fun unknownFutureVersionFailsWithoutDestructiveFallback() {
        legacy(version = 4).use { it.writableDatabase }
        val older = FocusBuddyDatabase.create(context, name).also { database = it }
        assertThrows(IllegalStateException::class.java) { older.openHelper.writableDatabase }
    }

    @Test fun versionTwoUpgradesWithNotesWithoutLosingSnoozes() = runTest {
        val id = UUID.randomUUID()
        legacy(version = 2).use { helper ->
            helper.writableDatabase.execSQL("INSERT INTO reminders(id,title,triggerAt,enabled,soundEnabled,vibrationEnabled,snoozedUntil) VALUES(?,'Retained',1000,1,1,1,2000)", arrayOf(id.toString()))
        }
        val reopened = FocusBuddyDatabase.create(context, name).also { database = it }
        val reminder = com.focusbuddy.data.repository.RoomReminderRepository(reopened, Clock.systemUTC()).get(id)!!
        assertEquals(java.time.Instant.ofEpochMilli(2000), reminder.snoozedUntil)
        val taskRepository = RoomTaskRepository(reopened, Clock.systemUTC())
        val task = taskRepository.create(com.focusbuddy.domain.model.Task(title = "Notes", scheduledDate = LocalDate.of(2026, 10, 20), notes = "Separate notes"))
        assertEquals("Separate notes", taskRepository.get(task.id)!!.task.notes)
    }
}
