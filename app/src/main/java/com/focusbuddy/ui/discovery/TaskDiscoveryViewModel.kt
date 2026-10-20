package com.focusbuddy.ui.discovery

import androidx.lifecycle.*
import com.focusbuddy.domain.discovery.*
import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.repository.*
import com.focusbuddy.domain.time.MomentSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

data class DiscoveryState(val tasks: List<Task> = emptyList(), val categories: List<Category> = emptyList(),
    val loading: Boolean = true, val error: String? = null)
@OptIn(ExperimentalCoroutinesApi::class)
class TaskDiscoveryViewModel(tasks: TaskRepository, private val categories: CategoryRepository,
    moments: MomentSource, private val handle: SavedStateHandle) : ViewModel() {
    private val mutableFilters = MutableStateFlow(TaskFilters(
        query = handle["search"] ?: "", list = TaskList.entries.firstOrNull { it.name == handle.get<String>("taskList") } ?: TaskList.Today,
        categoryId = handle.get<String>("category")?.let { runCatching { UUID.fromString(it) }.getOrNull() },
        uncategorized = handle["uncategorized"] ?: false,
        completion = CompletionFilter.entries.firstOrNull { it.name == handle.get<String>("completion") } ?: CompletionFilter.Any,
        priority = TaskPriority.entries.firstOrNull { it.name == handle.get<String>("priority") }))
    val filters = mutableFilters.asStateFlow()
    private val retry = MutableStateFlow(0)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    private var operationJob: Job? = null
    fun cancelPendingOperation() { operationJob?.cancel(); busy.value = false }
    val state = retry.flatMapLatest {
        combine(tasks.observeAll(), categories.observeAll(), moments.moments, filters) { records, categories, now, selected ->
            DiscoveryState(discoverTasks(records, selected, now), categories, loading = false)
        }.catch { emit(DiscoveryState(loading = false, error = "Your tasks couldn't be searched. Please retry.")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DiscoveryState())
    fun change(value: TaskFilters) {
        mutableFilters.value = value
        handle["search"] = value.query; handle["taskList"] = value.list.name
        handle["category"] = value.categoryId?.toString(); handle["uncategorized"] = value.uncategorized
        handle["completion"] = value.completion.name; handle["priority"] = value.priority?.name
    }
    fun reset() = change(TaskFilters())
    fun retry() { retry.value++ }
    fun addCategory(name: String, color: Long) = mutation {
        require(name.isNotBlank()) { "Give your category a name." }
        categories.create(Category(name = name.trim(), color = color, icon = "tag"))
        message.value = "Category created. Assign it when editing a task."
    }
    fun deleteCategory(id: UUID) = mutation {
        categories.delete(id)
        if (filters.value.categoryId == id) change(filters.value.copy(categoryId = null))
        message.value = "Category removed. Its tasks are kept without a category."
    }
    private fun mutation(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true; message.value = null
        operationJob = viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { message.value = e.message }
            catch (_: Exception) { message.value = "Your category couldn't be updated. Please retry." }
            finally { busy.value = false }
        }
    }
}
