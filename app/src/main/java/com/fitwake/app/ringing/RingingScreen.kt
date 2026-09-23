package com.fitwake.app.ringing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitwake.alarm.Alarm
import com.fitwake.alarm.EmergencyDismiss
import com.fitwake.alarm.Snooze
import com.fitwake.app.R
import com.fitwake.app.alarm.AlarmService
import com.fitwake.app.alarm.RingState
import com.fitwake.app.mission.MissionScreen
import com.fitwake.app.mission.missionConfig
import com.fitwake.app.ui.difficultyAndReps
import com.fitwake.app.ui.formatTime
import com.fitwake.pose.Exercise
import kotlinx.coroutines.delay
import java.time.LocalTime

private enum class Step { RINGING, MISSION, SNOOZE_MISSION, DONE, SNOOZED }

@Composable
fun RingingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val state by AlarmService.state.collectAsStateWithLifecycle()
    var step by remember { mutableStateOf(Step.RINGING) }
    var elapsedSec by remember { mutableStateOf(0) }

    // 뒤로 가기로는 빠져나갈 수 없다.
    BackHandler { if (step == Step.MISSION || step == Step.SNOOZE_MISSION) step = Step.RINGING }

    // 알람이 이미 꺼졌으면(다른 경로로 해제됨) 화면을 닫는다.
    LaunchedEffect(state, step) {
        if (state == RingState.Idle && step != Step.DONE && step != Step.SNOOZED) onFinished()
    }

    val ringing = state as? RingState.Ringing
    val alarm = ringing?.alarm
    when {
        step == Step.DONE -> DoneContent(stringResource(R.string.done_body_alarm, elapsedSec), onFinished)
        step == Step.SNOOZED -> DoneContent(
            stringResource(R.string.snoozed_body, Snooze.MINUTES),
            onFinished,
            title = stringResource(R.string.snoozed_title),
        )
        ringing == null || alarm == null -> Unit // 서비스가 알람 정보를 불러오는 중
        step == Step.MISSION || step == Step.SNOOZE_MISSION -> {
            val snoozing = step == Step.SNOOZE_MISSION
            DisposableEffect(Unit) {
                AlarmService.setMissionActive(context, true)
                onDispose { AlarmService.setMissionActive(context, false) }
            }
            val config = alarm.missionConfig(ringing.exercise)
            MissionScreen(
                // 스누즈도 짧은 미션을 해야 한다 (PRD AL-08).
                config = if (snoozing) config.copy(targetReps = Snooze.MINI_MISSION_REPS) else config,
                onComplete = { sec ->
                    elapsedSec = sec
                    if (snoozing) {
                        step = Step.SNOOZED
                        AlarmService.snooze(context)
                    } else {
                        step = Step.DONE
                        AlarmService.dismissByMission(context, config.targetReps)
                    }
                },
                onQuit = { step = Step.RINGING },
                onRep = { AlarmService.reportProgress(context) },
            )
        }
        else -> RingingContent(
            alarm = alarm,
            exercise = ringing.exercise,
            canSnooze = Snooze.canSnooze(alarm, ringing.snoozeCount),
            snoozesLeft = Snooze.MAX_COUNT - ringing.snoozeCount,
            onStartMission = { step = Step.MISSION },
            onSnooze = { step = Step.SNOOZE_MISSION },
            onEmergencyDismiss = {
                AlarmService.dismissEmergency(context)
                onFinished()
            },
        )
    }
}

@Composable
private fun RingingContent(
    alarm: Alarm,
    exercise: Exercise,
    canSnooze: Boolean,
    snoozesLeft: Int,
    onStartMission: () -> Unit,
    onSnooze: () -> Unit,
    onEmergencyDismiss: () -> Unit,
) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = LocalTime.now()
        }
    }
    var showEmergency by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(formatTime(now.hour, now.minute), fontSize = 64.sp, style = MaterialTheme.typography.displayLarge)
        if (alarm.label.isNotBlank()) {
            Text(alarm.label, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.ringing_prompt),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            difficultyAndReps(exercise, alarm.difficulty, alarm.targetReps),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onStartMission,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) { Text(stringResource(R.string.start_mission), style = MaterialTheme.typography.titleLarge) }
        if (canSnooze) {
            OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.snooze_button, Snooze.MINUTES, Snooze.MINI_MISSION_REPS, snoozesLeft))
            }
        }
        TextButton(onClick = { showEmergency = true }) {
            Text(stringResource(R.string.emergency_dismiss), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showEmergency) {
        EmergencyDialog(onDismissRequest = { showEmergency = false }, onConfirmed = onEmergencyDismiss)
    }
}

/** PRD AC-07: 긴 문장을 그대로 입력해야 끌 수 있다. */
@Composable
private fun EmergencyDialog(onDismissRequest: () -> Unit, onConfirmed: () -> Unit) {
    val phrase = stringResource(R.string.emergency_phrase)
    var input by remember { mutableStateOf("") }
    val ok = EmergencyDismiss.matches(input, phrase)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.emergency_dismiss)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.emergency_instructions))
                Text(phrase, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmed, enabled = ok) { Text(stringResource(R.string.emergency_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun DoneContent(body: String, onClose: () -> Unit, title: String = stringResource(R.string.done_title)) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.displaySmall)
        Text(body, textAlign = TextAlign.Center)
        Button(onClick = onClose) { Text(stringResource(R.string.close)) }
    }
}
