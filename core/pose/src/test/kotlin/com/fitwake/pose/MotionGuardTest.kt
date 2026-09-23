package com.fitwake.pose

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MotionGuardTest {
    private val g = 9.81f
    private val rnd = Random(42)

    /** 50Hz 샘플. */
    private fun MotionGuard.feed(fromMs: Long, ms: Long, sample: (Long) -> Triple<Float, Float, Float>): Long {
        var t = fromMs
        while (t < fromMs + ms) {
            val (x, y, z) = sample(t)
            onAccelerometer(t, x, y, z)
            t += 20
        }
        return t
    }

    private fun jitter(amp: Float) = (rnd.nextFloat() - 0.5f) * 2 * amp

    @Test
    fun stillPhoneWithFloorVibrationIsNotMoving() {
        val guard = MotionGuard()
        val end = guard.feed(0, 5_000) { Triple(jitter(0.3f), g + jitter(0.3f), jitter(0.3f)) }
        assertFalse(guard.isMoving(end))
    }

    @Test
    fun shakingIsMovingThenSettles() {
        val guard = MotionGuard()
        var t = guard.feed(0, 1_000) { Triple(0f, g, 0f) }
        t = guard.feed(t, 500) { ms -> Triple(4f * sin(ms / 30.0).toFloat(), g, 0f) }
        assertTrue(guard.isMoving(t))
        t = guard.feed(t, 1_500) { Triple(0f, g, 0f) }
        assertFalse(guard.isMoving(t))
    }

    @Test
    fun pickingUpPhoneTiltsGravity() {
        val guard = MotionGuard()
        var t = guard.feed(0, 1_000) { Triple(0f, 0f, g) }
        // 1초 동안 눕혀 둔 폰을 세운다
        val start = t
        t = guard.feed(t, 1_000) { ms ->
            val a = (ms - start) / 1000.0 * Math.PI / 2
            Triple(0f, (g * sin(a)).toFloat(), (g * cos(a)).toFloat())
        }
        assertTrue(guard.isMoving(t))
    }
}
