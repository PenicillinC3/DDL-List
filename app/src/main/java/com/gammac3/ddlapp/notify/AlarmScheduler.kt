package com.gammac3.ddlapp.notify

import com.gammac3.ddlapp.logic.TriggerPoint

interface AlarmScheduler {
    /** 立即重排（内部应先 cancel 再排未来触发点）。 */
    fun reschedule(ddlId: Long, title: String, triggers: List<TriggerPoint>)

    /** 撤销该 DDL 的全部候选触发点。 */
    fun cancelAll(ddlId: Long)
}
