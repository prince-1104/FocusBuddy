package com.focusbuddy.domain.time

import com.focusbuddy.domain.model.TimeFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

fun timeFormatter(format: TimeFormat): DateTimeFormatter = DateTimeFormatter.ofPattern(
    if (format == TimeFormat.Hour24) "HH:mm" else "h:mm a", Locale.getDefault())
fun dateTimeFormatter(format: TimeFormat): DateTimeFormatter = DateTimeFormatter.ofPattern(
    if (format == TimeFormat.Hour24) "MMM d, uuuu · HH:mm" else "MMM d, uuuu · h:mm a", Locale.getDefault())
