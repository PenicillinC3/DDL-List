package com.gammac3.ddlapp.ui

import android.content.Context
import com.gammac3.ddlapp.R
import com.gammac3.ddlapp.notify.LeadLabels
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 本地化格式器：文案/模式全部出自资源（values 默认中文、values-en 英文），
 *  系统语言切换后由资源管线自动生效；context 取调用方（Compose 用 LocalContext，
 *  小组件用 applicationContext——均跟随系统 locale）。 */
object Format {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun locale(context: Context): Locale =
        context.resources.configuration.locales[0]

    fun absolute(context: Context, millis: Long): String {
        val z = Instant.ofEpochMilli(millis).atZone(zone)
        val pattern = context.getString(R.string.fmt_abs_date_time)
        return z.format(DateTimeFormatter.ofPattern(pattern, locale(context)))
    }

    /** 「剩3天14:22」式倒计时 / 「已过期2小时」，1 分钟跳变。中文不插空格（R35）；
     *  英文按英文习惯读（"3d 14:22 left" / "2h 5m overdue"）。 */
    fun remaining(context: Context, deadlineMillis: Long, nowMillis: Long): String {
        val res = context.resources
        val diff = deadlineMillis - nowMillis
        val past = diff < 0
        var d = Math.abs(diff)
        val days = d / 86_400_000L; d -= days * 86_400_000L
        val hours = d / 3_600_000L; d -= hours * 3_600_000L
        val minutes = d / 60_000L
        val hhmm = "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
        val body = when {
            days > 0 -> res.getString(R.string.fmt_cd_body_d, days, hhmm)
            hours > 0 -> res.getString(R.string.fmt_cd_body_h, hours, minutes)
            minutes > 0 -> res.getString(R.string.fmt_cd_body_m, minutes)
            else -> res.getString(R.string.fmt_cd_body_soon)
        }
        return res.getString(
            if (past) R.string.fmt_cd_past else R.string.fmt_cd_future,
            body,
        )
    }

    fun leadText(context: Context, minutes: Long): String = LeadLabels.text(context, minutes)
}
