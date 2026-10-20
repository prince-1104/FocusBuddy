package com.focusbuddy.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusbuddy.ui.components.DestinationIcon
import com.focusbuddy.ui.screens.*
import com.focusbuddy.domain.model.ThemeMode
import com.focusbuddy.ui.today.TodayRoute
import com.focusbuddy.ui.today.TodayViewModel

@Composable
fun FocusBuddyApp(appViewModel: AppViewModel, todayViewModel: TodayViewModel, exploreViewModel: com.focusbuddy.ui.explore.ExploreViewModel) {
    val selectedName by appViewModel.destination.collectAsStateWithLifecycle()
    val uiState by appViewModel.uiState.collectAsStateWithLifecycle()
    val destination = Destination.entries.firstOrNull { it.name == selectedName } ?: Destination.Today
    BackHandler(enabled = destination != Destination.Today) { appViewModel.select(Destination.Today) }
    Column(Modifier.statusBarsPadding()) {
    com.focusbuddy.ui.reminders.ReminderSettingsEntry()
    Box(Modifier.weight(1f)) { FocusBuddyContent(destination, appViewModel::select, uiState.theme, appViewModel::setTheme,
        todayContent = { TodayRoute(todayViewModel, appViewModel::selectDate, appViewModel::selectToday, { appViewModel.select(Destination.Explore) }) },
        exploreContent = { com.focusbuddy.ui.explore.ExploreRoute(exploreViewModel, todayViewModel, appViewModel::selectDate, appViewModel::selectToday) },
        showMascot = uiState.showMascot, onMascotVisibilityChange = appViewModel::setMascotVisible,
        animationsEnabled = uiState.animationsEnabled, onAnimationsChange = appViewModel::setAnimationsEnabled,
        settingsError = uiState.preferencesError, onRetryPreferences = appViewModel::retryFailedPreference) }
    }
}

/** Stateless navigation shell; previews never construct a ViewModel or read preferences. */
@Composable
fun FocusBuddyContent(
    destination: Destination,
    onNavigate: (Destination) -> Unit,
    theme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    todayContent: @Composable () -> Unit,
    exploreContent: @Composable () -> Unit,
    showMascot: Boolean = true,
    onMascotVisibilityChange: (Boolean) -> Unit = {},
    animationsEnabled: Boolean = true,
    onAnimationsChange: (Boolean) -> Unit = {},
    settingsError: String? = null,
    onRetryPreferences: () -> Unit = {},
) {
    val savedState = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(settingsError) {
        if (settingsError != null && snackbar.showSnackbar(settingsError, "Retry") == SnackbarResult.ActionPerformed) {
            onRetryPreferences()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 2.dp) {
                        Destination.entries.forEach { item ->
                            NavigationBarItem(
                                selected = destination == item, onClick = { onNavigate(item) },
                                icon = { DestinationIcon(item, Modifier.size(24.dp)) },
                                label = { Text(item.name) },
                            )
                        }
                    }
                }
            },
        ) { insets ->
            Row(Modifier.fillMaxSize().padding(insets)) {
                if (useRail) {
                    NavigationRail(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Spacer(Modifier.height(24.dp))
                        Destination.entries.forEach { item ->
                            NavigationRailItem(
                                selected = destination == item, onClick = { onNavigate(item) },
                                icon = { DestinationIcon(item, Modifier.size(24.dp)) },
                                label = { Text(item.name) },
                            )
                        }
                    }
                }
                Crossfade(destination, modifier = Modifier.weight(1f).fillMaxHeight(),
                    animationSpec = tween(if (animationsEnabled) 160 else 0), label = "Destination") { current ->
                    savedState.SaveableStateProvider(current.name) {
                        when (current) {
                            Destination.Today -> todayContent()
                            Destination.Explore -> exploreContent()
                            Destination.Focus -> FocusScreen()
                            Destination.Mascot -> MascotScreen()
                            Destination.Profile -> ProfileScreen(theme, onThemeChange, showMascot, onMascotVisibilityChange,
                                animationsEnabled, onAnimationsChange)
                        }
                    }
                }
            }
        }
    }
}
