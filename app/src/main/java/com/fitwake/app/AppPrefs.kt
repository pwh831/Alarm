package com.fitwake.app

import android.content.Context
import com.fitwake.alarm.Alarm
import com.fitwake.pose.Difficulty
import com.fitwake.pose.Exercise

/** 알람 목록 외의 작은 설정값. */
object AppPrefs {
    private const val FILE = "fitwake_prefs"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"
    private const val KEY_DEFAULT_EXERCISE = "default_exercise"
    private const val KEY_DEFAULT_DIFFICULTY = "default_difficulty"
    private const val KEY_DEFAULT_REPS = "default_reps"
    private const val RANDOM = "RANDOM"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isOnboardingDone(context: Context): Boolean = prefs(context).getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingDone(context: Context, done: Boolean = true) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
    }

    /** 새 알람의 기본 미션 (PRD SE-02). 시간 등 나머지는 기본값. */
    fun newAlarm(context: Context): Alarm {
        val p = prefs(context)
        val base = Alarm(hour = 7, minute = 0)
        val exercise = when (val name = p.getString(KEY_DEFAULT_EXERCISE, null)) {
            null -> base.exercise
            RANDOM -> null
            else -> Exercise.entries.firstOrNull { it.name == name } ?: base.exercise
        }
        return base.copy(
            exercise = exercise,
            difficulty = Difficulty.entries.firstOrNull { it.name == p.getString(KEY_DEFAULT_DIFFICULTY, null) }
                ?: base.difficulty,
            targetReps = p.getInt(KEY_DEFAULT_REPS, base.targetReps),
        )
    }

    fun setDefaultMission(context: Context, mission: Alarm) {
        prefs(context).edit()
            .putString(KEY_DEFAULT_EXERCISE, mission.exercise?.name ?: RANDOM)
            .putString(KEY_DEFAULT_DIFFICULTY, mission.difficulty.name)
            .putInt(KEY_DEFAULT_REPS, mission.targetReps)
            .apply()
    }
}
