package com.manu.reeldrop.service

import android.content.Context
import com.manu.reeldrop.core.Constants
import com.manu.reeldrop.core.Formatters
import com.manu.reeldrop.data.local.JobStore
import com.manu.reeldrop.data.local.SettingsRepository
import com.manu.reeldrop.data.remote.ApiException
import com.manu.reeldrop.data.remote.JobEventDto
import com.manu.reeldrop.data.remote.ReelDropApi
import com.manu.reeldrop.data.remote.withRetries
import com.manu.reeldrop.domain.DownloadJob
import com.manu.reeldrop.domain.JobStatus
import com.manu.reeldrop.domain.Quality
import com.manu.reeldrop.util.DeviceDownloads
import com.manu.reeldrop.util.LocalFolder
import com.manu.reeldrop.util.NetworkInfo
import com.manu.reeldrop.util.UrlUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.currentCoroutineContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The heart of the app: a durable download orchestrator.
 *
 * Responsibilities
 *  * validate links and enqueue jobs (persisted by [JobStore], so they survive restarts),
 *  * respect the user's concurrency limit,
 *  * follow progress in real time through server-sent events, with a polling fallback,
 *  * retry transient failures with exponential backoff + jitter,
 *  * fall back to the legacy `download-stream` endpoint when an older server is installed,
 *  * publish progress/result notifications and optionally mirror the file to the gallery.
 */
