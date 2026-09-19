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
import kotlin.math.abs

/**
 * Owns every notification the app posts:
 *
 *  * a foreground/download notification per active job (percent, speed, ETA, size, cancel),
 *  * a grouped summary when several downloads run at once,
 *  * a result notification when a download finishes or fails (open / share / retry).
 */
class Notifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

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

    private fun actionIntent(action: String, localId: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(Constants.EXTRA_LOCAL_ID, localId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun idFor(job: DownloadJob): Int = abs(job.localId.hashCode()) % 100_000 + 1

    /** Per-download progress notification with live speed, size and ETA. */
    fun buildProgress(job: DownloadJob, ongoing: Boolean = true): Notification {
        val percent = job.progress.toInt().coerceIn(0, 100)
        val total = if (job.totalBytes > 0) job.totalBytes else null
        val sizeText = if (total != null) {
            "${Formatters.bytes(job.downloadedBytes)} / ${Formatters.bytes(total)}"
        } else {
            Formatters.bytes(job.downloadedBytes)
        }
        val statusLine = buildString {
            append(job.displayTitle().take(60))
            append('\n')
            append("$percent%")
            if (job.speedBps > 0) append(" · ${Formatters.speed(job.speedBps)}")
            job.etaSeconds?.let { append(" · resta ${Formatters.eta(it)}") }
            append(" · $sizeText")
        }

        return NotificationCompat.Builder(context, Constants.CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification_download)
            .setContentTitle(job.displayTitle().take(60))
            .setContentText("$percent% · ${if (job.speedBps > 0) Formatters.speed(job.speedBps) else "calculando velocidad…"}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(statusLine))
            .setProgress(100, percent, total == null)
            .setSubText(if (job.status == JobStatus.PROCESSING) "Procesando en el servidor…" else "Descargando en el servidor")
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(contentIntent())
            .addAction(
                R.drawable.ic_notification_error,
                context.getString(R.string.notif_action_cancel),
                actionIntent(Constants.ACTION_CANCEL, job.localId, job.localId.hashCode()),
            )
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** Summary used while the service owns several concurrent downloads. */
    fun buildSummary(jobs: List<DownloadJob>): Notification {
        val active = jobs.filter { it.isActive }
        val avg = if (active.isEmpty()) 0 else (active.sumOf { it.progress.toDouble() } / active.size).toInt()
        val totalSpeed = active.sumOf { it.speedBps }
        val text = buildString {
            append("${active.size} descarga${if (active.size == 1) "" else "s"} en curso")
            append(" · $avg%")
            if (totalSpeed > 0) append(" · ${Formatters.speed(totalSpeed)}")
        }
        return NotificationCompat.Builder(context, Constants.CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification_download)
            .setContentTitle("ReelDrop")
            .setContentText(text)
            .setStyle(NotificationCompat.InboxStyle().also { style ->
                active.take(5).forEach { style.addLine("${it.displayTitle().take(40)} — ${it.progress.toInt()}%") }
            })
            .setProgress(100, avg, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setGroup("reeldrop_active")
            .setGroupSummary(true)
            .setContentIntent(contentIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun buildIdleServiceNotification(): Notification =
        NotificationCompat.Builder(context, Constants.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification_download)
            .setContentTitle("ReelDrop")
            .setContentText("Preparando descarga…")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(contentIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    /** "Done" / "error" notification, always actionable. */
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
                        "${job.displayTitle()}\nGuardado en el servidor como ${job.filename ?: "archivo"}."
                    } else {
                        (job.errorMessage ?: "Error desconocido") + "\nToca Reintentar para volver a intentarlo."
                    },
                ),
            )
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .setGroup("reeldrop_results")

        if (completed) {
            builder.addAction(
                R.drawable.ic_notification_done,
                context.getString(R.string.notif_action_open),
                actionIntent(Constants.ACTION_OPEN_APP, job.localId, job.localId.hashCode() + 7),
            )
        } else {
            builder.addAction(
                R.drawable.ic_notification_download,
                context.getString(R.string.notif_action_retry),
                actionIntent(Constants.ACTION_RETRY, job.localId, job.localId.hashCode() + 3),
            )
        }
        return builder.build()
    }

    fun notifyProgress(job: DownloadJob) = safeNotify(idFor(job), buildProgress(job))

    fun notifyResult(job: DownloadJob) {
        val id = if (job.status == JobStatus.COMPLETED) {
            Constants.NOTIFICATION_RESULT_BASE_ID + (abs(job.localId.hashCode()) % 500)
        } else {
            Constants.NOTIFICATION_ERROR_BASE_ID + (abs(job.localId.hashCode()) % 500)
        }
        manager.cancel(idFor(job))
        safeNotify(id, buildResult(job))
    }

    fun cancel(job: DownloadJob) {
        runCatching { manager.cancel(idFor(job)) }
    }

    fun cancelAll() {
        runCatching { manager.cancelAll() }
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
