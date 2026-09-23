package com.fitwake.alarm

/** PRD AL-08: 스누즈는 알람당 최대 2회, 누를 때도 짧은 미션을 해야 한다. */
object Snooze {
    const val MAX_COUNT = 2
    const val MINUTES = 5
    const val MINI_MISSION_REPS = 3

    fun canSnooze(alarm: Alarm, snoozeCount: Int): Boolean = alarm.snoozeEnabled && snoozeCount < MAX_COUNT
}
