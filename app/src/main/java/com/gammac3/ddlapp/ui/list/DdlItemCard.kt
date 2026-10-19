package com.gammac3.ddlapp.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gammac3.ddlapp.model.DdlRow
import com.gammac3.ddlapp.ui.Format
import com.gammac3.ddlapp.ui.theme.DataFont
import com.gammac3.ddlapp.ui.theme.FigureFont

@Composable
fun DdlItemCard(
    row: DdlRow,
    nowMillis: Long,
    overdue: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick,
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
            ReminderPill(
                muted = row.muted,
                text = when {
                    row.muted -> "🔕 不提醒"
                    row.leadsMinutes.isEmpty() -> "🔔 到期时提醒"
                    else -> "🔔 提前 " + row.leadsMinutes.sorted()
                        .joinToString(" · ") { Format.leadText(it) }
                },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
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
    Text(
        text = text,
        fontSize = 11.sp,
        color = foreground,
        modifier = modifier
            .background(color = background, shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
