package com.focusbuddy.app

import android.content.Context
import com.focusbuddy.data.local.FocusBuddyDatabase
import com.focusbuddy.data.preferences.DataStorePreferencesRepository
import com.focusbuddy.data.preferences.focusBuddyPreferences
import com.focusbuddy.data.repository.*
import com.focusbuddy.data.time.DeviceTimeZoneSource
import com.focusbuddy.domain.repository.*
import java.time.Clock
import com.focusbuddy.domain.time.SystemMomentSource
import com.focusbuddy.domain.usecase.ObserveDashboard

/** Application-scoped singletons, constructor injection, no service locator inside feature code. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val clock = Clock.systemUTC()
    private val database by lazy { FocusBuddyDatabase.create(appContext) }
    val preferences: PreferencesRepository by lazy { DataStorePreferencesRepository(appContext.focusBuddyPreferences) }
    val reminderScheduler = com.focusbuddy.domain.reminders.UnavailableReminderScheduler
    val tasks: TaskRepository by lazy { SchedulingTaskRepository(RoomTaskRepository(database, clock), reminderScheduler) }
    val reminders: ReminderRepository by lazy { SchedulingReminderRepository(RoomReminderRepository(database, clock), reminderScheduler) }
    val categories: CategoryRepository by lazy { RoomCategoryRepository(database) }
    val focusSessions: FocusSessionRepository by lazy { RoomFocusSessionRepository(database) }
    val calendar: CalendarRepository by lazy { RoomCalendarRepository(database, DeviceTimeZoneSource(appContext)) }
    val agenda: AgendaRepository by lazy { RoomAgendaRepository(database) }
    val observeDashboard by lazy { ObserveDashboard(agenda, preferences, SystemMomentSource(clock, DeviceTimeZoneSource(appContext))) }
}
