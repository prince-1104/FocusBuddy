package com.focusbuddy.domain.time

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.time.Clock
import java.time.ZonedDateTime

interface MomentSource { val moments: Flow<ZonedDateTime> }

class SystemMomentSource(private val clock: Clock, timeZones: TimeZoneSource) : MomentSource {
    @OptIn(ExperimentalCoroutinesApi::class)
    override val moments = timeZones.zones.flatMapLatest { zone ->
        flow {
            while (currentCoroutineContext().isActive) {
                emit(clock.instant().atZone(zone))
                delay(30_000) // Collected only while the dashboard is subscribed.
            }
        }
    }
}
