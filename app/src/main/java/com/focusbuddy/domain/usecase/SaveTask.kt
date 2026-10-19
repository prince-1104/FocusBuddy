package com.focusbuddy.domain.usecase

import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.repository.TaskRepository
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.UUID

class DraftValidationException(val field: String, override val message: String) : IllegalArgumentException(message)

class SaveTask(private val tasks: TaskRepository) {
    suspend operator fun invoke(draft: TaskDraft, id: UUID?, zone: ZoneId): Task {
        val content = validate(draft, zone)
        return if (id == null) tasks.create(Task(title = content.title, description = content.description,
            scheduledDate = content.scheduledDate, startAt = content.startAt, endAt = content.endAt,
            allDay = content.allDay, priority = content.priority, categoryId = content.categoryId))
        else tasks.updateContent(id, content)
    }

    companion object {
        fun validate(draft: TaskDraft, zone: ZoneId): TaskContent {
            if (draft.title.isBlank()) throw DraftValidationException("title", "Give your task a title.")
            val date = try { LocalDate.parse(draft.date.trim()) } catch (_: DateTimeException) {
                throw DraftValidationException("date", "Use a valid date in YYYY-MM-DD format.")
            }
            fun time(text: String, field: String): Instant {
                val time = try { LocalTime.parse(text.trim(), DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)) }
                    catch (_: DateTimeException) { throw DraftValidationException(field, "Use 24-hour time, HH:mm.") }
                val local = date.atTime(time)
                val offsets = zone.rules.getValidOffsets(local)
                if (offsets.isEmpty()) throw DraftValidationException(field, "This time doesn't exist because the clocks change. Choose another time.")
                return local.atOffset(offsets.first()).toInstant() // First occurrence when clocks repeat.
            }
            val start = if (draft.allDay) null else time(draft.startTime, "startTime")
            val end = if (draft.allDay || draft.endTime.isBlank()) null else time(draft.endTime, "endTime")
            if (start != null && end != null && end < start) throw DraftValidationException("endTime", "End time must follow start time on this date.")
            return TaskContent(draft.title.trim(), draft.description.trim().ifBlank { null }, date,
                start, end, draft.allDay, draft.priority, draft.categoryId)
        }
    }
}
