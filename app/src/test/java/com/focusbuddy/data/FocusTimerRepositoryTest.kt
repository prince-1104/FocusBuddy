package com.focusbuddy.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focusbuddy.data.focus.RoomFocusTimerRepository
import com.focusbuddy.data.local.FocusBuddyDatabase
import com.focusbuddy.data.repository.*
import com.focusbuddy.domain.focus.FocusClock
import com.focusbuddy.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = com.focusbuddy.TestApplication::class)
class FocusTimerRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "focus-qa-${UUID.randomUUID()}.db"
    private var db = FocusBuddyDatabase.create(context, name)
    private val clock = FakeFocusClock()
    private val timer get() = RoomFocusTimerRepository(db, clock)
    private val history get() = RoomFocusSessionRepository(db)
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }

    @Test fun startPauseResumeAndStopAccumulateOnlyFocusTimeAndAreIdempotent() = runTest {
        timer.start(25, null)
        clock.advance(12_300)
        timer.pause(); timer.pause()
        val paused = timer.snapshot()!!
        assertFalse(paused.running); assertEquals(1_487_700L, paused.remainingMillis)
        clock.advance(90_000)
        assertEquals(paused, timer.snapshot())
        timer.resume(); timer.resume(); clock.advance(7_800)
        timer.stop(); timer.stop()
        val row = history.observeAll().first().single()
        assertEquals(20L, row.durationSeconds); assertEquals(FocusOutcome.Stopped, row.outcome)
        assertNotNull(row.endedAt); assertFalse(row.completed); assertNull(timer.snapshot())
    }
    @Test fun runningAndPausedSessionsSurviveRepositoryAndDatabaseRecreation() = runTest {
        timer.start(45, null); clock.advance(15_000)
        val id = timer.snapshot()!!.id
        db.close(); db = FocusBuddyDatabase.create(context, name)
        assertEquals(id, timer.snapshot()!!.id)
        assertEquals(2_685_000L, timer.snapshot()!!.remainingMillis)
        timer.pause()
        db.close(); db = FocusBuddyDatabase.create(context, name)
        clock.advance(100_000)
        assertFalse(timer.snapshot()!!.running)
        assertEquals(2_685_000L, timer.snapshot()!!.remainingMillis)
    }
    @Test fun completionCatchesUpWithoutACollectorAndNeverCreatesDuplicateHistory() = runTest {
        timer.start(1, null); clock.advance(80_000)
        assertNull(timer.snapshot()); assertNull(timer.snapshot())
        val row = history.observeAll().first().single()
        assertTrue(row.completed); assertEquals(60L, row.durationSeconds)
        assertEquals(FocusOutcome.Completed, row.outcome)
        assertEquals(Instant.parse("2026-10-20T10:01:00Z"), row.endedAt)
    }
    @Test fun manualClockChangesDoNotChangeRemainingTime() = runTest {
        timer.start(60, null); clock.advance(20_000)
        clock.wall = clock.wall.minus(Duration.ofDays(3))
        assertEquals(3_580_000L, timer.snapshot()!!.remainingMillis)
        timer.stop()
        assertEquals(20L, history.observeAll().first().single().durationSeconds)
        assertTrue(history.observeAll().first().single().endedAt!! >= history.observeAll().first().single().startedAt)
    }
    @Test fun rebootInterruptsRunningSessionAtLastCheckpointButPreservesPausedSessions() = runTest {
        timer.start(25, null); clock.advance(12_300); timer.pause(); clock.advance(20_000); timer.resume()
        clock.advance(10_000); clock.boot = "new-boot"; clock.elapsed = 0
        assertNull(timer.snapshot())
        val interrupted = history.observeAll().first().single()
        assertEquals(FocusOutcome.Interrupted, interrupted.outcome); assertEquals(12L, interrupted.durationSeconds)
        timer.start(1, null); clock.advance(500); timer.pause()
        clock.boot = "another-boot"; clock.elapsed = 0
        assertFalse(timer.snapshot()!!.running)
        assertEquals(59_500L, timer.snapshot()!!.remainingMillis)
        timer.resume(); clock.advance(59_500); assertNull(timer.snapshot())
        assertTrue(history.observeAll().first().first().completed)
    }
    @Test fun invalidDurationAndDeletedAssociationNeverCreateInvalidSessions() = runTest {
        for (duration in listOf(0, -1, 241)) {
            assertTrue(runCatching { timer.start(duration, null) }.exceptionOrNull() is IllegalArgumentException)
        }
        assertTrue(runCatching { timer.start(25, UUID.randomUUID()) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(history.observeAll().first().isEmpty())
        val tasks = RoomTaskRepository(db, Clock.systemUTC())
        val task = tasks.create(Task(title = "A real task", scheduledDate = LocalDate.of(2026, 10, 20)))
        timer.start(25, task.id); tasks.delete(task.id)
        assertNull(timer.snapshot()!!.taskId)
        timer.stop(); assertNull(history.observeAll().first().single().taskId)
    }
    @Test fun simultaneousStartsCreateExactlyOneActiveSessionAndHistoryRow() = runTest {
        val results = listOf(async(Dispatchers.IO) { runCatching { timer.start(25, null) } }, async(Dispatchers.IO) { runCatching { timer.start(45, null) } }).awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, history.observeAll().first().size); assertNotNull(timer.snapshot())
    }
}
class FakeFocusClock : FocusClock {
    var wall: Instant = Instant.parse("2026-10-20T10:00:00Z")
    var elapsed: Long = 10_000
    var boot: String = "first-boot"
    override fun now() = wall
    override fun elapsedMillis() = elapsed
    override fun bootId() = boot
    fun advance(millis: Long) { wall = wall.plusMillis(millis); elapsed += millis }
}
