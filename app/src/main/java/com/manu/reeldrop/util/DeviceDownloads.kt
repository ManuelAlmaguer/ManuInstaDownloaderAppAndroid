package com.manu.reeldrop.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.manu.reeldrop.core.Constants
import com.manu.reeldrop.data.local.SettingsRepository
import java.io.File

/**
 * Hands a finished server file to Android's DownloadManager so it lands in the phone
 * gallery (Movies/ReelDrop) with its own robust retry logic and a system notification.
 */
object DeviceDownloads {

    fun saveToDevice(
        context: Context,
        settings: SettingsRepository,
        fileName: String,
        url: String,
    ): Long? = runCatching {
        val manager = context.getSystemService(DownloadManager::class.java) ?: return null
        val target = Uri.fromFile(File(Environment.DIRECTORY_MOVIES, "ReelDrop/$fileName"))
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setDescription("Guardado por ${Constants.APP_NAME}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "ReelDrop/$fileName")

        settings.cached.apiTokenForRequests.takeIf { it.isNotBlank() }?.let {
            request.addRequestHeader("X-Api-Token", it)
        }
        manager.enqueue(request)
    }.getOrNull()
}
