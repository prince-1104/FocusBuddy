package com.focusbuddy.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusbuddy.domain.calendar.*
import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.repository.*
import com.focusbuddy.domain.time.MomentSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*
import java.time.temporal.WeekFields
import java.util.Locale

data class ExploreState(
    val selected: LocalDate = LocalDate.now(), val today: LocalDate = selected,
    val firstDay: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek,
    val followsLocale: Boolean = true, val snapshot: CalendarSnapshot? = null,
    val loading: Boolean = true, val error: String? = null,
    val detailKey: String? = null, val actionError: String? = null, val disabling: Boolean = false,
) {
    val month: YearMonth get() = YearMonth.from(selected)
    val tasks get() = snapshot?.tasks.orEmpty().filter { it.date == selected }
    val alerts get() = snapshot?.alerts.orEmpty().filter { it.trigger.atZone(snapshot!!.zone).toLocalDate() == selected }
    val completed get() = tasks.count { it.task.completed }
}
fun AlertOccurrence.key() = identity

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModel(
    selectedDates: StateFlow<LocalDate>, private val calendar: CalendarRepository,
    private val preferences: PreferencesRepository, private val reminders: ReminderRepository,
    moments: MomentSource,
) : ViewModel() {
    private val retry = MutableStateFlow(0)
    private val detail = MutableStateFlow<String?>(null)
    private val actionError = MutableStateFlow<String?>(null)
    private val disabling = MutableStateFlow(false)
    private val data = retry.flatMapLatest {
        selectedDates.map(YearMonth::from).distinctUntilChanged().flatMapLatest { month ->
            calendar.observeSnapshot(month).map { (it as CalendarSnapshot?) to null as String? }
                .onStart { emit(null to null) }
                .catch { emit(null to "Your calendar couldn't be loaded. Check recurrence rules and try again.") }
        }
    }
    private val base = retry.flatMapLatest { combine(selectedDates, data, preferences.settings, moments.moments) { selected, data, settings, now ->
        // Never show last month's counts beneath a newly selected month.
        val snapshot = data.first?.takeIf { it.days.firstOrNull()?.date?.let(YearMonth::from) == YearMonth.from(selected) }
        ExploreState(selected, now.toLocalDate(), settings.weekStart ?: WeekFields.of(Locale.getDefault()).firstDayOfWeek,
            settings.weekStart == null, snapshot, snapshot == null && data.second == null, data.second)
    }.catch { emit(ExploreState(selected = selectedDates.value, loading = false, error = "Calendar preferences couldn't be loaded. Please try again.")) } }
    val uiState = combine(base, detail, actionError, disabling) { state, key, error, busy ->
        state.copy(detailKey = key, actionError = error, disabling = busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExploreState(selectedDates.value))
    fun retry() { retry.value++ }
    fun openReminder(key: String) { detail.value = key; actionError.value = null }
    fun closeReminder() { if (!disabling.value) detail.value = null }
    fun setWeekStart(day: DayOfWeek?) = viewModelScope.launch {
        try { preferences.setWeekStart(day) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { actionError.value = "Week start couldn't be saved. Please try again." }
    }
    fun dismissError() { actionError.value = null }
    fun disableReminder() {
        if (disabling.value) return
        val reminder = uiState.value.snapshot?.alerts?.firstOrNull { it.key() == detail.value }?.reminder ?: return
        disabling.value = true
        viewModelScope.launch {
            try { reminders.disable(reminder.id); detail.value = null }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { actionError.value = "This reminder couldn't be disabled. Please try again." }
            finally { disabling.value = false }
        }
    }
}
