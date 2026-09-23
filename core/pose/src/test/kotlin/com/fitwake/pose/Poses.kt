package com.fitwake.pose

import kotlin.math.cos
import kotlin.math.sin

/** 테스트용 합성 포즈. 좌표는 0..1 이미지 좌표(y 아래로 증가). */
object Poses {
    private const val SEG = 0.2f

    /** 측면 스쿼트: 발목·무릎 고정, 무릎 각도만큼 허벅지를 회전. */
    fun squat(kneeDeg: Double, confidence: Float = 0.9f): Map<Landmark, Point> {
        val ankle = Point(0.5f, 0.9f, confidence)
        val knee = Point(0.5f, 0.7f, confidence)
        val t = Math.toRadians(kneeDeg)
        val hip = Point(knee.x + SEG * sin(t).toFloat(), knee.y + SEG * cos(t).toFloat(), confidence)
        val shoulder = Point(hip.x, hip.y - 0.3f, confidence)
        return legs(shoulder, hip, knee, ankle)
    }

    /** 무릎은 굽혔지만 엉덩이는 제자리 (한쪽 다리 들기 등). */
    fun kneeOnlyBend(kneeDeg: Double): Map<Landmark, Point> {
        val hip = Point(0.5f, 0.5f)
        val knee = Point(0.5f, 0.7f)
        val t = Math.toRadians(kneeDeg)
        // 허벅지는 수직으로 두고 정강이만 회전
        val ankle = Point(knee.x - SEG * sin(t).toFloat(), knee.y - SEG * cos(t).toFloat())
        return legs(Point(0.5f, 0.2f), hip, knee, ankle)
    }

    private fun legs(shoulder: Point, hip: Point, knee: Point, ankle: Point) = buildMap {
        for (s in Side.entries) {
            put(s.shoulder, shoulder); put(s.hip, hip); put(s.knee, knee); put(s.ankle, ankle)
        }
    }

    /**
     * 측면 푸시업: 손목 고정, 팔꿈치 각도에 따라 어깨 높이가 바뀐다.
     * [knees]가 true면 무릎을 바닥에 대고 발은 들어 올린 자세.
     */
    fun pushup(elbowDeg: Double, knees: Boolean = false, sag: Float = 0f): Map<Landmark, Point> {
        val arm = 0.1f
        val wrist = Point(0.3f, 0.8f)
        val half = Math.toRadians(elbowDeg / 2)
        val shoulder = Point(0.3f, wrist.y - 2 * arm * sin(half).toFloat())
        val elbow = Point(0.3f - arm * cos(half).toFloat(), (shoulder.y + wrist.y) / 2)
        val (knee, ankle) = if (knees) {
            Point(0.65f, 0.8f) to Point(0.9f, 0.6f)
        } else {
            Point(0.65f, shoulder.y + (0.8f - shoulder.y) * 0.58f) to Point(0.9f, 0.8f)
        }
        val base = if (knees) knee else ankle
        val hip = Point(0.5f, shoulder.y + (base.y - shoulder.y) * (0.2f / (base.x - 0.3f)) + sag)
        return side(shoulder, elbow, wrist, hip, knee, ankle)
    }

    /**
     * 정면에서 본 팔 올리기. [leftDeg]/[rightDeg]는 엉덩이-어깨-손목 각도 (0 = 내림, 180 = 머리 위).
     */
    fun armRaise(leftDeg: Double, rightDeg: Double = leftDeg): Map<Landmark, Point> {
        fun arm(shoulder: Point, deg: Double, dir: Float): Point {
            val t = Math.toRadians(deg)
            return Point(shoulder.x + dir * 0.25f * sin(t).toFloat(), shoulder.y + 0.25f * cos(t).toFloat())
        }
        val ls = Point(0.4f, 0.3f)
        val rs = Point(0.6f, 0.3f)
        return mapOf(
            Landmark.LEFT_SHOULDER to ls, Landmark.RIGHT_SHOULDER to rs,
            Landmark.LEFT_HIP to Point(0.4f, 0.6f), Landmark.RIGHT_HIP to Point(0.6f, 0.6f),
            Landmark.LEFT_WRIST to arm(ls, leftDeg, -1f), Landmark.RIGHT_WRIST to arm(rs, rightDeg, 1f),
        )
    }

    /** 선 채로 팔만 굽히기. */
    fun standingArmCurl(elbowDeg: Double): Map<Landmark, Point> {
        val shoulder = Point(0.5f, 0.3f)
        val elbow = Point(0.5f, 0.45f)
        val t = Math.toRadians(elbowDeg)
        val wrist = Point(elbow.x + 0.15f * sin(t).toFloat(), elbow.y - 0.15f * cos(t).toFloat())
        return side(shoulder, elbow, wrist, Point(0.5f, 0.6f), Point(0.5f, 0.75f), Point(0.5f, 0.9f))
    }

    private fun side(shoulder: Point, elbow: Point, wrist: Point, hip: Point, knee: Point, ankle: Point) =
        mapOf(
            Landmark.LEFT_SHOULDER to shoulder, Landmark.LEFT_ELBOW to elbow, Landmark.LEFT_WRIST to wrist,
            Landmark.LEFT_HIP to hip, Landmark.LEFT_KNEE to knee, Landmark.LEFT_ANKLE to ankle,
        )
}

/** 30fps로 각도를 top→bottom→top 으로 선형 이동시키며 프레임을 흘려 넣는다. */
class Driver(private val counter: RepCounter, private val pose: (Double) -> Map<Landmark, Point>) {
    var t = 0L
        private set
    var last: RepState? = null
        private set

    fun hold(deg: Double, ms: Long) = step(deg, deg, ms)

    fun step(from: Double, to: Double, ms: Long) {
        val frames = (ms / 33).coerceAtLeast(1)
        for (i in 1..frames) {
            val deg = from + (to - from) * i / frames
            t += 33
            last = counter.update(PoseFrame(t, pose(deg)))
        }
    }

    fun rep(top: Double, bottom: Double, ms: Long = 1500) {
        step(top, bottom, ms / 2)
        step(bottom, top, ms / 2)
    }

    fun raw(points: Map<Landmark, Point>, ms: Long) {
        repeat((ms / 33).toInt()) {
            t += 33
            last = counter.update(PoseFrame(t, points))
        }
    }
}
