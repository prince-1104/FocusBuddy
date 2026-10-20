package com.focusbuddy.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusbuddy.domain.model.TaskDraft
import com.focusbuddy.domain.repository.TaskRepository
import com.focusbuddy.domain.usecase.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val selectedDates: StateFlow<LocalDate>,
    private val observeDashboard: ObserveDashboard,
    private val tasks: TaskRepository,
    private val onSelectDate: (LocalDate) -> Unit,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {
    private val retry = MutableStateFlow(0)
    private val pending = MutableStateFlow<Set<String>>(emptySet())
    private val actionError = MutableStateFlow<String?>(null)
    private val editor = MutableStateFlow<TaskEditorState?>(null)
    val editorState = editor.asStateFlow()
    private var editorJob: Job? = null
    private val saveTask = SaveTask(tasks)

    val uiState = combine(
        retry.flatMapLatest {
            selectedDates.flatMapLatest { date ->
                observeDashboard(flowOf(date)).map { it.uiState() }
                    .onStart { emit(TodayUiState(selectedDate = date, isLoading = true)) }
                    .catch { emit(TodayUiState(selectedDate = date, error = "Your plans couldn't be loaded. Please try again.")) }
            }
        }, pending, actionError, editor,
    ) { dashboard, pending, error, editor ->
        dashboard.copy(pendingTaskIds = pending, actionError = error, editor = editor)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState(selectedDate = selectedDates.value, isLoading = true))

    fun retry() { retry.value += 1 }
    fun dismissActionError() { actionError.value = null }

    fun completeTask(id: String, completed: Boolean) {
        if (id in pending.value) return
        pending.update { it + id }
        viewModelScope.launch {
            try { tasks.setCompleted(UUID.fromString(id), completed) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { actionError.value = "Your task couldn't be updated. Please try again." }
            finally { pending.update { it - id } }
        }
    }

    fun createTask() {
        editorJob?.cancel()
        editor.value = TaskEditorState(draft = TaskDraft(date = selectedDates.value.toString()))
    }

    fun editTask(id: String) {
        editorJob?.cancel()
        val taskId = UUID.fromString(id)
        val opening = TaskEditorState(taskId = taskId, draft = TaskDraft(date = selectedDates.value.toString()), loading = true)
        editor.value = opening
        editorJob = viewModelScope.launch {
            try {
                val task = requireNotNull(tasks.get(taskId)) { "Task no longer exists" }.task
                val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(zone())
                val draft = TaskDraft(task.title, task.description.orEmpty(), task.scheduledDate.toString(), task.allDay,
                    task.startAt?.let(formatter::format).orEmpty(), task.endAt?.let(formatter::format).orEmpty(), task.priority, task.categoryId)
                editor.update { current -> if (current?.session == opening.session) current.copy(draft = draft, original = draft, loading = false, recurrenceRule = task.recurrenceRule) else current }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                editor.update { current -> if (current?.session == opening.session) current.copy(loading = false, loadError = "This task couldn't be opened. Please try again.") else current }
            }
        }
    }

    fun updateDraft(draft: TaskDraft) { editor.update { if (it?.busy == false) it.copy(draft = draft, validation = emptyMap(), error = null) else it } }

    fun dismissEditor(force: Boolean = false) {
        val current = editor.value ?: return
        if (current.busy) return
        if (!force && current.draft != current.original) editor.value = current.copy(confirmDiscard = true)
        else { editorJob?.cancel(); editor.value = null }
    }
    fun cancelDiscard() { editor.update { it?.copy(confirmDiscard = false) } }
    fun requestDelete() { editor.update { it?.copy(confirmDelete = true) } }
    fun cancelDelete() { editor.update { it?.copy(confirmDelete = false) } }

    fun saveEditor() {
        val current = editor.value ?: return
        if (current.busy || current.loading || current.loadError != null) return
        editor.value = current.copy(busy = true, error = null, validation = emptyMap())
        editorJob = viewModelScope.launch {
            try {
                val saved = saveTask(current.draft, current.taskId, zone())
                editor.value = null
                onSelectDate(saved.scheduledDate)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (invalid: DraftValidationException) { editor.update { it?.copy(busy = false, validation = mapOf(invalid.field to invalid.message)) } }
            catch (_: Exception) { editor.update { it?.copy(busy = false, error = "Your task couldn't be saved. Please try again.") } }
        }
    }

    fun deleteTask() {
        val current = editor.value ?: return
        val id = current.taskId ?: return
        if (current.busy) return
        editor.value = current.copy(busy = true, confirmDelete = false)
        editorJob = viewModelScope.launch {
            try { tasks.delete(id); editor.value = null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { editor.update { it?.copy(busy = false, error = "Your task couldn't be deleted. Please try again.") } }
        }
    }
}
