package com.focusbuddy.domain.usecase

import com.focusbuddy.domain.model.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

fun newTaskDraft(date: LocalDate, time: LocalTime?, settings: MascotSettings): TaskDraft = TaskDraft(
    date = date.toString(), allDay = time == null, startTime = time?.format(DateTimeFormatter.ofPattern("HH:mm")).orEmpty(),
    alerts = settings.defaultReminderOffset?.let { listOf(AlertDraft(offsetMinutes = it, followTaskRecurrence = true,
        soundEnabled = settings.defaultAlertSound, vibrationEnabled = settings.defaultAlertVibration)) } ?: emptyList())
fun newReminderDraft(date: LocalDate, time: LocalTime?, settings: MascotSettings): ReminderDraft = ReminderDraft(alert = AlertDraft(
    date = date.toString(), time = (time ?: LocalTime.of(9, 0)).format(DateTimeFormatter.ofPattern("HH:mm")),
    soundEnabled = settings.defaultAlertSound, vibrationEnabled = settings.defaultAlertVibration))
