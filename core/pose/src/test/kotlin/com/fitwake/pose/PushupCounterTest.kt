package com.fitwake.pose

import kotlin.test.Test
import kotlin.test.assertEquals

class PushupCounterTest {
    @Test
    fun countsPlankPushups() {
        val d = Driver(PushupCounter(Difficulty.NORMAL)) { Poses.pushup(it) }
        d.hold(175.0, 500)
        repeat(4) { d.rep(175.0, 80.0) }
        assertEquals(4, d.last!!.reps)
    }

    @Test
    fun halfPushupDoesNotCountOnNormal() {
        val d = Driver(PushupCounter(Difficulty.NORMAL)) { Poses.pushup(it) }
        d.hold(175.0, 500)
        repeat(3) { d.rep(175.0, 105.0) }
        assertEquals(0, d.last!!.reps)
    }

    @Test
    fun standingArmCurlDoesNotCount() {
        val d = Driver(PushupCounter(Difficulty.EASY)) { Poses.standingArmCurl(it) }
        d.hold(175.0, 500)
        repeat(3) { d.rep(175.0, 60.0) }
        assertEquals(0, d.last!!.reps)
        assertEquals(Hint.GET_HORIZONTAL, d.last!!.hint)
    }

    @Test
    fun kneePushupOnlyOnEasy() {
        val easy = Driver(PushupCounter(Difficulty.EASY)) { Poses.pushup(it, knees = true) }
        easy.hold(175.0, 500)
        repeat(2) { easy.rep(175.0, 90.0) }
        assertEquals(2, easy.last!!.reps)

        val normal = Driver(PushupCounter(Difficulty.NORMAL)) { Poses.pushup(it, knees = true) }
        normal.hold(175.0, 500)
        repeat(2) { normal.rep(175.0, 80.0) }
        assertEquals(0, normal.last!!.reps)
        assertEquals(Hint.KEEP_BODY_STRAIGHT, normal.last!!.hint)
    }

    @Test
    fun saggingHipsDoNotCount() {
        val d = Driver(PushupCounter(Difficulty.NORMAL)) { Poses.pushup(it, sag = 0.12f) }
        d.hold(175.0, 500)
        repeat(2) { d.rep(175.0, 80.0) }
        assertEquals(0, d.last!!.reps)
        assertEquals(Hint.KEEP_BODY_STRAIGHT, d.last!!.hint)
    }
}
