package com.gammac3.ddlapp.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 无障碍服务连接状态；UI 只观察，不主动改写。 */
object CaptureServiceState {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    fun setConnected(value: Boolean) {
        _connected.value = value
    }
}
