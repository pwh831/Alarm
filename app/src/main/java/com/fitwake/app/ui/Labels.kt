package com.fitwake.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fitwake.app.R
import com.fitwake.pose.Difficulty
import com.fitwake.pose.Exercise

@Composable
fun Exercise.label(): String = stringResource(
    when (this) {
        Exercise.SQUAT -> R.string.exercise_squat
        Exercise.PUSHUP -> R.string.exercise_pushup
    },
)

@Composable
fun Difficulty.label(): String = stringResource(
    when (this) {
        Difficulty.EASY -> R.string.difficulty_easy
        Difficulty.NORMAL -> R.string.difficulty_normal
        Difficulty.HARD -> R.string.difficulty_hard
    },
)
