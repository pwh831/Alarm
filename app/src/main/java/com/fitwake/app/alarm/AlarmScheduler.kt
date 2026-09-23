package com.fitwake.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.fitwake.alarm.Alarm
import com.fitwake.app.MainActivity
import java.time.ZonedDateTime

/**
 * AlarmManager.setAlarmClock으로 예약한다. 이 방식은 도즈 모드에서도 정시에 울리고,
 * 상태 표시줄에 다음 알람으로 표시된다 (PRD 7.1).
 */
class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun schedule(alarm: Alarm, now: ZonedDateTime = ZonedDateTime.now()) {
        if (!alarm.enabled) {
            cancel(alarm.id)
            return
        }
        val triggerAtMs = alarm.nextTrigger(now).toInstant().toEpochMilli()
        val operation = firePendingIntent(alarm.id)
        if (canScheduleExact()) {
            val showIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMs, showIntent), operation)
        } else {
            // 정확한 알람 권한이 없을 때의 차선책. 몇 분 늦을 수 있어 홈 화면에서 권한을 안내한다.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, operation)
        }
    }

    fun cancel(alarmId: Long) {
        alarmManager.cancel(firePendingIntent(alarmId))
    }

    private fun firePendingIntent(alarmId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        alarmId.toInt(),
        Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE)
            .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
