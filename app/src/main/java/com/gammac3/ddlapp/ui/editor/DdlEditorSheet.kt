package com.gammac3.ddlapp.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gammac3.ddlapp.R
import com.gammac3.ddlapp.model.DdlRow
import com.gammac3.ddlapp.notify.CandidateLeads
import com.gammac3.ddlapp.notify.LeadLabels
import com.gammac3.ddlapp.ui.list.DdlDraft
import com.gammac3.ddlapp.ui.theme.FieldShape
import com.gammac3.ddlapp.ui.theme.FigureFont
import com.gammac3.ddlapp.ui.theme.UiFont
import com.gammac3.ddlapp.ui.theme.SectionLabel
import com.gammac3.ddlapp.ui.theme.SerifFont
import com.gammac3.ddlapp.ui.theme.StampChip
import com.gammac3.ddlapp.ui.theme.paperFieldColors
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

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
    val context = LocalContext.current

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
    // 输入框：透明底 + 1dp 细边，聚焦时细线转为陶土色（纸上的铅笔框）
    val fieldColors = paperFieldColors()
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 标题：宋体，像一页纸的页眉，下面压一条细线
            Column(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(if (editing == null) R.string.add_ddl else R.string.edit_ddl),
                    fontFamily = SerifFont,
                    fontSize = 23.sp,
                    lineHeight = 30.sp,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text(stringResource(R.string.editor_title_required)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = FieldShape,
                colors = fieldColors,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = SerifFont,
                    fontSize = 17.sp,
                    letterSpacing = 0.2.sp,
                ),
            )
            // 日期/时间展示值：模式出自资源，系统英文下出「Nov 4, 2026」
            val editorLocale = remember(context) { context.resources.configuration.locales[0] }
            val datePattern = remember(context) { context.getString(R.string.fmt_editor_date) }
            val dateText = remember(z, editorLocale, datePattern) {
                z.toLocalDate().format(DateTimeFormatter.ofPattern(datePattern, editorLocale))
            }
            val timeText = remember(z) {
                z.hour.toString().padStart(2, '0') + ":" + z.minute.toString().padStart(2, '0')
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(3f)) {
                    OutlinedTextField(
                        value = stringResource(R.string.editor_date_value, dateText),
                        onValueChange = {}, readOnly = true,
                        label = { Text(stringResource(R.string.editor_date)) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        shape = FieldShape,
                        colors = fieldColors,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FigureFont,
                            fontSize = 15.sp,
                        ),
                    )
                    Box(
                        Modifier
                            .matchParentSize()
                            .clickable(
                                onClickLabel = stringResource(R.string.editor_click_change_date),
                            ) { showDate = true },
                    )
                }
                Box(Modifier.weight(2f)) {
                    OutlinedTextField(
                        value = stringResource(R.string.editor_time_value, timeText),
                        onValueChange = {}, readOnly = true,
                        label = { Text(stringResource(R.string.editor_time)) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        shape = FieldShape,
                        colors = fieldColors,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FigureFont,
                            fontSize = 15.sp,
                        ),
                    )
                    Box(
                        Modifier
                            .matchParentSize()
                            .clickable(
                                onClickLabel = stringResource(R.string.editor_click_change_time),
                            ) { showTime = true },
                    )
                }
            }
            OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text(stringResource(R.string.editor_note_optional)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = FieldShape,
                colors = fieldColors,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = UiFont,
                    fontSize = 14.sp,
                ),
            )
            // 小标题：字距拉开的小号标签 + 一条收尾细线（章节感）
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(stringResource(R.string.editor_leads_title))
                Box(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                StampChip(
                    text = stringResource(R.string.pill_muted),
                    selected = muted,
                    onClick = {
                        muted = !muted
                        if (muted) leads = emptySet()
                    },
                )
                LEAD_CHOICES.forEach { lead ->
                    val selected = lead in leads
                    StampChip(
                        text = LeadLabels.text(context, lead),
                        selected = selected,
                        onClick = {
                            leads = if (selected) leads - lead else leads + lead
                            muted = false
                        },
                    )
                }
            }
            if (editing == null && inPast) {
                Text(
                    stringResource(R.string.editor_past_error),
                    fontFamily = UiFont,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.5.sp,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 保存按钮使用墨色底。
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
                    modifier = Modifier.weight(2f).height(46.dp),
                    shape = RoundedCornerShape(3.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    border = if (canSave) null else BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Text(
                        stringResource(R.string.btn_save),
                        fontFamily = UiFont,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp,
                    )
                }
                if (editing != null) {
                    TextButton(onClick = { onToggleDone(editing) }, modifier = Modifier.weight(1.2f)) {
                        Text(
                            stringResource(
                                if (editing.isDone) R.string.editor_mark_not_done
                                else R.string.editor_mark_done,
                            ),
                            fontFamily = UiFont,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onDelete(editing) }, modifier = Modifier.weight(1f)) {
                        // I1 线描垃圾桶（R47 定款）：图标+删除红字横排，tint 走主题 error 色
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.ic_del),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.btn_delete),
                                fontFamily = UiFont,
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
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
                }) { Text(stringResource(R.string.btn_ok)) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.btn_cancel)) } },
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
            title = { Text(stringResource(R.string.editor_pick_time)) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    z = z.withHour(timeState.hour).withMinute(timeState.minute)
                    showTime = false
                }) { Text(stringResource(R.string.btn_ok)) }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.btn_cancel)) } },
        )
    }
}
