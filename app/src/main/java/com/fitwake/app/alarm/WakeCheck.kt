package com.fitwake.app.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fitwake.app.appScope
import com.fitwake.app.wakeLogRepository
import kotlinx.coroutines.launch

/**
 * 기상 재확인 (PRD WK-01): 미션으로 알람을 끄고 5분 뒤 "일어나 계신가요?" 알림을 띄우고,
 * 60초 안에 누르지 않으면 같은 알람을 다시 울린다.
 */
object WakeCheck {
    const val DELAY_MS = 5 * 60_000L
    const val RESPONSE_MS = 60_000L

    internal const val ACTION_ASK = "com.fitwake.app.action.WAKE_CHECK_ASK"
    internal const val ACTION_CONFIRM = "com.fitwake.app.action.WAKE_CHECK_CONFIRM"
    internal const val ACTION_TIMEOUT = "com.fitwake.app.action.WAKE_CHECK_TIMEOUT"
    internal const val EXTRA_LOG_ID = "log_id"
    internal const val EXTRA_ALARM_ID = "alarm_id"

    fun schedule(context: Context, logId: Long, alarmId: Long) {
        setExact(context, System.currentTimeMillis() + DELAY_MS, pendingIntent(context, ACTION_ASK, logId, alarmId))
    }

    internal fun setExact(context: Context, atMs: Long, operation: PendingIntent) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (AlarmScheduler(context).canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, operation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, operation)
        }
    }

    internal fun pendingIntent(context: Context, action: String, logId: Long, alarmId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            logId.toInt(),
            Intent(context, WakeCheckReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_LOG_ID, logId)
                .putExtra(EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

class WakeCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val logId = intent.getLongExtra(WakeCheck.EXTRA_LOG_ID, -1)
        val alarmId = intent.getLongExtra(WakeCheck.EXTRA_ALARM_ID, -1)
        if (logId < 0 || alarmId < 0) return
        val notifications = context.getSystemService(NotificationManager::class.java)
        val timeout = WakeCheck.pendingIntent(context, WakeCheck.ACTION_TIMEOUT, logId, alarmId)

        when (intent.action) {
            WakeCheck.ACTION_ASK -> {
                val confirm = WakeCheck.pendingIntent(context, WakeCheck.ACTION_CONFIRM, logId, alarmId)
                notifications.notify(
                    AlarmNotifications.WAKE_CHECK_NOTIFICATION_ID,
                    AlarmNotifications.wakeCheck(context, confirm, WakeCheck.RESPONSE_MS),
                )
                WakeCheck.setExact(context, System.currentTimeMillis() + WakeCheck.RESPONSE_MS, timeout)
            }
            WakeCheck.ACTION_CONFIRM -> {
                context.getSystemService(AlarmManager::class.java).cancel(timeout)
                notifications.cancel(AlarmNotifications.WAKE_CHECK_NOTIFICATION_ID)
                record(context, logId, passed = true)
            }
            WakeCheck.ACTION_TIMEOUT -> {
                notifications.cancel(AlarmNotifications.WAKE_CHECK_NOTIFICATION_ID)
                record(context, logId, passed = false)
                AlarmService.ringAgain(context, alarmId)
            }
        }
    }

    private fun record(context: Context, logId: Long, passed: Boolean) {
        val pending = goAsync()
        context.appScope.launch {
            try {
                context.wakeLogRepository.setWakeCheck(logId, passed)
            } finally {
                pending.finish()
            }
        }
    }
}
