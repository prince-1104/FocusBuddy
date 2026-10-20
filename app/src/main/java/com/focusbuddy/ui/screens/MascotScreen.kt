package com.focusbuddy.ui.screens

import androidx.compose.runtime.*
import com.focusbuddy.domain.model.MascotSettings
import com.focusbuddy.ui.mascot.*

/** Stateless-shell preview of configuration; production installs MascotStudioRoute. */
@Composable
fun MascotScreen() {
    var settings by remember { mutableStateOf(MascotSettings()) }
    MascotStudio(MascotStudioState(draft = settings, loaded = true), { settings = it }, {}, {}, { settings = MascotSettings() }, {})
}
