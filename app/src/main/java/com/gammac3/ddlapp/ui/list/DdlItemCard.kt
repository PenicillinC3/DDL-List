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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gammac3.ddlapp.R
import com.gammac3.ddlapp.model.DdlRow
import com.gammac3.ddlapp.ui.Format
import com.gammac3.ddlapp.ui.theme.BellMuted
import com.gammac3.ddlapp.ui.theme.BellSolid
import com.gammac3.ddlapp.ui.theme.DataFont
import com.gammac3.ddlapp.ui.theme.FigureFont
import com.gammac3.ddlapp.ui.theme.UiFont
import com.gammac3.ddlapp.ui.theme.SerifFont
import java.time.Instant
import java.time.ZoneId

// 一张纸卡：亮一档的纸面 + 1dp 细边，零投影。
// 标题用宋体（像议程上的一行议题），数字用 Maple（像账簿上的墨迹），
// 其余信息用黑体小字。层级靠字号与留白，不靠色块。
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
    // SwipeRow 在随手势移动的前景容器上处理卡片点按。
    // 例外（v1.15.0 顺延 chip）：卡内唯一可点区，hit 区小而独立，拖动不入此层。
    val context = LocalContext.current
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 10.dp)) {
            Text(
                text = if (row.title.length > 28) row.title.take(28) + "…" else row.title,
                fontFamily = SerifFont,
                fontSize = 16.5.sp,
                lineHeight = 23.sp,
                letterSpacing = 0.2.sp,
                color = if (row.isDone) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (row.isDone) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.note.isNotBlank()) {
                Text(
                    text = row.note,
                    fontFamily = UiFont,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            if (row.isDone) {
                Text(
                    text = stringResource(R.string.card_done_tag),
                    fontFamily = UiFont,
                    fontSize = 11.sp,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 7.dp),
                )
            } else {
                // 账簿上的数字：墨色正体，逾期才转赭红
                Text(
                    text = Format.remaining(context, row.deadlineMillis, nowMillis),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = DataFont,
                        fontSize = 23.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = if (overdue) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Text(
                text = stringResource(
                    R.string.card_due,
                    Format.absolute(context, row.deadlineMillis),
                ),
                fontFamily = FigureFont,
                fontSize = 11.5.sp,
                letterSpacing = 0.2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
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
                        row.muted -> stringResource(R.string.pill_muted)
                        row.leadsMinutes.isEmpty() -> stringResource(R.string.pill_at_deadline)
                        else -> stringResource(
                            R.string.pill_early,
                            row.leadsMinutes.sorted()
                                .joinToString(" · ") { Format.leadText(context, it) },
                        )
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

/** 逾期一键顺延：描边胶囊，外层透明 contact 环补足 48dp 防误触；
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
    val postponeText = stringResource(R.string.chip_postpone)
    val tonightLabel = stringResource(R.string.postpone_tonight)
    val tomorrowLabel = stringResource(R.string.postpone_tomorrow)
    val sameTimeLabel = stringResource(R.string.postpone_same_time)
    val customLabel = stringResource(R.string.postpone_custom)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(width = 54.dp, height = 44.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clip(CircleShape)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                )
                .clickable(interactionSource = interaction, indication = null) { menuOpen = true }
                .padding(start = 8.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                text = postponeText,
                fontFamily = UiFont,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = "▾",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            PostponeMenuItem(tonightLabel) {
                menuOpen = false
                onPostpone(today.atTime(23, 59).atZone(zone).toInstant().toEpochMilli())
            }
            PostponeMenuItem(tomorrowLabel) {
                menuOpen = false
                onPostpone(today.plusDays(1).atTime(23, 59).atZone(zone).toInstant().toEpochMilli())
            }
            PostponeMenuItem(sameTimeLabel) {
                menuOpen = false
                onPostpone(
                    today.plusDays(1).atTime(deadlineTime).atZone(zone).toInstant().toEpochMilli()
                )
            }
            PostponeMenuItem(customLabel) {
                menuOpen = false
                onPostpone(null)
            }
        }
    }
}

@Composable
private fun PostponeMenuItem(label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontFamily = UiFont, fontSize = 14.sp) },
        onClick = onClick,
    )
}

/** 提醒标签：有提醒时用麦色胶囊，静音时用描边胶囊。 */
@Composable
private fun ReminderPill(
    muted: Boolean,
    text: String,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    val background = if (muted) androidx.compose.ui.graphics.Color.Transparent
    else MaterialTheme.colorScheme.primaryContainer
    val foreground = if (muted) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onPrimaryContainer
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(color = background, shape = shape)
            .then(
                if (muted) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Icon(
            imageVector = if (muted) BellMuted else BellSolid,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = foreground,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            fontFamily = UiFont,
            fontSize = 10.5.sp,
            letterSpacing = 0.2.sp,
            color = foreground,
        )
    }
}
