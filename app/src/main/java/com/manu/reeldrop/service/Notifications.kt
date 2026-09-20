package com.manu.reeldrop.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.manu.reeldrop.R
import com.manu.reeldrop.core.Constants
import com.manu.reeldrop.core.Formatters
import com.manu.reeldrop.domain.DownloadJob
import com.manu.reeldrop.domain.JobStatus
import com.manu.reeldrop.ui.MainActivity

/**
 * Owns the app's notification surface.
 *
 * There is deliberately one live notification for the whole queue. The foreground service and
 * the engine update the same notification id, so a progress event can never create a second
 * card for the same job. Finished/error notifications also reuse one id and therefore behave as
 * a single result card instead of accumulating duplicates.
 */
class Notifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)
    private val progressLock = Any()
    private var lastProgressNotificationAt = 0L

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val system = context.getSystemService(NotificationManager::class.java) ?: return

        system.createNotificationChannel(
            NotificationChannel(
                Constants.CHANNEL_PROGRESS,
                context.getString(R.string.channel_progress_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_progress_desc)
                setShowBadge(false)
                enableVibration(false)
            },
        )
        system.createNotificationChannel(
            NotificationChannel(
                Constants.CHANNEL_RESULTS,
                context.getString(R.string.channel_result_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.channel_result_desc)
                enableVibration(true)
            },
        )
        system.createNotificationChannel(
            NotificationChannel(
                Constants.CHANNEL_SERVICE,
                context.getString(R.string.channel_progress_name),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = "Mantiene el servicio de descargas en segundo plano"
                setShowBadge(false)
            },
        )
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = Constants.ACTION_OPEN_APP
        }
        return PendingIntent.getActivity(
            context,
            100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** One compact, grouped notification for every active download. */
    fun buildActiveNotification(jobs: List<DownloadJob>): Notification {
        val active = jobs.filter { it.isActive }
        val average = if (active.isEmpty()) 0 else {
            (active.sumOf { it.progress.toDouble() } / active.size).toInt()
        }
        val totalSpeed = active.sumOf { it.speedBps }
        val single = active.size == 1
        val headline = if (single) active.first().displayTitle().take(60)
        else "${Constants.APP_NAME} · ${active.size} descargas"
        val summary = if (single) {
            val job = active.first()
            "${job.progress.toInt()}% · ${if (job.speedBps > 0) Formatters.speed(job.speedBps) else "calculando velocidad…"}"
        } else {
            "$average% medio${if (totalSpeed > 0) " · ${Formatters.speed(totalSpeed)}" else ""}"
        }
        val builder = NotificationCompat.Builder(context, Constants.CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification_download)
            .setContentTitle(headline)
            .setContentText(summary)
            .setSubText(if (single) "Descarga activa" else "Cola de descargas")
            .setProgress(100, average.coerceIn(0, 100), active.none { it.totalBytes > 0 })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setColor(0xFFA855F7.toInt())
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent())
            .addAction(
                R.drawable.ic_notification_error,
                "Cancelar todo",
                actionIntent(Constants.ACTION_CANCEL_ALL, 101),
            )
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (single) {
            val job = active.first()
            val detail = buildString {
                append("${job.progress.toInt()}%")
                if (job.speedBps > 0) append(" · ${Formatters.speed(job.speedBps)}")
                job.etaSeconds?.let { append(" · resta ${Formatters.eta(it)}") }
                append("\n${Formatters.bytes(job.downloadedBytes)}")
                if (job.totalBytes > 0) append(" / ${Formatters.bytes(job.totalBytes)}")
                job.serverMessage?.takeIf { it.isNotBlank() }?.let { append("\n$it") }
            }
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(detail))
        } else {
            builder.setStyle(NotificationCompat.InboxStyle().also { style ->
                active.take(5).forEach { job ->
                    style.addLine("${job.progress.toInt()}% · ${job.displayTitle().take(42)}")
                }
            })
        }
        return builder.build()
    }

    /** Throttled update path used by the engine; it always targets one id. */
    fun notifyProgress(jobs: List<DownloadJob>) {
        val active = jobs.filter { it.isActive }
        synchronized(progressLock) {
            if (active.isEmpty()) {
                manager.cancel(Constants.NOTIFICATION_SERVICE_ID)
                return
            }
            val now = System.currentTimeMillis()
            if (now - lastProgressNotificationAt < 450L) return
            lastProgressNotificationAt = now
            safeNotify(Constants.NOTIFICATION_SERVICE_ID, buildActiveNotification(active))
        }
    }

    /** Forces a queue refresh after cancellation/removal, including clearing the card when empty. */
    fun refreshProgress(jobs: List<DownloadJob>) {
        synchronized(progressLock) {
            lastProgressNotificationAt = 0L
            val active = jobs.filter { it.isActive }
            if (active.isEmpty()) {
                manager.cancel(Constants.NOTIFICATION_SERVICE_ID)
            } else {
                safeNotify(Constants.NOTIFICATION_SERVICE_ID, buildActiveNotification(active))
            }
        }
    }

    fun buildIdleServiceNotification(): Notification =
        NotificationCompat.Builder(context, Constants.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification_download)
            .setContentTitle(Constants.APP_NAME)
            .setContentText("Preparando descarga…")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setColor(0xFFA855F7.toInt())
            .setContentIntent(contentIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    /** One reusable result card; a later result updates the same card instead of stacking. */
    fun buildResult(job: DownloadJob): Notification {
        val completed = job.status == JobStatus.COMPLETED
        val builder = NotificationCompat.Builder(context, Constants.CHANNEL_RESULTS)
            .setSmallIcon(if (completed) R.drawable.ic_notification_done else R.drawable.ic_notification_error)
            .setContentTitle(if (completed) "Descarga completada" else "La descarga falló")
            .setContentText(
                if (completed) {
                    "${job.displayTitle().take(50)} · ${Formatters.bytes(job.totalBytes.takeIf { it > 0 } ?: job.downloadedBytes)}"
                } else {
                    job.errorMessage?.take(120) ?: "Error desconocido"
                },
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    if (completed) {
                        "${Constants.APP_NAME}\n${job.displayTitle()}\nGuardado en el servidor como ${job.filename ?: "archivo"}."
                    } else {
                        "${Constants.APP_NAME}\n${job.errorMessage ?: "Error desconocido"}\nToca Reintentar para volver a intentarlo."
                    },
                ),
            )
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setColor(if (completed) 0xFF34D399.toInt() else 0xFFF87171.toInt())
            .setCategory(if (completed) NotificationCompat.CATEGORY_STATUS else NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(contentIntent())

        if (completed) {
            builder.addAction(
                R.drawable.ic_notification_done,
                context.getString(R.string.notif_action_open),
                actionIntent(Constants.ACTION_OPEN_APP, 102),
            )
        } else {
            val retryIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = Constants.ACTION_RETRY
                putExtra(Constants.EXTRA_LOCAL_ID, job.localId)
            }
            builder.addAction(
                R.drawable.ic_notification_download,
                context.getString(R.string.notif_action_retry),
                PendingIntent.getBroadcast(
                    context,
                    103,
                    retryIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        return builder.build()
    }

    fun notifyResult(job: DownloadJob, jobs: List<DownloadJob> = emptyList()) {
        synchronized(progressLock) {
            // Keep the foreground card visible when another download is still active. The
            // service observer will remove it once the last active job finishes.
            if (jobs.none { it.isActive }) {
                manager.cancel(Constants.NOTIFICATION_SERVICE_ID)
            } else {
                safeNotify(Constants.NOTIFICATION_SERVICE_ID, buildActiveNotification(jobs))
            }
            safeNotify(Constants.NOTIFICATION_RESULT_ID, buildResult(job))
        }
    }

    fun cancel(job: DownloadJob) {
        // Kept for callers compiled against the original API. Queue-aware callers should use
        // refreshProgress so another active job is never hidden accidentally.
        if (job.status.isTerminal) refreshProgress(emptyList())
    }

    fun cancelAll() {
        synchronized(progressLock) {
            lastProgressNotificationAt = 0L
            runCatching { manager.cancelAll() }
        }
    }

    /** POST_NOTIFICATIONS may be denied; never crash because of it. */
    private fun safeNotify(id: Int, notification: Notification) {
        runCatching { manager.notify(id, notification) }
    }

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || manager.areNotificationsEnabled()

    fun canPostNotifications(): Boolean = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.POST_NOTIFICATIONS,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
}
