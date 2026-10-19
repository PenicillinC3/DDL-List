package com.gammac3.ddlapp

import android.app.Application
import android.content.Context
import com.gammac3.ddlapp.data.DdlDatabase
import com.gammac3.ddlapp.data.DdlRepository
import com.gammac3.ddlapp.notify.AlarmScheduler
import com.gammac3.ddlapp.notify.AndroidAlarmScheduler
import com.gammac3.ddlapp.notify.NotificationHelper

class AppContainer(context: Context) {
    val database: DdlDatabase = DdlDatabase.build(context)
    val scheduler: AlarmScheduler = AndroidAlarmScheduler(context)
    val repository: DdlRepository = DdlRepository(database.dao(), scheduler)
    val notifications: NotificationHelper = NotificationHelper(context)
}

class DdlApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifications.ensureChannel()
    }
}
