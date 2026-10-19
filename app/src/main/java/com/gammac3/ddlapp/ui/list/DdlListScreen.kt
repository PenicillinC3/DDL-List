package com.gammac3.ddlapp.ui.list

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
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
            delay(1_000)
        }
    }
    val zone = remember { ZoneId.systemDefault() }
    var doneExpanded by rememberSaveable { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }

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
    LifecycleResumeEffect(Unit) {
        notifGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (notifGranted) notifDenied = false
        exactOk = (context.applicationContext as DdlApplication)
            .container.scheduler.canScheduleExact()
        onPauseOrDispose { }
    }

    LaunchedEffect(state.lastDeleted) {
        val deleted = state.lastDeleted
        if (deleted != null) {
            val result = snackbar.showSnackbar("已删除「${deleted.title}」", actionLabel = "撤销")
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreDeleted()
            if (result != SnackbarResult.ActionPerformed) viewModel.clearDeleted()
        }
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
        snackbarHost = { SnackbarHost(snackbar) },
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
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.rows.isEmpty()) {
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
                        if (rows.isEmpty()) return@forEach
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
                        items(rows, key = { it.id }) { row ->
                            SwipeRow(
                                row = row,
                                nowMillis = now,
                                overdue = group == DdlGroup.OVERDUE,
                                onClick = { onEdit(row) },
                                onDelete = { viewModel.delete(row) },
                                onToggleDone = { viewModel.toggleDone(row) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(
    row: DdlRow,
    nowMillis: Long,
    overdue: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleDone: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete(); true
            } else if (value == SwipeToDismissBoxValue.StartToEnd) {
                onToggleDone(); false // 不消失，仅切换完成状态
            } else false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = !row.isDone,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = "删除",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 20.dp),
                )
            }
        },
    ) {
        DdlItemCard(row = row, nowMillis = nowMillis, overdue = overdue, onClick = onClick)
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
