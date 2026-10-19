package com.focusbuddy.ui.today

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusbuddy.ui.screens.TodayActions
import com.focusbuddy.ui.screens.TodayScreen
import com.focusbuddy.ui.components.ConfirmationDialog
import java.time.LocalDate

@Composable
fun TodayRoute(viewModel: TodayViewModel, onSelectDate: (LocalDate) -> Unit, onToday: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel, onSelectDate, onToday) {
        TodayActions(onSelectDate, onToday, viewModel::createTask, viewModel::editTask,
            viewModel::completeTask, viewModel::retry, viewModel::dismissActionError)
    }
    TodayScreen(state, actions)
    state.editor?.let { editor ->
        TaskEditorSheet(editor, state.categories, viewModel::updateDraft, viewModel::saveEditor,
            { viewModel.dismissEditor() }, viewModel::requestDelete,
            { editor.taskId?.let { viewModel.editTask(it.toString()) } })
        if (editor.confirmDiscard) ConfirmationDialog("Discard these changes?", "Your unsaved changes will be lost.",
            "Discard", { viewModel.dismissEditor(force = true) }, viewModel::cancelDiscard)
        if (editor.confirmDelete) ConfirmationDialog("Delete this task?", "This also deletes its subtasks and attached reminders.",
            "Delete task", viewModel::deleteTask, viewModel::cancelDelete)
    }
}
