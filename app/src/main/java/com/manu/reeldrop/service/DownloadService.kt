package com.manu.reeldrop.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.manu.reeldrop.core.Constants
import com.manu.reeldrop.core.ServiceLocator
import com.manu.reeldrop.domain.DownloadJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Keeps downloads alive with a foreground notification while the app is in the background.
 *
 * The heavy lifting happens on the Termux server, so this service only tracks state and
 * publishes notifications — that keeps battery usage negligible.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observer: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastActiveCount = 0

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(applicationContext)
        ServiceLocator.notifications.ensureChannels()
        startForegroundSafely(ServiceLocator.notifications.buildIdleServiceNotification())
        observeJobs()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceLocator.init(applicationContext)
        when (intent?.action) {
            ACTION_ENQUEUE -> {
                val rawUrl = intent.getStringExtra(Constants.EXTRA_URL)
                val quality = intent.getStringExtra("extra_quality")
                if (!rawUrl.isNullOrBlank()) {
                    scope.launch { ServiceLocator.engine.enqueue(rawUrl, quality) }
                }
            }
            Constants.ACTION_RETRY -> intent.getStringExtra(Constants.EXTRA_LOCAL_ID)?.let {
                ServiceLocator.engine.retry(it)
            }
            Constants.ACTION_CANCEL -> intent.getStringExtra(Constants.EXTRA_LOCAL_ID)?.let {
                ServiceLocator.engine.cancel(it)
            }
            Constants.ACTION_CANCEL_ALL -> ServiceLocator.engine.cancelAll()
            Constants.ACTION_STOP_SERVICE -> {
                ServiceLocator.engine.cancelAll()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun observeJobs() {
        observer?.cancel()
        observer = scope.launch {
            ServiceLocator.jobStore.jobs
                .debounce(350)
                .collectLatest { jobs ->
                    val active = jobs.filter { it.isActive }
                    lastActiveCount = active.size
                    if (active.isEmpty()) {
                        ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        releaseWakeLock()
                        stopSelf()
                    } else {
                        val notification = ServiceLocator.notifications.buildActiveNotification(active)
                        startForegroundSafely(notification)
                        updateWakeLock()
                    }
                }
        }
    }

    private fun startForegroundSafely(notification: android.app.Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, Constants.NOTIFICATION_SERVICE_ID, notification, type)
        }
    }

    private fun updateWakeLock() {
        val keepAwake = ServiceLocator.settings.cached.keepScreenAwakeWhileDownloading && lastActiveCount > 0
        if (keepAwake) {
            if (wakeLock == null) {
                val power = getSystemService(PowerManager::class.java)
                wakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ReelDrop:downloads")
                wakeLock?.setReferenceCounted(false)
                runCatching { wakeLock?.acquire(30 * 60 * 1000L) }
            }
        } else {
            releaseWakeLock()
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(startId: Int) {
        // Android 14+ gave us a short deadline: finish politely instead of crashing.
        if (lastActiveCount == 0) stopSelf()
    }

    companion object {
        const val ACTION_ENQUEUE = "com.manu.reeldrop.action.ENQUEUE"
        const val ACTION_START = "com.manu.reeldrop.action.START"
        private const val EXTRA_QUALITY = "extra_quality"

        /** Starts the service, degrading to WorkManager when the OS forbids a background start. */
        fun start(context: Context, url: String? = null, quality: String? = null) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = if (url.isNullOrBlank()) ACTION_START else ACTION_ENQUEUE
                putExtra(Constants.EXTRA_URL, url)
                putExtra(EXTRA_QUALITY, quality)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (notAllowed: Throwable) {
                ResumeWorker.enqueue(context)
            }
        }

        fun retry(context: Context, localId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = Constants.ACTION_RETRY
                putExtra(Constants.EXTRA_LOCAL_ID, localId)
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { ResumeWorker.enqueue(context) }
        }

        fun cancel(context: Context, localId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = Constants.ACTION_CANCEL
                putExtra(Constants.EXTRA_LOCAL_ID, localId)
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply { action = Constants.ACTION_STOP_SERVICE }
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        suspend fun idleMessage(): String {
            delay(0)
            return "${Constants.APP_NAME} listo"
        }

        fun activeCount(): Int = ServiceLocator.engine.activeJobs().size

        fun trackedJob(localId: String): DownloadJob? = ServiceLocator.jobStore.find(localId)
    }
}
