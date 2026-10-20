package com.focusbuddy.ui.focus

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.focusbuddy.domain.mascot.*
import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.time.dateTimeFormatter
import com.focusbuddy.ui.components.*
import com.focusbuddy.ui.screens.ScreenPage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.time.ZoneId
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusRoute(model: FocusViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val minutes by model.minutes.collectAsStateWithLifecycle()
    val selected by model.selectedTask.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val settings = LocalMascotSettings.current
    val latestSettings by rememberUpdatedState(settings)
    val owner = LocalLifecycleOwner.current
    val sound = com.focusbuddy.ui.mascot.rememberCompanionSound()
    var celebrating by remember { mutableStateOf(false) }
    var reaction by remember { mutableLongStateOf(0) }
    LaunchedEffect(model, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            model.completions.collectLatest {
                celebrating = true; reaction++
                if (latestSettings.showMascot && latestSettings.soundEnabled) sound()
                try { delay(2400) } finally { celebrating = false }
            }
        }
    }
    var stopConfirmation by rememberSaveable { mutableStateOf(false) }
    ScreenPage("Focus", "A little room for one meaningful thing.") {
        when {
            state.loading -> LoadingState("Opening your focus session")
            state.error != null -> ErrorState(state.error!!, model::retry)
            else -> {
                val active = state.active
                BuddyCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        val seconds = active?.remainingMillis?.let { (it + 999) / 1000 } ?: ((minutes.toIntOrNull()?.takeIf { it in 1..240 } ?: 25) * 60L)
                        Box(Modifier.size(208.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(progress = { active?.progress ?: 0f }, Modifier.fillMaxSize(), strokeWidth = 6.dp)
                            Text(String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60), style = MaterialTheme.typography.displayMedium,
                                modifier = Modifier.semantics { contentDescription = "${seconds / 60} minutes ${seconds % 60} seconds remaining" })
                        }
                        Text(if (active == null) "Ready when you are" else if (active.running) "Finding your rhythm" else "Paused · take your time")
                        if (active != null) {
                            Text(state.tasks.firstOrNull { it.id == active.taskId }?.title ?: "A session for yourself")
                            PrimaryButton(if (active.running) "Pause" else "Resume", if (active.running) model::pause else model::resume, Modifier.fillMaxWidth(), !busy)
                            SecondaryButton("Stop session", { stopConfirmation = true }, Modifier.fillMaxWidth(), !busy)
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(25, 45, 60).forEach { value -> FilterChip(minutes == value.toString(), { model.duration(value.toString()) }, label = { Text("$value min") }, enabled = !busy) }
                            }
                            OutlinedTextField(minutes, model::duration, label = { Text("Custom duration · 1–240 minutes") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                            var choosing by remember { mutableStateOf(false) }
                            Box {
                                SecondaryButton(state.tasks.firstOrNull { it.id.toString() == selected }?.title ?: "Choose a task · optional", { choosing = true }, enabled = !busy)
                                DropdownMenu(choosing, { choosing = false }) {
                                    DropdownMenuItem({ Text("No task") }, { model.selectTask(""); choosing = false })
                                    state.tasks.filterNot { it.completed }.forEach { task -> DropdownMenuItem({ Text(task.title) }, { model.selectTask(task.id.toString()); choosing = false }) }
                                }
                            }
                            PrimaryButton("Start focus", model::start, Modifier.fillMaxWidth(), !busy)
                        }
                        message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                    }
                }
                if (settings.showMascot) BuddyCard {
                    val moment = if (celebrating || (active == null && state.history.firstOrNull()?.completed == true)) MascotMoment.TaskCompleted else MascotMoment.Resting
                    MascotArtwork(Modifier.size(96.dp), "Your focus companion", settings, moment, if (celebrating) reaction else 0)
                    Text(if (moment == MascotMoment.TaskCompleted) "Focus session complete. Make room for a break." else mascotMessage(settings.personality, moment))
                }
                Text("Your timer keeps time in the background. You'll see completion when you return; no alert sounds while the app is closed. Stopping saves elapsed focus time. After a device restart, an interrupted session retains only the time saved before restarting.", style = MaterialTheme.typography.bodySmall)
                Text("Your recent focus", style = MaterialTheme.typography.titleLarge)
                if (state.history.isEmpty()) Text("Your first quiet moment starts here.")
                val formatter = dateTimeFormatter(settings.timeFormat).withZone(ZoneId.systemDefault())
                state.history.take(30).forEach { session -> key(session.id) { BuddyCard {
                    val seconds = active?.takeIf { it.id == session.id }?.let { (it.targetSeconds * 1000 - it.remainingMillis) / 1000 } ?: session.durationSeconds
                    val outcome = if (session.outcome == FocusOutcome.Legacy) "Earlier session" else session.outcome.name
                    Text(formatter.format(session.startedAt))
                    Text(state.tasks.firstOrNull { it.id == session.taskId }?.title ?: "Personal focus")
                    Text("${seconds / 60} min ${seconds % 60} sec · $outcome")
                } } }
            }
        }
    }
    if (stopConfirmation) ConfirmationDialog("Stop this session?", "Elapsed focus time will be saved as stopped. You can start a fresh session whenever you're ready.", "Stop session",
        { stopConfirmation = false; model.stop() }, { stopConfirmation = false })
}
