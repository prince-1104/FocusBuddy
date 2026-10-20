package com.focusbuddy.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.focusbuddy.ui.components.*
import com.focusbuddy.ui.explore.*
import com.focusbuddy.domain.calendar.*
import java.time.*
import java.time.format.*
import java.util.Locale

data class ExploreActions(
    val select: (LocalDate) -> Unit, val today: () -> Unit, val add: () -> Unit,
    val edit: (String) -> Unit, val reminder: (String) -> Unit,
    val weekStart: (DayOfWeek?) -> Unit, val retry: () -> Unit,
    val addReminder: () -> Unit = {}, val manageReminders: () -> Unit = {},
)

@Composable
fun ExploreScreen(state: ExploreState, actions: ExploreActions) {
    val snapshot = state.snapshot
    val time = remember(snapshot?.zone) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(snapshot?.zone ?: ZoneId.systemDefault()) }
    val counts = remember(snapshot) { snapshot?.days.orEmpty().associate { it.date to DayCounts(it.taskCount, it.reminderAlertCount) } }
    var weekMenu by remember { mutableStateOf(false) }
    val agenda = remember(state.selected, snapshot) { snapshot?.agenda(state.selected).orEmpty() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("YOUR RHYTHM", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text("Explore", style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
            Text("Space for the month. Attention for the day.", style = MaterialTheme.typography.bodyLarge)
        }
        item {
            BuddyCard(contentPadding = 8.dp) {
                Text(state.month.format(DateTimeFormatter.ofPattern("MMMM uuuu")), style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton({ actions.select(state.selected.minusMonths(1)) }, Modifier.semantics { contentDescription = "Previous month" }) { Text("Previous") }
                    TextButton(actions.today) { Text("Today") }
                    TextButton({ actions.select(state.selected.plusMonths(1)) }, Modifier.semantics { contentDescription = "Next month" }) { Text("Next") }
                }
                MonthCalendar(state.month, state.selected, counts, actions.select,
                    today = state.today, firstDay = state.firstDay)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Number: tasks", style = MaterialTheme.typography.labelSmall)
                    BellIcon(Modifier.size(14.dp))
                    Text("Reminder alerts", style = MaterialTheme.typography.labelSmall)
                }
                Box {
                    TextButton({ weekMenu = true }) {
                        Text("Week starts: " + if (state.followsLocale) "Device default" else state.firstDay.getDisplayName(TextStyle.FULL, Locale.getDefault()))
                    }
                    DropdownMenu(weekMenu, { weekMenu = false }) {
                        DropdownMenuItem({ Text("Device default") }, { actions.weekStart(null); weekMenu = false })
                        DayOfWeek.entries.forEach { day -> DropdownMenuItem({ Text(day.getDisplayName(TextStyle.FULL, Locale.getDefault())) },
                            { actions.weekStart(day); weekMenu = false }) }
                    }
                }
            }
        }
        item {
            Text(state.selected.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() })
        }
        when {
            state.loading -> item { LoadingState("Gathering this month's plans") }
            state.error != null -> item { ErrorState(state.error, actions.retry) }
            snapshot != null -> {
                item {
                    BuddyCard {
                        Text("${state.tasks.size} tasks / ${state.alerts.size} reminder alerts", style = MaterialTheme.typography.titleMedium)
                        Text("${state.completed} completed / ${state.tasks.size - state.completed} remaining")
                        DailyProgress(state.completed, state.tasks.size)
                        PrimaryButton("+ Add task", actions.add, Modifier.fillMaxWidth())
                        TextButton(actions.addReminder) { Text("+ Standalone reminder") }
                        TextButton(actions.manageReminders) { Text("Manage reminders") }
                    }
                }
                if (agenda.isEmpty()) item { EmptyState("A little breathing room", "No tasks or reminder alerts here. Make room for something that matters.") }
                items(agenda, key = { it.key }) { row ->
                    (row as? CalendarEntry.ScheduledTask)?.occurrence?.let { occurrence ->
                        val task = occurrence.task
                        val category = snapshot.categories.firstOrNull { it.id == task.categoryId }
                        TaskCard(TaskCardData(task.id.toString(), task.title,
                            (occurrence.start?.let(time::format) ?: "All day") + if (task.recurrenceRule != null) " / Repeats / edit series" else "",
                            category?.name, CategoryTone.Blue, Priority.valueOf(task.priority.name), task.completed,
                            categoryColor = category?.color, subtasksCompleted = occurrence.subtasksCompleted, subtasksTotal = occurrence.subtasksTotal), null, onClick = { actions.edit(task.id.toString()) })
                    }
                    (row as? CalendarEntry.ReminderAlert)?.occurrence?.let { alert ->
                        BuddyCard(onClick = { actions.reminder(alert.key()) }) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BellIcon(Modifier.size(18.dp))
                                Text(time.format(alert.trigger), style = MaterialTheme.typography.labelLarge)
                            }
                            Text(alert.reminder.title ?: alert.task?.title.orEmpty(), style = MaterialTheme.typography.titleMedium)
                            Text(alert.task?.let { "Reminder for ${it.title}" } ?: "Standalone reminder", style = MaterialTheme.typography.bodyMedium)
                            if (alert.reminder.recurrenceRule != null) Text("Repeating alert", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}
