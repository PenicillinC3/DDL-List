package com.gammac3.ddlapp.capture

/**
 * 进程内单次抓取会话：服务写、浮卡读、关闭即清。
 * 不持久化，不写日志；进程被杀后自然消失。
 */
object CaptureSession {
    @Volatile
    var pending: CapturePayload? = null

    @Volatile
    var cardOpen: Boolean = false

    @Volatile
    var cardShown: Boolean = false

    var onCardClosed: (() -> Unit)? = null
    var onSaved: ((Long) -> Unit)? = null
}
