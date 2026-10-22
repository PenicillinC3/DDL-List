package com.gammac3.ddlapp.ui.list

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
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
import com.gammac3.ddlapp.data.DdlBackup
import com.gammac3.ddlapp.logic.DdlGrouper
import com.gammac3.ddlapp.R
import com.gammac3.ddlapp.model.DdlGroup
import com.gammac3.ddlapp.model.DdlRow
import com.gammac3.ddlapp.ui.Format
import com.gammac3.ddlapp.ui.theme.FigureFont
import com.gammac3.ddlapp.ui.theme.HanFont
import com.gammac3.ddlapp.ui.theme.TitleFont
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            viewModel.gcPostponements(now) // 顺延撤销槽与墓碑同拍收场
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

    // ---- 备份导入导出（⋯ 菜单 → SAF 选文件 → 追加/覆盖二级弹窗）----
    val scope = rememberCoroutineScope()

    // 顺延完成 → Snackbar「已顺延到 … · 撤销」（v1.15.0）；动作回写原截止走同一管线
    val postponeSnacks = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.postponeEvents.collect { p ->
            val result = postponeSnacks.showSnackbar(
                message = "已顺延到 " + Format.absolute(p.newDeadlineMillis),
                actionLabel = "撤销",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoPostpone(p.rowId)
        }
    }
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    var pendingImportText by remember { mutableStateOf<String?>(null) }
    var pendingImportCount by remember { mutableIntStateOf(0) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = viewModel.exportJson()
                    val count = DdlBackup.fromJson(json).size
                    withContext(Dispatchers.IO) {
                        (context.contentResolver.openOutputStream(uri) ?: error("输出流打开失败"))
                            .use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    }
                    toast("导出完成：$count 条")
                } catch (t: Throwable) {
                    toast("导出失败：${t.message}")
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = withContext(Dispatchers.IO) {
                        (context.contentResolver.openInputStream(uri) ?: error("无法读取所选文件"))
                            .bufferedReader().use { it.readText() }
                    }
                    // 无效文件在此整体拒绝，弹窗根本不出
                    pendingImportCount = DdlBackup.fromJson(text).size
                    pendingImportText = text
                } catch (t: Throwable) {
                    toast("无法导入：${t.message}")
                }
            }
        }
    }
    if (pendingImportText != null) {
        val text = pendingImportText // 定格快照：确认回调里用
        AlertDialog(
            onDismissRequest = { pendingImportText = null },
            title = { Text("导入 $pendingImportCount 条") },
            text = {
                Text(
                    "追加：现有条目不动，同标题＋同截止的自动跳过。\n" +
                        "覆盖恢复：先清空现有全部条目再写入，不可撤销。",
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        if (text == null) return@TextButton
                        pendingImportText = null
                        scope.launch {
                            try {
                                val r = viewModel.importJson(text, replace = false)
                                toast(
                                    if (r.skipped == 0) "导入完成：新增 ${r.affected} 条"
                                    else "导入完成：新增 ${r.affected} 条，跳过 ${r.skipped} 条重复",
                                )
                            } catch (t: Throwable) {
                                toast("导入失败：${t.message}")
                            }
                        }
                    }) { Text("追加到现有") }
                    TextButton(onClick = {
                        if (text == null) return@TextButton
                        pendingImportText = null
                        scope.launch {
                            try {
                                val r = viewModel.importJson(text, replace = true)
                                toast("恢复完成：写入 ${r.affected} 条，原有 ${r.discarded} 条已清除")
                            } catch (t: Throwable) {
                                toast("导入失败：${t.message}")
                            }
                        }
                    }) { Text("覆盖恢复", color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportText = null }) { Text("取消") }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(postponeSnacks) },
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
                actions = {
                    Box {
                        var menuOpen by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuOpen = true }) {
                            Text(
                                "⋯",
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("导出 JSON") },
                                onClick = {
                                    menuOpen = false
                                    exportLauncher.launch("DDL清单_" + LocalDate.now() + ".json")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("导入 JSON") },
                                onClick = {
                                    menuOpen = false
                                    importLauncher.launch(
                                        arrayOf("application/json", "text/plain", "application/octet-stream"),
                                    )
                                },
                            )
                        }
                    }
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
                                    onPostpone = { target ->
                                        if (target == null) onEdit(item.row)
                                        else viewModel.postpone(item.row.id, target)
                                    },
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
    onPostpone: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 手势自绘（R44 立骨架，R46 重做映射）：左划最多拖到恰好露出「删除」即停靠；悬停中
    // 右划只收回，永不误触「已完成」。位移映射 = 工作区线性 1:1 跟手，越过 92dp/64dp
    // 边界才上橡皮筋（渐近 24dp 并硬夹），停靠位在数学上不可能再漂到阻尼渐近线；
    // 另设 10 秒看门狗：结算若丢失强制归位，杜绝「停在深水区」复现。
    val openPx = with(LocalDensity.current) { 92.dp.toPx() }
    val donePx = with(LocalDensity.current) { 64.dp.toPx() }
    val rubberPx = with(LocalDensity.current) { 24.dp.toPx() }   // 越界橡皮筋渐近上限
    val labelBias = with(LocalDensity.current) { 40.dp.toPx() } // 标签内收补偿（padding 24 + 半字宽 16）
    val revealBias = with(LocalDensity.current) { 50.dp.toPx() } // 揭出区补偿（padding 24 + 半内容宽 26：图标15+缝4+删除32）

    var raw by remember { mutableFloatStateOf(0f) }     // 线性等价行程（工作区内 = 视觉位移）
    var anchor by remember { mutableFloatStateOf(0f) }  // 松手落点：0 或 -openPx
    var dragging by remember { mutableStateOf(false) }
    var fromOpen by remember { mutableStateOf(false) }
    var dragStartRaw by remember { mutableFloatStateOf(0f) } // 本次手势起点的 raw

    // 位移映射：工作区（0..-openPx、0..+donePx）线性 1:1；越界才像拉橡皮筋（渐近上限），
    // 末端再 coerce 硬夹一道保险
    fun visualFor(r: Float): Float = when {
        r < -openPx -> -(openPx + rubberPx * (1f - exp((r + openPx) / rubberPx)))
        r > donePx -> donePx + rubberPx * (1f - exp(-(r - donePx) / rubberPx))
        else -> r
    }.coerceIn(-(openPx + rubberPx), donePx + rubberPx)

    fun settle() {
        dragging = false
        if (fromOpen) {
            // 划回来（R45）：从悬停向右累计行进 0.75×露出位即收拢——代价与划出对称，
            // 不再被阻尼曲线的深水区吞掉行程；收回途中永不触发「标为已完成」
            anchor = if (raw - dragStartRaw >= openPx * 0.75f) 0f else -openPx
        } else when {
            raw <= -openPx * 0.75f -> anchor = -openPx
            raw >= donePx && !row.isDone -> { onToggleDone(); anchor = 0f }
            else -> anchor = 0f
        }
        raw = anchor
    }

    val visualState = animateFloatAsState(
        targetValue = if (dragging) visualFor(raw) else anchor,
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
    // 看门狗（R46）：任何路径若让 dragging 悬而不决（结算丢失），10 秒强制归位，
    // 彻底杜绝「停靠位停在深水区」的病例；正常手势远短于此，永不触发
    LaunchedEffect(dragging) {
        if (dragging) {
            delay(10_000)
            if (dragging) settle()
        }
    }

    val errBg = MaterialTheme.colorScheme.errorContainer
    val onErr = MaterialTheme.colorScheme.onErrorContainer
    val doneBg = MaterialTheme.colorScheme.primaryContainer
    val onDone = MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            // 点击收敛整行（R46）：卡片本体不再自带 clickable（Card(onClick) 参与横向手势
            // 竞争，是结算丢失的头号嫌疑）。悬停中点本体 = 收拢（读快照态保新鲜），
            // 未悬停点本体 = 进编辑器；红底点按仍走下方 bg 的 clickable(enabled=docked) 删除
            .pointerInput(row.id) {
                detectTapGestures {
                    if (anchor <= -openPx * 0.95f) {
                        anchor = 0f
                        raw = 0f
                    } else onClick()
                }
            }
            // 手势挂整行（R45）：从卡片或露出的底衬任意位置起划都能拖；松手必经 settle
            .pointerInput(row.id) {
                detectHorizontalDragGestures(
                    onDragStart = { _ ->
                        dragStartRaw = raw
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
        // 揭出区「垃圾桶＋删除」（R47，I1 定款）：亮暗随主题 error/onError 自动适配
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .graphicsLayer { alpha = ((-visualState.value) / (openPx * 0.6f)).coerceIn(0f, 1f) }
                // 居中补偿承 R46：CenterEnd 锚定右缘 + 半程位移会让内容心偏出中点，
                // 内收 revealBias 后恰落揭示区中点
                .offset { IntOffset((visualState.value * 0.5f + revealBias).roundToInt(), 0) }
                .padding(end = 24.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_del),
                contentDescription = null,
                tint = onErr,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "删除",
                color = onErr,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = "完成",
            color = onDone,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer { alpha = (visualState.value / donePx).coerceIn(0f, 1f) }
                // 同上（R46）：右向揭出区 64dp 更窄，半程+40dp 内收让「完成」字心落中点
                .offset { IntOffset((visualState.value * 0.5f - labelBias).roundToInt(), 0) }
                .padding(start = 20.dp),
        )
        Box(
            Modifier
                .offset { IntOffset(visualState.value.roundToInt(), 0) },
        ) {
            DdlItemCard(row = row, nowMillis = nowMillis, overdue = overdue, onPostpone = onPostpone)
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

/** CallChip ease-out（0.23, 1, 0.32, 1）：墓碑槽内滚入、按压等小动效统一用这条缓出曲线。 */
private val EaseOutChip = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** 墓碑槽（R48 重做成「实底呼叫条」定款，U2 虚线擦除框退役）：实底圆角小框 =
 *  「垃圾桶图标槽 + 已删名 + 秒表 + 撤销符」，倒计时本体改成进度读条——一条
 *  onSurfaceVariant 半透明洗色自左向右读满（等价 transform: scaleX、origin left），
 *  读满即窗口关闭、整槽被 GC 摘除（列表 animateItem 出场淡出）；点槽即以原 id 复活。
 *  滚入/按压走 240ms/160ms 缓出（blur 滚依赖 API 31+，位移+淡入已足型）。 */
@Composable
private fun TombCard(tomb: Tombstone, nowMillis: Long, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val initial = ((tomb.expiresAtMillis - nowMillis) / DdlListViewModel.WINDOW_MILLIS.toFloat())
        .coerceIn(0f, 1f)
    // 读条进度 = 已度过占比：首帧从当前钟面折算的百分位起步（0 → 1 自左向右线性读满），
    // 读满的瞬间 GC 恰好摘除，两相衔接；中途重组/滚回视口不重放（起点随时钟校正）
    val progress = remember(tomb.row.id) { Animatable((1f - initial).coerceIn(0f, 1f)) }
    LaunchedEffect(tomb.row.id) {
        if (initial > 0f) {
            progress.animateTo(
                1f,
                tween(
                    durationMillis = (initial * DdlListViewModel.WINDOW_MILLIS).toInt().coerceAtLeast(1),
                    easing = LinearEasing,
                ),
            )
        }
    }
    // 图标与撤销符滚入（glyph 'in' 定款）：自下 70% 归位 + 淡入，240ms 缓出
    val glyphIn = remember(tomb.row.id) { Animatable(if (initial > 0f) 0f else 1f) }
    LaunchedEffect(tomb.row.id) {
        if (initial > 0f) glyphIn.animateTo(1f, tween(240, easing = EaseOutChip))
    }
    // 按压微缩（data-pressed 定款）：0.97，160ms——全槽点击写在这层，微缩就是唯一的按压反馈
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(160, easing = EaseOutChip),
        label = "tombPress",
    )
    val remainSec = (((tomb.expiresAtMillis - nowMillis) + 999) / 1_000).toInt().coerceIn(0, 5)
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(interactionSource = interaction, indication = null, onClick = onUndo),
    ) {
        // 进度读条（__fill 定款）：matchParentSize 自绘矩形，宽 = 满宽 × 已读占比，左锚不动
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    val f = progress.value.coerceIn(0f, 1f)
                    if (f > 0.002f) {
                        drawRect(
                            ink.copy(alpha = 0.10f),
                            size = Size(size.width * f, size.height),
                        )
                    }
                },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
        ) {
            Box(
                Modifier
                    .size(15.dp)
                    .graphicsLayer {
                        alpha = glyphIn.value
                        translationY = (1f - glyphIn.value) * size.height * 0.7f
                    },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_del),
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "已删除「${tomb.row.title}」",
                fontSize = 12.sp,
                fontFamily = HanFont,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$remainSec″",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FigureFont,
                    fontSize = 10.5.sp,
                    fontFeatureSettings = "tnum",
                ),
                color = ink.copy(alpha = 0.85f),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "点此撤销",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = HanFont,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.graphicsLayer {
                    alpha = glyphIn.value
                    translationY = (1f - glyphIn.value) * size.height * 0.7f
                },
            )
        }
    }
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
