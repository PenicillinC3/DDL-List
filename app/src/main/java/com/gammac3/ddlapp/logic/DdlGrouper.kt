package com.gammac3.ddlapp.logic

import com.gammac3.ddlapp.model.DdlGroup
import com.gammac3.ddlapp.model.DdlRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object DdlGrouper {
    /** 六组始终齐全；组内按截止时间升序。 */
    fun group(rows: List<DdlRow>, nowMillis: Long, zone: ZoneId): Map<DdlGroup, List<DdlRow>> {
        val out: MutableMap<DdlGroup, MutableList<DdlRow>> =
            DdlGroup.entries.associateWithTo(mutableMapOf()) { mutableListOf() }
        for (row in rows) out.getValue(groupOf(row, nowMillis, zone)).add(row)
        return out.mapValues { (_, list) -> list.sortedBy { it.deadlineMillis } }
    }

    /** 单条行的组归属（与 group 完全同规则；删除后的墓碑槽回插原位用）。 */
    fun groupOf(row: DdlRow, nowMillis: Long, zone: ZoneId): DdlGroup {
        if (row.isDone) return DdlGroup.DONE
        if (row.deadlineMillis < nowMillis) return DdlGroup.OVERDUE
        val today: LocalDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val date = Instant.ofEpochMilli(row.deadlineMillis).atZone(zone).toLocalDate()
        return when {
            date == today -> DdlGroup.TODAY
            date == today.plusDays(1) -> DdlGroup.TOMORROW
            date < today.plusDays(8) -> DdlGroup.THIS_WEEK
            else -> DdlGroup.LATER
        }
    }
}
