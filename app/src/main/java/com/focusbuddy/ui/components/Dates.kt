package com.focusbuddy.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Null counts mean unavailable, never zero. Tasks and reminder alerts remain independent. */
data class DayCounts(val tasks: Int, val reminderAlerts: Int)

@Composable
fun HorizontalDateSelector(dates: List<LocalDate>, selected: LocalDate, onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier, counts: Map<LocalDate, DayCounts> = emptyMap(), animationsEnabled: Boolean = true) {
    val target = (dates.indexOf(selected) - 2).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = target)
    LaunchedEffect(selected) {
        if (animationsEnabled) listState.animateScrollToItem(target) else listState.scrollToItem(target)
    }
    LazyRow(modifier.selectableGroup(), state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(dates, key = { it.toEpochDay() }) { date ->
            val active = date == selected
            val summary = counts[date]
            Surface(shape = MaterialTheme.shapes.medium,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) {
                Column(Modifier.widthIn(min = 58.dp).selectable(active, role = Role.Tab, onClick = { onSelect(date) })
                    .semantics(mergeDescendants = true) {
                        contentDescription = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) +
                            (summary?.let { ", ${it.tasks} tasks, ${it.reminderAlerts} reminder alerts" } ?: "")
                    }.padding(horizontal = 12.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(date.format(DateTimeFormatter.ofPattern("EEE")), style = MaterialTheme.typography.labelMedium)
                    Text("${date.dayOfMonth}", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.height(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (summary != null && summary.tasks > 0) Box(Modifier.size(4.dp).background(
                            if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary, CircleShape))
                        if (summary != null && summary.reminderAlerts > 0) Box(Modifier.size(4.dp).background(
                            if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.tertiary, CircleShape))
                    }
                }
            }
        }
    }
}

@Composable
fun MonthCalendar(month: YearMonth, selected: LocalDate, counts: Map<LocalDate, DayCounts>,
    onSelect: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val locale = Locale.getDefault()
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val offset = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
    val cellCount = ((offset + month.lengthOfMonth() + 6) / 7) * 7
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            repeat(7) { index ->
                val day = firstDay.plus(index.toLong())
                Text(day.getDisplayName(java.time.format.TextStyle.NARROW, locale),
                    Modifier.weight(1f).semantics {
                        contentDescription = day.getDisplayName(java.time.format.TextStyle.FULL, locale)
                    }, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
        }
        (0 until cellCount).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                week.forEach { cell ->
                    val day = cell - offset + 1
                    if (day !in 1..month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val date = month.atDay(day)
                        val summary = counts[date]
                        val active = date == selected
                        val description = buildString {
                            append(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)))
                            append(if (summary == null) ", task and reminder alert counts unavailable"
                                else ", ${summary.tasks} tasks, ${summary.reminderAlerts} reminder alerts")
                        }
                        Surface(Modifier.weight(1f), shape = MaterialTheme.shapes.small,
                            color = if (active) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface) {
                            Column(Modifier.selectable(active, role = Role.Tab, onClick = { onSelect(date) })
                                .clearAndSetSemantics {
                                    contentDescription = description
                                    this.selected = active
                                    role = Role.Tab
                                    onClick(label = "Select date") { onSelect(date); true }
                                }.heightIn(min = 90.dp).padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("$day", style = MaterialTheme.typography.labelLarge)
                                Text("T ${summary?.tasks ?: "—"}", style = MaterialTheme.typography.labelSmall)
                                Text("R ${summary?.reminderAlerts ?: "—"}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
