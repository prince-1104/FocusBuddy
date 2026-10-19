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
    val today = TodayUiState(tasks, reminders)
    val counts = mapOf(date to DayCounts(3, 1), date.plusDays(1) to DayCounts(0, 2), date.plusDays(2) to DayCounts(2, 0))
}

@Preview(name = "Today · ivory", widthDp = 390, heightDp = 900)
@Preview(name = "Today · dark", widthDp = 390, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TodayPreview() {
    FocusBuddyTheme {
        Surface { TodayScreen(PreviewData.today, {}, {}, today = PreviewData.date) }
    }
}

@Preview(name = "Today · empty · large text", widthDp = 320, heightDp = 900, fontScale = 1.6f)
@Composable
private fun EmptyTodayPreview() {
    FocusBuddyTheme { Surface { TodayScreen(TodayUiState(), {}, {}, today = PreviewData.date) } }
}

@Preview(name = "Explore · separate date counts", widthDp = 390, heightDp = 1000)
@Preview(name = "Explore · dark · large text", widthDp = 320, heightDp = 1000, fontScale = 1.5f,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ExplorePreview() {
    FocusBuddyTheme { Surface { ExploreScreen(PreviewData.date, PreviewData.counts) } }
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
    FocusBuddyTheme(mode) {
        FocusBuddyContent(destination, { destination = it }, mode, { mode = it })
    }
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
