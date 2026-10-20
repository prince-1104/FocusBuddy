package com.focusbuddy.domain.reminders

import java.time.Instant
import java.util.UUID

data class ReminderOccurrence(val key: String, val reminderId: UUID, val baseTrigger: Instant,
    val triggerAt: Instant, val kind: String, val fingerprint: String)
data class SchedulingAccess(val notifications: Boolean, val exact: Boolean)
data class ReminderDelivery(val occurrence: ReminderOccurrence, val title: String, val body: String,
    val taskId: UUID?, val sound: Boolean, val vibration: Boolean, val soundUri: String?)
interface AlarmGateway {
    val generation: String get() = ""
    fun access(): SchedulingAccess
    /** Returns the mode actually used, including a permission-race fallback. */
    fun schedule(occurrence: ReminderOccurrence): String
    fun cancel(key: String)
    fun maintenance(at: Instant)
    fun cancelMaintenance() {}
}
interface NotificationGateway {
    fun show(delivery: ReminderDelivery): Boolean
    fun cancel(key: String)
    fun cancelAll() {}
}
interface ReminderScheduler {
    val deliveryAvailable: Boolean
    fun requestReconciliation()
    suspend fun scheduleOccurrence(occurrence: ReminderOccurrence) { requestReconciliation() }
    suspend fun reschedule(reminderId: UUID) { requestReconciliation() }
    suspend fun cancel(reminderId: UUID) { requestReconciliation() }
    suspend fun scheduleSnoozedOccurrence(sourceKey: String, minutes: Int): String? = null
    suspend fun completeOccurrence(key: String): Boolean = false
    suspend fun reconcile() { requestReconciliation() }
    suspend fun rebuild() { requestReconciliation() }
}

data class ReminderContext(val reminder: com.focusbuddy.domain.model.Reminder?, val task: com.focusbuddy.domain.model.Task?, val at: Instant?, val status: String)
data class SchedulingHealth(val pending: Int = 0, val failed: Int = 0, val blocked: Int = 0)
interface ReminderOccurrenceRepository {
    fun observe(key: String): kotlinx.coroutines.flow.Flow<ReminderContext>
    val health: kotlinx.coroutines.flow.Flow<SchedulingHealth>
}
