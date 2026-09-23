package com.fitwake.pose

import kotlin.math.sqrt

/**
 * 가속도계로 폰이 움직이는지 판단한다 (PRD AC-06).
 * 저역 통과 필터로 중력 방향을 추정하고, 그와 다른 가속도가 [thresholdMs2]를 넘으면
 * 그 뒤 [holdMs] 동안 "움직이는 중"으로 본다. 바닥에서 운동할 때 생기는 잔떨림은 기준보다 작다.
 */
class MotionGuard(
    private val thresholdMs2: Float = 1.0f,
    private val holdMs: Long = 700,
    private val alpha: Float = 0.1f,
) {
    private var gravity: FloatArray? = null
    private var lastMovementMs: Long? = null

    fun onAccelerometer(timestampMs: Long, x: Float, y: Float, z: Float) {
        val g = gravity ?: floatArrayOf(x, y, z).also {
            gravity = it
            return
        }
        g[0] += alpha * (x - g[0])
        g[1] += alpha * (y - g[1])
        g[2] += alpha * (z - g[2])
        val dx = x - g[0]
        val dy = y - g[1]
        val dz = z - g[2]
        if (sqrt(dx * dx + dy * dy + dz * dz) > thresholdMs2) lastMovementMs = timestampMs
    }

    fun isMoving(nowMs: Long): Boolean = lastMovementMs?.let { nowMs - it < holdMs } ?: false
}
