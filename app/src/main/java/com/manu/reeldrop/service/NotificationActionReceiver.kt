package com.manu.reeldrop.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.manu.reeldrop.core.Constants
import com.manu.reeldrop.core.ServiceLocator
import com.manu.reeldrop.ui.MainActivity

/** Handles the buttons on the progress and result notifications. */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        ServiceLocator.init(context.applicationContext)
        val localId = intent.getStringExtra(Constants.EXTRA_LOCAL_ID).orEmpty()
        when (intent.action) {
            Constants.ACTION_CANCEL -> if (localId.isNotBlank()) {
                ServiceLocator.engine.cancel(localId)
            }
            Constants.ACTION_RETRY -> if (localId.isNotBlank()) {
                ServiceLocator.engine.retry(localId)
                DownloadService.retry(context, localId)
            }
            Constants.ACTION_OPEN_APP -> {
                val open = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(Constants.EXTRA_LOCAL_ID, localId)
                }
                context.startActivity(open)
            }
        }
    }
}
