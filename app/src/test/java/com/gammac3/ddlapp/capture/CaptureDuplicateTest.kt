package com.gammac3.ddlapp.capture

import com.gammac3.ddlapp.data.DdlEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureDuplicateTest {

    private fun entity(title: String, deadline: Long) = DdlEntity(
        id = 1,
        title = title,
        note = "",
        deadlineEpochMillis = deadline,
        leadsMinutes = emptyList(),
        createdAt = 0,
    )

    @Test
    fun `same title and deadline is duplicate`() {
        val existing = listOf(entity("第三章习题", 1_000_000L))
        assertTrue(CaptureDuplicate.isDuplicate(existing, "第三章习题", 1_000_000L))
    }

    @Test
    fun `deadline within one minute is duplicate`() {
        val existing = listOf(entity("第三章习题", 1_000_000L))
        assertTrue(CaptureDuplicate.isDuplicate(existing, "第三章习题", 1_060_000L))
    }

    @Test
    fun `deadline beyond one minute is not duplicate`() {
        val existing = listOf(entity("第三章习题", 1_000_000L))
        assertFalse(CaptureDuplicate.isDuplicate(existing, "第三章习题", 1_061_000L))
    }

    @Test
    fun `different title is not duplicate`() {
        val existing = listOf(entity("第三章习题", 1_000_000L))
        assertFalse(CaptureDuplicate.isDuplicate(existing, "第四章习题", 1_000_000L))
    }

    @Test
    fun `punctuation and whitespace are normalized`() {
        val existing = listOf(entity("第三章 习题！", 1_000_000L))
        assertTrue(CaptureDuplicate.isDuplicate(existing, "第三章习题", 1_000_000L))
    }

    @Test
    fun `case is normalized`() {
        val existing = listOf(entity("Homework 3", 1_000_000L))
        assertTrue(CaptureDuplicate.isDuplicate(existing, "homework 3", 1_000_000L))
    }

    @Test
    fun `blank title is never duplicate`() {
        val existing = listOf(entity("", 1_000_000L))
        assertFalse(CaptureDuplicate.isDuplicate(existing, "  ", 1_000_000L))
    }
}
