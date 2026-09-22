package com.manu.reeldrop.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire models for the ReelDrop PHP server (`api.php`). */

@Serializable
data class HealthResponse(
    val ok: Boolean = false,
    val app: String? = null,
    val version: String? = null,
    @SerialName("ytdlp") val ytDlp: String? = null,
    @SerialName("ytdlp_version") val ytDlpVersion: String? = null,
    val ffmpeg: Boolean = false,
    val tokenRequired: Boolean = false,
    @SerialName("free_space") val freeSpace: Long = 0L,
    @SerialName("total_space") val totalSpace: Long = 0L,
    @SerialName("active_jobs") val activeJobs: Int = 0,
    @SerialName("library_count") val libraryCount: Int = 0,
    val time: Long = 0L,
    val message: String? = null,
) {
    val ytDlpAvailable: Boolean get() = !ytDlp.isNullOrBlank()
}

@Serializable
data class JobDto(
    val id: String,
    val url: String = "",
    val status: String = "queued",
    val progress: Double = 0.0,
    val speed: String? = null,
    @SerialName("speed_bps") val speedBps: Long = 0L,
    val eta: String? = null,
    @SerialName("eta_seconds") val etaSeconds: Long? = null,
    @SerialName("downloaded_bytes") val downloadedBytes: Long = 0L,
    @SerialName("total_bytes") val totalBytes: Long = 0L,
    val title: String? = null,
    val author: String? = null,
    val thumbnail: String? = null,
    val filename: String? = null,
    val error: String? = null,
    val message: String? = null,
    val attempts: Int = 0,
    @SerialName("created_at") val createdAt: Long = 0L,
    @SerialName("started_at") val startedAt: Long? = null,
    @SerialName("finished_at") val finishedAt: Long? = null,
    val quality: String? = null,
)

@Serializable
data class JobEnvelope(
    val ok: Boolean = false,
    val job: JobDto? = null,
    val error: String? = null,
)

@Serializable
data class AnalysisResponse(
    val ok: Boolean = false,
    val media: MediaAnalysisDto? = null,
    val error: String? = null,
)

@Serializable
data class MediaAnalysisDto(
    val id: String? = null,
    val title: String? = null,
    val author: String? = null,
    val thumbnail: String? = null,
    val duration: Double? = null,
    val qualities: List<QualityOptionDto> = emptyList(),
)

@Serializable
data class QualityOptionDto(
    val id: String,
    val label: String,
    val description: String = "",
    val height: Int? = null,
    val kind: String = "video",
)

@Serializable
data class JobsEnvelope(
    val ok: Boolean = false,
    val jobs: List<JobDto> = emptyList(),
    val error: String? = null,
)

@Serializable
data class LibraryItemDto(
    @SerialName("file") val file: String,
    val size: Long = 0L,
    val mtime: Long = 0L,
    val duration: Double? = null,
    val width: Int? = null,
    val height: Int? = null,
    val thumb: String? = null,
    val url: String? = null,
    val title: String? = null,
    val author: String? = null,
)

@Serializable
data class LibraryEnvelope(
    val ok: Boolean = false,
    val items: List<LibraryItemDto> = emptyList(),
    val error: String? = null,
)

@Serializable
data class TemporaryFileDto(
    @SerialName("file") val file: String,
    val size: Long = 0L,
    val mtime: Long = 0L,
    val kind: String = "Temporal",
)

@Serializable
data class TemporaryEnvelope(
    val ok: Boolean = false,
    val items: List<TemporaryFileDto> = emptyList(),
    val error: String? = null,
)

@Serializable
data class SimpleResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val deleted: Int? = null,
    val bytes: Long? = null,
)

/** Payload sent by the server on the SSE progress channel. */
@Serializable
data class JobEventDto(
    val type: String = "progress",
    val id: String? = null,
    val status: String? = null,
    val progress: Double? = null,
    val speed: String? = null,
    @SerialName("speed_bps") val speedBps: Long? = null,
    val eta: String? = null,
    @SerialName("eta_seconds") val etaSeconds: Long? = null,
    @SerialName("downloaded_bytes") val downloadedBytes: Long? = null,
    @SerialName("total_bytes") val totalBytes: Long? = null,
    val title: String? = null,
    val author: String? = null,
    val thumbnail: String? = null,
    val filename: String? = null,
    val error: String? = null,
    val message: String? = null,
    /** Legacy event fields (compatible with the original web API). */
    val file: String? = null,
    val size: Long? = null,
    val url: String? = null,
    val phase: String? = null,
)
