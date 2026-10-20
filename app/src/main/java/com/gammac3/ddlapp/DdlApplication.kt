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
        // 数据变更 → 小组件整批刷新；进程冷启动/被系统重建时也对一次表
        container.repository.onChanged = { com.gammac3.ddlapp.widget.DdlWidgets.refresh(this) }
        com.gammac3.ddlapp.widget.DdlWidgets.refresh(this)
    }
}
