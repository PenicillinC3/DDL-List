package com.gammac3.ddlapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 两套字面，一条分界：
// 西文一律 Timeless Serif Text，中文一律思源宋体（Source Han Serif SC）。
// 两份字体原样随包发布，运行时由 ScriptTypeface 逐字回退拼成一套字面
// （见 ScriptTypeface.kt）。只有 400 / 600 两档，层级靠字号与留白，不靠加粗。

/** 界面正文与控件：思源宋体 + Timeless Serif Text。 */
val UiFont = FontFamily(
    scriptFont(FontWeight.W400),
    scriptFont(FontWeight.W500),
    scriptFont(FontWeight.W600),
    scriptFont(FontWeight.W700),
)

/** 编辑式标题：同一套字面，固定 400，不加粗。 */
val SerifFont = FontFamily(
    scriptFont(FontWeight.W400, semibold = false),
    scriptFont(FontWeight.W500, semibold = false),
    scriptFont(FontWeight.W600, semibold = false),
    scriptFont(FontWeight.W700, semibold = false),
)

/** 应用标题：宋体正体。 */
val TitleFont = SerifFont

/** 倒计时：数字按 tnum 等宽（调用处已开 fontFeatureSettings）。 */
val DataFont = UiFont

/** 日期/期限：衬线数字 + 思源宋体。 */
val FigureFont = SerifFont

private fun serif(size: Int, lineHeight: Float, spacing: Double = 0.0) = TextStyle(
    fontFamily = SerifFont,
    fontWeight = FontWeight.W400,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = spacing.sp,
)

/** 全局基线：正文界面字，标题衬线字。 */
val DdlTypography: Typography = Typography().let { base ->
    base.copy(
        displayLarge = serif(48, 1.12f),
        displayMedium = serif(40, 1.15f),
        displaySmall = serif(34, 1.18f),
        headlineLarge = serif(30, 1.2f),
        headlineMedium = serif(26, 1.22f),
        headlineSmall = serif(22, 1.25f),
        titleLarge = serif(22, 1.25f),
        titleMedium = base.titleMedium.copy(fontFamily = UiFont, fontWeight = FontWeight.W600),
        titleSmall = base.titleSmall.copy(fontFamily = UiFont, fontWeight = FontWeight.W600),
        bodyLarge = base.bodyLarge.copy(fontFamily = UiFont),
        bodyMedium = base.bodyMedium.copy(fontFamily = UiFont),
        bodySmall = base.bodySmall.copy(fontFamily = UiFont),
        labelLarge = base.labelLarge.copy(fontFamily = UiFont),
        labelMedium = base.labelMedium.copy(fontFamily = UiFont),
        labelSmall = base.labelSmall.copy(fontFamily = UiFont),
    )
}
