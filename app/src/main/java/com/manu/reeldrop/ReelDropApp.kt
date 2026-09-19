package com.manu.reeldrop

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.manu.reeldrop.core.ServiceLocator
import com.manu.reeldrop.service.NetworkMonitor
import com.manu.reeldrop.service.Notifications

/**
 * Application entry point: wires the dependency container, notification channels and the
 * connectivity watcher that resumes queued downloads when the phone gets a network again.
 */
class ReelDropApp : Application(), ImageLoaderFactory {

    private lateinit var networkMonitor: NetworkMonitor

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        ServiceLocator.notifications.ensureChannels()
        networkMonitor = NetworkMonitor(this, ServiceLocator.scope).also { it.start() }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            // Renders the first frame of local videos used as library thumbnails.
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()

    /** Exposed so screens can react to connectivity without re-registering callbacks. */
    fun isOnline(): Boolean = networkMonitor.isOnline()

    fun isOnWifi(): Boolean = networkMonitor.isOnWifi()

    fun notifications(): Notifications = ServiceLocator.notifications
}
