package com.focusbuddy.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.focusbuddy.ui.components.*
import com.focusbuddy.domain.model.ThemeMode

@Composable
fun ProfileScreen(theme: ThemeMode, onThemeChange: (ThemeMode) -> Unit,
    showMascot: Boolean = true, onMascotVisibilityChange: (Boolean) -> Unit = {},
    animationsEnabled: Boolean = true, onAnimationsChange: (Boolean) -> Unit = {}) {
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showReset by rememberSaveable { mutableStateOf(false) }
    ScreenPage("Your space", "Personal by design. Yours to make comfortable.") {
        BuddyCard {
            Text("Just you, and what matters", style = MaterialTheme.typography.titleLarge)
            Text("No account to manage. Your plans stay on your device.", style = MaterialTheme.typography.bodyLarge)
        }
        SectionTitle("Appearance")
        BuddyCard {
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    val label = if (mode == ThemeMode.System) "Use device theme" else "${mode.name} theme"
                    Row(Modifier.fillMaxWidth().selectable(theme == mode, role = Role.RadioButton,
                        onClick = { onThemeChange(mode) }).heightIn(min = 56.dp).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadioButton(selected = theme == mode, onClick = null)
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            }
            TextButton(onClick = { showReset = true }, enabled = theme != ThemeMode.System) { Text("Reset appearance") }
        }
        SectionTitle("Your companion")
        BuddyCard {
            PreferenceSwitch("Show companion on Today", showMascot, onMascotVisibilityChange)
            PreferenceSwitch("Gentle animations", animationsEnabled, onAnimationsChange)
        }
        SectionTitle("At your own pace")
        BuddyCard {
            Text("Reminders & preferences", style = MaterialTheme.typography.titleMedium)
            ComingNext("Notification preferences will be available when reminders arrive.")
            SecondaryButton("About FocusBuddy", { showAbout = true }, Modifier.fillMaxWidth())
        }
    }
    if (showAbout) {
        BuddyBottomSheet("A little more room to focus", { showAbout = false }) {
            MascotArtwork(Modifier.size(100.dp).align(Alignment.CenterHorizontally))
            Text("FocusBuddy is your personal productivity companion. A quiet space for daily tasks, gentle reminders, and meaningful progress.",
                style = MaterialTheme.typography.bodyLarge)
            Text("Offline first. No login, subscription, or analytics.", style = MaterialTheme.typography.bodyMedium)
            PrimaryButton("Sounds good", { showAbout = false }, Modifier.fillMaxWidth())
        }
    }
    if (showReset) {
        ConfirmationDialog("Use your device theme?", "FocusBuddy will follow your device's light or dark appearance.",
            "Use device theme", { onThemeChange(ThemeMode.System); showReset = false }, { showReset = false })
    }
}

@Composable
private fun PreferenceSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onCheckedChange = null)
    }
}
