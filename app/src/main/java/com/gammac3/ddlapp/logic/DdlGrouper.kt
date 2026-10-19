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
        val today: LocalDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        for (row in rows) {
            val target = when {
                row.isDone -> DdlGroup.DONE
                row.deadlineMillis < nowMillis -> DdlGroup.OVERDUE
                else -> {
                    val date = Instant.ofEpochMilli(row.deadlineMillis).atZone(zone).toLocalDate()
                    when {
                        date == today -> DdlGroup.TODAY
                        date == today.plusDays(1) -> DdlGroup.TOMORROW
                        date < today.plusDays(8) -> DdlGroup.THIS_WEEK
                        else -> DdlGroup.LATER
                    }
                }
            }
            out.getValue(target).add(row)
        }
        return out.mapValues { (_, list) -> list.sortedBy { it.deadlineMillis } }
    }
}
