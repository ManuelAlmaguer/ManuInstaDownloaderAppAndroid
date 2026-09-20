package com.manu.reeldrop.data.remote

import com.manu.reeldrop.core.Constants
import com.manu.reeldrop.core.Formatters
import com.manu.reeldrop.data.local.AppSettings
import com.manu.reeldrop.domain.JobStatus
import com.manu.reeldrop.domain.ServerHealth
import com.manu.reeldrop.util.NetworkInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Thin, dependency-light client for the ReelDrop server.
 *
 * It understands both the modern job API (`action=job-create`, `action=events`, …) and the
 * original web API shipped before the Android app (`action=download-stream`), so the app keeps
 * working with an older `api.php` while recommending the upgraded one.
 */
class ReelDropApi(private val settingsProvider: () -> AppSettings) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val sseClient: OkHttpClient by lazy {
        HttpClient.client.newBuilder()
            .readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // ---------------------------------------------------------------- helpers

    fun normalizeBaseUrl(raw: String): String {
        var value = raw.trim()
        if (value.isEmpty()) return Constants.DEFAULT_SERVER_URL
        if (!value.startsWith("http://", true) && !value.startsWith("https://", true)) {
            value = "http://$value"
        }
        value = value.trimEnd('/')
        value = value.removeSuffix(Constants.API_PATH).removeSuffix("/api.php?")
        return value
    }

    private fun endpoint(base: String, action: String): String = "${normalizeBaseUrl(base)}${Constants.API_PATH}?action=$action"

    private fun Request.Builder.authorize(token: String): Request.Builder =
        if (token.isBlank()) this else header("X-Api-Token", token)

    private fun okhttpError(e: Throwable, base: String): ApiException = when (e) {
        is ApiException -> e
        is SocketTimeoutException -> ApiException.Timeout("El servidor no respondió a tiempo ($base)")
        is IOException -> ApiException.Unreachable(
            "No se pudo conectar con el servidor ($base). ¿Está Termux y el servidor PHP en marcha?",
            e,
        )
        else -> ApiException.Unreachable(e.message ?: "Error desconocido de red", e)
    }

    private fun parseErrorCode(code: Int, body: String): ApiException {
        val serverMessage = runCatching {
            json.decodeFromString(SimpleResponse.serializer(), body).error
        }.getOrNull()?.takeIf { it.isNotBlank() }
        val message = serverMessage ?: "El servidor respondió con error $code"
        return when (code) {
            401, 403 -> ApiException.Rejected("Token de API incorrecto o ausente. Revísalo en Ajustes.", code)
            404 -> ApiException.NotSupported("El servidor no soporta esta operación ($message). Actualiza api.php.")
            in 400..499 -> ApiException.Rejected(message, code)
            else -> ApiException.ServerError(message)
        }
    }

