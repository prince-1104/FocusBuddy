package com.focusbuddy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focusbuddy.R

@Composable
fun FocusBuddyApp(appViewModel: AppViewModel = viewModel()) {
    val selected by appViewModel.destination.collectAsStateWithLifecycle()
    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    val label = stringResource(
                        if (destination == Destination.Today) R.string.today else R.string.explore,
                    )
                    NavigationBarItem(
                        selected = selected == destination.name,
                        onClick = { appViewModel.select(destination) },
                        icon = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        val isToday = selected == Destination.Today.name
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(if (isToday) R.string.today else R.string.explore),
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                stringResource(if (isToday) R.string.today_message else R.string.explore_message),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                stringResource(if (isToday) R.string.today_foundation else R.string.explore_foundation),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
