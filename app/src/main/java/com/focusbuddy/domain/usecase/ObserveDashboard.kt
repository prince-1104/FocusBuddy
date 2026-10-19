package com.focusbuddy.domain.usecase

import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.repository.*
import com.focusbuddy.domain.time.MomentSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.ZonedDateTime

class ObserveDashboard(
    private val agenda: AgendaRepository,
    private val preferences: PreferencesRepository,
    private val moments: MomentSource,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(selectedDates: Flow<LocalDate>): Flow<Dashboard> = selectedDates.flatMapLatest { selected ->
        combine(moments.moments, preferences.settings) { now, settings -> now to settings }
            .flatMapLatest { (now, settings) ->
                agenda.observe(selected, now.toLocalDate(), now.zone).map { snapshot -> project(selected, now, settings, snapshot) }
            }
    }.distinctUntilChanged()

    companion object {
        fun project(selected: LocalDate, now: ZonedDateTime, settings: MascotSettings, snapshot: AgendaSnapshot): Dashboard {
            val today = now.toLocalDate()
            val categories = snapshot.categories.associateBy { it.id }
            val cards = snapshot.tasks.map { details ->
                val task = details.task
                val deadline = task.endAt ?: task.startAt
                val overdue = !task.completed && (task.scheduledDate < today ||
                    (task.scheduledDate == today && !task.allDay && deadline != null && deadline <= now.toInstant()))
                DashboardTask(task, categories[task.categoryId], details.reminders.count { it.enabled }, overdue)
            }
            fun section(card: DashboardTask) = when {
                card.task.completed -> TaskSection.Completed
                card.overdue -> TaskSection.Overdue
                card.task.allDay -> TaskSection.Anytime
                selected == today && card.task.startAt != null && card.task.startAt > now.toInstant().plusSeconds(7200) -> TaskSection.LaterToday
                else -> TaskSection.Upcoming
            }
            val grouped = cards.groupBy(::section)
            val order = compareBy<DashboardTask>({ it.task.scheduledDate }, { it.task.startAt })
                .thenByDescending { it.task.priority.ordinal }.thenBy { it.task.id }
            val groups = TaskSection.entries.mapNotNull { group ->
                grouped[group]?.takeIf { it.isNotEmpty() }?.let { TaskGroup(group, it.sortedWith(order)) }
            }
            val scheduled = cards.filter { it.task.scheduledDate == selected }
            return Dashboard(selected, today,
                when {
                    selected != today -> Greeting.SelectedDay
                    now.hour < 12 -> Greeting.Morning
                    now.hour < 18 -> Greeting.Afternoon
                    else -> Greeting.Evening
                }, groups, snapshot.standaloneReminders, scheduled.count { it.task.completed }, scheduled.size,
                snapshot.categories, snapshot.days, settings, now.zone)
        }
    }
}
