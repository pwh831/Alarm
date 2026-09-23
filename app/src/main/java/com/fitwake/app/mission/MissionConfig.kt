package com.fitwake.app.mission

import com.fitwake.alarm.Alarm
import com.fitwake.pose.Difficulty
import com.fitwake.pose.Exercise

data class MissionConfig(
    val exercise: Exercise,
    val difficulty: Difficulty,
    val targetReps: Int,
)

fun Alarm.missionConfig() = MissionConfig(exercise, difficulty, targetReps)
