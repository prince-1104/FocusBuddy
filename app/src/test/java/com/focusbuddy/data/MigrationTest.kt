package com.focusbuddy.data

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.focusbuddy.data.local.FocusBuddyDatabase
import com.focusbuddy.data.repository.RoomTaskRepository
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

    private fun legacy(version: Int = 1): SupportSQLiteOpenHelper {
        val sql = checkNotNull(javaClass.classLoader!!.getResourceAsStream("v1.sql"))
            .bufferedReader().use { reader -> reader.readLines().filterNot { it.trimStart().startsWith("--") }.joinToString("\n") }
        return FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    sql.split(';').filter { it.isNotBlank() }.forEach { db.execSQL(it.trim()) }
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Not used")
            }).build())
    }

    @Test fun migrationAddsNullableSnoozeAndIndexWhilePreservingRecords() = runTest {
        val taskId = UUID.randomUUID()
        val reminderId = UUID.randomUUID()
        val date = LocalDate.of(2026, 10, 19)
        legacy().use { helper ->
            val db = helper.writableDatabase
            db.execSQL("INSERT INTO tasks(id,title,scheduledDate,allDay,priority,completed,createdAt,updatedAt) VALUES(?,?,?,1,'High',0,1000,1000)",
                arrayOf(taskId.toString(), "Survive upgrade", date.toEpochDay()))
            db.execSQL("INSERT INTO reminders(id,taskId,triggerAt,enabled,soundEnabled,vibrationEnabled) VALUES(?,?,2000,1,1,0)",
                arrayOf(reminderId.toString(), taskId.toString()))
            db.execSQL("INSERT INTO subtasks(id,taskId,title,completed,sortOrder) VALUES(?,?,?,0,0)",
                arrayOf(UUID.randomUUID().toString(), taskId.toString(), "Retained child"))
        }
        val reopened = FocusBuddyDatabase.create(context, name).also { database = it }
        // Room validates the entire migrated table/index/FK schema before serving this query.
        val details = RoomTaskRepository(reopened, Clock.systemUTC()).get(taskId)!!
        assertEquals("Survive upgrade", details.task.title)
        assertEquals(date, details.task.scheduledDate)
        assertEquals("Retained child", details.subtasks.single().title)
        assertEquals(reminderId, details.reminders.single().id)
        assertTrue(details.reminders.single().enabled)
        assertNull(details.reminders.single().snoozedUntil)
        reopened.openHelper.readableDatabase.query("PRAGMA index_list(reminders)").use { cursor ->
            val names = mutableListOf<String>()
            while (cursor.moveToNext()) names += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            assertTrue("index_reminders_enabled_snoozedUntil" in names)
        }
    }

    @Test fun omittedMigrationChangeFailsRoomSchemaValidation() {
        legacy().use { it.writableDatabase }
        val broken = Room.databaseBuilder(context, FocusBuddyDatabase::class.java, name)
            .addMigrations(object : Migration(1, 2) { override fun migrate(db: SupportSQLiteDatabase) = Unit })
            .build().also { database = it }
        assertThrows(IllegalStateException::class.java) { broken.openHelper.writableDatabase }
    }

    @Test fun unknownFutureVersionFailsWithoutDestructiveFallback() {
        legacy(version = 3).use { it.writableDatabase }
        val older = FocusBuddyDatabase.create(context, name).also { database = it }
        assertThrows(IllegalStateException::class.java) { older.openHelper.writableDatabase }
    }
}
