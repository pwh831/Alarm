package com.fitwake.pose

import kotlin.math.abs

/**
 * 푸시업: 팔꿈치 각도(어깨-팔꿈치-손목)로 센다. 측면 촬영을 가정하고 더 잘 보이는 쪽 팔을 쓴다.
 * 몸이 수평에 가까워야 하고(서서 팔만 굽히기 방지), 어깨-엉덩이-발목이 일직선이어야 한다.
 * [Difficulty.allowKneePushup]이면 어깨-엉덩이-무릎 일직선(무릎 대고 하는 푸시업)도 허용한다.
 */
class PushupCounter(
    private val difficulty: Difficulty,
    config: CounterConfig = CounterConfig(),
    private val minBodyLineDeg: Double = 150.0,
    /** 어깨→발끝 벡터가 수평에서 이 각도 이내여야 한다. */
    private val maxBodyTiltDeg: Double = 45.0,
) : RepCounter(config) {

    override val topDeg = 150.0
    override val bottomDeg = difficulty.pushupBottomElbowDeg

    override fun measure(frame: PoseFrame, smoothedAngleDeg: (Double) -> Double): Measurement? {
        val side = Side.entries
            .filter { frame.visible(config.minConfidence, it.shoulder, it.elbow, it.wrist, it.hip) != null }
            .maxByOrNull { s -> listOf(s.shoulder, s.elbow, s.wrist, s.hip).minOf { frame.points.getValue(it).confidence } }
            ?: return null
        val (shoulder, elbow, wrist, hip) = frame.visible(0f, side.shoulder, side.elbow, side.wrist, side.hip)!!
        val ankle = frame.visible(config.minConfidence, side.ankle)?.first()
        val knee = frame.visible(config.minConfidence, side.knee)?.first()

        val fullPlank = ankle?.takeIf { angleDeg(shoulder, hip, it) >= minBodyLineDeg }
        val kneePlank = knee?.takeIf { difficulty.allowKneePushup && angleDeg(shoulder, hip, it) >= minBodyLineDeg }
        val end = fullPlank ?: kneePlank ?: ankle ?: knee ?: return null

        val angle = smoothedAngleDeg(angleDeg(shoulder, elbow, wrist))
        val tilt = Math.toDegrees(kotlin.math.atan2(abs(end.y - shoulder.y).toDouble(), abs(end.x - shoulder.x).toDouble()))
        val hint = when {
            tilt > maxBodyTiltDeg -> Hint.GET_HORIZONTAL
            fullPlank == null && kneePlank == null -> Hint.KEEP_BODY_STRAIGHT
            else -> Hint.NONE
        }
        return Measurement(angle, hint)
    }
}
