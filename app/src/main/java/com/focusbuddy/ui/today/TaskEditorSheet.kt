package com.focusbuddy.ui.today

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.focusbuddy.domain.model.*
import com.focusbuddy.ui.components.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskEditorSheet(state: TaskEditorState, categories: List<Category>, onChange: (TaskDraft) -> Unit,
    onSave: () -> Unit, onDismiss: () -> Unit, onDelete: () -> Unit, onRetry: () -> Unit) {
    val draft = state.draft
    BuddyBottomSheet(if (state.taskId == null) "A little plan" else "Your task", onDismiss, canDismiss = !state.busy) {
        when {
            state.loading -> LoadingState("Opening your task")
            state.loadError != null -> ErrorState(state.loadError, onRetry)
            else -> {
                if (state.recurrenceRule != null) Text("Editing the recurring series. Changes and deletion apply to the whole series.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                EditorField(draft.title, "Title", { onChange(draft.copy(title = it)) }, state.validation["title"], !state.busy)
                OutlinedTextField(draft.description, { onChange(draft.copy(description = it)) },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Notes · optional") }, minLines = 2,
                    maxLines = 5, enabled = !state.busy)
                EditorField(draft.date, "Date · YYYY-MM-DD", { onChange(draft.copy(date = it)) }, state.validation["date"], !state.busy)
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(draft.allDay, enabled = !state.busy,
                    role = Role.Switch, onValueChange = { onChange(draft.copy(allDay = it)) }),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("All day", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(draft.allDay, onCheckedChange = null)
                }
                if (!draft.allDay) {
                    EditorField(draft.startTime, "Start time · HH:mm", { onChange(draft.copy(startTime = it)) }, state.validation["startTime"], !state.busy)
                    EditorField(draft.endTime, "End time · optional", { onChange(draft.copy(endTime = it)) }, state.validation["endTime"], !state.busy)
                    Text("24-hour time, using your device's current time zone. Repeated clock-change times use the first occurrence.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Priority", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskPriority.entries.forEach { priority ->
                        FilterChip(selected = draft.priority == priority, onClick = { onChange(draft.copy(priority = priority)) },
                            label = { Text(priority.name) }, enabled = !state.busy)
                    }
                }
                Text("Category", style = MaterialTheme.typography.titleMedium)
                var expanded by remember { mutableStateOf(false) }
                Box {
                    SecondaryButton(categories.firstOrNull { it.id == draft.categoryId }?.name ?: "No category",
                        { expanded = true }, enabled = !state.busy)
                    DropdownMenu(expanded, { expanded = false }) {
                        DropdownMenuItem(text = { Text("No category") }, onClick = { onChange(draft.copy(categoryId = null)); expanded = false })
                        categories.forEach { category ->
                            DropdownMenuItem(text = { Text(category.name) }, onClick = { onChange(draft.copy(categoryId = category.id)); expanded = false })
                        }
                    }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (state.busy) LoadingState("Saving your changes")
                PrimaryButton(if (state.taskId == null) "Create task" else "Save changes", onSave, Modifier.fillMaxWidth(), enabled = !state.busy)
                if (state.taskId != null) TextButton(onClick = onDelete, enabled = !state.busy) {
                    Text("Delete task", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun EditorField(value: String, label: String, onChange: (String) -> Unit, error: String?, enabled: Boolean) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) },
        singleLine = true, isError = error != null, enabled = enabled,
        supportingText = if (error == null) null else { { Text(error) } })
}
