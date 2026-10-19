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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusbuddy.ui.components.DestinationIcon
import com.focusbuddy.ui.screens.*
import com.focusbuddy.domain.model.ThemeMode
import java.time.LocalDate

@Composable
fun FocusBuddyApp(appViewModel: AppViewModel) {
    val selectedName by appViewModel.destination.collectAsStateWithLifecycle()
    val theme by appViewModel.theme.collectAsStateWithLifecycle()
    val destination = Destination.entries.firstOrNull { it.name == selectedName } ?: Destination.Today
    BackHandler(enabled = destination != Destination.Today) { appViewModel.select(Destination.Today) }
    FocusBuddyContent(destination, appViewModel::select, theme, appViewModel::setTheme)
}

/** Stateless navigation shell; previews never construct a ViewModel or read preferences. */
@Composable
fun FocusBuddyContent(
    destination: Destination,
    onNavigate: (Destination) -> Unit,
    theme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    todayState: TodayUiState = TodayUiState(),
) {
    val savedState = rememberSaveableStateHolder()
    var exploreDate by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp
        Scaffold(
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
                    animationSpec = tween(160), label = "Destination") { current ->
                    savedState.SaveableStateProvider(current.name) {
                        when (current) {
                            Destination.Today -> TodayScreen(todayState, { date ->
                                exploreDate = date.toEpochDay()
                                onNavigate(Destination.Explore)
                            }, {})
                            Destination.Explore -> ExploreScreen(initialDate = LocalDate.ofEpochDay(exploreDate))
                            Destination.Focus -> FocusScreen()
                            Destination.Mascot -> MascotScreen()
                            Destination.Profile -> ProfileScreen(theme, onThemeChange)
                        }
                    }
                }
            }
        }
    }
}
