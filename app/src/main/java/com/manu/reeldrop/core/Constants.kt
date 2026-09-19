package com.manu.reeldrop.core

/** Fixed identifiers and tunables used across the app. */
object Constants {
    const val DEFAULT_SERVER_URL = "http://127.0.0.1:8080"
    const val FALLBACK_SERVER_URL = "http://127.0.0.1:9000"

    /** Candidate addresses probed by "Buscar servidor". */
    val SERVER_CANDIDATES = listOf(
        "http://127.0.0.1:8080",
        "http://127.0.0.1:9000",
        "http://127.0.0.1:8000",
        "http://localhost:8080",
        "http://10.0.2.2:8080",
    )

    const val API_PATH = "/api.php"
    const val LEGACY_API_PATH = "/api.php"

    const val CHANNEL_PROGRESS = "reeldrop_progress"
    const val CHANNEL_RESULTS = "reeldrop_results"
    const val CHANNEL_SERVICE = "reeldrop_service"

    const val NOTIFICATION_SERVICE_ID = 1001
    const val NOTIFICATION_RESULT_BASE_ID = 2000
    const val NOTIFICATION_ERROR_BASE_ID = 3000

    const val ACTION_CANCEL = "com.manu.reeldrop.action.CANCEL_JOB"
    const val ACTION_RETRY = "com.manu.reeldrop.action.RETRY_JOB"
    const val ACTION_OPEN_APP = "com.manu.reeldrop.action.OPEN"
    const val ACTION_STOP_SERVICE = "com.manu.reeldrop.action.STOP_SERVICE"

    const val EXTRA_LOCAL_ID = "extra_local_id"
    const val EXTRA_URL = "extra_url"

    /** Default automatic retries when the server or the network fails. */
    const val DEFAULT_MAX_ATTEMPTS = 3
    const val DEFAULT_CONCURRENT_DOWNLOADS = 2

    const val SSE_RETRY_BACKOFF_MS = 1_500L
    const val POLL_INTERVAL_MS = 900L
    const val HEALTH_TIMEOUT_MS = 6_000L

    const val GITHUB_REPO = "https://github.com/ManuelAlmaguer/ManuInstaDownloaderAppAndroid"
    const val AUTHOR_NAME = "Manuel Almaguer Sosa"
    const val AUTHOR_EMAIL = "manu004@atomicmail.io"
    const val APP_NAME = "ReelDrop"
}
