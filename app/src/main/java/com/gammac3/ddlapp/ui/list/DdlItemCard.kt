package com.gammac3.ddlapp.ui.list

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gammac3.ddlapp.model.DdlRow
import com.gammac3.ddlapp.ui.Format
import com.gammac3.ddlapp.ui.theme.BellMuted
import com.gammac3.ddlapp.ui.theme.BellSolid
import com.gammac3.ddlapp.ui.theme.DataFont
import com.gammac3.ddlapp.ui.theme.FigureFont
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun DdlItemCard(
    row: DdlRow,
    nowMillis: Long,
    overdue: Boolean,
    onPostpone: (Long?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 纯展示卡（R46）：此前 Card(onClick=…) 是高层 clickable，会参与横向手势竞争——
    // 悬停/划回拖动的「松手结算」若被它截断，卡片就会停在阻尼渐近线（真机截图病例）。
    // 点击统一收敛到整行的 detectTapGestures（见 SwipeRow）。
    // 例外（v1.15.0 顺延 chip）：卡内唯一可点区，hit 区小而独立，拖动不入此层。
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = if (row.title.length > 28) row.title.take(28) + "…" else row.title,
                style = MaterialTheme.typography.titleMedium,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (row.note.isNotBlank()) {
                Text(
                    text = row.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (row.isDone) {
                Text(
                    text = "已完成",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                Text(
                    text = Format.remaining(row.deadlineMillis, nowMillis),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = DataFont,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = if (overdue) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Text(
                text = "期限 " + Format.absolute(row.deadlineMillis),
                fontFamily = FigureFont,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                ReminderPill(
                    muted = row.muted,
                    text = when {
                        row.muted -> "不提醒"
                        row.leadsMinutes.isEmpty() -> "到期时提醒"
                        else -> "提前 " + row.leadsMinutes.sorted()
                            .joinToString(" · ") { Format.leadText(it) }
                    },
                )
                if (overdue && !row.isDone) {
                    Spacer(Modifier.weight(1f))
                    PostponeChip(
                        deadlineMillis = row.deadlineMillis,
                        nowMillis = nowMillis,
                        onPostpone = onPostpone,
                    )
                }
            }
        }
    }
}

/** 逾期一键顺延 chip：视觉 ~24dp 胶囊，外层透明 contact 环补足 48dp 防误触；
 *  按压微缩走 R48 的 pressed 反馈语汇（无 ripple）。菜单目标：今晚/明天 23:59、
 *  明天同一时刻、自定义（onPostpone(null) → 屏上层转编辑器）。 */
@Composable
private fun PostponeChip(
    deadlineMillis: Long,
    nowMillis: Long,
    onPostpone: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    var menuOpen by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(140),
        label = "postponePress",
    )
    val today = remember(nowMillis, zone) {
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    }
    val deadlineTime = remember(deadlineMillis, zone) {
        Instant.ofEpochMilli(deadlineMillis).atZone(zone).toLocalTime()
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(width = 52.dp, height = 44.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable(interactionSource = interaction, indication = null) { menuOpen = true }
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Text(
                text = "顺延",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = "▾",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            PostponeMenuItem("今晚 23:59") {
                menuOpen = false
                onPostpone(today.atTime(23, 59).atZone(zone).toInstant().toEpochMilli())
            }
            PostponeMenuItem("明天 23:59") {
                menuOpen = false
                onPostpone(today.plusDays(1).atTime(23, 59).atZone(zone).toInstant().toEpochMilli())
            }
            PostponeMenuItem("明天同一时刻") {
                menuOpen = false
                onPostpone(
                    today.plusDays(1).atTime(deadlineTime).atZone(zone).toInstant().toEpochMilli()
                )
            }
            PostponeMenuItem("自定义…") {
                menuOpen = false
                onPostpone(null)
            }
        }
    }
}

@Composable
private fun PostponeMenuItem(label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontSize = 14.sp) },
        onClick = onClick,
    )
}

@Composable
private fun ReminderPill(
    muted: Boolean,
    text: String,
    modifier: Modifier = Modifier,
) {
    val background = if (muted) MaterialTheme.colorScheme.surfaceVariant
    else MaterialTheme.colorScheme.primaryContainer
    val foreground = if (muted) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onPrimaryContainer
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(color = background, shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Icon(
            imageVector = if (muted) BellMuted else BellSolid,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = foreground,
        )
        Spacer(Modifier.width(4.dp))
        Text(text = text, fontSize = 11.sp, color = foreground)
    }
}
