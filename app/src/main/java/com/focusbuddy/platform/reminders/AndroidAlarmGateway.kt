package com.focusbuddy.platform.reminders

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.focusbuddy.domain.reminders.*
import java.time.Instant

fun alarmIntent(context: Context, key: String) = Intent(context, ReminderAlarmReceiver::class.java)
    .setAction("com.focusbuddy.FIRE").setData(Uri.parse("focusbuddy://alarm/$key"))
fun alarmPendingIntent(context: Context, key: String, flags: Int = PendingIntent.FLAG_UPDATE_CURRENT): PendingIntent? =
    PendingIntent.getBroadcast(context, 0, alarmIntent(context, key), flags or PendingIntent.FLAG_IMMUTABLE)

class AndroidAlarmGateway(private val context: Context) : AlarmGateway {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    override val generation get() = android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, 0).toString()
    override fun access() = SchedulingAccess(
        context.getSystemService(NotificationManager::class.java).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED),
        Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms())
    override fun schedule(occurrence: ReminderOccurrence): String {
        val pending = checkNotNull(alarmPendingIntent(context, occurrence.key))
        if (access().exact) try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, occurrence.triggerAt.toEpochMilli(), pending)
            return "Exact"
        } catch (_: SecurityException) { /* Grant can change between capability check and binder call. */ }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, occurrence.triggerAt.toEpochMilli(), pending)
        return "Inexact"
    }
    override fun cancel(key: String) {
        alarmPendingIntent(context, key, PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() }
    }
    override fun maintenance(at: Instant) {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).setAction("com.focusbuddy.MAINTAIN")
            .setData(Uri.parse("focusbuddy://maintenance/v1"))
        val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
    }
    override fun cancelMaintenance() {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).setAction("com.focusbuddy.MAINTAIN")
            .setData(Uri.parse("focusbuddy://maintenance/v1"))
        PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { alarms.cancel(it); it.cancel() }
    }
}
