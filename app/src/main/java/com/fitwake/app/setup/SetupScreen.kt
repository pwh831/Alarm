package com.fitwake.app.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fitwake.app.R
import com.fitwake.app.mission.MissionConfig
import com.fitwake.app.ui.label
import com.fitwake.pose.Difficulty
import com.fitwake.pose.Exercise
import kotlin.math.roundToInt

private const val MIN_REPS = 5
private const val MAX_REPS = 50

/** PRD 3.1의 기본 횟수. */
private fun Exercise.defaultReps() = when (this) {
    Exercise.PUSHUP -> 10
    Exercise.SQUAT -> 15
}

@Composable
fun SetupScreen(initial: MissionConfig, onStart: (MissionConfig) -> Unit) {
    var exercise by remember { mutableStateOf(initial.exercise) }
    var difficulty by remember { mutableStateOf(initial.difficulty) }
    var reps by remember { mutableIntStateOf(initial.targetReps) }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.setup_subtitle), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))

        Text(stringResource(R.string.exercise), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Exercise.entries.forEach { e ->
                FilterChip(
                    selected = exercise == e,
                    onClick = {
                        if (exercise != e) reps = e.defaultReps()
                        exercise = e
                    },
                    label = { Text(e.label()) },
                )
            }
        }

        Text(stringResource(R.string.difficulty), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Difficulty.entries.forEach { d ->
                FilterChip(
                    selected = difficulty == d,
                    onClick = { difficulty = d },
                    label = { Text(d.label()) },
                )
            }
        }

        Text(stringResource(R.string.target_reps, reps), style = MaterialTheme.typography.titleMedium)
        Slider(
            value = reps.toFloat(),
            onValueChange = { reps = it.roundToInt() },
            valueRange = MIN_REPS.toFloat()..MAX_REPS.toFloat(),
            steps = MAX_REPS - MIN_REPS - 1,
        )

        Spacer(Modifier.weight(1f))
        Button(
            onClick = { onStart(MissionConfig(exercise, difficulty, reps)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(stringResource(R.string.start), style = MaterialTheme.typography.titleMedium)
        }
    }
}
