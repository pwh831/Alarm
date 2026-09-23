package com.fitwake.alarm

import com.fitwake.pose.Difficulty
import com.fitwake.pose.Exercise
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime

data class Alarm(
    val id: Long = 0,
    val hour: Int,
    val minute: Int,
    /** 비어 있으면 한 번만 울리는 알람. */
    val repeatDays: Set<DayOfWeek> = emptySet(),
    val label: String = "",
    val enabled: Boolean = true,
    val exercise: Exercise = Exercise.SQUAT,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val targetReps: Int = 15,
    /** null이면 기기 기본 알람음. */
    val soundUri: String? = null,
    val vibrate: Boolean = true,
    val volumeRamp: Boolean = true,
) {
    init {
        require(hour in 0..23 && minute in 0..59) { "invalid time $hour:$minute" }
    }

    val isRepeating: Boolean get() = repeatDays.isNotEmpty()

    /**
     * [now] 이후(같은 시각 제외) 처음 울릴 시각.
     * 서머타임으로 해당 시각이 없는 날은 [ZonedDateTime.of] 규칙대로 뒤로 밀린다.
     */
    fun nextTrigger(now: ZonedDateTime): ZonedDateTime {
        val time = LocalTime.of(hour, minute)
        val today = now.toLocalDate()
        for (offset in 0L..7L) {
            val date = today.plusDays(offset)
            if (isRepeating && date.dayOfWeek !in repeatDays) continue
            val candidate = ZonedDateTime.of(date, time, now.zone)
            if (candidate.isAfter(now)) return candidate
        }
        error("unreachable: a matching day always exists within 8 days")
    }
}

/** 켜진 알람 중 가장 먼저 울릴 알람과 그 시각. */
fun List<Alarm>.nextAlarm(now: ZonedDateTime): Pair<Alarm, ZonedDateTime>? =
    filter { it.enabled }
        .map { it to it.nextTrigger(now) }
        .minByOrNull { it.second }

/** 요일 집합 ↔ 비트마스크 (월요일 = bit 0). DB 저장용. */
object DayMask {
    fun toMask(days: Set<DayOfWeek>): Int = days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }

    fun fromMask(mask: Int): Set<DayOfWeek> =
        DayOfWeek.entries.filterTo(mutableSetOf()) { mask and (1 shl (it.value - 1)) != 0 }
}
