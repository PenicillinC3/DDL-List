package com.gammac3.ddlapp.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// 提醒胶囊铃铛：用户选定的 C 款「圆润极简」，手绘 24×24 路径（提案页 1:1 迁移，非图标库拷贝）。
// 单色字形：仅作占位色，运行时由 Icon(tint = …) 统一染色——提醒/静音、亮/暗主题自动适配。
// 语义：BellSolid = 提醒中；BellMuted = 描边 + 斜杠（斜杠方向与启动图标红杠同语言）。

private val PlaceholderInk = Color(0xFF000000)

/** 提醒中：实心钟体 + 悬浮钟舌圆点。 */
val BellSolid: ImageVector by lazy {
    ImageVector.Builder(
        name = "BellSolid",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(PlaceholderInk)) {
            // 钟体：圆肩 + 外撇裙边 + 圆角唇线
            moveTo(12f, 4.6f)
            curveTo(8.9f, 4.6f, 6.6f, 7.1f, 6.6f, 10.5f)
            lineTo(6.6f, 13.4f)
            curveTo(6.6f, 14.3f, 6.3f, 15.05f, 5.7f, 15.7f)
            lineTo(4.95f, 16.6f)
            curveTo(4.3f, 17.4f, 4.85f, 18.6f, 5.9f, 18.6f)
            lineTo(18.1f, 18.6f)
            curveTo(19.15f, 18.6f, 19.7f, 17.4f, 19.05f, 16.6f)
            lineTo(18.3f, 15.7f)
            curveTo(17.7f, 15.05f, 17.4f, 14.3f, 17.4f, 13.4f)
            lineTo(17.4f, 10.5f)
            curveTo(17.4f, 7.1f, 15.1f, 4.6f, 12f, 4.6f)
            close()
            // 悬浮钟舌
            moveTo(13.4f, 20.9f)
            arcTo(1.4f, 1.4f, 0f, true, false, 10.6f, 20.9f)
            arcTo(1.4f, 1.4f, 0f, true, false, 13.4f, 20.9f)
            close()
        }
    }.build()
}

/** 静音：描边钟体 + 钟舌 + 左下→右上斜杠。 */
val BellMuted: ImageVector by lazy {
    ImageVector.Builder(
        name = "BellMuted",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(PlaceholderInk),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 4.6f)
            curveTo(8.9f, 4.6f, 6.6f, 7.1f, 6.6f, 10.5f)
            lineTo(6.6f, 13.4f)
            curveTo(6.6f, 14.3f, 6.3f, 15.05f, 5.7f, 15.7f)
            lineTo(4.95f, 16.6f)
            curveTo(4.3f, 17.4f, 4.85f, 18.6f, 5.9f, 18.6f)
            lineTo(18.1f, 18.6f)
            curveTo(19.15f, 18.6f, 19.7f, 17.4f, 19.05f, 16.6f)
            lineTo(18.3f, 15.7f)
            curveTo(17.7f, 15.05f, 17.4f, 14.3f, 17.4f, 13.4f)
            lineTo(17.4f, 10.5f)
            curveTo(17.4f, 7.1f, 15.1f, 4.6f, 12f, 4.6f)
            close()
            moveTo(13.4f, 20.9f)
            arcTo(1.4f, 1.4f, 0f, true, false, 10.6f, 20.9f)
            arcTo(1.4f, 1.4f, 0f, true, false, 13.4f, 20.9f)
            close()
        }
        path(
            stroke = SolidColor(PlaceholderInk),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(5.4f, 19.6f)
            lineTo(18.6f, 4.4f)
        }
    }.build()
}
