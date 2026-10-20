package com.focusbuddy.platform.reminders

import android.app.job.*
import android.content.*
import com.focusbuddy.data.reminders.ReminderEngine
import com.focusbuddy.domain.reminders.*
import kotlinx.coroutines.*
import java.util.UUID

class AndroidReminderScheduler(private val context: Context, private val engine: ReminderEngine,
    private val scope: CoroutineScope) : ReminderScheduler {
    override val deliveryAvailable get() = AndroidAlarmGateway(context).access().notifications
    override fun requestReconciliation() {
        runCatching { enqueue(context) }
        scope.launch { runCatching { engine.reconcile() } }
    }
    override suspend fun scheduleOccurrence(occurrence: ReminderOccurrence) { engine.install(occurrence) }
    override suspend fun reschedule(reminderId: UUID) { engine.reconcile(force = true) }
    override suspend fun cancel(reminderId: UUID) { engine.cancel(reminderId); engine.reconcile() }
    override suspend fun scheduleSnoozedOccurrence(sourceKey: String, minutes: Int): String? = engine.snooze(sourceKey, minutes).also { requestReconciliation() }
    override suspend fun completeOccurrence(key: String): Boolean = engine.complete(key).also { requestReconciliation() }
    override suspend fun reconcile() { engine.reconcile() }
    override suspend fun rebuild() { engine.reconcile(force = true) }
    fun initialize() {
        AndroidNotifications(context).defaultChannel()
        val jobs = context.getSystemService(JobScheduler::class.java)
        if (jobs.getPendingJob(PERIODIC_JOB) == null) jobs.schedule(JobInfo.Builder(PERIODIC_JOB, ComponentName(context, ReminderReconcileJob::class.java))
            .setPersisted(true).setPeriodic(15 * 60_000L).build())
        requestReconciliation()
    }
    companion object {
        const val PERIODIC_JOB = 7101
        const val IMMEDIATE_JOB = 7102
        fun enqueue(context: Context, force: Boolean = false) {
            val jobs = context.getSystemService(JobScheduler::class.java)
            val needsRebuild = force || jobs.getPendingJob(IMMEDIATE_JOB)?.extras?.getBoolean("force") == true
            val extras = android.os.PersistableBundle().apply { putBoolean("force", needsRebuild) }
            // Replace the one job identity so a change during a running job cannot lose its wakeup.
            jobs.schedule(JobInfo.Builder(IMMEDIATE_JOB, ComponentName(context, ReminderReconcileJob::class.java))
                .setPersisted(true).setMinimumLatency(0).setOverrideDeadline(60_000).setExtras(extras).build())
        }
    }
}
class ReminderReconcileJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = mutableMapOf<Int, Job>()
    override fun onStartJob(params: JobParameters): Boolean {
        running[params.jobId] = scope.launch {
            try {
                (application as com.focusbuddy.FocusBuddyApplication).container.reminderEngine.reconcile(params.extras.getBoolean("force"))
                jobFinished(params, false)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { jobFinished(params, true) }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { running.remove(params.jobId)?.cancel(); return true }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
