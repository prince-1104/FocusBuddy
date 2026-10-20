package com.focusbuddy.ui

import android.content.Context
import androidx.lifecycle.*
import androidx.test.core.app.ApplicationProvider
import com.focusbuddy.data.local.FocusBuddyDatabase
import com.focusbuddy.data.repository.*
import com.focusbuddy.domain.discovery.*
import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.time.MomentSource
import com.focusbuddy.ui.discovery.TaskDiscoveryViewModel
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
class TaskDiscoveryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "search-qa-${UUID.randomUUID()}.db"
    private val db = FocusBuddyDatabase.create(context, name)
    private val tasks = RoomTaskRepository(db, Clock.systemUTC())
    private val categories = RoomCategoryRepository(db)
    private val now = MutableStateFlow(ZonedDateTime.parse("2026-10-20T10:00:00+05:30[Asia/Kolkata]"))
    private val models = mutableListOf<TaskDiscoveryViewModel>()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { models.forEach { it.viewModelScope.cancel() }; db.close(); context.deleteDatabase(name); Dispatchers.resetMain() }
    private fun TestScope.model(handle: SavedStateHandle = SavedStateHandle()) = TaskDiscoveryViewModel(tasks, categories,
        object : MomentSource { override val moments = now }, handle).also {
        models += it; backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.state.collect() }
    }
    @Test fun realRoomMutationsRefreshSearchPriorityCompletionAndDeletedCategoryFilters() = runTest(dispatcher) {
        val category = Category(name = "Work", color = 0xFFACD8C5, icon = "tag"); categories.create(category)
        val task = tasks.create(Task(title = "Write report", scheduledDate = now.value.toLocalDate(), categoryId = category.id, priority = TaskPriority.High))
        tasks.create(Task(title = "Read", scheduledDate = now.value.toLocalDate()))
        val vm = model(); vm.state.first { !it.loading && it.tasks.size == 2 }
        vm.change(TaskFilters(query = "REPORT", categoryId = category.id, priority = TaskPriority.High))
        assertEquals(task.id, vm.state.first { it.tasks.size == 1 }.tasks.single().id)
        tasks.update(task.copy(title = "Different title"))
        vm.state.first { !it.loading && it.tasks.isEmpty() }
        vm.change(TaskFilters(list = TaskList.Completed)); tasks.setCompleted(task.id, true)
        assertEquals(task.id, vm.state.first { it.tasks.size == 1 }.tasks.single().id)
        tasks.delete(task.id); vm.state.first { !it.loading && it.tasks.isEmpty() }
        vm.change(TaskFilters(categoryId = category.id)); vm.deleteCategory(category.id)
        vm.state.first { it.categories.isEmpty() && it.tasks.size == 1 }
        assertNull(vm.filters.value.categoryId)
    }
    @Test fun savedFiltersAndLocalDayChangesSurviveViewModelRecreation() = runTest(dispatcher) {
        val date = now.value.toLocalDate()
        val task = tasks.create(Task(title = "Next local day", scheduledDate = date.plusDays(1)))
        val handle = SavedStateHandle(); val vm = model(handle)
        vm.change(TaskFilters(query = "next", list = TaskList.Upcoming, completion = CompletionFilter.Remaining))
        vm.state.first { !it.loading && it.tasks.size == 1 }
        val restored = model(handle); restored.state.first { !it.loading }
        assertEquals(vm.filters.value, restored.filters.value)
        now.value = now.value.plusDays(1)
        restored.state.first { !it.loading && it.tasks.isEmpty() }
        restored.change(restored.filters.value.copy(list = TaskList.Today))
        assertEquals(task.id, restored.state.first { it.tasks.size == 1 }.tasks.single().id)
    }
}
