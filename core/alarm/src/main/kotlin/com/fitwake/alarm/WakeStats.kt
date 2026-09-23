package com.fitwake.alarm

import com.fitwake.pose.Exercise
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class DismissMethod { MISSION, EMERGENCY }

/** 알람 한 번의 기상 기록 (PRD ST-01, ST-02). */
data class WakeRecord(
    /** 알람이 울린 날 (현지 날짜). */
    val date: LocalDate,
    val ringStartedAt: Instant,
    val dismissedAt: Instant,
    val method: DismissMethod,
    /** 미션으로 끈 경우의 운동. */
    val exercise: Exercise?,
    val reps: Int,
    val snoozeCount: Int = 0,
) {
    val secondsToDismiss: Long get() = Duration.between(ringStartedAt, dismissedAt).seconds.coerceAtLeast(0)
}

data class WakeSummary(
    /** 연속 기상 일수. 오늘 아직 기록이 없어도 어제까지 이어졌으면 유지된다. */
    val streak: Int,
    val weekReps: Map<Exercise, Int>,
    val monthReps: Map<Exercise, Int>,
    val averageSecondsToDismiss: Long?,
    val monthEmergencyCount: Int,
)

/** 기록 → 통계 (PRD ST-03, ST-04). */
object WakeStats {
    /** 미션으로 알람을 끈 날. 긴급 해제만 한 날은 성공이 아니다. */
    fun successDays(records: List<WakeRecord>): Set<LocalDate> =
        records.filter { it.method == DismissMethod.MISSION }.mapTo(mutableSetOf()) { it.date }

    fun streak(records: List<WakeRecord>, today: LocalDate): Int {
        val days = successDays(records)
        var day = if (today in days) today else today.minusDays(1)
        var count = 0
        while (day in days) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    fun repsByExercise(records: List<WakeRecord>, from: LocalDate, to: LocalDate): Map<Exercise, Int> =
        records
            .filter { it.method == DismissMethod.MISSION && it.exercise != null && it.date in from..to }
            .groupBy { it.exercise!! }
            .mapValues { (_, list) -> list.sumOf { it.reps } }

    fun summary(records: List<WakeRecord>, today: LocalDate): WakeSummary {
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val monthStart = today.withDayOfMonth(1)
        val missions = records.filter { it.method == DismissMethod.MISSION }
        return WakeSummary(
            streak = streak(records, today),
            weekReps = repsByExercise(records, weekStart, today),
            monthReps = repsByExercise(records, monthStart, today),
            averageSecondsToDismiss = missions.takeIf { it.isNotEmpty() }?.map { it.secondsToDismiss }?.average()?.toLong(),
            monthEmergencyCount = records.count { it.method == DismissMethod.EMERGENCY && it.date in monthStart..today },
        )
    }
}
