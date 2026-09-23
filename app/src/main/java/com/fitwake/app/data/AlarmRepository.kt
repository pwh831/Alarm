package com.fitwake.app.data

import com.fitwake.alarm.Alarm
import com.fitwake.app.alarm.AlarmScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.ZonedDateTime

/** 알람 저장과 AlarmManager 예약을 항상 함께 처리한다. */
class AlarmRepository(
    private val dao: AlarmDao,
    private val scheduler: AlarmScheduler,
) {
    val alarms: Flow<List<Alarm>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun get(id: Long): Alarm? = dao.get(id)?.toModel()

    /** 저장 후 예약까지 마친 알람을 돌려준다. */
    suspend fun save(alarm: Alarm): Alarm {
        val saved = if (alarm.id == 0L) {
            alarm.copy(id = dao.insert(alarm.toEntity()))
        } else {
            dao.update(alarm.toEntity())
            alarm
        }
        scheduler.schedule(saved)
        return saved
    }

    suspend fun setEnabled(alarm: Alarm, enabled: Boolean): Alarm = save(alarm.copy(enabled = enabled))

    suspend fun delete(alarm: Alarm) {
        scheduler.cancel(alarm.id)
        dao.delete(alarm.id)
    }

    /** 알람이 울린 직후: 반복 알람은 다음 회차를 예약하고, 1회용 알람은 끈다. */
    suspend fun onFired(alarm: Alarm) {
        if (alarm.isRepeating) {
            scheduler.schedule(alarm, ZonedDateTime.now().plusMinutes(1))
        } else {
            dao.update(alarm.copy(enabled = false).toEntity())
            scheduler.cancel(alarm.id)
        }
    }

    /** 재부팅, 앱 업데이트, 시간대 변경 뒤 모든 알람을 다시 예약한다. */
    suspend fun rescheduleAll() {
        dao.getAll().forEach { scheduler.schedule(it.toModel()) }
    }
}
