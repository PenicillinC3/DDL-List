package com.gammac3.ddlapp.capture

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 自动记 DDL 的本地偏好；只存开关与悬浮球位置，不存任何页面文字。 */
class CapturePrefs private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("capture_prefs", Context.MODE_PRIVATE)

    private val _floatingBallEnabled = MutableStateFlow(prefs.getBoolean(KEY_FLOATING_BALL, false))
    val floatingBallEnabled: StateFlow<Boolean> = _floatingBallEnabled

    fun setFloatingBallEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FLOATING_BALL, enabled).apply()
        _floatingBallEnabled.value = enabled
    }

    var ballEdge: String
        get() = prefs.getString(KEY_BALL_EDGE, EDGE_RIGHT) ?: EDGE_RIGHT
        set(value) = prefs.edit().putString(KEY_BALL_EDGE, value).apply()

    var ballYRatio: Float
        get() = prefs.getFloat(KEY_BALL_Y, 0.4f).coerceIn(0f, 1f)
        set(value) = prefs.edit().putFloat(KEY_BALL_Y, value.coerceIn(0f, 1f)).apply()

    var firstHintShown: Boolean
        get() = prefs.getBoolean(KEY_FIRST_HINT, false)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_HINT, value).apply()

    /** 用户点了「开启」但服务尚未连接时记一笔；10 分钟内有效，避免很久以后突然亮球。 */
    fun markPendingEnable() {
        prefs.edit().putLong(KEY_PENDING_ENABLE_AT, System.currentTimeMillis()).apply()
    }

    fun consumePendingEnable(): Boolean {
        val markedAt = prefs.getLong(KEY_PENDING_ENABLE_AT, 0L)
        prefs.edit().remove(KEY_PENDING_ENABLE_AT).apply()
        return markedAt > 0L && System.currentTimeMillis() - markedAt <= PENDING_ENABLE_TTL_MILLIS
    }

    companion object {
        private const val KEY_FLOATING_BALL = "floating_ball_enabled"
        private const val KEY_BALL_EDGE = "ball_edge"
        private const val KEY_BALL_Y = "ball_y_ratio"
        private const val KEY_FIRST_HINT = "first_hint_shown"
        private const val KEY_PENDING_ENABLE_AT = "pending_enable_at"
        private const val PENDING_ENABLE_TTL_MILLIS = 10 * 60 * 1000L

        const val EDGE_LEFT = "left"
        const val EDGE_RIGHT = "right"

        @Volatile
        private var instance: CapturePrefs? = null

        fun get(context: Context): CapturePrefs =
            instance ?: synchronized(this) {
                instance ?: CapturePrefs(context).also { instance = it }
            }
    }
}
