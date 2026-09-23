package com.fitwake.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.fitwake.app.alarm.AlarmNotifications
import com.fitwake.app.alarm.AlarmScheduler
import com.fitwake.app.data.AlarmRepository
import com.fitwake.app.data.AppDatabase
import com.fitwake.app.data.WakeLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class FitWakeApplication : Application() {
    private val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "fitwake.db").build()
    }

    val repository: AlarmRepository by lazy { AlarmRepository(database.alarmDao(), AlarmScheduler(this)) }
    val wakeLogs: WakeLogRepository by lazy { WakeLogRepository(database.wakeLogDao()) }

    /** 서비스·리시버가 끝난 뒤에도 마쳐야 하는 짧은 DB 작업용. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AlarmNotifications.createChannels(this)
    }
}

private val Context.app: FitWakeApplication get() = applicationContext as FitWakeApplication

val Context.alarmRepository: AlarmRepository get() = app.repository
val Context.wakeLogRepository: WakeLogRepository get() = app.wakeLogs
val Context.appScope: CoroutineScope get() = app.appScope
