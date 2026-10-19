package com.gammac3.ddlapp.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderTimeCalculatorTest {

    private val now = 200_000_000_000L // 任意「现在」

    @Test
    fun `到期时刻本身被排入（lead 0）`() {
        val deadline = now + 10 * 60_000L
        val triggers = ReminderTimeCalculator.futureTriggers(deadline, emptyList(), now)
        assertEquals(listOf(TriggerPoint(0L, deadline)), triggers)
    }

    @Test
    fun `提前量按 deadline 减去 minute 数换算`() {
        val deadline = now + 10 * 60_000L
        val triggers = ReminderTimeCalculator.futureTriggers(deadline, listOf(5L), now)
        // 到期时刻（lead=0）始终一并排入，与接口说明及本类其余用例一致
        assertEquals(
            listOf(TriggerPoint(5L, deadline - 5 * 60_000L), TriggerPoint(0L, deadline)),
            triggers,
        )
    }

    @Test
    fun `过去时刻被过滤`() {
        val deadline = now + 3 * 60_000L // 3 分钟后到期
        val triggers = ReminderTimeCalculator.futureTriggers(deadline, listOf(5L, 2L), now)
        // lead=5 已在过去；lead=2 与 lead=0 仍在未来
        assertEquals(false, triggers.any { it.leadMinutes == 5L })
        assertEquals(2, triggers.size)
    }

    @Test
    fun `重复提前量去重`() {
        val deadline = now + 30 * 60_000L
        val triggers = ReminderTimeCalculator.futureTriggers(deadline, listOf(10L, 10L), now)
        assertEquals(2, triggers.size) // lead=10 + lead=0
    }

    @Test
    fun `按触发时间升序`() {
        val deadline = now + (3 * 24 * 60 + 30) * 60_000L
        val triggers = ReminderTimeCalculator.futureTriggers(deadline, listOf(1440L, 60L, 5L), now)
        assertTrue(triggers.zipWithNext().all { (a, b) -> a.triggerAtMillis < b.triggerAtMillis })
        assertEquals(0L, triggers.last().leadMinutes)
    }

    @Test
    fun `全部提前量都在过去时只剩到期时刻`() {
        val deadline = now + 60_000L
        val triggers = ReminderTimeCalculator.futureTriggers(deadline, listOf(60L), now)
        assertFalse(triggers.any { it.leadMinutes == 60L })
        assertTrue(triggers.any { it.leadMinutes == 0L })
    }
}
