package com.manu.reeldrop.data.repository

import com.manu.reeldrop.data.remote.ApiException
import com.manu.reeldrop.data.remote.ReelDropApi
import com.manu.reeldrop.domain.LinkAnalysis
import com.manu.reeldrop.domain.QualityOption
import com.manu.reeldrop.domain.ServerHealth

class ServerRepository(private val api: ReelDropApi) {

    suspend fun health(baseUrl: String? = null): ServerHealth = api.health(baseUrl)

    /** Returns the first reachable server among the known candidates. */
    suspend fun discover(): Pair<String, ServerHealth>? = api.discoverServers().firstOrNull()

    suspend fun analyze(url: String): LinkAnalysis {
        val media = api.analyze(url)
        return LinkAnalysis(
            id = media.id,
            title = media.title,
            author = media.author,
            thumbnailUrl = media.thumbnail,
            durationSeconds = media.duration,
            qualities = media.qualities.map {
                QualityOption(
                    id = it.id,
                    label = it.label,
                    description = it.description,
                    height = it.height,
                    kind = it.kind,
                )
            },
        )
    }

    suspend fun metricSummary(): String {
        val health = api.health()
        if (!health.reachable) throw ApiException.Unreachable(health.message ?: "Servidor inaccesible")
        return buildString {
            append("Servidor OK")
            health.version?.let { append(" · v$it") }
            health.ytDlpVersion?.let { append(" · yt-dlp $it") }
            if (!health.ffmpegAvailable) append(" · sin ffmpeg")
        }
    }
}
