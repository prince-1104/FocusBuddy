package com.focusbuddy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF52633F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E7C8),
    onPrimaryContainer = Color(0xFF17230D),
    background = Color(0xFFFAF7F0),
    onBackground = Color(0xFF282D24),
    surface = Color(0xFFFAF7F0),
    onSurface = Color(0xFF282D24),
    secondaryContainer = Color(0xFFE8DDCB),
    onSecondaryContainer = Color(0xFF30291D),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9CE9F),
    onPrimary = Color(0xFF263619),
    primaryContainer = Color(0xFF3B4C2B),
    onPrimaryContainer = Color(0xFFD9E7C8),
    background = Color(0xFF191C17),
    onBackground = Color(0xFFE4E5DB),
    surface = Color(0xFF191C17),
    onSurface = Color(0xFFE4E5DB),
    secondaryContainer = Color(0xFF4C4335),
    onSecondaryContainer = Color(0xFFE8DDCB),
)

@Composable
fun FocusBuddyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
