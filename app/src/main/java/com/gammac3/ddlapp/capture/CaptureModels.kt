package com.gammac3.ddlapp.capture

import java.time.ZonedDateTime

/** 一条解析候选：标题 + 截止时间；deadline 为 null 表示没识别到时间。 */
data class DdlCandidate(
    val title: String,
    val deadline: ZonedDateTime?,
    val confidence: Float,
    val sourceLine: String,
)

/** 一次抓屏的进程内载荷；不落盘、不打日志。 */
data class CapturePayload(
    val candidates: List<DdlCandidate>,
    val capturedAtMillis: Long,
)
