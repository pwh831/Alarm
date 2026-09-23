package com.fitwake.app.alarm

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.fitwake.alarm.Alarm
import com.fitwake.alarm.RingVolumePolicy
import com.fitwake.app.alarmRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface RingState {
    data object Idle : RingState
    /** 서비스가 알람 정보를 불러오는 중. */
    data object Starting : RingState
    data class Ringing(val alarm: Alarm) : RingState
}

/**
 * 알람이 울리는 동안 살아 있는 포그라운드 서비스.
 * 화면(RingingActivity)이 꺼지거나 앱이 스와이프로 종료돼도 소리는 계속 나고,
 * 미션을 완료하거나 긴급 해제해야만 멈춘다 (PRD AC-05).
 */
class AlarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: AlarmPlayer
    private var wakeLock: PowerManager.WakeLock? = null
    private var volumeJob: Job? = null
    private val volumePolicy = RingVolumePolicy()

    private var ringStartMs = 0L
    private var missionActive = false
    private var lastProgressMs = 0L

    override fun onCreate() {
        super.onCreate()
        player = AlarmPlayer(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> onRing(intent.getLongExtra(EXTRA_ALARM_ID, -1))
            ACTION_MISSION -> {
                missionActive = intent.getBooleanExtra(EXTRA_ACTIVE, false)
                lastProgressMs = SystemClock.elapsedRealtime()
            }
            ACTION_PROGRESS -> lastProgressMs = SystemClock.elapsedRealtime()
            ACTION_DISMISS -> stopRinging()
            else -> if (_state.value == RingState.Idle) stopSelf()
        }
        return START_REDELIVER_INTENT
    }

    private fun onRing(alarmId: Long) {
        // startForegroundService 호출마다 곧바로 startForeground가 필요하므로, 알람 정보를 읽기 전에 먼저 알림을 띄운다.
        val current = _state.value
        if (current == RingState.Idle) _state.value = RingState.Starting
        ServiceCompat.startForeground(
            this,
            AlarmNotifications.NOTIFICATION_ID,
            AlarmNotifications.ringing(this, (current as? RingState.Ringing)?.alarm?.label.orEmpty()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        acquireWakeLock()

        scope.launch {
            val alarm = alarmRepository.get(alarmId)
            if (alarm == null) {
                Log.w(TAG, "alarm $alarmId no longer exists")
                if (_state.value == RingState.Starting) stopRinging()
                return@launch
            }
            alarmRepository.onFired(alarm)

            // 이미 다른 알람이 울리는 중이면 그 알람을 끌 때 같이 끝난다.
            if (_state.value is RingState.Ringing) return@launch

            _state.value = RingState.Ringing(alarm)
            ringStartMs = SystemClock.elapsedRealtime()
            lastProgressMs = ringStartMs
            missionActive = false
            player.start(alarm.soundUri, alarm.vibrate, currentVolume(alarm))
            getSystemService(NotificationManager::class.java).notify(
                AlarmNotifications.NOTIFICATION_ID,
                AlarmNotifications.ringing(this@AlarmService, alarm.label),
            )

            volumeJob?.cancel()
            volumeJob = scope.launch {
                while (isActive) {
                    player.setVolume(currentVolume(alarm))
                    delay(VOLUME_TICK_MS)
                }
            }
        }
    }

    private fun currentVolume(alarm: Alarm) = volumePolicy.volume(
        nowMs = SystemClock.elapsedRealtime(),
        ringStartMs = ringStartMs,
        volumeRamp = alarm.volumeRamp,
        missionActive = missionActive,
        lastProgressMs = lastProgressMs,
    )

    private fun stopRinging() {
        volumeJob?.cancel()
        player.stop()
        _state.value = RingState.Idle
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FitWake:ringing")
            .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }
    }

    override fun onDestroy() {
        volumeJob?.cancel()
        player.stop()
        wakeLock?.let { if (it.isHeld) it.release() }
        _state.value = RingState.Idle
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmService"
        private const val ACTION_RING = "com.fitwake.app.action.RING"
        private const val ACTION_MISSION = "com.fitwake.app.action.MISSION"
        private const val ACTION_PROGRESS = "com.fitwake.app.action.PROGRESS"
        private const val ACTION_DISMISS = "com.fitwake.app.action.DISMISS"
        private const val EXTRA_ALARM_ID = "alarm_id"
        private const val EXTRA_ACTIVE = "active"
        private const val VOLUME_TICK_MS = 250L
        private const val WAKE_LOCK_TIMEOUT_MS = 60 * 60 * 1000L

        private val _state = MutableStateFlow<RingState>(RingState.Idle)
        val state: StateFlow<RingState> = _state.asStateFlow()

        fun ring(context: Context, alarmId: Long) {
            ContextCompat.startForegroundService(
                context,
                intent(context, ACTION_RING).putExtra(EXTRA_ALARM_ID, alarmId),
            )
        }

        /** 미션 화면에 들어가면 볼륨을 줄이고, 나가면 되돌린다. */
        fun setMissionActive(context: Context, active: Boolean) =
            send(context, intent(context, ACTION_MISSION).putExtra(EXTRA_ACTIVE, active))

        /** 1회 성공할 때마다 호출. 60초간 호출이 없으면 볼륨이 다시 커진다. */
        fun reportProgress(context: Context) = send(context, intent(context, ACTION_PROGRESS))

        fun dismiss(context: Context) = send(context, intent(context, ACTION_DISMISS))

        private fun send(context: Context, intent: Intent) {
            if (_state.value == RingState.Idle) return
            context.startService(intent)
        }

        private fun intent(context: Context, action: String) =
            Intent(context, AlarmService::class.java).setAction(action)
    }
}
