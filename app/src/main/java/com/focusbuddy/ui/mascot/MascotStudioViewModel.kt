package com.focusbuddy.ui.mascot

import android.os.Bundle
import androidx.lifecycle.*
import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.repository.PreferencesRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class MascotStudioState(val draft: MascotSettings? = null, val loaded: Boolean = false,
    val busy: Boolean = false, val dirty: Boolean = false, val error: String? = null,
    val saved: Boolean = false, val confirmReset: Boolean = false)

class MascotStudioViewModel(private val repository: PreferencesRepository,
    private val handle: SavedStateHandle) : ViewModel() {
    private val restored = handle.get<Bundle>("mascotDraft")?.decode()
    private val mutable = MutableStateFlow(MascotStudioState(draft = restored, dirty = restored != null))
    val state = mutable.asStateFlow()
    private var reading: Job? = null
    private var saving: Job? = null
    fun cancelPendingSave() { saving?.cancel(); mutable.update { it.copy(busy = false) } }
    init { retry() }
    fun retry() {
        reading?.cancel()
        reading = viewModelScope.launch {
            repository.settings.catch { mutable.update { it.copy(error = "Your companion settings couldn't be loaded. Please retry.") } }
                .collect { saved -> mutable.update { it.copy(loaded = true, draft = if (it.dirty) it.draft?.copy(
                    weekStart = saved.weekStart, timeFormat = saved.timeFormat, defaultReminderOffset = saved.defaultReminderOffset,
                    defaultAlertSound = saved.defaultAlertSound, defaultAlertVibration = saved.defaultAlertVibration) else saved, error = null) } }
        }
    }
    fun change(value: MascotSettings) {
        if (state.value.busy || !state.value.loaded) return
        handle["mascotDraft"] = value.encode()
        mutable.update { it.copy(draft = value, dirty = true, saved = false, error = null) }
    }
    fun requestReset() { if (!state.value.busy) mutable.update { it.copy(confirmReset = true) } }
    fun cancelReset() { mutable.update { it.copy(confirmReset = false) } }
    fun reset() { change(MascotSettings()); cancelReset() }
    fun discard() {
        if (state.value.busy) return
        handle.remove<Bundle>("mascotDraft")
        mutable.update { it.copy(dirty = false, saved = false) }; retry()
    }
    fun save() {
        val current = state.value
        if (current.busy || !current.loaded || !current.dirty) return
        val value = current.draft ?: return
        mutable.update { it.copy(busy = true, error = null) }
        saving = viewModelScope.launch {
            try {
                repository.setMascotSettings(value)
                handle.remove<Bundle>("mascotDraft")
                mutable.update { it.copy(busy = false, dirty = false, saved = true) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(busy = false, error = "Your companion couldn't be saved. Your preview is kept; try again.") } }
        }
    }
}

private fun MascotSettings.encode() = Bundle().apply {
    putString("variant", selectedMascot); putString("personality", personality.name)
    putLongArray("colors", supportedColors.toLongArray()); putLong("color", selectedColor)
    putString("theme", theme.name); putBoolean("motion", animationsEnabled)
    putBoolean("sound", soundEnabled); putBoolean("visible", showMascot)
}
private fun Bundle.decode(): MascotSettings? = runCatching { MascotSettings(
    selectedMascot = getString("variant") ?: "bunny",
    personality = MascotPersonality.valueOf(getString("personality") ?: "Calm"),
    supportedColors = getLongArray("colors")!!.toSet(), selectedColor = getLong("color"),
    theme = ThemeMode.valueOf(getString("theme") ?: "System"),
    animationsEnabled = getBoolean("motion"), soundEnabled = getBoolean("sound"), showMascot = getBoolean("visible")) }.getOrNull()
