package com.gammac3.ddlapp.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gammac3.ddlapp.R
import com.gammac3.ddlapp.model.DdlRow
import com.gammac3.ddlapp.notify.CandidateLeads
import com.gammac3.ddlapp.notify.LeadLabels
import com.gammac3.ddlapp.ui.list.DdlDraft
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

private val LEAD_CHOICES: List<Long> = CandidateLeads.ALL.filter { it > 0 }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DdlEditorSheet(
    open: Boolean,
    editing: DdlRow?,
    onDismiss: () -> Unit,
    onSave: (DdlDraft, Long?) -> Unit,
    onToggleDone: (DdlRow) -> Unit,
    onDelete: (DdlRow) -> Unit,
) {
    if (!open) return
    val zone = remember { ZoneId.systemDefault() }
    val stateKey = editing?.id

    var title by remember(stateKey) { mutableStateOf(editing?.title.orEmpty()) }
    var note by remember(stateKey) { mutableStateOf(editing?.note.orEmpty()) }
    var z by remember(stateKey) {
        mutableStateOf(
            if (editing == null) {
                ZonedDateTime.now(zone).plusHours(1).withSecond(0).withNano(0)
            } else {
                Instant.ofEpochMilli(editing.deadlineMillis).atZone(zone)
            }
        )
    }
    var leads by remember(stateKey) { mutableStateOf(editing?.leadsMinutes?.toSet() ?: emptySet()) }
    var muted by remember(stateKey) { mutableStateOf(editing?.muted ?: false) }

    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    val inPast = ZonedDateTime.now(zone).toInstant().toEpochMilli() >= z.toInstant().toEpochMilli()
    val canSave = title.isNotBlank() && (editing != null || !inPast)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surface,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = MaterialTheme.colorScheme.inverseSurface,
        selectedLabelColor = MaterialTheme.colorScheme.inverseOnSurface,
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (editing == null) "添加 DDL" else "编辑 DDL",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedTextField(
                value = title, onValueChange = { title = it }, label = { Text("标题（必填）") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(3f)) {
                    OutlinedTextField(
                        value = "📅 ${z.year}年${z.monthValue}月${z.dayOfMonth}日",
                        onValueChange = {}, readOnly = true, label = { Text("日期") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                    )
                    Box(
                        Modifier
                            .matchParentSize()
                            .clickable(onClickLabel = "更改日期") { showDate = true },
                    )
                }
                Box(Modifier.weight(2f)) {
                    OutlinedTextField(
                        value = "⏰ ${z.hour.toString().padStart(2, '0')}:${z.minute.toString().padStart(2, '0')}",
                        onValueChange = {}, readOnly = true, label = { Text("时间") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                    )
                    Box(
                        Modifier
                            .matchParentSize()
                            .clickable(onClickLabel = "更改时间") { showTime = true },
                    )
                }
            }
            OutlinedTextField(
                value = note, onValueChange = { note = it }, label = { Text("备注（可选）") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Text("提前提醒（可多选）", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = muted,
                    label = { Text("不提醒") },
                    onClick = {
                        muted = !muted
                        if (muted) leads = emptySet()
                    },
                    colors = chipColors,
                    border = BorderStroke(
                        1.dp,
                        if (muted) MaterialTheme.colorScheme.inverseSurface
                        else MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
                LEAD_CHOICES.forEach { lead ->
                    val selected = lead in leads
                    FilterChip(
                        selected = selected,
                        label = { Text(LeadLabels.text(lead)) },
                        onClick = {
                            leads = if (selected) leads - lead else leads + lead
                            muted = false
                        },
                        colors = chipColors,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.inverseSurface
                            else MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                }
            }
            if (editing == null && inPast) {
                Text(
                    "截止时间必须晚于现在",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = canSave,
                    onClick = {
                        onSave(
                            DdlDraft(
                                title = title,
                                note = note,
                                deadlineMillis = z.toInstant().toEpochMilli(),
                                leadsMinutes = leads.sorted(),
                                muted = muted,
                            ),
                            editing?.id,
                        )
                    },
                    modifier = Modifier.weight(2f),
                ) { Text("保存") }
                if (editing != null) {
                    TextButton(onClick = { onToggleDone(editing) }, modifier = Modifier.weight(1f)) {
                        Text(if (editing.isDone) "标为未完成" else "已完成")
                    }
                    TextButton(onClick = { onDelete(editing) }, modifier = Modifier.weight(1f)) {
                        // I1 线描垃圾桶（R47 定款）：图标+删除红字横排，tint 走主题 error 色
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.ic_del),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = z.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { picked ->
                        val newDate = Instant.ofEpochMilli(picked).atZone(ZoneOffset.UTC).toLocalDate()
                        z = newDate.atTime(z.hour, z.minute).atZone(zone)
                    }
                    showDate = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("取消") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
    if (showTime) {
        val timeState = rememberTimePickerState(
            initialHour = z.hour, initialMinute = z.minute, is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("选择时间") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    z = z.withHour(timeState.hour).withMinute(timeState.minute)
                    showTime = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("取消") } },
        )
    }
}
