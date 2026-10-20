package com.focusbuddy.domain.reminders

/** Post-commit, non-throwing reconciliation request. Only the platform adapter may touch OS alarms. */
interface ReminderScheduler {
    val deliveryAvailable: Boolean
    fun requestReconciliation()
}

/** Version 1 has no platform alarm adapter or notification permission yet. Never claims delivery. */
object UnavailableReminderScheduler : ReminderScheduler {
    override val deliveryAvailable = false
    override fun requestReconciliation() = Unit
}
