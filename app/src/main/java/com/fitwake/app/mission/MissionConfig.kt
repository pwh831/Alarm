package com.fitwake.app.mission

import com.fitwake.alarm.Alarm
import com.fitwake.pose.Difficulty
import com.fitwake.pose.Exercise

data class MissionConfig(
    val exercise: Exercise,
    val difficulty: Difficulty,
    val targetReps: Int,
)

/** 랜덤 미션이면 [exercise]를 미리 정해서 넘긴다. */
fun Alarm.missionConfig(exercise: Exercise = pickExercise()) = MissionConfig(exercise, difficulty, targetReps)
