package com.fitwake.pose

import kotlin.test.Test
import kotlin.test.assertEquals

class GeometryTest {
    @Test
    fun rightAngle() {
        assertEquals(90.0, angleDeg(Point(0f, 1f), Point(0f, 0f), Point(1f, 0f)), 1e-6)
    }

    @Test
    fun straightLine() {
        assertEquals(180.0, angleDeg(Point(-1f, 0f), Point(0f, 0f), Point(1f, 0f)), 1e-6)
    }

    @Test
    fun reflexAngleIsFolded() {
        assertEquals(45.0, angleDeg(Point(1f, 0f), Point(0f, 0f), Point(1f, -1f)), 1e-6)
    }

    @Test
    fun syntheticPosesHaveRequestedAngles() {
        val sq = Poses.squat(95.0)
        assertEquals(95.0, angleDeg(sq.getValue(Landmark.LEFT_HIP), sq.getValue(Landmark.LEFT_KNEE), sq.getValue(Landmark.LEFT_ANKLE)), 1e-3)
        val pu = Poses.pushup(80.0)
        assertEquals(80.0, angleDeg(pu.getValue(Landmark.LEFT_SHOULDER), pu.getValue(Landmark.LEFT_ELBOW), pu.getValue(Landmark.LEFT_WRIST)), 1e-3)
    }
}
