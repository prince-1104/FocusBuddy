package com.focusbuddy.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import com.focusbuddy.data.local.FocusBuddyDatabase
import com.focusbuddy.data.repository.*
import com.focusbuddy.domain.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = com.focusbuddy.TestApplication::class)
class RepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "repository-${UUID.randomUUID()}.db"
    private val now = Instant.parse("2026-10-19T08:00:00Z")
    private val date = LocalDate.of(2026, 10, 19)
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private lateinit var database: FocusBuddyDatabase
    private lateinit var tasks: RoomTaskRepository
    private lateinit var reminders: RoomReminderRepository
    private lateinit var categories: RoomCategoryRepository
    private lateinit var sessions: RoomFocusSessionRepository

    @Before fun open() {
        database = FocusBuddyDatabase.create(context, databaseName)
        tasks = RoomTaskRepository(database, clock)
        reminders = RoomReminderRepository(database, clock)
        categories = RoomCategoryRepository(database)
        sessions = RoomFocusSessionRepository(database)
    }

    @After fun close() { database.close(); context.deleteDatabase(databaseName) }

    private fun task(title: String = "Write a chapter") = Task(title = title, scheduledDate = date)
    private fun reminder(taskId: UUID? = null) = Reminder(taskId = taskId,
        title = if (taskId == null) "Take a break" else null, triggerAt = now.plusSeconds(3600))

    @Test fun createReadAndUpdatePreservesIdentityCreationAndChildren() = runTest {
        val task = task()
        val reminder = reminder(task.id)
        val subtask = Subtask(taskId = task.id, title = "Outline", sortOrder = 2)
        val created = tasks.create(task, listOf(reminder), listOf(subtask))
        assertEquals(now, created.createdAt)
        assertEquals(now, created.updatedAt)
        val changed = tasks.update(created.copy(title = "Write two chapters", description = "A quiet hour", priority = TaskPriority.High))
        val read = tasks.get(task.id)!!
        assertEquals(changed, read.task)
        assertEquals(created.createdAt, changed.createdAt)
        assertEquals(listOf(reminder), read.reminders)
        assertEquals(listOf(subtask), read.subtasks)
        assertEquals(listOf(changed), tasks.observeScheduled(date, date).first())
    }

    @Test fun missingUpdatesFailInsteadOfInserting() = runTest {
        assertFails<IllegalArgumentException> { tasks.update(task()) }
        assertFails<IllegalArgumentException> { reminders.update(reminder()) }
        assertFails<IllegalArgumentException> { categories.update(Category(name = "Missing", color = 0, icon = "leaf")) }
    }

    @Test fun completingTaskDisablesEveryAttachedReminderAndClearsSnooze() = runTest {
        val record = tasks.create(task())
        val first = reminder(record.id).copy(snoozedUntil = now.plusSeconds(7200))
        val second = reminder(record.id).copy(recurrenceRule = "FREQ=DAILY")
        val standalone = reminder()
        reminders.create(first); reminders.create(second); reminders.create(standalone)
        tasks.setCompleted(record.id, true)
        val details = tasks.get(record.id)!!
        assertTrue(details.task.completed)
        assertEquals(2, details.reminders.size)
        assertTrue(details.reminders.all { !it.enabled && it.snoozedUntil == null })
        assertEquals(listOf(standalone), reminders.observeEnabled().first())
        tasks.setCompleted(record.id, false)
        assertTrue(tasks.get(record.id)!!.reminders.none { it.enabled })
    }

    @Test fun updateCompletionCannotBypassReminderCancellation() = runTest {
        val record = tasks.create(task())
        reminders.create(reminder(record.id))
        tasks.update(record.copy(completed = true))
        assertFalse(tasks.get(record.id)!!.reminders.single().enabled)
    }

    @Test fun aggregateCreationWithCompletedTaskNeverEnablesAlerts() = runTest {
        val record = task().copy(completed = true)
        tasks.create(record, listOf(reminder(record.id)))
        assertFalse(tasks.get(record.id)!!.reminders.single().enabled)
    }

    @Test fun completedAndMissingParentsRejectNewOrReenabledReminders() = runTest {
        val record = tasks.create(task().copy(completed = true))
        assertFails<IllegalArgumentException> { reminders.create(reminder(record.id)) }
        assertFails<IllegalArgumentException> { reminders.create(reminder(UUID.randomUUID())) }
        val disabled = reminder(record.id).copy(enabled = false)
        reminders.create(disabled)
        assertFails<IllegalArgumentException> { reminders.update(disabled.copy(enabled = true)) }
    }

    @Test fun taskDeletionCascadesChildrenPreservesStandaloneAndFocusHistory() = runTest {
        val record = task()
        tasks.create(record, listOf(reminder(record.id)), listOf(Subtask(taskId = record.id, title = "First step")))
        val standalone = reminder()
        reminders.create(standalone)
        val session = FocusSession(taskId = record.id, startedAt = now)
        sessions.create(session)
        tasks.delete(record.id)
        assertNull(tasks.get(record.id))
        assertEquals(listOf(standalone), reminders.observeAll().first())
        assertNull(sessions.get(session.id)!!.taskId)
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM subtasks").use {
            it.moveToFirst(); assertEquals(0, it.getInt(0))
        }
    }

    @Test fun invalidAggregateRollsBackParentAndAlreadyInsertedChildren() = runTest {
        val record = task()
        val duplicate = reminder(record.id)
        assertFails<SQLiteConstraintException> { tasks.create(record, listOf(duplicate, duplicate)) }
        assertNull(tasks.get(record.id))
        assertTrue(reminders.observeAll().first().isEmpty())
        assertFails<IllegalArgumentException> { tasks.create(record, listOf(reminder(UUID.randomUUID()))) }
        assertNull(tasks.get(record.id))
    }

    @Test fun subtaskReplacementRollsBackOnConflictAndSortsDeterministically() = runTest {
        val record = tasks.create(task())
        val original = Subtask(taskId = record.id, title = "Keep this", sortOrder = 1)
        tasks.replaceSubtasks(record.id, listOf(original))
        val duplicate = Subtask(taskId = record.id, title = "Duplicate")
        assertFails<SQLiteConstraintException> { tasks.replaceSubtasks(record.id, listOf(duplicate, duplicate)) }
        assertEquals(listOf(original), tasks.get(record.id)!!.subtasks)
        val first = original.copy(title = "First", sortOrder = 0)
        val last = Subtask(taskId = record.id, title = "Last", sortOrder = 5)
        tasks.replaceSubtasks(record.id, listOf(last, first))
        assertEquals(listOf(first, last), tasks.get(record.id)!!.subtasks)
    }

    @Test fun standaloneReminderCrudSnoozeAndDisable() = runTest {
        val record = reminder()
        reminders.create(record)
        val edited = record.copy(title = "Longer break", vibrationEnabled = false, soundEnabled = false)
        reminders.update(edited)
        assertEquals(edited, reminders.get(record.id))
        val until = now.plusSeconds(8000)
        reminders.snooze(record.id, until)
        assertEquals(until, reminders.get(record.id)!!.effectiveTriggerAt)
        assertEquals(record.triggerAt, reminders.get(record.id)!!.triggerAt)
        assertFails<IllegalArgumentException> { reminders.snooze(record.id, now) }
        reminders.disable(record.id)
        assertFalse(reminders.get(record.id)!!.enabled)
        assertNull(reminders.get(record.id)!!.snoozedUntil)
        assertFails<IllegalArgumentException> { reminders.snooze(record.id, until) }
        reminders.delete(record.id)
        assertNull(reminders.get(record.id))
    }

    @Test fun categoryUpdatesDoNotDeleteTasksAndDeletionClearsAssociation() = runTest {
        val category = Category(name = "Writing", color = 0xFF112233, icon = "pen")
        categories.create(category)
        val record = tasks.create(task().copy(categoryId = category.id))
        categories.update(category.copy(name = "Creative"))
        assertEquals("Creative", categories.observeAll().first().single().name)
        assertEquals(category.id, tasks.get(record.id)!!.task.categoryId)
        categories.delete(category.id)
        assertNull(tasks.get(record.id)!!.task.categoryId)
        assertFails<SQLiteConstraintException> { tasks.create(task().copy(categoryId = UUID.randomUUID())) }
    }

    @Test fun focusSessionCrudSupportsStandaloneAndAssociatedRecords() = runTest {
        val record = FocusSession(startedAt = now)
        sessions.create(record)
        val finished = record.copy(endedAt = now.plusSeconds(1500), durationSeconds = 1400, completed = true)
        sessions.update(finished)
        assertEquals(finished, sessions.get(record.id))
        assertEquals(listOf(finished), sessions.observeAll().first())
        sessions.delete(record.id)
        assertNull(sessions.get(record.id))
    }

    @Test fun persistedAggregateAndSnoozeSurviveDatabaseCloseAndReopen() = runTest {
        val category = Category(name = "Personal", color = 0xFF223344, icon = "leaf")
        categories.create(category)
        val record = task().copy(categoryId = category.id, recurrenceRule = "FREQ=WEEKLY;BYDAY=MO")
        val alert = reminder(record.id).copy(soundUri = "content://test/sound", snoozedUntil = now.plusSeconds(9000))
        val child = Subtask(taskId = record.id, title = "One small step", completed = true)
        tasks.create(record, listOf(alert), listOf(child))
        val session = FocusSession(taskId = record.id, startedAt = now)
        sessions.create(session)
        val before = tasks.get(record.id)
        database.close()
        open()
        assertEquals(before, tasks.get(record.id))
        assertEquals(alert, reminders.get(alert.id))
        assertEquals(listOf(category), categories.observeAll().first())
        assertEquals(session, sessions.get(session.id))
    }

    @Test fun flowEmitsCommittedCompletionWithDisabledChildren() = runTest {
        val record = task()
        tasks.create(record, listOf(reminder(record.id)))
        val completed = async { tasks.observe(record.id).first { it?.task?.completed == true } }
        tasks.setCompleted(record.id, true)
        assertTrue(completed.await()!!.reminders.all { !it.enabled })
    }

    @Test fun failedReminderCancellationRollsBackTaskCompletion() = runTest {
        val record = task()
        tasks.create(record, listOf(reminder(record.id)))
        database.openHelper.writableDatabase.execSQL("""CREATE TRIGGER reject_disable
            BEFORE UPDATE OF enabled ON reminders WHEN NEW.enabled = 0
            BEGIN SELECT RAISE(ABORT, 'test cancellation failure'); END""")
        assertFails<SQLiteConstraintException> { tasks.setCompleted(record.id, true) }
        val details = tasks.get(record.id)!!
        assertFalse(details.task.completed)
        assertTrue(details.reminders.single().enabled)
    }

    @Test fun modificationTimeAdvancesAndCannotMoveBeforePreviousUpdate() = runTest {
        val created = tasks.create(task())
        val later = RoomTaskRepository(database, Clock.fixed(now.plusSeconds(60), ZoneOffset.UTC))
        val updated = later.update(created.copy(title = "Later change"))
        assertEquals(now, updated.createdAt)
        assertEquals(now.plusSeconds(60), updated.updatedAt)
        val afterClockCorrection = tasks.update(updated.copy(title = "Clock moved back"))
        assertEquals(updated.updatedAt, afterClockCorrection.updatedAt)
    }

    @Test fun modelsRejectInvalidStandaloneAndTimeRanges() {
        assertThrows(IllegalArgumentException::class.java) { Reminder(triggerAt = now) }
        assertThrows(IllegalArgumentException::class.java) { task().copy(title = " ") }
        assertThrows(IllegalArgumentException::class.java) { task().copy(startAt = now) }
        assertThrows(IllegalArgumentException::class.java) { FocusSession(startedAt = now, completed = true) }
    }
}

internal suspend inline fun <reified T : Throwable> assertFails(noinline block: suspend () -> Unit) {
    try { block() } catch (failure: Throwable) {
        if (failure is T) return
        throw AssertionError("Expected ${T::class.java.simpleName}, got $failure", failure)
    }
    throw AssertionError("Expected ${T::class.java.simpleName}")
}
