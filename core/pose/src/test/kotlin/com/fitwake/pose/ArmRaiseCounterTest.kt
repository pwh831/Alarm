package com.fitwake.pose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArmRaiseCounterTest {
    @Test
    fun syntheticArmAngle() {
        val p = Poses.armRaise(120.0)
        assertEquals(
            120.0,
            angleDeg(p.getValue(Landmark.LEFT_HIP), p.getValue(Landmark.LEFT_SHOULDER), p.getValue(Landmark.LEFT_WRIST)),
            1.0,
        )
    }

    /** 각도는 "180 - 어깨 각도"이므로, 팔을 내린 상태가 170, 머리 위가 5 정도. */
    private fun driver(difficulty: Difficulty, right: (Double) -> Double = { it }) =
        Driver(ArmRaiseCounter(difficulty)) { metric -> Poses.armRaise(180.0 - metric, right(180.0 - metric)) }

    @Test
    fun countsFullRaises() {
        val d = driver(Difficulty.NORMAL)
        d.hold(170.0, 500)
        repeat(4) { d.rep(170.0, 10.0) }
        assertEquals(4, d.last!!.reps)
    }

    @Test
    fun shoulderHeightOnlyCountsOnEasy() {
        // 어깨 높이(어깨 각도 100°)까지만 든 경우
        for ((difficulty, expected) in listOf(Difficulty.EASY to 2, Difficulty.NORMAL to 0)) {
            val d = driver(difficulty)
            d.hold(170.0, 500)
            repeat(2) { d.rep(170.0, 80.0) }
            assertEquals(expected, d.last!!.reps, "difficulty=$difficulty")
        }
    }

    @Test
    fun raisingOneArmDoesNotCount() {
        val d = driver(Difficulty.NORMAL, right = { 10.0 })
        d.hold(170.0, 500)
        repeat(3) { d.rep(170.0, 10.0) }
        assertEquals(0, d.last!!.reps)
    }

    @Test
    fun seesRequiresArmsAndHips() {
        val counter = ArmRaiseCounter(Difficulty.NORMAL)
        assertTrue(counter.sees(PoseFrame(0, Poses.armRaise(10.0))))
        assertEquals(false, counter.sees(PoseFrame(0, Poses.armRaise(10.0) - Landmark.LEFT_HIP - Landmark.RIGHT_HIP)))
    }
}
