package com.fitwake.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

// 아직 배포 전이라 스키마 버전은 1로 유지한다. 첫 배포 뒤부터는 버전을 올리고 Migration을 추가할 것.
@Database(entities = [AlarmEntity::class, WakeLogEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun wakeLogDao(): WakeLogDao
}
