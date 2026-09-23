package com.fitwake.app.mission

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.fitwake.pose.Landmark
import com.fitwake.pose.Landmark.LEFT_ANKLE
import com.fitwake.pose.Landmark.LEFT_ELBOW
import com.fitwake.pose.Landmark.LEFT_HIP
import com.fitwake.pose.Landmark.LEFT_KNEE
import com.fitwake.pose.Landmark.LEFT_SHOULDER
import com.fitwake.pose.Landmark.LEFT_WRIST
import com.fitwake.pose.Landmark.RIGHT_ANKLE
import com.fitwake.pose.Landmark.RIGHT_ELBOW
import com.fitwake.pose.Landmark.RIGHT_HIP
import com.fitwake.pose.Landmark.RIGHT_KNEE
import com.fitwake.pose.Landmark.RIGHT_SHOULDER
import com.fitwake.pose.Landmark.RIGHT_WRIST
import com.fitwake.pose.Point

private val bones = listOf(
    LEFT_SHOULDER to RIGHT_SHOULDER, LEFT_HIP to RIGHT_HIP,
    LEFT_SHOULDER to LEFT_ELBOW, LEFT_ELBOW to LEFT_WRIST,
    RIGHT_SHOULDER to RIGHT_ELBOW, RIGHT_ELBOW to RIGHT_WRIST,
    LEFT_SHOULDER to LEFT_HIP, RIGHT_SHOULDER to RIGHT_HIP,
    LEFT_HIP to LEFT_KNEE, LEFT_KNEE to LEFT_ANKLE,
    RIGHT_HIP to RIGHT_KNEE, RIGHT_KNEE to RIGHT_ANKLE,
)

private const val MIN_CONFIDENCE = 0.5f

/** 인식된 관절을 카메라 프리뷰 위에 그린다 (PRD MS-06). 좌표는 프리뷰 뷰의 픽셀 좌표. */
@Composable
fun SkeletonOverlay(points: Map<Landmark, Point>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        fun Point.offset() = Offset(x, y)
        val visible = points.filterValues { it.confidence >= MIN_CONFIDENCE }
        for ((a, b) in bones) {
            val pa = visible[a] ?: continue
            val pb = visible[b] ?: continue
            drawLine(color, pa.offset(), pb.offset(), strokeWidth = 8f, cap = StrokeCap.Round)
        }
        for (p in visible.values) {
            drawCircle(Color.White, radius = 10f, center = p.offset())
        }
    }
}
