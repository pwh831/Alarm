package com.fitwake.pose

/**
 * 스쿼트: 무릎 각도(엉덩이-무릎-발목)로 센다.
 * 무릎만 굽히는 속임수를 막기 위해, 선 자세 대비 엉덩이가 허벅지 길이의 [minHipDropRatio] 이상 내려가야 BOTTOM으로 인정한다.
 */
class SquatCounter(
    difficulty: Difficulty,
    config: CounterConfig = CounterConfig(),
    private val minHipDropRatio: Double = 0.3,
) : RepCounter(config) {

    override val topDeg = 160.0
    override val bottomDeg = difficulty.squatBottomKneeDeg

    private var standingHipY: Double? = null
    private var standingThigh: Double? = null

    override fun measure(frame: PoseFrame, smoothedAngleDeg: (Double) -> Double): Measurement? {
        val legs = Side.entries.mapNotNull { frame.visible(config.minConfidence, it.hip, it.knee, it.ankle) }
        if (legs.isEmpty()) return null

        val angle = smoothedAngleDeg(legs.map { (hip, knee, ankle) -> angleDeg(hip, knee, ankle) }.average())

        val hipY = legs.map { it[0].y.toDouble() }.average()
        val topHipY = standingHipY
        val thigh = standingThigh
        val hint = if (angle <= bottomDeg && topHipY != null && thigh != null &&
            hipY - topHipY < thigh * minHipDropRatio
        ) Hint.LOWER_HIPS else Hint.NONE
        return Measurement(angle, hint)
    }

    override fun onTop(frame: PoseFrame) {
        val legs = Side.entries.mapNotNull { frame.visible(config.minConfidence, it.hip, it.knee) }
        if (legs.isEmpty()) return
        standingHipY = legs.map { it[0].y.toDouble() }.average()
        standingThigh = legs.map { (hip, knee) -> distance(hip, knee) }.average()
    }

    override fun reset() {
        standingHipY = null
        standingThigh = null
    }
}
