package com.fitwake.app.data

import com.fitwake.alarm.WakeRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WakeLogRepository(private val dao: WakeLogDao) {
    val logs: Flow<List<WakeLogEntity>> = dao.observeAll()
    val records: Flow<List<WakeRecord>> = logs.map { list -> list.map { it.toRecord() } }

    suspend fun add(log: WakeLogEntity): Long = dao.insert(log)

    suspend fun setWakeCheck(logId: Long, passed: Boolean) = dao.setWakeCheck(logId, passed)
}
