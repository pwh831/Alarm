package com.fitwake.app.edit

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fitwake.alarm.Alarm
import com.fitwake.alarm.Snooze
import com.fitwake.app.AppPrefs
import com.fitwake.app.R
import com.fitwake.app.alarmRepository
import com.fitwake.app.mission.MissionScreen
import com.fitwake.app.mission.missionConfig
import com.fitwake.app.ui.shortName
import com.fitwake.app.ui.weekOrder
import kotlinx.coroutines.launch

/** [alarmId]가 null이면 새 알람. */
@Composable
fun EditAlarmScreen(alarmId: Long?, onDone: () -> Unit) {
    val context = LocalContext.current
    var initial by remember { mutableStateOf<Alarm?>(null) }
    LaunchedEffect(alarmId) {
        initial = alarmId?.let { context.alarmRepository.get(it) } ?: AppPrefs.newAlarm(context)
    }
    initial?.let { EditAlarmForm(it, onDone) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditAlarmForm(initial: Alarm, onDone: () -> Unit) {
    val context = LocalContext.current
    val repository = context.alarmRepository
    val scope = rememberCoroutineScope()

    val time = rememberTimePickerState(initial.hour, initial.minute, android.text.format.DateFormat.is24HourFormat(context))
    var repeatDays by remember { mutableStateOf(initial.repeatDays) }
    var label by remember { mutableStateOf(initial.label) }
    // 미션 관련 필드(exercise, difficulty, targetReps)만 이 초안에서 쓴다.
    var mission by remember { mutableStateOf(initial) }
    var soundUri by remember { mutableStateOf(initial.soundUri) }
    var vibrate by remember { mutableStateOf(initial.vibrate) }
    var volumeRamp by remember { mutableStateOf(initial.volumeRamp) }
    var snoozeEnabled by remember { mutableStateOf(initial.snoozeEnabled) }
    var wakeCheck by remember { mutableStateOf(initial.wakeCheck) }
    var previewing by remember { mutableStateOf(false) }

    fun current() = initial.copy(
        hour = time.hour,
        minute = time.minute,
        repeatDays = repeatDays,
        label = label.trim(),
        exercise = mission.exercise,
        difficulty = mission.difficulty,
        targetReps = mission.targetReps,
        soundUri = soundUri,
        vibrate = vibrate,
        volumeRamp = volumeRamp,
        snoozeEnabled = snoozeEnabled,
        wakeCheck = wakeCheck,
        enabled = true,
    )

    // 저장 전 "미리 해보기" (PRD 3.1-6). 폼 상태는 이 컴포저블에 남아 있어 돌아와도 유지된다.
    if (previewing) {
        // 랜덤 미션도 미리 해보기 한 번 동안은 같은 운동을 유지한다.
        val previewConfig = remember { mission.missionConfig() }
        BackHandler { previewing = false }
        MissionScreen(
            config = previewConfig,
            onComplete = { sec ->
                previewing = false
                Toast.makeText(context, context.getString(R.string.preview_success, sec), Toast.LENGTH_SHORT).show()
            },
            onQuit = { previewing = false },
        )
        return
    }

    BackHandler(onBack = onDone)

    val pickSound = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.pickedRingtone()
            soundUri = uri?.toString()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(if (initial.id == 0L) R.string.new_alarm else R.string.edit_alarm),
            style = MaterialTheme.typography.headlineSmall,
        )
        TimePicker(state = time, modifier = Modifier.align(Alignment.CenterHorizontally))

        Text(stringResource(R.string.repeat), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            weekOrder.forEach { day ->
                val selected = day in repeatDays
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .clickable { repeatDays = if (selected) repeatDays - day else repeatDays + day },
                ) {
                    Text(
                        day.shortName(),
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        OutlinedTextField(
            value = label,
            onValueChange = { label = it.take(30) },
            label = { Text(stringResource(R.string.label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()
        Text(stringResource(R.string.mission), style = MaterialTheme.typography.titleMedium)
        MissionPicker(mission, onChange = { mission = it })
        OutlinedButton(onClick = { previewing = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.preview_mission))
        }

        HorizontalDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { pickSound.launch(ringtonePickerIntent(soundUri)) }
                .padding(vertical = 8.dp),
        ) {
            Text(stringResource(R.string.sound), Modifier.weight(1f))
            Text(ringtoneTitle(soundUri), color = MaterialTheme.colorScheme.primary)
        }
        SwitchRow(stringResource(R.string.vibrate), vibrate) { vibrate = it }
        SwitchRow(stringResource(R.string.volume_ramp), volumeRamp) { volumeRamp = it }
        SwitchRow(
            stringResource(R.string.snooze_setting),
            snoozeEnabled,
            description = stringResource(R.string.snooze_setting_desc, Snooze.MINUTES, Snooze.MAX_COUNT, Snooze.MINI_MISSION_REPS),
        ) { snoozeEnabled = it }
        SwitchRow(
            stringResource(R.string.wake_check_setting),
            wakeCheck,
            description = stringResource(R.string.wake_check_setting_desc),
        ) { wakeCheck = it }

        Button(
            onClick = {
                scope.launch {
                    repository.save(current())
                    onDone()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.save)) }

        if (initial.id != 0L) {
            TextButton(
                onClick = {
                    scope.launch {
                        repository.delete(initial)
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, description: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ringtoneTitle(soundUri: String?): String {
    val context = LocalContext.current
    val default = stringResource(R.string.sound_default)
    return remember(soundUri) {
        soundUri?.let { runCatching { RingtoneManager.getRingtone(context, Uri.parse(it))?.getTitle(context) }.getOrNull() }
            ?: default
    }
}

private fun ringtonePickerIntent(current: String?) = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
    .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current?.let(Uri::parse))

/** 기본값을 고르면 null(= 기기 기본 알람음)로 저장한다. */
private fun Intent.pickedRingtone(): Uri? {
    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
    }
    return uri?.takeUnless { RingtoneManager.isDefault(it) }
}
