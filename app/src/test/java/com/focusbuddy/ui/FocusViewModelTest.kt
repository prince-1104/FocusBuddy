package com.focusbuddy.ui

import androidx.lifecycle.*
import com.focusbuddy.domain.focus.*
import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.repository.*
import com.focusbuddy.ui.focus.FocusViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.time.*
import java.util.UUID
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = com.focusbuddy.TestApplication::class)
class FocusViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = mutableListOf<FocusViewModel>()
    private val active = MutableStateFlow<ActiveFocus?>(null)
    private val sessions = MutableStateFlow<List<FocusSession>>(emptyList())
    private var starts = 0
    private val timer = object : FocusTimerRepository {
        override fun observe() = active
        override suspend fun snapshot() = active.value
        override suspend fun start(minutes: Int, taskId: UUID?) { starts++; active.value = ActiveFocus(UUID.randomUUID(), taskId, minutes * 60L, minutes * 60_000L, true) }
        override suspend fun pause() { active.value = active.value?.copy(running = false) }
        override suspend fun resume() { active.value = active.value?.copy(running = true) }
        override suspend fun stop() { active.value = null }
    }
    private val history = object : FocusSessionRepository {
        override fun observeAll() = sessions
        override suspend fun get(id: UUID) = sessions.value.firstOrNull { it.id == id }
        override suspend fun create(session: FocusSession) { sessions.value += session }
        override suspend fun update(session: FocusSession) { sessions.value = sessions.value.map { if (it.id == session.id) session else it } }
        override suspend fun delete(id: UUID) { sessions.value = sessions.value.filterNot { it.id == id } }
    }
    private val taskRecords = object : TaskRepository by StubTasks() {}
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { models.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain() }
    private fun TestScope.model(handle: SavedStateHandle = SavedStateHandle()) = FocusViewModel(timer, history, taskRecords, handle).also {
        models += it; backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.state.collect() }
    }
    @Test fun controlsGuardRepeatedStartsAndRetainInputOnRecreation() = runTest(dispatcher) {
        val handle = SavedStateHandle(); val model = model(handle); runCurrent()
        model.duration("45"); model.selectTask(UUID.randomUUID().toString())
        model.start(); model.start(); runCurrent()
        assertEquals(1, starts); assertEquals(2700L, model.state.value.active!!.targetSeconds)
        model.pause(); runCurrent(); assertFalse(model.state.value.active!!.running)
        val reopened = model(handle); runCurrent()
        assertEquals("45", reopened.minutes.value); assertFalse(reopened.state.value.active!!.running)
        reopened.resume(); runCurrent(); assertTrue(reopened.state.value.active!!.running)
    }
    @Test fun invalidDurationNeverStartsAndCompletionFeedbackSurvivesHistoryFlowLag() = runTest(dispatcher) {
        val model = model(); runCurrent()
        model.duration("0"); model.start(); runCurrent(); assertEquals(0, starts); assertNotNull(model.message.value)
        val id = UUID.randomUUID(); val start = Instant.parse("2026-10-20T10:00:00Z")
        val events = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.completions.collect { events += it } }
        active.value = ActiveFocus(id, null, 60, 1000, true); runCurrent()
        active.value = null; runCurrent() // Timer has ended, Room history invalidation arrives later.
        sessions.value = listOf(FocusSession(id, startedAt = start, endedAt = start.plusSeconds(60), durationSeconds = 60, completed = true))
        runCurrent(); assertEquals(1, events.size)
        sessions.value = sessions.value.toList(); runCurrent(); assertEquals(1, events.size)
    }
}
private open class StubTasks : TaskRepository {
    override fun observeAll() = flowOf(emptyList<Task>())
    override fun observeScheduled(from: LocalDate, through: LocalDate) = observeAll()
    override fun observe(id: UUID) = flowOf<TaskDetails?>(null)
    override suspend fun get(id: UUID): TaskDetails? = null
    override suspend fun create(task: Task, reminders: List<Reminder>, subtasks: List<Subtask>) = task
    override suspend fun update(task: Task) = task
    override suspend fun updateContent(id: UUID, content: TaskContent): Task = error("Unused")
    override suspend fun updateAggregate(id: UUID, content: TaskContent, notes: String?, recurrenceRule: String?, reminders: List<Reminder>, subtasks: List<Subtask>): Task = error("Unused")
    override suspend fun setCompleted(id: UUID, completed: Boolean) {}
    override suspend fun replaceSubtasks(taskId: UUID, subtasks: List<Subtask>) {}
    override suspend fun setSubtaskCompleted(taskId: UUID, id: UUID, completed: Boolean) {}
    override suspend fun delete(id: UUID) {}
}
