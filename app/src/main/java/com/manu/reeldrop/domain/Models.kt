package com.manu.reeldrop.domain

import kotlinx.serialization.Serializable

/** Lifecycle of a single download, mirroring the states reported by the ReelDrop server. */
@Serializable
enum class JobStatus {
    QUEUED,
    DOWNLOADING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELED,
    RETRYING,
    UNKNOWN;

    val isActive: Boolean
        get() = this == QUEUED || this == DOWNLOADING || this == PROCESSING || this == RETRYING

    val isTerminal: Boolean
        get() = this == COMPLETED || this == FAILED || this == CANCELED

    companion object {
        fun from(raw: String?): JobStatus = when (raw?.trim()?.lowercase()) {
            "queued", "pending", "starting" -> QUEUED
            "downloading" -> DOWNLOADING
            "processing", "merging", "postprocessing", "extracting" -> PROCESSING
            "completed", "finished", "done", "success" -> COMPLETED
            "failed", "error" -> FAILED
            "canceled", "cancelled", "aborted" -> CANCELED
            "retrying" -> RETRYING
            else -> UNKNOWN
        }
    }
}

/** A download tracked by the app. Persisted locally so the queue survives restarts. */
@Serializable
data class DownloadJob(
    val localId: String,
    val serverId: String? = null,
    val url: String,
    val quality: String = Quality.BEST.id,
    val status: JobStatus = JobStatus.QUEUED,
    val title: String? = null,
    val author: String? = null,
    val thumbnailUrl: String? = null,
    val progress: Float = 0f,
    val speedBps: Long = 0L,
    val speedText: String? = null,
    val etaSeconds: Long? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val filename: String? = null,
    val errorMessage: String? = null,
    val attempts: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val savedToDevice: Boolean = false,
    val serverMessage: String? = null,
) {
    val isActive: Boolean get() = status.isActive
    val fraction: Float get() = (progress / 100f).coerceIn(0f, 1f)

    fun displayTitle(): String = title?.takeIf { it.isNotBlank() }
        ?: filename?.takeIf { it.isNotBlank() }
        ?: url.substringAfterLast('/').ifBlank { url }

    fun withRetryScheduled() = copy(status = JobStatus.RETRYING, attempts = attempts + 1)
}

/** Download quality presets exposed in Settings and on the home screen. */
enum class Quality(val id: String, val label: String, val description: String) {
    BEST("best", "Máxima calidad", "Mejor video y audio disponibles (MP4)"),
    FHD("1080", "1080p", "Full HD equilibrado"),
    HD("720", "720p", "Ligero y rápido"),
    SD("480", "480p", "Ahorra datos"),
    AUDIO("audio", "Solo audio", "Extrae el audio en M4A/MP3");

    companion object {
        fun fromId(id: String?): Quality = entries.firstOrNull { it.id == id } ?: BEST
    }
}

@Serializable
data class LibraryItem(
    val name: String,
    val size: Long = 0L,
    val modified: Long = 0L,
    val durationSeconds: Double? = null,
    val width: Int? = null,
    val height: Int? = null,
    val thumbnailUrl: String? = null,
    val streamUrl: String? = null,
    val title: String? = null,
    val author: String? = null,
) {
    val resolution: String? get() = if (width != null && height != null) "${height}p" else null
    val isVideo: Boolean get() = name.lowercase().let { it.endsWith(".mp4") || it.endsWith(".mkv") || it.endsWith(".webm") || it.endsWith(".mov") }
}

data class ServerHealth(
    val reachable: Boolean,
    val version: String? = null,
    val ytDlpVersion: String? = null,
    val ffmpegAvailable: Boolean = false,
    val freeSpaceBytes: Long = 0L,
    val totalSpaceBytes: Long = 0L,
    val activeJobs: Int = 0,
    val libraryCount: Int = 0,
    val tokenRequired: Boolean = false,
    val latencyMs: Long = 0L,
    val message: String? = null,
)

enum class AppTheme(val id: String, val label: String) {
    NEON("neon", "Neón Púrpura"),
    AMOLED("amoled", "Medianoche AMOLED"),
    OCEAN("ocean", "Océano"),
    SUNSET("sunset", "Atardecer"),
    FOREST("forest", "Bosque"),
    CANDY("candy", "Chicle"),
    DYNAMIC("dynamic", "Color dinámico"),
    SYSTEM("system", "Sistema");

    companion object {
        fun fromId(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: NEON
    }
}

enum class ThemeMode(val id: String, val label: String) {
    DARK("dark", "Oscuro"),
    LIGHT("light", "Claro"),
    SYSTEM("system", "Automático");

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: DARK
    }
}
