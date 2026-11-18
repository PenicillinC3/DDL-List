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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gammac3.ddlapp.DdlApplication
import com.gammac3.ddlapp.capture.CaptureEntryCapsule
import com.gammac3.ddlapp.capture.CaptureSettingsSheet
import com.gammac3.ddlapp.data.DdlBackup
import com.gammac3.ddlapp.logic.DdlGrouper
import com.gammac3.ddlapp.R
import com.gammac3.ddlapp.model.DdlGroup
import com.gammac3.ddlapp.model.DdlRow
import com.gammac3.ddlapp.ui.Format
import com.gammac3.ddlapp.ui.theme.Clay
import com.gammac3.ddlapp.ui.theme.FigureFont
import com.gammac3.ddlapp.ui.theme.UiFont
import com.gammac3.ddlapp.ui.theme.SerifFont
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

/** 组名（资源化：系统切英文后自动映射 Overdue/Today/…）。 */
@Composable
private fun groupName(g: DdlGroup): String = when (g) {
    DdlGroup.OVERDUE -> stringResource(R.string.group_overdue)
    DdlGroup.TODAY -> stringResource(R.string.group_today)
    DdlGroup.TOMORROW -> stringResource(R.string.group_tomorrow)
    DdlGroup.THIS_WEEK -> stringResource(R.string.group_next_week)
    DdlGroup.LATER -> stringResource(R.string.group_later)
    DdlGroup.DONE -> stringResource(R.string.group_done)
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
    var captureSettingsOpen by rememberSaveable { mutableStateOf(false) }

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
                message = context.getString(
                    R.string.snack_postponed,
                    Format.absolute(context, p.newDeadlineMillis),
                ),
                actionLabel = context.getString(R.string.btn_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoPostpone(p.rowId)
        }
    }
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    // 复数计数（「N 条」/"N items"）：资源按系统语言出正确单复数
    val qty: (Int) -> String = { n -> context.resources.getQuantityString(R.plurals.items_plain, n, n) }
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
                        (context.contentResolver.openOutputStream(uri)
                            ?: error(context.getString(R.string.stream_open_failed)))
                            .use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    }
                    toast(context.getString(R.string.toast_export_done, qty(count)))
                } catch (t: Throwable) {
                    toast(context.getString(R.string.toast_export_failed, t.message))
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
                        (context.contentResolver.openInputStream(uri)
                            ?: error(context.getString(R.string.stream_read_failed)))
                            .bufferedReader().use { it.readText() }
                    }
                    // 无效文件在此整体拒绝，弹窗根本不出
                    pendingImportCount = DdlBackup.fromJson(text).size
                    pendingImportText = text
                } catch (t: Throwable) {
                    toast(context.getString(R.string.toast_import_failed, t.message))
                }
            }
        }
    }
    if (pendingImportText != null) {
        val text = pendingImportText // 定格快照：确认回调里用
        AlertDialog(
            onDismissRequest = { pendingImportText = null },
            title = { Text(stringResource(R.string.dialog_import_title, qty(pendingImportCount))) },
            text = {
                Text(stringResource(R.string.dialog_import_body))
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
                                    if (r.skipped == 0) {
                                        context.getString(R.string.toast_import_done, qty(r.affected))
                                    } else {
                                        context.getString(
                                            R.string.toast_import_done_dupes,
                                            qty(r.affected), qty(r.skipped),
                                        )
                                    },
                                )
                            } catch (t: Throwable) {
                                toast(context.getString(R.string.toast_import_failed, t.message))
                            }
                        }
                    }) { Text(stringResource(R.string.btn_append)) }
                    TextButton(onClick = {
                        if (text == null) return@TextButton
                        pendingImportText = null
                        scope.launch {
                            try {
                                val r = viewModel.importJson(text, replace = true)
                                toast(
                                    context.getString(
                                        R.string.toast_restore_done,
                                        qty(r.affected), qty(r.discarded),
                                    )
                                )
                            } catch (t: Throwable) {
                                toast(context.getString(R.string.toast_import_failed, t.message))
                            }
                        }
                    }) { Text(stringResource(R.string.btn_restore), color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportText = null }) { Text(stringResource(R.string.btn_cancel)) }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(postponeSnacks) },
        topBar = {
            Masthead(
                actions = {
                    CaptureEntryCapsule(onClick = { captureSettingsOpen = true })
                    Box {
                        var menuOpen by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuOpen = true }) {
                            Text(
                                "⋯",
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_export_json)) },
                                onClick = {
                                    menuOpen = false
                                    exportLauncher.launch(
                                        context.getString(R.string.export_file_prefix) +
                                            LocalDate.now() + ".json",
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_import_json)) },
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
            )
        },
        floatingActionButton = {
            NewDdlButton(onClick = { onEdit(null) })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 精确闹钟引导（Android 12+ 未授权）
            if (!exactOk) {
                Notice(
                    text = stringResource(R.string.banner_exact_alarm),
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:${context.packageName}"),
                            )
                        )
                    },
                )
            }
            // 通知权限被拒绝提示（Android 13+）
            if (notifDenied) {
                Notice(
                    text = stringResource(R.string.banner_notifications),
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    },
                )
            }
            // 电池优化豁免引导（准点保障）：厂商省电策略会压后台闹钟，加白名单显著减小延迟
            if (!batteryWhitelisted && !batteryCardDismissed) {
                Notice(
                    text = stringResource(R.string.banner_battery),
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}"),
                            )
                        )
                    },
                    trailing = {
                        TextButton(onClick = { batteryCardDismissed = true }) {
                            Text(
                                stringResource(R.string.banner_not_now),
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
            // 筛选：印刷式的文字页签 + 墨线下划线，不用胶囊
            FilterTabs(
                selected = state.filter,
                onSelect = { viewModel.setFilter(it) },
            )
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.rows.isEmpty() && state.tombstones.isEmpty()) {
                    item(key = "empty") {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 28.dp, vertical = 64.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = if (state.filter == DdlFilter.ALL) {
                                    stringResource(R.string.empty_all)
                                } else {
                                    stringResource(R.string.empty_filtered)
                                },
                                fontFamily = SerifFont,
                                fontSize = 19.sp,
                                lineHeight = 30.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(14.dp))
                            Box(
                                Modifier
                                    .width(30.dp)
                                    .height(1.dp)
                                    .background(Clay),
                            )
                        }
                    }
                } else {
                    groupOrder.forEach { group ->
                        val rows = grouped[group].orEmpty()
                        val tombs = tombsByGroup[group].orEmpty()
                        if (rows.isEmpty() && tombs.isEmpty()) return@forEach
                        item(key = "header_$group") {
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable(enabled = group == DdlGroup.DONE) {
                                        doneExpanded = !doneExpanded
                                    }
                                    .padding(top = 10.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (group == DdlGroup.OVERDUE) {
                                    OverdueSeal(count = rows.size)
                                } else {
                                    Text(
                                        text = groupName(group),
                                        fontFamily = UiFont,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 1.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "${rows.size}",
                                        fontFamily = FigureFont,
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 6.dp),
                                    )
                                }
                                // 编辑式的「行尾细线」：组名像一本书的章节，线把右侧留白收住
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .padding(start = 12.dp)
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant),
                                )
                                if (group == DdlGroup.DONE) {
                                    Text(
                                        text = if (doneExpanded) "▾" else "▸",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 8.dp),
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

    CaptureSettingsSheet(
        open = captureSettingsOpen,
        onDismiss = { captureSettingsOpen = false },
    )
}

/**
 * 报头：宋体字标 + 一枚陶土句点，下面一条 1dp 细线。
 * 没有底栏阴影、没有大色块——像一页纸的页眉。
 */
@Composable
private fun Masthead(actions: @Composable RowScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.app_name),
                fontFamily = SerifFont,
                fontSize = 25.sp,
                lineHeight = 32.sp,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                Modifier
                    .padding(start = 5.dp, top = 3.dp)
                    .size(5.dp)
                    .background(Clay, CircleShape),
            )
            Spacer(Modifier.weight(1f))
            actions()
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/** 筛选：印刷目录式的文字页签，选中项在字下压一条墨线（不是胶囊）。 */
@Composable
private fun FilterTabs(selected: DdlFilter, onSelect: (DdlFilter) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        DdlFilter.entries.forEach { f ->
            val on = f == selected
            val ink = MaterialTheme.colorScheme.onSurface
            Text(
                text = when (f) {
                    DdlFilter.ALL -> stringResource(R.string.chip_all)
                    DdlFilter.ACTIVE -> stringResource(R.string.chip_active)
                    DdlFilter.DONE -> stringResource(R.string.chip_done)
                },
                fontFamily = UiFont,
                fontSize = 13.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                letterSpacing = 0.4.sp,
                color = if (on) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(f) },
                    )
                    .padding(top = 10.dp, bottom = 11.dp)
                    .drawBehind {
                        if (on) {
                            val h = 1.5.dp.toPx()
                            drawRect(
                                color = ink,
                                topLeft = Offset(0f, size.height - h),
                                size = Size(size.width, h),
                            )
                        }
                    },
            )
        }
    }
}

