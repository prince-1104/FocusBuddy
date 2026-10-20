package com.focusbuddy.ui.preview

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.focusbuddy.ui.*
import com.focusbuddy.ui.components.*
import com.focusbuddy.ui.screens.*
import com.focusbuddy.ui.theme.*
import com.focusbuddy.domain.model.ThemeMode
import com.focusbuddy.domain.model.Greeting
import com.focusbuddy.domain.model.TaskSection
import com.focusbuddy.domain.model.DaySummary
import com.focusbuddy.domain.model.TaskDraft
import com.focusbuddy.ui.today.TodayUiState
import com.focusbuddy.ui.today.TaskSectionUi
import com.focusbuddy.ui.today.TaskEditorState
import com.focusbuddy.ui.today.TaskEditorSheet
import java.time.LocalDate

/** Debug source set only. No application code can access these fixtures. */
private object PreviewData {
    val date: LocalDate = LocalDate.of(2026, 10, 19)
    val tasks = listOf(
        TaskCardData("read", "A chapter, just for you", "20 minutes · after breakfast", "Personal", CategoryTone.Coral, Priority.Low),
        TaskCardData("write", "Make space for the first draft", "45 minutes · a quiet desk", "Work", CategoryTone.Blue, Priority.High),
        TaskCardData("walk", "Step outside for a little while", "15 minutes · fresh air", "Wellbeing", CategoryTone.Green, Priority.Medium, true),
    )
    val reminders = listOf(ReminderCardData("Give your eyes a little break", "Today at 3:00 PM", "Wellbeing"))
    val today = TodayUiState(selectedDate = date, today = date, greeting = Greeting.Morning,
        sections = listOf(TaskSectionUi(TaskSection.Anytime, tasks.take(2)), TaskSectionUi(TaskSection.Completed, tasks.takeLast(1))),
        reminders = reminders, completed = 1, total = 3,
        days = (-14L..14L).map { offset -> DaySummary(date.plusDays(offset), if (offset == 0L) 3 else 0, if (offset == 0L) 1 else 0) })
    val counts = mapOf(date to DayCounts(3, 1), date.plusDays(1) to DayCounts(0, 2), date.plusDays(2) to DayCounts(2, 0))
}

private val PreviewActions = TodayActions({}, {}, {}, {}, { _, _ -> }, {}, {})

