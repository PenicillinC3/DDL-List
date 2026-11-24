package com.gammac3.ddlapp.ui.list

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeActionTest {
    @Test
    fun `right swipe completes an active item`() {
        assertEquals(SwipeAction.COMPLETE, swipeActionFor(isDone = false))
    }

    @Test
    fun `right swipe restores a completed item`() {
        assertEquals(SwipeAction.RESTORE, swipeActionFor(isDone = true))
    }
}