/** 系统权限提示：一个陶土小方点 + 一行赭红小字，克制、可点。 */
@Composable
private fun Notice(
    text: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(4.dp)
                .background(MaterialTheme.colorScheme.error, CircleShape),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = text,
            fontFamily = UiFont,
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** 新建：墨色纸签，上方两角近直角、下方两角圆——像从纸堆里抽出来的一页。 */
@Composable
private fun NewDdlButton(onClick: () -> Unit) {
    val desc = stringResource(R.string.add_ddl)
    Row(
        Modifier
            .semantics { contentDescription = desc }
            .clip(
                RoundedCornerShape(
                    topStart = 2.dp,
                    topEnd = 2.dp,
                    bottomStart = 13.dp,
                    bottomEnd = 13.dp,
                )
            )
            .background(MaterialTheme.colorScheme.inverseSurface)
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 20.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "＋",
            fontFamily = UiFont,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
        Spacer(Modifier.width(7.dp))
        Text(
            stringResource(R.string.fab_new),
            fontFamily = UiFont,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
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
            .clip(RoundedCornerShape(18.dp))
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
        val deleteLabel = stringResource(R.string.btn_delete)
        val doneLabel = stringResource(R.string.swipe_done)
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
                text = deleteLabel,
                color = onErr,
                fontFamily = UiFont,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = doneLabel,
            color = onDone,
            fontFamily = UiFont,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
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
    val ink = MaterialTheme.colorScheme.onSurface
    val soft = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(13.dp))
            // 一张麦色便条：比纸卡低一档的纸，1dp 细边，没有阴影
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(13.dp),
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onUndo),
    ) {
        // 读秒条：陶土色一层薄墨自左向右读满，读满即被 GC 摘除
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    val f = progress.value.coerceIn(0f, 1f)
                    if (f > 0.002f) {
                        drawRect(
                            Clay.copy(alpha = 0.16f),
                            size = Size(size.width * f, size.height),
                        )
                    }
                },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 15.dp, end = 16.dp, top = 13.dp, bottom = 13.dp),
        ) {
            Box(
                Modifier
                    .size(14.dp)
                    .graphicsLayer {
                        alpha = glyphIn.value
                        translationY = (1f - glyphIn.value) * size.height * 0.7f
                    },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_del),
                    contentDescription = null,
                    tint = soft,
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(Modifier.width(9.dp))
            Text(
                text = stringResource(R.string.tomb_deleted, tomb.row.title),
                fontSize = 12.sp,
                fontFamily = UiFont,
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
                color = soft,
            )
            Spacer(Modifier.width(12.dp))
            // 撤销是这里唯一的行动点：常驻下划线，像纸上被划出来的一句
            Text(
                text = stringResource(R.string.tomb_tap_undo),
                fontSize = 12.sp,
                fontFamily = UiFont,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
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
    val sealText = stringResource(R.string.seal_overdue)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = sealText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.graphicsLayer {
                val p = press.value
                scaleX = 1.55f - 0.55f * p
                scaleY = 1.55f - 0.55f * p
                rotationZ = -9f + 7f * p
                alpha = (p * 4f).coerceIn(0f, 1f)
            }
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(2.dp),
                )
                .padding(start = 8.dp, end = 7.dp, top = 3.5.dp, bottom = 3.5.dp),
        )
        Text(
            text = "$count",
            fontFamily = FigureFont,
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
