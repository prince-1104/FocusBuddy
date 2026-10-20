package com.focusbuddy.domain.calendar

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Date-based RRULE expansion. Unsupported fields fail explicitly instead of inventing counts. */
object Recurrence {
    fun dates(anchor: LocalDate, rule: String?, from: LocalDate, through: LocalDate): List<LocalDate> {
        if (rule == null) return listOf(anchor).filter { it in from..through }
        val fields = rule.removePrefix("RRULE:").uppercase().split(';').associate {
            val pair = it.split('=', limit = 2); require(pair.size == 2) { "Invalid recurrence rule" }; pair[0] to pair[1]
        }
        require(fields.keys.all { it in setOf("FREQ", "INTERVAL", "COUNT", "UNTIL", "BYDAY", "BYMONTHDAY", "BYMONTH", "WKST") }) { "Unsupported recurrence field" }
        val frequency = fields["FREQ"]
        require(frequency in setOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY")) { "Unsupported recurrence frequency" }
        val interval = fields["INTERVAL"]?.toLong() ?: 1L
        val limit = fields["COUNT"]?.toInt() ?: Int.MAX_VALUE
        require(interval > 0 && limit > 0)
        require(fields["COUNT"] == null || fields["UNTIL"] == null)
        val until = fields["UNTIL"]?.take(8)?.let { LocalDate.parse(it, DateTimeFormatter.BASIC_ISO_DATE) } ?: through
        val weekdays = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
        val weekStart = fields["WKST"]?.let { require(it in weekdays); DayOfWeek.of(weekdays.indexOf(it) + 1) } ?: DayOfWeek.MONDAY
        val byDays = fields["BYDAY"]?.split(',')?.map {
            val code = it.takeLast(2); require(code in weekdays)
            val ordinal = it.dropLast(2).takeIf(String::isNotEmpty)?.toInt()
            require(ordinal == null || (ordinal != 0 && ordinal in -53..53 && frequency in setOf("MONTHLY", "YEARLY") && (frequency != "MONTHLY" || ordinal in -5..5)))
            ordinal to DayOfWeek.of(weekdays.indexOf(code) + 1)
        }
        val monthDays = fields["BYMONTHDAY"]?.split(',')?.map { it.toInt().also { n -> require(n != 0 && n in -31..31) } }
        require(frequency != "WEEKLY" || monthDays == null) { "BYMONTHDAY is invalid for weekly rules" }
        val months = fields["BYMONTH"]?.split(',')?.map { it.toInt().also { n -> require(n in 1..12) } }
        fun week(d: LocalDate) = d.minusDays(((d.dayOfWeek.value - weekStart.value + 7) % 7).toLong())
        val end = minOf(through, until)
        var date = if (fields["COUNT"] == null) maxOf(anchor, from) else anchor
        var count = 0
        val result = mutableListOf<LocalDate>()
        while (date <= end && count < limit) {
            val period = when (frequency) {
                "DAILY" -> ChronoUnit.DAYS.between(anchor, date)
                "WEEKLY" -> ChronoUnit.WEEKS.between(week(anchor), week(date))
                "MONTHLY" -> ChronoUnit.MONTHS.between(YearMonth.from(anchor), YearMonth.from(date))
                else -> (date.year - anchor.year).toLong()
            }
            val dayMatch = byDays?.any { (ordinal, day) ->
                val position = if (frequency == "YEARLY" && months == null) date.dayOfYear else date.dayOfMonth
                val length = if (frequency == "YEARLY" && months == null) date.lengthOfYear() else date.lengthOfMonth()
                date.dayOfWeek == day && (ordinal == null ||
                    if (ordinal > 0) (position - 1) / 7 + 1 == ordinal
                    else -((length - position) / 7 + 1) == ordinal)
            } ?: when (frequency) {
                "WEEKLY" -> date.dayOfWeek == anchor.dayOfWeek
                "MONTHLY", "YEARLY" -> monthDays != null || date.dayOfMonth == anchor.dayOfMonth
                else -> true
            }
            val monthMatch = months?.contains(date.monthValue) ?: (frequency != "YEARLY" || byDays != null || monthDays != null || date.month == anchor.month)
            val monthDayMatch = monthDays?.any { if (it > 0) date.dayOfMonth == it else date.dayOfMonth == date.lengthOfMonth() + it + 1 } ?: true
            if (period % interval == 0L && dayMatch && monthMatch && monthDayMatch) {
                count++
                if (date >= from) result += date
            }
            date = date.plusDays(1)
        }
        return result.distinct()
    }
}

/** Null cells preserve conventional month boundaries for every week-start preference. */
fun monthCells(month: YearMonth, firstDay: DayOfWeek): List<LocalDate?> {
    val offset = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
    val size = ((offset + month.lengthOfMonth() + 6) / 7) * 7
    return List(size) { index -> (index - offset + 1).takeIf { it in 1..month.lengthOfMonth() }?.let(month::atDay) }
}
