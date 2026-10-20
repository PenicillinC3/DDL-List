package com.gammac3.ddlapp.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        val title = intent.getStringExtra(EXTRA_TITLE)
        if (id < 0 || title.isNullOrBlank()) return
        val lead = intent.getLongExtra(EXTRA_LEAD, 0L)
        val app = context.applicationContext as com.gammac3.ddlapp.DdlApplication
        app.container.notifications.show(id, title, lead)
        // 截止时刻刚过：桌面倒计时需切到下一条 upcoming
        com.gammac3.ddlapp.widget.DdlWidgets.refresh(context)
    }

    companion object {
        const val EXTRA_ID = "ddl_id"
        const val EXTRA_TITLE = "ddl_title"
        const val EXTRA_LEAD = "ddl_lead"
    }
}