    private suspend fun execute(request: Request, base: String): String = withContext(Dispatchers.IO) {
        try {
            HttpClient.client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw parseErrorCode(response.code, body)
                body
            }
        } catch (e: Throwable) {
            throw okhttpError(e, base)
        }
    }

    private suspend inline fun <reified T> getJson(url: String, token: String, base: String): T {
        val request = Request.Builder().url(url).authorize(token).get().build()
        return json.decodeFromString(execute(request, base))
    }

    private suspend inline fun <reified T> postJson(url: String, token: String, payload: String, base: String): T {
        val request = Request.Builder()
            .url(url)
            .authorize(token)
            .post(payload.toRequestBody(jsonMediaType))
            .build()
        return json.decodeFromString(execute(request, base))
    }

    private fun jsonBody(vararg pairs: Pair<String, String>): String {
        val fields = pairs.filter { it.second.isNotBlank() }.joinToString(",") {
            "\"${it.first}\":\"${it.second.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        }
        return "{$fields}"
    }

    // ---------------------------------------------------------------- health

    suspend fun health(baseUrl: String? = null): ServerHealth = withContext(Dispatchers.IO) {
        val settings = settingsProvider()
        val base = normalizeBaseUrl(baseUrl ?: settings.serverUrl)
        val started = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url(endpoint(base, "health"))
                .authorize(settings.apiToken)
                .get()
                .build()
            HttpClient.probeClient.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - started
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val error = runCatching { json.decodeFromString(HealthResponse.serializer(), body) }.getOrNull()
                    if (response.code == 404) {
                        // Older api.php without /health: still reachable, just features are limited.
                        return@withContext ServerHealth(
                            reachable = true,
                            latencyMs = latency,
                            message = "Servidor antiguo: sin endpoint /health. Actualiza la carpeta del servidor para ver disco y versiones.",
                        )
                    }
                    return@withContext ServerHealth(
                        reachable = false,
                        latencyMs = latency,
                        message = error?.message ?: "El servidor respondió ${response.code}",
                    )
                }
                val health = json.decodeFromString(HealthResponse.serializer(), body)
                ServerHealth(
                    reachable = true,
                    version = health.version ?: health.app,
                    ytDlpVersion = health.ytDlpVersion ?: health.ytDlp,
                    ffmpegAvailable = health.ffmpeg,
                    freeSpaceBytes = health.freeSpace,
                    totalSpaceBytes = health.totalSpace,
                    activeJobs = health.activeJobs,
                    libraryCount = health.libraryCount,
                    tokenRequired = health.tokenRequired,
                    latencyMs = latency,
                    message = if (health.ytDlpAvailable) null else "yt-dlp no está instalado en el servidor",
                )
            }
        } catch (e: Throwable) {
            ServerHealth(reachable = false, message = okhttpError(e, base).message)
        }
    }

    /**
     * Looks for a reachable ReelDrop server:
     *  1. the address already configured,
     *  2. loopback (`127.0.0.1`) — the Termux case,
     *  3. this phone's Wi-Fi address,
     *  4. a bounded sweep of the local subnet on the usual ports.
     *
     * Everything runs in parallel with a small concurrency budget so a phone stays responsive.
     */
    suspend fun discoverServers(): List<Pair<String, ServerHealth>> = coroutineScope {
        val ports = listOf(8080, 9000)
        val localIp = NetworkInfo.localIpv4()
        val loopback = listOf("127.0.0.1", "localhost")
        val hosts = (loopback + listOfNotNull(localIp)).distinct()

        val quick = (listOf(normalizeBaseUrl(settingsProvider().serverUrl)) +
            Constants.SERVER_CANDIDATES.map { normalizeBaseUrl(it) } +
            hosts.flatMap { host -> ports.map { port -> "http://$host:$port" } })
            .distinct()

        val quickResults = probeAll(quick)
        if (quickResults.isNotEmpty()) return@coroutineScope quickResults

        // Nothing on loopback: sweep a slice of the LAN (gateway + first neighbours).
        val lan = NetworkInfo.lanCandidates(localIp, maxNeighbours = 32)
            .flatMap { host -> ports.map { port -> "http://$host:$port" } }
            .filterNot { it in quick }
        probeAll(lan)
    }

    private suspend fun probeAll(urls: List<String>): List<Pair<String, ServerHealth>> = coroutineScope {
        val gate = Semaphore(24)
        urls.map { candidate ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    val health = runCatching { health(candidate) }.getOrNull()
                    if (health?.reachable == true) candidate to health else null
                }
            }
        }.awaitAll().filterNotNull()
    }

    // ---------------------------------------------------------------- jobs

    suspend fun createJob(url: String, qualityId: String, baseUrl: String? = null): JobDto {
        val settings = settingsProvider()
        val base = normalizeBaseUrl(baseUrl ?: settings.serverUrl)
        val envelope = postJson<JobEnvelope>(
            endpoint(base, "job-create"),
            settings.apiToken,
            jsonBody("url" to url, "quality" to qualityId),
            base,
        )
        return envelope.job ?: throw ApiException.ServerError(envelope.error ?: "El servidor no devolvió el trabajo creado")
    }

    suspend fun getJob(jobId: String, baseUrl: String? = null): JobDto {
        val settings = settingsProvider()
        val base = normalizeBaseUrl(baseUrl ?: settings.serverUrl)
        val envelope = getJson<JobEnvelope>(endpoint(base, "job") + "&id=$jobId", settings.apiToken, base)
        return envelope.job ?: throw ApiException.ServerError(envelope.error ?: "Trabajo no encontrado")
    }

    suspend fun listJobs(limit: Int = 50, baseUrl: String? = null): List<JobDto> {
        val settings = settingsProvider()
        val base = normalizeBaseUrl(baseUrl ?: settings.serverUrl)
        return getJson<JobsEnvelope>(endpoint(base, "jobs") + "&limit=$limit", settings.apiToken, base).jobs
    }

    suspend fun cancelJob(jobId: String) {
        val settings = settingsProvider()
        val base = normalizeBaseUrl(settings.serverUrl)
        runCatching {
            postJson<SimpleResponse>(endpoint(base, "job-cancel"), settings.apiToken, jsonBody("id" to jobId), base)
        }
    }

    suspend fun retryJob(jobId: String): JobDto {
        val settings = settingsProvider()
        val base = normalizeBaseUrl(settings.serverUrl)
        val envelope = postJson<JobEnvelope>(endpoint(base, "job-retry"), settings.apiToken, jsonBody("id" to jobId), base)
        return envelope.job ?: throw ApiException.ServerError(envelope.error ?: "No se pudo reintentar")
    }

    suspend fun deleteJob(jobId: String) {
        val settings = settingsProvider()
        val base = normalizeBaseUrl(settings.serverUrl)
        runCatching {
            postJson<SimpleResponse>(endpoint(base, "job-delete"), settings.apiToken, jsonBody("id" to jobId), base)
        }
    }

    suspend fun cancelAll() {
        val settings = settingsProvider()
        val base = normalizeBaseUrl(settings.serverUrl)
        runCatching {
            postJson<SimpleResponse>(endpoint(base, "job-cancel-all"), settings.apiToken, "{}", base)
        }
    }

    // ---------------------------------------------------------------- library

    suspend fun library(query: String = ""): List<LibraryItemDto> {
        val settings = settingsProvider()
        val base = normalizeBaseUrl(settings.serverUrl)
        val suffix = if (query.isBlank()) "" else "&q=" + java.net.URLEncoder.encode(query, "UTF-8")
        return getJson<LibraryEnvelope>(endpoint(base, "library") + suffix, settings.apiToken, base).items
    }

    suspend fun deleteLibraryItem(file: String) {
        val settings = settingsProvider()
        val base = normalizeBaseUrl(settings.serverUrl)
        val response = postJson<SimpleResponse>(
            endpoint(base, "library-delete"),
            settings.apiToken,
            jsonBody("file" to file),
            base,
        )
        if (!response.ok && response.error != null) throw ApiException.ServerError(response.error)
    }

    suspend fun temporaryFiles(): List<TemporaryFileDto> {
        val settings = settingsProvider()
        val base = normalizeBaseUrl(settings.serverUrl)
        return getJson<TemporaryEnvelope>(endpoint(base, "temporary"), settings.apiToken, base).items
    }

    suspend fun deleteTemporaryFile(file: String) {
        val settings = settingsProvider()
        val base = normalizeBaseUrl(settings.serverUrl)
        val response = postJson<SimpleResponse>(
            endpoint(base, "temporary-delete"),
            settings.apiToken,
            jsonBody("file" to file),
            base,
        )
        if (!response.ok && response.error != null) throw ApiException.ServerError(response.error)
    }

    suspend fun cleanup(): SimpleResponse {
        val settings = settingsProvider()
        val base = normalizeBaseUrl(settings.serverUrl)
        return postJson(endpoint(base, "cleanup"), settings.apiToken, "{}", base)
    }

    fun fileUrl(name: String): String {
        val settings = settingsProvider()
        val base = normalizeBaseUrl(settings.serverUrl)
        return endpoint(base, "file") + "&file=" + java.net.URLEncoder.encode(name, "UTF-8") + tokenQuery(settings)
    }

    fun thumbnailUrl(name: String): String {
        val settings = settingsProvider()
        val base = normalizeBaseUrl(settings.serverUrl)
        return endpoint(base, "thumb") + "&file=" + java.net.URLEncoder.encode(name, "UTF-8") + tokenQuery(settings)
    }

    private fun tokenQuery(settings: AppSettings): String =
        if (settings.apiToken.isBlank()) "" else "&token=" + java.net.URLEncoder.encode(settings.apiToken, "UTF-8")

    // ---------------------------------------------------------------- live progress

    /** Server-sent events for one job; falls back to the caller's polling loop on failure. */
    fun jobEvents(jobId: String): Flow<JobEventDto> = flow {
        val settings = settingsProvider()
        val base = normalizeBaseUrl(settings.serverUrl)
        val url = endpoint(base, "events") + "&id=$jobId" + tokenQuery(settings)
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .authorize(settings.apiToken)
            .get()
            .build()
        val call: Call = sseClient.newCall(request)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw parseErrorCode(response.code, response.body?.string().orEmpty())
                val source = response.body?.source() ?: throw ApiException.ServerError("Respuesta de streaming vacía")
                while (currentCoroutineContext().isActive) {
                    val line = source.readUtf8Line() ?: break
                    val payload = when {
                        line.startsWith("data:") -> line.removePrefix("data:").trim()
                        line.startsWith("{") -> line.trim()
                        else -> continue
                    }
                    if (payload.isEmpty()) continue
                    val event = runCatching { json.decodeFromString(JobEventDto.serializer(), payload) }.getOrNull()
                    if (event != null) emit(event)
                }
            }
        } catch (e: Throwable) {
            throw okhttpError(e, base)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Compatibility path for the original `api.php?action=download-stream` endpoint.
     * Used when the upgraded server is not installed yet.
     */
    fun legacyDownloadStream(url: String): Flow<JobEventDto> = flow {
        val settings = settingsProvider()
        val base = normalizeBaseUrl(settings.serverUrl)
        val request = Request.Builder()
            .url(endpoint(base, "download-stream"))
            .authorize(settings.apiToken)
            .header("Accept", "text/event-stream")
            .post(jsonBody("url" to url).toRequestBody(jsonMediaType))
            .build()
        val call = sseClient.newCall(request)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw parseErrorCode(response.code, response.body?.string().orEmpty())
                val source = response.body?.source() ?: throw ApiException.ServerError("Respuesta de streaming vacía")
                while (currentCoroutineContext().isActive) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty()) continue
                    val raw = runCatching { json.decodeFromString(LegacyEvent.serializer(), payload) }.getOrNull() ?: continue
                    val percent = raw.percent?.replace("%", "")?.trim()?.toDoubleOrNull()
                    emit(
                        JobEventDto(
                            type = raw.type,
                            status = when (raw.type) {
                                "done" -> "completed"
                                "error" -> "failed"
                                "progress" -> "downloading"
                                else -> "processing"
                            },
                            progress = percent,
                            speed = raw.speed,
                            speedBps = Formatters.parseSpeedToBps(raw.speed),
                            eta = raw.eta,
                            etaSeconds = Formatters.parseEtaToSeconds(raw.eta),
                            downloadedBytes = raw.downloaded,
                            totalBytes = raw.total,
                            filename = raw.file,
                            size = raw.size,
                            url = raw.url,
                            error = raw.error,
                            message = raw.phase ?: raw.line,
                        ),
                    )
                }
            }
        } catch (e: Throwable) {
            throw okhttpError(e, base)
        }
    }.flowOn(Dispatchers.IO)

    /** True when the job status mapping produced a usable state. */
    fun toStatus(raw: String?): JobStatus = JobStatus.from(raw)

    @kotlinx.serialization.Serializable
    private data class LegacyEvent(
        val type: String = "log",
        val percent: String? = null,
        val speed: String? = null,
        val eta: String? = null,
        val downloaded: Long? = null,
        val total: Long? = null,
        val file: String? = null,
        val size: Long? = null,
        val url: String? = null,
        val error: String? = null,
        val line: String? = null,
        val phase: String? = null,
    )
}
