package com.gammac3.ddlapp.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.gammac3.ddlapp.logic.TriggerPoint

/** UI 里可选择的全部提前量（分钟）；到期时刻（0）不计入编辑选择、但计入候选。 */
object CandidateLeads {
    val ALL: List<Long> = listOf(0L, 5L, 15L, 30L, 60L, 180L, 1440L, 4320L, 10080L)
}

class AndroidAlarmScheduler(private val context: Context) : AlarmScheduler {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    override fun reschedule(ddlId: Long, title: String, triggers: List<TriggerPoint>) {
        cancelAll(ddlId)
        for (trigger in triggers) scheduleOne(ddlId, title, trigger)
    }

    override fun cancelAll(ddlId: Long) {
        for (lead in CandidateLeads.ALL) {
            val pi = pendingIntent(ddlId, lead, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: continue
            pi.cancel()
        }
    }

    private fun scheduleOne(ddlId: Long, title: String, trigger: TriggerPoint) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ID, ddlId)
            putExtra(AlarmReceiver.EXTRA_TITLE, title)
            putExtra(AlarmReceiver.EXTRA_LEAD, trigger.leadMinutes)
        }
        val pi = PendingIntent.getBroadcast(
            context, requestCode(ddlId, trigger.leadMinutes), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.triggerAtMillis, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.triggerAtMillis, pi)
        }
    }

    /** 取消搜索时不带 extras：PendingIntent 匹配按组件而非 extras，因此能命中同一 requestCode 的旧 alarm。 */
    private fun pendingIntent(ddlId: Long, lead: Long, flags: Int): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java)
        return PendingIntent.getBroadcast(context, requestCode(ddlId, lead), intent, flags)
    }

    /**
     * 用候选提前量的序号（而非分钟数）编码 requestCode，避免 (ddlId=a, lead=10080)
     * 与 (ddlId=a+99, lead=180) 这类结构性碰撞。序号 1..9 保留给候选集；
     * 非候选 lead 走 fallback 槽位（正常调用方不会传入）。
     */
    private fun requestCode(ddlId: Long, lead: Long): Int {
        val rank = CandidateLeads.ALL.indexOf(lead)
        return if (rank >= 0) ((ddlId + 1) * 100L + rank + 1).toInt()
               else ((ddlId + 1) * 100L + Math.abs(lead % 98) + 2).toInt()
    }
}
