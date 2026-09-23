package com.fitwake.pose

enum class Exercise { PUSHUP, SQUAT }

/** PRD 5.3 난이도별 기준. */
enum class Difficulty(
    val squatBottomKneeDeg: Double,
    val pushupBottomElbowDeg: Double,
    val allowKneePushup: Boolean,
) {
    EASY(120.0, 110.0, true),
    NORMAL(100.0, 90.0, false),
    HARD(85.0, 75.0, false),
}

data class CounterConfig(
    /** 이 신뢰도 미만의 관절은 보이지 않는 것으로 본다 (AC-01). */
    val minConfidence: Float = 0.5f,
    /** 이보다 빠른 반복은 인정하지 않는다 (AC-03). */
    val minRepMs: Long = 400,
    /** 몸이 이 시간 이상 안 보이면 안내를 띄우고 진행 중인 반복을 초기화한다. */
    val lostVisibilityMs: Long = 1000,
    /** 각도 지수이동평균 계수. 1이면 스무딩 없음. */
    val smoothing: Double = 0.5,
)

enum class Phase {
    /** 아직 시작 자세(서기/팔 편 상태)가 확인되지 않음. */
    WAITING,
    TOP,
    BOTTOM,
}

enum class Hint {
    NONE,
    BODY_NOT_VISIBLE,
    TOO_FAST,
    /** 스쿼트: 무릎만 굽히고 엉덩이가 내려가지 않음. */
    LOWER_HIPS,
    /** 푸시업: 몸이 수평에 가깝지 않음 (서서 팔만 굽히는 경우). */
    GET_HORIZONTAL,
    /** 푸시업: 엉덩이가 처지거나 솟음. */
    KEEP_BODY_STRAIGHT,
}

data class RepState(
    val reps: Int,
    val phase: Phase,
    val hint: Hint,
    /** 판정에 쓰인 (스무딩된) 관절 각도. 몸이 안 보이면 null. */
    val angleDeg: Double?,
)

/**
 * 관절 각도 기반 반복 카운터 (PRD 5.2).
 *
 * 각도가 [topDeg] 이상이면 TOP, [bottomDeg] 이하이면 BOTTOM이고,
 * TOP → BOTTOM → TOP을 한 번 거치면 1회로 센다. 두 기준 사이의 간격이 히스테리시스 역할을 한다.
 */
abstract class RepCounter(protected val config: CounterConfig) {

    protected class Measurement(val angleDeg: Double, val formHint: Hint = Hint.NONE)

    protected abstract val topDeg: Double
    protected abstract val bottomDeg: Double

    /** 필요한 관절이 안 보이면 null. formHint가 NONE이 아니면 BOTTOM 진입을 막는다. */
    protected abstract fun measure(frame: PoseFrame, smoothedAngleDeg: (Double) -> Double): Measurement?

    /** TOP 자세가 확인된 프레임마다 호출된다. */
    protected open fun onTop(frame: PoseFrame) {}

    protected open fun reset() {}

    var reps = 0
        private set
    private var phase = Phase.WAITING
    private var smoothed: Double? = null
    private var lastTopMs = 0L
    private var lastSeenMs: Long? = null
    private var lastHint = Hint.NONE

    fun update(frame: PoseFrame): RepState {
        val now = frame.timestampMs
        val m = measure(frame) { raw ->
            val prev = smoothed
            val s = if (prev == null) raw else prev + config.smoothing * (raw - prev)
            smoothed = s
            s
        }

        if (m == null) {
            val seen = lastSeenMs ?: now.also { lastSeenMs = it }
            if (now - seen >= config.lostVisibilityMs) {
                phase = Phase.WAITING
                smoothed = null
                reset()
                lastHint = Hint.BODY_NOT_VISIBLE
            }
            return RepState(reps, phase, lastHint, null)
        }
        lastSeenMs = now
        if (lastHint == Hint.BODY_NOT_VISIBLE) lastHint = Hint.NONE

        val angle = m.angleDeg
        when (phase) {
            Phase.WAITING, Phase.TOP -> when {
                angle >= topDeg -> {
                    phase = Phase.TOP
                    lastTopMs = now
                    onTop(frame)
                    if (lastHint != Hint.TOO_FAST) lastHint = m.formHint
                }
                phase == Phase.TOP && angle <= bottomDeg -> {
                    if (m.formHint == Hint.NONE) {
                        phase = Phase.BOTTOM
                        lastHint = Hint.NONE
                    } else {
                        lastHint = m.formHint
                    }
                }
                else -> if (m.formHint != Hint.NONE) lastHint = m.formHint
            }
            Phase.BOTTOM -> if (angle >= topDeg) {
                if (now - lastTopMs >= config.minRepMs) {
                    reps++
                    lastHint = Hint.NONE
                } else {
                    lastHint = Hint.TOO_FAST
                }
                phase = Phase.TOP
                lastTopMs = now
                onTop(frame)
            }
        }
        return RepState(reps, phase, lastHint, angle)
    }

    companion object {
        fun create(
            exercise: Exercise,
            difficulty: Difficulty,
            config: CounterConfig = CounterConfig(),
        ): RepCounter = when (exercise) {
            Exercise.SQUAT -> SquatCounter(difficulty, config)
            Exercise.PUSHUP -> PushupCounter(difficulty, config)
        }
    }
}
