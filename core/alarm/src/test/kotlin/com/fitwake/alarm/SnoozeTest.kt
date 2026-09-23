package com.fitwake.alarm

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnoozeTest {
    @Test
    fun disabledByDefault() = assertFalse(Snooze.canSnooze(Alarm(hour = 7, minute = 0), 0))

    @Test
    fun limitedToMaxCount() {
        val alarm = Alarm(hour = 7, minute = 0, snoozeEnabled = true)
        assertTrue(Snooze.canSnooze(alarm, 0))
        assertTrue(Snooze.canSnooze(alarm, 1))
        assertFalse(Snooze.canSnooze(alarm, 2))
    }
}
