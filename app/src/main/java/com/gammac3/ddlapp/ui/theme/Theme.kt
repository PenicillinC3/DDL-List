package com.gammac3.ddlapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 暖纸 · 陶土 · 墨色。
// 画布是一张米色纸（ivory），卡片是更亮一档的纸；陶土（clay）是全系统唯一的彩色，
// 只出现在「必须注意」和「可以行动」的地方。逾期用同族的赭红，不用冷色。
// 层级靠纸的明度和 1dp 细线建立，不用投影；按钮的「下圆上方」圆角是纸页从纸堆里抽出的样子。
val Clay = Color(0xFFD97757)
val ClayDeep = Color(0xFFA34826)
val ClayPressed = Color(0xFF8B3A1F)
val Manilla = Color(0xFFF5E3C7)
val Oat = Color(0xFFE3DACC)
val Parchment = Color(0xFFF0EEE6)
val Paper = Color(0xFFFAF9F5)
val Ink = Color(0xFF141413)
val InkSoft = Color(0xFF3D3D3A)
val Rust = Color(0xFF9C3D24)

private val LightColors = lightColorScheme(
    primary = ClayDeep,
    onPrimary = Paper,
    primaryContainer = Manilla,
    onPrimaryContainer = Ink,
    secondary = InkSoft,
    onSecondary = Paper,
    secondaryContainer = Oat,
    onSecondaryContainer = Ink,
    tertiary = Clay,
    onTertiary = Ink,
    tertiaryContainer = Color(0xFFF2DDD3),
    onTertiaryContainer = Color(0xFF4A1809),
    error = Rust,
    onError = Paper,
    errorContainer = Color(0xFFF2DDD3),
    onErrorContainer = Color(0xFF4A1809),
    background = Parchment,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDE9DD),
    onSurfaceVariant = Color(0xFF5F5C53),
    outline = Color(0xFFC9C4B4),
    outlineVariant = Color(0xFFDFDACB),
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    inversePrimary = Clay,
    surfaceTint = Ink,
    surfaceBright = Color(0xFFFCFAF5),
    surfaceDim = Oat,
    surfaceContainerLowest = Color(0xFFFDFBF6),
    surfaceContainerLow = Color(0xFFF7F4EC),
    surfaceContainer = Color(0xFFF2EFE6),
    surfaceContainerHigh = Color(0xFFEAE6D9),
    surfaceContainerHighest = Oat,
    scrim = Ink,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE2906F),
    onPrimary = Color(0xFF2B1207),
    primaryContainer = Color(0xFF3D2A1F),
    onPrimaryContainer = Color(0xFFF3D9CB),
    secondary = Color(0xFFD8D2C2),
    onSecondary = Color(0xFF1F1E1B),
    secondaryContainer = Color(0xFF33302A),
    onSecondaryContainer = Color(0xFFF2EFE6),
    tertiary = Color(0xFFE0A184),
    onTertiary = Color(0xFF2B1207),
    tertiaryContainer = Color(0xFF4A2016),
    onTertiaryContainer = Color(0xFFFFD9CC),
    error = Color(0xFFEE9A85),
    onError = Color(0xFF3B120A),
    errorContainer = Color(0xFF4A2016),
    onErrorContainer = Color(0xFFFFD9CC),
    background = Color(0xFF171614),
    onBackground = Color(0xFFF2EFE6),
    surface = Color(0xFF1F1E1B),
    onSurface = Color(0xFFF2EFE6),
    surfaceVariant = Color(0xFF2A2823),
    onSurfaceVariant = Color(0xFFA9A599),
    outline = Color(0xFF3E3A32),
    outlineVariant = Color(0xFF33302A),
    inverseSurface = Color(0xFFF2EFE6),
    inverseOnSurface = Color(0xFF171614),
    inversePrimary = ClayDeep,
    surfaceTint = Color(0xFFF2EFE6),
    surfaceBright = Color(0xFF2A2823),
    surfaceDim = Color(0xFF121110),
    surfaceContainerLowest = Color(0xFF121110),
    surfaceContainerLow = Color(0xFF1B1A17),
    surfaceContainer = Color(0xFF201F1C),
    surfaceContainerHigh = Color(0xFF26241F),
    surfaceContainerHighest = Color(0xFF2E2B25),
    scrim = Color(0xFF000000),
)

@Composable
fun DdlAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = DdlTypography,
        content = content
    )
}
