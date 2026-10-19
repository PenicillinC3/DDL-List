package com.gammac3.ddlapp.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val receiverResult = goAsync()
        val app = context.applicationContext as com.gammac3.ddlapp.DdlApplication
        CoroutineScope(Dispatchers.Default).launch {
            try { app.container.repository.rescheduleAll() } finally { receiverResult.finish() }
        }
    }
}
