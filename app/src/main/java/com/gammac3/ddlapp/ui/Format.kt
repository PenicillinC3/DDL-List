package com.gammac3.ddlapp.ui

import com.gammac3.ddlapp.notify.LeadLabels
import java.time.Instant
import java.time.ZoneId

object Format {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun absolute(millis: Long): String {
        val z = Instant.ofEpochMilli(millis).atZone(zone)
        val h = z.hour.toString().padStart(2, '0')
        val m = z.minute.toString().padStart(2, '0')
        return "${z.monthValue}月${z.dayOfMonth}日 $h:$m"
    }

    /** 「剩3天14:22」式倒计时 / 「已过期2小时」，1 分钟跳变。不插空格：Maple 等宽空格占 0.6em，会让数字在「剩」与单位之间偏右（R35）。 */
    fun remaining(deadlineMillis: Long, nowMillis: Long): String {
        val diff = deadlineMillis - nowMillis
        val past = diff < 0
        var d = Math.abs(diff)
        val days = d / 86_400_000L; d -= days * 86_400_000L
        val hours = d / 3_600_000L; d -= hours * 3_600_000L
        val minutes = d / 60_000L
        val body = when {
            days > 0 -> "${days}天${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
            hours > 0 -> "${hours}小时${minutes}分"
            minutes > 0 -> "${minutes}分钟"
            else -> "1分钟内"
        }
        return if (past) "已过期$body" else "剩$body"
    }

    fun leadText(minutes: Long): String = LeadLabels.text(minutes)
}
