package com.fitwake.alarm

import com.fitwake.pose.Exercise
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WakeStatsTest {
    // 2026-09-23 수요일
    private val today = LocalDate.of(2026, 9, 23)

    private fun rec(
        daysAgo: Long,
        method: DismissMethod = DismissMethod.MISSION,
        exercise: Exercise? = Exercise.SQUAT,
        reps: Int = 15,
        seconds: Long = 90,
    ): WakeRecord {
        val start = Instant.parse("2026-09-23T22:00:00Z").minusSeconds(daysAgo * 86_400)
        return WakeRecord(today.minusDays(daysAgo), start, start.plusSeconds(seconds), method, exercise, reps)
    }

    @Test
    fun streakCountsConsecutiveMissionDays() {
        val records = listOf(rec(0), rec(1), rec(2), rec(4))
        assertEquals(3, WakeStats.streak(records, today))
    }

    @Test
    fun streakSurvivesUntilTodaysAlarm() {
        assertEquals(2, WakeStats.streak(listOf(rec(1), rec(2)), today))
        assertEquals(0, WakeStats.streak(listOf(rec(2), rec(3)), today))
    }

    @Test
    fun emergencyDayBreaksStreak() {
        val records = listOf(rec(0), rec(1, DismissMethod.EMERGENCY, exercise = null, reps = 0), rec(2))
        assertEquals(1, WakeStats.streak(records, today))
    }

    @Test
    fun multipleAlarmsSameDayCountOnce() {
        assertEquals(1, WakeStats.streak(listOf(rec(0), rec(0)), today))
    }

    @Test
    fun summaryAggregatesWeekAndMonth() {
        val records = listOf(
            rec(0, exercise = Exercise.SQUAT, reps = 15, seconds = 60), // 9/23 수
            rec(1, exercise = Exercise.PUSHUP, reps = 10, seconds = 120), // 9/22 화
            rec(3, exercise = Exercise.SQUAT, reps = 20), // 9/20 일 — 지난주
            rec(22, exercise = Exercise.SQUAT, reps = 30), // 9/1
            rec(23, exercise = Exercise.SQUAT, reps = 99), // 8/31 — 지난달
            rec(2, DismissMethod.EMERGENCY, exercise = null, reps = 0), // 9/21
        )
        val s = WakeStats.summary(records, today)
        assertEquals(mapOf(Exercise.SQUAT to 15, Exercise.PUSHUP to 10), s.weekReps)
        assertEquals(mapOf(Exercise.SQUAT to 65, Exercise.PUSHUP to 10), s.monthReps)
        assertEquals(1, s.monthEmergencyCount)
        assertEquals(2, s.streak)
    }

    @Test
    fun averageOnlyCountsMissions() {
        val records = listOf(rec(0, seconds = 60), rec(1, seconds = 120), rec(2, DismissMethod.EMERGENCY, seconds = 999))
        assertEquals(90L, WakeStats.summary(records, today).averageSecondsToDismiss)
        assertNull(WakeStats.summary(emptyList(), today).averageSecondsToDismiss)
    }
}
