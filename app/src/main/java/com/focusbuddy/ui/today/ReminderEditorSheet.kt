package com.focusbuddy.ui.today

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.focusbuddy.domain.model.ReminderDraft
import com.focusbuddy.ui.components.*

@Composable
fun ReminderEditorSheet(state: ReminderEditorState, onChange: (ReminderDraft) -> Unit, onSave: () -> Unit,
    onDismiss: () -> Unit, onDelete: () -> Unit, onRetry: () -> Unit) {
    val draft = state.draft
    BuddyBottomSheet(if (state.id == null) "Create a reminder" else "Edit reminder", onDismiss, canDismiss = !state.busy, hasUnsavedChanges = draft != state.original, footer = {
        EditorSaveFooter(if (state.busy) "Saving reminder..." else if (state.id == null) "Create reminder" else "Save reminder",
            onSave, !state.busy && !state.loading && state.loadError == null, state.error ?: state.validation.values.firstOrNull())
    }) {
        when {
            state.loading -> LoadingState("Opening reminder")
            state.loadError != null -> ErrorState(state.loadError, onRetry)
            else -> {
                Text("A gentle nudge, without a task attached.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                EditorField(draft.title, "Reminder title / required", { onChange(draft.copy(title = it)) }, state.validation["title"], !state.busy)
                EditorField(draft.description, "Description / optional", { onChange(draft.copy(description = it)) }, enabled = !state.busy, multiline = true)
                AlertFields(draft.alert, { onChange(draft.copy(alert = it)) }, !state.busy, error = state.validation["trigger"])
                EditorToggle("Keep this enabled alert in the past", draft.allowPast, { onChange(draft.copy(allowPast = it)) }, !state.busy)
                Text("Past alerts expire without delivery. They will not be moved automatically.", style = MaterialTheme.typography.bodySmall)
                Text("Saved locally. Delivery follows your Android notification, channel and timing permissions.", style = MaterialTheme.typography.bodySmall)
                (state.error ?: state.validation["past"])?.let { Text(it, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite; error(it) }) }
                com.focusbuddy.ui.reminders.ReminderSettingsEntry(compact = true)
                if (state.busy) LoadingState("Saving reminder")
                if (state.id != null) TextButton(onDelete, enabled = !state.busy) { Text("Delete reminder", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
