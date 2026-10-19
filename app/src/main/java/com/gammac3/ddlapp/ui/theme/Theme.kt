package com.gammac3.ddlapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// V1 净白聚焦：白灰画布 + 墨黑主色，靛蓝只用于倒计时/提醒，逾期红单独可辨。
private val LightColors = lightColorScheme(
    primary = Color(0xFF3A5AF0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEEF2FF),
    onPrimaryContainer = Color(0xFF1B2E8F),
    secondary = Color(0xFF3A5AF0),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEEF2FF),
    onSecondaryContainer = Color(0xFF1B2E8F),
    tertiary = Color(0xFF3A5AF0),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEEF2FF),
    onTertiaryContainer = Color(0xFF1B2E8F),
    error = Color(0xFFE5484D),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE8E9),
    onErrorContainer = Color(0xFF7A1518),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF101828),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF101828),
    surfaceVariant = Color(0xFFF0F2F5),
    onSurfaceVariant = Color(0xFF98A0AA),
    outline = Color(0xFFE4E7EC),
    outlineVariant = Color(0xFFE4E7EC),
    inverseSurface = Color(0xFF101828),
    inverseOnSurface = Color(0xFFF6F7F9),
    inversePrimary = Color(0xFF8FA3FF),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE4E7EC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF6F7F9),
    surfaceContainerHigh = Color(0xFFF0F2F5),
    surfaceContainerHighest = Color(0xFFE4E7EC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FA3FF),
    onPrimary = Color(0xFF101223),
    primaryContainer = Color(0xFF1E2A4F),
    onPrimaryContainer = Color(0xFFC5D0FF),
    secondary = Color(0xFF8FA3FF),
    onSecondary = Color(0xFF101223),
    secondaryContainer = Color(0xFF1E2A4F),
    onSecondaryContainer = Color(0xFFC5D0FF),
    tertiary = Color(0xFF8FA3FF),
    onTertiary = Color(0xFF101223),
    tertiaryContainer = Color(0xFF1E2A4F),
    onTertiaryContainer = Color(0xFFC5D0FF),
    error = Color(0xFFFF7A7F),
    onError = Color(0xFF3B0A0C),
    errorContainer = Color(0xFF4C1418),
    onErrorContainer = Color(0xFFFFD9D9),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE8ECF1),
    surface = Color(0xFF151A21),
    onSurface = Color(0xFFE8ECF1),
    surfaceVariant = Color(0xFF1A1F26),
    onSurfaceVariant = Color(0xFF8B949E),
    outline = Color(0xFF22262E),
    outlineVariant = Color(0xFF22262E),
    inverseSurface = Color(0xFFF2F4F8),
    inverseOnSurface = Color(0xFF0E1116),
    inversePrimary = Color(0xFF3A5AF0),
    surfaceBright = Color(0xFF1A1F26),
    surfaceDim = Color(0xFF0B0E13),
    surfaceContainerLowest = Color(0xFF0B0E13),
    surfaceContainerLow = Color(0xFF11151B),
    surfaceContainer = Color(0xFF151A21),
    surfaceContainerHigh = Color(0xFF1A1F26),
    surfaceContainerHighest = Color(0xFF22262E),
)

@Composable
fun DdlAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
