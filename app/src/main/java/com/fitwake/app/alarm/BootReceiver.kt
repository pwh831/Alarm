package com.fitwake.app.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fitwake.app.alarmRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AlarmManager 예약은 재부팅하면 사라지고, 시간·시간대가 바뀌면 틀어진다.
 * 이런 이벤트마다 모든 알람을 다시 예약한다.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in handledActions) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                context.alarmRepository.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val handledActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
