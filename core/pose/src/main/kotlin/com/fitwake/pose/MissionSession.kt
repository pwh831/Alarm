package com.fitwake.pose

import kotlin.math.ceil

/**
 * 미션 한 번의 진행을 관리한다.
 * - 필요한 관절이 계속 보이면 [countdownMs] 카운트다운 후 카운트를 시작한다 (PRD 3.2-4).
 * - 폰이 움직이는 동안에는 프레임을 무시한다 (PRD AC-06). 폰을 들고 흔들어 카운트를 만들 수 없다.
 */
class MissionSession(
    private val counter: RepCounter,
    private val countdownMs: Long = 3_000,
) {
    data class State(
        /** 카운트다운 중이면 남은 초(올림), 아니면 null. */
        val countdownSec: Int?,
        val started: Boolean,
        val rep: RepState,
    )

    private var visibleSinceMs: Long? = null
    private var started = false
    private var last = RepState(0, Phase.WAITING, Hint.NONE, null)

    fun update(frame: PoseFrame, phoneMoving: Boolean = false): State {
        if (!started) {
            if (phoneMoving) {
                visibleSinceMs = null
                return State(null, false, last.copy(hint = Hint.PHONE_MOVING))
            }
            if (!counter.sees(frame)) {
                visibleSinceMs = null
                return State(null, false, last)
            }
            val since = visibleSinceMs ?: frame.timestampMs.also { visibleSinceMs = it }
            val left = countdownMs - (frame.timestampMs - since)
            if (left > 0) return State(ceil(left / 1000.0).toInt(), false, last)
            started = true
        }
        if (phoneMoving) return State(null, true, last.copy(hint = Hint.PHONE_MOVING))
        last = counter.update(frame)
        return State(null, true, last)
    }
}
