package com.focusbuddy.ui.today

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.focusbuddy.domain.model.*
import com.focusbuddy.ui.components.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskEditorSheet(state: TaskEditorState, categories: List<Category>, onChange: (TaskDraft) -> Unit,
    onSave: () -> Unit, onDismiss: () -> Unit, onDelete: () -> Unit, onRetry: () -> Unit) {
    val draft = state.draft
    val enabled = !state.busy
    BuddyBottomSheet(if (state.taskId == null) "Create a task" else "Edit your task", onDismiss, canDismiss = enabled, hasUnsavedChanges = draft != state.original, footer = {
        EditorSaveFooter(if (state.busy) "Saving task..." else if (state.taskId == null) "Create task" else "Save changes",
            onSave, enabled && !state.loading && state.loadError == null, state.error ?: state.validation.values.firstOrNull())
    }) {
        when {
            state.loading -> LoadingState("Opening your task")
            state.loadError != null -> ErrorState(state.loadError, onRetry)
            else -> {
                Text("A little intention goes a long way.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (draft.recurrenceRule.isNotBlank()) Text("Changes apply to the recurring series.", color = MaterialTheme.colorScheme.primary)
                if (state.taskCompleted) Text("This task is completed. Its alerts will stay disabled until you reopen it and enable them.")
                if (state.duplicated) Text("Subtasks are reset and copied alerts are disabled. Review them before saving.")
                EditorField(draft.title, "Task title / required", { onChange(draft.copy(title = it)) }, state.validation["title"], enabled)
                EditorField(draft.description, "Description / optional", { onChange(draft.copy(description = it)) }, enabled = enabled, multiline = true)
                DateField(draft.date, "Date / YYYY-MM-DD", { onChange(draft.copy(date = it)) }, state.validation["date"], enabled)
                EditorToggle("All day", draft.allDay, { onChange(draft.copy(allDay = it)) }, enabled)
                if (!draft.allDay) {
                    TimeField(draft.startTime, "Start time", { onChange(draft.copy(startTime = it)) }, state.validation["startTime"], enabled)
                    TimeField(draft.endTime, "End time / optional", { onChange(draft.copy(endTime = it)) }, state.validation["endTime"], enabled)
                }
                Text("Times use your device zone. Repeated clock-change times use the earlier offset.", style = MaterialTheme.typography.bodySmall)
                Text("Priority", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskPriority.entries.forEach { priority -> FilterChip(draft.priority == priority,
                        { onChange(draft.copy(priority = priority)) }, label = { Text(priority.name) }, enabled = enabled) }
                }
                var categoryMenu by remember { mutableStateOf(false) }
                Box {
                    SecondaryButton(categories.firstOrNull { it.id == draft.categoryId }?.name ?: "No category", { categoryMenu = true }, enabled = enabled)
                    DropdownMenu(categoryMenu, { categoryMenu = false }) {
                        DropdownMenuItem({ Text("No category") }, { onChange(draft.copy(categoryId = null)); categoryMenu = false })
                        categories.forEach { category -> DropdownMenuItem({ Text(category.name) }, { onChange(draft.copy(categoryId = category.id)); categoryMenu = false }) }
                    }
                }
                RecurrenceControl(draft.recurrenceRule, { onChange(draft.copy(recurrenceRule = it)) }, enabled, state.validation["recurrence"])
                HorizontalDivider()
                Text("Small steps", style = MaterialTheme.typography.titleMedium)
                draft.subtasks.forEachIndexed { index, subtask -> key(subtask.id) {
                    BuddyCard(contentPadding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(subtask.completed, { checked -> onChange(draft.copy(subtasks = draft.subtasks.map { if (it.id == subtask.id) it.copy(completed = checked) else it })) },
                                enabled = enabled, modifier = Modifier.semantics { contentDescription = "Complete subtask ${subtask.title}" })
                            OutlinedTextField(subtask.title, { title -> onChange(draft.copy(subtasks = draft.subtasks.map { if (it.id == subtask.id) it.copy(title = title) else it })) },
                                Modifier.weight(1f), label = { Text("Subtask ${index + 1}") }, enabled = enabled, singleLine = true,
                                isError = state.validation["subtasks"] != null && subtask.title.isBlank(),
                                supportingText = if (state.validation["subtasks"] != null && subtask.title.isBlank()) { { Text("Subtask title required") } } else null)
                        }
                        FlowRow {
                            TextButton({ onChange(draft.copy(subtasks = draft.subtasks.toMutableList().apply { add(index - 1, removeAt(index)) })) }, enabled = enabled && index > 0) { Text("Move up") }
                            TextButton({ onChange(draft.copy(subtasks = draft.subtasks.toMutableList().apply { add(index + 1, removeAt(index)) })) }, enabled = enabled && index < draft.subtasks.lastIndex) { Text("Move down") }
                            TextButton({ onChange(draft.copy(subtasks = draft.subtasks.filterNot { it.id == subtask.id })) }, enabled = enabled) { Text("Remove") }
                        }
                    }
                } }
                SecondaryButton("+ Add subtask", { onChange(draft.copy(subtasks = draft.subtasks + SubtaskDraft())) }, enabled = enabled)
                state.validation["subtasks"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                EditorField(draft.notes, "Private notes / optional", { onChange(draft.copy(notes = it)) }, enabled = enabled, multiline = true)
                HorizontalDivider()
                Text("Gentle reminders", style = MaterialTheme.typography.titleMedium)
                Text("${draft.alerts.count { it.enabled }} enabled alerts. All-day presets use 09:00.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 5, 10, 30).forEach { minutes ->
                        AssistChip(onClick = { if (draft.alerts.none { it.offsetMinutes == minutes }) onChange(draft.copy(alerts = draft.alerts + AlertDraft(offsetMinutes = minutes, followTaskRecurrence = true))) },
                            label = { Text(if (minutes == 0) "At event" else "$minutes min before") }, enabled = enabled)
                    }
                }
                draft.alerts.forEachIndexed { index, alert -> key(alert.id) {
                    BuddyCard(contentPadding = 12.dp) {
                        Text("Alert ${index + 1}", style = MaterialTheme.typography.titleSmall)
                        AlertFields(alert, { change -> onChange(draft.copy(alerts = draft.alerts.map { if (it.id == alert.id) change else it })) }, enabled, draft, state.validation["alerts.$index"])
                        TextButton({ onChange(draft.copy(alerts = draft.alerts.filterNot { it.id == alert.id })) }, enabled = enabled) { Text("Remove alert") }
                    }
                } }
                SecondaryButton("+ Custom reminder", { onChange(draft.copy(alerts = draft.alerts + AlertDraft(date = draft.date))) }, enabled = enabled)
                EditorToggle("Keep enabled alerts in the past", draft.allowPastAlerts, { onChange(draft.copy(allowPastAlerts = it)) }, enabled)
                Text("Past alerts expire without delivery; they are never silently moved to now.", style = MaterialTheme.typography.bodySmall)
                Text("Alerts are saved on this device. Delivery follows your Android notification, channel and timing permissions.", style = MaterialTheme.typography.bodySmall)
                (state.error ?: state.validation["past"])?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite; error(it) }) }
                com.focusbuddy.ui.reminders.ReminderSettingsEntry(compact = true)
                if (state.busy) LoadingState("Saving your changes")
                if (state.taskId != null) TextButton(onDelete, enabled = enabled) { Text("Delete task", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
