package com.gammac3.ddlapp.capture

import com.gammac3.ddlapp.data.DdlEntity
import kotlin.math.abs

/** 重复判定：同标题（归一化后）+ 截止时间差 ≤ 60 秒。 */
object CaptureDuplicate {

    private val NOISE = Regex("""[\s，,。.;；:：!！?？、"'“”‘’（）()\[\]【】]""")

    fun normalizeTitle(title: String): String = title.lowercase().replace(NOISE, "")

    fun isDuplicate(
        existing: List<DdlEntity>,
        title: String,
        deadlineMillis: Long,
    ): Boolean {
        val key = normalizeTitle(title)
        if (key.isEmpty()) return false
        return existing.any { entity ->
            normalizeTitle(entity.title) == key &&
                abs(entity.deadlineEpochMillis - deadlineMillis) <= 60_000L
        }
    }
}
