package com.gammac3.ddlapp.capture

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gammac3.ddlapp.DdlApplication
import com.gammac3.ddlapp.R
import com.gammac3.ddlapp.data.DdlEntity
import com.gammac3.ddlapp.notify.CandidateLeads
import com.gammac3.ddlapp.notify.LeadLabels
import com.gammac3.ddlapp.ui.theme.DdlAppTheme
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 自动识别的浮卡：Dialog 主题 Activity + 居中浮动卡。
 * 只负责展示候选、让用户确认/修改，然后复用 DdlRepository 入库。
 */
class DdlCaptureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0.35f)

        val payload = CaptureSession.pending
        if (payload == null) {
            finish()
            return
        }

        setContent {
            DdlAppTheme {
                CaptureCard(
                    payload = payload,
                    onCancel = { closeWithoutSaving() },
                    onSaved = { id ->
                        CaptureSession.pending = null
                        CaptureSession.cardShown = false
                        CaptureSession.onSaved?.invoke(id)
                        finish()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        CaptureSession.cardShown = true
    }

    override fun onStop() {
        super.onStop()
        // 用户按 Home/切走：浮卡是临时卡，不留后台；关掉让悬浮球回来
        if (!isFinishing && !isChangingConfigurations) {
            closeWithoutSaving()
        }
    }

    private fun closeWithoutSaving() {
        CaptureSession.pending = null
        CaptureSession.cardShown = false
        CaptureSession.onCardClosed?.invoke()
        finish()
    }

    override fun onDestroy() {
        // 兜底：被系统收回/异常结束也要把球放出来，避免 cardOpen 永久卡住
        if (isFinishing && CaptureSession.cardOpen) {
            CaptureSession.cardOpen = false
            CaptureSession.cardShown = false
            CaptureSession.pending = null
            CaptureSession.onCardClosed?.invoke()
        }
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CaptureCard(
    payload: CapturePayload,
    onCancel: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as DdlApplication
    val repository = app.container.repository
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }
    val leadChoices = remember { CandidateLeads.ALL.filter { it > 0 } }

    // 默认选第一个未过期的候选；整页只有过去时间时保留第一个，让用户看到提示再改
    val initial = remember(payload) {
        val openedAt = System.currentTimeMillis()
        payload.candidates.firstOrNull { candidate ->
            candidate.deadline == null ||
                candidate.deadline.toInstant().toEpochMilli() > openedAt
        } ?: payload.candidates.firstOrNull()
            ?: DdlCandidate("", null, 0f, "")
    }
    val datedCandidates = remember(payload) { payload.candidates.filter { it.deadline != null } }
    val initialIndex = remember(payload) {
        datedCandidates.indexOfFirst { candidate ->
            candidate.deadline == initial.deadline && candidate.title == initial.title
        }.coerceAtLeast(0)
    }
    val noText = initial.confidence == 0f && initial.title.isBlank() && initial.deadline == null
    val noDeadline = initial.deadline == null

    var selectedIndex by rememberSaveable { mutableIntStateOf(initialIndex) }
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var note by rememberSaveable { mutableStateOf("") }
    var deadlineMillis by rememberSaveable {
        mutableLongStateOf(
            initial.deadline?.toInstant()?.toEpochMilli()
                ?: ZonedDateTime.now(zone).plusHours(1).withSecond(0).withNano(0)
                    .toInstant().toEpochMilli(),
        )
    }
    var leads by rememberSaveable { mutableStateOf(ArrayList<Long>()) }
    var muted by rememberSaveable { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var duplicateWarning by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val z = Instant.ofEpochMilli(deadlineMillis).atZone(zone)
    val inPast = deadlineMillis <= nowMillis
    val canSave = title.isNotBlank() && !inPast && !saving

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        if (title.isBlank()) {
            delay(150)
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    BackHandler(onBack = onCancel)

    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surface,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = MaterialTheme.colorScheme.inverseSurface,
        selectedLabelColor = MaterialTheme.colorScheme.inverseOnSurface,
    )

    fun save(forceDuplicate: Boolean) {
        if (!canSave) return
        saving = true
        saveFailed = false
        duplicateWarning = false
        scope.launch {
            try {
                val existing = repository.observeAll().first()
                if (!forceDuplicate &&
                    CaptureDuplicate.isDuplicate(existing, title, deadlineMillis)
                ) {
                    duplicateWarning = true
                    saving = false
                    return@launch
                }
                val id = repository.upsert(
                    DdlEntity(
                        title = title.trim(),
                        note = note.trim(),
                        deadlineEpochMillis = deadlineMillis,
                        leadsMinutes = leads.distinct().sorted(),
                        isDone = false,
                        createdAt = System.currentTimeMillis(),
                        muted = muted,
                    )
                )
                onSaved(id)
            } catch (t: Throwable) {
                saveFailed = true
                saving = false
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCancel,
                ),
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 420.dp)
                .heightIn(max = 560.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_capture_sparkle),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.capture_card_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            stringResource(R.string.capture_card_subtitle),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onCancel) {
                        Text(
                            "×",
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (datedCandidates.size > 1) {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        datedCandidates.forEachIndexed { index, candidate ->
                            val selected = index == selectedIndex
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedIndex = index
                                    title = candidate.title
                                    candidate.deadline?.let {
                                        deadlineMillis = it.toInstant().toEpochMilli()
                                    }
                                    duplicateWarning = false
                                    saveFailed = false
                                },
                                label = {
                                    Text(
                                        candidate.deadline?.let { formatChip(context, it) }.orEmpty(),
                                    )
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
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        duplicateWarning = false
                        saveFailed = false
                    },
                    label = { Text(stringResource(R.string.editor_title_required)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    singleLine = true,
                )

                val editorLocale = remember(context) { context.resources.configuration.locales[0] }
                val datePattern = remember(context) { context.getString(R.string.fmt_editor_date) }
                val dateText = remember(z, editorLocale, datePattern) {
                    z.toLocalDate().format(DateTimeFormatter.ofPattern(datePattern, editorLocale))
                }
                val timeText = remember(z) {
                    z.hour.toString().padStart(2, '0') + ":" +
                        z.minute.toString().padStart(2, '0')
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(3f)) {
                        OutlinedTextField(
                            value = stringResource(R.string.editor_date_value, dateText),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.editor_date)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
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
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.editor_time)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
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
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.editor_note_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Text(
                    stringResource(R.string.editor_leads_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = muted,
                        label = { Text(stringResource(R.string.pill_muted)) },
                        onClick = {
                            muted = !muted
                            if (muted) leads = ArrayList()
                        },
                        colors = chipColors,
                        border = BorderStroke(
                            1.dp,
                            if (muted) MaterialTheme.colorScheme.inverseSurface
                            else MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                    leadChoices.forEach { lead ->
                        val selected = lead in leads
                        FilterChip(
                            selected = selected,
                            label = { Text(LeadLabels.text(context, lead)) },
                            onClick = {
                                leads = ArrayList(
                                    if (selected) leads - lead else leads + lead,
                                )
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

                if (noText) {
                    Text(
                        stringResource(R.string.capture_no_text),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (noDeadline) {
                    Text(
                        stringResource(R.string.capture_no_deadline),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (inPast) {
                    Text(
                        stringResource(R.string.editor_past_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (saveFailed) {
                    Text(
                        stringResource(R.string.capture_save_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (duplicateWarning) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.capture_duplicate),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { save(forceDuplicate = true) }) {
                            Text(stringResource(R.string.capture_add_anyway))
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                    Button(
                        enabled = canSave,
                        onClick = { save(forceDuplicate = false) },
                        modifier = Modifier.weight(2f),
                    ) {
                        Text(stringResource(R.string.capture_btn_add))
                    }
                }

                Text(
                    stringResource(R.string.capture_privacy),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = z.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { picked ->
                        val newDate = Instant.ofEpochMilli(picked)
                            .atZone(ZoneOffset.UTC).toLocalDate()
                        deadlineMillis = newDate.atTime(z.hour, z.minute)
                            .atZone(zone).toInstant().toEpochMilli()
                    }
                    showDate = false
                }) { Text(stringResource(R.string.btn_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
    if (showTime) {
        val timeState = rememberTimePickerState(
            initialHour = z.hour,
            initialMinute = z.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text(stringResource(R.string.editor_pick_time)) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    deadlineMillis = z.withHour(timeState.hour)
                        .withMinute(timeState.minute)
                        .toInstant().toEpochMilli()
                    showTime = false
                }) { Text(stringResource(R.string.btn_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTime = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
        )
    }
}

private fun formatChip(context: Context, deadline: ZonedDateTime): String {
    val locale = context.resources.configuration.locales[0]
    val pattern = context.getString(R.string.fmt_capture_chip)
    return deadline.format(DateTimeFormatter.ofPattern(pattern, locale))
}
