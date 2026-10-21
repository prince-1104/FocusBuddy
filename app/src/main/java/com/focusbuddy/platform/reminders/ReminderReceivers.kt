package com.focusbuddy.platform.reminders

import android.content.*
import android.os.PowerManager
import com.focusbuddy.FocusBuddyApplication
import kotlinx.coroutines.*
import java.util.UUID

internal fun BroadcastReceiver.performReminderOperation(context: Context, operation: suspend () -> Unit) {
    val pending = goAsync()
    val lock = context.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FocusBuddy:delivery").apply { acquire(9_000) }
    (context.applicationContext as FocusBuddyApplication).container.reminderScope.launch {
        var rebuild = false
        try { withTimeout(8_000) { operation() } }
        catch (error: Exception) {
            rebuild = true
            android.util.Log.e("FocusBuddyReminders", "Reminder delivery could not finish; recovery requested", error)
        }
        finally {
            if (lock.isHeld) lock.release()
            // The OS has consumed this alarm. A failed pre-claim delivery must be
            // reinstalled instead of trusting the old registration journal.
            runCatching { AndroidReminderScheduler.enqueue(context, force = rebuild) }
            pending.finish()
        }
    }
}
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.focusbuddy.MAINTAIN") { AndroidReminderScheduler.enqueue(context); return }
        if (intent.action != "com.focusbuddy.FIRE") return
        val key = intent.data?.takeIf { it.host == "alarm" }?.lastPathSegment ?: return
        runCatching { UUID.fromString(key) }.getOrNull() ?: return
        performReminderOperation(context) { (context.applicationContext as FocusBuddyApplication).container.reminderEngine.deliver(key) }
    }
}
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val parts = intent.data?.takeIf { it.host == "action" }?.pathSegments ?: return
        if (parts.size != 3) return
        val key = parts[0]
        runCatching { UUID.fromString(key) }.getOrNull() ?: return
        val action = parts[1]
        if (intent.action != "com.focusbuddy.$action") return
        performReminderOperation(context) {
            val container = (context.applicationContext as FocusBuddyApplication).container
            when (action) {
                "COMPLETE" -> container.reminderEngine.complete(key)
                "SNOOZE" -> parts[2].toIntOrNull()?.takeIf { it in setOf(5, 10, 15, 30) }?.let { container.reminderScheduler.scheduleSnoozedOccurrence(key, it) }
                "DISMISS" -> container.reminderEngine.dismiss(key)
            }
        }
    }
}
class ReminderRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED,
                android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) {
            AndroidReminderScheduler.enqueue(context, force = true)
        }
    }
}
