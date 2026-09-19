package com.manu.reeldrop.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.manu.reeldrop.core.ServiceLocator

/** After a reboot, queue resumption is delegated to WorkManager (allowed from the background). */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        ServiceLocator.init(context.applicationContext)
        ResumeWorker.enqueue(context)
    }
}
