package com.focusbuddy.ui

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.focusbuddy.data.local.FocusBuddyDatabase
import com.focusbuddy.data.repository.*
import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.repository.PreferencesRepository
import com.focusbuddy.domain.time.MomentSource
import com.focusbuddy.domain.usecase.ObserveDashboard
import com.focusbuddy.ui.today.TodayViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = com.focusbuddy.TestApplication::class)
class TaskExperienceViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "experience-${UUID.randomUUID()}.db"
    private val db = FocusBuddyDatabase.create(context, name)
    private val now = ZonedDateTime.parse("2026-10-20T10:00:00Z")
    private val clock = Clock.fixed(now.toInstant(), ZoneOffset.UTC)
    private val date = now.toLocalDate()
    private val selected = MutableStateFlow(date)
    private val tasks = RoomTaskRepository(db, clock)
    private val reminders = RoomReminderRepository(db, clock)
    private val models = mutableListOf<TodayViewModel>()
    private val preferences = object : PreferencesRepository {
        override val settings = MutableStateFlow(MascotSettings())
        override suspend fun setWeekStart(day: DayOfWeek?) {}
        override suspend fun setTheme(mode: ThemeMode) {}
        override suspend fun setSettings(settings: MascotSettings) {}
        override suspend fun setMascotVisible(visible: Boolean) {}
        override suspend fun setAnimationsEnabled(enabled: Boolean) {}
    }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() {
        runTest(dispatcher) { models.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } }
        db.close(); context.deleteDatabase(name); Dispatchers.resetMain()
    }
    private fun TestScope.model(): TodayViewModel {
        val moments = object : MomentSource { override val moments = MutableStateFlow(now) }
        val vm = TodayViewModel(selected, ObserveDashboard(RoomAgendaRepository(db, clock), preferences, moments), tasks,
            { selected.value = it }, { ZoneOffset.UTC }, reminders, clock)
        models += vm
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.taskDetailsState.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.reminderLibrary.collect() }
        return vm
    }
    @Test fun prefilledDateTimeAndUnsavedDraftAreRetained() = runTest(dispatcher) {
        val vm = model()
        vm.beginTask(date.plusDays(2), LocalTime.of(14, 30))
        assertEquals(date.plusDays(2).toString(), vm.editorState.value!!.draft.date)
        assertEquals("14:30", vm.editorState.value!!.draft.startTime)
        assertFalse(vm.editorState.value!!.draft.allDay)
        vm.updateDraft(vm.editorState.value!!.draft.copy(title = "Unsaved intention", notes = "Keep me"))
        vm.createTask() // Repeated opening must not wipe an existing form.
        assertEquals("Keep me", vm.editorState.first()!!.draft.notes)
        vm.dismissEditor()
        assertTrue(vm.editorState.value!!.confirmDiscard)
        vm.cancelDiscard()
        assertEquals("Unsaved intention", vm.editorState.value!!.draft.title)
    }
    @Test fun doubleSubmissionCreatesOneAggregateAndRefreshesSubtaskProgress() = runTest(dispatcher) {
        val vm = model()
        vm.beginTask(date.plusDays(1), LocalTime.of(11, 0))
        vm.updateDraft(vm.editorState.value!!.draft.copy(title = "One save", subtasks = listOf(SubtaskDraft(title = "Step", completed = true)), alerts = listOf(AlertDraft(offsetMinutes = 5))))
        vm.saveEditor(); vm.saveEditor()
        vm.editorState.first { it == null }
        val rows = tasks.observeScheduled(date.plusDays(1), date.plusDays(1)).first()
        assertEquals(1, rows.size)
        assertEquals(1, tasks.get(rows.single().id)!!.reminders.size)
        assertTrue(vm.reminderSaved.value)
        vm.dismissReminderSaved()
        assertFalse(vm.reminderSaved.value)
        val state = vm.uiState.first { !it.isLoading && it.selectedDate == date.plusDays(1) && it.total == 1 }
        assertEquals(1, state.sections.single().tasks.single().subtasksCompleted)
    }
    @Test fun detailsCompleteReopenToggleSubtaskAndDeleteAreObservable() = runTest(dispatcher) {
        val parent = tasks.create(Task(title = "Details", scheduledDate = date))
        val child = Subtask(taskId = parent.id, title = "Step")
        tasks.replaceSubtasks(parent.id, listOf(child))
        reminders.create(Reminder(taskId = parent.id, triggerAt = now.plusHours(2).toInstant()))
        val vm = model()
        vm.openTask(parent.id.toString())
        vm.taskDetailsState.first { it?.details != null }
        vm.toggleSubtask(child.id, true)
        vm.taskDetailsState.first { it?.details?.subtasks?.single()?.completed == true && !it.busy }
        vm.completeDetails()
        vm.taskDetailsState.first { it?.details?.task?.completed == true && !it.busy }
        assertFalse(tasks.get(parent.id)!!.reminders.single().enabled)
        vm.completeDetails()
        vm.taskDetailsState.first { it?.details?.task?.completed == false && !it.busy }
        assertFalse(tasks.get(parent.id)!!.reminders.single().enabled)
        vm.requestDetailsDelete()
        vm.taskDetailsState.first { it?.confirmDelete == true }
        assertNotNull(tasks.get(parent.id))
        vm.deleteDetails()
        vm.taskDetailsState.first { it == null }
        assertNull(tasks.get(parent.id))
    }
    @Test fun duplicateGetsNewIdsAndResetsCompletionAndDisablesCopiedAlerts() = runTest(dispatcher) {
        val parent = tasks.create(Task(title = "Original", scheduledDate = date, notes = "Retain notes"))
        val subtask = Subtask(taskId = parent.id, title = "Already done", completed = true)
        tasks.replaceSubtasks(parent.id, listOf(subtask))
        val alert = Reminder(taskId = parent.id, triggerAt = now.plusHours(2).toInstant())
        reminders.create(alert)
        val vm = model()
        vm.openTask(parent.id.toString())
        vm.taskDetailsState.first { it?.details != null }
        vm.duplicateDetails()
        val draft = vm.editorState.value!!.draft
        assertNull(vm.editorState.value!!.taskId)
        assertEquals("Copy of Original", draft.title)
        assertFalse(draft.alerts.single().enabled)
        assertNotEquals(subtask.id, draft.subtasks.single().id)
        assertFalse(draft.subtasks.single().completed)
        vm.saveEditor(); vm.editorState.first { it == null }
        val rows = tasks.observeScheduled(date, date).first()
        assertEquals(2, rows.size)
        assertEquals("Retain notes", rows.first { it.id != parent.id }.notes)
    }
    @Test fun standaloneCrudIncludesDisabledReminderManagementAndDuplicateGuard() = runTest(dispatcher) {
        val vm = model()
        vm.beginReminder(date.plusDays(1), LocalTime.of(15, 0))
        assertEquals("15:00", vm.reminderEditorState.value!!.draft.alert.time)
        vm.saveReminder()
        vm.reminderEditorState.first { it?.validation?.containsKey("title") == true }
        vm.updateReminderDraft(vm.reminderEditorState.value!!.draft.copy(title = "A gentle nudge", description = "Without a task"))
        vm.saveReminder(); vm.saveReminder()
        vm.reminderEditorState.first { it == null }
        val record = reminders.observeAll().first().single()
        assertNull(record.taskId)
        vm.uiState.first { !it.isLoading && it.selectedDate == date.plusDays(1) && it.reminders.any { row -> row.title == record.title } }
        vm.editReminder(record.id.toString())
        val editing = vm.reminderEditorState.first { it?.loading == false }!!
        vm.updateReminderDraft(editing.draft.copy(title = "Disabled nudge", alert = editing.draft.alert.copy(enabled = false)))
        vm.saveReminder(); vm.reminderEditorState.first { it == null }
        vm.openReminderLibrary()
        val library = vm.reminderLibrary.first { it.open && !it.loading }
        assertFalse(library.reminders.single().enabled)
        assertEquals("Disabled nudge", library.reminders.single().title)
        vm.closeReminderLibrary()
        vm.editReminder(record.id.toString())
        vm.reminderEditorState.first { it?.loading == false }
        vm.requestReminderDelete()
        assertTrue(vm.reminderEditorState.value!!.confirmDelete)
        assertNotNull(reminders.get(record.id))
        vm.deleteReminder(); vm.reminderEditorState.first { it == null }
        assertTrue(reminders.observeAll().first().isEmpty())
    }
}
