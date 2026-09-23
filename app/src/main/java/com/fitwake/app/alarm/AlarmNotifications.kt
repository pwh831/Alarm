package com.fitwake.app.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.fitwake.app.R
import com.fitwake.app.ringing.RingingActivity

object AlarmNotifications {
    const val CHANNEL_ID = "alarm_ringing"
    const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_alarm),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_alarm_description)
            // 소리와 진동은 AlarmService가 직접 낸다.
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** 잠금 화면에서는 전체 화면으로 RingingActivity를 띄우고, 사용 중일 때는 헤드업 알림으로 뜬다. */
    fun ringing(context: Context, label: String): Notification {
        val openRinging = PendingIntent.getActivity(
            context,
            0,
            Intent(context, RingingActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION,
            ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(label.ifBlank { context.getString(R.string.app_name) })
            .setContentText(context.getString(R.string.notification_ringing_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openRinging)
            .setFullScreenIntent(openRinging, true)
            .build()
    }
}
