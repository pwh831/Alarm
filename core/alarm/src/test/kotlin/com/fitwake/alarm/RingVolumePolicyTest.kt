package com.fitwake.alarm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RingVolumePolicyTest {
    private val policy = RingVolumePolicy(rampMs = 30_000, rampStart = 0.2f, missionVolume = 0.3f, idleMs = 60_000)

    @Test
    fun rampsUpToFull() {
        assertEquals(0.2f, policy.volume(0, 0, volumeRamp = true, missionActive = false, lastProgressMs = 0), 1e-4f)
        assertEquals(0.6f, policy.volume(15_000, 0, volumeRamp = true, missionActive = false, lastProgressMs = 0), 1e-4f)
        assertEquals(1f, policy.volume(90_000, 0, volumeRamp = true, missionActive = false, lastProgressMs = 0), 1e-4f)
    }

    @Test
    fun noRampMeansFullImmediately() {
        assertEquals(1f, policy.volume(0, 0, volumeRamp = false, missionActive = false, lastProgressMs = 0))
    }

    @Test
    fun missionLowersVolume() {
        assertEquals(0.3f, policy.volume(40_000, 0, volumeRamp = false, missionActive = true, lastProgressMs = 35_000))
    }

    @Test
    fun missionNeverRaisesAboveRamp() {
        assertEquals(0.2f, policy.volume(0, 0, volumeRamp = true, missionActive = true, lastProgressMs = 0), 1e-4f)
    }

    @Test
    fun idleMissionGoesBackToFull() {
        val v = policy.volume(200_000, 0, volumeRamp = false, missionActive = true, lastProgressMs = 140_000)
        assertEquals(1f, v)
        assertTrue(policy.volume(199_999, 0, volumeRamp = false, missionActive = true, lastProgressMs = 140_000) < 1f)
    }
}
