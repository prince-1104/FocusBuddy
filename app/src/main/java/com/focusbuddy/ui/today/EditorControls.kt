package com.focusbuddy.ui.today

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.usecase.EditorValidation
import com.focusbuddy.ui.components.*
import java.time.*
import java.time.format.*

@Composable
internal fun EditorField(value: String, label: String, onChange: (String) -> Unit, error: String? = null, enabled: Boolean = true, multiline: Boolean = false) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth().semantics {
        if (error != null) { this.error(error); liveRegion = LiveRegionMode.Polite }
    }, label = { Text(label) }, singleLine = !multiline, minLines = if (multiline) 2 else 1,
        isError = error != null, enabled = enabled, supportingText = error?.let { { Text(it) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateField(value: String, label: String, onChange: (String) -> Unit, error: String? = null, enabled: Boolean = true) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    EditorField(value, label, onChange, error, enabled)
    TextButton({ choosing = true }, enabled = enabled) { Text("Choose date") }
    if (choosing) {
        val initial = runCatching { LocalDate.parse(value).takeIf { it.year in 1..9999 }?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli() }.getOrNull()
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial, yearRange = 1..9999)
        DatePickerDialog(onDismissRequest = { choosing = false }, confirmButton = {
            TextButton({ picker.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }; choosing = false }, enabled = picker.selectedDateMillis != null) { Text("Use date") }
        }, dismissButton = { TextButton({ choosing = false }) { Text("Cancel") } }) { DatePicker(picker) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeField(value: String, label: String, onChange: (String) -> Unit, error: String? = null, enabled: Boolean = true) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    EditorField(value, "$label / HH:mm", onChange, error, enabled)
    val format = LocalMascotSettings.current.timeFormat
    TextButton({ choosing = true }, enabled = enabled) { Text("Choose ${label.lowercase()}") }
    if (choosing) {
        val initial = runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.of(9, 0))
        val picker = rememberTimePickerState(initial.hour, initial.minute, is24Hour = format == TimeFormat.Hour24)
        var clockMode by rememberSaveable { mutableStateOf(true) }
        Dialog(onDismissRequest = { choosing = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.padding(12.dp).widthIn(max = 400.dp).fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp) {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(label, style = MaterialTheme.typography.titleLarge)
                    if (clockMode) Box(Modifier.testTag("time-dial")) { TimePicker(picker, layoutType = TimePickerLayoutType.Vertical) }
                    else Box(Modifier.testTag("time-keyboard")) { TimeInput(picker) }
                    TextButton({ clockMode = !clockMode }) { Text(if (clockMode) "Use keyboard" else "Use clock") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton({ choosing = false }) { Text("Cancel") }
                        TextButton({
                            onChange(LocalTime.of(picker.hour, picker.minute).format(DateTimeFormatter.ofPattern("HH:mm")))
                            choosing = false
                        }) { Text("Use time") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EditorToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, null, enabled = enabled)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecurrenceControl(value: String, onChange: (String) -> Unit, enabled: Boolean, error: String? = null) {
    Text("Repeat", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Never" to "", "Daily" to "FREQ=DAILY", "Weekly" to "FREQ=WEEKLY", "Monthly" to "FREQ=MONTHLY", "Yearly" to "FREQ=YEARLY").forEach { (label, rule) ->
            FilterChip(value == rule, { onChange(rule) }, label = { Text(label) }, enabled = enabled)
        }
    }
    var advanced by rememberSaveable { mutableStateOf(value.isNotEmpty() && value !in listOf("FREQ=DAILY", "FREQ=WEEKLY", "FREQ=MONTHLY", "FREQ=YEARLY")) }
    TextButton({ advanced = !advanced }, enabled = enabled) { Text(if (advanced) "Hide custom repeat rule" else "Custom repeat rule") }
    if (advanced) EditorField(value, "Rule / e.g. FREQ=WEEKLY;INTERVAL=2;COUNT=6", onChange, error, enabled)
    else error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (value.isNotBlank()) Text(value.replace(';', ' '), style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun AlertFields(draft: AlertDraft, onChange: (AlertDraft) -> Unit, enabled: Boolean,
    task: TaskDraft? = null, error: String? = null) {
    EditorToggle("Alert enabled", draft.enabled, { onChange(draft.copy(enabled = it)) }, enabled)
    if (draft.offsetMinutes == null) {
        DateField(draft.date, "Alert date / YYYY-MM-DD", { onChange(draft.copy(date = it)) }, enabled = enabled)
        TimeField(draft.time, "Alert time", { onChange(draft.copy(time = it)) }, enabled = enabled)
    } else Text(if (draft.offsetMinutes == 0) "At event time" else "${draft.offsetMinutes} minutes before event time")
    val format = LocalMascotSettings.current.timeFormat
    val summary = remember(draft, task, format) {
        runCatching {
            val zone = ZoneId.systemDefault()
            val anchor = task?.let { EditorValidation.instant(it.date, if (it.allDay) "09:00" else it.startTime, zone, "trigger") }
            val record = EditorValidation.alert(draft, task?.let { java.util.UUID(0, 0) }, anchor, task?.recurrenceRule?.ifBlank { null }, zone, Instant.MIN, true, "trigger")
            com.focusbuddy.domain.time.dateTimeFormatter(format).withZone(zone).format(record.effectiveTriggerAt)
        }.getOrDefault("Choose a valid date, time and repeat rule")
    }
    Text("${if (draft.enabled) "Alert" else "Disabled alert"}: $summary", style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary)
    if (task != null && draft.offsetMinutes != null) EditorToggle("Repeat with task", draft.followTaskRecurrence, { onChange(draft.copy(followTaskRecurrence = it)) }, enabled)
    if (!draft.followTaskRecurrence) RecurrenceControl(draft.recurrenceRule, { onChange(draft.copy(recurrenceRule = it)) }, enabled)
    EditorToggle("Default alert sound", draft.soundEnabled, { onChange(draft.copy(soundEnabled = it)) }, enabled)
    EditorToggle("Vibration", draft.vibrationEnabled, { onChange(draft.copy(vibrationEnabled = it)) }, enabled)
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite; this.error(it) }) }
}

@Composable
internal fun EditorSaveFooter(label: String, onSave: () -> Unit, enabled: Boolean, message: String?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite; error(it) }) }
        PrimaryButton(label, onSave, Modifier.fillMaxWidth(), enabled)
    }
}
