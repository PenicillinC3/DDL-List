package com.gammac3.ddlapp.notify

import com.gammac3.ddlapp.logic.TriggerPoint

interface AlarmScheduler {
    /** 立即重排（内部应先 cancel 再排未来触发点）。 */
    fun reschedule(ddlId: Long, title: String, triggers: List<TriggerPoint>)

    /** 撤销该 DDL 的全部候选触发点。 */
    fun cancelAll(ddlId: Long)

    /** 当前是否可排精确闹钟（Android 12+ 需要用户授权）；UI 横幅据此提示。 */
    fun canScheduleExact(): Boolean
}
