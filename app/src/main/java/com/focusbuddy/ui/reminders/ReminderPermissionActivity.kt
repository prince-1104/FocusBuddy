package com.focusbuddy.ui.reminders

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.*
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusbuddy.FocusBuddyApplication
import com.focusbuddy.domain.reminders.SchedulingAccess
import com.focusbuddy.platform.reminders.AndroidAlarmGateway
import com.focusbuddy.ui.components.*
import com.focusbuddy.ui.theme.FocusBuddyTheme
import kotlinx.coroutines.launch

class ReminderPermissionActivity : ComponentActivity() {
    private var access by mutableStateOf(SchedulingAccess(false, false))
    private var message by mutableStateOf<String?>(null)
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh()
        if (!it) message = "Notifications are off. You can enable them in Android settings whenever you choose."
    }
    private fun refresh() {
        access = AndroidAlarmGateway(this).access()
        (application as FocusBuddyApplication).container.reminderScheduler.requestReconciliation()
    }
    override fun onResume() { super.onResume(); refresh() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ReminderTheme {
            val health by (application as FocusBuddyApplication).container.reminderOccurrences.health.collectAsStateWithLifecycle(com.focusbuddy.domain.reminders.SchedulingHealth())
            Surface { Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (LocalReminderMascot.current) MascotArtwork(Modifier.size(80.dp))
                Text("A gentle nudge, on time", style = MaterialTheme.typography.headlineMedium)
                Text("FocusBuddy uses local Android notifications for the alerts you create. No account, network, or background overlay is needed.")
                BuddyCard {
                    Text(if (access.notifications) "Notifications enabled" else "Notifications are off", style = MaterialTheme.typography.titleMedium)
                    Text("Allow notifications to see reminders and their Complete, Snooze, and Open actions. Sound and vibration also follow your Android channel choices.")
                    if (!access.notifications && Build.VERSION.SDK_INT >= 33) PrimaryButton("Allow notifications", { permission.launch(Manifest.permission.POST_NOTIFICATIONS) })
                    SecondaryButton("Notification and channel settings", {
                        launchSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                    })
                }
                BuddyCard {
                    Text(if (access.exact) "Precise timing allowed" else "Timing may be delayed", style = MaterialTheme.typography.titleMedium)
                    Text("For reminders you choose at a specific time, Android can allow precise alarms. Without this special access, FocusBuddy uses inexact alarms, which may arrive later, especially during battery saving.")
                    if (Build.VERSION.SDK_INT >= 31 && !access.exact) PrimaryButton("Review alarms and reminders access", {
                        launchSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                    })
                }
                if (health.failed > 0) Text("${health.failed} alarms could not be registered. Android or device alarm limits may apply. FocusBuddy will retry; reduce pending reminders if this persists.", color = MaterialTheme.colorScheme.error)
                if (health.blocked > 0) Text("${health.blocked} pending alerts are waiting for notification permission.")
                SecondaryButton("Retry schedule recovery", { lifecycleScope.launch { (application as FocusBuddyApplication).container.reminderScheduler.rebuild() } })
                Text("Battery restrictions, device vendor policies, Doze limits, force-stop and denied permissions can delay or prevent delivery. Exact timing is not guaranteed on every device.")
                Text("After a force-stop, open FocusBuddy again to recover alarms. After reboot, reminders recover after the device is unlocked. Missed alerts older than two hours expire instead of appearing as stale notifications.", style = MaterialTheme.typography.bodySmall)
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                PrimaryButton("Back to FocusBuddy", { finish() }, Modifier.fillMaxWidth())
            } }
        } }
    }
    private fun launchSettings(intent: Intent) {
        try { startActivity(intent) } catch (_: android.content.ActivityNotFoundException) { message = "This settings page is unavailable on this device. Open FocusBuddy's app settings manually." }
    }
}

/** Reads access on lifecycle resume; never schedules an alarm from recomposition. */
@Composable
fun ReminderSettingsEntry(compact: Boolean = false) {
    if (androidx.compose.ui.platform.LocalInspectionMode.current) { Text("Notification and timing settings"); return }
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var access by remember { mutableStateOf(AndroidAlarmGateway(context).access()) }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) access = AndroidAlarmGateway(context).access() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    TextButton({ context.startActivity(Intent(context, ReminderPermissionActivity::class.java)) }) {
        Text(if (compact) "Notification and timing settings" else
            "Notifications ${if (access.notifications) "on" else "off"} · ${if (access.exact) "Precise timing allowed" else "Inexact timing"} / Review")
    }
}
