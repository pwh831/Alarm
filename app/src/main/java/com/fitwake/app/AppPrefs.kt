package com.fitwake.app

import android.content.Context

/** 알람 목록 외의 작은 설정값. */
object AppPrefs {
    private const val FILE = "fitwake_prefs"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isOnboardingDone(context: Context): Boolean = prefs(context).getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingDone(context: Context) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
    }
}
