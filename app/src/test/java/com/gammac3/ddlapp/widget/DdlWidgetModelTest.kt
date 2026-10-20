package com.gammac3.ddlapp.widget

import com.gammac3.ddlapp.model.DdlRow
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DdlWidgetModel 纯逻辑验收：upcoming 选取、今日/本周计数、静音占位、空态——语义必须与 DdlGrouper 同源。 */
class DdlWidgetModelTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private fun ms(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    // 「现在」固定为 2026-10-20 09:00（周二）
    private val now = ms(2026, 10, 20, 9)

    private fun row(id: Long, deadline: Long, done: Boolean = false, muted: Boolean = false) =
        DdlRow(id, "t$id", "", deadline, emptyList(), done, muted)

    @Test
    fun `最近到期跳过已逾期与已完成`() {
        val model = DdlWidgetModel.build(
            listOf(
                row(1, ms(2026, 10, 20, 8)), // 时间已过 → OVERDUE
                row(2, ms(2026, 10, 20, 23), done = true), // 已完成 → DONE
                row(3, ms(2026, 10, 20, 23)), // 今日 23 点 → 应为最近
            ),
            now, zone,
        )
        assertEquals(3L, model.nearest?.id)
    }

    @Test
    fun `静音条目照常进入最近到期`() {
        val model = DdlWidgetModel.build(listOf(row(1, ms(2026, 10, 20, 23), muted = true)), now, zone)
        assertEquals(1, model.upcoming.size)
        assertEquals(1L, model.nearest?.id)
    }

    @Test
    fun `今日与本周计数对齐分组器`() {
        val model = DdlWidgetModel.build(
            listOf(
                row(1, ms(2026, 10, 20, 8)), // 逾期
                row(2, ms(2026, 10, 20, 23)), // 今日
                row(3, ms(2026, 10, 21, 9)), // 明天
                row(4, ms(2026, 10, 23, 9)), // 本周（后天）
                row(5, ms(2026, 10, 19, 11), done = true), // 已完成 → 不计 active
                row(6, ms(2026, 12, 1, 9)), // 稍后
            ),
            now, zone,
        )
        assertEquals(5, model.activeCount)
        assertEquals(1, model.overdueCount)
        assertEquals(1, model.todayCount)
        assertEquals(2, model.weekCount) // 明天 + 本周
        assertEquals(4, model.upcoming.size)
    }

    @Test
    fun `upcoming 按截止时间升序`() {
        val model = DdlWidgetModel.build(
            listOf(row(1, ms(2026, 10, 23, 9)), row(2, ms(2026, 10, 20, 23))),
            now, zone,
        )
        assertEquals(listOf(2L, 1L), model.upcoming.map { it.id })
    }

    @Test
    fun `空态（无未完成条目）`() {
        val model = DdlWidgetModel.build(emptyList(), now, zone)
        assertTrue(model.upcoming.isEmpty())
        assertEquals(0, model.activeCount)
        assertNull(model.nearest)
    }

    @Test
    fun `全部已逾期时 upcoming 为空但逾期计数保留`() {
        val model = DdlWidgetModel.build(listOf(row(1, ms(2026, 10, 19, 8))), now, zone)
        assertTrue(model.upcoming.isEmpty())
        assertEquals(1, model.overdueCount)
    }

    @Test
    fun `周尺进度与可见性`() {
        val m1 = DdlWidgetModel.build(listOf(row(1, ms(2026, 10, 20, 23))), now, zone)
        assertTrue(m1.rulerVisible)
        assertEquals(23, m1.weekProgressHours)
        val m2 = DdlWidgetModel.build(listOf(row(1, ms(2026, 10, 21, 9))), now, zone)
        assertTrue(m2.rulerVisible)
        assertEquals(33, m2.weekProgressHours)
        val m3 = DdlWidgetModel.build(listOf(row(1, ms(2026, 12, 1, 9))), now, zone)
        assertTrue(!m3.rulerVisible)
        assertEquals(0, m3.weekProgressHours)
    }

    @Test
    fun `周多点按日分布且每日至多两点`() {
        val model = DdlWidgetModel.build(
            listOf(
                row(1, ms(2026, 10, 20, 22)),
                row(2, ms(2026, 10, 20, 23)),
                row(3, ms(2026, 10, 22, 9)),
                row(4, ms(2026, 10, 27, 9)), // 下周一 → 7 日窗外，不进条
            ),
            now, zone,
        )
        assertEquals(listOf(2, 0, 1, 0, 0, 0, 0), model.weekDots)
    }
}
