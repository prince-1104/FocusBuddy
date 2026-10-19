package com.focusbuddy.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.focusbuddy.ui.components.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ExploreScreen(selectedDate: LocalDate, onSelectDate: (LocalDate) -> Unit, onToday: () -> Unit,
    onOpenDay: () -> Unit, counts: Map<LocalDate, DayCounts> = emptyMap(), today: LocalDate = LocalDate.now()) {
    var visibleMonth by rememberSaveable(selectedDate) { mutableStateOf(YearMonth.from(selectedDate).toString()) }
    val selected = selectedDate
    val month = YearMonth.parse(visibleMonth)
    ScreenPage("Explore", "A little perspective. A little possibility.") {
        Text(month.format(DateTimeFormatter.ofPattern("MMMM uuuu")), style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Previous", { visibleMonth = month.minusMonths(1).toString() },
                Modifier.weight(1f).semantics { contentDescription = "Previous month" })
            SecondaryButton("Next", { visibleMonth = month.plusMonths(1).toString() },
                Modifier.weight(1f).semantics { contentDescription = "Next month" })
        }
        MonthCalendar(month, selected, counts, onSelectDate)
        ComingNext("T · tasks    R · reminder alerts\n— means counts are not available yet.")
        TextButton(onClick = {
            visibleMonth = YearMonth.from(today).toString()
            onToday()
        }) { Text("Back to today") }
        SectionTitle("Your day, at a glance")
        Text(selected.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
            style = MaterialTheme.typography.titleMedium)
        BuddyCard {
            Text("Give this day a little attention.", style = MaterialTheme.typography.titleMedium)
            Text("Open your daily dashboard to view and edit this day's plans.", style = MaterialTheme.typography.bodyMedium)
            PrimaryButton("Open daily agenda", onOpenDay, Modifier.fillMaxWidth())
        }
    }
}
