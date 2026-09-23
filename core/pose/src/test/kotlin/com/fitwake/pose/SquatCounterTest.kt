package com.fitwake.pose

import kotlin.test.Test
import kotlin.test.assertEquals

class SquatCounterTest {
    private fun driver(difficulty: Difficulty = Difficulty.NORMAL, pose: (Double) -> Map<Landmark, Point> = { Poses.squat(it) }) =
        Driver(SquatCounter(difficulty), pose)

    @Test
    fun countsFullSquats() {
        val d = driver()
        d.hold(175.0, 500)
        repeat(5) { d.rep(175.0, 85.0) }
        assertEquals(5, d.last!!.reps)
        assertEquals(Phase.TOP, d.last!!.phase)
    }

    @Test
    fun shallowSquatDependsOnDifficulty() {
        for ((difficulty, expected) in listOf(Difficulty.EASY to 3, Difficulty.NORMAL to 0, Difficulty.HARD to 0)) {
            val d = driver(difficulty)
            d.hold(175.0, 500)
            repeat(3) { d.rep(175.0, 110.0) }
            assertEquals(expected, d.last!!.reps, "difficulty=$difficulty")
        }
    }

    @Test
    fun hardRequiresDeepSquat() {
        val d = driver(Difficulty.HARD)
        d.hold(175.0, 500)
        d.rep(175.0, 90.0)
        assertEquals(0, d.last!!.reps)
        d.rep(175.0, 70.0)
        assertEquals(1, d.last!!.reps)
    }

    @Test
    fun tooFastRepIsRejected() {
        val d = driver()
        d.hold(175.0, 500)
        // 약 0.33초 만에 끝난 반복 (스무딩 지연을 감안해 바닥에서 잠깐 멈춤)
        d.step(175.0, 85.0, 100)
        d.hold(85.0, 100)
        d.step(85.0, 175.0, 100)
        d.hold(175.0, 33)
        assertEquals(0, d.last!!.reps)
        assertEquals(Hint.TOO_FAST, d.last!!.hint)
        d.rep(175.0, 85.0)
        assertEquals(1, d.last!!.reps)
    }

    @Test
    fun mustStartFromStanding() {
        val d = driver()
        d.hold(85.0, 500)
        d.step(85.0, 175.0, 700)
        assertEquals(0, d.last!!.reps)
    }

    @Test
    fun bendingKneeWithoutLoweringHipsDoesNotCount() {
        val d = driver(pose = { Poses.kneeOnlyBend(it) })
        d.hold(178.0, 500)
        repeat(3) { d.rep(178.0, 70.0) }
        assertEquals(0, d.last!!.reps)
    }

    @Test
    fun lowConfidenceFramesAreIgnoredAndHinted() {
        val d = driver(pose = { Poses.squat(it, confidence = 0.2f) })
        repeat(3) { d.rep(175.0, 85.0) }
        assertEquals(0, d.last!!.reps)
        assertEquals(Hint.BODY_NOT_VISIBLE, d.last!!.hint)
    }

    @Test
    fun briefOcclusionDoesNotBreakRep() {
        val counter = SquatCounter(Difficulty.NORMAL)
        val d = Driver(counter) { Poses.squat(it) }
        d.hold(175.0, 500)
        d.step(175.0, 85.0, 700)
        d.raw(emptyMap(), 300)
        d.step(85.0, 175.0, 700)
        assertEquals(1, d.last!!.reps)
    }

    @Test
    fun longOcclusionResetsRep() {
        val d = driver()
        d.hold(175.0, 500)
        d.step(175.0, 85.0, 700)
        d.raw(emptyMap(), 1500)
        assertEquals(Hint.BODY_NOT_VISIBLE, d.last!!.hint)
        d.hold(85.0, 200)
        d.step(85.0, 175.0, 700)
        assertEquals(0, d.last!!.reps)
        assertEquals(Hint.NONE, d.last!!.hint)
    }
}
