package com.focusbuddy.ui.today

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusbuddy.ui.screens.TodayActions
import com.focusbuddy.ui.screens.TodayScreen
import com.focusbuddy.ui.components.ConfirmationDialog
import java.time.LocalDate

@Composable
fun TodayRoute(viewModel: TodayViewModel, onSelectDate: (LocalDate) -> Unit, onToday: () -> Unit, onExplore: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel, onSelectDate, onToday, onExplore) {
        TodayActions(onSelectDate, onToday, viewModel::createTask, viewModel::openTask,
            viewModel::completeTask, viewModel::retry, viewModel::dismissActionError, onExplore, viewModel::createReminder, viewModel::editReminder, viewModel::openReminderLibrary)
    }
    TodayScreen(state, actions)
    TaskEditorHost(viewModel, state.editor, state.categories)
}

@Composable
fun TaskEditorHost(viewModel: TodayViewModel, editorState: TaskEditorState?, categories: List<com.focusbuddy.domain.model.Category>) {
    val library by viewModel.reminderLibrary.collectAsStateWithLifecycle()
    val details by viewModel.taskDetailsState.collectAsStateWithLifecycle()
    val reminder by viewModel.reminderEditorState.collectAsStateWithLifecycle()
    if (library.open) com.focusbuddy.ui.components.BuddyBottomSheet("Your reminders", viewModel::closeReminderLibrary) {
        when {
            library.loading -> com.focusbuddy.ui.components.LoadingState("Loading reminders")
            library.error != null -> com.focusbuddy.ui.components.ErrorState(library.error!!, viewModel::retryReminderLibrary)
            else -> {
                if (library.reminders.isEmpty()) androidx.compose.material3.Text("No standalone reminders yet.")
                library.reminders.forEach { record ->
                    com.focusbuddy.ui.components.BuddyCard(onClick = { viewModel.closeReminderLibrary(); viewModel.editReminder(record.id.toString()) }) {
                        androidx.compose.material3.Text(record.title.orEmpty(), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        androidx.compose.material3.Text(java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM).withZone(java.time.ZoneId.systemDefault()).format(record.effectiveTriggerAt))
                        androidx.compose.material3.Text(if (record.enabled) "Enabled" else "Disabled / tap to edit or enable")
                    }
                }
                com.focusbuddy.ui.components.PrimaryButton("+ Standalone reminder", { viewModel.closeReminderLibrary(); viewModel.createReminder() })
            }
        }
    }
    details?.let { state ->
        TaskDetailsSheet(state, categories, viewModel::closeTask, viewModel::retryDetails, viewModel::editDetails,
            viewModel::duplicateDetails, viewModel::completeDetails, viewModel::requestDetailsDelete, viewModel::toggleSubtask)
        if (state.confirmDelete) ConfirmationDialog("Delete this task?", "This deletes the whole task series, its subtasks and attached reminders.",
            "Delete task", viewModel::deleteDetails, viewModel::cancelDetailsDelete)
    }
    reminder?.let { state ->
        ReminderEditorSheet(state, viewModel::updateReminderDraft, viewModel::saveReminder, { viewModel.dismissReminder() },
            viewModel::requestReminderDelete, { state.id?.let { viewModel.editReminder(it.toString()) } })
        if (state.confirmDiscard) ConfirmationDialog("Discard reminder changes?", "Your unsaved changes will be lost.", "Discard", { viewModel.dismissReminder(force = true) }, viewModel::cancelReminderDiscard)
        if (state.confirmDelete) ConfirmationDialog("Delete reminder?", "This deletes the reminder and all future alerts in its series.", "Delete reminder", viewModel::deleteReminder, viewModel::cancelReminderDelete)
    }
    editorState?.let { editor ->
        TaskEditorSheet(editor, categories, viewModel::updateDraft, viewModel::saveEditor,
            { viewModel.dismissEditor() }, viewModel::requestDelete,
            { editor.taskId?.let { viewModel.editTask(it.toString()) } })
        if (editor.confirmDiscard) ConfirmationDialog("Discard these changes?", "Your unsaved changes will be lost.",
            "Discard", { viewModel.dismissEditor(force = true) }, viewModel::cancelDiscard)
        if (editor.confirmDelete) ConfirmationDialog("Delete this task?", "This also deletes its subtasks and attached reminders.",
            "Delete task", viewModel::deleteTask, viewModel::cancelDelete)
    }
}
