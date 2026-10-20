package com.focusbuddy.ui.explore

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusbuddy.ui.components.*
import com.focusbuddy.ui.screens.*
import com.focusbuddy.ui.today.*
import java.time.LocalDate
import java.time.format.*

@Composable
fun ExploreRoute(viewModel: ExploreViewModel, editorModel: TodayViewModel, select: (LocalDate) -> Unit, today: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editor by editorModel.editorState.collectAsStateWithLifecycle()
    ExploreScreen(state, ExploreActions(select, today, editorModel::createTask, editorModel::editTask,
        viewModel::openReminder, { viewModel.setWeekStart(it) }, viewModel::retry))
    TaskEditorHost(editorModel, editor, state.snapshot?.categories.orEmpty())
    if (state.detailKey != null) {
        val alert = state.snapshot?.alerts?.firstOrNull { it.key() == state.detailKey }
        BuddyBottomSheet("Reminder details", viewModel::closeReminder, canDismiss = !state.disabling) {
            if (alert == null) Text("This alert is no longer active.") else {
                Text(alert.reminder.title ?: alert.task?.title.orEmpty(), style = MaterialTheme.typography.titleLarge)
                Text(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(state.snapshot!!.zone).format(alert.trigger))
                Text(alert.task?.let { "Attached to: ${it.title}" } ?: "Standalone reminder")
                alert.reminder.description?.let { Text(it) }
                Text("Sound: ${if (alert.reminder.soundEnabled) "On" else "Off"} / Vibration: ${if (alert.reminder.vibrationEnabled) "On" else "Off"}")
                alert.reminder.recurrenceRule?.let { Text("Repeats: $it") }
                if (alert.reminder.snoozedUntil == alert.trigger) Text("Snoozed alert")
                alert.task?.let { task -> SecondaryButton("Open task", { viewModel.closeReminder(); editorModel.editTask(task.id.toString()) }, enabled = !state.disabling) }
                SecondaryButton(if (alert.reminder.recurrenceRule != null) "Disable all alerts in this series" else "Disable reminder",
                    viewModel::disableReminder, enabled = !state.disabling)
            }
            state.actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    } else if (state.actionError != null) {
        ConfirmationDialog("Couldn't save", state.actionError!!, "OK", viewModel::dismissError, viewModel::dismissError)
    }
}
