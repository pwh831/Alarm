package com.fitwake.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 예약된 시각(또는 스누즈 종료 시각)에 AlarmManager가 호출한다. 곧바로 포그라운드 서비스를 띄워 알람을 울린다. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
        if (alarmId < 0) return
        when (intent.action) {
            ACTION_FIRE -> AlarmService.ring(context, alarmId)
            ACTION_SNOOZE_FIRE -> AlarmService.ringSnoozed(
                context,
                alarmId,
                snoozeCount = intent.getIntExtra(EXTRA_SNOOZE_COUNT, 1),
                firstRingAt = intent.getLongExtra(EXTRA_FIRST_RING_AT, 0),
            )
        }
    }

    companion object {
        const val ACTION_FIRE = "com.fitwake.app.action.FIRE_ALARM"
        const val ACTION_SNOOZE_FIRE = "com.fitwake.app.action.FIRE_SNOOZE"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_SNOOZE_COUNT = "snooze_count"
        const val EXTRA_FIRST_RING_AT = "first_ring_at"
    }
}
