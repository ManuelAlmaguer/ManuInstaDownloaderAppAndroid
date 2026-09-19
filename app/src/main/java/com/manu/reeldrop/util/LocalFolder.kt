package com.manu.reeldrop.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.manu.reeldrop.data.remote.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

/**
 * Everything related to the *optional* custom save folder.
 *
 * The user picks any folder with Android's system picker (internal storage, SD card,
 * Documents provider…), ReelDrop keeps a persistable permission and then:
 *  * copies finished downloads into that folder while reporting progress,
 *  * lists the videos already there so they can be played, shared or deleted from the app.
 */
object LocalFolder {

    data class LocalVideo(
        val uri: Uri,
        val name: String,
        val size: Long,
        val lastModified: Long,
        val mimeType: String,
    ) {
        val isVideo: Boolean
            get() = mimeType.startsWith("video/") ||
                name.lowercase().let {
                    it.endsWith(".mp4") || it.endsWith(".mkv") || it.endsWith(".webm") ||
                        it.endsWith(".mov") || it.endsWith(".m4v") || it.endsWith(".3gp")
                }
    }

    fun folderName(context: Context, treeUri: String): String? {
        if (treeUri.isBlank()) return null
        return runCatching { DocumentFile.fromTreeUri(context, Uri.parse(treeUri))?.name }.getOrNull()
    }

    /** True when the persisted permission still works (for example, the SD card is still mounted). */
    fun isAccessible(context: Context, treeUri: String): Boolean {
        if (treeUri.isBlank()) return false
        return runCatching {
            val doc = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return false
            doc.exists() && doc.canRead()
        }.getOrDefault(false)
    }

    fun list(context: Context, treeUri: String): List<LocalVideo> {
        if (!isAccessible(context, treeUri)) return emptyList()
        return runCatching {
            val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return emptyList()
            root.listFiles()
                .filter { it.isFile }
                .map {
                    LocalVideo(
                        uri = it.uri,
                        name = it.name ?: "video.mp4",
                        size = it.length(),
                        lastModified = it.lastModified(),
                        mimeType = it.type ?: "video/mp4",
                    )
                }
                .filter { it.isVideo }
                .sortedByDescending { it.lastModified }
        }.getOrDefault(emptyList())
    }

    fun delete(context: Context, uri: Uri): Boolean =
        runCatching { DocumentFile.fromSingleUri(context, uri)?.delete() ?: false }.getOrDefault(false)

    /**
     * Streams a server file straight into the chosen folder (no extra temporary copy),
     * reporting progress so the UI can show the same speed/ETA numbers as a normal download.
     */
    suspend fun saveFromUrl(
        context: Context,
        treeUri: String,
        fileName: String,
        url: String,
        token: String,
        onProgress: suspend (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): Uri = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
            ?: throw IOException("La carpeta seleccionada ya no está disponible. Vuelve a elegirla en Ajustes.")
        if (!root.canWrite()) throw IOException("ReelDrop no tiene permiso de escritura en esa carpeta")

        val safeName = sanitize(fileName)
        root.findFile(safeName)?.delete()
        val target = root.createFile(mimeOf(safeName), stripExtension(safeName))
            ?: throw IOException("No se pudo crear el archivo en la carpeta elegida")

        val request = Request.Builder()
            .url(url)
            .apply { if (token.isNotBlank()) header("X-Api-Token", token) }
            .get()
            .build()

        try {
            HttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("El servidor respondió ${response.code}")
                val body = response.body ?: throw IOException("Respuesta vacía del servidor")
                val total = body.contentLength().takeIf { it > 0 } ?: 0L
                val output = context.contentResolver.openOutputStream(target.uri, "w")
                    ?: throw IOException("No se pudo abrir el archivo de destino")
                output.use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        var lastReport = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastReport > 250) {
                                lastReport = now
                                onProgress(downloaded, total)
                            }
                        }
                        out.flush()
                        onProgress(downloaded, if (total > 0) total else downloaded)
                    }
                }
            }
            target.uri
        } catch (error: Throwable) {
            runCatching { target.delete() }
            throw error
        }
    }

    fun playIntent(video: LocalVideo): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(video.uri, video.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun shareIntent(video: LocalVideo): Intent = Intent(Intent.ACTION_SEND).apply {
        type = video.mimeType
        putExtra(Intent.EXTRA_STREAM, video.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").take(120).ifBlank { "reeldrop_video.mp4" }

    private fun stripExtension(name: String): String = name.substringBeforeLast('.', name)

    private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "m4v" -> "video/x-m4v"
        "3gp" -> "video/3gpp"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        else -> "video/mp4"
    }
}
