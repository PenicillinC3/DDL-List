package com.gammac3.ddlapp.widget

import com.gammac3.ddlapp.logic.DdlGrouper
import com.gammac3.ddlapp.model.DdlGroup
import com.gammac3.ddlapp.model.DdlRow
import java.time.ZoneId

/**
 * 小组件展示模型：纯逻辑、零 Android 依赖（可单测）。
 * 语义与 DdlGrouper 完全同源：未完成条目分组后取「今日/明天/本周/稍后」为 upcoming（含静音——
 * 静音只是不提醒，桌面上照常占位）；翔实文本（倒计时/日期串）由 DdlWidgets 渲染面生成。
 */
data class DdlWidgetModel(
    val activeCount: Int,
    val upcoming: List<DdlRow>,
    val overdueCount: Int,
    val todayCount: Int,
    val weekCount: Int,
) {
    val nearest: DdlRow? get() = upcoming.firstOrNull()

    companion object {
        fun build(allActive: List<DdlRow>, nowMillis: Long, zone: ZoneId): DdlWidgetModel {
            val grouped = DdlGrouper.group(allActive, nowMillis, zone)
            val upcoming = listOf(DdlGroup.TODAY, DdlGroup.TOMORROW, DdlGroup.THIS_WEEK, DdlGroup.LATER)
                .flatMap { grouped.getValue(it) }
                .sortedBy { it.deadlineMillis }
            return DdlWidgetModel(
                activeCount = allActive.count { !it.isDone },
                upcoming = upcoming,
                overdueCount = grouped.getValue(DdlGroup.OVERDUE).size,
                todayCount = grouped.getValue(DdlGroup.TODAY).size,
                weekCount = grouped.getValue(DdlGroup.TOMORROW).size + grouped.getValue(DdlGroup.THIS_WEEK).size,
            )
        }
    }
}
