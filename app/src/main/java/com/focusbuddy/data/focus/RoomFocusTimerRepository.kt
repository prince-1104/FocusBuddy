package com.focusbuddy.data.focus

import androidx.room.withTransaction
import com.focusbuddy.data.local.*
import com.focusbuddy.domain.focus.*
import com.focusbuddy.domain.model.FocusOutcome
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

/** Only transitions write state. Countdown derives from a persisted monotonic segment deadline. */
class RoomFocusTimerRepository(private val db: FocusBuddyDatabase, private val clock: FocusClock) : FocusTimerRepository {
    override fun observe(): Flow<ActiveFocus?> = flow {
        while (currentCoroutineContext().isActive) { emit(snapshot()); delay(1000) }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)
    override suspend fun snapshot(): ActiveFocus? = withContext(Dispatchers.IO) { db.withTransaction { resolve()?.let { row ->
        ActiveFocus(row.sessionId, db.focusSessions().get(row.sessionId)?.taskId, row.targetSeconds, remaining(row), row.segmentStartedElapsed != null)
    } } }
    private fun remaining(row: ActiveFocusEntity): Long = (row.remainingMillis -
        (row.segmentStartedElapsed?.let { (clock.elapsedMillis() - it).coerceAtLeast(0) } ?: 0)).coerceAtLeast(0)
    private suspend fun resolve(): ActiveFocusEntity? {
        val row = db.activeFocus().get() ?: return null
        val history = db.focusSessions().get(row.sessionId) ?: run { db.activeFocus().clear(); return null }
        if (row.segmentStartedElapsed != null && (row.bootId != clock.bootId() || clock.elapsedMillis() < row.segmentStartedElapsed)) {
            db.focusSessions().update(history.copy(endedAt = maxOf(history.startedAt, row.checkpointAt),
                durationSeconds = (row.targetSeconds * 1000 - row.remainingMillis) / 1000, outcome = FocusOutcome.Interrupted.name))
            db.activeFocus().clear(); return null
        }
        if (remaining(row) == 0L) {
            db.focusSessions().update(history.copy(endedAt = maxOf(history.startedAt, row.checkpointAt.plusMillis(row.remainingMillis)),
                durationSeconds = row.targetSeconds, completed = true, outcome = FocusOutcome.Completed.name))
            db.activeFocus().clear(); return null
        }
        return row
    }
    override suspend fun start(minutes: Int, taskId: UUID?) = withContext(Dispatchers.IO) { db.withTransaction {
        require(minutes in 1..240) { "Choose 1 to 240 minutes." }
        check(resolve() == null) { "Stop the current session before starting another." }
        require(taskId == null || db.tasks().get(taskId) != null) { "This task was removed. Select another task." }
        val id = UUID.randomUUID(); val now = clock.now()
        db.focusSessions().insert(FocusSessionEntity(id, taskId, now, null, 0, false, FocusOutcome.Running.name))
        db.activeFocus().insert(ActiveFocusEntity(sessionId = id, targetSeconds = minutes * 60L,
            remainingMillis = minutes * 60_000L, segmentStartedElapsed = clock.elapsedMillis(), bootId = clock.bootId(), checkpointAt = now))
    } }
    override suspend fun pause() = withContext(Dispatchers.IO) { db.withTransaction {
        val row = resolve() ?: return@withTransaction
        if (row.segmentStartedElapsed == null) return@withTransaction
        val left = remaining(row)
        db.activeFocus().update(row.copy(remainingMillis = left, segmentStartedElapsed = null, checkpointAt = clock.now()))
        val history = db.focusSessions().get(row.sessionId)!!
        db.focusSessions().update(history.copy(durationSeconds = (row.targetSeconds * 1000 - left) / 1000, outcome = FocusOutcome.Paused.name))
    } }
    override suspend fun resume() = withContext(Dispatchers.IO) { db.withTransaction {
        val row = resolve() ?: return@withTransaction
        if (row.segmentStartedElapsed != null) return@withTransaction
        db.activeFocus().update(row.copy(segmentStartedElapsed = clock.elapsedMillis(), bootId = clock.bootId(), checkpointAt = clock.now()))
        val history = db.focusSessions().get(row.sessionId)!!
        db.focusSessions().update(history.copy(outcome = FocusOutcome.Running.name))
    } }
    override suspend fun stop() = withContext(Dispatchers.IO) { db.withTransaction {
        val row = resolve() ?: return@withTransaction
        val history = db.focusSessions().get(row.sessionId)!!
        db.focusSessions().update(history.copy(endedAt = maxOf(history.startedAt, clock.now()),
            durationSeconds = (row.targetSeconds * 1000 - remaining(row)) / 1000, outcome = FocusOutcome.Stopped.name))
        db.activeFocus().clear()
    } }
}
