package com.focusbuddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.SystemBarStyle
import com.focusbuddy.domain.model.ThemeMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.activity.viewModels
import com.focusbuddy.ui.AppViewModel
import com.focusbuddy.ui.FocusBuddyApp
import com.focusbuddy.ui.theme.FocusBuddyTheme
import com.focusbuddy.ui.today.TodayViewModel

class MainActivity : ComponentActivity() {
    private val mascotViewModel: com.focusbuddy.ui.mascot.MascotStudioViewModel by viewModels {
        viewModelFactory { initializer {
            com.focusbuddy.ui.mascot.MascotStudioViewModel((application as FocusBuddyApplication).container.preferences, createSavedStateHandle())
        } }
    }
    private val appViewModel: AppViewModel by viewModels {
        viewModelFactory {
            initializer { AppViewModel(createSavedStateHandle(), (application as FocusBuddyApplication).container.preferences) }
        }
    }
    private val todayViewModel: TodayViewModel by viewModels {
        viewModelFactory {
            initializer {
                val container = (application as FocusBuddyApplication).container
                TodayViewModel(appViewModel.selectedDate, container.observeDashboard, container.tasks, appViewModel::selectDate, reminders = container.reminders)
            }
        }
    }

    private val exploreViewModel: com.focusbuddy.ui.explore.ExploreViewModel by viewModels {
        viewModelFactory { initializer {
            val container = (application as FocusBuddyApplication).container
            com.focusbuddy.ui.explore.ExploreViewModel(appViewModel.selectedDate, container.calendar, container.preferences, container.reminders,
                com.focusbuddy.domain.time.SystemMomentSource(java.time.Clock.systemUTC(), com.focusbuddy.data.time.DeviceTimeZoneSource(application)))
        } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleReminderIntent(intent)
        enableEdgeToEdge()
        setContent {
            val uiState by appViewModel.uiState.collectAsStateWithLifecycle()
            val theme = uiState.theme
            val dark = theme == ThemeMode.Dark || (theme == ThemeMode.System && isSystemInDarkTheme())
            SideEffect {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            FocusBuddyTheme(theme) { FocusBuddyApp(appViewModel, todayViewModel, exploreViewModel, mascotViewModel) }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as FocusBuddyApplication).container.reminderScheduler.requestReconciliation()
    }
    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); setIntent(intent); handleReminderIntent(intent) }
    private fun handleReminderIntent(intent: android.content.Intent) {
        fun id(name: String) = intent.getStringExtra(name)?.let { runCatching { java.util.UUID.fromString(it).toString() }.getOrNull() }
        val open = id("openTask"); val edit = id("editTask"); val reminder = id("editReminder")
        if (open == null && edit == null && reminder == null) return
        appViewModel.select(com.focusbuddy.ui.Destination.Today)
        intent.getStringExtra("reminderDate")?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull()?.let(appViewModel::selectDate) }
        when { open != null -> todayViewModel.openTask(open); edit != null -> todayViewModel.editTask(edit); reminder != null -> todayViewModel.editReminder(reminder) }
        listOf("openTask", "editTask", "editReminder", "reminderDate").forEach(intent::removeExtra)
    }
}
