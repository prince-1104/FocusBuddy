package com.focusbuddy.ui.mascot

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusbuddy.domain.mascot.*
import com.focusbuddy.domain.model.*
import com.focusbuddy.ui.components.*
import com.focusbuddy.ui.theme.FocusBuddyTheme

@Composable
fun MascotStudioRoute(model: MascotStudioViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    MascotStudio(state, model::change, model::save, model::discard, model::requestReset, model::retry)
    if (state.confirmReset) ConfirmationDialog("Reset your companion?", "This restores the default preview. Save when you're ready to apply it.",
        "Reset preview", model::reset, model::cancelReset)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MascotStudio(state: MascotStudioState, onChange: (MascotSettings) -> Unit, onSave: () -> Unit,
    onDiscard: () -> Unit, onReset: () -> Unit, onRetry: () -> Unit) {
    val draft = state.draft
    if (draft == null) {
        if (state.error != null) ErrorState(state.error, onRetry) else LoadingState("Opening Mascot Studio")
        return
    }
    FocusBuddyTheme(draft.theme) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Mascot Studio", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                Text("A little companion, made yours. Preview changes here before saving.")
                Text("Choose your companion", style = MaterialTheme.typography.titleLarge)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                val tileWidth = ((maxWidth - 12.dp) / 2).coerceAtMost(200.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MascotVariant.entries.forEach { variant ->
                        Surface(Modifier.width(tileWidth).testTag("mascot-${variant.id}")
                            .selectable(draft.selectedMascot == variant.id, enabled = state.loaded && !state.busy,
                                role = Role.RadioButton, onClick = { onChange(draft.copy(selectedMascot = variant.id)) }),
                            shape = MaterialTheme.shapes.large,
                            color = if (draft.selectedMascot == variant.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                MascotArtwork(Modifier.size(96.dp), settings = draft.copy(selectedMascot = variant.id))
                                Text(variant.label, style = MaterialTheme.typography.titleMedium)
                                Text(variant.detail, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                }
                Text("Accent color", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val palette = listOf(0xFFD9C8FAL to "Lavender", 0xFFB8C9F0L to "Periwinkle", 0xFFACD8C5L to "Sage", 0xFFF3BDA8L to "Peach")
                    palette.forEach { (color, name) ->
                        FilterChip(draft.selectedColor == color, { onChange(draft.copy(selectedColor = color, supportedColors = draft.supportedColors + color)) },
                            label = { Text(name) }, enabled = state.loaded && !state.busy,
                            leadingIcon = { Surface(Modifier.size(16.dp), color = Color(color), shape = MaterialTheme.shapes.small) {} })
                    }
                }
                Text("Personality", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MascotPersonality.entries.forEach { value -> FilterChip(draft.personality == value,
                        { onChange(draft.copy(personality = value)) }, label = { Text(value.name) }, enabled = state.loaded && !state.busy) }
                }
                Text("Appearance · all companions support every theme", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { value -> FilterChip(draft.theme == value, { onChange(draft.copy(theme = value)) },
                        label = { Text(value.name) }, enabled = state.loaded && !state.busy) }
                }
                StudioSwitch("Gentle animations", "Also follows Android's reduced-motion setting.", draft.animationsEnabled, state.loaded && !state.busy) { onChange(draft.copy(animationsEnabled = it)) }
                StudioSwitch("Companion sounds", "A short sound after a win or when you tap Preview sound. Reminder alerts keep their own sound settings.", draft.soundEnabled, state.loaded && !state.busy) { onChange(draft.copy(soundEnabled = it)) }
                StudioSwitch("Show companion in Today and reminders", "Your Studio gallery stays available.", draft.showMascot, state.loaded && !state.busy) { onChange(draft.copy(showMascot = it)) }
                if (draft.soundEnabled) {
                    val sound = rememberCompanionSound()
                    SecondaryButton("Preview companion sound", { sound() }, enabled = !state.busy)
                }
                MascotReminderPreview(draft, "Your next intention", "Reminder preview · ${com.focusbuddy.domain.time.timeFormatter(draft.timeFormat).format(java.time.LocalTime.of(9, 0))}")
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onRetry) { Text("Retry loading") } }
                if (state.saved) Text("Companion saved", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                PrimaryButton(if (state.busy) "Saving…" else "Save companion", onSave, Modifier.fillMaxWidth(), state.loaded && state.dirty && !state.busy)
                SecondaryButton("Discard preview changes", onDiscard, Modifier.fillMaxWidth(), state.dirty && !state.busy)
                TextButton(onReset, enabled = state.loaded && !state.busy) { Text("Reset to defaults") }
            }
        }
    }
}

@Composable
private fun StudioSwitch(title: String, detail: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { Text(title); Text(detail, style = MaterialTheme.typography.bodySmall) }
        Switch(checked, onChange, enabled = enabled, modifier = Modifier.semantics { contentDescription = title })
    }
}

/** Same artwork and template inputs used on the real reminder screen; preview never schedules alerts. */
@Composable
fun MascotReminderPreview(settings: MascotSettings, title: String, scheduledTime: String) {
    BuddyCard(Modifier.testTag("mascot-reminder-preview")) {
        if (settings.showMascot) key(settings.selectedMascot, settings.animationsEnabled) {
            MascotArtwork(Modifier.size(104.dp), "Reminder companion", settings, MascotMoment.UpcomingReminder, animateEntrance = true)
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(scheduledTime)
        Text(mascotMessage(settings.personality, MascotMoment.UpcomingReminder))
        Text("Complete · Snooze · Reschedule", style = MaterialTheme.typography.labelLarge)
    }
}