class DownloadEngine(
    private val context: Context,
    private val api: ReelDropApi,
    private val jobStore: JobStore,
    private val settings: SettingsRepository,
    private val notifications: Notifications,
    private val scope: CoroutineScope,
) {

    private val running = ConcurrentHashMap<String, Job>()
    private val gate = DownloadGate()

    /** Schedules everything that is queued, e.g. after a restart or when the network returns. */
    fun resumePending() {
        scope.launch {
            jobStore.jobs.value
                .filter { it.status == JobStatus.QUEUED || it.status == JobStatus.RETRYING }
                .forEach { schedule(it) }
        }
    }

    fun activeJobs(): List<DownloadJob> = jobStore.jobs.value.filter { it.isActive }

    fun hasActiveJobs(): Boolean = activeJobs().isNotEmpty()

    // ------------------------------------------------------------------ public API

    suspend fun enqueue(url: String, qualityId: String? = null): Result<DownloadJob> {
        val cleanUrl = UrlUtils.extractUrl(url) ?: return Result.failure(
            ApiException.Rejected("Pega un enlace válido de Instagram, YouTube o Facebook.", 400),
        )
        if (!UrlUtils.isSupportedUrl(cleanUrl)) {
            return Result.failure(
                ApiException.Rejected("Solo se admiten enlaces de Instagram, YouTube o Facebook.", 400),
            )
        }
        val quality = qualityId ?: settings.cached.qualityId
        val job = DownloadJob(
            localId = UUID.randomUUID().toString(),
            url = cleanUrl,
            quality = quality,
            status = JobStatus.QUEUED,
            title = UrlUtils.contentKind(cleanUrl),
        )
        jobStore.upsert(job)
        schedule(job)
        return Result.success(job)
    }

    fun retry(localId: String) {
        scope.launch {
            val job = jobStore.find(localId) ?: return@launch
            if (job.isActive) return@launch
            val fresh = job.copy(
                status = JobStatus.QUEUED,
                progress = 0f,
                attempts = 0,
                errorMessage = null,
                serverId = null,
                serverMessage = "Reintento manual",
                downloadedBytes = 0,
                totalBytes = 0,
                etaSeconds = null,
                speedBps = 0,
                speedText = null,
                finishedAt = null,
            )
            jobStore.upsert(fresh)
            schedule(fresh)
        }
    }

    fun cancel(localId: String) {
        scope.launch {
            val job = jobStore.find(localId) ?: return@launch
            running.remove(localId)?.cancel()
            job.serverId?.let { id -> runCatching { api.cancelJob(id) } }
            val canceled = job.copy(
                status = JobStatus.CANCELED,
                finishedAt = System.currentTimeMillis(),
                serverMessage = "Cancelado por el usuario",
            )
            jobStore.upsert(canceled)
            notifications.refreshProgress(jobStore.jobs.value)
        }
    }

    fun cancelAll() {
        scope.launch {
            running.values.forEach { it.cancel() }
            running.clear()
            runCatching { api.cancelAll() }
            val updated = jobStore.jobs.value.map { job ->
                if (job.isActive) job.copy(status = JobStatus.CANCELED, finishedAt = System.currentTimeMillis()) else job
            }
            jobStore.upsertAll(updated)
            notifications.cancelAll()
        }
    }

    fun remove(localId: String) {
        scope.launch {
            running.remove(localId)?.cancel()
            jobStore.remove(localId)
            notifications.refreshProgress(jobStore.jobs.value)
        }
    }

    fun clearFinished() {
        scope.launch { jobStore.clearFinished() }
    }

    fun saveToDevice(localId: String) {
        scope.launch {
            val job = jobStore.find(localId) ?: return@launch
            exportToDevice(job)
        }
    }

    // ------------------------------------------------------------------ internals

    private fun schedule(job: DownloadJob) {
        if (running.containsKey(job.localId)) return
        val coroutine = scope.launch {
            try {
                waitForAllowedNetwork(job.localId)
                gate.withSlot({ settings.cached.concurrentDownloads }) { runJob(job.localId) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (unexpected: Throwable) {
                // Defensive: never let an unexpected exception kill the queue.
                jobStore.find(job.localId)?.let { current ->
                    jobStore.upsert(
                        current.copy(
                            status = JobStatus.FAILED,
                            errorMessage = unexpected.message ?: "Error inesperado en la app",
                            finishedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            } finally {
                running.remove(job.localId)
            }
        }
        running[job.localId] = coroutine
        coroutine.invokeOnCompletion { running.remove(job.localId) }
    }

    private suspend fun waitForAllowedNetwork(localId: String) {
        while (settings.cached.wifiOnly &&
            !NetworkInfo.isLoopbackUrl(settings.cached.serverUrl) &&
            !NetworkInfo.isWifiConnected(context)
        ) {
            jobStore.find(localId)?.let { current ->
                if (current.status.isActive) {
                    jobStore.upsert(current.copy(serverMessage = "Esperando conexión Wi‑Fi…"))
                }
            }
            delay(Constants.POLL_INTERVAL_MS.coerceAtLeast(5_000L))
        }
    }

    private suspend fun runJob(localId: String) {
        var attempts = 0
        val maxAttempts = settings.cached.maxAttempts.coerceAtLeast(0)

        while (currentCoroutineContext().isActive) {
            val current = jobStore.find(localId) ?: return
            if (current.status == JobStatus.CANCELED || current.status == JobStatus.COMPLETED) return

            waitForAllowedNetwork(localId)
            attempts++
            val started = current.copy(
                status = JobStatus.DOWNLOADING,
                attempts = attempts,
                errorMessage = null,
                startedAt = current.startedAt ?: System.currentTimeMillis(),
                progress = if (attempts == 1) current.progress else 0f,
            )
            jobStore.upsert(started)
            publish(started)

            try {
                downloadOnce(started)
                return
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                val retryable = error.isRetryableOrUnknown()
                val canRetry = settings.cached.autoRetry && retryable && attempts < maxAttempts
                if (canRetry) {
                    val delayMs = backoffMs(attempts)
                    val retrying = jobStore.find(localId)?.copy(
                        status = JobStatus.RETRYING,
                        attempts = attempts,
                        errorMessage = error.message,
                        serverMessage = "Reintento ${attempts + 1} de $maxAttempts en ${delayMs / 1000}s",
                    )
                    if (retrying != null) {
                        jobStore.upsert(retrying)
                        notifications.notifyProgress(jobStore.jobs.value)
                    }
                    delay(delayMs)
                } else {
                    val failed = jobStore.find(localId)?.copy(
                        status = JobStatus.FAILED,
                        errorMessage = humanError(error),
                        attempts = attempts,
                        finishedAt = System.currentTimeMillis(),
                    )
                    if (failed != null) {
                        jobStore.upsert(failed)
                        notifications.notifyResult(failed, jobStore.jobs.value)
                    }
                    return
                }
            }
        }
    }

    /** One attempt: create the server job and follow it until it reaches a terminal state. */
    private suspend fun downloadOnce(job: DownloadJob) {
        val serverJob = try {
            withRetries(
                maxAttempts = 2,
                initialDelayMs = 700,
                onAttemptFailed = { _, _, wait ->
                    jobStore.find(job.localId)?.let {
                        jobStore.upsert(it.copy(serverMessage = "Sin conexión con el servidor, reintentando (${wait / 1000}s)…"))
                    }
                },
            ) { api.createJob(job.url, job.quality) }
        } catch (notSupported: ApiException.NotSupported) {
            downloadViaLegacyEndpoint(job)
            return
        }

        val withServerId = job.copy(
            serverId = serverJob.id,
            title = serverJob.title ?: job.title,
            author = serverJob.author ?: job.author,
            thumbnailUrl = serverJob.thumbnail ?: job.thumbnailUrl,
            status = JobStatus.from(serverJob.status),
            serverMessage = null,
        )
        jobStore.upsert(withServerId)
        publish(withServerId)

        follow(serverJob.id, withServerId)
    }

    /** Live progress: SSE first, polling as a safety net. */
    private suspend fun follow(serverJobId: String, job: DownloadJob) {
        var terminal = false
        try {
            api.jobEvents(serverJobId)
                .onEach { event ->
                    val updated = applyEvent(job.localId, event) ?: return@onEach
                    terminal = updated.status.isTerminal
                    if (updated.status.isActive) publish(updated)
                }
                .takeWhile { !terminal }
                .collect { /* events already applied */ }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (streamError: Throwable) {
            val latest = jobStore.find(job.localId)
            if (latest?.status?.isTerminal == true) return
            // Poll instead of failing the download because the stream broke.
            pollUntilFinished(serverJobId, job)
            return
        }
        if (!terminal) pollUntilFinished(serverJobId, job)
    }

    private suspend fun pollUntilFinished(serverJobId: String, job: DownloadJob) {
        var misses = 0
        while (currentCoroutineContext().isActive) {
            val snapshot = try {
                api.getJob(serverJobId)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                misses++
                if (misses > 20) throw error
                delay(Constants.POLL_INTERVAL_MS * 2)
                continue
            }

            misses = 0
            val dtoEvent = JobEventDto(
                type = "poll",
                id = snapshot.id,
                status = snapshot.status,
                progress = snapshot.progress,
                speed = snapshot.speed,
                speedBps = snapshot.speedBps,
                eta = snapshot.eta,
                etaSeconds = snapshot.etaSeconds,
                downloadedBytes = snapshot.downloadedBytes,
                totalBytes = snapshot.totalBytes,
                title = snapshot.title,
                author = snapshot.author,
                thumbnail = snapshot.thumbnail,
                filename = snapshot.filename,
                error = snapshot.error,
                message = snapshot.message,
            )
            val updated = applyEvent(job.localId, dtoEvent) ?: return
            if (updated.status.isActive) publish(updated)
            if (updated.status.isTerminal) return
            delay(Constants.POLL_INTERVAL_MS)
        }
    }

    /** Compatibility path with the original server (`api.php?action=download-stream`). */
    private suspend fun downloadViaLegacyEndpoint(job: DownloadJob) {
        jobStore.find(job.localId)?.let {
            jobStore.upsert(it.copy(serverMessage = "Servidor antiguo detectado: usando el modo compatible"))
        }
        var terminal = false
        api.legacyDownloadStream(job.url)
            .onEach { event ->
                val updated = applyEvent(job.localId, event) ?: return@onEach
                terminal = updated.status.isTerminal
                if (updated.status.isActive) publish(updated)
            }
            .takeWhile { !terminal }
            .collect { }
        if (!terminal) throw ApiException.ServerError("El servidor cerró la conexión antes de terminar la descarga")
    }

    /** Maps a server event onto the persisted job and reports terminal states. */
    private suspend fun applyEvent(localId: String, event: JobEventDto): DownloadJob? {
        val current = jobStore.find(localId) ?: return null
        val status = when (event.type.lowercase()) {
            "done" -> JobStatus.COMPLETED
            "error" -> JobStatus.FAILED
            "start" -> JobStatus.DOWNLOADING
            "log" -> if (event.status != null) JobStatus.from(event.status) else current.status
            else -> if (event.status != null) JobStatus.from(event.status) else current.status
        }
        val progressSource = event.message ?: event.phase
        val parsedProgress = Formatters.parseProgressText(progressSource)
        val readableMessage = listOfNotNull(event.message, event.phase)
            .firstOrNull { it.isNotBlank() && !Formatters.isProgressText(it) }
        val progress = event.progress?.toFloat()?.coerceIn(0f, 100f)
            ?: parsedProgress?.progress
            ?: current.progress
        val speedBps = event.speedBps?.takeIf { it > 0 }
            ?: Formatters.parseSpeedToBps(event.speed).takeIf { it > 0 }
            ?: parsedProgress?.speedBps?.takeIf { it > 0 }
            ?: current.speedBps
        val updated = current.copy(
            serverId = event.id ?: current.serverId,
            status = status,
            progress = if (status == JobStatus.COMPLETED) 100f else progress,
            speedBps = if (status.isTerminal) 0 else speedBps,
            speedText = event.speed?.takeUnless(Formatters::isProgressText)
                ?: parsedProgress?.speedText
                ?: current.speedText,
            etaSeconds = event.etaSeconds ?: Formatters.parseEtaToSeconds(event.eta)
                ?: parsedProgress?.etaSeconds
                ?: current.etaSeconds,
            downloadedBytes = event.downloadedBytes ?: parsedProgress?.downloadedBytes ?: current.downloadedBytes,
            totalBytes = event.totalBytes ?: event.size ?: parsedProgress?.totalBytes ?: current.totalBytes,
            title = event.title ?: current.title,
            author = event.author ?: current.author,
            thumbnailUrl = event.thumbnail ?: current.thumbnailUrl,
            filename = event.filename ?: event.file ?: current.filename,
            errorMessage = event.error ?: if (status == JobStatus.FAILED) current.errorMessage ?: "Error en el servidor" else current.errorMessage,
            serverMessage = readableMessage ?: if (parsedProgress != null) "Descargando…" else current.serverMessage,
            finishedAt = if (status.isTerminal) System.currentTimeMillis() else current.finishedAt,
        )
        jobStore.upsert(updated)
        if (updated.status.isTerminal) {
            if (updated.status == JobStatus.COMPLETED) maybeAutoSave(updated)
            notifications.notifyResult(updated, jobStore.jobs.value)
        }
        return updated
    }

    private suspend fun maybeAutoSave(job: DownloadJob) {
        if (!settings.cached.autoSaveToDevice) return
        exportToDevice(job)
    }

    /**
     * Copies the finished video out of the server: into the user's custom folder (SAF) when one
     * is configured, otherwise into Movies/ReelDrop through Android's DownloadManager.
     */
    private suspend fun exportToDevice(job: DownloadJob) {
        val name = job.filename ?: return
        val current = settings.cached
        val url = api.fileUrl(name)
        try {
            if (current.useCustomFolder && LocalFolder.isAccessible(context, current.saveFolderUri)) {
                jobStore.upsert(job.copy(serverMessage = "Guardando en la carpeta elegida…"))
                LocalFolder.saveFromUrl(
                    context = context,
                    treeUri = current.saveFolderUri,
                    fileName = name,
                    url = url,
                    token = current.apiTokenForRequests,
                ) { downloaded, total ->
                    val percent = if (total > 0) downloaded * 100 / total else 0
                    jobStore.upsert(
                        job.copy(
                            savedToDevice = false,
                            serverMessage = "Guardando en el dispositivo: $percent%",
                        ),
                    )
                }
                val label = current.saveFolderLabel.ifBlank { "carpeta elegida" }
                jobStore.upsert(job.copy(savedToDevice = true, serverMessage = "Guardado en $label"))
            } else {
                DeviceDownloads.saveToDevice(context, settings, name, url)
                jobStore.upsert(job.copy(savedToDevice = true, serverMessage = "Guardado en Movies/ReelDrop"))
            }
        } catch (error: Throwable) {
            jobStore.upsert(
                job.copy(serverMessage = "No se pudo guardar en el dispositivo: ${error.message ?: "error desconocido"}"),
            )
        }
        // Progress notifications are cheap; keep the device-copy state visible while it happens.
        jobStore.find(job.localId)?.let { publish(it) }
    }

    private fun publish(job: DownloadJob) {
        if (settings.cached.notificationsEnabled) notifications.notifyProgress(jobStore.jobs.value)
    }

    private fun humanError(error: Throwable): String = when (error) {
        is ApiException.Unreachable -> "Sin conexión con el servidor. Comprueba que Termux y el servidor PHP siguen activos."
        is ApiException.NotSupported -> "Tu servidor es antiguo o la acción no existe. Actualiza la carpeta del servidor."
        is ApiException.Timeout -> "El servidor tardó demasiado en responder."
        is ApiException.Rejected -> error.message ?: "El servidor rechazó la descarga."
        is ApiException.ServerError -> error.message ?: "Error del servidor."
        else -> error.message ?: "Error inesperado"
    }

    private fun Throwable.isRetryableOrUnknown(): Boolean = when (this) {
        is ApiException -> isRetryable
        is java.io.IOException -> true
        else -> true // unknown failures get one retry pass, then stop
    }

    private fun backoffMs(attempt: Int): Long {
        val base = 1_500L * (1 shl (attempt - 1).coerceIn(0, 4))
        val jitter = (0..800).random().toLong()
        return (base + jitter).coerceAtMost(30_000L)
    }

    /**
     * Dynamic concurrency gate: waits until fewer than `limit` downloads are in flight.
     * Reads the limit on every acquisition so changing it in Settings applies immediately.
     */
    private class DownloadGate {
        private val mutex = Mutex()
        private var inFlight = 0
        private val waiters = ArrayDeque<CompletableDeferred<Unit>>()

        suspend fun <T> withSlot(limitProvider: () -> Int, block: suspend () -> T): T {
            val ticket = CompletableDeferred<Unit>()
            mutex.withLock {
                val limit = limitProvider().coerceAtLeast(1)
                if (inFlight < limit) {
                    inFlight++
                    ticket.complete(Unit)
                } else {
                    waiters.addLast(ticket)
                }
            }
            ticket.await()
            try {
                return block()
            } finally {
                mutex.withLock {
                    val next = waiters.removeFirstOrNull()
                    if (next == null) inFlight-- else next.complete(Unit)
                }
            }
        }
    }
}
