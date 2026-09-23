package com.fitwake.app.mission

import com.fitwake.pose.Landmark
import com.fitwake.pose.Point
import com.fitwake.pose.PoseFrame
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

private val mlKitToLandmark = mapOf(
    PoseLandmark.LEFT_SHOULDER to Landmark.LEFT_SHOULDER,
    PoseLandmark.RIGHT_SHOULDER to Landmark.RIGHT_SHOULDER,
    PoseLandmark.LEFT_ELBOW to Landmark.LEFT_ELBOW,
    PoseLandmark.RIGHT_ELBOW to Landmark.RIGHT_ELBOW,
    PoseLandmark.LEFT_WRIST to Landmark.LEFT_WRIST,
    PoseLandmark.RIGHT_WRIST to Landmark.RIGHT_WRIST,
    PoseLandmark.LEFT_HIP to Landmark.LEFT_HIP,
    PoseLandmark.RIGHT_HIP to Landmark.RIGHT_HIP,
    PoseLandmark.LEFT_KNEE to Landmark.LEFT_KNEE,
    PoseLandmark.RIGHT_KNEE to Landmark.RIGHT_KNEE,
    PoseLandmark.LEFT_ANKLE to Landmark.LEFT_ANKLE,
    PoseLandmark.RIGHT_ANKLE to Landmark.RIGHT_ANKLE,
)

/** ML Kit 결과를 플랫폼 독립적인 [PoseFrame]으로 바꾼다. 좌표는 분석기가 준 좌표계(뷰 기준)를 그대로 쓴다. */
fun Pose.toPoseFrame(timestampMs: Long): PoseFrame = PoseFrame(
    timestampMs,
    buildMap {
        for ((type, landmark) in mlKitToLandmark) {
            val p = getPoseLandmark(type) ?: continue
            put(landmark, Point(p.position.x, p.position.y, p.inFrameLikelihood))
        }
    },
)
