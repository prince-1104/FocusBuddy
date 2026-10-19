package com.focusbuddy.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.focusbuddy.domain.repository.PreferencesRepository
import com.focusbuddy.domain.model.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

enum class Destination { Today, Explore, Focus, Mascot, Profile }

data class AppUiState(
    val theme: ThemeMode = ThemeMode.System,
    val preferencesLoaded: Boolean = false,
    val preferencesError: String? = null,
)

class AppViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val preferences: PreferencesRepository,
) : ViewModel() {
    val destination: StateFlow<String> = savedStateHandle.getStateFlow(
        "destination", Destination.Today.name,
    )
    private val mutableUiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = mutableUiState.asStateFlow()
    private var preferencesJob: Job? = null
    private var pendingTheme: ThemeMode? = null

    init { retryPreferences() }

    fun retryPreferences() {
        preferencesJob?.cancel()
        preferencesJob = viewModelScope.launch {
            mutableUiState.update { it.copy(preferencesError = null) }
            preferences.settings.catch { failure ->
                if (failure !is IOException) throw failure
                mutableUiState.update { it.copy(preferencesError = "Your preferences couldn't be loaded. Please try again.") }
            }.collect { settings ->
                mutableUiState.update { it.copy(theme = settings.theme, preferencesLoaded = true, preferencesError = null) }
            }
        }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch {
            try {
                preferences.setTheme(mode)
                pendingTheme = null
                mutableUiState.update { it.copy(preferencesError = null) }
                if (preferencesJob?.isActive != true) retryPreferences()
            } catch (_: IOException) {
                pendingTheme = mode
                mutableUiState.update { it.copy(preferencesError = "Your appearance couldn't be saved. Please try again.") }
            }
        }
    }

    fun retryFailedPreference() {
        val requested = pendingTheme
        if (requested != null) setTheme(requested) else retryPreferences()
    }

    fun select(destination: Destination) {
        savedStateHandle["destination"] = destination.name
    }
}
