package com.focusbuddy.domain.discovery

import com.focusbuddy.domain.calendar.Recurrence
import com.focusbuddy.domain.model.*
import java.time.*
import java.util.UUID

enum class TaskList { Today, Upcoming, Overdue, Completed, All }
enum class CompletionFilter { Any, Remaining, Completed }
data class TaskFilters(val query: String = "", val list: TaskList = TaskList.Today,
    val categoryId: UUID? = null, val uncategorized: Boolean = false,
    val completion: CompletionFilter = CompletionFilter.Any, val priority: TaskPriority? = null)

/** Each series appears once; dates use the same device-zone recurrence rules as Explore. */
fun discoverTasks(tasks: List<Task>, filters: TaskFilters, now: ZonedDateTime): List<Task> {
    val today = now.toLocalDate()
    fun occursToday(task: Task) = Recurrence.dates(task.scheduledDate, task.recurrenceRule, today, today).isNotEmpty()
    fun hasFuture(task: Task): Boolean {
        val rule = task.recurrenceRule ?: return task.scheduledDate > today
        val fields = rule.removePrefix("RRULE:").split(';').associate { it.substringBefore('=') to it.substringAfter('=') }
        val until = fields["UNTIL"]?.take(8)?.let { LocalDate.parse(it, java.time.format.DateTimeFormatter.BASIC_ISO_DATE) }
        if (until != null && until < maxOf(today.plusDays(1), task.scheduledDate)) return false
        if (task.scheduledDate > today) return true
        val count = fields["COUNT"]?.toInt()
        return count == null || Recurrence.dates(task.scheduledDate, rule, task.scheduledDate, today).size < count
    }
    return tasks.asSequence().filter { task ->
        val occurrenceStart = if (task.scheduledDate == today) task.startAt else
            task.startAt?.atZone(now.zone)?.toLocalTime()?.atDate(today)?.atZone(now.zone)?.toInstant()
        val dateMatch = when (filters.list) {
            TaskList.Today -> occursToday(task)
            TaskList.Upcoming -> !task.completed && hasFuture(task)
            TaskList.Completed -> task.completed
            TaskList.All -> true
            TaskList.Overdue -> !task.completed && (
                (task.recurrenceRule == null && task.scheduledDate < today) ||
                (occursToday(task) && !task.allDay && occurrenceStart?.let { it < now.toInstant() } == true))
        }
        dateMatch && (filters.categoryId == null || task.categoryId == filters.categoryId) &&
            (!filters.uncategorized || task.categoryId == null) &&
            (filters.priority == null || task.priority == filters.priority) &&
            when (filters.completion) { CompletionFilter.Any -> true; CompletionFilter.Remaining -> !task.completed; CompletionFilter.Completed -> task.completed } &&
            (filters.query.isBlank() || listOfNotNull(task.title, task.description, task.notes).any { it.contains(filters.query.trim(), ignoreCase = true) })
    }.sortedWith(compareBy<Task> { it.scheduledDate }.thenBy { it.startAt }.thenBy { it.id }).toList()
}
