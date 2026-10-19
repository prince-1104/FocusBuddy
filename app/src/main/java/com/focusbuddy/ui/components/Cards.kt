package com.focusbuddy.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

enum class CategoryTone { Coral, Orange, Green, Blue }
enum class Priority { Low, Medium, High }

data class TaskCardData(
    val id: String,
    val title: String,
    val detail: String,
    val category: String,
    val tone: CategoryTone,
    val priority: Priority,
    val completed: Boolean = false,
)

data class ReminderCardData(val title: String, val schedule: String, val category: String)

@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick, modifier.heightIn(min = 48.dp), enabled = enabled) { Text(label) }
}

@Composable
fun SecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick, modifier.heightIn(min = 48.dp), enabled = enabled) { Text(label) }
}

@Composable
fun BuddyCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun CategoryBadge(label: String, tone: CategoryTone, modifier: Modifier = Modifier) {
    // Fixed pastel backgrounds with dark ink keep badge contrast stable in both themes.
    val colors = when (tone) {
        CategoryTone.Coral -> Color(0xFFFFE0DA) to Color(0xFF793A32)
        CategoryTone.Orange -> Color(0xFFFFE8CC) to Color(0xFF704616)
        CategoryTone.Green -> Color(0xFFE0EDD9) to Color(0xFF34532A)
        CategoryTone.Blue -> Color(0xFFDFEAF8) to Color(0xFF304D76)
    }
    Surface(modifier, shape = RoundedCornerShape(8.dp), color = colors.first, contentColor = colors.second) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun PriorityIndicator(priority: Priority, modifier: Modifier = Modifier) {
    val label = when (priority) {
        Priority.High -> "High priority"
        Priority.Medium -> "Medium priority"
        Priority.Low -> "Low priority"
    }
    Text(label, modifier, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
}

@Composable
fun CompletionControl(completed: Boolean, title: String, onChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    Checkbox(
        checked = completed,
        onCheckedChange = onChange,
        enabled = onChange != null,
        modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics {
            contentDescription = if (completed) "Mark $title incomplete" else "Complete $title"
        },
    )
}

@Composable
fun TaskCard(task: TaskCardData, onCompletionChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    BuddyCard(modifier) {
        Row(verticalAlignment = Alignment.Top) {
            CompletionControl(task.completed, task.title, onCompletionChange)
            Column(Modifier.weight(1f).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else null)
                Text(task.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CategoryBadge(task.category, task.tone)
                PriorityIndicator(task.priority)
            }
        }
    }
}

@Composable
fun ReminderCard(reminder: ReminderCardData, modifier: Modifier = Modifier) {
    BuddyCard(modifier.semantics(mergeDescendants = true) {}) {
        Text("Reminder", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(reminder.title, style = MaterialTheme.typography.titleMedium)
        Text(reminder.schedule, style = MaterialTheme.typography.bodyMedium)
        CategoryBadge(reminder.category, CategoryTone.Orange)
    }
}

@Composable
fun DailyProgress(completed: Int, total: Int, modifier: Modifier = Modifier) {
    val safeTotal = total.coerceAtLeast(0)
    val safeCompleted = completed.coerceIn(0, safeTotal)
    val progress by animateFloatAsState(
        if (safeTotal == 0) 0f else safeCompleted.toFloat() / safeTotal,
        label = "Daily completion",
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (safeTotal == 0) "A fresh start" else "$safeCompleted of $safeTotal tasks complete",
            style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(
            progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp)
                .semantics { contentDescription = "Daily task progress" },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer,
        )
    }
}
