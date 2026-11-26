package com.gammac3.ddlapp.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class DdlStatisticsSummary(
    val weekCount: Int,
    val monthCount: Int,
    val yearCount: Int,
    val days: Map<LocalDate, Int>,
)

object DdlStatistics {
    fun calculate(
        completionTimes: List<Long>,
        today: LocalDate,
        zone: ZoneId,
    ): DdlStatisticsSummary {
        val yearStart = today.withDayOfYear(1)
        val days = (0 until today.lengthOfYear()).associate { offset ->
            yearStart.plusDays(offset.toLong()) to 0
        }.toMutableMap()
        var weekCount = 0
        var monthCount = 0
        var yearCount = 0
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        completionTimes.forEach { completedAt ->
            val date = java.time.Instant.ofEpochMilli(completedAt).atZone(zone).toLocalDate()
            if (date.isAfter(today)) return@forEach
            if (!date.isBefore(weekStart)) weekCount++
            if (date.year == today.year && date.month == today.month) monthCount++
            if (date.year == today.year) {
                yearCount++
                days[date] = days.getValue(date) + 1
            }
        }
        return DdlStatisticsSummary(weekCount, monthCount, yearCount, days)
    }
}
