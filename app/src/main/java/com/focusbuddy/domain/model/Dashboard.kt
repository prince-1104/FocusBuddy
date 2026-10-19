package com.focusbuddy.domain.model

import java.time.LocalDate

data class AgendaSnapshot(
    val tasks: List<TaskDetails>, val standaloneReminders: List<Reminder>,
    val categories: List<Category>, val days: List<DaySummary>,
)

enum class TaskSection { Overdue, Upcoming, LaterToday, Anytime, Completed }
enum class Greeting { Morning, Afternoon, Evening, SelectedDay }
data class DashboardTask(val task: Task, val category: Category?, val activeReminderCount: Int, val overdue: Boolean)
data class TaskGroup(val section: TaskSection, val tasks: List<DashboardTask>)
data class Dashboard(
    val selectedDate: LocalDate, val today: LocalDate, val greeting: Greeting,
    val groups: List<TaskGroup>, val standaloneReminders: List<Reminder>,
    val completed: Int, val total: Int, val categories: List<Category>, val days: List<DaySummary>,
    val settings: MascotSettings, val zone: java.time.ZoneId,
)

data class TaskContent(
    val title: String, val description: String?, val scheduledDate: LocalDate,
    val startAt: java.time.Instant?, val endAt: java.time.Instant?, val allDay: Boolean,
    val priority: TaskPriority, val categoryId: java.util.UUID?,
)

/** Form input is validated by a domain service, not by composables. */
data class TaskDraft(
    val title: String = "", val description: String = "", val date: String,
    val allDay: Boolean = true, val startTime: String = "", val endTime: String = "",
    val priority: TaskPriority = TaskPriority.Medium, val categoryId: java.util.UUID? = null,
)
