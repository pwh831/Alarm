package com.fitwake.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.fitwake.app.alarm.AlarmNotifications
import com.fitwake.app.alarm.AlarmScheduler
import com.fitwake.app.data.AlarmRepository
import com.fitwake.app.data.AppDatabase

class FitWakeApplication : Application() {
    val repository: AlarmRepository by lazy {
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "fitwake.db").build()
        AlarmRepository(db.alarmDao(), AlarmScheduler(this))
    }

    override fun onCreate() {
        super.onCreate()
        AlarmNotifications.createChannel(this)
    }
}

val Context.alarmRepository: AlarmRepository
    get() = (applicationContext as FitWakeApplication).repository
