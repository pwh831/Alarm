package com.fitwake.alarm

/**
 * 알람이 울리는 동안의 볼륨 (0..1). PRD 3.2:
 * - 볼륨 점증이 켜져 있으면 [rampMs] 동안 [rampStart]에서 1까지 올린다.
 * - 미션 중에는 [missionVolume]으로 줄이되, [idleMs] 동안 한 번도 성공하지 못하면 원래 볼륨으로 되돌린다.
 */
data class RingVolumePolicy(
    val rampMs: Long = 30_000,
    val rampStart: Float = 0.15f,
    val missionVolume: Float = 0.35f,
    val idleMs: Long = 60_000,
) {
    fun volume(
        nowMs: Long,
        ringStartMs: Long,
        volumeRamp: Boolean,
        missionActive: Boolean,
        lastProgressMs: Long,
    ): Float {
        val elapsed = (nowMs - ringStartMs).coerceAtLeast(0)
        val base = if (volumeRamp && rampMs > 0) {
            (rampStart + (1f - rampStart) * elapsed.toFloat() / rampMs).coerceAtMost(1f)
        } else {
            1f
        }
        val idle = nowMs - lastProgressMs >= idleMs
        return if (missionActive && !idle) minOf(base, missionVolume) else base
    }
}
