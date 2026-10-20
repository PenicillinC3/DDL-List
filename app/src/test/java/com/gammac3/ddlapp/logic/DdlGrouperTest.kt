package com.gammac3.ddlapp.logic

import com.gammac3.ddlapp.model.DdlGroup
import com.gammac3.ddlapp.model.DdlRow
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DdlGrouperTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private fun ms(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    // 「现在」固定为 2026-10-19 12:00（周一）
    private val now = ms(2026, 10, 19, 12)

    private fun row(id: Long, deadline: Long, done: Boolean = false) =
        DdlRow(id, "t$id", "", deadline, emptyList(), done)

    @Test
    fun `已完成的进 DONE`() {
        val result = DdlGrouper.group(listOf(row(1, ms(2026, 10, 20, 9), done = true)), now, zone)
        assertEquals(1, result[DdlGroup.DONE]!!.size)
        assertEquals(0, result[DdlGroup.TODAY]!!.size)
    }

    @Test
    fun `截止时刻已过但仍是今天 → OVERDUE`() {
        val result = DdlGrouper.group(listOf(row(1, ms(2026, 10, 19, 8))), now, zone)
        assertEquals(1, result[DdlGroup.OVERDUE]!!.size)
    }

    @Test
    fun `今天晚些时候 → TODAY（23：59 边界）`() {
        val result = DdlGrouper.group(listOf(row(1, ms(2026, 10, 19, 23, 59))), now, zone)
        assertEquals(1, result[DdlGroup.TODAY]!!.size)
    }

    @Test
    fun `明天 00：00 → TOMORROW`() {
        val result = DdlGrouper.group(listOf(row(1, ms(2026, 10, 20, 0, 1))), now, zone)
        assertEquals(1, result[DdlGroup.TOMORROW]!!.size)
    }

    @Test
    fun `未来第 7 天 23：59 → THIS_WEEK 第 8 天 → LATER`() {
        val inWeek = row(1, ms(2026, 10, 26, 23, 59))
        val beyond = row(2, ms(2026, 10, 27, 0, 1))
        val result = DdlGrouper.group(listOf(inWeek, beyond), now, zone)
        assertEquals(1, result[DdlGroup.THIS_WEEK]!!.size)
        assertEquals(1, result[DdlGroup.LATER]!!.size)
    }

    @Test
    fun `组内按截止时间升序`() {
        val later = row(1, ms(2026, 10, 25, 9))
        val earlier = row(2, ms(2026, 10, 22, 9))
        val result = DdlGrouper.group(listOf(later, earlier), now, zone)
        assertEquals(listOf(2L, 1L), result[DdlGroup.THIS_WEEK]!!.map { it.id })
    }

    @Test
    fun `永远返回六个分组键`() {
        val result = DdlGrouper.group(emptyList(), now, zone)
        assertEquals(6, result.size)
        assertTrue(DdlGroup.entries.all { it in result })
    }

    @Test
    fun `单行组归属 groupOf 与 group 同规则`() {
        assertEquals(DdlGroup.DONE, DdlGrouper.groupOf(row(1, ms(2026, 10, 20, 9), done = true), now, zone))
        assertEquals(DdlGroup.OVERDUE, DdlGrouper.groupOf(row(2, ms(2026, 10, 19, 8)), now, zone))
        assertEquals(DdlGroup.TODAY, DdlGrouper.groupOf(row(3, ms(2026, 10, 19, 23, 59)), now, zone))
        assertEquals(DdlGroup.TOMORROW, DdlGrouper.groupOf(row(4, ms(2026, 10, 20, 0, 1)), now, zone))
        assertEquals(DdlGroup.THIS_WEEK, DdlGrouper.groupOf(row(5, ms(2026, 10, 26, 23, 59)), now, zone))
        assertEquals(DdlGroup.LATER, DdlGrouper.groupOf(row(6, ms(2026, 10, 27, 0, 1)), now, zone))
    }
}
