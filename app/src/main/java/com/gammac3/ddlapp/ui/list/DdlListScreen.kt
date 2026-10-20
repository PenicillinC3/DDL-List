package com.gammac3.ddlapp.ui.list

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gammac3.ddlapp.DdlApplication
import com.gammac3.ddlapp.logic.DdlGrouper
import com.gammac3.ddlapp.model.DdlGroup
import com.gammac3.ddlapp.model.DdlRow
import com.gammac3.ddlapp.ui.theme.FigureFont
import com.gammac3.ddlapp.ui.theme.HanFont
import com.gammac3.ddlapp.ui.theme.TitleFont
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val groupOrder = listOf(
    DdlGroup.OVERDUE, DdlGroup.TODAY, DdlGroup.TOMORROW,
    DdlGroup.THIS_WEEK, DdlGroup.LATER, DdlGroup.DONE,
)

private fun groupName(g: DdlGroup): String = when (g) {
    DdlGroup.OVERDUE -> "已过期"; DdlGroup.TODAY -> "今天"; DdlGroup.TOMORROW -> "明天"
    DdlGroup.THIS_WEEK -> "未来 7 天"; DdlGroup.LATER -> "更晚"; DdlGroup.DONE -> "已完成"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DdlListScreen(
    viewModel: DdlListViewModel,
    onEdit: (DdlRow?) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            viewModel.gcTombstones(now) // 墓碑槽窗口走完即从 UI 收场
            delay(1_000)
        }
    }
    val zone = remember { ZoneId.systemDefault() }
    var doneExpanded by rememberSaveable { mutableStateOf(false) }

    // 通知权限（Android 13+）：以系统通知开关为真源，每次回前台刷新
    val context = LocalContext.current
    var notifGranted by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var notifDenied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notifDenied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notifGranted && !notifDenied
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 精确闹钟（Android 12+，canScheduleExact 内部处理低版本）
    var exactOk by remember { mutableStateOf(true) }
    // 电池白名单（准点保障）：回前台按系统真源刷新；「先不」仅本次会话收起
    var batteryWhitelisted by remember { mutableStateOf(true) }
    var batteryCardDismissed by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        notifGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (notifGranted) notifDenied = false
        exactOk = (context.applicationContext as DdlApplication)
            .container.scheduler.canScheduleExact()
        batteryWhitelisted = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "DDL 清单",
                        fontFamily = TitleFont,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onEdit(null) },
                modifier = Modifier.semantics { contentDescription = "添加 DDL" },
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ) { Text("＋", fontFamily = HanFont, fontSize = 24.sp) }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 精确闹钟引导（Android 12+ 未授权）
            if (!exactOk) {
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:${context.packageName}"),
                            )
                        )
                    },
                ) {
                    Text(
                        "⚠ 精确提醒未开启，点击去设置（关闭后提醒可能略晚）",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            // 通知权限被拒绝提示（Android 13+）
            if (notifDenied) {
                Text(
                    "🔔 通知权限未开启，将收不到提醒，点击去系统设置",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            // 电池优化豁免引导（准点保障）：厂商省电策略会压后台闹钟，加白名单显著减小延迟
            if (!batteryWhitelisted && !batteryCardDismissed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}"),
                                )
                            )
                        },
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        Text(
                            "🔋 后台省电时闹钟可能被延迟，点击加入电池白名单",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(onClick = { batteryCardDismissed = true }) {
                        Text("先不", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // 过滤 chips
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DdlFilter.entries.forEach { f ->
                    val selected = state.filter == f
                    FilterChip(
                        selected = selected,
                        label = {
                            Text(
                                when (f) {
                                    DdlFilter.ALL -> "全部"
                                    DdlFilter.ACTIVE -> "未完成"
                                    DdlFilter.DONE -> "已完成"
                                }
                            )
                        },
                        onClick = { viewModel.setFilter(f) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                            selectedLabelColor = MaterialTheme.colorScheme.inverseOnSurface,
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.inverseSurface
                            else MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                }
            }
            // 分组列表
            val grouped = remember(state.rows, now) {
                DdlGrouper.group(state.rows, now, zone)
            }
            // 墓碑槽按组归位（与 DdlGrouper.groupOf 同规则）；筛选与桶位不匹配的槽静默（窗口仍由 gc 清）
            val tombsByGroup = remember(state.tombstones, state.filter, now) {
                state.tombstones
                    .filter {
                        (state.filter == DdlFilter.ALL) ||
                            (it.row.isDone == (state.filter == DdlFilter.DONE))
                    }
                    .groupBy { DdlGrouper.groupOf(it.row, now, zone) }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.rows.isEmpty() && state.tombstones.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = if (state.filter == DdlFilter.ALL) {
                                "还没有 DDL，点右下角 ＋ 添加"
                            } else {
                                "该筛选下暂无条目，试试切换筛选"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 40.dp),
                        )
                    }
                } else {
                    var firstGroup = true
                    groupOrder.forEach { group ->
                        val rows = grouped[group].orEmpty()
                        val tombs = tombsByGroup[group].orEmpty()
                        if (rows.isEmpty() && tombs.isEmpty()) return@forEach
                        if (!firstGroup) {
                            item(key = "separator_$group") {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                        firstGroup = false
                        item(key = "header_$group") {
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable(enabled = group == DdlGroup.DONE) {
                                        doneExpanded = !doneExpanded
                                    }
                                    .padding(top = 8.dp, bottom = 2.dp),
                            ) {
                                if (group == DdlGroup.OVERDUE) {
                                    OverdueSeal(count = rows.size)
                                } else {
                                    Text(
                                        text = buildAnnotatedString {
                                            append(groupName(group))
                                            append("（")
                                            withStyle(SpanStyle(fontFamily = FigureFont)) {
                                                append("${rows.size}")
                                            }
                                            append("）")
                                            if (group == DdlGroup.DONE) append(if (doneExpanded) " ▾" else " ▸")
                                        },
                                        fontFamily = HanFont,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.6.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (group == DdlGroup.DONE && !doneExpanded) return@forEach
                        // 组内展示序 = 真行 + 墓碑槽（按截止时刻回插原位）
                        val display = mergeTombs(rows, tombs)
                        items(display, key = { item ->
                            when (item) {
                                is ListItem.RowV -> item.row.id
                                is ListItem.TombV -> -(item.tomb.row.id + 1L)
                            }
                        }, contentType = { item ->
                            when (item) {
                                is ListItem.RowV -> "row"
                                is ListItem.TombV -> "tomb"
                            }
                        }) { item ->
                            when (item) {
                                is ListItem.RowV -> SwipeRow(
                                    row = item.row,
                                    nowMillis = now,
                                    overdue = group == DdlGroup.OVERDUE,
                                    onClick = { onEdit(item.row) },
                                    onDelete = { viewModel.delete(item.row) },
                                    onToggleDone = { viewModel.toggleDone(item.row) },
                                    modifier = Modifier.animateItem(),
                                )
                                is ListItem.TombV -> TombCard(
                                    tomb = item.tomb,
                                    nowMillis = now,
                                    onUndo = { viewModel.undoTombstone(item.tomb.row.id) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeRow(
    row: DdlRow,
    nowMillis: Long,
    overdue: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 手势自绘（R44）：SwipeToDismissBox「一口气划走整卡」手感差，且从悬停态收回时
    // 会过冲到 StartToEnd 锚点误触「标为已完成」。规则收敛为：
    // 左划最多拖到恰好露出「删除」即停靠（行程带阻尼）；悬停中右划只收回，永不误触。
    val openPx = with(LocalDensity.current) { 92.dp.toPx() }
    val donePx = with(LocalDensity.current) { 64.dp.toPx() }
    val capLeft = openPx * 1.45f   // 左向阻尼渐近上限
    val capRight = donePx * 1.6f   // 右向

    var raw by remember { mutableFloatStateOf(0f) }     // 未阻尼累计行程
    var anchor by remember { mutableFloatStateOf(0f) }  // 松手落点：0 或 -openPx
    var dragging by remember { mutableStateOf(false) }
    var fromOpen by remember { mutableStateOf(false) }

    // 阻尼映射：起段线性跟手，越接近上限越像拉橡皮筋
    fun resist(x: Float): Float =
        if (x >= 0f) capRight * (1f - exp(-x / capRight)) else -capLeft * (1f - exp(x / capLeft))
    fun rawFor(a: Float): Float = if (a < 0f) capLeft * ln(1f + a / capLeft) else 0f

    fun settle() {
        dragging = false
        if (fromOpen) {
            // 悬停中右划只收回（回程中点即落锚收回），切换完成只许「完全收拢」状态下右划
            anchor = if (raw >= rawFor(-openPx * 0.5f)) 0f else -openPx
        } else when {
            raw <= -openPx * 0.55f -> anchor = -openPx
            raw >= donePx && !row.isDone -> { onToggleDone(); anchor = 0f }
            else -> anchor = 0f
        }
        raw = rawFor(anchor)
    }

    val visualState = animateFloatAsState(
        targetValue = if (dragging) resist(raw) else anchor,
        animationSpec = if (dragging) snap() else spring(),
        label = "swipeOffset",
    )

    val docked = !dragging && anchor <= -openPx * 0.95f
    // 悬停安全带：3.5 秒不点删除自动收回
    LaunchedEffect(docked) {
        if (docked) {
            delay(3_500)
            anchor = 0f
            raw = 0f
        }
    }
    // 悬停中点卡片本体 = 先收回（防误进编辑器）
    val cardClick: () -> Unit = {
        if (docked) { anchor = 0f; raw = 0f } else onClick()
    }

    val errBg = MaterialTheme.colorScheme.errorContainer
    val onErr = MaterialTheme.colorScheme.onErrorContainer
    val doneBg = MaterialTheme.colorScheme.primaryContainer
    val onDone = MaterialTheme.colorScheme.onPrimaryContainer

    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))) {
        // 衬底：drawBehind 延迟读位移（拖动中零重组）；静置透明，右显「删除」红底、左显「完成」靛底
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    when {
                        visualState.value <= -8f -> drawRect(errBg)
                        visualState.value >= donePx * 0.35f -> drawRect(doneBg)
                    }
                }
                .clickable(enabled = docked, onClick = onDelete),
        )
        Text(
            text = "删除",
            color = onErr,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .graphicsLayer { alpha = ((-visualState.value) / (openPx * 0.6f)).coerceIn(0f, 1f) }
                .offset { IntOffset((visualState.value * 0.5f).roundToInt(), 0) }
                .padding(end = 24.dp),
        )
        Text(
            text = "完成",
            color = onDone,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer { alpha = (visualState.value / donePx).coerceIn(0f, 1f) }
                .offset { IntOffset((visualState.value * 0.5f).roundToInt(), 0) }
                .padding(start = 20.dp),
        )
        Box(
            Modifier
                .offset { IntOffset(visualState.value.roundToInt(), 0) }
                .pointerInput(row.id) {
                    detectHorizontalDragGestures(
                        onDragStart = { _ ->
                            dragging = true
                            fromOpen = anchor <= -openPx * 0.95f
                        },
                        onDragEnd = { settle() },
                        onDragCancel = { settle() },
                    ) { change, dragAmount ->
                        change.consume()
                        raw += dragAmount
                    }
                },
        ) {
            DdlItemCard(row = row, nowMillis = nowMillis, overdue = overdue, onClick = cardClick)
        }
    }
}

/** 组内展示序列：真行 + 墓碑槽（按截止时刻回插原位，保证组内列序一致）。 */
private sealed interface ListItem {
    data class RowV(val row: DdlRow) : ListItem
    data class TombV(val tomb: Tombstone) : ListItem
}

private fun mergeTombs(rows: List<DdlRow>, tombs: List<Tombstone>): List<ListItem> {
    val out: MutableList<ListItem> = rows.map { ListItem.RowV(it) }.toMutableList()
    for (t in tombs.sortedBy { it.row.deadlineMillis }) {
        val idx = out.indexOfFirst {
            it is ListItem.RowV && it.row.deadlineMillis > t.row.deadlineMillis
        }
        out.add(if (idx < 0) out.size else idx, ListItem.TombV(t))
    }
    return out
}

/** 墓碑槽（U2 定款）：虚线框本身就是倒计时——虚线沿圆角矩形游走、可见弧随 5 秒缩短直至烧尽；点槽即以原 id 复活。 */
@Composable
private fun TombCard(tomb: Tombstone, nowMillis: Long, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val initial = ((tomb.expiresAtMillis - nowMillis) / DdlListViewModel.WINDOW_MILLIS.toFloat())
        .coerceIn(0f, 1f)
    val progress = remember(tomb.row.id) { Animatable(initial) }
    LaunchedEffect(tomb.row.id) {
        if (initial > 0f) {
            progress.animateTo(
                0f,
                tween(
                    durationMillis = (initial * DdlListViewModel.WINDOW_MILLIS).toInt().coerceAtLeast(1),
                    easing = LinearEasing,
                ),
            )
        }
    }
    val remainSec = (((tomb.expiresAtMillis - nowMillis) + 999) / 1_000).toInt().coerceIn(0, 5)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onUndo)
            .dashedCountdownBorder({ progress.value }, MaterialTheme.colorScheme.onSurfaceVariant)
            .padding(horizontal = 15.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "已删除「${tomb.row.title}」",
                fontSize = 12.sp,
                fontFamily = HanFont,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$remainSec″",
                fontSize = 10.5.sp,
                fontFamily = FigureFont,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "点此撤销",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = HanFont,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 虚线倒计时边框（擦除式）：缺口从框线起点单向扩张、可见弧逐秒减少，dash 相位恒定。
 *  PathEffect 走 android 原生画笔（Compose drawPath 无 pathEffect 参数），分段由 PathMeasure 截取。 */
private fun Modifier.dashedCountdownBorder(progress: () -> Float, color: Color): Modifier = drawBehind {
    val p = progress().coerceIn(0f, 1f)
    if (p <= 0.01f) return@drawBehind
    val strokeW = 1.5.dp.toPx()
    val inset = strokeW / 2f
    if (size.width <= strokeW + 2f || size.height <= strokeW + 2f) return@drawBehind
    val apath = android.graphics.Path().apply {
        addRoundRect(
            inset, inset, size.width - inset, size.height - inset,
            16.dp.toPx(), 16.dp.toPx(),
            android.graphics.Path.Direction.CW,
        )
    }
    val measure = android.graphics.PathMeasure(apath, false)
    val total = measure.length
    if (total <= 0f) return@drawBehind
    val paint = android.graphics.Paint().apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = strokeW
        isAntiAlias = true
        this.color = color.toArgb()
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(4.dp.toPx(), 3.5.dp.toPx()), 0f)
    }
    // 擦除式倒计时（R44 重做）：缺口从框线起点沿路径单向扩张，可见弧长 = 周长 × p——
    // 框线一点一点减少（无旋转、无相位滑动），烧尽即无
    val seg = android.graphics.Path()
    measure.getSegment(total * (1f - p), total, seg, true)
    drawContext.canvas.nativeCanvas.drawPath(seg, paint)
}

/** 逾期组头小印章：朱文阳刻（纸底红框红字），斜盖 3°；首次出现或新增逾期项时「盖章」落下。 */
@Composable
private fun OverdueSeal(count: Int) {
    // 计数变化（含首次出现）重触发盖章；已定格后保持斜角
    var stampedSize by remember { mutableIntStateOf(-1) }
    // press: 0 = 起手（略大更斜未蘸墨），1 = 盖定；spring 回弹让缩放/角度越过终点形成「回压」手感
    val press = remember { Animatable(1f) }
    LaunchedEffect(count) {
        if (count != stampedSize) {
            stampedSize = count
            press.snapTo(0f)
            press.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "逾期",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.graphicsLayer {
                val p = press.value
                scaleX = 1.7f - 0.7f * p
                scaleY = 1.7f - 0.7f * p
                rotationZ = -13f + 10f * p
                alpha = (p * 4f).coerceIn(0f, 1f)
            }
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(3.dp),
                )
                .padding(start = 7.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        )
        Text(
            text = buildAnnotatedString {
                append("（")
                withStyle(SpanStyle(fontFamily = FigureFont)) { append("$count") }
                append("）")
            },
            fontFamily = HanFont,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
