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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusbuddy.ui.components.DestinationIcon
import com.focusbuddy.ui.screens.*
import com.focusbuddy.domain.model.ThemeMode
import com.focusbuddy.ui.today.TodayRoute
import com.focusbuddy.ui.today.TodayViewModel

@Composable
fun FocusBuddyApp(appViewModel: AppViewModel, todayViewModel: TodayViewModel, exploreViewModel: com.focusbuddy.ui.explore.ExploreViewModel,
    mascotViewModel: com.focusbuddy.ui.mascot.MascotStudioViewModel,
    focusViewModel: com.focusbuddy.ui.focus.FocusViewModel,
    settingsViewModel: com.focusbuddy.ui.settings.SettingsViewModel,
    discoveryViewModel: com.focusbuddy.ui.discovery.TaskDiscoveryViewModel,
    beforeClear: () -> Unit, afterClear: () -> Unit) {
    val selectedName by appViewModel.destination.collectAsStateWithLifecycle()
    val uiState by appViewModel.uiState.collectAsStateWithLifecycle()
    val destination = Destination.entries.firstOrNull { it.name == selectedName } ?: Destination.Today
    var discoveryOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val settingsBusy by settingsViewModel.busy.collectAsStateWithLifecycle()
    val navigate: (Destination) -> Unit = { if (!settingsBusy) appViewModel.select(it) }
    BackHandler(discoveryOpen && destination == Destination.Today) { discoveryOpen = false }
    BackHandler(enabled = destination != Destination.Today) { appViewModel.select(Destination.Today) }
    BackHandler(enabled = settingsBusy) { /* Finish the in-flight settings/reset operation before navigating. */ }
    CompositionLocalProvider(com.focusbuddy.ui.components.LocalMascotSettings provides uiState.mascot) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(Modifier.statusBarsPadding()) {
    Box(Modifier.weight(1f)) { FocusBuddyContent(destination, navigate, uiState.theme, appViewModel::setTheme,
        todayContent = {
            if (discoveryOpen) com.focusbuddy.ui.discovery.TaskDiscoveryRoute(discoveryViewModel, { discoveryOpen = false },
                { discoveryOpen = false; todayViewModel.openTask(it) }, { discoveryOpen = false; todayViewModel.createTask() })
            else TodayRoute(todayViewModel, appViewModel::selectDate, appViewModel::selectToday, { appViewModel.select(Destination.Explore) }, { discoveryOpen = true })
        },
        exploreContent = { com.focusbuddy.ui.explore.ExploreRoute(exploreViewModel, todayViewModel, appViewModel::selectDate, appViewModel::selectToday) },
        mascotContent = { com.focusbuddy.ui.mascot.MascotStudioRoute(mascotViewModel) },
        focusContent = { com.focusbuddy.ui.focus.FocusRoute(focusViewModel) },
        profileContent = { com.focusbuddy.ui.settings.SettingsRoute(settingsViewModel, { navigate(Destination.Mascot) }, beforeClear, afterClear) },
        showMascot = uiState.showMascot, onMascotVisibilityChange = appViewModel::setMascotVisible,
        animationsEnabled = uiState.animationsEnabled, onAnimationsChange = appViewModel::setAnimationsEnabled,
        settingsError = uiState.preferencesError, onRetryPreferences = appViewModel::retryFailedPreference) }
    }
    }
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
    mascotContent: @Composable () -> Unit = { MascotScreen() },
    focusContent: @Composable () -> Unit = { FocusScreen() },
    profileContent: (@Composable () -> Unit)? = null,
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
                                label = { Text(item.name, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                                label = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                            Destination.Focus -> focusContent()
                            Destination.Mascot -> mascotContent()
                            Destination.Profile -> if (profileContent != null) profileContent() else ProfileScreen(theme, onThemeChange, showMascot, onMascotVisibilityChange,
                                animationsEnabled, onAnimationsChange)
                        }
                    }
                }
            }
        }
    }
}
