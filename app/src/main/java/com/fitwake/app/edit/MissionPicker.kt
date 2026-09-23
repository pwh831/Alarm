package com.fitwake.app.edit

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fitwake.alarm.Alarm
import com.fitwake.app.R
import com.fitwake.app.ui.label
import com.fitwake.pose.Difficulty
import com.fitwake.pose.Exercise
import kotlin.math.roundToInt

private const val MIN_REPS = 5
private const val MAX_REPS = 50

/** 선택지 순서. null은 랜덤 (PRD MS-07). */
private val exerciseChoices: List<Exercise?> = listOf(Exercise.SQUAT, Exercise.PUSHUP, null, Exercise.ARM_RAISE)

/** PRD 3.1의 기본 횟수. */
fun Exercise?.defaultReps() = when (this) {
    Exercise.PUSHUP -> 10
    Exercise.SQUAT, null -> 15
    Exercise.ARM_RAISE -> 20
}

/** 알람의 운동 종목, 난이도, 목표 횟수를 고른다. */
@Composable
fun MissionPicker(value: Alarm, onChange: (Alarm) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.exercise), style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            exerciseChoices.forEach { e ->
                FilterChip(
                    selected = value.exercise == e,
                    onClick = {
                        if (value.exercise != e) onChange(value.copy(exercise = e, targetReps = e.defaultReps()))
                    },
                    label = { Text(e.label()) },
                )
            }
        }
        val note = when (value.exercise) {
            null -> R.string.exercise_random_note
            Exercise.ARM_RAISE -> R.string.exercise_arm_raise_note
            else -> null
        }
        note?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }

        Text(stringResource(R.string.difficulty), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Difficulty.entries.forEach { d ->
                FilterChip(
                    selected = value.difficulty == d,
                    onClick = { onChange(value.copy(difficulty = d)) },
                    label = { Text(d.label()) },
                )
            }
        }

        Text(stringResource(R.string.target_reps, value.targetReps), style = MaterialTheme.typography.titleSmall)
        Slider(
            value = value.targetReps.toFloat(),
            onValueChange = { onChange(value.copy(targetReps = it.roundToInt())) },
            valueRange = MIN_REPS.toFloat()..MAX_REPS.toFloat(),
            steps = MAX_REPS - MIN_REPS - 1,
        )
    }
}
