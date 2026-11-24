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
    // DdlParser 从 0.8 起才有明确的截止词和可解析的日期。
    private const val MIN_DEADLINE_CONFIDENCE = 0.8f

    /** 明确的截止日期还需要能配上任务标题，才能跳过截图。 */
    fun needsOcr(candidates: List<DdlCandidate>): Boolean =
        candidates.none {
            it.deadline != null && it.title.isNotBlank() && it.confidence >= MIN_DEADLINE_CONFIDENCE
        }

    /**
     * 合并取词与 OCR 候选：先看截止时间和标题是否完整，再按 confidence 降序。
     * 完整度与置信度相同时优先保留 OCR 候选。
     * 同标题 + 同截止时间（±60 秒）去重；最多 3 条。
     */
    fun merge(node: List<DdlCandidate>, ocr: List<DdlCandidate>): List<DdlCandidate> {
        val sorted = (node + ocr).sortedWith(
            compareByDescending<DdlCandidate> { it.deadline != null }
                .thenByDescending { it.title.isNotBlank() }
                .thenByDescending { it.confidence }
                .thenByDescending { it in ocr }
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
        if (!sameTitle(a.title, b.title)) return false
        val deadlineA = a.deadline?.toInstant()?.toEpochMilli()
        val deadlineB = b.deadline?.toInstant()?.toEpochMilli()
        if (deadlineA == null || deadlineB == null) {
            return deadlineA == null && deadlineB == null
        }
        return abs(deadlineA - deadlineB) <= MERGE_WINDOW_MILLIS
    }

    /** 同标题，或一个标题包含另一个（如 OCR 少读了「【作业】」前缀），都视为同一条。 */
    private fun sameTitle(a: String, b: String): Boolean {
        val left = CaptureDuplicate.normalizeTitle(a)
        val right = CaptureDuplicate.normalizeTitle(b)
        if (left.isEmpty() || right.isEmpty()) return false
        if (left == right) return true
        val shorter = minOf(left.length, right.length)
        return shorter >= 3 && (left.contains(right) || right.contains(left))
    }
}
