package com.focusbuddy.ui.preview

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.focusbuddy.domain.mascot.*
import com.focusbuddy.domain.model.*
import com.focusbuddy.ui.components.*
import com.focusbuddy.ui.mascot.*
import com.focusbuddy.ui.theme.FocusBuddyTheme

@Preview(name = "Original companions", widthDp = 360, showBackground = true)
@Composable
fun MascotGalleryPreview() {
    FocusBuddyTheme(ThemeMode.Light) {
        Column(Modifier.padding(20.dp)) {
            MascotVariant.entries.forEach {
                MascotArtwork(Modifier.size(104.dp), it.label, MascotSettings(selectedMascot = it.id), MascotMoment.TaskCompleted)
            }
        }
    }
}
@Preview(name = "Studio · compact large text", widthDp = 320, heightDp = 900, fontScale = 1.4f)
@Composable
fun MascotStudioPreview() {
    MascotStudio(MascotStudioState(draft = MascotSettings(animationsEnabled = false), loaded = true), {}, {}, {}, {}, {})
}
@Preview(name = "Reminder · dark", widthDp = 360, showBackground = true)
@Composable
fun MascotReminderPreviewDark() {
    val settings = MascotSettings(selectedMascot = "owl", theme = ThemeMode.Dark, personality = MascotPersonality.Focused)
    FocusBuddyTheme(settings.theme) { MascotReminderPreview(settings, "A moment to read", "Today · 9:00 AM") }
}
