package com.focusbuddy.ui.focus

import androidx.lifecycle.*
import com.focusbuddy.domain.focus.*
import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

data class FocusUiState(val active: ActiveFocus? = null, val tasks: List<Task> = emptyList(),
    val history: List<FocusSession> = emptyList(), val loading: Boolean = true, val error: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class FocusViewModel(private val timer: FocusTimerRepository, history: FocusSessionRepository,
    tasks: TaskRepository, private val handle: SavedStateHandle) : ViewModel() {
    val minutes = handle.getStateFlow("focusMinutes", "25")
    val selectedTask = handle.getStateFlow("focusTask", "")
    private val refresh = MutableStateFlow(0)
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()
    private val completed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val completions = completed.asSharedFlow()
    private var previous: UUID? = null
    private var operationJob: Job? = null
    fun cancelPendingOperation() { operationJob?.cancel(); mutableBusy.value = false }
    val state = refresh.flatMapLatest {
        combine(timer.observe(), history.observeAll(), tasks.observeAll()) { active, sessions, records ->
            FocusUiState(active, records, sessions, loading = false)
        }.onEach { state ->
            if (state.active != null) previous = state.active.id
            else state.history.firstOrNull { it.id == previous }?.let { ended ->
                if (ended.completed) { completed.tryEmit(Unit); previous = null }
                else if (ended.endedAt != null) previous = null
            }
        }.catch { emit(FocusUiState(loading = false, error = "Your focus session couldn't be loaded. Please retry.")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FocusUiState())
    fun duration(value: String) { if (!busy.value) handle["focusMinutes"] = value }
    fun selectTask(id: String) { if (!busy.value) handle["focusTask"] = id }
    fun retry() { refresh.value++ }
    fun start() = mutate {
        val value = minutes.value.toIntOrNull()
        require(value != null && value in 1..240) { "Choose a whole number from 1 to 240 minutes." }
        timer.start(value, selectedTask.value.takeIf { it.isNotEmpty() }?.let(UUID::fromString))
    }
    fun pause() = mutate { timer.pause() }
    fun resume() = mutate { timer.resume() }
    fun stop() = mutate { timer.stop() }
    private fun mutate(operation: suspend () -> Unit) {
        if (busy.value) return
        mutableBusy.value = true; mutableMessage.value = null
        operationJob = viewModelScope.launch {
            try { operation(); refresh.value++ }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { mutableMessage.value = e.message }
            catch (_: Exception) { mutableMessage.value = "Your session couldn't be updated. Please retry." }
            finally { mutableBusy.value = false }
        }
    }
}