@Preview(name = "Today · ivory", widthDp = 390, heightDp = 900)
@Preview(name = "Today · dark", widthDp = 390, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TodayPreview() {
    FocusBuddyTheme {
        Surface { TodayScreen(PreviewData.today, PreviewActions) }
    }
}

@Preview(name = "Today · empty · large text", widthDp = 320, heightDp = 900, fontScale = 1.6f)
@Composable
private fun EmptyTodayPreview() {
    FocusBuddyTheme { Surface { TodayScreen(TodayUiState(selectedDate = PreviewData.date), PreviewActions) } }
}

@Preview(name = "Explore · separate date counts", widthDp = 390, heightDp = 1000)
@Preview(name = "Explore · dark · large text", widthDp = 320, heightDp = 1000, fontScale = 1.5f,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ExplorePreview() {
    var selected by remember { mutableStateOf(PreviewData.date) }
    FocusBuddyTheme { Surface { ExploreScreen(previewExplore(selected), com.focusbuddy.ui.screens.ExploreActions({ selected = it }, { selected = PreviewData.date }, {}, {}, {}, {}, {})) } }
}

@Preview(name = "Focus", widthDp = 390, heightDp = 900)
@Composable
private fun FocusPreview() { FocusBuddyTheme { Surface { FocusScreen() } } }

@Preview(name = "Mascot", widthDp = 390, heightDp = 900)
@Composable
private fun MascotPreview() { FocusBuddyTheme { Surface { MascotScreen() } } }

@Preview(name = "Profile · appearance", widthDp = 390, heightDp = 1000)
@Composable
private fun ProfilePreview() {
    var mode by remember { mutableStateOf(ThemeMode.System) }
    FocusBuddyTheme(mode) { Surface { ProfileScreen(mode, { mode = it }) } }
}

@Preview(name = "Phone navigation", widthDp = 390, heightDp = 900)
@Preview(name = "Compact · large text", widthDp = 320, heightDp = 900, fontScale = 1.6f)
@Preview(name = "Tablet navigation", widthDp = 900, heightDp = 1000)
@Composable
private fun NavigationPreview() {
    var destination by remember { mutableStateOf(Destination.Today) }
    var mode by remember { mutableStateOf(ThemeMode.System) }
    var selected by remember { mutableStateOf(PreviewData.date) }
    FocusBuddyTheme(mode) {
        FocusBuddyContent(destination, { destination = it }, mode, { mode = it },
            todayContent = { TodayScreen(PreviewData.today.copy(selectedDate = selected), PreviewActions.copy(selectDate = { selected = it }, goToday = { selected = PreviewData.date })) },
            exploreContent = { ExploreScreen(previewExplore(selected), ExploreActions({ selected = it }, { selected = PreviewData.date }, {}, {}, {}, {}, {})) })
    }
}

@Preview(name = "Task creation", widthDp = 390, heightDp = 900)
@Composable
private fun TaskEditorPreview() {
    var state by remember { mutableStateOf(TaskEditorState(draft = TaskDraft(title = "One meaningful step", date = PreviewData.date.toString()))) }
    FocusBuddyTheme { TaskEditorSheet(state, emptyList(), { state = state.copy(draft = it) }, {}, {}, {}, {}) }
}

@Preview(name = "Reusable components", widthDp = 390, heightDp = 1400)
@Preview(name = "Reusable components · dark", widthDp = 390, heightDp = 1400,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ComponentGalleryPreview() {
    FocusBuddyTheme {
        Surface {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                PrimaryButton("Make a little progress", {})
                SecondaryButton("Take your time", {})
                PreviewData.tasks.forEach { task ->
                    var complete by remember { mutableStateOf(task.completed) }
                    TaskCard(task.copy(completed = complete), { complete = it })
                }
                ReminderCard(PreviewData.reminders.first())
                CategoryBadge("Learning", CategoryTone.Orange)
                DailyProgress(1, 3)
                LoadingState()
                ErrorState("Your plans couldn't be loaded. Please try again.", {})
                EmptyState("Nothing here yet", "A fresh little space, ready when you are.")
            }
        }
    }
}

@Preview(name = "Confirmation dialog", widthDp = 390, heightDp = 600)
@Composable
private fun DialogPreview() {
    FocusBuddyTheme { ConfirmationDialog("Use your device theme?", "Follow your device's appearance.", "Use device theme", {}, {}) }
}

@Preview(name = "Bottom sheet", widthDp = 390, heightDp = 700)
@Composable
private fun SheetPreview() {
    FocusBuddyTheme {
        BuddyBottomSheet("A little more room to focus", {}) {
            Text("Your quiet corner for the things that matter.")
            PrimaryButton("Sounds good", {})
        }
    }
}

private fun previewExplore(selected: LocalDate): com.focusbuddy.ui.explore.ExploreState {
    val zone = java.time.ZoneId.of("Asia/Kolkata")
    val task = com.focusbuddy.domain.model.Task(title = "A quiet moment to plan", scheduledDate = PreviewData.date)
    val reminder = com.focusbuddy.domain.model.Reminder(title = "Step outside", triggerAt = PreviewData.date.atTime(16, 0).atZone(zone).toInstant())
    return com.focusbuddy.ui.explore.ExploreState(selected = selected, today = PreviewData.date,
        snapshot = com.focusbuddy.domain.calendar.calendarSnapshot(listOf(task), listOf(reminder), emptyList(), java.time.YearMonth.from(selected), zone), loading = false)
}

@Preview(name = "Standalone reminder editor", widthDp = 390, heightDp = 1000)
@Preview(name = "Standalone reminder / dark / large text", widthDp = 320, heightDp = 1000, fontScale = 1.5f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReminderEditorPreview() {
    var state by remember { mutableStateOf(com.focusbuddy.ui.today.ReminderEditorState(draft = com.focusbuddy.domain.model.ReminderDraft(
        title = "A moment away from the screen", alert = com.focusbuddy.domain.model.AlertDraft(date = PreviewData.date.toString(), time = "15:00")))) }
    FocusBuddyTheme { com.focusbuddy.ui.today.ReminderEditorSheet(state, { state = state.copy(draft = it) }, {}, {}, {}, {}) }
}

@Preview(name = "Task details / small steps", widthDp = 390, heightDp = 1000)
@Preview(name = "Task details / dark", widthDp = 390, heightDp = 1000, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TaskDetailsPreview() {
    val task = remember { com.focusbuddy.domain.model.Task(title = "Make space for the first draft", description = "One clear idea is enough to begin.",
        scheduledDate = PreviewData.date, notes = "Start with the outline.", priority = com.focusbuddy.domain.model.TaskPriority.High) }
    val details = com.focusbuddy.domain.model.TaskDetails(task, listOf(com.focusbuddy.domain.model.Reminder(taskId = task.id,
        triggerAt = PreviewData.date.atTime(9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant())), listOf(
        com.focusbuddy.domain.model.Subtask(taskId = task.id, title = "Gather a few thoughts", completed = true),
        com.focusbuddy.domain.model.Subtask(taskId = task.id, title = "Write the opening paragraph", sortOrder = 1)))
    FocusBuddyTheme { com.focusbuddy.ui.today.TaskDetailsSheet(com.focusbuddy.ui.today.TaskDetailsState(task.id, details), emptyList(), {}, {}, {}, {}, {}, {}, { _, _ -> }) }
}
