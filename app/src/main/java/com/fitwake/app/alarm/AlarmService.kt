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
import com.fitwake.alarm.DismissMethod
import com.fitwake.alarm.RingVolumePolicy
import com.fitwake.alarm.Snooze
import com.fitwake.app.alarmRepository
import com.fitwake.app.appScope
import com.fitwake.app.data.WakeLogEntity
import com.fitwake.app.wakeLogRepository
import com.fitwake.pose.Exercise
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

    data class Ringing(
        val alarm: Alarm,
        /** 이번에 할 운동. 랜덤 미션도 울리는 동안에는 바뀌지 않는다. */
        val exercise: Exercise,
        /** 지금까지 스누즈한 횟수. */
        val snoozeCount: Int,
        /** 처음 울린 시각 (epoch millis). 스누즈를 거쳐도 유지되어 기록에 쓰인다. */
        val firstRingAt: Long,
    ) : RingState
}

/**
 * 알람이 울리는 동안 살아 있는 포그라운드 서비스.
 * 화면(RingingActivity)이 꺼지거나 앱이 스와이프로 종료돼도 소리는 계속 나고,
 * 미션 완료, 긴급 해제, 스누즈로만 멈춘다 (PRD AC-05).
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
            ACTION_RING -> onRing(
                alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1),
                kind = RingKind.entries.getOrElse(intent.getIntExtra(EXTRA_KIND, 0)) { RingKind.SCHEDULED },
                snoozeCount = intent.getIntExtra(EXTRA_SNOOZE_COUNT, 0),
                firstRingAt = intent.getLongExtra(EXTRA_FIRST_RING_AT, 0),
            )
            ACTION_MISSION -> {
                missionActive = intent.getBooleanExtra(EXTRA_ACTIVE, false)
                lastProgressMs = SystemClock.elapsedRealtime()
            }
            ACTION_PROGRESS -> lastProgressMs = SystemClock.elapsedRealtime()
            ACTION_DISMISS -> dismiss(
                DismissMethod.entries.getOrElse(intent.getIntExtra(EXTRA_METHOD, 0)) { DismissMethod.MISSION },
                intent.getIntExtra(EXTRA_REPS, 0),
            )
            ACTION_SNOOZE -> snooze()
            else -> if (_state.value == RingState.Idle) stopSelf()
        }
        return START_REDELIVER_INTENT
    }

    private fun onRing(alarmId: Long, kind: RingKind, snoozeCount: Int, firstRingAt: Long) {
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
            // 예약된 회차일 때만 다음 회차를 예약한다. 스누즈·재확인으로 다시 울리는 건 같은 회차.
            if (kind == RingKind.SCHEDULED) alarmRepository.onFired(alarm)

            // 이미 다른 알람이 울리는 중이면 그 알람을 끌 때 같이 끝난다.
            if (_state.value is RingState.Ringing) return@launch

            val now = System.currentTimeMillis()
            _state.value = RingState.Ringing(
                alarm = alarm,
                exercise = alarm.pickExercise(),
                snoozeCount = if (kind == RingKind.SNOOZED) snoozeCount else 0,
                firstRingAt = if (kind == RingKind.SNOOZED && firstRingAt > 0) firstRingAt else now,
            )
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

    private fun dismiss(method: DismissMethod, reps: Int) {
        val ringing = _state.value as? RingState.Ringing
        stopRinging()
        if (ringing != null) record(ringing, method, reps)
    }

    /** 기록을 남기고, 필요하면 기상 재확인을 예약한다. 서비스가 끝난 뒤에도 마치도록 앱 스코프에서 실행. */
    private fun record(ringing: RingState.Ringing, method: DismissMethod, reps: Int) {
        val context = applicationContext
        context.appScope.launch {
            val logId = context.wakeLogRepository.add(
                WakeLogEntity(
                    alarmId = ringing.alarm.id,
                    label = ringing.alarm.label,
                    ringStartedAt = ringing.firstRingAt,
                    dismissedAt = System.currentTimeMillis(),
                    method = method.name,
                    exercise = ringing.exercise.name.takeIf { method == DismissMethod.MISSION },
                    reps = reps,
                    snoozeCount = ringing.snoozeCount,
                    wakeCheckPassed = null,
                ),
            )
            if (method == DismissMethod.MISSION && ringing.alarm.wakeCheck) {
                WakeCheck.schedule(context, logId, ringing.alarm.id)
            }
        }
    }

    private fun snooze() {
        val ringing = _state.value as? RingState.Ringing ?: return
        if (!Snooze.canSnooze(ringing.alarm, ringing.snoozeCount)) return
        AlarmScheduler(this).scheduleSnooze(
            alarmId = ringing.alarm.id,
            snoozeCount = ringing.snoozeCount + 1,
            firstRingAt = ringing.firstRingAt,
            atMs = System.currentTimeMillis() + Snooze.MINUTES * 60_000L,
        )
        stopRinging()
    }

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

    private enum class RingKind {
        /** 예약된 시각에 울림. */
        SCHEDULED,

        /** 스누즈 후 다시 울림. */
        SNOOZED,

        /** 기상 재확인에 응답하지 않아 다시 울림. */
        RECHECK,
    }

    companion object {
        private const val TAG = "AlarmService"
        private const val ACTION_RING = "com.fitwake.app.action.RING"
        private const val ACTION_MISSION = "com.fitwake.app.action.MISSION"
        private const val ACTION_PROGRESS = "com.fitwake.app.action.PROGRESS"
        private const val ACTION_DISMISS = "com.fitwake.app.action.DISMISS"
        private const val ACTION_SNOOZE = "com.fitwake.app.action.SNOOZE"
        private const val EXTRA_ALARM_ID = "alarm_id"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_SNOOZE_COUNT = "snooze_count"
        private const val EXTRA_FIRST_RING_AT = "first_ring_at"
        private const val EXTRA_ACTIVE = "active"
        private const val EXTRA_METHOD = "method"
        private const val EXTRA_REPS = "reps"
        private const val VOLUME_TICK_MS = 250L
        private const val WAKE_LOCK_TIMEOUT_MS = 60 * 60 * 1000L

        private val _state = MutableStateFlow<RingState>(RingState.Idle)
        val state: StateFlow<RingState> = _state.asStateFlow()

        fun ring(context: Context, alarmId: Long) = start(context, alarmId, RingKind.SCHEDULED)

        fun ringSnoozed(context: Context, alarmId: Long, snoozeCount: Int, firstRingAt: Long) =
            start(context, alarmId, RingKind.SNOOZED) {
                putExtra(EXTRA_SNOOZE_COUNT, snoozeCount).putExtra(EXTRA_FIRST_RING_AT, firstRingAt)
            }

        /** 기상 재확인에 응답이 없을 때 (PRD WK-01). */
        fun ringAgain(context: Context, alarmId: Long) = start(context, alarmId, RingKind.RECHECK)

        private fun start(context: Context, alarmId: Long, kind: RingKind, extras: Intent.() -> Unit = {}) {
            ContextCompat.startForegroundService(
                context,
                intent(context, ACTION_RING)
                    .putExtra(EXTRA_ALARM_ID, alarmId)
                    .putExtra(EXTRA_KIND, kind.ordinal)
                    .apply(extras),
            )
        }

        /** 미션 화면에 들어가면 볼륨을 줄이고, 나가면 되돌린다. */
        fun setMissionActive(context: Context, active: Boolean) =
            send(context, intent(context, ACTION_MISSION).putExtra(EXTRA_ACTIVE, active))

        /** 1회 성공할 때마다 호출. 60초간 호출이 없으면 볼륨이 다시 커진다. */
        fun reportProgress(context: Context) = send(context, intent(context, ACTION_PROGRESS))

        fun dismissByMission(context: Context, reps: Int) = send(
            context,
            intent(context, ACTION_DISMISS)
                .putExtra(EXTRA_METHOD, DismissMethod.MISSION.ordinal)
                .putExtra(EXTRA_REPS, reps),
        )

        fun dismissEmergency(context: Context) = send(
            context,
            intent(context, ACTION_DISMISS).putExtra(EXTRA_METHOD, DismissMethod.EMERGENCY.ordinal),
        )

        /** 미니 미션을 마친 뒤 호출 (PRD AL-08). */
        fun snooze(context: Context) = send(context, intent(context, ACTION_SNOOZE))

        private fun send(context: Context, intent: Intent) {
            if (_state.value == RingState.Idle) return
            context.startService(intent)
        }

        private fun intent(context: Context, action: String) =
            Intent(context, AlarmService::class.java).setAction(action)
    }
}
