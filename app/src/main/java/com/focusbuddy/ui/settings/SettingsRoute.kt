package com.focusbuddy.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.mascot.MascotVariant
import com.focusbuddy.ui.components.*
import com.focusbuddy.ui.screens.ScreenPage
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsRoute(model: SettingsViewModel, onStudio: () -> Unit, beforeClear: () -> Unit, afterClear: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val confirm by model.confirmClear.collectAsStateWithLifecycle()
    val clearing by model.clearing.collectAsStateWithLifecycle()
    ScreenPage("Your space", "Personal by design. Everything stays on this device.") {
        val value = state.settings
        if (value == null) {
            if (state.error != null) ErrorState(state.error!!, model::retry) else LoadingState("Opening your preferences")
        } else {
            BuddyCard {
                Text("Appearance", style = MaterialTheme.typography.titleLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { theme -> FilterChip(value.theme == theme, { model.theme(theme) }, label = { Text(theme.name) }, enabled = !busy) }
                }
                SettingSwitch("Gentle animations", value.animationsEnabled, !busy, model::animations)
                Text("Also follows Android's reduced-motion setting.")
            }
            BuddyCard {
                Text("Dates and time", style = MaterialTheme.typography.titleLarge)
                var choosing by remember { mutableStateOf(false) }
                Box {
                    SecondaryButton("Week starts: ${value.weekStart?.getDisplayName(TextStyle.FULL, Locale.getDefault()) ?: "Device default"}", { choosing = true }, enabled = !busy)
                    DropdownMenu(choosing, { choosing = false }) {
                        DropdownMenuItem({ Text("Device default") }, { model.weekStart(null); choosing = false })
                        DayOfWeek.entries.forEach { day -> DropdownMenuItem({ Text(day.getDisplayName(TextStyle.FULL, Locale.getDefault())) }, { model.weekStart(day); choosing = false }) }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeFormat.entries.forEach { format -> FilterChip(value.timeFormat == format, { model.update(value.copy(timeFormat = format)) },
                        label = { Text(if (format == TimeFormat.Hour12) "12-hour time" else "24-hour time") }, enabled = !busy) }
                }
            }
            BuddyCard {
                Text("New reminder defaults", style = MaterialTheme.typography.titleLarge)
                Text("New task alert · existing tasks and alerts keep their settings.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf<Int?>(null, 0, 5, 10, 30).forEach { offset -> FilterChip(value.defaultReminderOffset == offset,
                        { model.update(value.copy(defaultReminderOffset = offset)) }, label = { Text(when (offset) { null -> "None"; 0 -> "At event"; else -> "$offset min before" }) }, enabled = !busy) }
                }
                SettingSwitch("Default alert sound", value.defaultAlertSound, !busy) { model.update(value.copy(defaultAlertSound = it)) }
                SettingSwitch("Default vibration", value.defaultAlertVibration, !busy) { model.update(value.copy(defaultAlertVibration = it)) }
                Text("All-day task presets use 09:00. Android notification channels can override sound and vibration.", style = MaterialTheme.typography.bodySmall)
            }
            BuddyCard {
                Text("Your companion · ${MascotVariant.from(value.selectedMascot).label}", style = MaterialTheme.typography.titleLarge)
                SettingSwitch("Show companion", value.showMascot, !busy, model::visible)
                Text("${value.personality.name} personality · Companion sounds ${if (value.soundEnabled) "on" else "off"}")
                SecondaryButton("Open Mascot Studio", onStudio, enabled = !busy)
            }
            BuddyCard {
                Text("Notification delivery", style = MaterialTheme.typography.titleLarge)
                com.focusbuddy.ui.reminders.ReminderSettingsEntry()
                Text("Review notification permission, channels and exact-alarm access. Without exact access, user reminders use inexact delivery.")
            }
            BuddyCard {
                Text("Data management", style = MaterialTheme.typography.titleLarge)
                Text("Clear tasks, reminders, categories, focus history, the active timer and preferences. Android's own permission and channel choices remain in system settings.")
                SecondaryButton("Clear all data", { model.confirmClear.value = true }, Modifier.fillMaxWidth(), !busy)
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (busy) LoadingState("Saving your changes")
        }
    }
    if (confirm) ConfirmationDialog("Clear all FocusBuddy data?", "This permanently removes all personal records and preferences and cancels reminder notifications. This cannot be undone. FocusBuddy will reopen with an empty day.",
        "Clear all data", { model.clear(beforeClear, afterClear) }, { model.confirmClear.value = false })
    if (clearing) androidx.compose.ui.window.Dialog(onDismissRequest = {},
        properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(shape = MaterialTheme.shapes.large) { LoadingState("Clearing your data") }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange, enabled = enabled, modifier = Modifier.semantics { contentDescription = label })
    }
}
