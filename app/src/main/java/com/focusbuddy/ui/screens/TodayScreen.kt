package com.focusbuddy.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.focusbuddy.ui.components.*

/** Display state only. Sample tasks are supplied exclusively by debug previews. */
data class TodayUiState(
    val tasks: List<TaskCardData> = emptyList(),
    val reminders: List<ReminderCardData> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@Composable
fun TodayScreen(state: TodayUiState, onExplore: (LocalDate) -> Unit, onRetry: () -> Unit,
    onCompletionChange: ((String, Boolean) -> Unit)? = null,
    today: LocalDate = LocalDate.now()) {
    var selectedDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedDay)
    ScreenPage("Today", "Make room for what matters.") {
        MascotContainer("Small steps. A lighter day.", "Your companion for a little more focus and a little less rush.")
        HorizontalDateSelector((-3L..3L).map(today::plusDays), selected, { selectedDay = it.toEpochDay() })
        if (selected != today) {
            BuddyCard {
                Text(selected.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)), style = MaterialTheme.typography.titleMedium)
                Text("Visit Explore to plan this day.", style = MaterialTheme.typography.bodyMedium)
                SecondaryButton("Open Explore", { onExplore(selected) })
            }
        } else {
            when {
                state.isLoading -> LoadingState()
                state.error != null -> ErrorState(state.error, onRetry)
                else -> {
                    BuddyCard { DailyProgress(state.tasks.count { it.completed }, state.tasks.size) }
                    SectionTitle("Your daily tasks")
                    if (state.tasks.isEmpty()) {
                        BuddyCard {
                            EmptyState("A little breathing room", "Your tasks will have a home here. Task planning is coming soon.")
                            PrimaryButton("Add task · coming soon", {}, Modifier.fillMaxWidth(), enabled = false)
                        }
                    } else {
                        state.tasks.forEach { task ->
                            TaskCard(task, onCompletionChange?.let { callback -> { checked -> callback(task.id, checked) } })
                        }
                    }
                    SectionTitle("Gentle reminders")
                    if (state.reminders.isEmpty()) {
                        ComingNext("No reminders to show. Reminder scheduling is coming soon.")
                    } else {
                        state.reminders.forEach { ReminderCard(it) }
                    }
                }
            }
        }
    }
}
