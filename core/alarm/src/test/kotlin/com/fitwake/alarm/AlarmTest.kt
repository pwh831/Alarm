package com.fitwake.alarm

import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AlarmTest {
    private val seoul = ZoneId.of("Asia/Seoul")

    // 2026-09-23은 수요일
    private fun at(day: Int, h: Int, m: Int, s: Int = 0, zone: ZoneId = seoul) =
        ZonedDateTime.of(2026, 9, day, h, m, s, 0, zone)

    @Test
    fun oneShotLaterToday() {
        assertEquals(at(23, 7, 0), Alarm(hour = 7, minute = 0).nextTrigger(at(23, 6, 30)))
    }

    @Test
    fun oneShotAlreadyPassedGoesToTomorrow() {
        assertEquals(at(24, 7, 0), Alarm(hour = 7, minute = 0).nextTrigger(at(23, 7, 30)))
    }

    @Test
    fun exactSameMinuteIsNotRepeated() {
        assertEquals(at(24, 7, 0), Alarm(hour = 7, minute = 0).nextTrigger(at(23, 7, 0)))
        assertEquals(at(24, 7, 0), Alarm(hour = 7, minute = 0).nextTrigger(at(23, 7, 0, 30)))
    }

    @Test
    fun weekdaysSkipWeekend() {
        val weekdays = setOf(MONDAY, DayOfWeek.TUESDAY, WEDNESDAY, DayOfWeek.THURSDAY, FRIDAY)
        val alarm = Alarm(hour = 6, minute = 30, repeatDays = weekdays)
        // 금요일 07:00 → 다음 월요일 06:30
        assertEquals(at(28, 6, 30), alarm.nextTrigger(at(25, 7, 0)))
        // 수요일 06:00 → 오늘 06:30
        assertEquals(at(23, 6, 30), alarm.nextTrigger(at(23, 6, 0)))
    }

    @Test
    fun singleRepeatDayWrapsAWeek() {
        val alarm = Alarm(hour = 8, minute = 0, repeatDays = setOf(WEDNESDAY))
        assertEquals(at(30, 8, 0), alarm.nextTrigger(at(23, 8, 1)))
    }

    @Test
    fun dstGapShiftsForward() {
        // 미국 2026-03-08 02:00~03:00은 존재하지 않는다
        val ny = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(2026, 3, 7, 23, 0, 0, 0, ny)
        val next = Alarm(hour = 2, minute = 30).nextTrigger(now)
        assertEquals(8, next.dayOfMonth)
        assertEquals(3, next.hour)
        assertEquals(30, next.minute)
    }

    @Test
    fun nextAlarmPicksEarliestEnabled() {
        val now = at(23, 22, 0)
        val a = Alarm(id = 1, hour = 7, minute = 0)
        val b = Alarm(id = 2, hour = 6, minute = 0, enabled = false)
        val c = Alarm(id = 3, hour = 23, minute = 0)
        assertEquals(3L, listOf(a, b, c).nextAlarm(now)!!.first.id)
        assertEquals(1L, listOf(a, b).nextAlarm(now)!!.first.id)
        assertNull(listOf(b).nextAlarm(now))
    }

    @Test
    fun randomMissionPicksFromPool() {
        val alarm = Alarm(hour = 7, minute = 0, exercise = null)
        val picked = (1..50).map { alarm.pickExercise(kotlin.random.Random(it)) }.toSet()
        assertEquals(RANDOM_EXERCISES.toSet(), picked)
        assertEquals(com.fitwake.pose.Exercise.ARM_RAISE, alarm.copy(exercise = com.fitwake.pose.Exercise.ARM_RAISE).pickExercise())
    }

    @Test
    fun dayMaskRoundTrip() {
        val days = setOf(MONDAY, WEDNESDAY, SATURDAY, SUNDAY)
        assertEquals(0b1100101, DayMask.toMask(days))
        assertEquals(days, DayMask.fromMask(DayMask.toMask(days)))
        assertEquals(emptySet(), DayMask.fromMask(0))
    }
}
