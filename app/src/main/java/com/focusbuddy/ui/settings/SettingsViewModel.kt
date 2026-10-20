package com.focusbuddy.ui.settings

import androidx.lifecycle.*
import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.repository.PreferencesRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class SettingsState(val settings: MascotSettings? = null, val error: String? = null)
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(private val preferences: PreferencesRepository, private val clearData: suspend () -> Unit) : ViewModel() {
    private val retry = MutableStateFlow(0)
    val busy = MutableStateFlow(false)
    val clearing = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val confirmClear = MutableStateFlow(false)
    val state = retry.flatMapLatest { preferences.settings.map { SettingsState(it) }
        .catch { emit(SettingsState(error = "Your settings couldn't be loaded. Please retry.")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsState())
    fun retry() { retry.value++ }
    fun update(value: MascotSettings) = write {
        preferences.setProductivity(value.timeFormat, value.defaultReminderOffset, value.defaultAlertSound, value.defaultAlertVibration)
    }
    fun theme(value: ThemeMode) = write { preferences.setTheme(value) }
    fun weekStart(value: java.time.DayOfWeek?) = write { preferences.setWeekStart(value) }
    fun animations(value: Boolean) = write { preferences.setAnimationsEnabled(value) }
    fun visible(value: Boolean) = write { preferences.setMascotVisible(value) }
    private fun write(operation: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true; message.value = null
        viewModelScope.launch {
            try { operation() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { message.value = "Your settings couldn't be saved. Please try again." }
            finally { busy.value = false; clearing.value = false }
        }
    }
    fun clear(before: () -> Unit, after: () -> Unit) {
        if (busy.value) return
        confirmClear.value = false
        clearing.value = true
        write {
            before()
            clearData()
            after()
        }
    }
}
