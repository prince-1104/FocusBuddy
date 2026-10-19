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
import com.focusbuddy.data.preferences.ThemePreferences
import com.focusbuddy.ui.AppViewModel
import com.focusbuddy.ui.FocusBuddyApp
import com.focusbuddy.ui.theme.FocusBuddyTheme

class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels {
        viewModelFactory {
            initializer { AppViewModel(createSavedStateHandle(), ThemePreferences(applicationContext)) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by appViewModel.theme.collectAsStateWithLifecycle()
            val dark = theme == ThemeMode.Dark || (theme == ThemeMode.System && isSystemInDarkTheme())
            SideEffect {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            FocusBuddyTheme(theme) { FocusBuddyApp(appViewModel) }
        }
    }
}
