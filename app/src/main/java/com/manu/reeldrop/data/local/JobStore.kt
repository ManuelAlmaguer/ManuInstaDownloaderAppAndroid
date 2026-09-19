package com.manu.reeldrop.data.local

import android.content.Context
import com.manu.reeldrop.domain.DownloadJob
import com.manu.reeldrop.domain.JobStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Durable, crash-safe queue + history.
 *
 * The queue is a plain JSON document written atomically (temp file + rename) so that a
 * process kill in the middle of a write never leaves a corrupt file behind. That is what
 * lets the app resume interrupted downloads after a restart or a reboot.
 */
class JobStore(private val context: Context) {

    @Serializable
    private data class Snapshot(val jobs: List<DownloadJob> = emptyList())

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }
    private val mutex = Mutex()
    private val file: File get() = File(context.filesDir, "download_queue.json")
    private val backupFile: File get() = File(context.filesDir, "download_queue.json.bak")

    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    val jobs: StateFlow<List<DownloadJob>> = _jobs

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val loaded = readFile(file) ?: readFile(backupFile) ?: emptyList()
            _jobs.value = loaded.sortedByDescending { it.createdAt }
            markInterruptedAsRetryable()
        }
    }

    /** After a crash/restart, "downloading" rows are not really running on the server anymore. */
    private suspend fun markInterruptedAsRetryable() {
        val updated = _jobs.value.map { job ->
            if (job.status == JobStatus.DOWNLOADING || job.status == JobStatus.PROCESSING) {
                job.copy(status = JobStatus.QUEUED, serverMessage = "Reanudado tras reinicio")
            } else {
                job
            }
        }
        if (updated != _jobs.value) {
            _jobs.value = updated
            persist(updated)
        }
    }

    private fun readFile(target: File): List<DownloadJob>? = runCatching {
        if (!target.exists()) return@runCatching null
        json.decodeFromString<Snapshot>(target.readText()).jobs
    }.getOrNull()

    private fun persist(jobs: List<DownloadJob>) {
        runCatching {
            val tmp = File(file.parentFile, "download_queue.tmp")
            tmp.writeText(json.encodeToString(Snapshot.serializer(), Snapshot(jobs)))
            if (file.exists()) file.copyTo(backupFile, overwrite = true)
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    suspend fun upsert(job: DownloadJob) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = _jobs.value.toMutableList()
            val index = current.indexOfFirst { it.localId == job.localId }
            if (index >= 0) current[index] = job else current.add(0, job)
            val sorted = current.sortedByDescending { it.createdAt }
            _jobs.value = sorted
            persist(sorted)
        }
    }

    suspend fun upsertAll(newJobs: List<DownloadJob>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = _jobs.value.associateBy { it.localId }.toMutableMap()
            newJobs.forEach { current[it.localId] = it }
            val sorted = current.values.sortedByDescending { it.createdAt }
            _jobs.value = sorted
            persist(sorted)
        }
    }

    suspend fun remove(localId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val filtered = _jobs.value.filterNot { it.localId == localId }
            _jobs.value = filtered
            persist(filtered)
        }
    }

    suspend fun clearFinished() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val filtered = _jobs.value.filter { it.isActive }
            _jobs.value = filtered
            persist(filtered)
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _jobs.value = emptyList()
            persist(emptyList())
        }
    }

    fun find(localId: String): DownloadJob? = _jobs.value.firstOrNull { it.localId == localId }
}
