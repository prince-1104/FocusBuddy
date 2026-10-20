package com.focusbuddy.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.repository.ReminderRepository
import java.time.*
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
    private val reminders: ReminderRepository,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val moments = MutableSharedFlow<com.focusbuddy.domain.mascot.MascotMoment>(extraBufferCapacity = 1)
    val mascotMoments = moments.asSharedFlow()
    private suspend fun celebrateCompletion() {
        // Read committed repository state; zero-task days never count as a completed day.
        val date = selectedDates.value
        val dashboard = observeDashboard(flowOf(date)).first()
        moments.emit(com.focusbuddy.domain.mascot.completionMoment(dashboard.completed, dashboard.total, date == dashboard.today))
    }
    private suspend fun tryCelebrate() {
        try { celebrateCompletion() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* Feedback failure must not turn a committed completion into an error. */ }
    }
    private val retry = MutableStateFlow(0)
    private val pending = MutableStateFlow<Set<String>>(emptySet())
    private val actionError = MutableStateFlow<String?>(null)
    private val editor = MutableStateFlow<TaskEditorState?>(null)
    val editorState = editor.asStateFlow()
    private var editorJob: Job? = null
    private val saveTask = SaveTask(tasks, clock)

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
            try {
                tasks.setCompleted(UUID.fromString(id), completed)
                if (completed) tryCelebrate()
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { actionError.value = "Your task couldn't be updated. Please try again." }
            finally { pending.update { it - id } }
        }
    }

    fun createTask() {
        beginTask(selectedDates.value, null)
    }

    fun beginTask(date: LocalDate? = null, time: LocalTime? = null) {
        if (editor.value != null) return
        editorJob?.cancel()
        editor.value = TaskEditorState(draft = TaskDraft(date = (date ?: selectedDates.value).toString(),
            allDay = time == null, startTime = time?.format(DateTimeFormatter.ofPattern("HH:mm")).orEmpty()))
    }

    fun editTask(id: String) {
        if (editor.value?.busy == true) return
        editorJob?.cancel()
        val taskId = UUID.fromString(id)
        val opening = TaskEditorState(taskId = taskId, draft = TaskDraft(date = selectedDates.value.toString()), loading = true)
        editor.value = opening
        editorJob = viewModelScope.launch {
            try {
                val details = requireNotNull(tasks.get(taskId)) { "Task no longer exists" }
                val task = details.task
                val draft = details.toDraft(zone())
                editor.update { current -> if (current?.session == opening.session) current.copy(draft = draft, original = draft, loading = false, recurrenceRule = task.recurrenceRule, taskCompleted = task.completed) else current }
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

    private val detailId = MutableStateFlow<UUID?>(null)
    private val detailBusy = MutableStateFlow(false)
    private val detailError = MutableStateFlow<String?>(null)
    private val detailDelete = MutableStateFlow(false)
    val taskDetailsState = combine(detailId.flatMapLatest { id ->
        if (id == null) flowOf(null) else tasks.observe(id).map { TaskDetailsState(id, it) }
            .onStart { emit(TaskDetailsState(id, loading = true)) }
            .catch { emit(TaskDetailsState(id, error = "This task couldn't be loaded.")) }
    }, detailBusy, detailError, detailDelete) { state, busy, error, delete ->
        state?.copy(busy = busy, actionError = error, confirmDelete = delete)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun openTask(id: String) { detailError.value = null; detailDelete.value = false; detailId.value = UUID.fromString(id) }
    fun closeTask() { if (!detailBusy.value) detailId.value = null }
    fun editDetails() { val id = detailId.value ?: return; closeTask(); editTask(id.toString()) }
    fun retryDetails() { val id = detailId.value ?: return; detailId.value = null; viewModelScope.launch { yield(); detailId.value = id } }
    fun duplicateDetails() {
        val details = taskDetailsState.value?.details ?: return
        if (detailBusy.value) return
        val draft = details.toDraft(zone()).let { it.copy(title = "Copy of ${it.title}",
            subtasks = it.subtasks.map { child -> child.copy(id = UUID.randomUUID(), completed = false) },
            alerts = it.alerts.map { alert -> alert.copy(id = UUID.randomUUID(), enabled = false, snoozedUntil = null, originalTrigger = null, originalEnabled = false) }) }
        closeTask()
        editor.value = TaskEditorState(draft = draft, original = draft, duplicated = true)
    }
    fun completeDetails() {
        val details = taskDetailsState.value?.details ?: return
        detailMutation {
            tasks.setCompleted(details.task.id, !details.task.completed)
            if (!details.task.completed) tryCelebrate()
        }
    }
    fun toggleSubtask(id: UUID, completed: Boolean) {
        val parent = detailId.value ?: return
        detailMutation { tasks.setSubtaskCompleted(parent, id, completed) }
    }
    fun requestDetailsDelete() { if (!detailBusy.value) detailDelete.value = true }
    fun cancelDetailsDelete() { detailDelete.value = false }
    fun deleteDetails() {
        val id = detailId.value ?: return
        detailDelete.value = false
        detailMutation { tasks.delete(id); detailId.value = null }
    }
    private fun detailMutation(operation: suspend () -> Unit) {
        if (detailBusy.value) return
        detailBusy.value = true; detailError.value = null
        viewModelScope.launch {
            try { operation() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { detailError.value = "The change couldn't be saved. Please try again." }
            finally { detailBusy.value = false }
        }
    }

    private val reminderEditor = MutableStateFlow<ReminderEditorState?>(null)
    val reminderEditorState = reminderEditor.asStateFlow()
    private var reminderJob: Job? = null
    fun createReminder() { beginReminder(selectedDates.value, null) }
    fun beginReminder(date: LocalDate? = null, time: LocalTime? = null) {
        if (reminderEditor.value != null) return
        reminderEditor.value = ReminderEditorState(draft = ReminderDraft(alert = AlertDraft(
            date = (date ?: selectedDates.value).toString(), time = (time ?: LocalTime.of(9, 0)).format(DateTimeFormatter.ofPattern("HH:mm")))))
    }
    fun editReminder(id: String) {
        if (reminderEditor.value?.busy == true) return
        reminderJob?.cancel()
        val opening = ReminderEditorState(id = UUID.fromString(id), draft = ReminderDraft(alert = AlertDraft()), loading = true)
        reminderEditor.value = opening
        reminderJob = viewModelScope.launch {
            try {
                val reminder = requireNotNull(reminders.get(opening.id!!))
                require(reminder.taskId == null) { "Attached alerts are edited with their task" }
                val draft = ReminderDraft(reminder.title.orEmpty(), reminder.description.orEmpty(), reminder.toDraft(zone()))
                reminderEditor.update { if (it?.session == opening.session) it.copy(draft = draft, original = draft, loading = false) else it }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { reminderEditor.update { it?.copy(loading = false, loadError = "This reminder couldn't be opened.") } }
        }
    }
    fun updateReminderDraft(draft: ReminderDraft) { reminderEditor.update { if (it?.busy == false) it.copy(draft = draft, error = null, validation = emptyMap()) else it } }
    fun dismissReminder(force: Boolean = false) {
        val state = reminderEditor.value ?: return
        if (state.busy) return
        if (!force && state.draft != state.original) reminderEditor.value = state.copy(confirmDiscard = true)
        else { reminderJob?.cancel(); reminderEditor.value = null }
    }
    fun cancelReminderDiscard() { reminderEditor.update { it?.copy(confirmDiscard = false) } }
    fun requestReminderDelete() { reminderEditor.update { if (it?.busy == false) it.copy(confirmDelete = true) else it } }
    fun cancelReminderDelete() { reminderEditor.update { it?.copy(confirmDelete = false) } }
    fun saveReminder() {
        val current = reminderEditor.value ?: return
        if (current.busy || current.loading || current.loadError != null) return
        reminderEditor.value = current.copy(busy = true, validation = emptyMap(), error = null)
        reminderJob = viewModelScope.launch {
            try {
                val record = EditorValidation.standalone(current.draft, zone(), clock.instant())
                val repository = reminders
                if (current.id == null) repository.create(record) else repository.update(record.copy(id = current.id))
                reminderEditor.value = null
                onSelectDate(record.effectiveTriggerAt.atZone(zone()).toLocalDate())
            } catch (e: CancellationException) { throw e }
            catch (e: DraftValidationException) { reminderEditor.update { it?.copy(busy = false, validation = mapOf(e.field to e.message)) } }
            catch (_: Exception) { reminderEditor.update { it?.copy(busy = false, error = "Your reminder couldn't be saved. Please try again.") } }
        }
    }
    fun deleteReminder() {
        val state = reminderEditor.value ?: return
        val id = state.id ?: return
        if (state.busy) return
        reminderEditor.value = state.copy(busy = true, confirmDelete = false)
        reminderJob = viewModelScope.launch {
            try { reminders.delete(id); reminderEditor.value = null }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { reminderEditor.update { it?.copy(busy = false, error = "This reminder couldn't be deleted.") } }
        }
    }

    private val libraryOpen = MutableStateFlow(false)
    private val libraryRetry = MutableStateFlow(0)
    val reminderLibrary = combine(libraryOpen, libraryRetry) { open, _ -> open }.flatMapLatest { open ->
        if (!open) flowOf(ReminderLibraryState()) else reminders.observeAll()
            .map { ReminderLibraryState(open = true, reminders = it.filter { reminder -> reminder.taskId == null }) }
            .onStart { emit(ReminderLibraryState(open = true, loading = true)) }
            .catch { emit(ReminderLibraryState(open = true, error = "Your reminders couldn't be loaded.")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderLibraryState())
    fun openReminderLibrary() { libraryOpen.value = true }
    fun closeReminderLibrary() { libraryOpen.value = false }
    fun retryReminderLibrary() { libraryRetry.value++ }
}
