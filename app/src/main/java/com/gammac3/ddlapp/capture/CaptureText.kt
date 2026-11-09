package com.gammac3.ddlapp.capture

import kotlin.math.abs

/**
 * 节点取词与 OCR 候选的纯 Kotlin 合并逻辑。
 *
 * 约束：只在内存中筛选候选，不依赖 Android；不读取、不保存、不输出页面原文。
 */
object CaptureText {

    private const val MAX_CANDIDATES = 3
    private const val MERGE_WINDOW_MILLIS = 60_000L

    /** 没有任何带截止时间的候选时，才值得再截一次图做 OCR。 */
    fun needsOcr(candidates: List<DdlCandidate>): Boolean =
        candidates.none { it.deadline != null }

    /**
     * 合并取词与 OCR 候选：带截止时间优先，其次 confidence 降序；
     * 同标题 + 同截止时间（±60 秒）去重；最多 3 条。
     */
    fun merge(node: List<DdlCandidate>, ocr: List<DdlCandidate>): List<DdlCandidate> {
        val sorted = (node + ocr).sortedWith(
            compareByDescending<DdlCandidate> { it.deadline != null }
                .thenByDescending { it.confidence }
                .thenBy { it.deadline?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE },
        )
        val result = ArrayList<DdlCandidate>(MAX_CANDIDATES)
        for (candidate in sorted) {
            if (result.any { sameItem(it, candidate) }) continue
            result += candidate
            if (result.size == MAX_CANDIDATES) break
        }
        return result
    }

    private fun sameItem(a: DdlCandidate, b: DdlCandidate): Boolean {
        val title = CaptureDuplicate.normalizeTitle(a.title)
        if (title.isEmpty() || title != CaptureDuplicate.normalizeTitle(b.title)) return false
        val deadlineA = a.deadline?.toInstant()?.toEpochMilli()
        val deadlineB = b.deadline?.toInstant()?.toEpochMilli()
        if (deadlineA == null || deadlineB == null) {
            return deadlineA == null && deadlineB == null
        }
        return abs(deadlineA - deadlineB) <= MERGE_WINDOW_MILLIS
    }
}
