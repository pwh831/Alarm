package com.fitwake.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fitwake.app.R
import com.fitwake.pose.Difficulty
import com.fitwake.pose.Exercise

/** null은 랜덤 미션. */
@Composable
fun Exercise?.label(): String = stringResource(
    when (this) {
        Exercise.SQUAT -> R.string.exercise_squat
        Exercise.PUSHUP -> R.string.exercise_pushup
        Exercise.ARM_RAISE -> R.string.exercise_arm_raise
        null -> R.string.exercise_random
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

/** "스쿼트 15회 · 보통" */
@Composable
fun difficultyAndReps(exercise: Exercise?, difficulty: Difficulty, reps: Int): String =
    stringResource(R.string.mission_summary, exercise.label(), reps, difficulty.label())
