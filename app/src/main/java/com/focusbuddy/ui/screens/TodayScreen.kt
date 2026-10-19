package com.focusbuddy.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.focusbuddy.domain.model.Greeting
import com.focusbuddy.domain.model.TaskSection
import com.focusbuddy.ui.components.*
import com.focusbuddy.ui.today.TodayUiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class TodayActions(
    val selectDate: (LocalDate) -> Unit,
    val goToday: () -> Unit,
    val addTask: () -> Unit,
    val editTask: (String) -> Unit,
    val completeTask: (String, Boolean) -> Unit,
    val retry: () -> Unit,
    val dismissError: () -> Unit,
)

@Composable
fun TodayScreen(state: TodayUiState, actions: TodayActions) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.actionError) {
        state.actionError?.let { snackbar.showSnackbar(it); actions.dismissError() }
    }
    val counts = remember(state.days) { state.days.associate { it.date to DayCounts(it.taskCount, it.reminderAlertCount) } }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = actions.addTask,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary) { Text("+ Add task") }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item("header") { TodayHeader(state, actions.goToday) }
                item("dates") {
                    HorizontalDateSelector(state.dateChoices, state.selectedDate, actions.selectDate,
                        counts = counts, animationsEnabled = state.animationsEnabled)
                }
                when {
                    state.isLoading -> item("loading") { LoadingState("Loading your day") }
                    state.error != null -> item("error") { ErrorState(state.error, actions.retry) }
                    else -> {
                        item("progress") {
                            BuddyCard {
                                Text("YOUR DAILY MOMENTUM", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary)
                                DailyProgress(state.completed, state.total, animationsEnabled = state.animationsEnabled)
                            }
                        }
                        if (state.sections.isEmpty()) item("empty") {
                            BuddyCard {
                                EmptyState("A little breathing room", "A clear day, ready for what matters to you.",
                                    illustration = {
                                        if (state.showMascot) MascotArtwork(Modifier.size(80.dp))
                                    })
                                PrimaryButton("Plan your first task", actions.addTask, Modifier.fillMaxWidth())
                            }
                        }
                        state.sections.forEach { group ->
                            item("section-${group.section}") {
                                SectionTitle(when (group.section) {
                                    TaskSection.Overdue -> "Overdue"
                                    TaskSection.Upcoming -> "Up next"
                                    TaskSection.LaterToday -> "Later today"
                                    TaskSection.Anytime -> "At your own pace"
                                    TaskSection.Completed -> "Completed"
                                })
                            }
                            items(group.tasks, key = { "task-${it.id}" }, contentType = { "task" }) { task ->
                                TaskCard(task, { checked -> actions.completeTask(task.id, checked) },
                                    onClick = { actions.editTask(task.id) }, busy = task.id in state.pendingTaskIds)
                            }
                        }
                        if (state.reminders.isNotEmpty()) {
                            item("reminder-heading") { SectionTitle("Gentle reminders") }
                            items(state.reminders, key = { "reminder-${it.id}" }, contentType = { "reminder" }) { ReminderCard(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayHeader(state: TodayUiState, onToday: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.selectedDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(when (state.greeting) {
                Greeting.Morning -> "Good morning."
                Greeting.Afternoon -> "A softer afternoon."
                Greeting.Evening -> "A little evening clarity."
                Greeting.SelectedDay -> "Make room for your day."
            }, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            Text("One small step at a time.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onToday) { Text("Back to today") }
        }
        if (state.showMascot) MascotArtwork(Modifier.size(72.dp), "Your lavender companion")
    }
}
