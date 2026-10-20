package com.gammac3.ddlapp.widget

import com.gammac3.ddlapp.logic.DdlGrouper
import com.gammac3.ddlapp.model.DdlGroup
import com.gammac3.ddlapp.model.DdlRow
import java.time.Instant
import java.time.ZoneId

/**
 * 小组件展示模型：纯逻辑、零 Android 依赖（可单测）。
 * 语义与 DdlGrouper 完全同源：未完成条目分组后取「今日/明天/本周/稍后」为 upcoming（含静音——
 * 静音只是不提醒，桌面上照常占位）。
 * 周尺（单焦点卡装饰）：7 段刻度 = 今日 0 点起 7×24h 线性窗口，进度按小时计（max=168）；
 * 最近截止落在窗口外时整把尺隐藏。
 * 周多点条（多行卡装饰）：今日起 7 天，每天截止条数（每日至多 2 点）。
 */
data class DdlWidgetModel(
    val activeCount: Int,
    val upcoming: List<DdlRow>,
    val overdueCount: Int,
    val todayCount: Int,
    val weekCount: Int,
    val rulerVisible: Boolean = false,
    val weekProgressHours: Int = 0,
    val weekDots: List<Int> = List(7) { 0 },
) {
    val nearest: DdlRow? get() = upcoming.firstOrNull()

    companion object {
        fun build(allActive: List<DdlRow>, nowMillis: Long, zone: ZoneId): DdlWidgetModel {
            val grouped = DdlGrouper.group(allActive, nowMillis, zone)
            val upcoming = listOf(DdlGroup.TODAY, DdlGroup.TOMORROW, DdlGroup.THIS_WEEK, DdlGroup.LATER)
                .flatMap { grouped.getValue(it) }
                .sortedBy { it.deadlineMillis }
            var rulerVisible = false
            var weekProgressHours = 0
            val nearest = upcoming.firstOrNull()
            if (nearest != null) {
                val startToday = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
                    .atStartOfDay(zone).toInstant().toEpochMilli()
                val weekEnd = startToday + 7 * 86_400_000L
                rulerVisible = nearest.deadlineMillis < weekEnd
                if (rulerVisible) {
                    weekProgressHours = ((nearest.deadlineMillis - startToday) / 3_600_000L)
                        .toInt().coerceIn(0, 168)
                }
            }
            val todayDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            val dots = List(7) { 0 }.toMutableList()
            for (row in upcoming) {
                val idx = Instant.ofEpochMilli(row.deadlineMillis).atZone(zone).toLocalDate().toEpochDay() -
                    todayDate.toEpochDay()
                if (idx in 0..6) {
                    val i = idx.toInt()
                    dots[i] = (dots[i] + 1).coerceAtMost(2)
                }
            }
            return DdlWidgetModel(
                activeCount = allActive.count { !it.isDone },
                upcoming = upcoming,
                overdueCount = grouped.getValue(DdlGroup.OVERDUE).size,
                todayCount = grouped.getValue(DdlGroup.TODAY).size,
                weekCount = grouped.getValue(DdlGroup.TOMORROW).size + grouped.getValue(DdlGroup.THIS_WEEK).size,
                rulerVisible = rulerVisible,
                weekProgressHours = weekProgressHours,
                weekDots = dots,
            )
        }
    }
}
