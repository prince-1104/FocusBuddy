package com.focusbuddy.ui.reminders

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusbuddy.FocusBuddyApplication
import com.focusbuddy.domain.model.MascotSettings
import com.focusbuddy.ui.theme.FocusBuddyTheme
import kotlinx.coroutines.flow.catch

val LocalReminderMascot = staticCompositionLocalOf { true }
@Composable
fun ReminderTheme(content: @Composable () -> Unit) {
    val container = (LocalContext.current.applicationContext as FocusBuddyApplication).container
    val safeDefaults = MascotSettings(showMascot = false, animationsEnabled = false, soundEnabled = false)
    val flow = remember(container) { container.preferences.settings.catch { emit(safeDefaults) } }
    val settings by flow.collectAsStateWithLifecycle(safeDefaults)
    FocusBuddyTheme(settings.theme) { CompositionLocalProvider(LocalReminderMascot provides settings.showMascot,
        com.focusbuddy.ui.components.LocalMascotSettings provides settings, content = content) }
}
