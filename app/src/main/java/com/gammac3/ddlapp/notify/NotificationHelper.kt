package com.gammac3.ddlapp.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.gammac3.ddlapp.MainActivity
import com.gammac3.ddlapp.R

class NotificationHelper(private val context: Context) {

    private var idSeq = 10_000

    fun ensureChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID, "DDL 截止提醒", NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "在截止时刻和自定义提前量准点提醒" }
        manager.createNotificationChannel(channel)
    }

    fun show(ddlId: Long, title: String, leadMinutes: Long) {
        val text = if (leadMinutes == 0L) "⏰ 已经到 deadline 了！" else "⏰ 还有 ${LeadLabels.text(leadMinutes)} 到期"
        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                // 与 MainActivity.EXTRA_OPEN_DDL_ID 保持一致（该常量由 Task 7/9 引入）。
                .putExtra("open_ddl_id", ddlId)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()
        try {
            androidx.core.app.NotificationManagerCompat.from(context).notify(idSeq++, notification)
        } catch (_: SecurityException) {
            // 通知权限被拒：静默丢弃（UI 里已有权限指引）
        }
    }

    companion object {
        const val CHANNEL_ID = "ddl_reminders"
    }
}
