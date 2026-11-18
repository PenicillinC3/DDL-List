package com.gammac3.ddlapp.capture

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gammac3.ddlapp.R
import com.gammac3.ddlapp.ui.theme.UiFont
import com.gammac3.ddlapp.ui.theme.SectionLabel
import com.gammac3.ddlapp.ui.theme.SerifFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureSettingsSheet(open: Boolean, onDismiss: () -> Unit) {
    if (!open) return
    val context = LocalContext.current
    val prefs = remember { CapturePrefs.get(context) }
    val enabled by prefs.floatingBallEnabled.collectAsStateWithLifecycle()
    val connected by CaptureServiceState.connected.collectAsStateWithLifecycle()
    val running = enabled && connected
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        runCatching { context.startActivity(intent) }
            .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.capture_settings_title),
                        fontFamily = SerifFont,
                        fontSize = 23.sp,
                        lineHeight = 30.sp,
                        letterSpacing = 0.6.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.capture_settings_subtitle),
                        fontFamily = UiFont,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StateChip(running)
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .toggleable(
                        value = enabled,
                        role = Role.Switch,
                        onValueChange = { want ->
                            if (want) {
                                if (connected) {
                                    prefs.setFloatingBallEnabled(true)
                                    DdlCaptureAccessibilityService.refreshBall()
                                } else {
                                    prefs.markPendingEnable()
                                    openAccessibilitySettings()
                                }
                            } else {
                                prefs.setFloatingBallEnabled(false)
                                DdlCaptureAccessibilityService.refreshBall()
                            }
                        },
                    )
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.capture_floating_ball),
                        fontFamily = UiFont,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.3.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(
                            if (running) R.string.capture_state_running
                            else R.string.capture_state_off,
                        ),
                        fontFamily = UiFont,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BallSwitch(checked = enabled)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // 无障碍未开启：红框 + 「需要授权」角标；连接成功后整项隐藏
            if (!connected) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.error,
                                shape = RoundedCornerShape(4.dp),
                            )
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { openAccessibilitySettings() }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.capture_accessibility),
                                fontFamily = UiFont,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.3.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                stringResource(R.string.capture_accessibility_off),
                                fontFamily = UiFont,
                                fontSize = 11.5.sp,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            stringResource(R.string.capture_open_settings),
                            fontFamily = UiFont,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.error,
                            textDecoration = TextDecoration.Underline,
                        )
                    }
                    Text(
                        stringResource(R.string.capture_need_permission),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = 12.dp, y = (-8).dp)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 6.dp),
                        fontFamily = UiFont,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                SectionLabel(stringResource(R.string.capture_scope))
                Text(
                    stringResource(R.string.capture_scope_value),
                    fontFamily = UiFont,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                stringResource(R.string.capture_privacy),
                fontFamily = UiFont,
                fontSize = 10.5.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !connected) {
                Text(
                    stringResource(R.string.capture_restricted_hint),
                    fontFamily = UiFont,
                    fontSize = 10.5.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun StateChip(running: Boolean) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        Modifier
            .clip(shape)
            .background(
                if (running) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            )
            .border(
                width = 1.dp,
                color = if (running) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            stringResource(
                if (running) R.string.capture_state_running else R.string.capture_state_off,
            ),
            fontFamily = UiFont,
            fontSize = 10.5.sp,
            letterSpacing = 0.6.sp,
            color = if (running) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 悬浮球开关：滑块就是一颗小球，开启时滑到右侧。整行的点击语义由父级 toggleable 提供。 */
@Composable
private fun BallSwitch(checked: Boolean) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(durationMillis = 220),
        label = "ballSwitchTrack",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 29.dp else 3.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f),
        label = "ballSwitchThumb",
    )
    val sparkleColor = if (checked) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        Modifier
            .size(width = 60.dp, height = 34.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(trackColor)
            .border(
                width = 1.dp,
                color = if (checked) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(999.dp),
            ),
    ) {
        // 小球是一张纸片：细边，不用投影
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbOffset)
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_capture_sparkle),
                contentDescription = null,
                tint = sparkleColor,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
