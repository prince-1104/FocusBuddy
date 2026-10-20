package com.focusbuddy.domain.model

import java.time.Instant
import java.util.UUID

data class SubtaskDraft(val id: UUID = UUID.randomUUID(), val title: String = "", val completed: Boolean = false)
data class AlertDraft(
    val id: UUID = UUID.randomUUID(), val date: String = "", val time: String = "09:00",
    val offsetMinutes: Int? = null, val enabled: Boolean = true,
    val soundEnabled: Boolean = true, val vibrationEnabled: Boolean = true,
    val recurrenceRule: String = "", val followTaskRecurrence: Boolean = false,
    val soundUri: String? = null, val snoozedUntil: Instant? = null, val originalTrigger: Instant? = null,
    val originalEnabled: Boolean = false,
)
data class ReminderDraft(
    val title: String = "", val description: String = "", val alert: AlertDraft,
    val allowPast: Boolean = false,
)
