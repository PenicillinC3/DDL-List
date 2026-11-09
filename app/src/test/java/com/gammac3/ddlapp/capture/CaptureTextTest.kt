package com.gammac3.ddlapp.capture

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureTextTest {

    private val base = ZonedDateTime.of(2026, 11, 9, 12, 0, 0, 0, ZoneId.of("UTC"))

    private fun candidate(
        title: String,
        minutes: Long? = null,
        confidence: Float = 0.5f,
    ) = DdlCandidate(
        title = title,
        deadline = minutes?.let { base.plusMinutes(it) },
        confidence = confidence,
        sourceLine = "",
    )

    @Test
    fun `empty candidates need ocr`() {
        assertTrue(CaptureText.needsOcr(emptyList()))
    }

    @Test
    fun `title only candidate needs ocr`() {
        assertTrue(CaptureText.needsOcr(listOf(candidate("第三章习题"))))
    }

    @Test
    fun `candidate with deadline skips ocr`() {
        assertFalse(CaptureText.needsOcr(listOf(candidate("第三章习题", 60))))
    }

    @Test
    fun `merge of empty lists is empty`() {
        assertEquals(emptyList<DdlCandidate>(), CaptureText.merge(emptyList(), emptyList()))
    }

    @Test
    fun `deadline candidates rank before title only`() {
        val merged = CaptureText.merge(
            listOf(candidate("只有标题", confidence = 0.9f)),
            listOf(candidate("有截止", 60, 0.5f)),
        )
        assertEquals("有截止", merged.first().title)
    }

    @Test
    fun `same title and deadline dedupes`() {
        val merged = CaptureText.merge(
            listOf(candidate("第三章习题", 60)),
            listOf(candidate("第三章 习题！", 61)),
        )
        assertEquals(1, merged.size)
    }

    @Test
    fun `contained title and deadline dedupes`() {
        val merged = CaptureText.merge(
            listOf(candidate("【作业】第三章习题", 60)),
            listOf(candidate("第三章习题", 61)),
        )
        assertEquals(1, merged.size)
    }

    @Test
    fun `same title different deadline is kept`() {
        val merged = CaptureText.merge(
            listOf(candidate("第三章习题", 60)),
            listOf(candidate("第三章习题", 120)),
        )
        assertEquals(2, merged.size)
    }

    @Test
    fun `title only duplicates dedupe`() {
        val merged = CaptureText.merge(
            listOf(candidate("海报标题")),
            listOf(candidate("海报 标题")),
        )
        assertEquals(1, merged.size)
    }

    @Test
    fun `blank titles are not deduped`() {
        val merged = CaptureText.merge(
            listOf(candidate("")),
            listOf(candidate("")),
        )
        assertEquals(2, merged.size)
    }

    @Test
    fun `merge keeps at most three`() {
        val merged = CaptureText.merge(
            listOf(
                candidate("A", 60),
                candidate("B", 120),
                candidate("C", 180),
            ),
            listOf(candidate("D", 240)),
        )
        assertEquals(3, merged.size)
    }
}
