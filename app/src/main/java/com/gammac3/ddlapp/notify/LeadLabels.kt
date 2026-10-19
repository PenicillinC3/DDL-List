package com.gammac3.ddlapp.notify

object LeadLabels {
    fun text(minutes: Long): String = when (minutes) {
        5L -> "5 分钟"; 15L -> "15 分钟"; 30L -> "30 分钟"
        60L -> "1 小时"; 180L -> "3 小时"
        1440L -> "1 天"; 4320L -> "3 天"; 10080L -> "7 天"
        else -> "$minutes 分钟"
    }
}
