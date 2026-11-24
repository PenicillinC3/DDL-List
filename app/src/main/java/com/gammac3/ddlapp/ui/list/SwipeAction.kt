package com.gammac3.ddlapp.ui.list

internal enum class SwipeAction {
    COMPLETE,
    RESTORE,
}

internal fun swipeActionFor(isDone: Boolean): SwipeAction =
    if (isDone) SwipeAction.RESTORE else SwipeAction.COMPLETE
