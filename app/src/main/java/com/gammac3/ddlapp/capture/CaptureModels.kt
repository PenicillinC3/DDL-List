package com.gammac3.ddlapp.capture

import java.time.ZonedDateTime

/** 一条解析候选：标题 + 截止时间；deadline 为 null 表示没识别到时间。 */
data class DdlCandidate(
    val title: String,
    val deadline: ZonedDateTime?,
    val confidence: Float,
    val sourceLine: String,
)

internal data class OcrText(val text: String, val lines: List<OcrLine>)

internal data class OcrLine(val text: String, val block: Int, val bounds: OcrBounds?)

internal data class OcrBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** 一次抓屏的进程内载荷；不落盘、不打日志。 */
data class CapturePayload(
    val candidates: List<DdlCandidate>,
    val capturedAtMillis: Long,
)
