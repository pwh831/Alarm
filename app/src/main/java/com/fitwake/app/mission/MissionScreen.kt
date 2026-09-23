package com.fitwake.app.mission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fitwake.app.R
import com.fitwake.app.ui.Accent
import com.fitwake.pose.Exercise
import com.fitwake.pose.Hint
import com.fitwake.pose.Landmark
import com.fitwake.pose.Phase
import com.fitwake.pose.Point
import com.fitwake.pose.RepCounter
import com.fitwake.pose.RepState
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

@Composable
fun MissionScreen(config: MissionConfig, onComplete: (elapsedSec: Int) -> Unit, onQuit: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    if (hasPermission) {
        KeepScreenOnAndBright()
        MissionCamera(config, onComplete, onQuit)
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.camera_permission_needed), textAlign = TextAlign.Center)
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Text(stringResource(R.string.grant_permission))
            }
            OutlinedButton(onClick = onQuit) { Text(stringResource(R.string.quit)) }
        }
    }
}

@Composable
private fun MissionCamera(config: MissionConfig, onComplete: (Int) -> Unit, onQuit: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val currentOnComplete by rememberUpdatedState(onComplete)

    val counter = remember(config) { RepCounter.create(config.exercise, config.difficulty) }
    var repState by remember { mutableStateOf(RepState(0, Phase.WAITING, Hint.NONE, null)) }
    var points by remember { mutableStateOf<Map<Landmark, Point>>(emptyMap()) }
    var useFrontCamera by remember { mutableStateOf(true) }
    val startMs = remember { SystemClock.elapsedRealtime() }

    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }
    val detector = remember {
        PoseDetection.getClient(
            PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build(),
        )
    }
    val controller = remember { LifecycleCameraController(context) }

    DisposableEffect(lifecycleOwner) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        controller.setImageAnalysisAnalyzer(
            mainExecutor,
            MlKitAnalyzer(
                listOf(detector),
                ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                mainExecutor,
            ) { result ->
                val pose = result.getValue(detector)
                if (pose != null) {
                    val frame = pose.toPoseFrame(SystemClock.elapsedRealtime())
                    points = frame.points
                    val before = repState.reps
                    repState = counter.update(frame)
                    if (repState.reps > before) {
                        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
            },
        )
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            detector.close()
            tone.release()
        }
    }

    LaunchedEffect(useFrontCamera) {
        controller.cameraSelector =
            if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
    }

    LaunchedEffect(repState.reps) {
        if (repState.reps >= config.targetReps) {
            currentOnComplete(((SystemClock.elapsedRealtime() - startMs) / 1000).toInt())
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.controller = controller
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        SkeletonOverlay(points, color = Accent, modifier = Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "${repState.reps} / ${config.targetReps}",
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Text(
                text = hintText(config.exercise, repState),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Box(Modifier.weight(1f))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = { useFrontCamera = !useFrontCamera }) {
                    Text(stringResource(R.string.switch_camera), color = Color.White)
                }
                OutlinedButton(onClick = onQuit) {
                    Text(stringResource(R.string.quit), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun hintText(exercise: Exercise, state: RepState): String = stringResource(
    when (state.hint) {
        Hint.BODY_NOT_VISIBLE -> R.string.hint_body_not_visible
        Hint.TOO_FAST -> R.string.hint_too_fast
        Hint.LOWER_HIPS -> R.string.hint_lower_hips
        Hint.GET_HORIZONTAL -> R.string.hint_get_horizontal
        Hint.KEEP_BODY_STRAIGHT -> R.string.hint_keep_body_straight
        Hint.NONE -> when {
            state.phase != Phase.WAITING -> R.string.hint_go
            exercise == Exercise.SQUAT -> R.string.hint_get_ready_squat
            else -> R.string.hint_get_ready_pushup
        }
    },
)

/** 미션 중 화면이 꺼지지 않게 하고, 어두운 방에서 조명 역할을 하도록 밝기를 최대로 올린다 (PRD 5.5). */
@Composable
private fun KeepScreenOnAndBright() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val previous = window.attributes.screenBrightness
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 1f }
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.attributes = window.attributes.apply { screenBrightness = previous }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
