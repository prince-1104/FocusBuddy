package com.focusbuddy.ui.discovery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusbuddy.domain.discovery.*
import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.time.timeFormatter
import com.focusbuddy.ui.components.*
import java.time.ZoneId
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskDiscoveryRoute(model: TaskDiscoveryViewModel, onBack: () -> Unit, onTask: (String) -> Unit, onAdd: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val filters by model.filters.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val settings = LocalMascotSettings.current
    var categoriesOpen by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onBack) { Text("Back to Today") }
            Text("Find your next step", style = MaterialTheme.typography.headlineLarge)
            OutlinedTextField(filters.query, { model.change(filters.copy(query = it)) }, label = { Text("Search titles, descriptions and notes") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TaskList.entries.forEach { value -> FilterChip(filters.list == value, { model.change(filters.copy(list = value)) }, label = { Text(value.name) }) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompletionFilter.entries.forEach { value -> FilterChip(filters.completion == value, { model.change(filters.copy(completion = value)) }, label = { Text(if (value == CompletionFilter.Any) "Any completion" else value.name) }) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(filters.priority == null, { model.change(filters.copy(priority = null)) }, label = { Text("Any priority") })
                TaskPriority.entries.forEach { value -> FilterChip(filters.priority == value, { model.change(filters.copy(priority = value)) }, label = { Text("${value.name} priority") }) }
            }
            var menu by remember { mutableStateOf(false) }
            Box {
                SecondaryButton(if (filters.uncategorized) "No category" else state.categories.firstOrNull { it.id == filters.categoryId }?.name ?: if (filters.categoryId == null) "All categories" else "Removed category", { menu = true })
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("All categories") }, { model.change(filters.copy(categoryId = null, uncategorized = false)); menu = false })
                    DropdownMenuItem({ Text("No category") }, { model.change(filters.copy(categoryId = null, uncategorized = true)); menu = false })
                    state.categories.forEach { category -> DropdownMenuItem({ Text(category.name) }, { model.change(filters.copy(categoryId = category.id, uncategorized = false)); menu = false }) }
                }
            }
            TextButton(model::reset) { Text("Reset search and filters") }
            PrimaryButton("+ Add task", onAdd)
            Text("Recurring series appear once; Explore shows their individual dates.", style = MaterialTheme.typography.bodySmall)
        }
        when {
            state.loading -> item { LoadingState("Finding your tasks") }
            state.error != null -> item { ErrorState(state.error!!, model::retry) }
            state.tasks.isEmpty() -> item { EmptyState("A little breathing room", "No tasks match these filters. Try resetting your search.") }
            else -> items(state.tasks, key = { it.id }) { task -> BuddyCard(onClick = { onTask(task.id.toString()) }) {
                Text(task.title, style = MaterialTheme.typography.titleMedium)
                Text(if (task.recurrenceRule != null) "Repeating series · dates in Explore" else task.scheduledDate.toString() +
                    (task.startAt?.let { " · ${timeFormatter(settings.timeFormat).withZone(ZoneId.systemDefault()).format(it)}" } ?: " · All day"))
                Text("${task.priority.name} priority · ${if (task.completed) "Completed" else "Remaining"}")
                state.categories.firstOrNull { it.id == task.categoryId }?.let { CategoryBadge(it.name, CategoryTone.Blue, color = it.color) }
            } }
        }
        item {
            TextButton({ categoriesOpen = !categoriesOpen }) { Text(if (categoriesOpen) "Close categories" else "Manage categories") }
            if (categoriesOpen) BuddyCard {
                OutlinedTextField(name, { name = it }, label = { Text("New category name") }, singleLine = true, enabled = !busy)
                SecondaryButton("Create category", { model.addCategory(name, settings.selectedColor) }, enabled = !busy)
                Text("New categories use your current accent color.", style = MaterialTheme.typography.bodySmall)
                state.categories.forEach { category -> Row(Modifier.fillMaxWidth()) {
                    Text(category.name, Modifier.weight(1f))
                    TextButton({ deleting = category.id.toString() }, enabled = !busy) { Text("Remove") }
                } }
                message?.let { Text(it) }
            }
        }
    }
    deleting?.let { id -> ConfirmationDialog("Remove category?", "Tasks in this category will be kept without a category.", "Remove category",
        { deleting = null; model.deleteCategory(UUID.fromString(id)) }, { deleting = null }) }
}
