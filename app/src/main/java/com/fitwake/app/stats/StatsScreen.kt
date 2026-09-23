package com.fitwake.app.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitwake.alarm.DismissMethod
import com.fitwake.alarm.WakeRecord
import com.fitwake.alarm.WakeStats
import com.fitwake.app.R
import com.fitwake.app.ui.formatTime
import com.fitwake.app.ui.label
import com.fitwake.app.ui.shortName
import com.fitwake.app.ui.weekOrder
import com.fitwake.app.wakeLogRepository
import com.fitwake.pose.Exercise
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** 기록·통계 (PRD ST-01~ST-04). */
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val records by context.wakeLogRepository.records.collectAsStateWithLifecycle(initialValue = emptyList())
    val today = remember { LocalDate.now() }
    val summary = remember(records) { WakeStats.summary(records, today) }
    val successDays = remember(records) { WakeStats.successDays(records) }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    BackHandler(onBack = onBack)

    LazyColumn(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(stringResource(R.string.stats), style = MaterialTheme.typography.headlineSmall)
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.streak_label), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.streak_days, summary.streak),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RepsCard(stringResource(R.string.this_week), summary.weekReps, Modifier.weight(1f))
                RepsCard(stringResource(R.string.this_month), summary.monthReps, Modifier.weight(1f))
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        summary.averageSecondsToDismiss?.let { stringResource(R.string.avg_dismiss, it / 60, it % 60) }
                            ?: stringResource(R.string.avg_dismiss_none),
                    )
                    Text(stringResource(R.string.month_emergency, summary.monthEmergencyCount))
                }
            }
        }
        item {
            MonthCalendar(
                month = month,
                successDays = successDays,
                today = today,
                onPrev = { month = month.minusMonths(1) },
                onNext = { month = month.plusMonths(1) },
            )
        }
        item { Text(stringResource(R.string.recent_records), style = MaterialTheme.typography.titleMedium) }
        if (records.isEmpty()) {
            item { Text(stringResource(R.string.no_records), style = MaterialTheme.typography.bodyMedium) }
        }
        items(records.take(30)) { RecordRow(it) }
    }
}

@Composable
private fun RepsCard(title: String, reps: Map<Exercise, Int>, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (reps.isEmpty()) {
                Text("-", style = MaterialTheme.typography.bodyMedium)
            }
            Exercise.entries.forEach { e ->
                reps[e]?.let { Text(stringResource(R.string.reps_line, e.label(), it)) }
            }
        }
    }
}

/** 미션으로 일어난 날을 표시하는 달력 (PRD ST-04). */
@Composable
private fun MonthCalendar(
    month: YearMonth,
    successDays: Set<LocalDate>,
    today: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPrev) { Text("‹") }
                Text(
                    month.format(DateTimeFormatter.ofPattern(stringResource(R.string.month_pattern))),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onNext, enabled = month < YearMonth.from(today)) { Text("›") }
            }
            Row {
                weekOrder.forEach {
                    Text(it.shortName(), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                }
            }
            // 월요일 시작 달력: 1일 앞의 빈칸 수 = (요일 값 - 1)
            val leading = month.atDay(1).dayOfWeek.value - 1
            val cells = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
            cells.chunked(7).forEach { week ->
                Row {
                    week.forEach { day -> DayCell(day, day in successDays, day == today, Modifier.weight(1f)) }
                    repeat(7 - week.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate?, success: Boolean, isToday: Boolean, modifier: Modifier) {
    Box(modifier.aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
        if (day == null) return@Box
        val background = when {
            success -> MaterialTheme.colorScheme.primary
            isToday -> MaterialTheme.colorScheme.surfaceVariant
            else -> null
        }
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .then(if (background != null) Modifier.background(background) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = if (success) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun RecordRow(record: WakeRecord) {
    val start = record.ringStartedAt.atZone(ZoneId.systemDefault())
    val date = record.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    val detail = when (record.method) {
        DismissMethod.MISSION -> stringResource(
            R.string.record_mission,
            record.exercise.label(),
            record.reps,
            record.secondsToDismiss / 60,
            record.secondsToDismiss % 60,
        )
        DismissMethod.EMERGENCY -> stringResource(R.string.record_emergency)
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("$date · ${formatTime(start.hour, start.minute)}", style = MaterialTheme.typography.bodyMedium)
        Text(
            if (record.snoozeCount > 0) detail + " · " + stringResource(R.string.record_snoozed, record.snoozeCount) else detail,
            style = MaterialTheme.typography.bodySmall,
            color = if (record.method == DismissMethod.MISSION) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}
