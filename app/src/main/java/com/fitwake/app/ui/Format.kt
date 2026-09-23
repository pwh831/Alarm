package com.fitwake.app.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.fitwake.app.R
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** 월요일부터 시작하는 요일 순서. */
val weekOrder: List<DayOfWeek> = DayOfWeek.entries

@Composable
fun formatTime(hour: Int, minute: Int): String {
    val is24h = DateFormat.is24HourFormat(LocalContext.current)
    val pattern = if (is24h) "HH:mm" else "a h:mm"
    return LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

fun DayOfWeek.shortName(): String = getDisplayName(TextStyle.SHORT, Locale.getDefault())

@Composable
fun formatRepeat(days: Set<DayOfWeek>): String = when {
    days.isEmpty() -> stringResource(R.string.repeat_once)
    days.size == 7 -> stringResource(R.string.repeat_every_day)
    days == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> stringResource(R.string.repeat_weekends)
    days.size == 5 && DayOfWeek.SATURDAY !in days && DayOfWeek.SUNDAY !in days ->
        stringResource(R.string.repeat_weekdays)
    else -> weekOrder.filter { it in days }.joinToString(" ") { it.shortName() }
}

/** PRD AL-05: "7시간 23분 후에 울려요". 1분 미만은 올려서 1분으로 보여준다. */
@Composable
fun formatRemaining(duration: Duration): String {
    val totalMinutes = ((duration.seconds + 59) / 60).coerceAtLeast(1)
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes / 60) % 24
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> stringResource(R.string.remaining_days, days, hours)
        hours > 0 -> stringResource(R.string.remaining_hours, hours, minutes)
        else -> stringResource(R.string.remaining_minutes, minutes)
    }
}
