package com.focusbuddy.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    illustration: @Composable () -> Unit = { MascotArtwork(Modifier.size(96.dp)) },
) {
    Column(modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) { illustration() }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun LoadingState(label: String = "Loading your space", modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator(Modifier.size(32.dp).semantics { contentDescription = label })
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    BuddyCard(modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text("Something needs a moment", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error)
        Text(message, style = MaterialTheme.typography.bodyMedium)
        SecondaryButton("Try again", onRetry)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuddyBottomSheet(title: String, onDismiss: () -> Unit, canDismiss: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val dismissAllowed by rememberUpdatedState(canDismiss)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { dismissAllowed || it != SheetValue.Hidden })
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().verticalScrollForSheet().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
private fun Modifier.verticalScrollForSheet(): Modifier =
    this.then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))

@Composable
fun ConfirmationDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
