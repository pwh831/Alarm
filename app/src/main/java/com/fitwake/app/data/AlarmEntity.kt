package com.fitwake.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.fitwake.alarm.Alarm
import com.fitwake.alarm.DayMask
import com.fitwake.pose.Difficulty
import com.fitwake.pose.Exercise

/** 랜덤 미션(Alarm.exercise == null)을 DB에 저장할 때 쓰는 값. */
private const val RANDOM = "RANDOM"

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val hour: Int,
    val minute: Int,
    val repeatMask: Int,
    val label: String,
    val enabled: Boolean,
    val exercise: String,
    val difficulty: String,
    val targetReps: Int,
    val soundUri: String?,
    val vibrate: Boolean,
    val volumeRamp: Boolean,
    val snoozeEnabled: Boolean,
    val wakeCheck: Boolean,
)

fun AlarmEntity.toModel() = Alarm(
    id = id,
    hour = hour,
    minute = minute,
    repeatDays = DayMask.fromMask(repeatMask),
    label = label,
    enabled = enabled,
    exercise = if (exercise == RANDOM) null else Exercise.entries.firstOrNull { it.name == exercise } ?: Exercise.SQUAT,
    difficulty = Difficulty.entries.firstOrNull { it.name == difficulty } ?: Difficulty.NORMAL,
    targetReps = targetReps,
    soundUri = soundUri,
    vibrate = vibrate,
    volumeRamp = volumeRamp,
    snoozeEnabled = snoozeEnabled,
    wakeCheck = wakeCheck,
)

fun Alarm.toEntity() = AlarmEntity(
    id = id,
    hour = hour,
    minute = minute,
    repeatMask = DayMask.toMask(repeatDays),
    label = label,
    enabled = enabled,
    exercise = exercise?.name ?: RANDOM,
    difficulty = difficulty.name,
    targetReps = targetReps,
    soundUri = soundUri,
    vibrate = vibrate,
    volumeRamp = volumeRamp,
    snoozeEnabled = snoozeEnabled,
    wakeCheck = wakeCheck,
)
