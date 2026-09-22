package com.manu.reeldrop.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Human readable formatting helpers shared by every screen. */
object Formatters {

    data class ParsedProgress(
        val progress: Float,
        val speedText: String? = null,
        val etaSeconds: Long? = null,
        val downloadedBytes: Long? = null,
        val totalBytes: Long? = null,
    ) {
        val speedBps: Long get() = parseSpeedToBps(speedText)
    }

    private val progressLinePattern = Regex(
        """^\s*(?:download:)?\s*(\d+(?:\.\d+)?)%\|([^|]*)\|([^|]*)\|([^|]*)\|([^|]*)\|([^|]*)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    fun bytes(value: Long?): String {
        val v = value ?: 0L
        if (v <= 0) return "—"
        if (v < 1024) return "$v B"
        val kb = v / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.2f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }

    fun speed(bytesPerSecond: Long?): String {
        val v = bytesPerSecond ?: 0L
        if (v <= 0) return "—"
        return "${bytes(v)}/s"
    }

    fun eta(seconds: Long?): String {
        val s = seconds ?: return "—"
        if (s <= 0) return "—"
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return when {
            h > 0 -> String.format(Locale.US, "%dh %02dm", h, m)
            m > 0 -> String.format(Locale.US, "%dm %02ds", m, sec)
            else -> "${sec}s"
        }
    }

    fun duration(totalSeconds: Double?): String {
        val s = (totalSeconds ?: return "—").toLong()
        if (s <= 0) return "—"
        val m = s / 60
        val sec = s % 60
        return String.format(Locale.US, "%d:%02d", m, sec)
    }

    fun percent(progress: Float, decimals: Int = 0): String =
        String.format(Locale.US, "%.${decimals}f%%", progress.coerceIn(0f, 100f))

    /** Keeps fractional progress visible while it is still below ten percent. */
    fun progressLabel(progress: Float): String =
        percent(progress, if (progress > 0f && progress < 10f) 1 else 0)

    /**
     * Reads the compact progress line emitted by older ReelDrop servers.
     *
     * Example: 1.7%|35.16KiB/s|2:02|4844|N/A|46218825
     */
    fun parseProgressText(text: String?): ParsedProgress? {
        val match = progressLinePattern.matchEntire(text?.trim().orEmpty()) ?: return null
        val progress = match.groupValues[1].toFloatOrNull() ?: return null
        val speed = match.groupValues[2].trim().takeUnless(::isPlaceholder)
        val eta = match.groupValues[3].trim().takeUnless(::isPlaceholder)
        val downloaded = match.groupValues[4].trim().toLongOrNull()
        val estimate = match.groupValues[5].trim().toLongOrNull()
        val total = match.groupValues[6].trim().toLongOrNull()
        return ParsedProgress(
            progress = progress.coerceIn(0f, 100f),
            speedText = speed,
            etaSeconds = parseEtaToSeconds(eta),
            downloadedBytes = downloaded?.takeIf { it >= 0L },
            totalBytes = (total?.takeIf { it > 0L } ?: estimate?.takeIf { it > 0L }),
        )
    }

    fun isProgressText(text: String?): Boolean = parseProgressText(text) != null

    private fun isPlaceholder(value: String): Boolean =
        value.isBlank() || value.equals("N/A", true) || value.equals("Unknown", true)

    fun dateTime(epochMillis: Long?): String {
        val v = epochMillis ?: return "—"
        if (v <= 0) return "—"
        return SimpleDateFormat("d MMM yyyy · HH:mm", Locale("es", "ES")).format(Date(v))
    }

    fun relativeTime(epochMillis: Long?, now: Long = System.currentTimeMillis()): String {
        val v = epochMillis ?: return "—"
        if (v <= 0) return "—"
        val diff = abs(now - v)
        val minutes = diff / 60_000
        return when {
            minutes < 1 -> "hace unos segundos"
            minutes < 60 -> "hace $minutes min"
            minutes < 60 * 24 -> "hace ${minutes / 60} h"
            minutes < 60 * 24 * 7 -> "hace ${minutes / (60 * 24)} d"
            else -> dateTime(v)
        }
    }

    /** Parses yt-dlp style strings such as " 1.25MiB/s" or "00:12" coming from JSON. */
    fun parseSpeedToBps(text: String?): Long {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return 0L
        val number = raw.takeWhile { it.isDigit() || it == '.' || it == ',' }
            .replace(',', '.')
            .toDoubleOrNull() ?: return 0L
        val lower = raw.lowercase()
        val multiplier = when {
            "gib" in lower || "gb/s" in lower -> 1024.0 * 1024 * 1024
            "mib" in lower || "mb/s" in lower -> 1024.0 * 1024
            "kib" in lower || "kb/s" in lower -> 1024.0
            else -> 1.0
        }
        return (number * multiplier).toLong()
    }

    fun parseEtaToSeconds(text: String?): Long? {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty() || raw.equals("N/A", true) || raw.equals("Unknown", true)) return null
        val parts = raw.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> null
        }
    }
}
