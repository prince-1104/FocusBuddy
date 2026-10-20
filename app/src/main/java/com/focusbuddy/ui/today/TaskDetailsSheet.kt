package com.focusbuddy.ui.today

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.focusbuddy.domain.model.Category
import com.focusbuddy.ui.components.*
import java.time.ZoneId
import java.time.format.*
import java.util.UUID

@Composable
fun TaskDetailsSheet(state: TaskDetailsState, categories: List<Category>, onDismiss: () -> Unit, onRetry: () -> Unit,
    onEdit: () -> Unit, onDuplicate: () -> Unit, onComplete: () -> Unit, onDelete: () -> Unit,
    onSubtask: (UUID, Boolean) -> Unit) {
    BuddyBottomSheet("Task details", onDismiss, canDismiss = !state.busy) {
        when {
            state.loading -> LoadingState("Opening your plan")
            state.error != null -> ErrorState(state.error, onRetry)
            state.details == null -> Text("This task has been deleted.")
            else -> {
                val details = state.details
                val task = details.task
                val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
                Text(task.title, style = MaterialTheme.typography.headlineSmall)
                Text(task.scheduledDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)), color = MaterialTheme.colorScheme.primary)
                Text(if (task.allDay) "All day" else task.startAt?.let(time::format).orEmpty() + (task.endAt?.let { " to ${time.format(it)}" } ?: ""))
                Text("${task.priority.name} priority / ${if (task.completed) "Completed" else "Remaining"}")
                categories.firstOrNull { it.id == task.categoryId }?.let { CategoryBadge(it.name, CategoryTone.Blue, color = it.color) }
                task.description?.let { Text(it) }
                task.notes?.let { Text("Private notes", style = MaterialTheme.typography.titleSmall); Text(it) }
                task.recurrenceRule?.let { Text("Repeats: $it"); Text("Edits, completion and deletion apply to the whole series.", style = MaterialTheme.typography.bodySmall) }
                if (details.subtasks.isNotEmpty()) {
                    com.focusbuddy.ui.reminders.ReminderSettingsEntry(compact = true)
                HorizontalDivider()
                    val completed = details.subtasks.count { it.completed }
                    Text("Small steps / $completed of ${details.subtasks.size} complete", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(progress = { completed.toFloat() / details.subtasks.size }, modifier = Modifier.fillMaxWidth())
                    details.subtasks.forEach { subtask ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(subtask.completed, { onSubtask(subtask.id, it) }, enabled = !state.busy,
                                modifier = Modifier.semantics { contentDescription = "${if (subtask.completed) "Reopen" else "Complete"} subtask ${subtask.title}" })
                            Text(subtask.title, Modifier.weight(1f))
                        }
                    }
                }
                HorizontalDivider()
                Text("Reminder alerts / ${details.reminders.count { it.enabled }} enabled", style = MaterialTheme.typography.titleMedium)
                if (details.reminders.isEmpty()) Text("No alerts attached. Add one in Edit task.")
                details.reminders.forEach { reminder -> BuddyCard(contentPadding = 12.dp) {
                    Text(time.format(reminder.effectiveTriggerAt), style = MaterialTheme.typography.titleSmall)
                    Text(if (reminder.enabled) "Enabled schedule" else "Disabled")
                    if (reminder.snoozedUntil != null) Text("Snoozed")
                    reminder.recurrenceRule?.let { Text("Repeats: $it") }
                    Text("Sound ${if (reminder.soundEnabled) "on" else "off"} / Vibration ${if (reminder.vibrationEnabled) "on" else "off"}")
                } }
                Text("Alerts are stored locally; Delivery follows your Android notification, channel and timing permissions.", style = MaterialTheme.typography.bodySmall)
                state.actionError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                PrimaryButton("Edit task", onEdit, Modifier.fillMaxWidth(), !state.busy)
                SecondaryButton(if (task.completed) "Reopen${if (task.recurrenceRule != null) " series" else " task"}" else "Complete${if (task.recurrenceRule != null) " series" else " task"}", onComplete, Modifier.fillMaxWidth(), !state.busy)
                if (task.completed) Text("Reopening keeps canceled reminders disabled.", style = MaterialTheme.typography.bodySmall)
                SecondaryButton("Duplicate task", onDuplicate, Modifier.fillMaxWidth(), !state.busy)
                TextButton(onDelete, enabled = !state.busy) { Text("Delete task", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
