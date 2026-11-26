package com.gammac3.ddlapp.logic

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DdlStatisticsTest {
    private val zone = ZoneId.of("America/Los_Angeles")

    @Test
    fun `counts completion events by local week month and year`() {
        val today = LocalDate.of(2026, 11, 26)
        val events = listOf(
            today.atTime(0, 15).atZone(zone).toInstant().toEpochMilli(),
            today.minusDays(2).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
            today.minusDays(7).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
            today.minusMonths(1).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
            today.minusYears(1).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        )

        val stats = DdlStatistics.calculate(events, today, zone)

        assertEquals(2, stats.weekCount)
        assertEquals(3, stats.monthCount)
        assertEquals(4, stats.yearCount)
        assertEquals(1, stats.days.getValue(today))
        assertEquals(1, stats.days.getValue(today.minusDays(2)))
        assertEquals(0, stats.days.getValue(today.minusDays(1)))
    }

    @Test
    fun `includes the complete current calendar year in heatmap`() {
        val today = LocalDate.of(2024, 2, 29)

        val stats = DdlStatistics.calculate(emptyList(), today, zone)

        assertEquals(366, stats.days.size)
        assertEquals(0, stats.yearCount)
        assertEquals(0, stats.weekCount)
        assertEquals(0, stats.monthCount)
    }
}
