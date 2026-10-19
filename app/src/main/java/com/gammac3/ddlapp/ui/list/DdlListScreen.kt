package com.gammac3.ddlapp.ui.list

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gammac3.ddlapp.DdlApplication
import com.gammac3.ddlapp.logic.DdlGrouper
import com.gammac3.ddlapp.model.DdlGroup
import com.gammac3.ddlapp.model.DdlRow
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
                title = { Text("DDL 清单", fontWeight = FontWeight.Bold) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onEdit(null) },
                modifier = Modifier.semantics { contentDescription = "添加 DDL" },
            ) { Text("＋", fontSize = 24.sp) }
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
                    FilterChip(
                        selected = state.filter == f,
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        )
                    }
                } else {
                    groupOrder.forEach { group ->
                        val rows = grouped[group].orEmpty()
                        if (rows.isEmpty()) return@forEach
                        item(key = "header_$group") {
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable(enabled = group == DdlGroup.DONE) {
                                        doneExpanded = !doneExpanded
                                    }
                                    .padding(top = 8.dp),
                            ) {
                                Text(
                                    text = buildString {
                                        append("${groupName(group)}（${rows.size}）")
                                        if (group == DdlGroup.DONE) append(if (doneExpanded) " ▾" else " ▸")
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (group == DdlGroup.OVERDUE) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary,
                                )
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
