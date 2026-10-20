package com.focusbuddy.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focusbuddy.data.local.FocusBuddyDatabase
import com.focusbuddy.data.repository.*
import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.reminders.ReminderScheduler
import com.focusbuddy.domain.usecase.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = com.focusbuddy.TestApplication::class)
class TaskEditorRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "editor-${UUID.randomUUID()}.db"
    private var db = FocusBuddyDatabase.create(context, name)
    private val clock = Clock.fixed(Instant.parse("2026-10-20T08:00:00Z"), ZoneOffset.UTC)
    private val tasks get() = RoomTaskRepository(db, clock)
    private val reminders get() = RoomReminderRepository(db, clock)
    private val zone = ZoneOffset.UTC
    private fun draft() = TaskDraft(title = "A thoughtful plan", description = "Description", date = "2026-10-21", allDay = false, startTime = "10:00", endTime = "11:00",
        notes = "Private notes", recurrenceRule = "FREQ=WEEKLY;COUNT=3", subtasks = listOf(SubtaskDraft(title = "First"), SubtaskDraft(title = "Second", completed = true)),
        alerts = listOf(AlertDraft(offsetMinutes = 0, followTaskRecurrence = true), AlertDraft(offsetMinutes = 10, followTaskRecurrence = true), AlertDraft(date = "2026-10-20", time = "17:00", enabled = false)))
    @After fun close() { db.close(); context.deleteDatabase(name) }
    @Test fun createsCompleteAggregateAndPersistsAfterDatabaseReopen() = runTest {
        val saved = SaveTask(tasks, clock)(draft(), null, zone)
        db.close(); db = FocusBuddyDatabase.create(context, name)
        val details = tasks.get(saved.id)!!
        assertEquals("Description", details.task.description)
        assertEquals("Private notes", details.task.notes)
        assertEquals("FREQ=WEEKLY;COUNT=3", details.task.recurrenceRule)
        assertEquals(listOf("First", "Second"), details.subtasks.map { it.title })
        assertEquals(listOf(0, 1), details.subtasks.map { it.sortOrder })
        assertEquals(3, details.reminders.size)
        assertEquals(2, details.reminders.count { it.enabled })
        assertEquals(Instant.parse("2026-10-21T09:50:00Z"), details.reminders.first { it.enabled && it.triggerAt < saved.startAt!! }.triggerAt)
    }
    @Test fun editingReordersRemovesAndAddsChildrenAtomically() = runTest {
        val saved = SaveTask(tasks, clock)(draft(), null, zone)
        val editing = tasks.get(saved.id)!!.toDraft(zone)
        val second = editing.subtasks[1]
        SaveTask(tasks, clock)(editing.copy(title = "Changed", subtasks = listOf(second.copy(title = "Now first"), SubtaskDraft(title = "New")), alerts = editing.alerts.take(1).map { it.copy(enabled = false) }), saved.id, zone)
        val details = tasks.get(saved.id)!!
        assertEquals("Changed", details.task.title)
        assertEquals(listOf("Now first", "New"), details.subtasks.map { it.title })
        assertEquals(second.id, details.subtasks.first().id)
        assertEquals(1, details.reminders.size); assertFalse(details.reminders.single().enabled)
    }
    @Test fun completionDuringEditingWinsAndNeverReactivatesAlerts() = runTest {
        val saved = SaveTask(tasks, clock)(draft(), null, zone)
        val editing = tasks.get(saved.id)!!.toDraft(zone)
        tasks.setCompleted(saved.id, true)
        SaveTask(tasks, clock)(editing.copy(title = "Edited while complete"), saved.id, zone)
        assertTrue(tasks.get(saved.id)!!.task.completed)
        assertTrue(tasks.get(saved.id)!!.reminders.none { it.enabled })
        tasks.setCompleted(saved.id, false)
        assertTrue(tasks.get(saved.id)!!.reminders.none { it.enabled })
    }
    @Test fun invalidAggregateRollsBackParentAndAllChildren() = runTest {
        val original = SaveTask(tasks, clock)(draft(), null, zone)
        val before = tasks.get(original.id)!!
        val other = tasks.create(Task(title = "Other", scheduledDate = LocalDate.of(2026, 10, 21)))
        val conflicting = Subtask(taskId = other.id, title = "Owned elsewhere")
        tasks.replaceSubtasks(other.id, listOf(conflicting))
        val failure = runCatching { tasks.updateAggregate(original.id, SaveTask.validate(draft().copy(title = "Must roll back"), zone), "Changed notes", null,
            before.reminders, listOf(conflicting.copy(taskId = original.id))) }
        assertTrue(failure.isFailure)
        assertEquals(before, tasks.get(original.id))
        assertEquals(conflicting.id, tasks.get(other.id)!!.subtasks.single().id)
    }
    @Test fun individualSubtaskCompletionAndTaskDeletionReachRepositories() = runTest {
        val saved = SaveTask(tasks, clock)(draft(), null, zone)
        val child = tasks.get(saved.id)!!.subtasks.first()
        tasks.setSubtaskCompleted(saved.id, child.id, true)
        assertTrue(tasks.get(saved.id)!!.subtasks.first().completed)
        tasks.delete(saved.id)
        assertNull(tasks.get(saved.id))
        assertTrue(reminders.observeAll().first().isEmpty())
    }
    @Test fun allAlertMutationsUseSchedulingPortAfterCommit() = runTest {
        var calls = 0
        val scheduler = object : ReminderScheduler {
            override val deliveryAvailable = true
            override fun requestReconciliation() { calls++ }
        }
        val scheduledTasks = SchedulingTaskRepository(tasks, scheduler)
        val scheduledReminders = SchedulingReminderRepository(reminders, scheduler)
        val saved = SaveTask(scheduledTasks, clock)(draft(), null, zone)
        assertEquals(1, calls)
        val alert = tasks.get(saved.id)!!.reminders.first()
        scheduledReminders.disable(alert.id)
        assertEquals(2, calls)
        scheduledTasks.setCompleted(saved.id, true)
        assertEquals(3, calls)
        scheduledTasks.delete(saved.id)
        assertEquals(4, calls)
    }

    @Test fun relativePresetsSurviveReopenAndMoveWhenTaskTimeChanges() = runTest {
        val saved = SaveTask(tasks, clock)(draft(), null, zone)
        val editing = tasks.get(saved.id)!!.toDraft(zone)
        assertEquals(setOf(0, 10), editing.alerts.mapNotNull { it.offsetMinutes }.toSet())
        SaveTask(tasks, clock)(editing.copy(startTime = "12:00", endTime = "13:00"), saved.id, zone)
        val after = tasks.get(saved.id)!!
        assertEquals(Instant.parse("2026-10-21T11:50:00Z"), after.reminders.first { it.offsetMinutes == 10 }.triggerAt)
        assertTrue(after.reminders.first { it.offsetMinutes == 10 }.followTaskRecurrence)
        tasks.update(after.task.copy(startAt = Instant.parse("2026-10-21T14:00:00Z"), endAt = null))
        assertEquals(Instant.parse("2026-10-21T13:50:00Z"), tasks.get(saved.id)!!.reminders.first { it.offsetMinutes == 10 }.triggerAt)
    }

    @Test fun schedulerFailureDoesNotTurnCommittedSaveIntoDuplicateSubmission() = runTest {
        val failingScheduler = object : ReminderScheduler {
            override val deliveryAvailable = true
            override fun requestReconciliation() { error("Platform unavailable") }
        }
        val record = SaveTask(SchedulingTaskRepository(tasks, failingScheduler), clock)(draft(), null, zone)
        assertNotNull(tasks.get(record.id))
        assertEquals(1, tasks.observeScheduled(record.scheduledDate, record.scheduledDate).first().size)
    }
}
