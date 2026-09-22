package com.manu.reeldrop.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "reeldrop_settings")

/**
 * Single source of truth for user preferences.
 *
 * [flow] is reactive for the UI, [cached] gives the download engine an instant snapshot
 * without suspending (needed from notifications and services).
 */
class SettingsRepository(
    private val context: Context,
    scope: CoroutineScope,
) {
    private object Keys {
        val serverUrl = stringPreferencesKey("server_url")
        val serverMode = stringPreferencesKey("server_mode")
        val apiToken = stringPreferencesKey("api_token")
        val apiTokenEnabled = booleanPreferencesKey("api_token_enabled")
        val quality = stringPreferencesKey("quality")
        val theme = stringPreferencesKey("theme")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val maxAttempts = intPreferencesKey("max_attempts")
        val concurrent = intPreferencesKey("concurrent_downloads")
        val autoRetry = booleanPreferencesKey("auto_retry")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val autoSave = booleanPreferencesKey("auto_save")
        val notifications = booleanPreferencesKey("notifications")
        val clipboardAutofill = booleanPreferencesKey("clipboard_autofill")
        val keepAwake = booleanPreferencesKey("keep_awake")
        val lastServer = stringPreferencesKey("last_server")
        val saveFolderUri = stringPreferencesKey("save_folder_uri")
        val saveFolderLabel = stringPreferencesKey("save_folder_label")
        val useCustomFolder = booleanPreferencesKey("use_custom_folder")
        val permissionsRequested = booleanPreferencesKey("permissions_requested")
    }

    private val _cached = MutableStateFlow(AppSettings())
    val cachedFlow: StateFlow<AppSettings> = _cached
    val cached: AppSettings get() = _cached.value

    val flow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            serverUrl = prefs[Keys.serverUrl]?.takeIf { it.isNotBlank() } ?: defaults.serverUrl,
            serverModeId = prefs[Keys.serverMode] ?: defaults.serverModeId,
            apiToken = prefs[Keys.apiToken].orEmpty(),
            apiTokenEnabled = prefs[Keys.apiTokenEnabled] ?: defaults.apiTokenEnabled,
            qualityId = prefs[Keys.quality] ?: defaults.qualityId,
            themeId = prefs[Keys.theme] ?: defaults.themeId,
            themeModeId = prefs[Keys.themeMode] ?: defaults.themeModeId,
            dynamicColor = prefs[Keys.dynamicColor] ?: defaults.dynamicColor,
            maxAttempts = prefs[Keys.maxAttempts] ?: defaults.maxAttempts,
            concurrentDownloads = prefs[Keys.concurrent] ?: defaults.concurrentDownloads,
            autoRetry = prefs[Keys.autoRetry] ?: defaults.autoRetry,
            wifiOnly = prefs[Keys.wifiOnly] ?: defaults.wifiOnly,
            autoSaveToDevice = prefs[Keys.autoSave] ?: defaults.autoSaveToDevice,
            notificationsEnabled = prefs[Keys.notifications] ?: defaults.notificationsEnabled,
            clipboardAutofill = prefs[Keys.clipboardAutofill] ?: defaults.clipboardAutofill,
            keepScreenAwakeWhileDownloading = prefs[Keys.keepAwake] ?: defaults.keepScreenAwakeWhileDownloading,
            lastSuccessfulServer = prefs[Keys.lastServer].orEmpty(),
            saveFolderUri = prefs[Keys.saveFolderUri].orEmpty(),
            saveFolderLabel = prefs[Keys.saveFolderLabel].orEmpty(),
            useCustomFolder = prefs[Keys.useCustomFolder] ?: defaults.useCustomFolder,
            permissionsRequested = prefs[Keys.permissionsRequested] ?: defaults.permissionsRequested,
        )
    }

    init {
        scope.launch {
            flow.collect { _cached.value = it }
        }
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val current = flowSnapshot(prefs)
            val next = transform(current)
            prefs[Keys.serverUrl] = next.serverUrl
            prefs[Keys.serverMode] = next.serverModeId
            prefs[Keys.apiToken] = next.apiToken
            prefs[Keys.apiTokenEnabled] = next.apiTokenEnabled
            prefs[Keys.quality] = next.qualityId
            prefs[Keys.theme] = next.themeId
            prefs[Keys.themeMode] = next.themeModeId
            prefs[Keys.dynamicColor] = next.dynamicColor
            prefs[Keys.maxAttempts] = next.maxAttempts.coerceIn(0, 10)
            prefs[Keys.concurrent] = next.concurrentDownloads.coerceIn(1, 5)
            prefs[Keys.autoRetry] = next.autoRetry
            prefs[Keys.wifiOnly] = next.wifiOnly
            prefs[Keys.autoSave] = next.autoSaveToDevice
            prefs[Keys.notifications] = next.notificationsEnabled
            prefs[Keys.clipboardAutofill] = next.clipboardAutofill
            prefs[Keys.keepAwake] = next.keepScreenAwakeWhileDownloading
            prefs[Keys.lastServer] = next.lastSuccessfulServer
            prefs[Keys.saveFolderUri] = next.saveFolderUri
            prefs[Keys.saveFolderLabel] = next.saveFolderLabel
            prefs[Keys.useCustomFolder] = next.useCustomFolder
            prefs[Keys.permissionsRequested] = next.permissionsRequested
        }
    }

    private fun flowSnapshot(prefs: Preferences): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            serverUrl = prefs[Keys.serverUrl]?.takeIf { it.isNotBlank() } ?: defaults.serverUrl,
            serverModeId = prefs[Keys.serverMode] ?: defaults.serverModeId,
            apiToken = prefs[Keys.apiToken].orEmpty(),
            apiTokenEnabled = prefs[Keys.apiTokenEnabled] ?: defaults.apiTokenEnabled,
            qualityId = prefs[Keys.quality] ?: defaults.qualityId,
            themeId = prefs[Keys.theme] ?: defaults.themeId,
            themeModeId = prefs[Keys.themeMode] ?: defaults.themeModeId,
            dynamicColor = prefs[Keys.dynamicColor] ?: defaults.dynamicColor,
            maxAttempts = prefs[Keys.maxAttempts] ?: defaults.maxAttempts,
            concurrentDownloads = prefs[Keys.concurrent] ?: defaults.concurrentDownloads,
            autoRetry = prefs[Keys.autoRetry] ?: defaults.autoRetry,
            wifiOnly = prefs[Keys.wifiOnly] ?: defaults.wifiOnly,
            autoSaveToDevice = prefs[Keys.autoSave] ?: defaults.autoSaveToDevice,
            notificationsEnabled = prefs[Keys.notifications] ?: defaults.notificationsEnabled,
            clipboardAutofill = prefs[Keys.clipboardAutofill] ?: defaults.clipboardAutofill,
            keepScreenAwakeWhileDownloading = prefs[Keys.keepAwake] ?: defaults.keepScreenAwakeWhileDownloading,
            lastSuccessfulServer = prefs[Keys.lastServer].orEmpty(),
            saveFolderUri = prefs[Keys.saveFolderUri].orEmpty(),
            saveFolderLabel = prefs[Keys.saveFolderLabel].orEmpty(),
            useCustomFolder = prefs[Keys.useCustomFolder] ?: defaults.useCustomFolder,
            permissionsRequested = prefs[Keys.permissionsRequested] ?: defaults.permissionsRequested,
        )
    }
}
