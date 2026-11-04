package com.gammac3.ddlapp.notify

import android.content.Context
import com.gammac3.ddlapp.R

/** 提前量 → 展示文案（「5 分钟」/"5 min"），随系统语言切换。 */
object LeadLabels {
    fun text(context: Context, minutes: Long): String {
        val res = context.resources
        return when (minutes) {
            5L -> res.getString(R.string.lead_5); 15L -> res.getString(R.string.lead_15)
            30L -> res.getString(R.string.lead_30); 60L -> res.getString(R.string.lead_60)
            180L -> res.getString(R.string.lead_180)
            1440L -> res.getString(R.string.lead_1440)
            4320L -> res.getString(R.string.lead_4320)
            10080L -> res.getString(R.string.lead_10080)
            else -> res.getString(R.string.lead_other, minutes)
        }
    }
}
