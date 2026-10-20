package com.focusbuddy.ui.today

import com.focusbuddy.domain.model.*
import com.focusbuddy.ui.components.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

data class TaskSectionUi(val section: TaskSection, val tasks: List<TaskCardData>)

data class TaskEditorState(
    val session: UUID = UUID.randomUUID(), val taskId: UUID? = null,
    val draft: TaskDraft, val original: TaskDraft = draft,
    val loading: Boolean = false, val busy: Boolean = false,
    val loadError: String? = null, val error: String? = null,
    val validation: Map<String, String> = emptyMap(),
    val confirmDiscard: Boolean = false, val confirmDelete: Boolean = false,
    val recurrenceRule: String? = null, val duplicated: Boolean = false, val taskCompleted: Boolean = false,
)

data class TodayUiState(
    val selectedDate: LocalDate = LocalDate.now(), val today: LocalDate = selectedDate,
    val greeting: Greeting = Greeting.SelectedDay,
    val sections: List<TaskSectionUi> = emptyList(), val reminders: List<ReminderCardData> = emptyList(),
    val completed: Int = 0, val total: Int = 0,
    val categories: List<Category> = emptyList(), val days: List<DaySummary> = emptyList(),
    val showMascot: Boolean = true, val animationsEnabled: Boolean = true,
    val isLoading: Boolean = false, val error: String? = null, val actionError: String? = null,
    val pendingTaskIds: Set<String> = emptySet(), val editor: TaskEditorState? = null,
    val dateChoices: List<LocalDate> = (-14L..14L).map(selectedDate::plusDays),
)

/** Presentation formatting only; grouping, due-state rules, and progress come from the domain. */
internal fun Dashboard.uiState(): TodayUiState {
    val time = com.focusbuddy.domain.time.timeFormatter(settings.timeFormat).withZone(zone)
    val shortDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    return TodayUiState(selectedDate, today, greeting,
        sections = groups.map { group -> TaskSectionUi(group.section, group.tasks.map { card ->
            TaskCardData(id = card.task.id.toString(), title = card.task.title,
                detail = listOfNotNull(
                    if (card.task.allDay) "All day" else card.task.startAt?.let { start ->
                        time.format(start) + (card.task.endAt?.let { " – ${time.format(it)}" } ?: "")
                    },
                    card.task.scheduledDate.takeIf { it != selectedDate }?.format(shortDate),
                ).joinToString(" · "),
                category = card.category?.name, tone = CategoryTone.Blue,
                priority = when (card.task.priority) {
                    TaskPriority.Low -> Priority.Low
                    TaskPriority.Medium -> Priority.Medium
                    TaskPriority.High -> Priority.High
                }, completed = card.task.completed, categoryColor = card.category?.color,
                reminderCount = card.activeReminderCount, series = card.task.recurrenceRule != null, overdue = card.overdue, subtasksCompleted = card.subtasksCompleted, subtasksTotal = card.subtasksTotal)
        }) },
        reminders = standaloneAlerts.map { ReminderCardData(it.reminder.title.orEmpty(), time.format(it.trigger),
            "Standalone reminder", "${it.reminder.id}:${it.identity}") },
        completed = completed, total = total, categories = categories, days = days,
        showMascot = settings.showMascot, animationsEnabled = settings.animationsEnabled)
}

data class TaskDetailsState(val id: UUID, val details: TaskDetails? = null, val loading: Boolean = false,
    val error: String? = null, val busy: Boolean = false, val actionError: String? = null, val confirmDelete: Boolean = false)
data class ReminderEditorState(val session: UUID = UUID.randomUUID(), val id: UUID? = null,
    val draft: ReminderDraft, val original: ReminderDraft = draft, val loading: Boolean = false,
    val busy: Boolean = false, val loadError: String? = null, val error: String? = null,
    val validation: Map<String, String> = emptyMap(), val confirmDiscard: Boolean = false, val confirmDelete: Boolean = false)

data class ReminderLibraryState(val open: Boolean = false, val loading: Boolean = false,
    val reminders: List<Reminder> = emptyList(), val error: String? = null)
