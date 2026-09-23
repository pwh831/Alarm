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
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMs, showIntent()), operation)
        } else {
            // 정확한 알람 권한이 없을 때의 차선책. 몇 분 늦을 수 있어 홈 화면에서 권한을 안내한다.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, operation)
        }
    }

    /** 스누즈 후 다시 울릴 시각. 예약된 회차와 별개의 PendingIntent를 쓴다. */
    fun scheduleSnooze(alarmId: Long, snoozeCount: Int, firstRingAt: Long, atMs: Long) {
        val operation = snoozePendingIntent(alarmId, snoozeCount, firstRingAt)
        if (canScheduleExact()) {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(atMs, showIntent()), operation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, operation)
        }
    }

    /** 예약된 회차와 대기 중인 스누즈를 모두 취소한다. */
    fun cancel(alarmId: Long) {
        alarmManager.cancel(firePendingIntent(alarmId))
        alarmManager.cancel(snoozePendingIntent(alarmId, 0, 0))
    }

    private fun showIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    // PendingIntent는 extras를 비교하지 않으므로, 같은 알람의 스누즈는 action으로 정규 회차와 구분된다.
    private fun snoozePendingIntent(alarmId: Long, snoozeCount: Int, firstRingAt: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_SNOOZE_FIRE)
                .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
                .putExtra(AlarmReceiver.EXTRA_SNOOZE_COUNT, snoozeCount)
                .putExtra(AlarmReceiver.EXTRA_FIRST_RING_AT, firstRingAt),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun firePendingIntent(alarmId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        alarmId.toInt(),
        Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE)
            .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
