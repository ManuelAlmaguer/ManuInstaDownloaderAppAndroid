package com.manu.reeldrop.util

import android.content.Context
import android.content.Intent

/** Opens the installed Termux app without trying to execute commands silently. */
object TermuxLauncher {

    const val PACKAGE_NAME = "com.termux"

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
        true
    }.getOrDefault(false)

    fun open(context: Context): Boolean = runCatching {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
            ?: return@runCatching false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        true
    }.getOrDefault(false)
}
