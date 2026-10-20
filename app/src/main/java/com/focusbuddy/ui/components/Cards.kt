package com.focusbuddy.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
    val category: String?,
    val tone: CategoryTone,
    val priority: Priority,
    val completed: Boolean = false,
    val categoryColor: Long? = null,
    val reminderCount: Int = 0,
    val overdue: Boolean = false,
    val series: Boolean = false,
    val subtasksCompleted: Int = 0, val subtasksTotal: Int = 0,
)

data class ReminderCardData(val title: String, val schedule: String, val category: String, val id: String = "$title-$schedule")

@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick, modifier.heightIn(min = 48.dp), enabled = enabled) { Text(label) }
}

@Composable
fun SecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick, modifier.heightIn(min = 48.dp), enabled = enabled) { Text(label) }
}

@Composable
fun BuddyCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, border: BorderStroke? = null,
    enabled: Boolean = true, contentPadding: androidx.compose.ui.unit.Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    val elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    if (onClick == null) Card(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = colors, elevation = elevation, border = border) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    } else Card(onClick, modifier.fillMaxWidth(), enabled = enabled, shape = MaterialTheme.shapes.large,
        colors = colors, elevation = elevation, border = border) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun CategoryBadge(label: String, tone: CategoryTone, modifier: Modifier = Modifier, color: Long? = null) {
    // Fixed pastel backgrounds with dark ink keep badge contrast stable in both themes.
    val colors = when (tone) {
        CategoryTone.Coral -> Color(0xFFFFE0DA) to Color(0xFF793A32)
        CategoryTone.Orange -> Color(0xFFFFE8CC) to Color(0xFF704616)
        CategoryTone.Green -> Color(0xFFE0EDD9) to Color(0xFF34532A)
        CategoryTone.Blue -> Color(0xFFDFEAF8) to Color(0xFF304D76)
    }
    Surface(modifier, shape = RoundedCornerShape(8.dp),
        color = if (color == null) colors.first else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (color == null) colors.second else MaterialTheme.colorScheme.onSurface) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (color != null) Box(Modifier.size(8.dp).background(Color(color), androidx.compose.foundation.shape.CircleShape))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
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
fun TaskCard(task: TaskCardData, onCompletionChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null, busy: Boolean = false) {
    val border = when {
        task.overdue -> BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = .45f))
        task.priority == Priority.High && !task.completed -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .4f))
        else -> null
    }
    BuddyCard(modifier, onClick = onClick, border = border, enabled = !busy) {
        Row(verticalAlignment = Alignment.Top) {
            if (busy) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp).semantics { contentDescription = "Updating ${task.title}" }, strokeWidth = 2.dp)
            } else CompletionControl(task.completed, task.title + if (task.series) " (entire recurring series)" else "", onCompletionChange)
            Column(Modifier.weight(1f).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else null)
                Text(task.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (task.category != null) CategoryBadge(task.category, task.tone, color = task.categoryColor)
                PriorityIndicator(task.priority)
                if (task.subtasksTotal > 0) Text("${task.subtasksCompleted}/${task.subtasksTotal} small steps complete", style = MaterialTheme.typography.labelMedium)
                if (task.series) Text("Repeats · completion applies to series", style = MaterialTheme.typography.labelSmall)
                if (task.overdue) Text("Overdue", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                if (task.completed) Text("Completed", style = MaterialTheme.typography.labelMedium)
                if (task.reminderCount > 0) Text(
                    if (task.reminderCount == 1) "1 reminder" else "${task.reminderCount} reminders",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun ReminderCard(reminder: ReminderCardData, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    BuddyCard(modifier.semantics(mergeDescendants = true) {}, onClick = onClick) {
        Text("Reminder", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(reminder.title, style = MaterialTheme.typography.titleMedium)
        Text(reminder.schedule, style = MaterialTheme.typography.bodyMedium)
        CategoryBadge(reminder.category, CategoryTone.Orange)
    }
}

@Composable
fun DailyProgress(completed: Int, total: Int, modifier: Modifier = Modifier, animationsEnabled: Boolean = true) {
    val safeTotal = total.coerceAtLeast(0)
    val safeCompleted = completed.coerceIn(0, safeTotal)
    val progress by animateFloatAsState(
        if (safeTotal == 0) 0f else safeCompleted.toFloat() / safeTotal,
        animationSpec = if (animationsEnabled) tween(220) else snap(),
        label = "Daily completion",
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("$safeCompleted of $safeTotal scheduled tasks complete",
            style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(
            progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp)
                .semantics { contentDescription = "Daily task progress" },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer,
        )
    }
}
