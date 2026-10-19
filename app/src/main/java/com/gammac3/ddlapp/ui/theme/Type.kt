package com.gammac3.ddlapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.gammac3.ddlapp.R

// F5 字体：中文全部走思源黑体（GB 一级字集），品牌标题/数据/日期各有专用字面。
// 同一 Family 内多字重同权重条目 = Compose 字形回退链：
// 拉丁字符先命中首个字体，缺失字形（中文）回退到思源黑体。

/** 所有中文正文。 */
val HanFont = FontFamily(
    Font(R.font.source_han_cn_400, FontWeight.W400),
    Font(R.font.source_han_cn_500, FontWeight.W500),
    Font(R.font.source_han_cn_600, FontWeight.W600),
    Font(R.font.source_han_cn_700, FontWeight.W700),
)

/** 应用标题：拉丁 Timeless Grotesk Bold + 中文思源黑体 Bold。 */
val TitleFont = FontFamily(
    Font(R.font.timeless_grotesk_bold, FontWeight.W700),
    Font(R.font.source_han_cn_700, FontWeight.W700),
)

/** 倒计时：Maple Mono 数字 + 中文单位（天/小时/分）。 */
val DataFont = FontFamily(
    Font(R.font.maple_mono_bold_data, FontWeight.W700),
    Font(R.font.source_han_cn_700, FontWeight.W700),
)

/** 日期/期限数字：Timeless Serif Text + 中文回退。 */
val FigureFont = FontFamily(
    Font(R.font.timeless_serif_text, FontWeight.W400),
    Font(R.font.source_han_cn_400, FontWeight.W400),
)

/** 全局基线：所有排版槽位默认思源黑体，仅 titleLarge 使用品牌标题字体。 */
val DdlTypography: Typography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = HanFont),
        displayMedium = base.displayMedium.copy(fontFamily = HanFont),
        displaySmall = base.displaySmall.copy(fontFamily = HanFont),
        headlineLarge = base.headlineLarge.copy(fontFamily = HanFont),
        headlineMedium = base.headlineMedium.copy(fontFamily = HanFont),
        headlineSmall = base.headlineSmall.copy(fontFamily = HanFont),
        titleLarge = base.titleLarge.copy(fontFamily = TitleFont),
        titleMedium = base.titleMedium.copy(fontFamily = HanFont),
        titleSmall = base.titleSmall.copy(fontFamily = HanFont),
        bodyLarge = base.bodyLarge.copy(fontFamily = HanFont),
        bodyMedium = base.bodyMedium.copy(fontFamily = HanFont),
        bodySmall = base.bodySmall.copy(fontFamily = HanFont),
        labelLarge = base.labelLarge.copy(fontFamily = HanFont),
        labelMedium = base.labelMedium.copy(fontFamily = HanFont),
        labelSmall = base.labelSmall.copy(fontFamily = HanFont),
    )
}
