package com.manu.reeldrop.data.local

import com.manu.reeldrop.core.Constants
import com.manu.reeldrop.domain.AppTheme
import com.manu.reeldrop.domain.Quality
import com.manu.reeldrop.domain.ServerMode
import com.manu.reeldrop.domain.ThemeMode

/** Everything the user can tune, stored in DataStore and read synchronously via [SettingsRepository.cached]. */
data class AppSettings(
    val serverUrl: String = Constants.DEFAULT_SERVER_URL,
    val serverModeId: String = ServerMode.SAME_PHONE.id,
    val apiToken: String = "",
    val qualityId: String = Quality.BEST.id,
    val themeId: String = AppTheme.NEON.id,
    val themeModeId: String = ThemeMode.DARK.id,
    val dynamicColor: Boolean = false,
    val maxAttempts: Int = Constants.DEFAULT_MAX_ATTEMPTS,
    val concurrentDownloads: Int = Constants.DEFAULT_CONCURRENT_DOWNLOADS,
    val autoRetry: Boolean = true,
    val wifiOnly: Boolean = false,
    val autoSaveToDevice: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val clipboardAutofill: Boolean = true,
    val keepScreenAwakeWhileDownloading: Boolean = false,
    val lastSuccessfulServer: String = "",
    /** Storage Access Framework tree uri chosen by the user to store videos. */
    val saveFolderUri: String = "",
    val saveFolderLabel: String = "",
    /** When true, finished downloads are copied into [saveFolderUri] and the library can browse it. */
    val useCustomFolder: Boolean = false,
    /** True once the app has asked for the runtime permissions at least one time. */
    val permissionsRequested: Boolean = false,
) {
    val quality: Quality get() = Quality.fromId(qualityId)
    val serverMode: ServerMode get() = ServerMode.fromId(serverModeId)
    val theme: AppTheme get() = AppTheme.fromId(themeId)
    val themeMode: ThemeMode get() = ThemeMode.fromId(themeModeId)
}
