package com.focusbuddy.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.focusbuddy.data.preferences.ThemePreferences
import com.focusbuddy.domain.model.ThemeMode

enum class Destination { Today, Explore, Focus, Mascot, Profile }

class AppViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val themePreferences: ThemePreferences,
) : ViewModel() {
    val destination: StateFlow<String> = savedStateHandle.getStateFlow(
        "destination", Destination.Today.name,
    )
    private val mutableTheme = MutableStateFlow(themePreferences.read())
    val theme: StateFlow<ThemeMode> = mutableTheme.asStateFlow()

    fun setTheme(mode: ThemeMode) {
        themePreferences.write(mode)
        mutableTheme.value = mode
    }

    fun select(destination: Destination) {
        savedStateHandle["destination"] = destination.name
    }
}
