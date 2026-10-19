package com.focusbuddy.ui

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.focusbuddy.data.local.FocusBuddyDatabase
import com.focusbuddy.data.repository.*
import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.repository.*
import com.focusbuddy.domain.time.MomentSource
import com.focusbuddy.domain.usecase.ObserveDashboard
import com.focusbuddy.ui.today.TodayViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.*
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TodayViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "today-${UUID.randomUUID()}.db"
    private val database = FocusBuddyDatabase.create(context, name)
    private val now = ZonedDateTime.parse("2026-10-19T10:00:00Z")
    private val date = now.toLocalDate()
    private val tasks = RoomTaskRepository(database, Clock.fixed(now.toInstant(), ZoneOffset.UTC))
    private val reminders = RoomReminderRepository(database, Clock.fixed(now.toInstant(), ZoneOffset.UTC))
    private val selected = MutableStateFlow(date)
    private val preferences = TestPreferences()
    private val moments = object : MomentSource { override val moments = MutableStateFlow(now) }
    private val models = mutableListOf<TodayViewModel>()

    @Before fun prepare() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() {
        runBlocking { models.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } }
        database.close(); context.deleteDatabase(name); Dispatchers.resetMain()
    }

    private fun TestScope.model(taskRepository: TaskRepository = tasks, agenda: AgendaRepository = RoomAgendaRepository(database)): TodayViewModel {
        val model = TodayViewModel(selected, ObserveDashboard(agenda, preferences, moments), taskRepository,
            { selected.value = it }, { ZoneOffset.UTC })
        models += model
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
        return model
    }

    @Test fun emptyDayLoadsWithZeroProgressAndNoProductionSamples() = runTest(dispatcher) {
        val model = model()
        val state = model.uiState.first { !it.isLoading }
        assertNull(state.error)
        assertEquals(0, state.total); assertEquals(0, state.completed)
        assertTrue(state.sections.isEmpty()); assertTrue(state.reminders.isEmpty())
        assertEquals(29, state.days.size)
        assertTrue(state.days.all { it.taskCount == 0 && it.reminderAlertCount == 0 })
    }

    @Test fun completionAndReopeningPersistAndRecalculateProgress() = runTest(dispatcher) {
        val task = tasks.create(Task(title = "One meaningful step", scheduledDate = date))
        reminders.create(Reminder(taskId = task.id, triggerAt = now.plusHours(1).toInstant()))
        val model = model()
        model.uiState.first { !it.isLoading && it.total == 1 }
        model.completeTask(task.id.toString(), true)
        val completed = model.uiState.first { it.completed == 1 && it.pendingTaskIds.isEmpty() }
        assertEquals(1, completed.total)
        assertTrue(tasks.get(task.id)!!.task.completed)
        assertFalse(tasks.get(task.id)!!.reminders.single().enabled)
        model.completeTask(task.id.toString(), false)
        model.uiState.first { it.completed == 0 && it.pendingTaskIds.isEmpty() }
        assertFalse(tasks.get(task.id)!!.task.completed)
        assertFalse(tasks.get(task.id)!!.reminders.single().enabled)
    }

    @Test fun overdueCarryoverDoesNotInflateSelectedDayProgress() = runTest(dispatcher) {
        tasks.create(Task(title = "Yesterday", scheduledDate = date.minusDays(1)))
        tasks.create(Task(title = "Today", scheduledDate = date, completed = true))
        val model = model()
        val state = model.uiState.first { !it.isLoading && it.sections.size == 2 }
        assertEquals(1, state.total); assertEquals(1, state.completed)
        assertEquals(TaskSection.Overdue, state.sections.first().section)
        assertTrue(state.sections.first().tasks.single().overdue)
    }

    @Test fun selectingDateRefreshesAgendaAndStandaloneReminderIndicators() = runTest(dispatcher) {
        val tomorrow = date.plusDays(1)
        val task = tasks.create(Task(title = "Tomorrow", scheduledDate = tomorrow))
        reminders.create(Reminder(title = "A gentle nudge", triggerAt = now.plusDays(1).toInstant()))
        val model = model()
        val today = model.uiState.first { !it.isLoading }
        assertEquals(0, today.total)
        assertEquals(1, today.days.first { it.date == tomorrow }.reminderAlertCount)
        selected.value = tomorrow
        val state = model.uiState.first { it.selectedDate == tomorrow && !it.isLoading }
        assertEquals(task.id.toString(), state.sections.single().tasks.single().id)
        assertEquals("A gentle nudge", state.reminders.single().title)
        assertEquals(1, state.total)
    }

    @Test fun creationEditingAndDeletionRefreshPersistentAgenda() = runTest(dispatcher) {
        val model = model()
        model.uiState.first { !it.isLoading }
        model.createTask()
        val editor = model.uiState.first { it.editor != null }.editor!!
        model.updateDraft(editor.draft.copy(title = "Create from Today", priority = TaskPriority.High))
        model.saveEditor()
        val created = model.uiState.first { it.total == 1 && it.editor == null }
        val id = created.sections.single().tasks.single().id
        model.editTask(id)
        val editing = model.uiState.first { it.editor?.taskId?.toString() == id && it.editor?.loading == false }.editor!!
        // Completing while the editor is open must not be undone by saving content.
        tasks.setCompleted(UUID.fromString(id), true)
        model.updateDraft(editing.draft.copy(title = "Edited", date = date.plusDays(1).toString()))
        model.saveEditor()
        val changed = model.uiState.first { it.selectedDate == date.plusDays(1) && !it.isLoading && it.total == 1 && it.editor == null }
        assertEquals(1, changed.completed)
        assertEquals("Edited", changed.sections.single().tasks.single().title)
        assertEquals(date.plusDays(1), selected.value)
        model.editTask(id)
        model.uiState.first { it.editor?.loading == false }
        model.deleteTask()
        model.uiState.first { it.total == 0 && it.editor == null }
        assertNull(tasks.get(UUID.fromString(id)))
    }

    @Test fun invalidDraftStaysOpenWithoutWritingAnyTask() = runTest(dispatcher) {
        val model = model()
        model.uiState.first { !it.isLoading }
        model.createTask(); model.saveEditor()
        val state = model.uiState.first { it.editor?.validation?.containsKey("title") == true }
        assertFalse(state.editor!!.busy)
        assertEquals(0, state.total)
        assertTrue(tasks.observeScheduled(date, date).first().isEmpty())
    }

    @Test fun loadingErrorCanRetryAndCompletionFailureLeavesStoredStateIntact() = runTest(dispatcher) {
        val task = tasks.create(Task(title = "Reliable state", scheduledDate = date))
        var failRead = true
        val realAgenda = RoomAgendaRepository(database)
        val agenda = object : AgendaRepository {
            override fun observe(selected: LocalDate, today: LocalDate, zone: ZoneId) = flow {
                if (failRead) throw IOException("test read failure")
                emitAll(realAgenda.observe(selected, today, zone))
            }
        }
        val failingWrites = object : TaskRepository by tasks {
            override suspend fun setCompleted(id: UUID, completed: Boolean) { throw IOException("test write failure") }
        }
        val model = model(failingWrites, agenda)
        model.uiState.first { it.error != null }
        failRead = false; model.retry()
        model.uiState.first { !it.isLoading && it.total == 1 }
        model.completeTask(task.id.toString(), true)
        val state = model.uiState.first { it.actionError != null && it.pendingTaskIds.isEmpty() }
        assertEquals(0, state.completed)
        assertFalse(tasks.get(task.id)!!.task.completed)
    }

    @Test fun companionPreferenceChangesAreObservedByDashboard() = runTest(dispatcher) {
        val model = model()
        model.uiState.first { !it.isLoading }
        preferences.setMascotVisible(false)
        preferences.setAnimationsEnabled(false)
        model.uiState.first { !it.isLoading && !it.showMascot && !it.animationsEnabled }
    }

    private class TestPreferences : PreferencesRepository {
        private val state = MutableStateFlow(MascotSettings())
        override val settings = state.asStateFlow()
        override suspend fun setTheme(mode: ThemeMode) { state.update { it.copy(theme = mode) } }
        override suspend fun setSettings(settings: MascotSettings) { state.value = settings }
        override suspend fun setMascotVisible(visible: Boolean) { state.update { it.copy(showMascot = visible) } }
        override suspend fun setAnimationsEnabled(enabled: Boolean) { state.update { it.copy(animationsEnabled = enabled) } }
    }
}
