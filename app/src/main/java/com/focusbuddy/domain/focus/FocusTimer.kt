package com.focusbuddy.domain.focus

import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface FocusClock {
    fun now(): Instant
    fun elapsedMillis(): Long
    fun bootId(): String
}
data class ActiveFocus(val id: UUID, val taskId: UUID?, val targetSeconds: Long,
    val remainingMillis: Long, val running: Boolean) {
    val progress: Float get() = (1f - remainingMillis.toFloat() / (targetSeconds * 1000)).coerceIn(0f, 1f)
}
interface FocusTimerRepository {
    fun observe(): Flow<ActiveFocus?>
    suspend fun snapshot(): ActiveFocus?
    suspend fun start(minutes: Int, taskId: UUID?)
    suspend fun pause()
    suspend fun resume()
    suspend fun stop()
}
