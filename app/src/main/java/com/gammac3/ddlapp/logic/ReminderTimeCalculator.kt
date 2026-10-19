package com.gammac3.ddlapp.logic

data class TriggerPoint(val leadMinutes: Long, val triggerAtMillis: Long)

object ReminderTimeCalculator {
    /** 返回仍需排期的触发点：所有提前量 + 到期时刻本身（lead=0），过滤已过去时刻，去重并升序。 */
    fun futureTriggers(deadlineMillis: Long, leadsMinutes: List<Long>, nowMillis: Long): List<TriggerPoint> =
        (leadsMinutes + 0L).distinct()
            .map { TriggerPoint(it, deadlineMillis - it * 60_000L) }
            .filter { it.triggerAtMillis > nowMillis }
            .sortedBy { it.triggerAtMillis }
}
