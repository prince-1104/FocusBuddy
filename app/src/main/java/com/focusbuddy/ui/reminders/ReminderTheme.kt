package com.focusbuddy.ui.reminders

import androidx.compose.runtime.*
import androidx.activity.enableEdgeToEdge
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
    val activity = androidx.activity.compose.LocalActivity.current as? androidx.activity.ComponentActivity
    val dark = settings.theme == com.focusbuddy.domain.model.ThemeMode.Dark ||
        (settings.theme == com.focusbuddy.domain.model.ThemeMode.System && androidx.compose.foundation.isSystemInDarkTheme())
    SideEffect {
        activity?.let {
            val style = if (dark) androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            it.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        }
    }
    FocusBuddyTheme(settings.theme) { CompositionLocalProvider(LocalReminderMascot provides settings.showMascot,
        com.focusbuddy.ui.components.LocalMascotSettings provides settings, content = content) }
}
