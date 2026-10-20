package com.focusbuddy.ui

import androidx.lifecycle.viewModelScope
import com.focusbuddy.domain.calendar.*
import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.repository.*
import com.focusbuddy.domain.time.MomentSource
import com.focusbuddy.ui.explore.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.time.*
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val date = LocalDate.of(2026, 3, 8)
    private val selected = MutableStateFlow(date)
    private val settings = MutableStateFlow(MascotSettings())
    private val now = MutableStateFlow(date.atTime(12, 0).atZone(ZoneOffset.UTC))
    private val models = mutableListOf<ExploreViewModel>()
    private val prefs = object : PreferencesRepository {
        override val settings = this@ExploreViewModelTest.settings
        override suspend fun setSettings(settings: MascotSettings) { this@ExploreViewModelTest.settings.value = settings }
        override suspend fun setWeekStart(day: java.time.DayOfWeek?) { settings.value = settings.value.copy(weekStart = day) }
        override suspend fun setTheme(mode: ThemeMode) { settings.value = settings.value.copy(theme = mode) }
        override suspend fun setMascotVisible(visible: Boolean) {}
        override suspend fun setAnimationsEnabled(enabled: Boolean) {}
    }
    private val alert = Reminder(title = "Remember", triggerAt = now.value.toInstant())
    private val snapshots = MutableStateFlow(calendarSnapshot(emptyList(), listOf(alert), emptyList(), YearMonth.from(date), ZoneOffset.UTC))
    private var fail = false
    private val calendar = object : CalendarRepository {
        override fun observeSnapshot(month: YearMonth): Flow<CalendarSnapshot> = flow {
            if (fail) error("read failed")
            emitAll(snapshots.map { snapshot -> if (YearMonth.from(snapshot.days.first().date) == month) snapshot else calendarSnapshot(emptyList(), emptyList(), emptyList(), month, ZoneOffset.UTC) })
        }
        override fun observeMonth(month: YearMonth) = observeSnapshot(month).map { it.days }
    }
    private val reminders = object : ReminderRepository {
        override suspend fun create(reminder: Reminder) {}
        override suspend fun update(reminder: Reminder) {}
        override suspend fun get(id: UUID): Reminder? = alert.takeIf { it.id == id }
        override fun observeAll(): Flow<List<Reminder>> = flowOf(listOf(alert))
        override fun observeEnabled() = observeAll()
        override suspend fun snooze(id: UUID, until: Instant) {}
        override suspend fun disable(id: UUID) { snapshots.value = calendarSnapshot(emptyList(), emptyList(), emptyList(), YearMonth.from(date), ZoneOffset.UTC) }
        override suspend fun delete(id: UUID) {}
    }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() {
        runTest(dispatcher) { models.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } }
        Dispatchers.resetMain()
    }
    private fun TestScope.model(): ExploreViewModel {
        val vm = ExploreViewModel(selected, calendar, prefs, reminders, object : MomentSource { override val moments = now })
        models += vm
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        return vm
    }
    @Test fun selectionAndWeekPreferenceRefreshWithoutStaleMonthCounts() = runTest(dispatcher) {
        val vm = model()
        assertEquals(1, vm.uiState.first { !it.loading }.alerts.size)
        vm.setWeekStart(DayOfWeek.SATURDAY)
        assertEquals(DayOfWeek.SATURDAY, vm.uiState.first { !it.followsLocale }.firstDay)
        selected.value = date.plusMonths(1)
        val april = vm.uiState.first { !it.loading && it.selected.monthValue == 4 }
        assertTrue(april.tasks.isEmpty()); assertTrue(april.alerts.isEmpty())
        assertEquals(4, april.snapshot!!.days.first().date.monthValue)
        selected.value = date
        assertEquals(1, vm.uiState.first { !it.loading && it.selected == date }.alerts.size)
    }
    @Test fun reminderDetailsDisableUpdatesCountsAndClosesSheet() = runTest(dispatcher) {
        val vm = model()
        val state = vm.uiState.first { !it.loading }
        vm.openReminder(state.alerts.single().key())
        vm.uiState.first { it.detailKey != null }
        vm.disableReminder()
        val disabled = vm.uiState.first { !it.loading && it.alerts.isEmpty() && !it.disabling }
        assertNull(disabled.detailKey)
        assertEquals(0, disabled.snapshot!!.days.sumOf { it.reminderAlertCount })
    }
    @Test fun errorRetryAndMidnightRefresh() = runTest(dispatcher) {
        fail = true
        val vm = model()
        assertNotNull(vm.uiState.first { !it.loading }.error)
        fail = false; vm.retry()
        assertNull(vm.uiState.first { !it.loading && it.snapshot != null }.error)
        now.value = now.value.plusDays(1)
        assertEquals(date.plusDays(1), vm.uiState.first { it.today != date }.today)
    }
}
