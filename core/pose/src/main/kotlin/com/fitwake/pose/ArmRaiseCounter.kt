package com.fitwake.pose

/**
 * 팔 올리기 (PRD MS-10 저강도 대체 미션): 양팔을 내린 상태에서 머리 위로 올렸다 내리면 1회.
 *
 * 엉덩이-어깨-손목 각도(팔을 내리면 0°, 머리 위로 들면 180°에 가까움)를 뒤집어서
 * `180 - 각도`로 판정한다. 그래야 "팔을 내린 상태"가 [RepCounter]의 TOP이 된다.
 * 양팔이 보이면 덜 올린 팔을 기준으로 삼아 한 팔만 드는 요령을 막는다.
 */
class ArmRaiseCounter(
    difficulty: Difficulty,
    config: CounterConfig = CounterConfig(),
) : RepCounter(config) {

    override val topDeg = 150.0
    override val bottomDeg = 180.0 - difficulty.armRaiseMinShoulderDeg

    override fun measure(frame: PoseFrame, smoothedAngleDeg: (Double) -> Double): Measurement? {
        val arms = Side.entries.mapNotNull { frame.visible(config.minConfidence, it.hip, it.shoulder, it.wrist) }
        if (arms.isEmpty()) return null
        val lowestArm = arms.minOf { (hip, shoulder, wrist) -> angleDeg(hip, shoulder, wrist) }
        return Measurement(smoothedAngleDeg(180.0 - lowestArm))
    }
}
