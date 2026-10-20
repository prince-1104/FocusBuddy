package com.focusbuddy.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.repository.PreferencesRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = com.focusbuddy.TestApplication::class)
class AppViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = mutableListOf<AppViewModel>()
    @Before fun prepare() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { viewModels.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain() }

    private fun viewModel(repository: PreferencesRepository) = AppViewModel(SavedStateHandle(), repository)
        .also { viewModels += it }

    @Test fun observesStoredThemeAndExposesImmutableLoadedState() = runTest(dispatcher) {
        val repository = TestPreferences()
        repository.state.value = MascotSettings(theme = ThemeMode.Dark)
        val model = viewModel(repository)
        runCurrent()
        assertEquals(AppUiState(mascot = MascotSettings(theme = ThemeMode.Dark), theme = ThemeMode.Dark, preferencesLoaded = true), model.uiState.value)
        model.setTheme(ThemeMode.Light)
        runCurrent()
        assertEquals(ThemeMode.Light, model.uiState.value.theme)
    }

    @Test fun failedReadCanBeRetriedWithoutReplacingStoredPreferences() = runTest(dispatcher) {
        val repository = TestPreferences().also { it.failRead = true; it.state.value = MascotSettings(theme = ThemeMode.Dark) }
        val model = viewModel(repository)
        runCurrent()
        assertNotNull(model.uiState.value.preferencesError)
        assertFalse(model.uiState.value.preferencesLoaded)
        model.retryFailedPreference()
        runCurrent()
        assertEquals(ThemeMode.Dark, model.uiState.value.theme)
        assertNull(model.uiState.value.preferencesError)
    }

    @Test fun failedWriteDoesNotPretendToSaveAndRetryWritesRequestedValue() = runTest(dispatcher) {
        val repository = TestPreferences()
        val model = viewModel(repository)
        runCurrent()
        repository.failWrite = true
        model.setTheme(ThemeMode.Dark)
        runCurrent()
        assertEquals(ThemeMode.System, model.uiState.value.theme)
        assertNotNull(model.uiState.value.preferencesError)
        repository.failWrite = false
        model.retryFailedPreference()
        runCurrent()
        assertEquals(ThemeMode.Dark, model.uiState.value.theme)
        assertNull(model.uiState.value.preferencesError)
    }

    @Test fun selectedDateIsSharedRestoredAndCanReturnToToday() = runTest(dispatcher) {
        val saved = SavedStateHandle(mapOf("selectedDate" to "2026-11-05"))
        val model = AppViewModel(saved, TestPreferences(), Clock.fixed(Instant.parse("2026-10-19T20:00:00Z"), ZoneOffset.UTC), { ZoneId.of("Asia/Kolkata") })
            .also { viewModels += it }
        runCurrent()
        assertEquals(LocalDate.of(2026, 11, 5), model.selectedDate.value)
        model.selectDate(LocalDate.of(2026, 12, 1)); runCurrent()
        assertEquals("2026-12-01", saved.get<String>("selectedDate"))
        model.selectToday(); runCurrent()
        assertEquals(LocalDate.of(2026, 10, 20), model.selectedDate.value)
    }

    /** Failure injection is test-only; production always uses DataStore. */
    private class TestPreferences : PreferencesRepository {
        val state = MutableStateFlow(MascotSettings())
        var failRead = false
        var failWrite = false
        override val settings = flow {
            if (failRead) { failRead = false; throw IOException("test read failure") }
            emitAll(state)
        }
        override suspend fun setWeekStart(day: java.time.DayOfWeek?) { state.value = state.value.copy(weekStart = day) }
        override suspend fun setTheme(mode: ThemeMode) {
            if (failWrite) throw IOException("test write failure")
            state.value = state.value.copy(theme = mode)
        }
        override suspend fun setSettings(settings: MascotSettings) { state.value = settings }
        override suspend fun setMascotVisible(visible: Boolean) { state.value = state.value.copy(showMascot = visible) }
        override suspend fun setAnimationsEnabled(enabled: Boolean) { state.value = state.value.copy(animationsEnabled = enabled) }
    }
}
