package com.gammac3.ddlapp.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gammac3.ddlapp.R
import com.gammac3.ddlapp.logic.DdlStatistics
import com.gammac3.ddlapp.ui.theme.Clay
import com.gammac3.ddlapp.ui.theme.ClayDeep
import com.gammac3.ddlapp.ui.theme.FigureFont
import com.gammac3.ddlapp.ui.theme.UiFont
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DdlStatisticsScreen(viewModel: DdlListViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val events by viewModel.completionEvents.collectAsState()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val stats = DdlStatistics.calculate(events.map { it.completedAt }, today, zone)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.statistics_title),
                        fontFamily = UiFont,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("‹", fontSize = 30.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatCard(stringResource(R.string.statistics_week), stats.weekCount, Modifier.weight(1f))
                StatCard(stringResource(R.string.statistics_month), stats.monthCount, Modifier.weight(1f))
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            stringResource(R.string.statistics_year),
                            fontFamily = UiFont,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            stats.yearCount.toString(),
                            fontFamily = FigureFont,
                            fontSize = 30.sp,
                            color = ClayDeep,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Heatmap(stats.days, today.year)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.statistics_heatmap_hint),
                            modifier = Modifier.weight(1f),
                            fontFamily = UiFont,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        listOf(0, 1, 2, 3, 4).forEach { level ->
                            Box(
                                Modifier.padding(start = 3.dp).size(10.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(heatColor(level)),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, count: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                label,
                fontFamily = UiFont,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                count.toString(),
                fontFamily = FigureFont,
                fontSize = 32.sp,
                color = ClayDeep,
            )
        }
    }
}

@Composable
private fun Heatmap(days: Map<LocalDate, Int>, year: Int) {
    val firstDay = LocalDate.of(year, 1, 1)
    val mondayBeforeYear = firstDay.minusDays((firstDay.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    val weekCount = ((LocalDate.of(year, 12, 31).toEpochDay() - mondayBeforeYear.toEpochDay()) / 7 + 1).toInt()
    val maxCount = days.values.maxOrNull() ?: 0
    val monthFormatter = rememberMonthFormatter()
    val horizontalState = rememberScrollState()

    Column {
        Row(
            Modifier.horizontalScroll(horizontalState),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            repeat(weekCount) { week ->
                val markerDate = mondayBeforeYear.plusDays((week * 7 + 3).toLong())
                val showMonth = markerDate.year == year && markerDate.dayOfMonth <= 7
                Text(
                    text = if (showMonth) markerDate.format(monthFormatter) else "",
                    modifier = Modifier.width(11.dp),
                    fontFamily = UiFont,
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.horizontalScroll(horizontalState),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            repeat(weekCount) { week ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(7) { dayIndex ->
                        val date = mondayBeforeYear.plusDays((week * 7 + dayIndex).toLong())
                        if (date.year == year) {
                            val count = days[date] ?: 0
                            val level = when {
                                count == 0 || maxCount == 0 -> 0
                                count * 4 >= maxCount * 3 -> 4
                                count * 4 >= maxCount * 2 -> 3
                                count * 4 >= maxCount -> 2
                                else -> 1
                            }
                            val description = stringResource(
                                R.string.statistics_day_count,
                                date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                                count,
                            )
                            Box(
                                Modifier.size(11.dp).clip(RoundedCornerShape(2.dp))
                                    .background(heatColor(level))
                                    .semantics {
                                        contentDescription = description
                                    },
                            )
                        } else {
                            Spacer(Modifier.size(11.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun heatColor(level: Int) = when (level) {
    1 -> Clay.copy(alpha = 0.30f)
    2 -> Clay.copy(alpha = 0.55f)
    3 -> Clay
    4 -> ClayDeep
    else -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun rememberMonthFormatter() = androidx.compose.runtime.remember {
    DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
}
