package com.focusbuddy.platform.reminders

import android.app.*
import android.content.*
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import com.focusbuddy.R
import com.focusbuddy.domain.reminders.*

fun actionIntent(context: Context, key: String, action: String, minutes: Int = 10): Intent =
    Intent(context, ReminderActionReceiver::class.java).setAction("com.focusbuddy.$action")
        .setData(Uri.parse("focusbuddy://action/$key/$action/$minutes"))
fun actionPendingIntent(context: Context, key: String, action: String, minutes: Int = 10): PendingIntent =
    PendingIntent.getBroadcast(context, 0, actionIntent(context, key, action, minutes), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
fun openPendingIntent(context: Context, key: String): PendingIntent = PendingIntent.getActivity(context, 0,
    Intent(context, com.focusbuddy.ui.reminders.ReminderActivity::class.java).setData(Uri.parse("focusbuddy://occurrence/$key"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

class AndroidNotifications(private val context: Context) : NotificationGateway {
    private val manager = context.getSystemService(NotificationManager::class.java)
    fun defaultChannel() { channel(true, true, null) }
    fun channel(sound: Boolean, vibration: Boolean, soundUri: String?): String {
        val custom = soundUri?.takeIf { sound }?.let { java.util.UUID.nameUUIDFromBytes(it.toByteArray(Charsets.UTF_8)).toString() }
        val id = "reminders_s${if (sound) 1 else 0}v${if (vibration) 1 else 0}" + (custom?.let { "_$it" } ?: "")
        val name = "Reminders / " + if (sound && vibration) "sound and vibration" else if (sound) "sound" else if (vibration) "vibration" else "silent"
        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Your FocusBuddy reminder alerts. Android channel choices override app preferences."
            enableVibration(vibration)
            setSound(if (!sound) null else soundUri?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
        }
        manager.createNotificationChannel(channel) // Existing user channel settings are never overwritten.
        return id
    }
    override fun show(delivery: ReminderDelivery): Boolean = try { showChecked(delivery) } catch (_: RuntimeException) { false }
    private fun showChecked(delivery: ReminderDelivery): Boolean {
        if (!AndroidAlarmGateway(context).access().notifications) return false
        val channel = channel(delivery.sound, delivery.vibration, delivery.soundUri)
        if (manager.getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val key = delivery.occurrence.key
        val icon = Icon.createWithResource(context, R.drawable.ic_reminder)
        val notification = Notification.Builder(context, channel).setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(delivery.title).setContentText(delivery.body.ifBlank { "A little reminder for your day" })
            .setStyle(Notification.BigTextStyle().bigText(delivery.body))
            .setWhen(delivery.occurrence.triggerAt.toEpochMilli()).setShowWhen(true)
            .setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true).setOnlyAlertOnce(true).setTimeoutAfter(java.time.Duration.ofHours(2).toMillis())
            .setContentIntent(openPendingIntent(context, key))
            .setDeleteIntent(actionPendingIntent(context, key, "DISMISS"))
            .addAction(Notification.Action.Builder(icon, "Complete", actionPendingIntent(context, key, "COMPLETE")).build())
            .addAction(Notification.Action.Builder(icon, "Snooze 10 min", actionPendingIntent(context, key, "SNOOZE", 10)).build())
            .addAction(Notification.Action.Builder(icon, "Open", openPendingIntent(context, key)).build()).build()
        return try { manager.notify(key, 0, notification); true } catch (_: SecurityException) { false }
    }
    override fun cancel(key: String) { manager.cancel(key, 0) }
}
