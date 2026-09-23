package com.fitwake.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.fitwake.alarm.DismissMethod
import com.fitwake.alarm.WakeRecord
import com.fitwake.pose.Exercise
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId

/** 알람 한 번의 기상 기록 (PRD ST-01, ST-02). 시각은 epoch millis. */
@Entity(tableName = "wake_logs")
data class WakeLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alarmId: Long,
    val label: String,
    val ringStartedAt: Long,
    val dismissedAt: Long,
    val method: String,
    val exercise: String?,
    val reps: Int,
    val snoozeCount: Int,
    /** 기상 재확인 결과. null이면 확인하지 않음. */
    val wakeCheckPassed: Boolean?,
)

fun WakeLogEntity.toRecord(zone: ZoneId = ZoneId.systemDefault()): WakeRecord {
    val start = Instant.ofEpochMilli(ringStartedAt)
    return WakeRecord(
        date = start.atZone(zone).toLocalDate(),
        ringStartedAt = start,
        dismissedAt = Instant.ofEpochMilli(dismissedAt),
        method = DismissMethod.entries.firstOrNull { it.name == method } ?: DismissMethod.MISSION,
        exercise = Exercise.entries.firstOrNull { it.name == exercise },
        reps = reps,
        snoozeCount = snoozeCount,
    )
}

@Dao
interface WakeLogDao {
    @Query("SELECT * FROM wake_logs ORDER BY ringStartedAt DESC")
    fun observeAll(): Flow<List<WakeLogEntity>>

    @Insert
    suspend fun insert(log: WakeLogEntity): Long

    @Query("UPDATE wake_logs SET wakeCheckPassed = :passed WHERE id = :id")
    suspend fun setWakeCheck(id: Long, passed: Boolean)
}
