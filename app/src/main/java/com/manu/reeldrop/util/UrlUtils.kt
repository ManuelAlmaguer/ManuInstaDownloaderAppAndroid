package com.manu.reeldrop.util

/** Instagram URL parsing and validation. */
object UrlUtils {

    private val instagramHosts = listOf("instagram.com", "instagr.am", "ig.me", "instagram.")
    private val urlRegex = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)

    /** Pulls the first http(s) URL out of arbitrary text (share sheets are messy). */
    fun extractUrl(text: String?): String? {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return urlRegex.find(raw)?.value?.trimEnd('.', ',', ')', ']', '"', '\'')
            ?: raw.takeIf { looksLikeUrl(it) }
    }

    fun looksLikeUrl(value: String): Boolean =
        value.startsWith("http://", true) || value.startsWith("https://", true)

    fun isInstagramUrl(value: String?): Boolean {
        val url = value?.trim().orEmpty()
        if (!looksLikeUrl(url)) return false
        val host = runCatching {
            java.net.URI(url).host?.lowercase()
        }.getOrNull() ?: return false
        return instagramHosts.any { host == it || host.endsWith(".$it") || host.contains(it) }
    }

    /** Short label such as "reel", "post" or "story" used in the UI. */
    fun contentKind(value: String?): String {
        val url = value?.lowercase().orEmpty()
        return when {
            "/reel" in url || "/reels" in url -> "Reel"
            "/stories/" in url -> "Historia"
            "/tv/" in url -> "IGTV"
            "/p/" in url -> "Publicación"
            else -> "Enlace"
        }
    }

    /** Best-effort short code extraction (D...) for duplicate detection. */
    fun shortCode(value: String?): String? {
        val url = value?.trim().orEmpty()
        val match = Regex("""/(?:reel|reels|p|tv|share)/([A-Za-z0-9_-]{5,})""").find(url)
        return match?.groupValues?.getOrNull(1)
    }
}
