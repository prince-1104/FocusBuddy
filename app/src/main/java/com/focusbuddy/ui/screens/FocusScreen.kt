package com.focusbuddy.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.focusbuddy.ui.components.*

@Composable
fun FocusScreen() {
    ScreenPage("Focus", "One thing at a time is enough.") {
        BuddyCard {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("YOUR QUIET MOMENT", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { 0f }, modifier = Modifier.fillMaxSize()
                        .semantics { contentDescription = "Focus session preview; timer not started" },
                        strokeWidth = 6.dp, trackColor = MaterialTheme.colorScheme.primaryContainer)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("25:00", style = MaterialTheme.typography.displaySmall,
                            modifier = Modifier.semantics { contentDescription = "25 minutes, timer preview" })
                        Text("Session preview", style = MaterialTheme.typography.labelMedium)
                    }
                }
                ComingNext("A calm timer for your next meaningful step. Focus sessions are coming soon.")
                PrimaryButton("Start focus · coming soon", {}, Modifier.fillMaxWidth(), enabled = false)
            }
        }
        MascotContainer("We'll find your rhythm.", "Take a breath. Your attention deserves a gentle place to land.")
    }
}
