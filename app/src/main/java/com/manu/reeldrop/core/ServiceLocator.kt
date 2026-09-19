package com.manu.reeldrop.core

import android.content.Context
import com.manu.reeldrop.data.local.JobStore
import com.manu.reeldrop.data.local.SettingsRepository
import com.manu.reeldrop.data.remote.ReelDropApi
import com.manu.reeldrop.data.repository.LibraryRepository
import com.manu.reeldrop.data.repository.ServerRepository
import com.manu.reeldrop.service.DownloadEngine
import com.manu.reeldrop.service.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Tiny hand-rolled dependency container. It keeps the project free of annotation
 * processors (faster builds, fewer moving parts) while still giving a single,
 * testable place where every singleton is wired.
 */
object ServiceLocator {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var initialized = false

    lateinit var settings: SettingsRepository
        private set
    lateinit var jobStore: JobStore
        private set
    lateinit var api: ReelDropApi
        private set
    lateinit var engine: DownloadEngine
        private set
    lateinit var notifications: Notifications
        private set
    lateinit var library: LibraryRepository
        private set
    lateinit var server: ServerRepository
        private set

    val scope: CoroutineScope get() = appScope

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            settings = SettingsRepository(app, appScope)
            notifications = Notifications(app)
            notifications.ensureChannels()
            jobStore = JobStore(app)
            api = ReelDropApi { settings.cached }
            library = LibraryRepository(api)
            server = ServerRepository(api)
            engine = DownloadEngine(
                context = app,
                api = api,
                jobStore = jobStore,
                settings = settings,
                notifications = notifications,
                scope = appScope,
            )
            appScope.launch {
                jobStore.load()
                engine.resumePending()
            }
            initialized = true
        }
    }
}
