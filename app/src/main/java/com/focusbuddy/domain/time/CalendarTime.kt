package com.focusbuddy.domain.time

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

interface TimeZoneSource { val zones: Flow<ZoneId> }

/** End is exclusive. Zone rules, rather than fixed 24-hour arithmetic, handle DST. */
data class MonthWindow(val start: Instant, val endExclusive: Instant) {
    companion object {
        fun of(month: YearMonth, zone: ZoneId) = MonthWindow(
            month.atDay(1).atStartOfDay(zone).toInstant(),
            month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant(),
        )
    }
}
