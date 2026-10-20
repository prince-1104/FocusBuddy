package com.focusbuddy.ui.reminders

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.focusbuddy.FocusBuddyApplication
import com.focusbuddy.MainActivity
import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.reminders.ReminderScheduler
import com.focusbuddy.ui.components.*
import com.focusbuddy.ui.theme.FocusBuddyTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*
import java.time.format.*
import java.util.UUID
import com.focusbuddy.domain.mascot.*

data class ReminderPresentation(val reminder: Reminder?, val task: Task?, val at: Instant?, val status: String, val error: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderPresentationViewModel(private val source: com.focusbuddy.domain.reminders.ReminderOccurrenceRepository,
    private val scheduler: ReminderScheduler) : ViewModel() {
    private val key = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    private val successes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val completionFeedback = successes.asSharedFlow()
    val state = key.flatMapLatest { id ->
        if (id == null) flowOf(ReminderPresentation(null, null, null, "Unavailable")) else
            source.observe(id).map { ReminderPresentation(it.reminder, it.task, it.at, it.status) }
                .catch { emit(ReminderPresentation(null, null, null, "Unavailable", "This reminder couldn't be loaded.")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderPresentation(null, null, null, "Loading"))
    fun open(value: String?) { key.value = value?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() } }
    fun snooze(minutes: Int) = mutate {
        val next = scheduler.scheduleSnoozedOccurrence(key.value ?: return@mutate, minutes)
        message.value = if (next == null) "This alert is no longer active or has expired." else "Snoozed for $minutes minutes."
    }
    fun complete() = mutate {
        val done = scheduler.completeOccurrence(key.value ?: return@mutate)
        message.value = if (done) "Completion saved." else "This alert is no longer active or has expired."
        if (done) successes.emit(Unit)
    }
    private fun mutate(operation: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true; message.value = null
        viewModelScope.launch {
            try { operation() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { message.value = "Your change couldn't be saved. Please try again." }
            finally { busy.value = false }
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
class ReminderActivity : ComponentActivity() {
    private val model: ReminderPresentationViewModel by viewModels { viewModelFactory { initializer {
        val container = (application as FocusBuddyApplication).container
        ReminderPresentationViewModel(container.reminderOccurrences, container.reminderScheduler)
    } } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model.open(intent.data?.takeIf { it.host == "occurrence" }?.lastPathSegment)
        setContent { ReminderTheme {
            val state by model.state.collectAsStateWithLifecycle()
            val busy by model.busy.collectAsStateWithLifecycle()
            val message by model.message.collectAsStateWithLifecycle()
            val settings = LocalMascotSettings.current
            val latestSettings by rememberUpdatedState(settings)
            val sound = com.focusbuddy.ui.mascot.rememberCompanionSound()
            LaunchedEffect(model) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    model.completionFeedback.collect { if (latestSettings.showMascot && latestSettings.soundEnabled) sound() }
                }
            }
            var minutes by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(10) }
            Surface { Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val moment = if (state.status == "Completed" || state.task?.completed == true) MascotMoment.TaskCompleted else MascotMoment.UpcomingReminder
                if (LocalReminderMascot.current) {
                    MascotArtwork(Modifier.size(140.dp), "Your reminder companion", settings, moment, animateEntrance = true)
                    Text(mascotMessage(settings.personality, moment), style = MaterialTheme.typography.bodyLarge)
                }
                Text("A moment for your day", style = MaterialTheme.typography.headlineMedium)
                when {
                    state.status == "Loading" -> LoadingState("Opening reminder")
                    state.reminder == null -> Text(state.error ?: "This reminder has been removed or is no longer available.")
                    else -> {
                        Text(state.reminder!!.title ?: state.task?.title.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                        state.task?.let { Text("For your task: ${it.title}") }
                        state.at?.let { Text(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(it), color = MaterialTheme.colorScheme.primary) }
                        Text("Status: ${state.status}")
                        (state.reminder!!.description ?: state.task?.description)?.let { Text(it) }
                        val active = state.status in setOf("Delivered", "Dismissed") && state.reminder!!.enabled && state.task?.completed != true
                        PrimaryButton(if (state.task == null) "Complete this alert" else "Complete task", { model.complete() }, Modifier.fillMaxWidth(), !busy && active)
                        Text("Snooze duration", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(5, 10, 15, 30).forEach { value ->
                            FilterChip(minutes == value, { minutes = value }, label = { Text("$value min") }, enabled = !busy)
                        } }
                        PrimaryButton("Snooze $minutes minutes", { model.snooze(minutes) }, Modifier.fillMaxWidth(), !busy && active)
                        SecondaryButton("Reschedule", { edit(state) }, Modifier.fillMaxWidth(), !busy)
                        SecondaryButton(if (state.task == null) "Open reminder" else "Open task", { edit(state, details = true) }, Modifier.fillMaxWidth(), !busy)
                    }
                }
                message?.let { Text(it) }
                ReminderSettingsEntry(compact = true)
                TextButton({ finish() }) { Text("Close") }
            } }
        } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); model.open(intent.data?.lastPathSegment) }
    override fun onResume() { super.onResume(); (application as FocusBuddyApplication).container.reminderScheduler.requestReconciliation() }
    private fun edit(state: ReminderPresentation, details: Boolean = false) {
        val record = state.reminder ?: return
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("reminderDate", (state.at ?: record.triggerAt).atZone(ZoneId.systemDefault()).toLocalDate().toString())
        if (state.task != null) intent.putExtra(if (details) "openTask" else "editTask", state.task.id.toString())
        else intent.putExtra("editReminder", record.id.toString())
        startActivity(intent)
    }
}
