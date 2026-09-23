package com.fitwake.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 예약된 시각에 AlarmManager가 호출한다. 곧바로 포그라운드 서비스를 띄워 알람을 울린다. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
        if (alarmId < 0) return
        AlarmService.ring(context, alarmId)
    }

    companion object {
        const val ACTION_FIRE = "com.fitwake.app.action.FIRE_ALARM"
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
