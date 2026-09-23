package com.fitwake.pose

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** 운동 판정에 쓰는 관절. 좌표계는 이미지 기준(y가 아래로 증가)이면 단위는 상관없다. */
enum class Landmark {
    LEFT_SHOULDER, RIGHT_SHOULDER,
    LEFT_ELBOW, RIGHT_ELBOW,
    LEFT_WRIST, RIGHT_WRIST,
    LEFT_HIP, RIGHT_HIP,
    LEFT_KNEE, RIGHT_KNEE,
    LEFT_ANKLE, RIGHT_ANKLE,
}

data class Point(val x: Float, val y: Float, val confidence: Float = 1f)

class PoseFrame(val timestampMs: Long, val points: Map<Landmark, Point>)

enum class Side(
    val shoulder: Landmark,
    val elbow: Landmark,
    val wrist: Landmark,
    val hip: Landmark,
    val knee: Landmark,
    val ankle: Landmark,
) {
    LEFT(
        Landmark.LEFT_SHOULDER, Landmark.LEFT_ELBOW, Landmark.LEFT_WRIST,
        Landmark.LEFT_HIP, Landmark.LEFT_KNEE, Landmark.LEFT_ANKLE,
    ),
    RIGHT(
        Landmark.RIGHT_SHOULDER, Landmark.RIGHT_ELBOW, Landmark.RIGHT_WRIST,
        Landmark.RIGHT_HIP, Landmark.RIGHT_KNEE, Landmark.RIGHT_ANKLE,
    ),
}

/** b를 꼭짓점으로 하는 각 abc (0..180도). */
fun angleDeg(a: Point, b: Point, c: Point): Double {
    val v1 = atan2((a.y - b.y).toDouble(), (a.x - b.x).toDouble())
    val v2 = atan2((c.y - b.y).toDouble(), (c.x - b.x).toDouble())
    var deg = abs(Math.toDegrees(v1 - v2))
    if (deg > 180.0) deg = 360.0 - deg
    return deg
}

fun distance(a: Point, b: Point): Double = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())

/** 주어진 관절이 모두 minConfidence 이상이면 해당 좌표들을, 아니면 null을 돌려준다. */
internal fun PoseFrame.visible(minConfidence: Float, vararg landmarks: Landmark): List<Point>? =
    landmarks.map { points[it]?.takeIf { p -> p.confidence >= minConfidence } ?: return null }
