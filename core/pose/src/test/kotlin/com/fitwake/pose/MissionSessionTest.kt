package com.fitwake.pose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MissionSessionTest {
    private var t = 0L

    private fun MissionSession.feed(kneeDeg: Double, ms: Long, moving: Boolean = false, visible: Boolean = true): MissionSession.State {
        var state: MissionSession.State? = null
        repeat((ms / 33).toInt().coerceAtLeast(1)) {
            t += 33
            state = update(PoseFrame(t, if (visible) Poses.squat(kneeDeg) else emptyMap()), moving)
        }
        return state!!
    }

    private fun MissionSession.squat() {
        feed(175.0, 100)
        for (deg in listOf(150.0, 120.0, 90.0, 80.0, 80.0, 80.0)) feed(deg, 100)
        for (deg in listOf(100.0, 130.0, 160.0, 175.0, 175.0, 175.0)) feed(deg, 100)
    }

    @Test
    fun countsDownBeforeCounting() {
        val s = MissionSession(SquatCounter(Difficulty.NORMAL))
        assertEquals(3, s.feed(175.0, 33).countdownSec)
        assertEquals(2, s.feed(175.0, 1200).countdownSec)
        // 카운트다운 중의 스쿼트는 세지 않는다
        s.squat()
        val afterCountdown = s.feed(175.0, 1500)
        assertNull(afterCountdown.countdownSec)
        assertTrue(afterCountdown.started)
        assertEquals(0, afterCountdown.rep.reps)
        s.squat()
        assertEquals(1, s.feed(175.0, 100).rep.reps)
    }

    @Test
    fun countdownRestartsWhenBodyLeavesFrame() {
        val s = MissionSession(SquatCounter(Difficulty.NORMAL))
        s.feed(175.0, 2500)
        s.feed(175.0, 100, visible = false)
        assertEquals(3, s.feed(175.0, 33).countdownSec)
        assertFalse(s.feed(175.0, 1000).started)
    }

    @Test
    fun movingPhoneResetsCountdownAndPausesCounting() {
        val s = MissionSession(SquatCounter(Difficulty.NORMAL))
        s.feed(175.0, 2500)
        assertEquals(Hint.PHONE_MOVING, s.feed(175.0, 100, moving = true).rep.hint)
        assertEquals(3, s.feed(175.0, 33).countdownSec)
        s.feed(175.0, 3100)

        // 폰을 들고 흔드는 동안의 "스쿼트"는 세지 않는다
        s.feed(175.0, 100, moving = true)
        for (deg in listOf(120.0, 80.0, 80.0, 120.0, 175.0)) s.feed(deg, 150, moving = true)
        val state = s.feed(175.0, 100)
        assertEquals(0, state.rep.reps)
    }
}
