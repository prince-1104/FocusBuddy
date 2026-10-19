package com.focusbuddy.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.focusbuddy.ui.components.*

@Composable
fun MascotScreen() {
    ScreenPage("Your companion", "A softer side to getting things done.") {
        MascotContainer("Meet your little cheerleader.", "A first look at the companion who will share your everyday wins.")
        SectionTitle("Make it feel like you")
        BuddyCard {
            Text("A world of little details", style = MaterialTheme.typography.titleMedium)
            Text("Colors, accessories, and a name of their own. Companion customization is coming soon.",
                style = MaterialTheme.typography.bodyLarge)
            SecondaryButton("Customize · coming soon", {}, Modifier.fillMaxWidth(), enabled = false)
        }
        ComingNext("Good company, without the pressure. Your companion will never ask you to keep a perfect streak.")
    }
}
