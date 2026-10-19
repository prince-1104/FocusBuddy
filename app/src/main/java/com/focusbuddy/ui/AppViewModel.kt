package com.focusbuddy.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

enum class Destination { Today, Explore }

class AppViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    val destination: StateFlow<String> = savedStateHandle.getStateFlow(
        "destination", Destination.Today.name,
    )

    fun select(destination: Destination) {
        savedStateHandle["destination"] = destination.name
    }
}
